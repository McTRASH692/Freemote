package com.mctrash692.freemote.util;

// ============================================================================
// FILE: NsdHelper.java
// WHAT:  Discovers TVs on your network using a feature called NSD (Network
//        Service Discovery). It listens for TVs that are broadcasting their
//        presence through special service names like "_samsungtv._tcp".
//        When a TV is found, it gets the TV's name, IP, and port.
// WHY:   This is another automatic discovery method. Some TVs advertise
//        themselves via NSD, and this file picks up those announcements so
//        you don't have to type in the IP address.
// ============================================================================

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.mctrash692.freemote.model.TvDevice;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

public class NsdHelper {

    // ==========================================================================
    // CONSTANTS
    // WHAT:  The network service names that different TV brands use to
    //        advertise themselves. These are like radio station names that
    //        TVs broadcast on so phones can find them.
    // ==========================================================================

    private static final String TAG = "NsdHelper";
    
    // Samsung TVs advertise under any of these service types
    private static final String[] SAMSUNG_SERVICE_TYPES = {
        "_samsungsmarthome._tcp.",    // Samsung Smart Home service
        "_samsungtv._tcp.",            // Samsung TV service
        "_samsung._tcp.",              // Generic Samsung service
        "_discovery._tcp.",            // Discovery service
        "_tv._tcp.",                   // Generic TV service
        "_samsungd2d._tcp.",           // Samsung device-to-device
        "_sercomm._tcp."               // Sercomm (manufacturer) service
    };
    
    // Android TV / Google TV uses this service type
    private static final String SERVICE_ANDROIDTV = "_androidtvremote2._tcp.";

    // ==========================================================================
    // INSTANCE VARIABLES
    // ==========================================================================

    private final NsdManager nsdManager;                    // Android's built-in network discovery manager
    private final Consumer<TvDevice> onDeviceFound;          // Callback for when a TV is found
    private final Map<String, NsdManager.DiscoveryListener> samsungListeners = new HashMap<>();  // One listener per service type
    private final Set<String> discoveredDeviceKeys = new HashSet<>();  // Already-found TVs (by IP:port)
    private final Map<String, String> serviceNameToKey = new HashMap<>();  // Service name -> device key

    // ==========================================================================
    // INNER CLASS: ResolveEntry
    // WHAT:  A temporary holder for a TV that needs its details looked up.
    //        When a TV is first found, we only have its service info; we
    //        must "resolve" it to get the IP address and port.
    // ==========================================================================

    private static final class ResolveEntry {
        final NsdServiceInfo info;    // The service info from discovery
        final TvDevice.Type type;      // What kind of TV (Samsung, Android TV, etc.)
        ResolveEntry(NsdServiceInfo info, TvDevice.Type type) {
            this.info = info;
            this.type = type;
        }
    }

    // A queue of TVs waiting to be resolved (looked up for IP/port)
    private final Queue<ResolveEntry> resolveQueue = new ArrayDeque<>();
    private boolean resolving = false;  // Whether we are currently resolving a TV

    private NsdManager.DiscoveryListener androidTvListener;  // The listener for Android TV discoveries

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Sets up the NSD discovery system. Gets access to Android's
    //        network discovery service.
    // INPUT: context      = the app (needed to access system services)
    //        onDeviceFound = a function to call when a TV is discovered
    // ==========================================================================

