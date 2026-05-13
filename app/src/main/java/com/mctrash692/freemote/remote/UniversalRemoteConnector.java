package com.mctrash692.freemote.remote;

// ============================================================================
// FILE: UniversalRemoteConnector.java
// WHAT:  Tries every known TV brand protocol until it finds one that works.
//        Tests ports in order (Samsung, LG, Android TV, Roku, etc.) and
//        stops at the first successful connection.
// ============================================================================

import android.content.Context;
import android.util.Log;

import com.mctrash692.freemote.model.TvProtocol;
import com.mctrash692.freemote.remote.androidtv.AndroidTvRemote;
import com.mctrash692.freemote.remote.lg.LgRemote;
import com.mctrash692.freemote.remote.roku.RokuRemote;
import com.mctrash692.freemote.remote.panasonic.PanasonicRemote;
import com.mctrash692.freemote.remote.philips.PhilipsRemote;
import com.mctrash692.freemote.remote.samsung.SamsungRemote;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal connector that tries multiple protocols and ports to connect to any TV brand.
 *
 * Ports are tested in order; once a connection succeeds the search stops.
 * Samsung and LG use WebSocket; Android TV uses TLS; Roku/Philips/Panasonic use REST/HTTP.
 */
public class UniversalRemoteConnector {

    // ==========================================================================
    // SECTION: CLASS DATA
    // WHAT:  Stores the TV's IP, a saved pairing token, the Android app context,
    //        a listener for callbacks, and the list of all connection methods
    //        (protocol + port combinations) that will be tried one by one.
    // ==========================================================================

    /** Tag used for Android logging. */
    private static final String TAG = "UniversalRemoteConnector";
    /** Maximum time (ms) to wait when testing if a network port is open. */
    private static final int CONNECTION_TIMEOUT_MS = 2000;

    /** Android app context, needed for some protocol handlers (e.g. LG webOS). */
    private final Context context;
    /** The TV's IP address to try connecting to. */
    private final String ip;
    /** A saved pairing token from a previous session (may be null). */
    private final String savedToken;
    /** The listener that receives connection, error, and token callbacks. */
    private final RemoteController.Listener listener;

    /** The remote-control object that is currently being tried or is active. */
    private RemoteController activeRemote;
    /** The protocol that was successfully detected (Samsung, LG, Android TV, etc.). */
    private TvProtocol detectedProtocol;
    /** The connection method (port + type) that is currently being tried. */
    private TvProtocol.ConnectionMethod activeMethod;
    /** Index into allMethods - which method to try next. */
    private int currentMethodIndex = 0;
    /** The complete list of all connection methods to try, in order. */
    private final List<TvProtocol.ConnectionMethod> allMethods = new ArrayList<>();

    /**
     * Prepares the connector with the TV's details and a listener for callbacks.
     * INPUT:  context    - Android app context (needed for some protocols like LG).
     *         ip         - the TV's network IP address.
     *         savedToken - an optional pairing token from a prior session (can be null).
     *         listener   - object that receives onConnected / onError / etc. callbacks.
     * OUTPUT: Nothing yet; call connect() to start trying protocols.
     */
    public UniversalRemoteConnector(Context context, String ip, String savedToken,
                                     RemoteController.Listener listener) {
        this.context = context;
        this.ip = ip;
        this.savedToken = savedToken;
        this.listener = listener;
    }

    // ==========================================================================
    // SECTION: CONNECTION ORCHESTRATION
    // WHAT:  connect() builds the list of all possible connection methods and
    //        starts trying them.  tryNextMethod() picks the next one.
    //        testPortConnectivity() checks if a port is reachable before
    //        attempting a full connection.  attemptConnection() creates the
    //        correct protocol handler based on the port and connection type.
    // ==========================================================================

    /**
     * Starts the connection process by building a list of every known protocol
     * method and trying them one by one.
     * Runs when the user selects a discovered TV to connect to.
     * INPUT:  Nothing.
     * OUTPUT: Eventually calls listener.onConnected() on success or
     *         listener.onError() if no protocol worked.
     */
    public void connect() {
        // Build list of all connection methods from all known protocols
        for (TvProtocol protocol : TvProtocol.getAllProtocols()) {
            allMethods.addAll(protocol.getConnectionMethods());
        }

        currentMethodIndex = 0;
        tryNextMethod();
    }

