package com.mctrash692.freemote;

// ============================================================================
// FILE: RemoteService.java
// WHAT:  The background service that keeps the connection to your TV alive.
//        It runs as a foreground service (shown as a notification in your
//        status bar) so Android won't shut it down. It handles connecting
//        to the TV, sending button presses, mouse movements, typed text,
//        app launch commands, volume controls, and media playback controls.
//        It also saves authentication tokens and MAC addresses automatically
//        so you do not have to re-pair your TV every time.
// ============================================================================

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.remote.RemoteController;
import com.mctrash692.freemote.remote.androidtv.AndroidTvRemote;
import com.mctrash692.freemote.remote.lg.LgRemote;
import com.mctrash692.freemote.remote.roku.RokuRemote;
import com.mctrash692.freemote.remote.panasonic.PanasonicRemote;
import com.mctrash692.freemote.remote.philips.PhilipsRemote;
import com.mctrash692.freemote.remote.samsung.SamsungRemote;
import com.mctrash692.freemote.ui.RemoteActivity;
import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.util.PairedDevicesManager;

// ==========================================================================
// SECTION: REMOTE SERVICE
// WHAT:  A background service that keeps your TV connection alive. It
//        shows a notification so Android won't kill it. It handles
//        connecting, disconnecting, sending commands (keys, mouse, text,
//        volume, media), and saving auth tokens.
// ==========================================================================

public class RemoteService extends Service {

    // ==========================================================================
    // CONSTANTS AND STATE
    // ==========================================================================

    private static final String TAG = "RemoteService";
    private static final String CHANNEL_ID = "RemoteControlChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "remote_prefs";

    // Actions that other parts of the app can send to this service
    public static final String ACTION_CONNECT    = "com.mctrash692.freemote.CONNECT";
    public static final String ACTION_DISCONNECT = "com.mctrash692.freemote.DISCONNECT";

    // Extra data keys passed with connect/disconnect intents
    public static final String EXTRA_IP        = "extra_ip";
    public static final String EXTRA_PORT      = "extra_port";
    public static final String EXTRA_TYPE      = "extra_type";
    public static final String EXTRA_NAME      = "extra_name";
    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    public static final String EXTRA_MAC       = "extra_mac";

    // Possible states of the connection to the TV
    private enum ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private final IBinder binder = new RemoteBinder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private RemoteController remoteController;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    // Information about the TV we are connected to
    private String tvIp;
    private int    tvPort;
    private TvDevice.Type tvType;
    private String tvName;
    private String deviceId;
    private String tvMac;

    // =========================================================================
    // Binder — lets the RemoteActivity talk to this service
    // =========================================================================

    public class RemoteBinder extends Binder {
        public RemoteService getService() { return RemoteService.this; }
    }

