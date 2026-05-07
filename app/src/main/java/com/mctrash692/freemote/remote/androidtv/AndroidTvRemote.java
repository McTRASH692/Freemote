package com.mctrash692.freemote.remote.androidtv;

import android.content.Context;
import android.util.Log;

import com.mctrash692.freemote.remote.RemoteController;
import com.mctrash692.freemote.remote.androidtv.proto.RemoteProto;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AndroidTvRemote implements RemoteController {

    private static final String TAG = "AndroidTvRemote";
    private static final int PORT = 6466;

    private final String ip;
    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean intentionalStop = false;

    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Context context;

    public AndroidTvRemote(String ip, Listener listener) {
        this.ip = ip;
        this.listener = listener;
    }
    
    public AndroidTvRemote(Context context, String ip, Listener listener) {
        this.context = context;
        this.ip = ip;
        this.listener = listener;
    }

    @Override
    public void connect() {
        intentionalStop = false;

        executor.submit(() -> {
            try {
                SSLContext sslContext = buildPermissiveSslContext();

                socket = (SSLSocket) sslContext.getSocketFactory().createSocket();
                socket.setSoTimeout(30000);
                socket.connect(new InetSocketAddress(ip, PORT), 5000);
                socket.startHandshake();

                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                connected.set(true);
                
                if (listener != null) {
                    executor.execute(() -> listener.onConnected());
                }

                sendCapabilityRequest();

                while (connected.get() && !intentionalStop) {
                    try {
                        int len = in.readInt();
                        if (len > 0 && len < 65536) {
                            byte[] data = new byte[len];
                            in.readFully(data);
                            try {
                                RemoteProto.RemoteMessage response = RemoteProto.RemoteMessage.parseFrom(data);
                                handleResponse(response);
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to parse response", e);
                            }
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        // Expected timeout, continue loop
                    } catch (IOException e) {
                        if (!intentionalStop && listener != null) {
                            connected.set(false);
                            listener.onDisconnected("Connection lost: " + e.getMessage());
                        }
                        break;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                if (!intentionalStop && listener != null) {
                    connected.set(false);
                    listener.onError("Connection failed: " + e.getMessage());
                }
            }
        });
    }

    private void handleResponse(RemoteProto.RemoteMessage response) {
        if (response.hasCapabilityResponse()) {
            RemoteProto.CapabilityResponse cap = response.getCapabilityResponse();
            Log.d(TAG, "TV supports - mouse:" + cap.getSupportsMouse() + 
                  " keyboard:" + cap.getSupportsKeyboard());
        }
    }

    private void sendCapabilityRequest() {
        if (!isReady()) return;
        try {
            RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                .setCapabilityRequest(RemoteProto.CapabilityRequest.newBuilder()
                    .setWantMouse(true)
                    .setWantKeyboard(true)
                    .setWantMedia(true)
                    .setWantSystem(true)
                    .build())
                .build();
            writeMessage(msg);
        } catch (IOException e) {
            Log.e(TAG, "Failed to send capability request", e);
        }
    }

    @Override
    public void disconnect() {
        intentionalStop = true;
        connected.set(false);
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            Log.w(TAG, "Error closing socket", e);
        }
        executor.shutdownNow();
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    private boolean isReady() {
        return connected.get() && out != null;
    }

    private void writeMessage(RemoteProto.RemoteMessage msg) throws IOException {
        byte[] data = msg.toByteArray();
        out.writeInt(data.length);
        out.write(data);
        out.flush();
    }

    @Override
    public void sendKey(int keyCode, boolean longPress) {
        if (!isReady()) return;
        
        executor.submit(() -> {
            try {
                // Build RemoteKeyCode message using the generated builder
                RemoteProto.RemoteKeyCode.Builder keyBuilder = RemoteProto.RemoteKeyCode.newBuilder();
                
                // Protobuf generates setKeyCode method (camelCase from key_code)
                // If that fails, try setKeyCodeValue
                try {
                    java.lang.reflect.Method method = keyBuilder.getClass().getMethod("setKeyCode", int.class);
                    method.invoke(keyBuilder, keyCode);
                } catch (NoSuchMethodException e1) {
                    try {
                        java.lang.reflect.Method method = keyBuilder.getClass().getMethod("setKeyCodeValue", int.class);
                        method.invoke(keyBuilder, keyCode);
                    } catch (NoSuchMethodException e2) {
                        Log.e(TAG, "Could not find setKeyCode method: " + e2.getMessage());
                        return;
                    }
                }
                
                java.lang.reflect.Method setLongPress = keyBuilder.getClass().getMethod("setLongPress", boolean.class);
                setLongPress.invoke(keyBuilder, longPress);
                
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setKey((RemoteProto.RemoteKeyCode) keyBuilder.build())
                    .build();
                writeMessage(msg);
            } catch (Exception e) {
                Log.e(TAG, "sendKey failed", e);
            }
        });
    }

    @Override
    public void sendKey(String keyCode) {
        int code = mapKeyCode(keyCode);
        sendKey(code, false);
    }

    private int mapKeyCode(String keyCode) {
        switch (keyCode) {
            case "KEY_UP": return 19;
            case "KEY_DOWN": return 20;
            case "KEY_LEFT": return 21;
            case "KEY_RIGHT": return 22;
            case "KEY_ENTER": return 23;
            case "KEY_BACK": return 4;
            case "KEY_HOME": return 3;
            case "KEY_MENU": return 82;
            case "KEY_VOLUMEUP": return 24;
            case "KEY_VOLUMEDOWN": return 25;
            case "KEY_MUTE": return 164;
            case "KEY_POWER": return 26;
            case "KEY_INFO": return 165;
            case "KEY_GUIDE": return 187;
            case "KEY_SOURCE": return 178;
            case "KEY_CHUP": return 166;
            case "KEY_CHDOWN": return 167;
            case "KEY_0": return 7;
            case "KEY_1": return 8;
            case "KEY_2": return 9;
            case "KEY_3": return 10;
            case "KEY_4": return 11;
            case "KEY_5": return 12;
            case "KEY_6": return 13;
            case "KEY_7": return 14;
            case "KEY_8": return 15;
            case "KEY_9": return 16;
            default: return 0;
        }
    }

    @Override
    public void sendText(String text) {
        sendText(text, false);
    }

    @Override
    public void sendText(String text, boolean replaceAll) {
        if (!isReady() || text == null || text.isEmpty()) return;
        
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setKeyboard(RemoteProto.KeyboardInput.newBuilder()
                        .setText(text)
                        .setReplaceAll(replaceAll)
                        .setSubmit(false)
                        .build())
                    .build();
                writeMessage(msg);
            } catch (IOException e) {
                Log.e(TAG, "sendText failed", e);
            }
        });
    }

    @Override
    public void sendMouseMove(int dx, int dy) {
        if (!isReady()) return;
        
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setMouse(RemoteProto.MouseInput.newBuilder()
                        .setDx(dx)
                        .setDy(dy)
                        .build())
                    .build();
                writeMessage(msg);
            } catch (IOException e) {
                Log.e(TAG, "sendMouseMove failed", e);
            }
        });
    }

    @Override
    public void sendMouseClick(String button) {
        if (!isReady()) return;
        
        RemoteProto.MouseInput.Button btn = RemoteProto.MouseInput.Button.LEFT;
        if ("Right".equalsIgnoreCase(button)) {
            btn = RemoteProto.MouseInput.Button.RIGHT;
        } else if ("Middle".equalsIgnoreCase(button)) {
            btn = RemoteProto.MouseInput.Button.MIDDLE;
        }
        
        final RemoteProto.MouseInput.Button finalBtn = btn;
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setMouse(RemoteProto.MouseInput.newBuilder()
                        .setButton(finalBtn)
                        .build())
                    .build();
                writeMessage(msg);
            } catch (IOException e) {
                Log.e(TAG, "sendMouseClick failed", e);
            }
        });
    }

    @Override
    public void sendMouseWheel(int deltaY) {
        if (!isReady()) return;
        
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setMouse(RemoteProto.MouseInput.newBuilder()
                        .setWheelY(deltaY)
                        .build())
                    .build();
                writeMessage(msg);
            } catch (IOException e) {
                Log.e(TAG, "sendMouseWheel failed", e);
            }
        });
    }

    @Override
    public void sendMouseActivate() {
        sendMouseMove(0, 0);
    }

    @Override
    public void sendAppLaunch(String appId) {
        if (!isReady()) return;
        
        executor.submit(() -> {
            try {
                RemoteProto.RemoteMessage msg = RemoteProto.RemoteMessage.newBuilder()
                    .setApp(RemoteProto.RemoteAppLinkLaunchRequest.newBuilder()
                        .setAppLink(appId)
                        .build())
                    .build();
                writeMessage(msg);
            } catch (IOException e) {
                Log.e(TAG, "sendAppLaunch failed", e);
            }
        });
    }

    @Override
    public void sendVolumeUp() {
        sendKey(24, false);
    }

    @Override
    public void sendVolumeDown() {
        sendKey(25, false);
    }

    @Override
    public void sendMute() {
        sendKey(164, false);
    }

    @Override
    public void sendHome() {
        sendKey(3, false);
    }

    @Override
    public void sendBack() {
        sendKey(4, false);
    }

    @Override
    public void sendPower() {
        sendKey(26, false);
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
