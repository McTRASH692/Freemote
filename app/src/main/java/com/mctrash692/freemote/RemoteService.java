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

import com.mctrash692.freemote.remote.RemoteController;
import com.mctrash692.freemote.remote.androidtv.AndroidTvRemote;
import com.mctrash692.freemote.remote.samsung.SamsungRemote;
import com.mctrash692.freemote.ui.RemoteActivity;
import com.mctrash692.freemote.util.PairedDevicesManager;

public class RemoteService extends Service {

    private static final String TAG = "RemoteService";
    private static final String CHANNEL_ID = "RemoteControlChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "remote_prefs";

    public static final String ACTION_CONNECT = "com.mctrash692.freemote.CONNECT";
    public static final String ACTION_DISCONNECT = "com.mctrash692.freemote.DISCONNECT";

    public static final String EXTRA_IP = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    public static final String EXTRA_MAC = "extra_mac";

    private enum TvType {
        SAMSUNG, ANDROID_TV, UNKNOWN
    }

    private enum ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    private final IBinder binder = new RemoteBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private RemoteController remoteController;
    private ConnectionState state = ConnectionState.DISCONNECTED;
    
    private String tvIp;
    private int tvPort;
    private TvType tvType;
    private String tvName;
    private String deviceId;
    private String tvMac;

    public class RemoteBinder extends Binder {
        public RemoteService getService() {
            return RemoteService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Freemote running"));

        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_CONNECT:
                tvIp = intent.getStringExtra(EXTRA_IP);
                tvPort = intent.getIntExtra(EXTRA_PORT, 8002);
                tvName = intent.getStringExtra(EXTRA_NAME);
                String typeStr = intent.getStringExtra(EXTRA_TYPE);
                deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                tvMac = intent.getStringExtra(EXTRA_MAC);
                
                try {
                    tvType = TvType.valueOf(typeStr);
                } catch (Exception e) {
                    tvType = TvType.UNKNOWN;
                }
                
                connectToTv();
                updateNotification("Connecting to " + tvName);
                break;

            case ACTION_DISCONNECT:
                disconnect();
                stopForeground(true);
                stopSelf();
                break;
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private void connectToTv() {
        state = ConnectionState.CONNECTING;
        
        if (remoteController != null) {
            remoteController.disconnect();
            remoteController = null;
        }

        String savedToken = prefs.getString("token_" + tvIp, null);
        
        RemoteController.Listener controllerListener = new RemoteController.Listener() {
            @Override
            public void onConnected() {
                state = ConnectionState.CONNECTED;
                uiMessage("Connected to " + tvName);
                updateNotification("Connected to " + tvName);
            }

            @Override
            public void onDisconnected(String reason) {
                state = ConnectionState.DISCONNECTED;
                uiMessage("Disconnected: " + reason);
                updateNotification("Disconnected");
            }

            @Override
            public void onError(String message) {
                state = ConnectionState.ERROR;
                uiMessage("Error: " + message);
                updateNotification("Error: " + message);
            }

            @Override
            public void onTokenReceived(String token) {
                if (token != null && !token.isEmpty()) {
                    prefs.edit().putString("token_" + tvIp, token).apply();
                    Log.d(TAG, "Saved token for " + tvIp);
                }
            }

            @Override
            public void onDeviceInfo(String modelName, String wifiMac) {
                if (wifiMac != null && !wifiMac.isEmpty()) {
                    tvMac = wifiMac;
                    prefs.edit().putString("mac_" + tvIp, wifiMac).apply();
                    Log.d(TAG, "Saved MAC for " + tvIp + ": " + wifiMac);
                    
                    if (deviceId != null) {
                        PairedDevicesManager manager = new PairedDevicesManager(RemoteService.this);
                        com.mctrash692.freemote.model.PairedDevice device = manager.getDeviceById(deviceId);
                        if (device != null) {
                            device.setMacAddress(wifiMac);
                            manager.saveDevice(device);
                            Log.d(TAG, "Updated paired device MAC: " + device.getName());
                        }
                    }
                }
            }
        };

        switch (tvType) {
            case SAMSUNG:
                Log.d(TAG, "Creating Samsung remote for " + tvIp);
                remoteController = new SamsungRemote(tvIp, savedToken, controllerListener);
                break;
                
            case ANDROID_TV:
                Log.d(TAG, "Creating Android TV remote for " + tvIp);
                remoteController = new AndroidTvRemote(this, tvIp, controllerListener);
                break;
                
            default:
                Log.e(TAG, "Unknown TV type: " + tvType);
                state = ConnectionState.ERROR;
                uiMessage("Unknown TV type");
                return;
        }

        remoteController.connect();
    }

    private void disconnect() {
        if (remoteController != null) {
            remoteController.disconnect();
            remoteController = null;
        }
        state = ConnectionState.DISCONNECTED;
    }

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED && 
               remoteController != null && 
               remoteController.isConnected();
    }

