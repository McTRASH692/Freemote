package com.mctrash692.freemote.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.RemoteService;
import com.mctrash692.freemote.ScreenCastingService;
import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.remote.samsung.SamsungRemote;
import com.mctrash692.freemote.util.PairedDevicesManager;
import com.mctrash692.freemote.util.WakeOnLan;

import java.util.Map;

public class RemoteActivity extends BaseActivity {

    private static final String TAG = "RemoteActivity";
    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    public static final String EXTRA_IP = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_MAC = "extra_mac";
    private static final int CAST_REQUEST_CODE = 1001;

    public static volatile MediaProjection pendingProjection;

    private TextView tvDeviceName;
    private TextView tvConnectionStatus;
    private RelativeLayout layoutDpad;
    private FrameLayout layoutTouchpad;
    private EditText hiddenKeyboardInput;
    
    private boolean isTouchpadMode = false;
    private SharedPreferences prefs;
    private String deviceId;
    private String tvIp;
    private int tvPort;
    private String tvName;
    private TvDevice.Type tvType;
    private String tvMac;
    private boolean deviceSaved = false;
    private PairedDevice pairedDevice;
    private PairedDevicesManager pairedDevicesManager;

    private RemoteService remoteService;
    private boolean isServiceBound = false;
    private MediaProjectionManager projectionManager;

    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private long lastTouchTime = 0;
    private static final long LONG_PRESS_TIMEOUT = 500;

    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    private static final int REPEAT_INITIAL_MS = 400;
    private static final int REPEAT_DELAY_MS = 150;
    private Runnable activeRepeatRunnable = null;

    private final int[] shortcutIds = {
        R.id.btnShortcut1, R.id.btnShortcut2, R.id.btnShortcut3,
        R.id.btnShortcut4, R.id.btnShortcut5, R.id.btnShortcut6
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RemoteService.RemoteBinder binder = (RemoteService.RemoteBinder) service;
            remoteService = binder.getService();
            isServiceBound = true;
            updateConnectionStatus(true, "Connected");
            saveCurrentDevice();
            loadPerDeviceSettings();
            Log.d(TAG, "Service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remoteService = null;
            isServiceBound = false;
            updateConnectionStatus(false, "Disconnected");
            Log.d(TAG, "Service disconnected");
        }
    };

