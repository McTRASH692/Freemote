package com.mctrash692.freemote.remote.androidtv;

import android.util.Log;

import com.mctrash692.freemote.remote.androidtv.proto.RemoteProto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AndroidTvRemote {

    private static final String TAG  = "AndroidTvRemote";
    private static final int    PORT = 6466;

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onError(String message);
    }

    private final String          ip;
    private final Listener        listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SSLSocket        socket;
    private DataInputStream  in;
    private DataOutputStream out;
    private volatile boolean connected      = false;
    private volatile boolean intentionalStop = false;

    public AndroidTvRemote(String ip, Listener listener) {
        this.ip       = ip;
        this.listener = listener;
    }

    public void connect() {
        intentionalStop = false;
        executor.submit(() -> {
            try {
                SSLContext sslContext = buildPermissiveSslContext();
                socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
                // Set a generous read timeout so the read loop doesn't false-fire
                socket.setSoTimeout(30000);
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();

                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                connected = true;

                Log.d(TAG, "Connected to Android TV @ " + ip);
                listener.onConnected();

                // Read loop — only reports disconnect on unexpected closure
                while (connected && !intentionalStop) {
                    try {
                        int len = in.readInt();
                        if (len > 0 && len < 65536) {
                            byte[] data = new byte[len];
                            in.readFully(data);
                            Log.d(TAG, "Received " + len + " bytes from TV");
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        // Read timeout — socket still alive, keep looping
                        Log.v(TAG, "Read timeout (keepalive tick)");
                    } catch (IOException e) {
                        if (!intentionalStop) {
                            Log.w(TAG, "Read loop ended unexpectedly: " + e.getMessage());
                            connected = false;
                            listener.onDisconnected("Connection lost: " + e.getMessage());
                        }
                        break;
                    }
                }

            } catch (Exception e) {
                if (!intentionalStop) {
                    Log.e(TAG, "Connection error", e);
                    connected = false;
                    listener.onError(e.getMessage());
                }
            }
        });
    }

    public void sendKey(int keyCode) {
        if (!connected || out == null) {
            Log.w(TAG, "sendKey called but not connected");
            return;
        }
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setCode(
                        RemoteProto.RemoteKeyCode.newBuilder()
                            .setKeyCode(keyCode)
                            .setLongPress(false)
                            .build()
                    ).build();
                writeMessage(msg);
                Log.d(TAG, "Sent keyCode: " + keyCode);
            } catch (IOException e) {
                Log.e(TAG, "sendKey error", e);
                listener.onError(e.getMessage());
            }
        });
    }

    public void sendKeyLong(int keyCode) {
        if (!connected || out == null) return;
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setCode(
                        RemoteProto.RemoteKeyCode.newBuilder()
                            .setKeyCode(keyCode)
                            .setLongPress(true)
                            .build()
                    ).build();
                writeMessage(msg);
            } catch (IOException e) {
                Log.e(TAG, "sendKeyLong error", e);
            }
        });
    }

    public void disconnect() {
        intentionalStop = true;
        connected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        executor.shutdown();
    }

    public boolean isConnected() { return connected; }

    private void writeMessage(RemoteProto.RemoteMessage msg) throws IOException {
        byte[] data = msg.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    private SSLContext buildPermissiveSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }
}
