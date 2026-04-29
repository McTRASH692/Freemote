package com.mctrash692.freemote.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mctrash692.freemote.model.TvDevice;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NetworkScanner {
    private static final String TAG = "NetworkScanner";
    private static final int[] TIZEN_PORTS = {8001, 8002, 55000, 7676};
    private static final int TIMEOUT_MS = 500;
    // Bounded pool: enough parallelism without spawning 1000+ threads on a /24 subnet.
    private static final int THREAD_POOL_SIZE = 32;

    private final Handler mainHandler;
    private final Consumer<TvDevice> onDeviceFound;
    private volatile boolean isRunning = true;
    private ExecutorService executor;

    public NetworkScanner(Consumer<TvDevice> onDeviceFound) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.onDeviceFound = onDeviceFound;
    }

    public void scanNetwork(String subnet) {
        isRunning = true;
        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        List<String> hosts = new ArrayList<>(254);
        for (int i = 1; i <= 254; i++) {
            hosts.add(subnet + i);
        }

        for (String ip : hosts) {
            executor.submit(() -> {
                if (!isRunning) return;
                for (int port : TIZEN_PORTS) {
                    if (checkPort(ip, port)) {
                        String name = "Samsung TV at " + ip;
                        Log.d(TAG, "Found TV on " + ip + ":" + port);
                        mainHandler.post(() -> onDeviceFound.accept(
                            new TvDevice(name, ip, port, TvDevice.Type.SAMSUNG)
                        ));
                        break;
                    }
                }
            });
        }

        executor.shutdown();
    }

    private boolean checkPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

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
