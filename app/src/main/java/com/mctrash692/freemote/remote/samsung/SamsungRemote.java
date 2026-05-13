// ============================================================================
// FILE: SamsungRemote.java
// WHAT:  Controls a Samsung Tizen TV over the network. This is the most
//        important remote in the app — it handles all the heavy lifting for
//        Samsung TVs (2016 and newer) including:
//        - Connecting via WebSocket (secure WSS on port 8002, then plain WS on 8001)
//        - Sending key presses (volume, home, power, etc.)
//        - Sending text input for keyboard/ search fields
//        - Mouse/touchpad control (move, click, scroll)
//        - Launching apps (Netflix, YouTube, Prime Video, etc.)
//        - Handling reconnection when the connection drops
//        It tries 3 methods in order: OkHttp WebSocket -> Legacy WebSocket ->
//        REST API (last resort, can't send keys).
// ============================================================================
package com.mctrash692.freemote.remote.samsung;

import android.util.Base64;
import android.util.Log;

import com.mctrash692.freemote.remote.BaseRemote;
import com.mctrash692.freemote.util.SslUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.Looper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SamsungRemote extends BaseRemote {

    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    // -- Ports for connecting to Samsung TVs ----------------------------------
    private static final int PORT_WSS = 8002;   // Secure WebSocket (preferred)
    private static final int PORT_WS  = 8001;   // Plain WebSocket (fallback)

    // -- App name sent to the TV so it knows what app is connecting -----------
    private static final String APP_NAME     = "Freemote";
    private static final String APP_NAME_B64 =
        Base64.encodeToString(APP_NAME.getBytes(), Base64.NO_WRAP);

    // ==========================================================================
    // APP IDS
    // WHAT:  Each app on a Samsung Tizen TV has a numeric ID. These are used
    //        to tell the TV which app to launch. Some apps also have a
    //        "package name" fallback (like "org.tizen.netflix") in case the
    //        numeric ID doesn't work on older TV models.
    // ==========================================================================
    public static final String APP_YOUTUBE    = "111299001912";
    public static final String APP_PRIME      = "3201512006785";
    public static final String APP_PRIME_FB   = "org.tizen.primevideo";   // fallback
    public static final String APP_DISNEY     = "3201901017640";
    public static final String APP_NETFLIX    = "11101200001";
    public static final String APP_NETFLIX_FB = "org.tizen.netflix";      // fallback
    public static final String APP_HBOMAX     = "3202301029760";
    public static final String APP_PLEX       = "3201512006963";
    public static final String APP_KODI       = "org.xbmc.kodi";
    public static final String APP_APPLETV    = "3201807016597";
    public static final String APP_SPOTIFY    = "3201606009684";
    public static final String APP_HULU       = "3201601007625";

    // ==========================================================================
    // SAMSUNG KEY NAMES
    // WHAT:  Samsung uses its own names for remote buttons. These are the text
    //        commands sent over the WebSocket. For example, Play/Pause is
    //        "KEY_PLAYPAUSE" (not "KEYCODE_MEDIA_PLAY_PAUSE" like Android uses).
    // ==========================================================================
    private static final String KEY_PLAY_PAUSE = "KEY_PLAYPAUSE";
    private static final String KEY_STOP       = "KEY_STOP";
    private static final String KEY_PREV       = "KEY_REWIND";
    private static final String KEY_NEXT       = "KEY_FORWARD";
    private static final String KEY_RW         = "KEY_REWIND";
    private static final String KEY_FF         = "KEY_FF";

    // ==========================================================================
    // CONNECTION METHODS (tried in order)
    // WHAT:  The app tries these one at a time until one works:
    //        1. OKHTTP   — Modern WebSocket using the OkHttp library (best)
    //        2. LEGACY_WS — Older WebSocket using a separate library (fallback)
    //        3. REST_API  — Just HTTP commands (can only launch apps, not keys)
    // ==========================================================================
    private enum ConnectionMethod {
        OKHTTP,
        LEGACY_WS,
        REST_API
    }

    // ==========================================================================
    // INTERNAL SETTINGS
    // WHAT:  Things the remote needs to remember while talking to the TV.
    // ==========================================================================
    private final String savedToken;        // The pairing code the TV gave us
    private OkHttpClient okHttpClient;      // The OkHttp connection manager
    private volatile WebSocket okHttpWebSocket;  // The open WebSocket (volatile = thread-safe)
    private SamsungWebSocket legacyWebSocket;    // The older WebSocket (if needed)
    private volatile String currentToken;   // The current pairing token
    private ConnectionMethod activeMethod;  // Which connection method is working
    private boolean deviceInfoFetched;      // Whether we already got TV details
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int reconnectAttempts = 0;      // How many times we've tried to reconnect
    private static final int MAX_RECONNECT_ATTEMPTS = 5;  // Give up after 5 tries
    private int tvWidth = 1920;             // TV screen width (guessed until we get real info)
    private int tvHeight = 1080;            // TV screen height
    private int lastMouseX = -1;            // Where the mouse cursor was last
    private int lastMouseY = -1;
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);  // Already trying to reconnect?
    private boolean keyboardInitialized;       // Whether the IME has been warmed up

    public SamsungRemote(String ip, String savedToken, Listener listener) {
        super("SamsungRemote", ip, listener);
        this.savedToken   = savedToken;
        this.currentToken = savedToken;
        keycodeMap.put(4,    "KEY_RETURN");
        keycodeMap.put(24,   "KEY_VOLUP");
        keycodeMap.put(25,   "KEY_VOLDOWN");
        keycodeMap.put(85,   KEY_PLAY_PAUSE);
        keycodeMap.put(86,   KEY_STOP);
        keycodeMap.put(87,   KEY_NEXT);
        keycodeMap.put(88,   KEY_PREV);
        keycodeMap.put(89,   KEY_RW);
        keycodeMap.put(90,   KEY_FF);
    }

    // =========================================================================
    // SECTION: CONNECTION
    // WHAT:  Establishes a WebSocket connection to the Samsung TV. It tries:
    //        1. Secure WebSocket (WSS) on port 8002 (preferred)
    //        2. Plain WebSocket (WS) on port 8001 (fallback)
    //        3. Legacy Java-WebSocket library (older TVs)
    //        4. REST API only (last resort — can only launch apps)
    //        If the connection drops, it automatically retries with backoff.
    // =========================================================================

    @Override
    public void connect() {
        connectWithOkHttp(PORT_WSS, true);
    }

    private void connectWithOkHttp(int port, boolean secure) {
        activeMethod = ConnectionMethod.OKHTTP;
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS);

            if (secure) {
                SslUtils.applyToOkHttp(builder);
            }

            okHttpClient = builder.build();

            String scheme = secure ? "wss" : "ws";
            StringBuilder url = new StringBuilder()
                .append(scheme).append("://").append(ip).append(":").append(port)
                .append("/api/v2/channels/samsung.remote.control")
                .append("?name=").append(APP_NAME_B64);

            if (currentToken != null && !currentToken.isEmpty()) {
                url.append("&token=").append(currentToken);
            }

            Log.d(TAG, "OkHttp connecting: " + url);
            okHttpClient.newWebSocket(
                new Request.Builder().url(url.toString()).build(),
                new OkHttpWebSocketListener(port, secure));

        } catch (Exception e) {
            Log.e(TAG, "OkHttp connect exception", e);
            fallbackToLegacyWebSocket();
        }
    }

    private class OkHttpWebSocketListener extends WebSocketListener {
        private final int port;
        private final boolean secure;

        OkHttpWebSocketListener(int port, boolean secure) {
            this.port   = port;
            this.secure = secure;
        }

        @Override
        public void onOpen(WebSocket ws, Response response) {
            okHttpWebSocket = ws;
            connected.set(true);
            reconnectAttempts = 0;
            Log.d(TAG, "OkHttp connected on port " + port);
            if (listener != null) {
                listener.onConnected();
                fetchDeviceInfo();
            }
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            Log.d(TAG, "OkHttp message: " + text);
            handleMessage(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            connected.set(false);
            Log.e(TAG, "OkHttp failure port=" + port + ": " + t.getMessage());
            scheduleReconnect(secure);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            connected.set(false);
            Log.d(TAG, "OkHttp closed port=" + port + ": " + reason);
            scheduleReconnect(secure);
        }
    }

    private void fallbackToLegacyWebSocket() {
        Log.w(TAG, "Falling back to SamsungWebSocket");
        activeMethod = ConnectionMethod.LEGACY_WS;

        legacyWebSocket = new SamsungWebSocket(ip, PORT_WS,
            new SamsungWebSocket.ConnectionListener() {
                @Override public void onConnected() {
                    connected.set(true);
                    reconnectAttempts = 0;
                    if (listener != null) listener.onConnected();
                    fetchDeviceInfo();
                }
                @Override public void onDisconnected() {
                    connected.set(false);
                    retryOkHttpWithBackoff();
                }
                @Override public void onError(String error) {
                    Log.e(TAG, "Legacy WS error: " + error);
                    retryOkHttpWithBackoff();
                }
                @Override public void onPairingRequired(String token) {
                    Log.d(TAG, "Pairing token: " + token);
                    if (listener != null) listener.onTokenReceived(token);
                }
            });

        legacyWebSocket.connect();
    }

    private void retryOkHttpWithBackoff() {
        if (!reconnectPending.compareAndSet(false, true)) {
            Log.d(TAG, "Retry already pending, skipping");
            return;
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            reconnectPending.set(false);
            Log.w(TAG, "Max reconnect attempts reached, falling back to REST API");
            fallbackToRestApi();
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(1000 * reconnectAttempts, 10000);
        Log.d(TAG, "Retry OkHttp attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + " in " + delay + "ms");
        mainHandler.postDelayed(() -> {
            reconnectPending.set(false);
            if (connected.get()) return;
            if (okHttpClient != null) {
                okHttpClient.dispatcher().executorService().shutdown();
                okHttpClient = null;
            }
            okHttpWebSocket = null;
            connectWithOkHttp(PORT_WSS, true);
        }, delay);
    }

    private void fallbackToRestApi() {
        Log.w(TAG, "Falling back to REST API only");
        activeMethod = ConnectionMethod.REST_API;
        connected.set(true);
        if (listener != null) listener.onConnected();
        fetchDeviceInfo();
    }

    private void scheduleReconnect(boolean wasSecure) {
        if (!reconnectPending.compareAndSet(false, true)) {
            Log.d(TAG, "Reconnect already pending, skipping");
            return;
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            reconnectPending.set(false);
            Log.w(TAG, "Max reconnect attempts reached, falling back");
            if (wasSecure) {
                fallbackToLegacyWebSocket();
            } else {
                fallbackToRestApi();
            }
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(1000 * reconnectAttempts, 10000);
        Log.d(TAG, "Reconnect attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + " in " + delay + "ms");
        mainHandler.postDelayed(() -> {
            reconnectPending.set(false);
            retryConnect(PORT_WSS, true);
        }, delay);
    }

    private void retryConnect(int port, boolean secure) {
        if (connected.get()) return;
        okHttpWebSocket = null;
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient = null;
        }
        connectWithOkHttp(port, secure);
    }

    private void handleMessage(String text) {
        try {
            JSONObject json  = new JSONObject(text);
            String event     = json.optString("event");
            if ("ms.channel.connect".equals(event)) {
                JSONObject data = json.optJSONObject("data");
                if (data != null && data.has("token")) {
                    currentToken = data.getString("token");
                    Log.d(TAG, "Got token: " + currentToken);
                    if (listener != null) listener.onTokenReceived(currentToken);
                }
            } else if ("ms.remote.touchEnable".equals(event)) {
                Log.d(TAG, "Mouse mode enabled by TV");
            } else if ("ms.remote.imeStart".equals(event)) {
                Log.d(TAG, "IME mode started by TV");
                if (listener != null) listener.onImeStarted();
            } else if ("ms.remote.imeUpdate".equals(event)) {
                Log.d(TAG, "IME text updated by TV");
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleMessage error", e);
        }
    }

    @Override
    public void disconnect() {
        connected.set(false);
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS;
        reconnectPending.set(false);
        mainHandler.removeCallbacksAndMessages(null);
        if (okHttpWebSocket != null) {
            okHttpWebSocket.close(1000, "User disconnected");
            okHttpWebSocket = null;
        }
        if (legacyWebSocket != null) {
            legacyWebSocket.disconnect();
            legacyWebSocket = null;
        }
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient = null;
        }
    }

        // =========================================================================
    // SECTION: KEY SENDING
    // WHAT:  Translates button presses into Samsung's remote control protocol.
    //        Samsung uses special names for keys like "KEY_VOLUP" instead of
    //        "KEY_VOLUMEUP". This section normalizes those names and sends
    //        the command through whichever connection method is active.
    // =========================================================================

    @Override
    public void sendKey(int keyCode, boolean longPress) {
        String mapped = keycodeMap.get(keyCode);
        if (mapped != null) {
            sendKey(mapped, longPress);
        } else {
            Log.w(TAG, "No Samsung key mapping for int keycode: " + keyCode);
        }
    }

    @Override
    public void sendKey(String keyCode) {
        sendKey(keyCode, false);
    }

    private void sendKey(String keyCode, boolean longPress) {
        if (!connected.get()) {
            Log.w(TAG, "sendKey: not connected");
            return;
        }

        // Normalise common aliases — Samsung uses shorter names than Android
        String mappedKey = keyCode;
        if ("KEY_VOLUMEUP".equals(keyCode))       mappedKey = "KEY_VOLUP";
        else if ("KEY_VOLUMEDOWN".equals(keyCode)) mappedKey = "KEY_VOLDOWN";

        switch (activeMethod) {
            case OKHTTP:
                sendKeyViaOkHttp(mappedKey, longPress);
                break;
            case LEGACY_WS:
                if (legacyWebSocket != null && legacyWebSocket.isConnected()) {
                    legacyWebSocket.sendCommand(mappedKey, longPress);
                }
                break;
            case REST_API:
                Log.w(TAG, "REST API fallback — key " + mappedKey + " not sent");
                break;
        }
    }

    private void sendWsMessage(JSONObject params) {
        try {
            okHttpWebSocket.send(new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params).toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendWsMessage error", e);
        }
    }

    private void sendKeyViaOkHttp(String mappedKey, boolean longPress) {
        if (okHttpWebSocket == null) return;
        try {
            String pressType = longPress ? "Click&Hold" : "Click";
            JSONObject params = new JSONObject()
                .put("Cmd",          pressType)
                .put("DataOfCmd",    mappedKey)
                .put("Option",       "false")
                .put("TypeOfRemote", "SendRemoteKey");
            sendWsMessage(params);
            Log.d(TAG, "Key sent: " + mappedKey + (longPress ? " (long)" : ""));
        } catch (JSONException e) {
            Log.e(TAG, "sendKeyViaOkHttp error", e);
        }
    }

    // =========================================================================
    // SECTION: TEXT INPUT
    // WHAT:  Sends typed text to the Samsung TV for entering search queries,
    //        passwords, or any text field. Samsung uses the "SendInputString"
    //        protocol which sends the entire text as one message (base64-
    //        encoded). IMPORTANT: This does NOT send KEY_ENTER before the
    //        text, unlike sendText(), which means it won't accidentally
    //        select a highlighted grid key on the TV's on-screen keyboard.
    // =========================================================================

    @Override
    public void sendInputString(String text) {
        if (!connected.get()) {
            Log.w(TAG, "sendInputString: not connected");
            return;
        }
        if (activeMethod == ConnectionMethod.OKHTTP) {
            if (!keyboardInitialized) {
                // First keyboard use this session — warm up the TV's IME
                // by sending an empty input string so the first real character
                // doesn't get delayed by IME initialization.
                keyboardInitialized = true;
                sendTextViaOkHttp("");
            }
            if (text != null && !text.isEmpty()) {
                sendTextViaOkHttp(text);
            }
        } else {
            Log.w(TAG, "sendInputString: unsupported connection method " + activeMethod);
        }
    }

    @Override
    public void sendText(String text, boolean replaceAll) {
        if (!connected.get()) {
            Log.w(TAG, "sendText: not connected");
            return;
        }
        if (text == null || text.isEmpty()) return;

        switch (activeMethod) {
            case OKHTTP:
                sendKeyViaOkHttp("KEY_ENTER", false);
                mainHandler.postDelayed(() -> {
                    if (connected.get()) sendTextViaOkHttp(text);
                }, 350);
                break;
            case LEGACY_WS:
                // Legacy WS has no SendInputString — best effort via key enter.
                if (legacyWebSocket != null && legacyWebSocket.isConnected()) {
                    legacyWebSocket.sendCommand("KEY_ENTER");
                }
                break;
            case REST_API:
                Log.w(TAG, "REST API fallback — text not sent");
                break;
        }
    }

    private void sendTextViaOkHttp(String text) {
        if (okHttpWebSocket == null) return;
        try {
            // Public spec (freman gist, xchwarze/samsung-tv-ws-api, Ape/samsungctl #75):
            //   Cmd: base64-encoded text
            //   DataOfCmd: literal "base64"
            //   TypeOfRemote: "SendInputString"
            String encoded = Base64.encodeToString(
                text.getBytes("UTF-8"), Base64.NO_WRAP);
            JSONObject params = new JSONObject()
                .put("Cmd",          encoded)
                .put("DataOfCmd",    "base64")
                .put("TypeOfRemote", "SendInputString");
            sendWsMessage(params);
            Log.d(TAG, "Text sent: " + text);
        } catch (Exception e) {
            Log.e(TAG, "sendTextViaOkHttp error", e);
        }
    }

    // =========================================================================
    // SECTION: MOUSE / TOUCHPAD
    // WHAT:  Controls the TV's mouse cursor like a laptop touchpad. Samsung
    //        uses the "ProcessMouseDevice" protocol which works with absolute
    //        screen coordinates (x=0..1920, y=0..1080 for a 1080p TV).
    //        The app maps your finger position on the phone's touchpad to
    //        the TV's screen resolution so moving your finger right on the
    //        phone moves the cursor right on the TV.
    // Reference: https://github.com/Ape/samsungctl/issues/75
    // =========================================================================

    private String timestamp() {
        return String.format("%.3f", System.currentTimeMillis() / 1000.0);
    }

    @Override
    public void sendMouseMove(int x, int y) {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP
                || okHttpWebSocket == null) {
            Log.d(TAG, "sendMouseMove skipped: connected=" + connected.get()
                + " method=" + activeMethod + " ws=" + (okHttpWebSocket != null));
            return;
        }
        try {
            lastMouseX = x;
            lastMouseY = y;
            JSONObject params = new JSONObject()
                .put("Cmd", "Move")
                .put("x", x)
                .put("y", y)
                .put("Time", timestamp())
                .put("TypeOfRemote", "ProcessMouseDevice");
            sendWsMessage(params);
            Log.d(TAG, "Mouse move: " + x + "," + y);
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseMove error", e);
        }
    }

    @Override
    public void sendMouseClick(String button) {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP
                || okHttpWebSocket == null) {
            Log.d(TAG, "sendMouseClick skipped: connected=" + connected.get()
                + " method=" + activeMethod);
            return;
        }
        try {
            String cmd;
            if ("LeftClick".equals(button) || "left".equalsIgnoreCase(button)) {
                cmd = "LeftClick";
            } else if ("right".equalsIgnoreCase(button) || "RightClick".equals(button)) {
                cmd = "RightClick";
            } else {
                Log.w(TAG, "Unsupported mouse button: " + button + " — sending LeftClick instead");
                cmd = "LeftClick";
            }
            JSONObject params = new JSONObject()
                .put("Cmd", cmd)
                .put("TypeOfRemote", "ProcessMouseDevice");
            sendWsMessage(params);
            Log.d(TAG, "Mouse click: " + cmd);
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseClick error", e);
        }
    }

    @Override
    public void sendMouseWheel(int deltaY) {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP
                || okHttpWebSocket == null) {
            Log.d(TAG, "sendMouseWheel skipped: connected=" + connected.get()
                + " method=" + activeMethod);
            return;
        }
        // Scroll via repeated small vertical moves (ProcessMouseDevice doesn't
        // have a dedicated scroll command, so we simulate with small moves).
        try {
            int y = lastMouseY >= 0 ? lastMouseY : tvHeight / 2;
            int x = lastMouseX >= 0 ? lastMouseX : tvWidth / 2;
            y += deltaY * 20;
            y = Math.max(0, Math.min(tvHeight, y));
            JSONObject params = new JSONObject()
                .put("Cmd", "Move")
                .put("x", x)
                .put("y", y)
                .put("Time", timestamp())
                .put("TypeOfRemote", "ProcessMouseDevice");
            sendWsMessage(params);
            lastMouseY = y;
            Log.d(TAG, "Mouse wheel: " + deltaY + " → y=" + y);
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseWheel error", e);
        }
    }

    @Override
    public void sendMouseActivate() {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP
                || okHttpWebSocket == null) {
            Log.d(TAG, "sendMouseActivate skipped: connected=" + connected.get()
                + " method=" + activeMethod);
            return;
        }
        try {
            int cx = tvWidth / 2;
            int cy = tvHeight / 2;
            lastMouseX = cx;
            lastMouseY = cy;
            JSONObject params = new JSONObject()
                .put("Cmd", "Move")
                .put("x", cx)
                .put("y", cy)
                .put("Time", timestamp())
                .put("TypeOfRemote", "ProcessMouseDevice");
            sendWsMessage(params);
            Log.d(TAG, "Mouse activate to " + cx + "," + cy);
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseActivate error", e);
        }
    }

    @Override
    public int getTvWidth() { return tvWidth; }
    @Override
    public int getTvHeight() { return tvHeight; }

    // =========================================================================
    // SECTION: APP LAUNCH
    // WHAT:  Opens an app on the Samsung TV (Netflix, YouTube, Prime Video,
    //        etc.). Samsung uses numeric app IDs (like "11101200001" for
    //        Netflix). It sends an HTTP POST request to the TV asking it to
    //        start the app. Tries port 8001 first, then 8002 if that fails,
    //        and falls back to package-name IDs for some apps.
    // =========================================================================

    @Override
    public void sendAppLaunch(String appId) {
        Log.d(TAG, "Launching app: " + appId);
        new Thread(() -> {
            boolean launched = tryAppLaunch(appId, 8001);
            if (!launched) {
                launched = tryAppLaunch(appId, 8002);
            }
            // If the primary numeric ID failed try the package-name fallback.
            if (!launched) {
                String fallback = getFallbackId(appId);
                if (fallback != null) {
                    launched = tryAppLaunch(fallback, 8001);
                    if (!launched) tryAppLaunch(fallback, 8002);
                }
            }
            if (!launched) {
                Log.w(TAG, "App launch failed for: " + appId);
            }
        }).start();
    }

    /**
     * Attempt a single POST to /api/v2/applications/{appId} on the given port.
     * Returns true if the TV responded with a 2xx or 3xx code (accepted).
     */
    private boolean tryAppLaunch(String appId, int port) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://" + ip + ":" + port + "/api/v2/applications/" + appId);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(false);
            conn.connect();
            int code = conn.getResponseCode();
            Log.d(TAG, "App launch " + appId + " port=" + port + " → HTTP " + code);
            return code >= 200 && code < 400;
        } catch (Exception e) {
            Log.d(TAG, "App launch attempt failed port=" + port + ": " + e.getMessage());
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Returns a package-name fallback for known numeric IDs, or null. */
    private String getFallbackId(String appId) {
        if (APP_PRIME.equals(appId))   return APP_PRIME_FB;
        if (APP_NETFLIX.equals(appId)) return APP_NETFLIX_FB;
        return null;
    }

    // =========================================================================
    // SECTION: MEDIA CONTROLS
    // WHAT:  Play, pause, skip, rewind, and fast-forward media on the TV.
    //        These are shortcuts that call sendKey() with the correct
    //        Samsung key names for each media transport action.
    // =========================================================================

    @Override public void sendMediaPlayPause() { sendKey(KEY_PLAY_PAUSE); }
    @Override public void sendMediaStop()      { sendKey(KEY_STOP); }
    @Override public void sendMediaPrev()      { sendKey(KEY_PREV); }
    @Override public void sendMediaNext()      { sendKey(KEY_NEXT); }
    @Override public void sendMediaRW()        { sendKey(KEY_RW); }
    @Override public void sendMediaFF()        { sendKey(KEY_FF); }

    // =========================================================================
    // SECTION: DEVICE INFO
    // WHAT:  Asks the TV for details about itself — its model name, MAC
    //        address, and screen resolution. This runs once after connecting.
    //        The resolution is used to correctly map mouse movements to the
    //        TV's actual screen size (1920x1080 vs 3840x2160 for 4K TVs).
    // =========================================================================

    private void fetchDeviceInfo() {
        if (deviceInfoFetched) return;
        deviceInfoFetched = true;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://" + ip + ":8001/api/v2/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }

                    JSONObject json   = new JSONObject(response.toString());
                    JSONObject device = json.optJSONObject("device");
                    if (device != null) {
                        String modelName = device.optString("modelName", "Samsung TV");
                        String wifiMac   = device.optString("wifiMac", "");
                        String resolution = device.optString("resolution", "");
                        if (!resolution.isEmpty()) {
                            String[] parts = resolution.split("x");
                            if (parts.length == 2) {
                                try {
                                    tvWidth = Integer.parseInt(parts[0]);
                                    tvHeight = Integer.parseInt(parts[1]);
                                } catch (NumberFormatException e) {
                                    Log.d(TAG, "Failed to parse resolution", e);
                                }
                            }
                        }
                        Log.d(TAG, "Device info: model=" + modelName + " mac=" + wifiMac
                            + " resolution=" + tvWidth + "x" + tvHeight);
                        if (listener != null) listener.onDeviceInfo(modelName, wifiMac);
                    }
                } else {
                    Log.w(TAG, "fetchDeviceInfo HTTP " + conn.getResponseCode());
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchDeviceInfo error", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

}
