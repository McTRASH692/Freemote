package com.mctrash692.freemote.remote.samsung;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class SamsungWebSocket {
    private static final String TAG = "SamsungWebSocket";

    private WebSocketClient client;
    private final String ip;
    private final int port;
    // N4: volatile so reads from other threads see writes from the WS thread.
    private volatile boolean isConnected = false;
    private volatile boolean isPaired    = false;
    private ConnectionListener listener;
    private String pairingToken = null;

    // App name encoded the same way SamsungRemote does it.
    private static final String APP_NAME_B64 =
        Base64.encodeToString("Freemote".getBytes(), Base64.NO_WRAP);

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onPairingRequired(String token);
    }

    public SamsungWebSocket(String ip, int port, ConnectionListener listener) {
        this.ip       = ip;
        this.port     = port;
        this.listener = listener;
    }

    public void connect() {
        try {
            // Include app name so the TV can identify us — same pattern as SamsungRemote.
            URI uri = new URI("ws://" + ip + ":" + port
                + "/api/v2/channels/samsung.remote.control?name=" + APP_NAME_B64);

            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "WebSocket opened");
                    isConnected = true;
                    // FIX (bug 6): do NOT send KEY_POWER here; just wait for the TV's
                    // ms.channel.connect event.  If the TV needs a handshake message,
                    // send the options request instead.
                    sendOptionsRequest();
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "Received: " + message);
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket closed: " + reason);
                    isConnected = false;
                    isPaired    = false;
                    if (listener != null) listener.onDisconnected();
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);
                    if (listener != null) listener.onError(ex.getMessage());
                }
            };
            client.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid URI", e);
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    // Sends the channel-options handshake that lets the TV know we are a remote.
    // Does NOT send any key code.
    private void sendOptionsRequest() {
        if (client == null) return;
        try {
            JSONObject params = new JSONObject()
                .put("Cmd",          "ChannelEmitCommand")
                .put("CommandType",  "Register")
                .put("TypeOfRemote", "SendRemoteKey");

            JSONObject msg = new JSONObject()
                .put("method", "ms.channel.emit")
                .put("params", params);

            client.send(msg.toString());
        } catch (JSONException e) {
            Log.e(TAG, "sendOptionsRequest error", e);
        }
    }

    // N3: parse via JSONObject instead of fragile string-index arithmetic.
    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String event = json.optString("event");

            if ("ms.channel.connect".equals(event)) {
                JSONObject data = json.optJSONObject("data");
                if (data != null && data.has("token")) {
                    // TV issued a new pairing token.
                    pairingToken = data.getString("token");
                    Log.d(TAG, "Got pairing token: " + pairingToken);
                    if (listener != null) listener.onPairingRequired(pairingToken);
                    confirmPairing();
                } else {
                    // TV accepted us without requiring a new token — already paired.
                    isPaired = true;
                    if (listener != null) listener.onConnected();
                }
            } else if ("ms.remote.control".equals(event)) {
                Log.d(TAG, "Command accepted by TV");
            }
        } catch (JSONException e) {
            Log.e(TAG, "handleMessage parse error", e);
        }
    }

    public void sendCommand(String command) {
        if (client == null || !isConnected) {
            Log.w(TAG, "sendCommand: not connected");
            return;
        }
        try {
            JSONObject params = new JSONObject()
                .put("Cmd",          "Click")
                .put("DataOfCmd",    command)
                .put("Option",       "false")
                .put("TypeOfRemote", "SendRemoteKey");

            JSONObject msg = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);

            client.send(msg.toString());
            Log.d(TAG, "Sent: " + command);
        } catch (JSONException e) {
            Log.e(TAG, "sendCommand error", e);
        }
    }

    // FIX (bug N2): confirmPairing no longer embeds KEY_POWER; it sends only
    // the RegisterDevice / emit message that carries the token back.
    public void confirmPairing() {
        if (client == null || pairingToken == null) return;
        try {
            JSONObject params = new JSONObject()
                .put("Cmd",          "ChannelEmitCommand")
                .put("CommandType",  "Register")
                .put("TypeOfRemote", "SendRemoteKey")
                .put("token",        pairingToken);

            JSONObject msg = new JSONObject()
                .put("method", "ms.channel.emit")
                .put("params", params);

            client.send(msg.toString());
            isPaired = true;
            Log.d(TAG, "Pairing confirmed with token: " + pairingToken);
        } catch (JSONException e) {
            Log.e(TAG, "confirmPairing error", e);
        }
    }

    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    public boolean isConnected() { return isConnected; }
    public boolean isPaired()    { return isPaired; }
}
