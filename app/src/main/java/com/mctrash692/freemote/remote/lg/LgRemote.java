package com.mctrash692.freemote.remote.lg;

// ============================================================================
// FILE: LgRemote.java
// WHAT:  Controls LG webOS TVs over the network. These TVs use a technology
//        called WebSocket (a permanent two-way connection) to receive commands.
//        LG uses a protocol called SSAP which sends commands as JSON messages
//        (a text format like {"key": "VOLUMEUP"}).
//        Supports: key presses, volume, power, source switching, text input,
//        app launching.
// NOTE:  This has NOT been tested because no LG webOS TV was available during
//        development. Newer LG TVs (webOS 4+) may show a pairing code on screen
//        that needs to be entered in the app.
// ============================================================================

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.remote.BaseRemote;
import com.mctrash692.freemote.util.SslUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * LG webOS TV remote control via SSAP over WebSocket.
 *
 * UNTESTED — no LG webOS TV available during development.
 * Protocol reference: https://github.com/ConnectSDK/Connect-SDK-Android-Core
 *
 * SSAP flow:
 *   1. Connect WebSocket to ws://TV_IP:3000/ or wss://TV_IP:3001/
 *   2. Send SSAP register handshake with client-key
 *   3. If TV requires pairing, respond to pairing challenge
 *   4. Send key commands as JSON SSAP requests to:
 *      ssap://com.webos.service.networkinput/sendKey
 *   5. Text input via: ssap://com.webos.service.textinput/sendText
 *   6. App launch via: ssap://system.launcher/launch
 *
 * Pairing: LG webOS 4+ may require a pairing key shown on screen.
 * This implementation includes the pairing flow but it has never been tested.
 */
public class LgRemote extends BaseRemote {

    // ==========================================================================
    // SECTION: CONSTANTS
    // WHAT:  Ports for connecting to LG webOS TVs (secure and regular), plus
    //        names for SSAP message types and a translation table that maps
    //        common button names (like "KEY_VOLUMEUP") to LG names (like
    //        "VOLUMEUP").
    // ==========================================================================

    // Port for secure WebSocket connections (encrypted).
    private static final int PORT_WSS = 3001;
    // Port for regular WebSocket connections (not encrypted).
    private static final int PORT_WS  = 3000;
    // Storage name for saving the LG client key so we don't have to re-pair.
    private static final String PREFS_NAME = "freemote_lg";
    // Storage key name for saving the client key per TV.
    private static final String KEY_CLIENT_KEY = "client_key_";

    // SSAP message types
    private static final String SSAP_REGISTER = "register";
    private static final String SSAP_REQUEST  = "request";
    private static final String SSAP_RESPONSE = "response";

    // Key name map for LG webOS
    private static final Map<String, String> KEY_MAP = new HashMap<>();
    static {
        KEY_MAP.put("KEY_UP",          "UP");
        KEY_MAP.put("KEY_DOWN",        "DOWN");
        KEY_MAP.put("KEY_LEFT",        "LEFT");
        KEY_MAP.put("KEY_RIGHT",       "RIGHT");
        KEY_MAP.put("KEY_ENTER",       "ENTER");
        KEY_MAP.put("KEY_RETURN",      "BACK");
        KEY_MAP.put("KEY_BACK",        "BACK");
        KEY_MAP.put("KEY_HOME",        "HOME");
        KEY_MAP.put("KEY_MENU",        "MENU");
        KEY_MAP.put("KEY_INFO",        "INFO");
        KEY_MAP.put("KEY_VOLUMEUP",    "VOLUMEUP");
        KEY_MAP.put("KEY_VOLUMEDOWN",  "VOLUMEDOWN");
        KEY_MAP.put("KEY_MUTE",        "MUTE");
        KEY_MAP.put("KEY_CHUP",        "CHANNELUP");
        KEY_MAP.put("KEY_CHDOWN",      "CHANNELDOWN");
        KEY_MAP.put("KEY_POWER",       "POWER");
        KEY_MAP.put("KEY_GUIDE",       "GUIDE");
        KEY_MAP.put("KEY_SOURCE",      "INPUT");
    }

