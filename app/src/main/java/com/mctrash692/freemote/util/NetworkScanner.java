package com.mctrash692.freemote.util;

// ============================================================================
// FILE: NetworkScanner.java
// WHAT:  Searches your home Wi-Fi network for TVs. It checks every possible
//        IP address (1 to 254) on your network and tries to connect to known
//        TV ports. If it finds a TV, it reports what brand and IP address it
//        found so you can pair with it.
// WHY:   You shouldn't need to type in your TV's IP address manually. This
//        file finds it for you automatically.
// ============================================================================

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.model.TvProtocol;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NetworkScanner {
    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    private static final String TAG = "NetworkScanner";   // Used for logging/diagnostics
    private static final int TIMEOUT_MS = 500;             // How long to wait for each port check (milliseconds)
    private static final int THREAD_POOL_SIZE = 50;        // How many network checks to run at the same time

    // ==========================================================================
    // INSTANCE VARIABLES
    // WHAT:  Data that this scanner keeps track of while it is running.
    // ==========================================================================

    // The list of all network ports that any TV brand might use
    private final List<Integer> allPorts;

    // A handler that lets us send results back to the main part of the app
    private final Handler mainHandler;
    // A callback that gets called each time a TV is found
    private final Consumer<TvDevice> onDeviceFound;
    // Whether the scanner should keep running
    private volatile boolean isRunning = true;
    // Manages the pool of background threads doing the scanning
    private volatile ExecutorService executor;

    // Keeps track of which IPs we already found (to avoid reporting the same TV twice)
    private final Map<String, List<Integer>> devicePorts = new HashMap<>();

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Sets up the network scanner. It gets the list of all TV ports
    //        to check and remembers where to send results when a TV is found.
    // INPUT: onDeviceFound = a function to call whenever a TV is discovered
    // ==========================================================================

    public NetworkScanner(Consumer<TvDevice> onDeviceFound) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.onDeviceFound = onDeviceFound;
        this.allPorts = TvProtocol.getAllPorts();
        Log.d(TAG, "Scanning for ports: " + allPorts);
    }

    // ==========================================================================
    // METHOD: scanNetwork
    // WHAT:  Starts scanning your entire home network for TVs. It checks
    //        every IP address from 1 to 254 (e.g., 192.168.1.1 through
    //        192.168.1.254) by trying to connect to each one on known TV
    //        ports. Multiple checks run at the same time for speed.
    // INPUT: subnet = the first three parts of your network's IP range
    //                 (e.g., "192.168.1.")
    // ==========================================================================

    public void scanNetwork(String subnet) {
        isRunning = true;
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Generate all 254 possible IP addresses on this subnet
        List<String> hosts = new ArrayList<>(254);
        for (int i = 1; i <= 254; i++) {
            hosts.add(subnet + i);
        }

        // Check each IP address in parallel for speed
        for (String ip : hosts) {
            executor.submit(() -> {
                if (!isRunning) return;
                scanHost(ip);
            });
        }

        executor.shutdown();
    }

    // ==========================================================================
    // METHOD: scanHost (private)
    // WHAT:  Checks a single IP address to see if a TV lives there. It tries
    //        all known TV ports and if any are open, it figures out what
    //        brand of TV it is based on which ports responded.
    // INPUT: ip = the IP address to check (e.g., "192.168.1.10")
    // ==========================================================================

    private void scanHost(String ip) {
        List<Integer> openPorts = new ArrayList<>();

        for (int port : allPorts) {
            if (checkPort(ip, port)) {
                openPorts.add(port);
                Log.d(TAG, "Found open port " + port + " on " + ip);
            }
        }

        if (!openPorts.isEmpty()) {
            TvProtocol.Brand brand = TvProtocol.detectBrandByPorts(openPorts);
            int primaryPort = getPrimaryPort(openPorts, brand);
            TvDevice.Type deviceType = mapBrandToDeviceType(brand);

            String deviceKey = ip;
            synchronized (devicePorts) {
                if (!devicePorts.containsKey(deviceKey)) {
                    devicePorts.put(deviceKey, openPorts);

                    String name = brand.name() + " TV at " + ip;
                    mainHandler.post(() -> onDeviceFound.accept(
                            new TvDevice(name, ip, primaryPort, deviceType)
                    ));
                }
            }
        }
    }

    // ==========================================================================
    // METHOD: getPrimaryPort (private)
    // WHAT:  Picks the best port to use for connecting to a TV once we know
    //        what brand it is. Each brand has a preferred port that works
    //        best for remote control.
    // INPUT: openPorts = which ports were open on this TV
    //        brand     = what brand of TV was detected
    // OUTPUT: the port number to use for connecting
    // ==========================================================================

    private int getPrimaryPort(List<Integer> openPorts, TvProtocol.Brand brand) {
        switch (brand) {
            case SAMSUNG:
                if (openPorts.contains(8002)) return 8002;
                if (openPorts.contains(8001)) return 8001;
                if (openPorts.contains(55000)) return 55000;
                break;
            case LG:
                if (openPorts.contains(3001)) return 3001;
                if (openPorts.contains(3000)) return 3000;
                break;
            case ANDROID_TV:
            case SONY:
            case TCL:
            case HISENSE:
                if (openPorts.contains(6466)) return 6466;
                if (openPorts.contains(5555)) return 5555;
                break;
            case PANASONIC:
                if (openPorts.contains(55000)) return 55000;
                break;
            case PHILIPS:
                if (openPorts.contains(1925)) return 1925;
                if (openPorts.contains(1926)) return 1926;
                break;
            default:
                if (!openPorts.isEmpty()) return openPorts.get(0);
        }
        return 8002;
    }

    // ==========================================================================
    // METHOD: mapBrandToDeviceType (private)
    // WHAT:  Converts a TV brand (like "SAMSUNG" or "LG") into a device type
    //        that the rest of the app understands.
    // INPUT: brand = the detected TV brand
    // OUTPUT: the matching device type for this brand
    // ==========================================================================

    private TvDevice.Type mapBrandToDeviceType(TvProtocol.Brand brand) {
        switch (brand) {
            case SAMSUNG:    return TvDevice.Type.SAMSUNG;
            case LG:         return TvDevice.Type.LG;
            case PANASONIC:  return TvDevice.Type.PANASONIC;
            case PHILIPS:    return TvDevice.Type.PHILIPS;
            case ANDROID_TV: return TvDevice.Type.ANDROID_TV;
            case SONY:       return TvDevice.Type.SONY;
            case TCL:        return TvDevice.Type.TCL;
            case HISENSE:    return TvDevice.Type.HISENSE;
            case SHARP:      return TvDevice.Type.SHARP;
            case HAIER:      return TvDevice.Type.HAIER;
            default:         return TvDevice.Type.UNKNOWN;
        }
    }

    // ==========================================================================
    // METHOD: checkPort (private)
    // WHAT:  Tries to open a connection to an IP address on a specific port.
    //        If the connection succeeds, the port is "open" (a TV is there).
    //        If it fails or times out, the port is closed or unreachable.
    // INPUT: ip   = the IP address to check
    //        port = the port number to try connecting to
    // OUTPUT: true if port is open, false if unreachable
    // ==========================================================================

    private boolean checkPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==========================================================================
    // METHOD: stop
    // WHAT:  Stops the network scanner. Cancels any checks still in progress
    //        and shuts down the background workers.
    // ==========================================================================

    public void stop() {
        isRunning = false;
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