    /**
     * Tries the next connection method in the list.  If all methods have
     * been exhausted without success, reports failure to the listener.
     * Called by connect() and after each failed attempt.
     * INPUT:  Nothing (reads currentMethodIndex and allMethods).
     * OUTPUT: Kicks off a port-connectivity test for the next method.
     */
    private void tryNextMethod() {
        if (currentMethodIndex >= allMethods.size()) {
            if (listener != null) {
                listener.onError("Failed to connect - no compatible protocol found");
            }
            return;
        }

        activeMethod = allMethods.get(currentMethodIndex);
        Log.d(TAG, "Trying method: port=" + activeMethod.getPort() +
              " type=" + activeMethod.getType() +
              " secure=" + activeMethod.isSecure());

        testPortConnectivity(ip, activeMethod.getPort(), success -> {
            if (success) {
                attemptConnection();
            } else {
                currentMethodIndex++;
                tryNextMethod();
            }
        });
    }

    /**
     * Tests whether a specific network port is open on the TV by trying to
     * open a TCP socket to it.  Runs in a background thread so it does not
     * block the app.
     * INPUT:  ip       - the TV's IP address.
     *         port     - the port number to test.
     *         callback - called with true if the port is open, false if not.
     * OUTPUT: Calls callback.onResult() with the test result.
     */
    private void testPortConnectivity(String ip, int port, PortTestCallback callback) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), CONNECTION_TIMEOUT_MS);
                callback.onResult(true);
            } catch (Exception e) {
                Log.d(TAG, "Port " + port + " not reachable: " + e.getMessage());
                callback.onResult(false);
            }
        }).start();
    }

    /**
     * Tries to connect using the current active method's port and connection type.
     * Based on the port number and type (WebSocket, TLS_SOCKET, HTTP_REST, etc.),
     * this method decides which protocol-specific connection handler to call.
     * INPUT:  Nothing (reads activeMethod).
     * OUTPUT: Calls the appropriate attemptXxxConnection() method, or advances
     *         to the next method if the port/type is not recognized.
     */
    private void attemptConnection() {
        int port = activeMethod.getPort();

        switch (activeMethod.getType()) {
            case WEBSOCKET:
                if (port == 8001 || port == 8002) {
                    attemptSamsungConnection();
                } else if (port == 3000 || port == 3001) {
                    attemptLgConnection();
                } else {
                    // Unknown WebSocket port - skip
                    currentMethodIndex++;
                    tryNextMethod();
                }
                break;

            case TLS_SOCKET:
                attemptAndroidTvConnection();
                break;

            case PLAIN_SOCKET:
                if (port == 55000) {
                    attemptPanasonicConnection();
                } else {
                    // Other plain socket protocols not yet implemented
                    currentMethodIndex++;
                    tryNextMethod();
                }
                break;

            case HTTP_REST:
                if (port == 8060) {
                    attemptRokuConnection();
                } else if (port == 1925 || port == 1926) {
                    attemptPhilipsConnection();
                } else if (port == 8001) {
                    // Samsung REST - skip, prefer WebSocket
                    currentMethodIndex++;
                    tryNextMethod();
                } else if (port == 8080) {
                    // Could be LG REST, Sony, Philips, etc. Skip for now.
                    currentMethodIndex++;
                    tryNextMethod();
                } else {
                    currentMethodIndex++;
                    tryNextMethod();
                }
                break;

            case ADB:
                // ADB not yet implemented in universal connector
                Log.d(TAG, "ADB connection not yet implemented");
                currentMethodIndex++;
                tryNextMethod();
                break;

            default:
                currentMethodIndex++;
                tryNextMethod();
        }
    }

    /**
     * Disconnects any previously-active remote before starting a new one.
     * Prevents stale connections from accumulating.
     * INPUT:  Nothing.
     * OUTPUT: The old remote (if any) is disconnected and set to null.
     */
    private void disconnectOldRemote() {
        if (activeRemote != null) {
            activeRemote.disconnect();
            activeRemote = null;
        }
    }

    // ==========================================================================
    // SECTION: SAMSUNG (WebSocket)
    // WHAT:  Attempts to connect using the Samsung SmartThings protocol over
    //        WebSocket (ports 8001 or 8002).
    // ==========================================================================

    /**
     * Creates a SamsungRemote and tells it to connect.
     * If the connection fails, the listener calls tryNextMethod() automatically.
     * INPUT:  Nothing (uses ip, savedToken, activeMethod from class data).
     * OUTPUT: Listener is notified of success or failure.
     */
    private void attemptSamsungConnection() {
        disconnectOldRemote();
        activeRemote = new SamsungRemote(ip, savedToken, new RemoteController.Listener() {
            @Override public void onConnected() {
                Log.d(TAG, "Connected via Samsung protocol on port " + activeMethod.getPort());
                detectedProtocol = TvProtocol.getSamsungProtocol();
                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected(String reason) {
                if (listener != null) {
                    currentMethodIndex++;
                    tryNextMethod();
                }
            }
            @Override public void onError(String message) {
                Log.w(TAG, "Samsung connection failed: " + message);
                currentMethodIndex++;
                tryNextMethod();
            }
            @Override public void onTokenReceived(String token) {
                if (listener != null) listener.onTokenReceived(token);
            }
            @Override public void onDeviceInfo(String modelName, String wifiMac) {
                if (listener != null) listener.onDeviceInfo(modelName, wifiMac);
            }
        });
        activeRemote.connect();
    }

    // ==========================================================================
    // SECTION: ANDROID TV (TLS Socket)
    // WHAT:  Attempts to connect using the Android TV / Google TV remote
    //        protocol over a TLS-encrypted socket (port 6466).
    // ==========================================================================

    /**
     * Creates an AndroidTvRemote and tells it to connect.
     * INPUT:  Nothing (uses ip from class data).
     * OUTPUT: Listener is notified of success or failure.
     */
    private void attemptAndroidTvConnection() {
        disconnectOldRemote();
        activeRemote = new AndroidTvRemote(ip, new RemoteController.Listener() {
            @Override public void onConnected() {
                Log.d(TAG, "Connected via Android TV protocol on port " + activeMethod.getPort());
                detectedProtocol = TvProtocol.getAndroidTvProtocol();
                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected(String reason) {
                if (listener != null) {
                    currentMethodIndex++;
                    tryNextMethod();
                }
            }
            @Override public void onError(String message) {
                Log.w(TAG, "Android TV connection failed: " + message);
                currentMethodIndex++;
                tryNextMethod();
            }
            @Override public void onTokenReceived(String token) {}
            @Override public void onDeviceInfo(String modelName, String wifiMac) {
                if (listener != null) listener.onDeviceInfo(modelName, wifiMac);
            }
        });
        activeRemote.connect();
    }

    // ==========================================================================
    // SECTION: LG webOS (WebSocket)
    // WHAT:  Attempts to connect using the LG webOS SSAP protocol over
    //        WebSocket (ports 3000 or 3001).  UNTESTED.
    // ==========================================================================

    /**
     * Creates an LgRemote and tells it to connect.
     * INPUT:  Nothing (uses context, ip from class data).
     * OUTPUT: Listener is notified of success or failure.
     */
    private void attemptLgConnection() {
        disconnectOldRemote();
        activeRemote = new LgRemote(context, ip, new RemoteController.Listener() {
            @Override public void onConnected() {
                Log.d(TAG, "Connected via LG protocol on port " + activeMethod.getPort());
                detectedProtocol = TvProtocol.getLgProtocol();
                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected(String reason) {
                if (listener != null) {
                    currentMethodIndex++;
                    tryNextMethod();
                }
            }
            @Override public void onError(String message) {
                Log.w(TAG, "LG connection failed: " + message);
                currentMethodIndex++;
                tryNextMethod();
            }
            @Override public void onTokenReceived(String token) {}
            @Override public void onDeviceInfo(String modelName, String wifiMac) {
                if (listener != null) listener.onDeviceInfo(modelName, wifiMac);
            }
        });
        activeRemote.connect();
    }

    // ==========================================================================
    // SECTION: ROKU (HTTP REST)
    // WHAT:  Attempts to connect using the Roku ECP (External Control Protocol)
    //        over HTTP REST (port 8060).  UNTESTED.
    // ==========================================================================

    /**
     * Creates a RokuRemote and tells it to connect.
     * INPUT:  Nothing (uses ip from class data).
     * OUTPUT: Listener is notified of success or failure.
     */
    private void attemptRokuConnection() {
        disconnectOldRemote();
        activeRemote = new RokuRemote(ip, new RemoteController.Listener() {
                @Override public void onConnected() {
                                Log.d(TAG, "Connected via Roku ECP on port " + activeMethod.getPort());
                                detectedProtocol = TvProtocol.getRokuProtocol();
                                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected(String reason) {
                if (listener != null) {
                    currentMethodIndex++;
                    tryNextMethod();
                }
            }
            @Override public void onError(String message) {
                Log.w(TAG, "Roku connection failed: " + message);
                currentMethodIndex++;
                tryNextMethod();
            }
            @Override public void onTokenReceived(String token) {}
            @Override public void onDeviceInfo(String modelName, String wifiMac) {}
        });
        activeRemote.connect();
    }

    // ==========================================================================
    // SECTION: PANASONIC (Plain Socket / SOAP)
    // WHAT:  Attempts to connect using the Panasonic Viera SOAP protocol over
    //        a plain TCP socket (port 55000).  UNTESTED.
    // ==========================================================================

    /**
     * Creates a PanasonicRemote and tells it to connect.
     * INPUT:  Nothing (uses ip from class data).
     * OUTPUT: Listener is notified of success or failure.
     */
    private void attemptPanasonicConnection() {
        disconnectOldRemote();
        activeRemote = new PanasonicRemote(ip, new RemoteController.Listener() {
            @Override public void onConnected() {
                Log.d(TAG, "Connected via Panasonic SOAP on port " + activeMethod.getPort());
                detectedProtocol = TvProtocol.getPanasonicProtocol();
                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected(String reason) {
                if (listener != null) {
                    currentMethodIndex++;
                    tryNextMethod();
                }
            }
            @Override public void onError(String message) {
                Log.w(TAG, "Panasonic connection failed: " + message);
                currentMethodIndex++;
                tryNextMethod();
            }
            @Override public void onTokenReceived(String token) {}
            @Override public void onDeviceInfo(String modelName, String wifiMac) {}
        });
        activeRemote.connect();
    }

    // ==========================================================================
    // SECTION: PHILIPS (HTTP REST)
    // WHAT:  Attempts to connect using the Philips jointSPACE protocol over
    //        HTTP REST (ports 1925 or 1926).  UNTESTED.
    // ==========================================================================

    /**
     * Creates a PhilipsRemote and tells it to connect.
     * INPUT:  Nothing (uses ip from class data).
     * OUTPUT: Listener is notified of success or failure.
     */
    private void attemptPhilipsConnection() {
        disconnectOldRemote();
        activeRemote = new PhilipsRemote(ip, new RemoteController.Listener() {
            @Override public void onConnected() {
                Log.d(TAG, "Connected via Philips jointSPACE on port " + activeMethod.getPort());
                detectedProtocol = TvProtocol.getPhilipsProtocol();
                if (listener != null) listener.onConnected();
            }
            @Override public void onDisconnected(String reason) {
                if (listener != null) {
                    currentMethodIndex++;
                    tryNextMethod();
                }
            }
            @Override public void onError(String message) {
                Log.w(TAG, "Philips connection failed: " + message);
                currentMethodIndex++;
                tryNextMethod();
            }
            @Override public void onTokenReceived(String token) {}
            @Override public void onDeviceInfo(String modelName, String wifiMac) {}
        });
        activeRemote.connect();
    }

    // ==========================================================================
    // SECTION: PUBLIC API
    // WHAT:  Methods that other parts of the app call to disconnect from the
    //        TV, check connection status, or retrieve the active remote.
    // ==========================================================================

    /**
     * Disconnects from the TV (if currently connected).
     * INPUT:  Nothing.
     * OUTPUT: The active remote is disconnected.
     */
    public void disconnect() {
        if (activeRemote != null) {
            activeRemote.disconnect();
        }
    }

    /** Reports whether a remote is currently connected to the TV. */
    public boolean isConnected() {
        return activeRemote != null && activeRemote.isConnected();
    }

    /** Returns the active remote controller object, or null if not connected. */
    public RemoteController getActiveRemote() {
        return activeRemote;
    }

    /** Returns which TV protocol was detected (Samsung, Android TV, LG, etc.). */
    public TvProtocol getDetectedProtocol() {
        return detectedProtocol;
    }

    /** Returns the connection method (port + type) that is currently active. */
    public TvProtocol.ConnectionMethod getActiveMethod() {
        return activeMethod;
    }

    /** Callback interface for port-connectivity test results. */
    private interface PortTestCallback {
        /** Called with true if the port is open, false otherwise. */
        void onResult(boolean success);
    }
}