    public NsdHelper(Context context, Consumer<TvDevice> onDeviceFound) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.onDeviceFound = onDeviceFound;
    }

    // ==========================================================================
    // METHOD: startDiscovery
    // WHAT:  Starts listening for TV announcements on all known service
    //        types. It creates listeners for Samsung TV services and
    //        Android TV services simultaneously.
    // ==========================================================================

    public void startDiscovery() {
        for (String serviceType : SAMSUNG_SERVICE_TYPES) {
            NsdManager.DiscoveryListener listener = buildListener(TvDevice.Type.SAMSUNG);
            samsungListeners.put(serviceType, listener);
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        }

        androidTvListener = buildListener(TvDevice.Type.ANDROID_TV);
        nsdManager.discoverServices(SERVICE_ANDROIDTV, NsdManager.PROTOCOL_DNS_SD, androidTvListener);
    }

    // ==========================================================================
    // METHOD: stopDiscovery
    // WHAT:  Stops listening for TV announcements. Unregisters all listeners
    //        and clears the list of already-discovered TVs so a fresh scan
    //        can start clean next time.
    // ==========================================================================

    public void stopDiscovery() {
        for (NsdManager.DiscoveryListener listener : samsungListeners.values()) {
            try { nsdManager.stopServiceDiscovery(listener); } catch (Exception ignored) {}
        }
        samsungListeners.clear();

        try {
            if (androidTvListener != null) {
                nsdManager.stopServiceDiscovery(androidTvListener);
            }
        } catch (Exception ignored) {}

        synchronized (resolveQueue) {
            resolveQueue.clear();
            resolving = false;
        }
        discoveredDeviceKeys.clear();
        serviceNameToKey.clear();
    }

    // ==========================================================================
    // METHOD: buildListener (private)
    // WHAT:  Creates a listener that reacts when a TV is found or lost on
    //        the network. When a TV is found, it queues it for resolution
    //        (looking up its IP address and port).
    // INPUT: type = what kind of TV this listener is for (Samsung, Android TV)
    // OUTPUT: a discovery listener object ready to use
    // ==========================================================================

    private NsdManager.DiscoveryListener buildListener(TvDevice.Type type) {
        return new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Discovery started: " + serviceType);
            }

            @Override public void onServiceFound(NsdServiceInfo info) {
                Log.d(TAG, "Service found: " + info.getServiceName());
                enqueueResolve(info, type);
            }

            @Override public void onServiceLost(NsdServiceInfo info) {
                String lostKey = serviceNameToKey.remove(info.getServiceName());
                if (lostKey != null) {
                    discoveredDeviceKeys.remove(lostKey);
                    Log.d(TAG, "Service lost, removed: " + lostKey);
                } else {
                    Log.d(TAG, "Service lost (not yet resolved, ignoring): " + info.getServiceName());
                }
            }

            @Override public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped: " + serviceType);
            }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Start discovery failed: " + serviceType + " error=" + errorCode);
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: " + serviceType + " error=" + errorCode);
            }
        };
    }

    // ==========================================================================
    // METHOD: enqueueResolve (private)
    // WHAT:  Adds a newly found TV to the queue of devices waiting to have
    //        their details looked up. If nothing is currently being resolved,
    //        it starts processing the queue.
    // INPUT: info = the service info from the discovery
    //        type = what kind of TV this is
    // ==========================================================================

    private void enqueueResolve(NsdServiceInfo info, TvDevice.Type type) {
        synchronized (resolveQueue) {
            resolveQueue.add(new ResolveEntry(info, type));
            if (!resolving) {
                drainQueue();
            }
        }
    }

    // ==========================================================================
    // METHOD: drainQueue (private)
    // WHAT:  Processes the next TV in the resolution queue. It asks Android's
    //        NSD system to look up the TV's IP address and port. Once that
    //        is done, it reports the TV to the app and moves to the next one
    //        in the queue.
    // ==========================================================================

    private void drainQueue() {
        synchronized (resolveQueue) {
            final ResolveEntry entry = resolveQueue.poll();
            if (entry == null) {
                resolving = false;
                return;
            }
            resolving = true;

            // Capture values as final locals for use in inner class
            final NsdServiceInfo infoToResolve = entry.info;
            final TvDevice.Type typeToUse = entry.type;

            nsdManager.resolveService(infoToResolve, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    Log.e(TAG, "Resolve failed: " + info.getServiceName() + " error=" + errorCode);
                    drainQueue();
                }

                @Override public void onServiceResolved(NsdServiceInfo info) {
                    if (info.getHost() == null) {
                        Log.w(TAG, "Resolved host is null for: " + info.getServiceName());
                        drainQueue();
                        return;
                    }
                    String ip = info.getHost().getHostAddress();
                    if (ip == null) {
                        Log.w(TAG, "IP address is null for: " + info.getServiceName());
                        drainQueue();
                        return;
                    }
                    int port = info.getPort();
                    String name = info.getServiceName();
                    String uniqueKey = ip + ":" + port;

                    synchronized (resolveQueue) {
                        if (!discoveredDeviceKeys.contains(uniqueKey)) {
                            discoveredDeviceKeys.add(uniqueKey);
                            serviceNameToKey.put(name, uniqueKey);
                            Log.d(TAG, "Resolved: " + name + " @ " + ip + ":" + port);
                            onDeviceFound.accept(new TvDevice(name, ip, port, typeToUse));
                        } else {
                            Log.d(TAG, "Duplicate ignored: " + name + " @ " + uniqueKey);
                        }
                    }
                    drainQueue();
                }
            });
        }
    }
}
