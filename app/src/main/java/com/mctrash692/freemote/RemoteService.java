package com.mctrash692.freemote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.mctrash692.freemote.remote.samsung.SamsungRemote;
import com.mctrash692.freemote.ui.RemoteActivity;

public class RemoteService extends Service {
    private static final String TAG = "RemoteService";
    private static final String CHANNEL_ID = "RemoteControlChannel";
    private static final int NOTIFICATION_ID = 1;

    private SamsungRemote samsungRemote;
    private String tvIp;
    private int tvPort;
    private String tvType;
    private String tvName;
    private SharedPreferences prefs;
    private Handler mainHandler;

    private final IBinder binder = new RemoteBinder();

    public static final String ACTION_CONNECT = "com.mctrash692.freemote.CONNECT";
    public static final String ACTION_DISCONNECT = "com.mctrash692.freemote.DISCONNECT";
    public static final String EXTRA_IP = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";

    public class RemoteBinder extends Binder {
        public RemoteService getService() {
            return RemoteService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("remote_prefs", MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must call startForeground() unconditionally on API 31+ to avoid
        // ForegroundServiceDidNotStartInTimeException on service restart (START_STICKY).
        startForeground(NOTIFICATION_ID, buildNotification("Freemote running"));

        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                tvIp = intent.getStringExtra(EXTRA_IP);
                tvPort = intent.getIntExtra(EXTRA_PORT, 8001);
                tvType = intent.getStringExtra(EXTRA_TYPE);
                tvName = intent.getStringExtra(EXTRA_NAME);
                connectToTv();
                updateNotification("Connecting to " + tvName);
            } else if (ACTION_DISCONNECT.equals(action)) {
                disconnect();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void connectToTv() {
        String savedToken = prefs.getString("token_" + tvIp, null);

        try {
            samsungRemote = new SamsungRemote(tvIp, savedToken, new SamsungRemote.Listener() {
                @Override
                public void onConnected() {
                    Log.d(TAG, "Connected to TV");
                    mainHandler.post(() -> {
                        updateNotification("Connected to " + tvName);
                        showToast("Connected to " + tvName);
                    });
                }

                @Override
                public void onTokenReceived(String token) {
                    prefs.edit().putString("token_" + tvIp, token).apply();
                    Log.d(TAG, "Token saved");
                }

                @Override
                public void onDisconnected(String reason) {
                    Log.d(TAG, "Disconnected: " + reason);
                    mainHandler.post(() -> {
                        updateNotification("Disconnected from " + tvName);
                        showToast("Disconnected: " + reason);
                    });
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Error: " + message);
                    mainHandler.post(() -> {
                        updateNotification("Error: " + message);
                        showToast("Error: " + message);
                    });
                }
            });
            samsungRemote.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create remote", e);
            showToast("Failed to connect: " + e.getMessage());
        }
    }

    public void sendKey(String keyCode) {
        if (samsungRemote != null && samsungRemote.isConnected()) {
            samsungRemote.sendKey(keyCode);
        }
    }

    public void sendAppLaunch(String appId) {
        if (samsungRemote != null) {
            samsungRemote.sendAppLaunch(appId);
        }
    }

    public void sendInputString(String text) {
        if (samsungRemote != null && samsungRemote.isConnected()) {
            samsungRemote.sendInputString(text);
        }
    }

    public boolean isConnected() {
        return samsungRemote != null && samsungRemote.isConnected();
    }

    private void disconnect() {
        if (samsungRemote != null) {
            samsungRemote.disconnect();
        }
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(RemoteService.this, message, Toast.LENGTH_SHORT).show());
    }

    private void updateNotification(String contentText) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(contentText));
        }
    }

    private Notification buildNotification(String contentText) {
        Intent intent = new Intent(this, RemoteActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Freemote Remote")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Remote Control Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}
