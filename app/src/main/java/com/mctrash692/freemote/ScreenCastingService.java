package com.mctrash692.freemote;

// ============================================================================
// FILE: ScreenCastingService.java
// WHAT:  The background service that handles screen mirroring (casting your
//        phone screen to the TV). It records your phone's screen using
//        Android's screen-capture permission, converts it into a video
//        stream, and sends it over your WiFi network to the TV. This is
//        experimental — it may not work with all TVs because different
//        brands use different screen-mirroring standards.
// ============================================================================

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

// ==========================================================================
// SECTION: SCREEN CASTING SERVICE
// WHAT:  A background service that captures your phone screen and sends
//        it to the TV as a live video stream (screen mirroring).
//        NOTE: This is experimental. It uses a raw TCP-based pipeline
//        that will not work with most TVs without a full Miracast/WiDi
//        or WebRTC implementation. Every failure is handled gracefully
//        so the app never crashes.
// ==========================================================================

/**
 * ScreenCastingService — wraps the experimental screen-casting pipeline.
 *
 * STATUS: The raw-TCP casting pipeline is a proof-of-concept and will not
 * succeed against real TVs without a full Miracast/WFD or WebRTC stack.
 * Every entry-point is guarded so that failures produce a user-visible
 * notification/toast instead of crashing the app.
 *
 * Manifest requirements (must be present or foreground start will throw):
 *   <service android:name=".ScreenCastingService"
 *            android:foregroundServiceType="mediaProjection" />   ← API 29+
 */
public class ScreenCastingService extends Service {

    // ==========================================================================
    // CONSTANTS AND STATE
    // ==========================================================================

    private static final String TAG = "ScreenCasting";
    private static final String CHANNEL_ID = "CastingChannel";
    private static final int NOTIFICATION_ID = 2;

    // Ports tried in order — none of these will work for real Miracast but
    // we keep the list so the pipeline can still be tested on custom receivers.
    private static final int[] MIRACAST_PORTS = {
        7236, 8080, 8554, 8888, 8001, 8002, 55000, 7676, 7250, 8266, 5555, 9000, 4001, 4002
    };

