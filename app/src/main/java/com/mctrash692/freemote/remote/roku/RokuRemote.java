package com.mctrash692.freemote.remote.roku;

// ============================================================================
// FILE: RokuRemote.java
// WHAT:  Controls Roku TVs and devices over the network. Roku uses a simple
//        system called ECP (External Control Protocol) that works through basic
//        web requests (like typing URLs in a browser). No pairing or password
//        is needed — any app on the same network can send commands.
//        Supports: key presses, volume, power, app launching, text input.
// NOTE:  This has NOT been tested because no Roku device was available during
//        development. The key names come from Roku's official documentation.
// ============================================================================

import android.util.Log;

import com.mctrash692.freemote.remote.BaseRemote;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Roku TV remote control via External Control Protocol (ECP).
 *
 * UNTESTED — no Roku device available during development.
 * Protocol reference: https://developer.roku.com/docs/developer-program/debugging/external-control-api.md
 *
 * ECP is a simple REST API — no pairing required, no WebSocket:
 *   Keypress:  POST http://TV_IP:8060/keypress/{key}
 *   App launch: POST http://TV_IP:8060/launch/{appId}
 *   Query:     GET  http://TV_IP:8060/query/apps
 *   Info:      GET  http://TV_IP:8060/query/device-info
 *
 * Key names are specific to Roku — see KEY_MAP below.
 * UNTESTED: key names from Roku docs, should be correct.
 */
public class RokuRemote extends BaseRemote {

    // ==========================================================================
    // SECTION: CONSTANTS
    // WHAT:  Port number and a translation table that maps common button names
    //        (like "KEY_VOLUMEUP") to Roku-specific names (like "VolumeUp").
    // ==========================================================================

    // Port number Roku devices listen on for ECP remote control commands.
    private static final int PORT = 8060;

    // Translates common button names to Roku's button names.
    private static final Map<String, String> KEY_MAP = new HashMap<>();
    static {
        KEY_MAP.put("KEY_UP",          "Up");
        KEY_MAP.put("KEY_DOWN",        "Down");
        KEY_MAP.put("KEY_LEFT",        "Left");
        KEY_MAP.put("KEY_RIGHT",       "Right");
        KEY_MAP.put("KEY_ENTER",       "Select");
        KEY_MAP.put("KEY_RETURN",      "Back");
        KEY_MAP.put("KEY_BACK",        "Back");
        KEY_MAP.put("KEY_HOME",        "Home");
        KEY_MAP.put("KEY_MENU",        "Star");
        KEY_MAP.put("KEY_INFO",        "Info");
        KEY_MAP.put("KEY_VOLUMEUP",    "VolumeUp");
        KEY_MAP.put("KEY_VOLUMEDOWN",  "VolumeDown");
        KEY_MAP.put("KEY_MUTE",        "VolumeMute");
        KEY_MAP.put("KEY_POWER",       "PowerOff");
        KEY_MAP.put("KEY_0", "Lit_0"); KEY_MAP.put("KEY_1", "Lit_1");
        KEY_MAP.put("KEY_2", "Lit_2"); KEY_MAP.put("KEY_3", "Lit_3");
        KEY_MAP.put("KEY_4", "Lit_4"); KEY_MAP.put("KEY_5", "Lit_5");
        KEY_MAP.put("KEY_6", "Lit_6"); KEY_MAP.put("KEY_7", "Lit_7");
        KEY_MAP.put("KEY_8", "Lit_8"); KEY_MAP.put("KEY_9", "Lit_9");
    }

    // ==========================================================================
    // SECTION: BACKGROUND WORKER
    // WHAT:  A single background thread that handles all network communication.
    //        All commands are sent on this thread so the app stays responsive.
    // ==========================================================================

    // A single background thread that runs all the network tasks (connecting,
    // sending keys, etc.) so the app doesn't freeze.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Sets up the remote for a specific Roku device.
    // INPUT: ip       = the Roku's network address (like "192.168.1.100")
    //        listener = the app code that gets notified of success/failure
    // ==========================================================================

    public RokuRemote(String ip, Listener listener) {
        super("RokuRemote", ip, listener);
        keycodeMap.put(85, "KEY_PLAYPAUSE");
        keycodeMap.put(86, "KEY_STOP");
    }

    // ==========================================================================
    // METHOD: connect
    // WHAT:  Checks if the Roku device is reachable on the network by asking it
    //        for device information. If the Roku responds, we know we can talk
    //        to it. This runs in the background so the app doesn't freeze.
    // ==========================================================================