    private final BroadcastReceiver voiceCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra("command");
            if (command != null && remoteService != null && remoteService.isConnected()) {
                remoteService.sendText(command);
                Toast.makeText(RemoteActivity.this, "Sent: " + command, Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        Log.d(TAG, "onCreate started");

        prefs = getSharedPreferences("remote_prefs", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        pairedDevicesManager = new PairedDevicesManager(this);

        Intent intent = getIntent();
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID);
        tvIp = intent.getStringExtra(EXTRA_IP);
        tvPort = intent.getIntExtra(EXTRA_PORT, 8002);
        tvName = intent.getStringExtra(EXTRA_NAME);
        tvMac = intent.getStringExtra(EXTRA_MAC);
        
        String typeStr = intent.getStringExtra(EXTRA_TYPE);
        if (typeStr != null) {
            try {
                tvType = TvDevice.Type.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                tvType = TvDevice.Type.SAMSUNG;
            }
        } else {
            tvType = TvDevice.Type.SAMSUNG;
        }
        
        if (deviceId != null) {
            pairedDevice = pairedDevicesManager.getDeviceById(deviceId);
            if (pairedDevice != null) {
                tvIp = pairedDevice.getIpAddress();
                tvPort = pairedDevice.getPort();
                tvName = pairedDevice.getName();
                tvType = pairedDevice.getType();
                tvMac = pairedDevice.getMacAddress();
                Log.d(TAG, "Loaded paired device: " + tvName);
            }
        } else if (tvIp != null) {
            pairedDevice = pairedDevicesManager.getDeviceByIp(tvIp);
            if (pairedDevice != null) {
                deviceId = pairedDevice.getDeviceId();
                tvName = pairedDevice.getName();
                tvMac = pairedDevice.getMacAddress();
            }
        }

        tvDeviceName = findViewById(R.id.tvDeviceName);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        layoutDpad = findViewById(R.id.layoutDpad);
        layoutTouchpad = findViewById(R.id.layoutTouchpad);
        hiddenKeyboardInput = findViewById(R.id.hiddenKeyboardInput);
        Log.d(TAG, "Views inflated");

        tvDeviceName.setText(tvName != null ? tvName : tvIp);
        updateConnectionStatus(false, "Connecting...");

        Intent serviceIntent = new Intent(this, RemoteService.class);
        serviceIntent.setAction(RemoteService.ACTION_CONNECT);
        serviceIntent.putExtra(RemoteService.EXTRA_IP, tvIp);
        serviceIntent.putExtra(RemoteService.EXTRA_PORT, tvPort);
        serviceIntent.putExtra(RemoteService.EXTRA_TYPE, tvType.name());
        serviceIntent.putExtra(RemoteService.EXTRA_NAME, tvName);
        serviceIntent.putExtra(RemoteService.EXTRA_DEVICE_ID, deviceId);
        serviceIntent.putExtra(RemoteService.EXTRA_MAC, tvMac);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(this).registerReceiver(voiceCommandReceiver,
            new IntentFilter("com.mctrash692.freemote.VOICE_COMMAND"));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay permission needed")
                .setMessage("Freemote needs overlay permission for the touchpad cursor.")
                .setPositiveButton("Grant", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)))
                .setNegativeButton("Skip", null)
                .show();
        }

        setupHiddenKeyboard();
        setupButtons();
        setupTouchpad();
        loadShortcuts();
        Log.d(TAG, "onCreate finished");
    }

    private void loadPerDeviceSettings() {
        if (pairedDevice == null) return;
        int sensitivity = pairedDevice.getTouchpadSensitivity();
        prefs.edit().putInt(SettingsActivity.KEY_SENSITIVITY, sensitivity).apply();
        Map<Integer, String> shortcuts = pairedDevice.getShortcuts();
        if (shortcuts != null && !shortcuts.isEmpty()) {
            for (Map.Entry<Integer, String> entry : shortcuts.entrySet()) {
                prefs.edit().putString(SettingsActivity.KEY_SLOT_APPID + entry.getKey(), entry.getValue()).apply();
            }
        }
        loadShortcuts();
    }

    private void savePerDeviceSettings() {
        if (pairedDevice == null) return;
        pairedDevice.setTouchpadSensitivity(prefs.getInt(SettingsActivity.KEY_SENSITIVITY, 15));
        for (int slot = 1; slot <= 6; slot++) {
            String appId = prefs.getString(SettingsActivity.KEY_SLOT_APPID + slot, null);
            if (appId != null) pairedDevice.setShortcut(slot, appId);
        }
        pairedDevicesManager.saveDevice(pairedDevice);
    }

    private void saveCurrentDevice() {
        if (deviceSaved) return;
        deviceSaved = true;
        if (pairedDevice == null) {
            pairedDevice = new PairedDevice();
            pairedDevice.setName(tvName != null ? tvName : tvIp);
            pairedDevice.setIpAddress(tvIp);
            pairedDevice.setPort(tvPort);
            pairedDevice.setType(tvType);
            pairedDevice.setMacAddress(tvMac);
        }
        pairedDevice.updateLastUsed();
        pairedDevicesManager.saveDevice(pairedDevice);
        deviceId = pairedDevice.getDeviceId();
    }

    private void updateConnectionStatus(boolean connected, String message) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText(message);
            tvConnectionStatus.setTextColor(connected ? 
                getColor(android.R.color.holo_green_dark) : 
                getColor(android.R.color.holo_orange_dark));
        });
    }

    private void setupHiddenKeyboard() {
        hiddenKeyboardInput.setFocusable(true);
        hiddenKeyboardInput.setFocusableInTouchMode(true);
        hiddenKeyboardInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        
        hiddenKeyboardInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                if (text.length() > 0 && remoteService != null && remoteService.isConnected()) {
                    Log.d(TAG, "sendText: " + text);
                    remoteService.sendText(text);
                    hiddenKeyboardInput.setText("");
                }
            }
        });
    }

    private void openKeyboard() {
        Log.d(TAG, "openKeyboard called");
        
        hiddenKeyboardInput.setVisibility(View.VISIBLE);
        hiddenKeyboardInput.requestFocus();
        
        hiddenKeyboardInput.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                boolean result = imm.showSoftInput(hiddenKeyboardInput, InputMethodManager.SHOW_FORCED);
                Log.d(TAG, "showSoftInput result: " + result);
                if (result) {
                    Toast.makeText(this, "Keyboard opened", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to open keyboard", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setRepeatListener(View btn, String keyCode) {
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    sendKey(keyCode);
                    activeRepeatRunnable = new Runnable() {
                        @Override
                        public void run() {
                            sendKey(keyCode);
                            keyHandler.postDelayed(this, REPEAT_DELAY_MS);
                        }
                    };
                    keyHandler.postDelayed(activeRepeatRunnable, REPEAT_INITIAL_MS);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (activeRepeatRunnable != null) {
                        keyHandler.removeCallbacks(activeRepeatRunnable);
                        activeRepeatRunnable = null;
                    }
                    return true;
            }
            return false;
        });
    }

    private void sendKey(String keyCode) {
        if (remoteService != null && remoteService.isConnected()) {
            remoteService.sendKey(keyCode);
            Log.d(TAG, "sendKey: " + keyCode);
        } else {
            Toast.makeText(this, "Not connected to TV", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAppLaunch(String appId) {
        if (remoteService != null && remoteService.isConnected()) {
            remoteService.sendAppLaunch(appId);
            Toast.makeText(this, "Launching...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Not connected to TV", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupButtons() {
        findViewById(R.id.btnSettings).setOnClickListener(v -> openSettings());
        
        View btnPower = findViewById(R.id.btnPower);
        btnPower.setOnClickListener(v -> sendKey("KEY_POWER"));
        btnPower.setOnLongClickListener(v -> {
            String mac = tvMac;
            if (mac == null && pairedDevice != null) mac = pairedDevice.getMacAddress();
            WakeOnLan.send(tvIp, mac, msg ->
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()));
            Toast.makeText(this, "Wake-on-LAN sent", Toast.LENGTH_SHORT).show();
            return true;
        });

        setRepeatListener(findViewById(R.id.btnVolUp), "KEY_VOLUMEUP");
        setRepeatListener(findViewById(R.id.btnVolDown), "KEY_VOLUMEDOWN");
        setRepeatListener(findViewById(R.id.btnChUp), "KEY_CHUP");
        setRepeatListener(findViewById(R.id.btnChDown), "KEY_CHDOWN");

        findViewById(R.id.btnMute).setOnClickListener(v -> sendKey("KEY_MUTE"));
        findViewById(R.id.btnInfo).setOnClickListener(v -> sendKey("KEY_INFO"));
        findViewById(R.id.btnGuide).setOnClickListener(v -> sendKey("KEY_GUIDE"));
        findViewById(R.id.btnMenu).setOnClickListener(v -> sendKey("KEY_MENU"));
        
        findViewById(R.id.btnDpadUp).setOnClickListener(v -> sendKey("KEY_UP"));
        findViewById(R.id.btnDpadDown).setOnClickListener(v -> sendKey("KEY_DOWN"));
        findViewById(R.id.btnDpadLeft).setOnClickListener(v -> sendKey("KEY_LEFT"));
        findViewById(R.id.btnDpadRight).setOnClickListener(v -> sendKey("KEY_RIGHT"));
        findViewById(R.id.btnOk).setOnClickListener(v -> sendKey("KEY_ENTER"));
        
        findViewById(R.id.btnBack).setOnClickListener(v -> sendKey("KEY_RETURN"));
        findViewById(R.id.btnHome).setOnClickListener(v -> sendKey("KEY_HOME"));
        findViewById(R.id.btnSource).setOnClickListener(v -> sendKey("KEY_SOURCE"));
        
        findViewById(R.id.btnApps).setOnClickListener(v -> showAppPicker());
        findViewById(R.id.btnCast).setOnClickListener(v -> showCastPicker());
        
        View keyboardBtn = findViewById(R.id.btnKeyboard);
        if (keyboardBtn != null) {
            keyboardBtn.setOnClickListener(v -> openKeyboard());
            Log.d(TAG, "Keyboard button found and listener set");
        } else {
            Log.e(TAG, "Keyboard button not found!");
        }
        
        findViewById(R.id.btnNumberPad).setOnClickListener(v -> showNumberEntry());
        findViewById(R.id.btnVoice).setOnClickListener(v -> voiceCommand());
        
        View toggleBtn = findViewById(R.id.btnToggleNavMode);
        if (toggleBtn != null) {
            toggleBtn.setOnClickListener(v -> toggleNavMode());
            Log.d(TAG, "Toggle button found");
        } else {
            Log.e(TAG, "Toggle button not found!");
        }
        
        findViewById(R.id.btnToggleVolume).setOnClickListener(v -> {
            Toast.makeText(this, "Use VOL buttons", Toast.LENGTH_SHORT).show();
        });
    }

    private void showAppPicker() {
        final String[] names = {"YouTube", "Prime Video", "Disney+", "Netflix", "HBO Max", "Plex", "Kodi"};
        final String[] ids = {
            SamsungRemote.APP_YOUTUBE, SamsungRemote.APP_PRIME, SamsungRemote.APP_DISNEY,
            SamsungRemote.APP_NETFLIX, SamsungRemote.APP_HBOMAX, SamsungRemote.APP_PLEX, SamsungRemote.APP_KODI
        };
        new AlertDialog.Builder(this)
            .setTitle("Select App")
            .setItems(names, (d, w) -> sendAppLaunch(ids[w]))
            .show();
    }

    private void showCastPicker() {
        final String[] opts = {"Prime Video", "YouTube", "Disney+", "Screen Mirror"};
        final String[] ids = {SamsungRemote.APP_PRIME, SamsungRemote.APP_YOUTUBE, SamsungRemote.APP_DISNEY, "SCREEN_MIRROR"};
        new AlertDialog.Builder(this)
            .setTitle("Cast to TV")
            .setItems(opts, (d, w) -> {
                if (w == 3) {
                    startScreenCasting();
                } else {
                    sendAppLaunch(ids[w]);
                }
            })
            .show();
    }

    private void startScreenCasting() {
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), CAST_REQUEST_CODE);
        } else {
            Toast.makeText(this, "Screen casting not supported", Toast.LENGTH_LONG).show();
        }
    }

    private void showNumberEntry() {
        final EditText input = new EditText(this);
        input.setHint("Enter channel number");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
            .setTitle("Enter Numbers")
            .setView(input)
            .setPositiveButton("Send", (d, w) -> {
                for (char c : input.getText().toString().toCharArray()) {
                    sendKey("KEY_" + c);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void voiceCommand() {
        Intent intent = new Intent(this, VoiceCommandActivity.class);
        intent.putExtra("tv_ip", tvIp);
        startActivity(intent);
    }

    private void toggleNavMode() {
        isTouchpadMode = !isTouchpadMode;
        layoutDpad.setVisibility(isTouchpadMode ? View.GONE : View.VISIBLE);
        layoutTouchpad.setVisibility(isTouchpadMode ? View.VISIBLE : View.GONE);
        Log.d(TAG, "toggleNavMode: isTouchpadMode=" + isTouchpadMode);
        Toast.makeText(this, isTouchpadMode ? "Touchpad ON" : "D-Pad ON", Toast.LENGTH_SHORT).show();
        if (isTouchpadMode && remoteService != null && remoteService.isConnected()) {
            remoteService.sendMouseActivate();
        }
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void loadShortcuts() {
        final String[] defaultAppIds = {
            SamsungRemote.APP_YOUTUBE, SamsungRemote.APP_PRIME, SamsungRemote.APP_DISNEY,
            SamsungRemote.APP_HBOMAX, SamsungRemote.APP_PLEX, SamsungRemote.APP_NETFLIX
        };
        final String[] defaultIcons = {"youtube", "primevideo", "disneyplus", "hbomax", "plex", "netflix"};

        for (int i = 0; i < 6; i++) {
            final int slot = i + 1;
            ImageButton btn = findViewById(shortcutIds[i]);
            String appId = prefs.getString(SettingsActivity.KEY_SLOT_APPID + slot, defaultAppIds[i]);
            String iconName = prefs.getString(SettingsActivity.KEY_SLOT_ICON + slot, defaultIcons[i]);
            int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
            btn.setImageResource(resId != 0 ? resId : R.drawable.default_icon);
            btn.setOnClickListener(v -> sendAppLaunch(appId));
            btn.setOnLongClickListener(v -> { openSettings(); return true; });
        }
    }

    private void setupTouchpad() {
        Log.d(TAG, "setupTouchpad called, layoutTouchpad=" + layoutTouchpad);
        if (layoutTouchpad == null) {
            Log.e(TAG, "layoutTouchpad is null!");
            return;
        }
        
        layoutTouchpad.setOnTouchListener((v, event) -> {
            if (!isTouchpadMode) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    lastTouchTime = System.currentTimeMillis();
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        float sens = 0.5f + (prefs.getInt(SettingsActivity.KEY_SENSITIVITY, 15) / 10.0f);
                        int moveX = Math.round(dx * sens);
                        int moveY = Math.round(dy * sens);
                        if (remoteService != null && remoteService.isConnected()) {
                            remoteService.sendMouseMove(moveX, moveY);
                        }
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - lastTouchTime;
                    if (remoteService != null && remoteService.isConnected()) {
                        if (duration < LONG_PRESS_TIMEOUT) {
                            remoteService.sendMouseClick("Left");
                        } else {
                            remoteService.sendMouseClick("Right");
                        }
                    }
                    return true;
            }
            return false;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAST_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            pendingProjection = projectionManager.getMediaProjection(resultCode, data);
            
            Intent intent = new Intent(this, ScreenCastingService.class);
            intent.setAction("START_CASTING");
            intent.putExtra("tv_ip", tvIp);
            
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            android.util.DisplayMetrics m = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(m);
            intent.putExtra("width", m.widthPixels);
            intent.putExtra("height", m.heightPixels);
            intent.putExtra("dpi", m.densityDpi);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Toast.makeText(this, "Screen casting started", Toast.LENGTH_LONG).show();
        } else if (requestCode == CAST_REQUEST_CODE) {
            Toast.makeText(this, "Screen cast permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePerDeviceSettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pendingProjection = null;
        keyHandler.removeCallbacksAndMessages(null);
        if (isServiceBound) {
            try { unbindService(serviceConnection); } catch (Exception ignored) {}
        }
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceCommandReceiver); } catch (Exception ignored) {}
    }
}