    // ==========================================================================
    // SECTION: SETTINGS
    // WHAT:  Stores the phone app's context (needed for saving settings), the
    //        network client, the active WebSocket connection, and the saved
    //        client key used for authentication.
    // ==========================================================================

    // The Android app's information (needed to save the client key to storage).
    private final Context context;

    // The HTTP/WebSocket client that handles network connections.
    private OkHttpClient client;
    // The active WebSocket connection to the LG TV.
    private WebSocket webSocket;
    // A secret key that identifies this app to the TV (saved after pairing).
    private String clientKey;

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Sets up the remote for a specific LG webOS TV. Tries to load a
    //        previously-saved client key so the app doesn't need to re-pair
    //        every time.
    // INPUT: context  = the Android app (needed to save settings)
    //        ip       = the TV's network address (like "192.168.1.100")
    //        listener = the app code that gets notified of success/failure
    // ==========================================================================

    public LgRemote(Context context, String ip, Listener listener) {
        super("LgRemote", ip, listener);
        this.context = context;
        this.clientKey = loadClientKey();
        keycodeMap.put(85, "KEY_PLAYPAUSE");
        keycodeMap.put(86, "KEY_STOP");
    }

    // ==========================================================================
    // METHOD: loadClientKey
    // WHAT:  Reads the saved secret key from the phone's storage. If there's a
    //        saved key, the app can skip the pairing process. If not, it
    //        generates a new random key.
    // ==========================================================================