    // =========================================================================
    // Lifecycle — runs when the service starts, receives commands, or stops
    // =========================================================================

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
            return START_NOT_STICKY;
        }

        // Decide what to do based on the action sent
        switch (intent.getAction()) {
            case ACTION_CONNECT:
                tvIp     = intent.getStringExtra(EXTRA_IP);
                tvPort   = intent.getIntExtra(EXTRA_PORT, 8002);
                tvName   = intent.getStringExtra(EXTRA_NAME);
                deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
                tvMac    = intent.getStringExtra(EXTRA_MAC);

                if (tvIp == null || tvIp.isEmpty()) {
                    Log.e(TAG, "No IP address provided");
                    updateNotification("Error: No IP address provided");
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                }

                String typeStr = intent.getStringExtra(EXTRA_TYPE);
                if (typeStr != null) {
                    try {
                        tvType = TvDevice.Type.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        tvType = TvDevice.Type.UNKNOWN;
                    }
                } else {
                    tvType = TvDevice.Type.UNKNOWN;
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

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    // =========================================================================
    // Connection — handles connecting to and disconnecting from the TV
    // =========================================================================

    private void connectToTv() {
        state = ConnectionState.CONNECTING;

        // Disconnect any existing connection first
        if (remoteController != null) {
            remoteController.disconnect();
            remoteController = null;
        }

        String savedToken = prefs.getString("token_" + tvIp, null);

        // Create a listener that responds to connection events from the TV
        RemoteController.Listener controllerListener = new RemoteController.Listener() {

            @Override
            public void onConnected() {
                state = ConnectionState.CONNECTED;
                updateNotification("Connected to " + tvName);
            }

            @Override
            public void onDisconnected(String reason) {
                state = ConnectionState.DISCONNECTED;
                Log.d(TAG, "Disconnected: " + reason);
                updateNotification("Disconnected");
            }

            @Override
            public void onError(String message) {
                state = ConnectionState.ERROR;
                Toast.makeText(RemoteService.this, "Connection error: " + message, Toast.LENGTH_SHORT).show();
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
                        PairedDevicesManager manager =
                            new PairedDevicesManager(RemoteService.this);
                        PairedDevice device =
                            manager.getDeviceById(deviceId);
                        if (device != null) {
                            device.setMacAddress(wifiMac);
                            manager.saveDevice(device);
                            Log.d(TAG, "Updated paired device MAC: " + device.getName());
                        }
                    }
                }
            }

            @Override
            public void onImeStarted() {
                Log.d(TAG, "TV IME started — broadcasting");
                LocalBroadcastManager.getInstance(RemoteService.this)
                    .sendBroadcast(new Intent("com.mctrash692.freemote.IME_STARTED"));
            }
        };

        // Create the right type of remote controller based on the TV brand
        switch (tvType) {
            case SAMSUNG:
                Log.d(TAG, "Creating Samsung remote for " + tvIp);
                remoteController = new SamsungRemote(tvIp, savedToken, controllerListener);
                break;

            case ANDROID_TV:
                Log.d(TAG, "Creating Android TV remote for " + tvIp);
                remoteController = new AndroidTvRemote(tvIp, controllerListener);
                break;

            case LG:
                Log.d(TAG, "Creating LG remote for " + tvIp);
                remoteController = new LgRemote(this, tvIp, controllerListener);
                break;

            case ROKU:
                Log.d(TAG, "Creating Roku remote for " + tvIp);
                remoteController = new RokuRemote(tvIp, controllerListener);
                break;

            case PANASONIC:
                Log.d(TAG, "Creating Panasonic remote for " + tvIp);
                remoteController = new PanasonicRemote(tvIp, controllerListener);
                break;

            case PHILIPS:
                Log.d(TAG, "Creating Philips remote for " + tvIp);
                remoteController = new PhilipsRemote(tvIp, controllerListener);
                break;

            default:
                Log.e(TAG, "Unknown TV type: " + tvType);
                state = ConnectionState.ERROR;
                Toast.makeText(RemoteService.this, "Unsupported device type", Toast.LENGTH_SHORT).show();
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

    // ==========================================================================
    // METHOD: isConnected
    // WHAT:  Checks whether there is an active connection to the TV.
    // ==========================================================================

    public boolean isConnected() {
        return state == ConnectionState.CONNECTED
            && remoteController != null
            && remoteController.isConnected();
    }

    // =========================================================================
    // Key / text passthrough
    // Silent-fail (log only) when not connected — callers must not show toasts.
    // =========================================================================

    public void sendKey(int keyCode) { sendKey(keyCode, false); }

    // ==========================================================================
    // METHOD: sendKey
    // WHAT:  Sends a button press command to the TV (e.g., volume up, enter,
    //        home). If not connected, it just logs a warning.
    // INPUT: keyCode = the button to press, longPress = hold the button down
    // ==========================================================================

    public void sendKey(int keyCode, boolean longPress) {
        if (!isConnected()) { Log.w(TAG, "sendKey: not connected"); return; }
        remoteController.sendKey(keyCode, longPress);
    }

    // ==========================================================================
    // METHOD: sendKey
    // WHAT:  Sends a named key command to the TV (e.g., "KEY_VOLUMEUP").
    // ==========================================================================

    public void sendKey(String keyCode) {
        if (!isConnected()) { Log.w(TAG, "sendKey: not connected"); return; }
        remoteController.sendKey(keyCode);
    }

    // ==========================================================================
    // METHOD: sendInputString
    // WHAT:  Sends a string of text to the TV character by character.
    //        Used for typing on the TV's on-screen keyboard.
    // ==========================================================================

    public void sendInputString(String text) {
        if (!isConnected()) { Log.w(TAG, "sendInputString: not connected"); return; }
        remoteController.sendInputString(text);
    }

    // ==========================================================================
    // METHOD: sendText
    // WHAT:  Sends a text string to the TV (inserts it into a text field).
    // ==========================================================================

    public void sendText(String text) {
        if (!isConnected()) { Log.w(TAG, "sendText: not connected"); return; }
        remoteController.sendText(text);
    }

    // ==========================================================================
    // METHOD: sendText (with replace)
    // WHAT:  Sends text to the TV, optionally replacing all existing text.
    // ==========================================================================

    public void sendText(String text, boolean replaceAll) {
        if (!isConnected()) { Log.w(TAG, "sendText: not connected"); return; }
        remoteController.sendText(text, replaceAll);
    }

    // =========================================================================
    // Mouse / touchpad passthrough
    // =========================================================================

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

    // =========================================================================
    // App launch
    // =========================================================================

    public void sendAppLaunch(String appId) {
        if (!isConnected()) {
            Log.w(TAG, "sendAppLaunch: not connected");
            return;
        }
        remoteController.sendAppLaunch(appId);
    }

    // Helper: only run an action if connected, otherwise log a warning
    private void sendIfConnected(Runnable action, String name) {
        if (isConnected()) action.run();
        else Log.w(TAG, name + ": not connected");
    }

    // =========================================================================
    // Volume / system controls
    // =========================================================================

    public void sendVolumeUp()   { sendIfConnected(() -> remoteController.sendVolumeUp(), "sendVolumeUp"); }
    public void sendVolumeDown() { sendIfConnected(() -> remoteController.sendVolumeDown(), "sendVolumeDown"); }
    public void sendMute()       { sendIfConnected(() -> remoteController.sendMute(), "sendMute"); }
    public void sendHome()       { sendIfConnected(() -> remoteController.sendHome(), "sendHome"); }
    public void sendBack()       { sendIfConnected(() -> remoteController.sendBack(), "sendBack"); }
    public void sendPower()      { sendIfConnected(() -> remoteController.sendPower(), "sendPower"); }

    // =========================================================================
    // Media transport controls
    // =========================================================================

    public void sendMediaPlayPause() { sendIfConnected(() -> remoteController.sendMediaPlayPause(), "sendMediaPlayPause"); }
    public void sendMediaStop()      { sendIfConnected(() -> remoteController.sendMediaStop(), "sendMediaStop"); }
    public void sendMediaPrev()      { sendIfConnected(() -> remoteController.sendMediaPrev(), "sendMediaPrev"); }
    public void sendMediaNext()      { sendIfConnected(() -> remoteController.sendMediaNext(), "sendMediaNext"); }
    public void sendMediaRW()        { sendIfConnected(() -> remoteController.sendMediaRW(), "sendMediaRW"); }
    public void sendMediaFF()        { sendIfConnected(() -> remoteController.sendMediaFF(), "sendMediaFF"); }

    // =========================================================================
    // Getters — provide TV information to the RemoteActivity
    // =========================================================================

    public String getTvIp()             { return tvIp; }
    public String getTvName()           { return tvName; }
    public String getTvMac()            { return tvMac; }

    // ==========================================================================
    // METHOD: getTvWidth / getTvHeight
    // WHAT:  Returns the TV's screen resolution so the touchpad can map
    //        finger movements to the correct position on the TV screen.
    //        If the TV doesn't report its resolution, defaults to 1920x1080.
    // ==========================================================================

    public int getTvWidth()  { return remoteController != null ? remoteController.getTvWidth() : 1920; }
    public int getTvHeight() { return remoteController != null ? remoteController.getTvHeight() : 1080; }

    // =========================================================================
    // UI helpers — notification management
    // =========================================================================

    // Updates the notification text shown in the status bar
    private void updateNotification(String text) {
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.notify(NOTIFICATION_ID, buildNotification(text));
    }

    // Builds the notification that appears while connected
    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, RemoteActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent disconnectIntent = new Intent(this, RemoteService.class);
        disconnectIntent.setAction(ACTION_DISCONNECT);
        PendingIntent disconnectPi = PendingIntent.getService(this, 0, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Freemote")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPi)
            .setOngoing(true)
            .build();
    }

    // Creates a notification channel (required on Android 8+)
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Remote Control", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Remote control connection status");
            NotificationManager mgr = getSystemService(NotificationManager.class);
            if (mgr != null) mgr.createNotificationChannel(channel);
        }
    }
}