    public void sendKey(int keyCode) {
        sendKey(keyCode, false);
    }

    public void sendKey(int keyCode, boolean longPress) {
        if (!isConnected()) {
            Log.w(TAG, "sendKey: not connected");
            return;
        }
        remoteController.sendKey(keyCode, longPress);
    }

    public void sendKey(String keyCode) {
        if (!isConnected()) {
            Log.w(TAG, "sendKey: not connected");
            return;
        }
        remoteController.sendKey(keyCode);
    }

    public void sendText(String text) {
        if (!isConnected()) {
            Log.w(TAG, "sendText: not connected");
            return;
        }
        remoteController.sendText(text);
    }

    public void sendText(String text, boolean replaceAll) {
        if (!isConnected()) {
            Log.w(TAG, "sendText: not connected");
            return;
        }
        remoteController.sendText(text, replaceAll);
    }

    public void sendMouseMove(int dx, int dy) {
        if (!isConnected()) return;
        remoteController.sendMouseMove(dx, dy);
    }

    public void sendMouseClick(String button) {
        if (!isConnected()) return;
        remoteController.sendMouseClick(button);
    }

    public void sendMouseWheel(int deltaY) {
        if (!isConnected()) return;
        remoteController.sendMouseWheel(deltaY);
    }

    public void sendMouseActivate() {
        if (!isConnected()) return;
        remoteController.sendMouseActivate();
    }

    public void sendAppLaunch(String appId) {
        if (!isConnected()) {
            Log.w(TAG, "sendAppLaunch: not connected");
            uiMessage("Not connected to TV");
            return;
        }
        remoteController.sendAppLaunch(appId);
    }

    public void sendVolumeUp() {
        if (!isConnected()) return;
        remoteController.sendVolumeUp();
    }

    public void sendVolumeDown() {
        if (!isConnected()) return;
        remoteController.sendVolumeDown();
    }

    public void sendMute() {
        if (!isConnected()) return;
        remoteController.sendMute();
    }

    public void sendHome() {
        if (!isConnected()) return;
        remoteController.sendHome();
    }

    public void sendBack() {
        if (!isConnected()) return;
        remoteController.sendBack();
    }

    public void sendPower() {
        if (!isConnected()) return;
        remoteController.sendPower();
    }

    public String getTvIp() {
        return tvIp;
    }

    public String getTvName() {
        return tvName;
    }

    public String getDeviceIdentifier() {
        return deviceId;
    }

    public String getTvMac() {
        return tvMac;
    }

    private void uiMessage(String msg) {
        mainHandler.post(() -> Toast.makeText(RemoteService.this, msg, Toast.LENGTH_SHORT).show());
    }

    private void updateNotification(String text) {
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) {
            mgr.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, RemoteActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent disconnectIntent = new Intent(this, RemoteService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        PendingIntent disconnectPi = PendingIntent.getService(
            this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Freemote")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPi)
            .setOngoing(true)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Remote Control",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Controls remote control connection status");
            
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) {
                mgr.createNotificationChannel(channel);
            }
        }
    }
}