    // Screen capture and encoding objects
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec encoder;
    // Network connection to the TV
    private Socket socket;
    private OutputStream outputStream;
    // Background thread for encoding
    private HandlerThread encoderThread;
    private Handler encoderHandler;
    private volatile boolean isCasting = false;
    private String tvIp;
    // Port scanning state
    private int currentPortIndex = 0;
    private int workingPort = -1;
    // Screen dimensions for encoding
    private int width, height, dpi;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==========================================================================
    // Service lifecycle
    // ==========================================================================

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createNotificationChannel();
        } catch (Exception e) {
            Log.e(TAG, "onCreate: channel creation failed", e);
        }
    }

    // ==========================================================================
    // METHOD: onStartCommand
    // WHAT:  Runs when another part of the app tells this service to start
    //        or stop casting. Handles "START_CASTING" (begins screen
    //        capture and transmission) and "STOP_CASTING" (ends it).
    // ==========================================================================

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            Log.w(TAG, "onStartCommand: null intent/action — stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            String action = intent.getAction();

            if ("START_CASTING".equals(action)) {
                tvIp    = intent.getStringExtra("tv_ip");
                width   = intent.getIntExtra("width",  1280);
                height  = intent.getIntExtra("height",  720);
                dpi     = intent.getIntExtra("dpi",     320);

                // Consume the pending projection that RemoteActivity stored.
                mediaProjection = RemoteActivity.pendingProjection;
                RemoteActivity.pendingProjection = null;

                if (mediaProjection == null) {
                    Log.e(TAG, "No MediaProjection — cannot cast");
                    notifyError("Screen mirroring permission was not granted.");
                    stopSelf();
                    return START_NOT_STICKY;
                }

                if (tvIp == null || tvIp.isEmpty()) {
                    Log.e(TAG, "No TV IP — cannot cast");
                    notifyError("No TV IP address provided.");
                    safeStopProjection();
                    stopSelf();
                    return START_NOT_STICKY;
                }

                startCasting();

            } else if ("STOP_CASTING".equals(action)) {
                stopCasting();
                stopForeground(true);
                stopSelf();
            }

        } catch (Exception e) {
            Log.e(TAG, "onStartCommand: unexpected error", e);
            notifyError("Screen mirroring failed to start: " + e.getMessage());
            safeStopProjection();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopCasting();
        super.onDestroy();
    }

    // ==========================================================================
    // Casting pipeline — the main steps to start screen mirroring
    // ==========================================================================

    // ==========================================================================
    // METHOD: startCasting
    // WHAT:  Starts the screen casting process. Shows a foreground
    //        notification (required for Android 10+), then begins
    //        trying to connect to the TV on different ports.
    // ==========================================================================

    private void startCasting() {
        isCasting = true;
        currentPortIndex = 0;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification("Scanning for TV…"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                );
            } else {
                startForeground(NOTIFICATION_ID, buildNotification("Scanning for TV…"));
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed — check manifest foregroundServiceType", e);
            notifyError("Screen mirroring unavailable: missing manifest permission.");
            safeStopProjection();
            stopSelf();
            return;
        }

        new Thread(this::findPortAndCast).start();
    }

    // ==========================================================================
    // METHOD: findPortAndCast
    // WHAT:  Tries to connect to the TV on different network ports one by
    //        one. For each port, it attempts a TCP connection. If it
    //        succeeds, it sends a handshake message and starts the video
    //        encoding pipeline. If all ports fail, it shows an error.
    // ==========================================================================

    private void findPortAndCast() {
        while (isCasting && currentPortIndex < MIRACAST_PORTS.length) {
            int port = MIRACAST_PORTS[currentPortIndex];
            mainHandler.post(() -> updateNotification("Trying port " + port + "…"));

            try {
                if (socket != null && !socket.isClosed()) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
                socket = new Socket();
                socket.connect(new InetSocketAddress(tvIp, port), 3000);
                outputStream = socket.getOutputStream();
                workingPort = port;
                Log.d(TAG, "Connected on port " + port);

                sendHandshake(port);
                mainHandler.post(this::startEncodingPipeline);
                return;

            } catch (IOException e) {
                Log.d(TAG, "Port " + port + " failed: " + e.getMessage());
                currentPortIndex++;
            }
        }

        // All ports exhausted — show error message
        mainHandler.post(() -> {
            updateNotification("No compatible port found");
            showToast(
                "Could not connect to TV for screen mirroring.\n" +
                "Enable 'Screen Mirroring' / 'AllShare Cast' on the TV first.",
                true
            );
            stopCasting();
            stopSelf();
        });
    }

    // ==========================================================================
    // METHOD: startEncodingPipeline
    // WHAT:  Once connected to the TV, this sets up the video encoder and
    //        virtual display. It creates a fake screen (virtual display)
    //        that mirrors your real screen, encodes the video frames into
    //        H.264 format, and sends them over the network to the TV.
    // ==========================================================================

    private void startEncodingPipeline() {
        if (mediaProjection == null) {
            Log.e(TAG, "startEncodingPipeline: mediaProjection is null");
            notifyError("Screen mirroring lost projection access.");
            stopSelf();
            return;
        }

        try {
            // Configure the H.264 video encoder
            MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_BIT_RATE, width * height * 2);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            Surface inputSurface = encoder.createInputSurface();

            // Create a virtual display that mirrors the phone's screen
            if (virtualDisplay != null) virtualDisplay.release();
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCast", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            );

            encoder.start();
            Log.d(TAG, "Encoder started on port " + workingPort);

            updateNotification("Screen mirroring active — port " + workingPort);

            // Start the encoding loop on a background thread
            encoderThread = new HandlerThread("EncoderThread");
            encoderThread.start();
            encoderHandler = new Handler(encoderThread.getLooper());
            startEncodingLoop();

        } catch (Exception e) {
            Log.e(TAG, "Pipeline setup error", e);
            notifyError("Screen mirror pipeline failed: " + e.getMessage());
            stopCasting();
            stopSelf();
        }
    }

    // ==========================================================================
    // METHOD: sendHandshake
    // WHAT:  Sends an initial handshake message to the TV after connecting.
    //        Different port numbers expect different handshake formats
    //        (RTSP for port 8554, HTTP for 8080/8888, SSDP for others).
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: startEncodingLoop
    // WHAT:  Starts the continuous loop that grabs encoded video frames
    //        from the encoder and sends them to the TV.
    // ==========================================================================

    private void startEncodingLoop() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        encodeLoopIteration(bufferInfo);
    }

    // ==========================================================================
    // METHOD: encodeLoopIteration
    // WHAT:  Gets one encoded video frame from the encoder and writes it
    //        to the network stream. Then schedules itself to run again
    //        for the next frame, creating a continuous loop.
    // ==========================================================================

    private void encodeLoopIteration(MediaCodec.BufferInfo bufferInfo) {
        if (!isCasting || encoder == null) return;
        try {
            int index = encoder.dequeueOutputBuffer(bufferInfo, 10_000);
            if (index >= 0) {
                ByteBuffer buffer = encoder.getOutputBuffer(index);
                if (buffer != null && bufferInfo.size > 0 && outputStream != null) {
                    byte[] data = new byte[bufferInfo.size];
                    buffer.get(data);
                    try {
                        outputStream.write(data);
                        outputStream.flush();
                    } catch (IOException e) {
                        Log.e(TAG, "Stream write error — stopping cast", e);
                        isCasting = false;
                        return;
                    }
                }
                encoder.releaseOutputBuffer(index, false);
            }
        } catch (Exception e) {
            if (isCasting) Log.e(TAG, "Encoding loop error", e);
        }
        if (encoderHandler != null) {
            encoderHandler.post(() -> encodeLoopIteration(bufferInfo));
        }
    }

    // ==========================================================================
    // Teardown — cleans up all resources when casting stops
    // ==========================================================================

    private void stopCasting() {
        isCasting = false;

        if (encoder != null) {
            try { encoder.stop();    } catch (Exception ignored) {}
            try { encoder.release(); } catch (Exception ignored) {}
            encoder = null;
        }
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        safeStopProjection();
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

    // ==========================================================================
    // METHOD: safeStopProjection
    // WHAT:  Stops the MediaProjection safely so the app doesn't crash
    //        if the projection was already stopped for another reason.
    // ==========================================================================

    private void safeStopProjection() {
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
    }

    // ==========================================================================
    // UI helpers — notification and toast management
    // ==========================================================================

    // Updates the notification text shown in the status bar
    private void updateNotification(String contentText) {
        try {
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.notify(NOTIFICATION_ID, buildNotification(contentText));
        } catch (Exception e) {
            Log.e(TAG, "updateNotification failed", e);
        }
    }

    // Shows an error message both in the notification and as a pop-up toast
    private void notifyError(String message) {
        mainHandler.post(() -> {
            updateNotification("Error: " + message);
            showToast(message, true);
        });
    }

    private void showToast(String message, boolean longDuration) {
        mainHandler.post(() ->
            Toast.makeText(this, message, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show()
        );
    }

    // Builds the notification shown while casting is active
    private Notification buildNotification(String contentText) {
        Intent openIntent = new Intent(this, RemoteActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, ScreenCastingService.class);
        stopIntent.setAction("STOP_CASTING");
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = (workingPort != -1)
            ? contentText + " (port " + workingPort + ")"
            : contentText;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📺 Screen Casting")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPi)
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
}
