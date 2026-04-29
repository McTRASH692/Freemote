package com.mctrash692.freemote.remote.samsung;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class SamsungRemote {
    private static final String TAG = "SamsungRemote";
    private static final int PORT_WSS = 8002;
    private static final int PORT_WS = 8001;
    private static final String APP_NAME_B64 =
        Base64.encodeToString("Freemote".getBytes(), Base64.NO_WRAP);

    public static final String APP_NETFLIX = "11101200001";
    public static final String APP_YOUTUBE = "111299001912";
    public static final String APP_PRIME = "3201910019365";
    public static final String APP_DISNEY = "3201901017640";
    public static final String APP_HULU = "3201601007625";
    public static final String APP_HBOMAX = "3201601007230";
    public static final String APP_PLEX = "3201512006963";
    public static final String APP_KODI = "org.xbmc.kodi";
    public static final String APP_APPLETV = "3201807016597";
    public static final String APP_CAST = "3202203026841";

    public interface Listener {
        void onConnected();
        void onTokenReceived(String token);
        void onDisconnected(String reason);
        void onError(String message);
    }

    private final String ip;
    private final String savedToken;
    private final Listener listener;
    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean connected = false;

    public SamsungRemote(String ip, String savedToken, Listener listener) {
        this.ip = ip;
        this.savedToken = savedToken;
        this.listener = listener;
    }

    public void connect() {
        connectWithPort(PORT_WSS, true);
    }

    private void connectWithPort(int port, boolean secure) {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS);

            if (secure) {
                SSLContext sslContext = buildPermissiveSslContext();
                builder.sslSocketFactory(sslContext.getSocketFactory(), buildPermissiveTrustManager())
                       .hostnameVerifier((hostname, session) -> true);
            }

            client = builder.build();
            String scheme = secure ? "wss" : "ws";
            StringBuilder url = new StringBuilder()
                .append(scheme).append("://").append(ip).append(":").append(port)
                .append("/api/v2/channels/samsung.remote.control")
                .append("?name=").append(APP_NAME_B64);

            if (savedToken != null && !savedToken.isEmpty()) {
                url.append("&token=").append(savedToken);
            }

            Log.d(TAG, "Connecting to: " + url);

            Request request = new Request.Builder().url(url.toString()).build();

            client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket ws, Response response) {
                    webSocket = ws;
                    connected = true;
                    Log.d(TAG, "Connected on port " + port);
                    if (listener != null) {
                        listener.onConnected();
                    }
                }

                @Override
                public void onMessage(WebSocket ws, String text) {
                    Log.d(TAG, "Message: " + text);
                    handleMessage(text);
                }

                @Override
                public void onFailure(WebSocket ws, Throwable t, Response response) {
                    connected = false;
                    Log.e(TAG, "Failure on port " + port + ": " + t.getMessage());
                    if (secure && listener != null) {
                        connectWithPort(PORT_WS, false);
                    } else if (listener != null) {
                        listener.onError("Connection failed: " + t.getMessage());
                    }
                }

                @Override
                public void onClosed(WebSocket ws, int code, String reason) {
                    connected = false;
                    if (listener != null) {
                        listener.onDisconnected(reason);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Connect exception", e);
            if (secure && listener != null) {
                connectWithPort(PORT_WS, false);
            } else if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    public void disconnect() {
        connected = false;
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
        }
    }

    public boolean isConnected() { return connected; }

    public void sendKey(String keyCode) {
        if (!connected || webSocket == null) {
            Log.w(TAG, "sendKey: not connected");
            return;
        }

        String mappedKey = keyCode;
        if (keyCode.equals("KEY_VOLUMEUP")) mappedKey = "KEY_VOLUP";
        if (keyCode.equals("KEY_VOLUMEDOWN")) mappedKey = "KEY_VOLDOWN";

        try {
            JSONObject params = new JSONObject()
                .put("Cmd", "Click")
                .put("DataOfCmd", mappedKey)
                .put("Option", "false")
                .put("TypeOfRemote", "SendRemoteKey");

            JSONObject message = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);

            webSocket.send(message.toString());
            Log.d(TAG, "Sent key: " + mappedKey);
        } catch (JSONException e) {
            Log.e(TAG, "sendKey error", e);
        }
    }

    public void sendAppLaunch(String appId) {
        new Thread(() -> {
            try {
                URL url = new URL("http://" + ip + ":8001/api/v2/applications/" + appId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                Log.d(TAG, "App launch response: " + responseCode);
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "REST app launch error", e);
            }
        }).start();
    }

    public void sendInputString(String text) {
        if (!connected || webSocket == null) {
            Log.w(TAG, "sendInputString: not connected");
            return;
        }

        try {
            JSONObject params = new JSONObject()
                .put("Cmd", text)
                .put("TypeOfRemote", "SendInputString");

            JSONObject message = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);

            webSocket.send(message.toString());
            Log.d(TAG, "Sent input string: " + text);
        } catch (JSONException e) {
            Log.e(TAG, "sendInputString error", e);
        }
    }

    public void showKeyboard() {
        sendKey("KEY_ENTER");
    }

    private void handleMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String event = json.optString("event");
            if ("ms.channel.connect".equals(event)) {
                JSONObject data = json.optJSONObject("data");
                if (data != null && data.has("token") && listener != null) {
                    String token = data.getString("token");
                    listener.onTokenReceived(token);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleMessage error", e);
        }
    }

    private SSLContext buildPermissiveSslContext() throws Exception {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{buildPermissiveTrustManager()}, new java.security.SecureRandom());
        return ctx;
    }

    private X509TrustManager buildPermissiveTrustManager() {
        return new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }
}
