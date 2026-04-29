package com.mctrash692.freemote.remote.samsung;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class SamsungWebSocket {
    private static final String TAG = "SamsungWebSocket";
    private WebSocketClient client;
    private String ip;
    private int port;
    private boolean isConnected = false;
    private boolean isPaired = false;
    private ConnectionListener listener;
    private String pairingToken = null;

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onPairingRequired(String token);
    }

    public SamsungWebSocket(String ip, int port, ConnectionListener listener) {
        this.ip = ip;
        this.port = port;
        this.listener = listener;
    }

    public void connect() {
        try {
            URI uri = new URI("ws://" + ip + ":" + port + "/api/v2/channels/samsung.remote.control");
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Log.d(TAG, "WebSocket opened");
                    isConnected = true;
                    sendPairingRequest();
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
                    isPaired = false;
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

    private void sendPairingRequest() {
        String request = "{\"method\":\"ms.remote.control\",\"params\":{\"Cmd\":\"Click\",\"DataOfCmd\":\"KEY_POWER\"}}";
        client.send(request);
    }

    private void handleMessage(String message) {
        if (message.contains("pairingToken")) {
            int start = message.indexOf("\"pairingToken\":\"") + 16;
            int end = message.indexOf("\"", start);
            if (start > 15 && end > start) {
                pairingToken = message.substring(start, end);
                if (listener != null) listener.onPairingRequired(pairingToken);
                confirmPairing();
            }
        } else if (message.contains("\"event\":\"ms.channel.connect\"")) {
            // TV acknowledged connection without a pairing token
            isPaired = true;
            if (listener != null) listener.onConnected();
        } else if (message.contains("\"event\":\"ms.remote.control\"")) {
            Log.d(TAG, "Command accepted by TV");
        }
    }

    public void sendCommand(String command) {
        if (client != null && isConnected) {
            String jsonCommand = "{\"method\":\"ms.remote.control\",\"params\":{\"Cmd\":\"Click\",\"DataOfCmd\":\"" + command + "\"}}";
            client.send(jsonCommand);
            Log.d(TAG, "Sent: " + command);
        }
    }

    public void confirmPairing() {
        if (client != null && pairingToken != null) {
            String confirm = "{\"method\":\"ms.remote.control\",\"params\":{\"Cmd\":\"Click\",\"DataOfCmd\":\"KEY_POWER\",\"pairingToken\":\"" + pairingToken + "\"}}";
            client.send(confirm);
            isPaired = true;
        }
    }

    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
