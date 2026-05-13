package com.mctrash692.freemote.remote.samsung;

// ============================================================================
// FILE: SamsungWebSocket.java
// WHAT:  Handles the WebSocket connection to older Samsung Smart TVs.
//        Opens a WebSocket to the TV, performs a pairing handshake,
//        and sends remote-control commands (keys like POWER, VOLUME, HOME, etc.).
// ============================================================================

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;

public class SamsungWebSocket {

    // ==========================================================================
    // SECTION: CLASS DATA
    // WHAT:  These variables hold the TV connection details, the WebSocket
    //        client object, the connection/pairing state, and the listener
    //        that reports events back to the rest of the app.
    // ==========================================================================

    private static final String TAG = "SamsungWebSocket";

    /** The WebSocket client that talks to the TV over the network. */
    private volatile WebSocketClient client;
    /** The TV's IP address on the local network (e.g. "192.168.1.100"). */
    private final String ip;
    /** The port number the TV listens on (usually 8001 or 8002). */
    private final int port;
    // N4: volatile so reads from other threads see writes from the WS thread.
    /** true once the WebSocket connection is open and ready. */
    private volatile boolean isConnected = false;
    /** true once the remote has been authorized (paired) by the TV. */
    private volatile boolean isPaired    = false;
    /** The object that receives callbacks (connected, error, etc.). */
    private final ConnectionListener listener;
    /** The token the TV sends for pairing; null until it arrives. */
    private String pairingToken = null;

    // App name encoded the same way SamsungRemote does it.
    /** Base64-encoded app name ("Freemote") sent to the TV for identification. */
    private static final String APP_NAME_B64 =
        Base64.encodeToString("Freemote".getBytes(), Base64.NO_WRAP);

    // ==========================================================================
    // SECTION: CONNECTION LISTENER INTERFACE
    // WHAT:  Defines the callbacks that SamsungWebSocket uses to notify
    //        the rest of the app about connection events.
    // ==========================================================================

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onPairingRequired(String token);
    }

    // ==========================================================================
    // SECTION: CONSTRUCTOR
    // WHAT:  Stores the TV's IP address, port number, and a listener object.
    //        The actual connection does not happen here — call connect() next.
    // ==========================================================================

    public SamsungWebSocket(String ip, int port, ConnectionListener listener) {
        this.ip       = ip;
        this.port     = port;
        this.listener = listener;
    }

    // ==========================================================================
    // SECTION: WEBSOCKET LIFECYCLE
    // WHAT:  connect() opens the WebSocket to the TV and defines what happens
    //        when the connection opens, when a message arrives, when it closes,
    //        or when an error occurs.  sendOptionsRequest() sends the initial
    //        handshake.  handleMessage() processes replies from the TV.
    // ==========================================================================

    /**
     * Opens a WebSocket connection to the Samsung TV and starts listening
     * for messages from it.
     * Runs when the user selects this TV from the device list.
     * INPUT:  Nothing (uses IP and port from the constructor).
     * OUTPUT: Listener receives onConnected() when the WebSocket opens,
     *         or onError() if the connection fails.
     */
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
                    if (listener != null) listener.onError(ex.getMessage() != null ? ex.getMessage() : "WebSocket error");
                }
            };
            client.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid URI", e);
            if (listener != null) listener.onError(e.getMessage());
        }
    }

    /**
     * Sends an options-request handshake to the TV telling it "I am a remote".
     * Runs right after the WebSocket opens (called from onOpen).
     * INPUT:  Nothing (uses the open WebSocket client).
     * OUTPUT: Sends a JSON message over the WebSocket; no return value.
     */
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

    /**
     * Processes incoming messages from the TV over the WebSocket.
     * Runs every time the TV sends a WebSocket message (called from onMessage).
     * INPUT:  message — raw JSON string from the TV.
     * OUTPUT: If the TV sends a pairing token, it stores the token and calls
     *         confirmPairing().  If already paired, it marks the connection
     *         as ready and notifies the listener.
     */
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
                    if (listener != null) listener.onConnected();
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

    // ==========================================================================
    // SECTION: COMMAND SENDING
    // WHAT:  Sends a remote-control key press (e.g. POWER, VOLUME, HOME) to
    //        the TV over the WebSocket.  Supports both short clicks and
    //        long presses (hold-and-click).
    // ==========================================================================

    /**
     * Sends a short press of a remote-control key to the TV.
     * INPUT:  command — string name of the key (e.g. "KEY_POWER").
     * OUTPUT: Nothing — sends JSON over the WebSocket.
     */
    public void sendCommand(String command) {
        sendCommand(command, false);
    }

    /**
     * Sends a remote-control key press, optionally as a long press.
     * INPUT:  command  — string name of the key (e.g. "KEY_VOLUMEUP").
     *         longPress — true = hold the button down, false = short click.
     * OUTPUT: Nothing — sends JSON over the WebSocket.
     */
    public void sendCommand(String command, boolean longPress) {
        if (client == null || !isConnected) {
            Log.w(TAG, "sendCommand: not connected");
            return;
        }
        try {
            JSONObject params = new JSONObject()
                .put("Cmd",          longPress ? "Click&Hold" : "Click")
                .put("DataOfCmd",    command)
                .put("Option",       "false")
                .put("TypeOfRemote", "SendRemoteKey");

            JSONObject msg = new JSONObject()
                .put("method", "ms.remote.control")
                .put("params", params);

            client.send(msg.toString());
            Log.d(TAG, "Sent: " + command + (longPress ? " (long)" : ""));
        } catch (JSONException e) {
            Log.e(TAG, "sendCommand error", e);
        }
    }

    // ==========================================================================
    // SECTION: PAIRING
    // WHAT:  Completes the pairing process by sending the token back to the
    //        TV so it knows this remote is authorized.
    // ==========================================================================

    // FIX (bug N2): confirmPairing no longer embeds KEY_POWER; it sends only
    // the RegisterDevice / emit message that carries the token back.
    /**
     * Sends the pairing token back to the TV to finish authorization.
     * Runs after the TV sends a pairing token (in handleMessage).
     * INPUT:  Nothing — uses the pairingToken field set by handleMessage.
     * OUTPUT: Marks isPaired = true on success.
     */
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

    // ==========================================================================
    // SECTION: PUBLIC API
    // WHAT:  Simple methods that let other parts of the app close the
    //        connection or check whether it is connected / paired.
    // ==========================================================================

    /**
     * Closes the WebSocket connection to the TV.
     * INPUT:  Nothing.
     * OUTPUT: Connection is closed; listener receives onDisconnected().
     */
    public void disconnect() {
        if (client != null) {
            client.close();
        }
    }

    /** Reports whether the WebSocket connection is currently open. */
    public boolean isConnected() { return isConnected; }

    /** Reports whether the remote has been paired with the TV. */
    public boolean isPaired()    { return isPaired; }
}