    @Override
    public void connect() {
        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://" + ip + ":" + PORT + "/query/device-info");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    connected.set(true);
                    Log.d(TAG, "Roku reachable at " + ip + ":" + PORT);
                    if (listener != null) listener.onConnected();
                } else {
                    connected.set(false);
                    if (listener != null) listener.onError("Roku returned HTTP " + code);
                }
            } catch (Exception e) {
                connected.set(false);
                Log.e(TAG, "Roku connection check failed", e);
                if (listener != null) listener.onError("Roku not reachable: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // ==========================================================================
    // METHOD: disconnect
    // WHAT:  Disconnects from the Roku device. Marks the connection as closed
    //        and stops the background worker thread.
    // ==========================================================================

    @Override
    public void disconnect() {
        connected.set(false);
        executor.shutdownNow();
    }

    // ==========================================================================
    // METHOD: sendKey
    // WHAT:  Presses a button on the Roku device (like Volume Up or Power).
    //        Looks up the common button name in the KEY_MAP, then sends the
    //        Roku-specific command as a simple web POST request.
    // INPUT: keyCode = the name of the button to press (e.g. "KEY_VOLUMEUP")
    // ==========================================================================

    @Override
    public void sendKey(String keyCode) {
        if (!connected.get()) return;

        String rokuKey = KEY_MAP.get(keyCode);
        if (rokuKey == null) {
            Log.w(TAG, "Unknown key for Roku: " + keyCode);
            return;
        }

        executor.submit(() -> {
            try {
                URL url = new URL("http://" + ip + ":" + PORT + "/keypress/" + rokuKey);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.setRequestProperty("Content-Length", "0");
                OutputStream os = conn.getOutputStream();
                os.write(new byte[0]);
                os.flush();
                os.close();
                int code = conn.getResponseCode();
                conn.disconnect();
                Log.d(TAG, "Sent key " + rokuKey + " -> HTTP " + code);
            } catch (Exception e) {
                Log.e(TAG, "sendKey error", e);
                connected.set(false);
                if (listener != null) listener.onDisconnected(e.getMessage());
            }
        });
    }

    // ==========================================================================
    // METHOD: sendText
    // WHAT:  Types text on the Roku device (like for search boxes). Each letter
    //        is sent one at a time as a separate web request. For example,
    //        typing "abc" sends three requests: one for "a", one for "b", one
    //        for "c".
    // INPUT: text       = the text to type
    //        replaceAll = whether to clear existing text first (not used on Roku)
    // ==========================================================================

    @Override
    public void sendText(String text, boolean replaceAll) {
        if (!connected.get() || text == null || text.isEmpty()) return;
        executor.submit(() -> {
            for (char c : text.toCharArray()) {
                try {
                    String keyName = Character.isLetter(c) ? "Lit_" + Character.toLowerCase(c) : "Lit_" + c;
                    URI uri = new URI("http", null, ip, PORT, "/keypress/" + keyName, null, null);
                    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(1000);
                    conn.setReadTimeout(1000);
                    conn.setRequestProperty("Content-Length", "0");
                    conn.getOutputStream().close();
                    conn.getResponseCode();
                    conn.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "sendText char error: " + c, e);
                }
            }
        });
    }

    // ==========================================================================
    // METHOD: sendMouseMove / sendMouseClick / sendMouseWheel / sendMouseActivate
    // WHAT:  Mouse control is NOT available on Roku's ECP protocol. These
    //        methods just log a warning and do nothing.
    // ==========================================================================

    @Override
    public void sendMouseMove(int dx, int dy) {
        Log.w(TAG, "Mouse not supported on Roku ECP");
    }

    @Override
    public void sendMouseClick(String button) {
        Log.w(TAG, "Mouse not supported on Roku ECP");
    }

    @Override
    public void sendMouseWheel(int deltaY) {
        Log.w(TAG, "Mouse wheel not supported on Roku ECP");
    }

    @Override
    public void sendMouseActivate() { /* no-op */ }

    // ==========================================================================
    // METHOD: sendAppLaunch
    // WHAT:  Opens an app on the Roku device (like Netflix or YouTube). Sends
    //        a simple web request telling the Roku which app to start.
    // INPUT: appId = the app's ID string (e.g. "12" for Netflix on Roku)
    // ==========================================================================

    @Override
    public void sendAppLaunch(String appId) {
        if (!connected.get()) return;
        executor.submit(() -> {
            try {
                URL url = new URL("http://" + ip + ":" + PORT + "/launch/" + appId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Content-Length", "0");
                OutputStream os = conn.getOutputStream();
                os.write(new byte[0]);
                os.flush();
                os.close();
                int code = conn.getResponseCode();
                conn.disconnect();
                Log.d(TAG, "Launch app " + appId + " -> HTTP " + code);
            } catch (Exception e) {
                Log.e(TAG, "sendAppLaunch error", e);
            }
        });
    }

}
