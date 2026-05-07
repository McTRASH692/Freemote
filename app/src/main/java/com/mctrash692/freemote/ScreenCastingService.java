package com.mctrash692.freemote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.mctrash692.freemote.ui.RemoteActivity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ScreenCastingService extends Service {
    private static final String TAG = "ScreenCasting";
    private static final String CHANNEL_ID = "CastingChannel";
    private static final int NOTIFICATION_ID = 2;

    private static final int[] MIRACAST_PORTS = {
        7236, 8080, 8554, 8888, 8001, 8002, 55000, 7676, 7250, 8266, 5555, 9000, 4001, 4002
    };

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec encoder;
    private Socket socket;
    private OutputStream outputStream;
    private HandlerThread encoderThread;
    private Handler encoderHandler;
    private boolean isCasting = false;
    private String tvIp;
    private int currentPortIndex = 0;
    private int workingPort = -1;
    private int width, height, dpi;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override 
    public IBinder onBind(Intent intent) { 
        return null; 
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

        String action = intent.getAction();
        
        if ("START_CASTING".equals(action)) {
            tvIp = intent.getStringExtra("tv_ip");
            width = intent.getIntExtra("width", 1280);
            height = intent.getIntExtra("height", 720);
            dpi = intent.getIntExtra("dpi", 320);
            
            // Get the pending projection from RemoteActivity
            mediaProjection = RemoteActivity.pendingProjection;
            RemoteActivity.pendingProjection = null;
            
            if (mediaProjection == null) {
                Log.e(TAG, "No media projection available");
                stopSelf();
                return START_NOT_STICKY;
            }
            
            startCasting();
        } else if ("STOP_CASTING".equals(action)) {
            stopCasting();
            stopForeground(true);
            stopSelf();
        }
        
        return START_STICKY;
    }
    
    private void startCasting() {
        if (tvIp == null || mediaProjection == null) {
            Log.e(TAG, "Missing tvIp or mediaProjection");
            stopSelf();
            return;
        }
        
        isCasting = true;
        currentPortIndex = 0;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Scanning for TV..."), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Scanning for TV..."));
        }
        
        new Thread(() -> findPortAndCast()).start();
    }

    private void findPortAndCast() {
        while (currentPortIndex < MIRACAST_PORTS.length) {
            int port = MIRACAST_PORTS[currentPortIndex];
            mainHandler.post(() -> updateNotification("Trying port " + port + "..."));

            try {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
                socket = new Socket();
                socket.connect(new InetSocketAddress(tvIp, port), 3000);
                outputStream = socket.getOutputStream();
                workingPort = port;
                Log.d(TAG, "Connected to TV on port " + port);

                sendHandshake(port);

                mainHandler.post(() -> startEncodingPipeline());
                return;

            } catch (IOException e) {
                Log.d(TAG, "Failed on port " + port + ": " + e.getMessage());
                currentPortIndex++;
            }
        }

        mainHandler.post(() -> {
            updateNotification("No compatible port found");
            Toast.makeText(this, "Could not connect to TV. Enable Screen Mirroring on the TV first.", Toast.LENGTH_LONG).show();
            stopCasting();
            stopSelf();
        });
    }

    private void startEncodingPipeline() {
        try {
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null");
                stopSelf();
                return;
            }
            
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 2);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            Surface inputSurface = encoder.createInputSurface();

            if (virtualDisplay != null) virtualDisplay.release();
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCast", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            );

            encoder.start();
            Log.d(TAG, "Encoder started — casting on port " + workingPort);

            updateNotification("Screen mirroring active on port " + workingPort);
            Toast.makeText(this, "Connected on port " + workingPort, Toast.LENGTH_SHORT).show();

            encoderThread = new HandlerThread("EncoderThread");
            encoderThread.start();
            encoderHandler = new Handler(encoderThread.getLooper());
            startEncodingLoop();

        } catch (Exception e) {
            Log.e(TAG, "Pipeline setup error", e);
            Toast.makeText(this, "Screen mirror failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopCasting();
            stopSelf();
        }
    }

    private void sendHandshake(int port) {
        if (outputStream == null) return;
        try {
            byte[] handshake;
            if (port == 8554) {
                handshake = "RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n".getBytes();
            } else if (port == 8080 || port == 8888) {
                handshake = "HTTP/1.1 200 OK\r\n\r\n".getBytes();
            } else {
                handshake = ("M-SEARCH * HTTP/1.1\r\nHOST: " + tvIp + ":" + port + "\r\n\r\n").getBytes();
            }
            outputStream.write(handshake);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Handshake failed", e);
        }
    }

    private void startEncodingLoop() {
        encoderHandler.post(() -> {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (isCasting && encoder != null) {
                try {
                    int index = encoder.dequeueOutputBuffer(bufferInfo, 10000);
                    if (index >= 0) {
                        ByteBuffer buffer = encoder.getOutputBuffer(index);
                        if (buffer != null && bufferInfo.size > 0 && outputStream != null) {
                            byte[] data = new byte[bufferInfo.size];
                            buffer.get(data);
                            outputStream.write(data);
                            outputStream.flush();
                        }
                        encoder.releaseOutputBuffer(index, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Encoding loop error", e);
                }
            }
        });
    }

    private void stopCasting() {
        isCasting = false;
        if (encoder != null) {
            try { encoder.stop(); encoder.release(); } catch (Exception ignored) {}
            encoder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
        if (outputStream != null) {
            try { outputStream.close(); } catch (IOException ignored) {}
            outputStream = null;
        }
        if (encoderThread != null) {
            encoderThread.quitSafely();
            encoderThread = null;
        }
        encoderHandler = null;
        workingPort = -1;
        Log.d(TAG, "Casting stopped");
    }

    private void updateNotification(String contentText) {
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.notify(NOTIFICATION_ID, buildNotification(contentText));
    }

    private Notification buildNotification(String contentText) {
        Intent intent = new Intent(this, RemoteActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ScreenCastingService.class);
        stopIntent.setAction("STOP_CASTING");
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = workingPort != -1 ? contentText + " (port " + workingPort + ")" : contentText;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📺 Screen Casting")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Screen Casting", NotificationManager.IMPORTANCE_LOW);
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        stopCasting();
        super.onDestroy();
    }
}
