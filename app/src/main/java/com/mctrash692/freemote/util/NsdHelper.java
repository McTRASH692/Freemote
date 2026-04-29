package com.mctrash692.freemote.util;

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

    private static final String TAG = "NsdHelper";
    private static final String[] SAMSUNG_SERVICE_TYPES = {
        "_samsungsmarthome._tcp.",
        "_samsungtv._tcp.",
        "_samsung._tcp.",
        "_discovery._tcp.",
        "_tv._tcp.",
        "_samsungd2d._tcp.",
        "_sercomm._tcp."
    };
    private static final String SERVICE_ANDROIDTV = "_androidtvremote2._tcp.";

    private final NsdManager nsdManager;
    private final Consumer<TvDevice> onDeviceFound;
    private final Map<String, NsdManager.DiscoveryListener> samsungListeners = new HashMap<>();
    private final Set<String> discoveredDeviceKeys = new HashSet<>();

    // Queued resolver: NsdManager only supports one concurrent resolveService call.
    // On API 34 calling it with overlapping listeners causes FAILURE_ALREADY_ACTIVE.
    private final Queue<NsdServiceInfo> resolveQueue = new ArrayDeque<>();
    private boolean resolving = false;

    private NsdManager.DiscoveryListener androidTvListener;

    public NsdHelper(Context context, Consumer<TvDevice> onDeviceFound) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.onDeviceFound = onDeviceFound;
    }

    public void startDiscovery() {
        for (String serviceType : SAMSUNG_SERVICE_TYPES) {
            NsdManager.DiscoveryListener listener = buildListener(TvDevice.Type.SAMSUNG);
            samsungListeners.put(serviceType, listener);
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        }

        androidTvListener = buildListener(TvDevice.Type.ANDROID_TV);
        nsdManager.discoverServices(SERVICE_ANDROIDTV, NsdManager.PROTOCOL_DNS_SD, androidTvListener);
    }

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
    }

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
                Log.d(TAG, "Service lost: " + info.getServiceName());
                discoveredDeviceKeys.remove(info.getServiceName());
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

    // Queue service infos and resolve them one at a time to avoid FAILURE_ALREADY_ACTIVE on API 34.
    private void enqueueResolve(NsdServiceInfo info, TvDevice.Type type) {
        synchronized (resolveQueue) {
            resolveQueue.add(info);
            if (!resolving) {
                drainQueue(type);
            }
        }
    }

    private void drainQueue(TvDevice.Type type) {
        synchronized (resolveQueue) {
            NsdServiceInfo next = resolveQueue.poll();
            if (next == null) {
                resolving = false;
                return;
            }
            resolving = true;
            nsdManager.resolveService(next, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    Log.e(TAG, "Resolve failed: " + info.getServiceName() + " error=" + errorCode);
                    drainQueue(type);
                }

                @Override public void onServiceResolved(NsdServiceInfo info) {
                    String ip = info.getHost().getHostAddress();
                    int port = info.getPort();
                    String name = info.getServiceName();
                    String uniqueKey = ip + ":" + port;

                    if (!discoveredDeviceKeys.contains(uniqueKey)) {
                        discoveredDeviceKeys.add(uniqueKey);
                        Log.d(TAG, "Resolved: " + name + " @ " + ip + ":" + port);
                        onDeviceFound.accept(new TvDevice(name, ip, port, type));
                    } else {
                        Log.d(TAG, "Duplicate ignored: " + name + " @ " + uniqueKey);
                    }
                    drainQueue(type);
                }
            });
        }
    }
}