    private String loadClientKey() {
        if (context == null) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_CLIENT_KEY + ip, null);
        if (saved != null && !saved.isEmpty()) {
            Log.d(TAG, "Loaded saved client-key");
            return saved;
        }
        String generated = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Log.d(TAG, "Generated new client-key");
        return generated;
    }

    // ==========================================================================
    // METHOD: saveClientKey
    // WHAT:  Saves the secret key to the phone's storage so the app can use it
    //        next time without having to pair again.
    // ==========================================================================

    private void saveClientKey(String key) {
        if (context == null || key == null || key.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_CLIENT_KEY + ip, key).apply();
        Log.d(TAG, "Saved client-key");
    }

    // ==========================================================================
    // METHOD: connect
    // WHAT:  Connects to the LG webOS TV. It first tries a secure WebSocket
    //        connection (encrypted, port 3001). If that fails, it falls back
    //        to a regular connection (port 3000).
    // ==========================================================================

    @Override
    public void connect() {
        connectWithPort(PORT_WSS, true);
    }

    // ==========================================================================
    // METHOD: connectWithPort
    // WHAT:  Opens a WebSocket connection to the LG TV on a specific port.
    //        If secure mode is on, it uses encryption (WSS). Once the
    //        connection opens, it automatically sends the registration
    //        handshake to identify this app to the TV.
    // INPUT: port   = the port number (3000 for regular, 3001 for secure)
    //        secure = whether to use encryption (true = WSS, false = WS)
    // ==========================================================================

    private void connectWithPort(int port, boolean secure) {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS);

            if (secure) {
                SslUtils.applyToOkHttp(builder);
            }

            client = builder.build();
            String scheme = secure ? "wss" : "ws";
            String url = scheme + "://" + ip + ":" + port + "/";

            Log.d(TAG, "Connecting to: " + url);

            client.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
                @Override public void onOpen(WebSocket ws, Response response) {
                    webSocket = ws;
                    connected.set(true);
                    Log.d(TAG, "WebSocket opened on port " + port);
                    sendHandshake();
                }
                @Override public void onMessage(WebSocket ws, String text) {
                    handleMessage(text);
                }
                @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                    connected.set(false);
                    Log.e(TAG, "WebSocket failure: " + (t != null ? t.getMessage() : "unknown"));
                    if (secure) {
                        Log.d(TAG, "Retrying on plain WebSocket port " + PORT_WS);
                        connectWithPort(PORT_WS, false);
                    } else if (listener != null) {
                        listener.onError("LG connection failed: " + (t != null ? t.getMessage() : "unknown"));
                    }
                }
                @Override public void onClosed(WebSocket ws, int code, String reason) {
                    connected.set(false);
                    if (listener != null) listener.onDisconnected(reason);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Connection exception", e);
            if (secure) {
                connectWithPort(PORT_WS, false);
            } else if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    // ==========================================================================
    // METHOD: sendHandshake
    // WHAT:  Sends a registration message to the LG TV to introduce this app.
    //        It tells the TV the app's name, version, and what permissions it
    //        needs (like controlling input, reading settings, etc.). It also
    //        sends the saved client key so the TV recognizes this app from a
    //        previous pairing.
    // ==========================================================================

    /**
     * Send SSAP register handshake with full manifest.
     * Matches the format used by lgtvremote-cli and ConnectSDK.
     */
    private void sendHandshake() {
        if (webSocket == null) return;
        try {
            JSONObject signed = new JSONObject();
            signed.put("created", "20140509");
            signed.put("appId", "com.lge.test");
            signed.put("vendorId", "com.lge");
            JSONObject localizedNames = new JSONObject();
            localizedNames.put("", "");
            signed.put("localizedAppNames", localizedNames);

            JSONObject manifest = new JSONObject();
            manifest.put("manifestVersion", 1);
            manifest.put("appVersion", "1.1");
            manifest.put("signed", signed);
            manifest.put("permissions", new JSONArray()
                .put("TEST_SECURE")
                .put("CONTROL_INPUT_TEXT")
                .put("CONTROL_MOUSE_AND_KEYBOARD")
                .put("READ_INSTALLED_APPS")
                .put("READ_LGSN_X")
                .put("READ_SETTINGS")
                .put("WRITE_SETTINGS")
                .put("READ_TV_CHANNEL_LIST")
                .put("WRITE_TV_CHANNEL_LIST")
                .put("CONTROL_BLOCKED_EXTERNAL_APPLICATION_LIST")
            );
            manifest.put("serial", UUID.randomUUID().toString().replace("-", "").substring(0, 32));

            JSONObject payload = new JSONObject();
            payload.put("forcePairing", false);
            payload.put("pairingType", "PROMPT");
            payload.put("manifest", manifest);
            payload.put("client-key", clientKey);

            JSONObject msg = new JSONObject();
            msg.put("type", SSAP_REGISTER);
            msg.put("id", "register_0");
            msg.put("payload", payload);

            webSocket.send(msg.toString());
            Log.d(TAG, "Sent SSAP register with manifest");
        } catch (JSONException e) {
            Log.e(TAG, "Handshake error", e);
        }
    }

    // ==========================================================================
    // METHOD: handleMessage
    // WHAT:  Processes messages coming back from the LG TV. If the TV accepts
    //        our registration, it sends back a client-key that we save for
    //        future connections. If the TV asks for pairing (shows a code on
    //        screen), that flow would be handled here (but hasn't been tested).
    // INPUT: text = the message from the TV as text
    // ==========================================================================

    private void handleMessage(String text) {
        Log.d(TAG, "Received: " + text);
        try {
            JSONObject msg = new JSONObject(text);
            String type = msg.optString("type");

            if (SSAP_RESPONSE.equals(type) || SSAP_REGISTER.equals(type)) {
                JSONObject payload = msg.optJSONObject("payload");
                if (payload != null) {
                    String pairingType = payload.optString("pairingType", null);
                    if (pairingType != null && !pairingType.isEmpty()) {
                        Log.d(TAG, "TV requires pairing: " + pairingType);
                        // UNTESTED: pairing flow may need user input
                        // LG may show a code on screen that the app needs to display
                    }
                    String respKey = payload.optString("client-key", null);
                    if (respKey != null && !respKey.isEmpty()) {
                        Log.d(TAG, "Registered with client-key: " + respKey);
                        clientKey = respKey;
                        saveClientKey(respKey);
                        if (listener != null) listener.onConnected();
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Message parse error", e);
        }
    }

    // ==========================================================================
    // METHOD: disconnect
    // WHAT:  Disconnects from the LG TV. Closes the WebSocket connection and
    //        shuts down the network client.
    // ==========================================================================

    @Override
    public void disconnect() {
        connected.set(false);
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client = null;
        }
    }

    // ==========================================================================
    // METHOD: sendKey
    // WHAT:  Presses a button on the LG TV (like Volume Up or Power). Looks up
    //        the common button name in the KEY_MAP, then sends the LG-specific
    //        command as a JSON message over the WebSocket connection.
    // INPUT: keyCode = the name of the button to press (e.g. "KEY_VOLUMEUP")
    // ==========================================================================

    @Override
    public void sendKey(String keyCode) {
        if (!connected.get() || webSocket == null) return;

        String lgKey = KEY_MAP.get(keyCode);
        if (lgKey == null) {
            Log.w(TAG, "Unknown key for LG: " + keyCode);
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("key", lgKey);

            JSONObject msg = new JSONObject();
            msg.put("type", SSAP_REQUEST);
            msg.put("id", UUID.randomUUID().toString());
            msg.put("uri", "ssap://com.webos.service.networkinput/sendKey");
            msg.put("payload", payload);

            webSocket.send(msg.toString());
            Log.d(TAG, "Sent key: " + lgKey);
        } catch (JSONException e) {
            Log.e(TAG, "sendKey error", e);
        }
    }

    // ==========================================================================
    // METHOD: sendText
    // WHAT:  Types text on the LG TV (like for search boxes). Sends a JSON
    //        message over the WebSocket telling the TV what text to enter.
    // INPUT: text       = the text to type
    //        replaceAll = whether to replace existing text or add to it
    // NOTE:  UNTESTED - the text input command may need verification.
    // ==========================================================================

    @Override
    public void sendText(String text, boolean replaceAll) {
        if (!connected.get() || webSocket == null) return;
        // UNTESTED: text input SSAP URI may need verification
        try {
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            payload.put("replace", replaceAll);

            JSONObject msg = new JSONObject();
            msg.put("type", SSAP_REQUEST);
            msg.put("id", UUID.randomUUID().toString());
            msg.put("uri", "ssap://com.webos.service.textinput/sendText");
            msg.put("payload", payload);

            webSocket.send(msg.toString());
            Log.d(TAG, "Sent text: " + text);
        } catch (JSONException e) {
            Log.e(TAG, "sendText error", e);
        }
    }

    // ==========================================================================
    // METHOD: sendMouseMove / sendMouseClick / sendMouseWheel / sendMouseActivate
    // WHAT:  Mouse control is NOT available on LG webOS via this SSAP API.
    //        These methods just log a warning and do nothing.
    // ==========================================================================

    @Override
    public void sendMouseMove(int dx, int dy) {
        // UNTESTED: LG webOS mouse support via SSAP may not be available
        Log.w(TAG, "Mouse move not supported on LG webOS via SSAP");
    }

    @Override
    public void sendMouseClick(String button) {
        Log.w(TAG, "Mouse click not supported on LG webOS via SSAP");
    }

    @Override
    public void sendMouseWheel(int deltaY) {
        Log.w(TAG, "Mouse wheel not supported on LG webOS via SSAP");
    }

    @Override
    public void sendMouseActivate() {
        // No-op for LG — no pointer mode
    }

    // ==========================================================================
    // METHOD: sendAppLaunch
    // WHAT:  Opens an app on the LG TV (like Netflix or YouTube). Sends a JSON
    //        message over the WebSocket telling the TV which app to start.
    // INPUT: appId = the app's ID string (e.g. "netflix")
    // NOTE:  UNTESTED - app launch via SSAP may need verification.
    // ==========================================================================

    @Override
    public void sendAppLaunch(String appId) {
        if (!connected.get() || webSocket == null) return;
        // UNTESTED: app launch via SSAP
        try {
            JSONObject payload = new JSONObject();
            payload.put("id", appId);

            JSONObject msg = new JSONObject();
            msg.put("type", SSAP_REQUEST);
            msg.put("id", UUID.randomUUID().toString());
            msg.put("uri", "ssap://system.launcher/launch");
            msg.put("payload", payload);

            webSocket.send(msg.toString());
            Log.d(TAG, "Launching app: " + appId);
        } catch (JSONException e) {
            Log.e(TAG, "sendAppLaunch error", e);
        }
    }

}
