package com.mctrash692.freemote.remote.samsung;

import android.util.Base64;
import android.util.Log;

import com.mctrash692.freemote.remote.RemoteController;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SamsungRemote implements RemoteController {

    private static final String TAG = "SamsungRemote";
    private static final int PORT_WSS = 8002;
    private static final int PORT_WS = 8001;
    private static final String APP_NAME = "Freemote";
    private static final String APP_NAME_B64 = Base64.encodeToString(APP_NAME.getBytes(), Base64.NO_WRAP);

    // Verified App IDs for Samsung Tizen TVs (March 2026)
    public static final String APP_YOUTUBE = "111299001912";
    public static final String APP_PRIME = "3201512006785";
    public static final String APP_DISNEY = "3201901017640";
    public static final String APP_NETFLIX = "11101200001";
    public static final String APP_HBOMAX = "3202301029760";
    public static final String APP_PLEX = "3201512006963";
    public static final String APP_KODI = "org.xbmc.kodi";
    public static final String APP_APPLETV = "3201807016597";
    public static final String APP_SPOTIFY = "3201606009684";

    private enum ConnectionMethod {
        OKHTTP,      // Primary - OkHttp WebSocket
        LEGACY_WS,   // Fallback 1 - SamsungWebSocket
        REST_API     // Fallback 2 - HTTP REST calls only
    }

    private final String ip;
    private final String savedToken;
    private final Listener listener;
    
    private OkHttpClient okHttpClient;
    private WebSocket okHttpWebSocket;
    private SamsungWebSocket legacyWebSocket;
    private String currentToken;
    private ConnectionMethod activeMethod = null;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private boolean deviceInfoFetched = false;

    public SamsungRemote(String ip, String savedToken, Listener listener) {
        this.ip = ip;
        this.savedToken = savedToken;
        this.listener = listener;
        this.currentToken = savedToken;
    }

    @Override
    public void connect() {
        connectWithOkHttp(PORT_WSS, true);
    }

    private void connectWithOkHttp(int port, boolean secure) {
        activeMethod = ConnectionMethod.OKHTTP;
        
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS);

            if (secure) {
                SSLContext sslContext = buildPermissiveSslContext();
                builder.sslSocketFactory(sslContext.getSocketFactory(), buildPermissiveTrustManager())
                       .hostnameVerifier((hostname, session) -> true);
            }

            this.okHttpClient = builder.build();

            String scheme = secure ? "wss" : "ws";
            StringBuilder url = new StringBuilder()
                .append(scheme).append("://").append(ip).append(":").append(port)
                .append("/api/v2/channels/samsung.remote.control")
                .append("?name=").append(APP_NAME_B64);

            if (currentToken != null && !currentToken.isEmpty()) {
                url.append("&token=").append(currentToken);
            }

            Log.d(TAG, "OkHttp connecting to: " + url);
            Request request = new Request.Builder().url(url.toString()).build();

            this.okHttpClient.newWebSocket(request, new OkHttpWebSocketListener(port, secure));

        } catch (Exception e) {
            Log.e(TAG, "OkHttp connect exception", e);
            fallbackToLegacyWebSocket();
        }
    }

    private class OkHttpWebSocketListener extends WebSocketListener {
        private final int port;
        private final boolean secure;
        
        OkHttpWebSocketListener(int port, boolean secure) {
            this.port = port;
            this.secure = secure;
        }
        
        @Override
        public void onOpen(WebSocket ws, Response response) {
            okHttpWebSocket = ws;
            connected.set(true);
            Log.d(TAG, "OkHttp connected on port " + port);
            if (listener != null) {
                listener.onConnected();
                // Fetch device info after connection
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
            Log.e(TAG, "OkHttp failure on port " + port + ": " + t.getMessage());
            if (secure) {
                connectWithOkHttp(PORT_WS, false);
            } else {
                fallbackToLegacyWebSocket();
            }
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            connected.set(false);
            if (listener != null) listener.onDisconnected(reason);
        }
    }

    private void fallbackToLegacyWebSocket() {
        Log.w(TAG, "Falling back to SamsungWebSocket");
        activeMethod = ConnectionMethod.LEGACY_WS;
        
        legacyWebSocket = new SamsungWebSocket(ip, PORT_WS, new SamsungWebSocket.ConnectionListener() {
            @Override
            public void onConnected() {
                connected.set(true);
                if (listener != null) listener.onConnected();
                fetchDeviceInfo();
            }

            @Override
            public void onDisconnected() {
                connected.set(false);
                fallbackToRestApi();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Legacy WebSocket error: " + error);
                fallbackToRestApi();
            }

            @Override
            public void onPairingRequired(String token) {
                Log.d(TAG, "Legacy WebSocket pairing token: " + token);
                if (listener != null) listener.onTokenReceived(token);
            }
        });
        
        legacyWebSocket.connect();
    }

    private void fallbackToRestApi() {
        Log.w(TAG, "Falling back to REST API only");
        activeMethod = ConnectionMethod.REST_API;
        connected.set(true);
        if (listener != null) listener.onConnected();
        fetchDeviceInfo();
    }

    private void handleMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String event = json.optString("event");
            if ("ms.channel.connect".equals(event)) {
                JSONObject data = json.optJSONObject("data");
                if (data != null && data.has("token")) {
                    currentToken = data.getString("token");
                    Log.d(TAG, "Got token: " + currentToken);
                    if (listener != null) listener.onTokenReceived(currentToken);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleMessage error", e);
        }
    }

    @Override
    public void disconnect() {
        connected.set(false);
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

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    @Override
    public void sendKey(int keyCode, boolean longPress) {
        sendKey(String.valueOf(keyCode), longPress);
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
        
        String mappedKey = keyCode;
        if ("KEY_VOLUMEUP".equals(keyCode)) mappedKey = "KEY_VOLUP";
        if ("KEY_VOLUMEDOWN".equals(keyCode)) mappedKey = "KEY_VOLDOWN";
        
        switch (activeMethod) {
            case OKHTTP:
                sendKeyViaOkHttp(mappedKey, longPress);
                break;
            case LEGACY_WS:
                if (legacyWebSocket != null && legacyWebSocket.isConnected()) {
                    legacyWebSocket.sendCommand(mappedKey);
                }
                break;
            case REST_API:
                Log.w(TAG, "REST API fallback - key " + mappedKey + " not sent");
                break;
        }
    }

    private void sendKeyViaOkHttp(String mappedKey, boolean longPress) {
        if (okHttpWebSocket == null) return;
        try {
            String pressType = longPress ? "Click&Hold" : "Click";
            JSONObject params = new JSONObject()
                .put("Cmd", pressType)
                .put("DataOfCmd", mappedKey)
                .put("Option", "false")
                .put("TypeOfRemote", "SendRemoteKey");
            JSONObject message = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);
            okHttpWebSocket.send(message.toString());
            Log.d(TAG, "Sent key via OkHttp: " + mappedKey + (longPress ? " (long press)" : ""));
        } catch (JSONException e) {
            Log.e(TAG, "sendKeyViaOkHttp error", e);
        }
    }

    @Override
    public void sendText(String text) {
        sendText(text, false);
    }

    @Override
    public void sendText(String text, boolean replaceAll) {
        if (!connected.get()) {
            Log.w(TAG, "sendText: not connected");
            return;
        }
        
        switch (activeMethod) {
            case OKHTTP:
                sendTextViaOkHttp(text);
                break;
            case LEGACY_WS:
                if (legacyWebSocket != null && legacyWebSocket.isConnected()) {
                    sendKeyViaOkHttp("KEY_ENTER", false);
                }
                break;
            case REST_API:
                Log.w(TAG, "REST API fallback - text not sent");
                break;
        }
    }

    private void sendTextViaOkHttp(String text) {
        if (okHttpWebSocket == null) return;
        try {
            JSONObject params = new JSONObject()
                .put("Cmd", "Input")
                .put("DataOfCmd", text)
                .put("Option", "false")
                .put("TypeOfRemote", "SendInputString");
            JSONObject message = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);
            okHttpWebSocket.send(message.toString());
            Log.d(TAG, "Sent text via OkHttp: " + text);
        } catch (JSONException e) {
            Log.e(TAG, "sendTextViaOkHttp error", e);
        }
    }

    @Override
    public void sendMouseMove(int dx, int dy) {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP || okHttpWebSocket == null) return;
        try {
            JSONObject params = new JSONObject()
                .put("Cmd", "Move")
                .put("DataOfCmd", "x:" + dx + ",y:" + dy)
                .put("Option", "true")
                .put("TypeOfRemote", "SendMouse");
            JSONObject message = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);
            okHttpWebSocket.send(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseMove error", e);
        }
    }

    @Override
    public void sendMouseClick(String button) {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP || okHttpWebSocket == null) return;
        try {
            JSONObject pressParams = new JSONObject()
                .put("Cmd", "Press")
                .put("DataOfCmd", button)
                .put("Option", "false")
                .put("TypeOfRemote", "SendMouse");
            okHttpWebSocket.send(new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", pressParams).toString());

            JSONObject releaseParams = new JSONObject()
                .put("Cmd", "Release")
                .put("DataOfCmd", button)
                .put("Option", "false")
                .put("TypeOfRemote", "SendMouse");
            okHttpWebSocket.send(new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", releaseParams).toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseClick error", e);
        }
    }

    @Override
    public void sendMouseWheel(int deltaY) {
        if (!connected.get() || activeMethod != ConnectionMethod.OKHTTP || okHttpWebSocket == null) return;
        try {
            JSONObject params = new JSONObject()
                .put("Cmd", "Scroll")
                .put("DataOfCmd", "y:" + deltaY)
                .put("Option", "false")
                .put("TypeOfRemote", "SendMouse");
            JSONObject message = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);
            okHttpWebSocket.send(message.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendMouseWheel error", e);
        }
    }

    @Override
    public void sendMouseActivate() {
        sendMouseMove(0, 0);
    }

    @Override
    public void sendAppLaunch(String appId) {
        Log.d(TAG, "Launching app: " + appId);
        new Thread(() -> {
            try {
                String urlStr = "http://" + ip + ":8001/api/v2/applications/" + appId;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.connect();
                Log.d(TAG, "App launch response: " + conn.getResponseCode());
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "App launch error", e);
            }
        }).start();
    }

    @Override
    public void sendVolumeUp() {
        sendKey("KEY_VOLUMEUP");
    }

    @Override
    public void sendVolumeDown() {
        sendKey("KEY_VOLUMEDOWN");
    }

    @Override
    public void sendMute() {
        sendKey("KEY_MUTE");
    }

    @Override
    public void sendHome() {
        sendKey("KEY_HOME");
    }

    @Override
    public void sendBack() {
        sendKey("KEY_RETURN");
    }

    @Override
    public void sendPower() {
        sendKey("KEY_POWER");
    }

    private void fetchDeviceInfo() {
        if (deviceInfoFetched) return;
        deviceInfoFetched = true;

        new Thread(() -> {
            try {
                String urlStr = "http://" + ip + ":8001/api/v2/";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    JSONObject device = json.optJSONObject("device");
                    if (device != null) {
                        String modelName = device.optString("modelName", "Samsung TV");
                        String wifiMac = device.optString("wifiMac", "");
                        Log.d(TAG, "Device info: model=" + modelName + ", mac=" + wifiMac);
                        if (listener != null) {
                            listener.onDeviceInfo(modelName, wifiMac);
                        }
                    }
                } else {
                    Log.w(TAG, "Device info fetch failed, response code: " + conn.getResponseCode());
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "fetchDeviceInfo error", e);
            }
        }).start();
    }

    private SSLContext buildPermissiveSslContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{buildPermissiveTrustManager()}, new java.security.SecureRandom());
        return ctx;
    }

    private X509TrustManager buildPermissiveTrustManager() {
        return new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }
}
