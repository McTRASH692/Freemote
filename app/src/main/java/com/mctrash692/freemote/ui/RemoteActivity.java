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
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.RemoteService;
import com.mctrash692.freemote.ScreenCastingService;
import com.mctrash692.freemote.model.TvDevice;
import com.mctrash692.freemote.remote.samsung.SamsungRemote;
import com.mctrash692.freemote.util.ThemeManager;

public class RemoteActivity extends AppCompatActivity {

    private static final String TAG = "RemoteActivity";
    public static final String EXTRA_IP   = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";

    private static final int CAST_REQUEST_CODE = 1001;
    
    private String tvIp;
    private int tvPort;
    private String tvName;
    private TvDevice.Type tvType;
    private TextView tvDeviceName;
    private TextView tvConnectionStatus;
    private RelativeLayout layoutDpad;
    private FrameLayout layoutTouchpad;
    private SeekBar volumeSeekBar;
    private boolean isTouchpadMode = false;
    private boolean useVolumeSlider = false;
    private SharedPreferences prefs;

    private RemoteService remoteService;
    private boolean isServiceBound = false;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            RemoteService.RemoteBinder binder = (RemoteService.RemoteBinder) service;
            remoteService = binder.getService();
            isServiceBound = true;
            tvConnectionStatus.setText("✓ Connected");
            tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            Toast.makeText(RemoteActivity.this, "Connected to TV", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Service disconnected");
            remoteService = null;
            isServiceBound = false;
            tvConnectionStatus.setText("● Disconnected");
            tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark));
        }
    };

    private final BroadcastReceiver voiceCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra("command");
            if (command != null && remoteService != null) {
                remoteService.sendInputString(command);
                Toast.makeText(RemoteActivity.this, "Voice: " + command, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final int[] shortcutIds = {
        R.id.btnShortcut1, R.id.btnShortcut2, R.id.btnShortcut3,
        R.id.btnShortcut4, R.id.btnShortcut5, R.id.btnShortcut6
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ThemeManager.applyTheme(this);
            setContentView(R.layout.activity_remote);

            Log.d(TAG, "onCreate started");
            
            prefs = getSharedPreferences("remote_prefs", MODE_PRIVATE);
            
            // Get intent extras
            Intent intent = getIntent();
            tvIp = intent.getStringExtra(EXTRA_IP);
            tvPort = intent.getIntExtra(EXTRA_PORT, 8001);
            tvName = intent.getStringExtra(EXTRA_NAME);
            
            Log.d(TAG, "TV IP: " + tvIp + ", Port: " + tvPort + ", Name: " + tvName);
            
            String typeStr = intent.getStringExtra(EXTRA_TYPE);
            if (typeStr != null) {
                tvType = TvDevice.Type.valueOf(typeStr);
            } else {
                tvType = TvDevice.Type.SAMSUNG;
            }
            
            useVolumeSlider = prefs.getBoolean("use_volume_slider", false);
            projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

            // Initialize views
            tvDeviceName = findViewById(R.id.tvDeviceName);
            tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
            layoutDpad = findViewById(R.id.layoutDpad);
            layoutTouchpad = findViewById(R.id.layoutTouchpad);
            volumeSeekBar = findViewById(R.id.volumeSeekBar);

            String savedLabel = prefs.getString("device_label_" + tvIp, null);
            String displayName = savedLabel != null && !savedLabel.isEmpty() ? savedLabel
                    : tvName != null ? tvName : tvIp;
            tvDeviceName.setText(displayName);
            tvConnectionStatus.setText("● Connecting...");
            tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark));

            // Start service
            Intent serviceIntent = new Intent(this, RemoteService.class);
            serviceIntent.setAction(RemoteService.ACTION_CONNECT);
            serviceIntent.putExtra(RemoteService.EXTRA_IP, tvIp);
            serviceIntent.putExtra(RemoteService.EXTRA_PORT, tvPort);
            serviceIntent.putExtra(RemoteService.EXTRA_TYPE, tvType.name());
            serviceIntent.putExtra(RemoteService.EXTRA_NAME, tvName);
            startService(serviceIntent);
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.d(TAG, "Service started and bound");

            // Register receiver
            LocalBroadcastManager.getInstance(this).registerReceiver(voiceCommandReceiver,
                    new IntentFilter("com.mctrash692.freemote.VOICE_COMMAND"));

            // Setup volume slider
            if (useVolumeSlider) {
                volumeSeekBar.setVisibility(View.VISIBLE);
                final int[] lastProgress = {50};
                volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && remoteService != null && remoteService.isConnected()) {
                            if (progress > lastProgress[0]) {
                                remoteService.sendKey("KEY_VOLUMEUP");
                            } else if (progress < lastProgress[0]) {
                                remoteService.sendKey("KEY_VOLUMEDOWN");
                            }
                            lastProgress[0] = progress;
                        }
                    }
                    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
                });
            }

            // Overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                new AlertDialog.Builder(this)
                    .setTitle("Overlay permission needed")
                    .setMessage("Freemote needs overlay permission for the touchpad cursor.")
                    .setPositiveButton("Grant", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)))
                    .setNegativeButton("Skip", null)
                    .show();
            }

            setupButtons();
            setupTouchpad();
            loadShortcuts();
            
            Log.d(TAG, "onCreate completed successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "onCreate error", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void sendKey(String keyCode) {
        if (remoteService != null && remoteService.isConnected()) {
            remoteService.sendKey(keyCode);
        } else {
            Toast.makeText(this, "Not connected to TV", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAppLaunch(String appId) {
        if (remoteService != null) {
            remoteService.sendAppLaunch(appId);
            Toast.makeText(this, "Launching...", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Service not ready", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendInputString(String text) {
        if (remoteService != null && remoteService.isConnected()) {
            remoteService.sendInputString(text);
            Toast.makeText(this, "Sent: " + text, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppPicker() {
        final String[] appNames = {"YouTube", "Prime Video", "Disney+", "Netflix", "Plex", "Kodi"};
        final String[] appIds = {
            SamsungRemote.APP_YOUTUBE,
            SamsungRemote.APP_PRIME,
            SamsungRemote.APP_DISNEY,
            SamsungRemote.APP_NETFLIX,
            SamsungRemote.APP_PLEX,
            SamsungRemote.APP_KODI
        };
        new AlertDialog.Builder(this)
            .setTitle("Select App")
            .setItems(appNames, (dialog, which) -> sendAppLaunch(appIds[which]))
            .show();
    }

    private void showCastPicker() {
        final String[] castOptions = {"Prime Video", "YouTube", "Disney+", "Screen Mirror"};
        final String[] appIds = {
            SamsungRemote.APP_PRIME,
            SamsungRemote.APP_YOUTUBE,
            SamsungRemote.APP_DISNEY,
            "SCREEN_MIRROR"
        };
        new AlertDialog.Builder(this)
            .setTitle("Cast to TV")
            .setItems(castOptions, (dialog, which) -> {
                if (which == 3) {
                    startScreenCasting();
                } else {
                    sendAppLaunch(appIds[which]);
                }
            })
            .show();
    }

    private void startScreenCasting() {
        if (projectionManager != null) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, CAST_REQUEST_CODE);
        } else {
            Toast.makeText(this, "Screen casting not supported", Toast.LENGTH_LONG).show();
        }
    }

    private void showNumberEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Numbers");

        final EditText input = new EditText(this);
        input.setHint("Enter channel number or digits");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String numbers = input.getText().toString();
            for (char c : numbers.toCharArray()) {
                switch (c) {
                    case '0': sendKey("KEY_0"); break;
                    case '1': sendKey("KEY_1"); break;
                    case '2': sendKey("KEY_2"); break;
                    case '3': sendKey("KEY_3"); break;
                    case '4': sendKey("KEY_4"); break;
                    case '5': sendKey("KEY_5"); break;
                    case '6': sendKey("KEY_6"); break;
                    case '7': sendKey("KEY_7"); break;
                    case '8': sendKey("KEY_8"); break;
                    case '9': sendKey("KEY_9"); break;
                }
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void toggleVolumeControl() {
        useVolumeSlider = !useVolumeSlider;
        prefs.edit().putBoolean("use_volume_slider", useVolumeSlider).apply();
        volumeSeekBar.setVisibility(useVolumeSlider ? View.VISIBLE : View.GONE);
        Toast.makeText(this, useVolumeSlider ? "Volume Slider ON" : "Volume Buttons ON", Toast.LENGTH_SHORT).show();
    }

    private void voiceCommand() {
        Intent intent = new Intent(this, VoiceCommandActivity.class);
        intent.putExtra("tv_ip", tvIp);
        startActivity(intent);
    }

    private void loadShortcuts() {
        for (int i = 0; i < 6; i++) {
            final int slot = i + 1;
            ImageButton btn = findViewById(shortcutIds[i]);
            String appId = prefs.getString("slot_app_" + slot, null);
            String key = prefs.getString("slot_key_" + slot, null);
            String iconName = prefs.getString("slot_icon_" + slot, null);

            if (iconName != null) {
                int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
                btn.setImageResource(resId != 0 ? resId : android.R.drawable.ic_menu_edit);
            }

            btn.setOnClickListener(v -> {
                if (appId != null) {
                    sendAppLaunch(appId);
                } else if (key != null) {
                    sendKey(key);
                } else {
                    openSettings();
                }
            });
            btn.setOnLongClickListener(v -> { openSettings(); return true; });
        }
    }

    private void showKeyboardDialog() {
        EditText et = new EditText(this);
        et.setHint("Type text to send to TV");
        new AlertDialog.Builder(this)
            .setTitle("Send Text")
            .setView(et)
            .setPositiveButton("Send", (d, w) -> {
                String text = et.getText().toString();
                if (!text.isEmpty()) sendInputString(text);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupTouchpad() {
        layoutTouchpad.setOnTouchListener((v, event) -> {
            if (!isTouchpadMode) return false;
            return true;
        });
    }

    private void toggleNavMode() {
        isTouchpadMode = !isTouchpadMode;
        layoutDpad.setVisibility(isTouchpadMode ? View.GONE : View.VISIBLE);
        layoutTouchpad.setVisibility(isTouchpadMode ? View.VISIBLE : View.GONE);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("tv_ip", tvIp);
        startActivity(intent);
    }

    private void setupButtons() {
        findViewById(R.id.btnSettings).setOnClickListener(v -> openSettings());
        findViewById(R.id.btnPower).setOnClickListener(v -> sendKey("KEY_POWER"));
        findViewById(R.id.btnMute).setOnClickListener(v -> sendKey("KEY_MUTE"));
        findViewById(R.id.btnInfo).setOnClickListener(v -> sendKey("KEY_INFO"));
        findViewById(R.id.btnGuide).setOnClickListener(v -> sendKey("KEY_GUIDE"));
        findViewById(R.id.btnMenu).setOnClickListener(v -> sendKey("KEY_MENU"));
        findViewById(R.id.btnVolUp).setOnClickListener(v -> sendKey("KEY_VOLUMEUP"));
        findViewById(R.id.btnVolDown).setOnClickListener(v -> sendKey("KEY_VOLUMEDOWN"));
        findViewById(R.id.btnChUp).setOnClickListener(v -> sendKey("KEY_CHUP"));
        findViewById(R.id.btnChDown).setOnClickListener(v -> sendKey("KEY_CHDOWN"));
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
        findViewById(R.id.btnKeyboard).setOnClickListener(v -> showKeyboardDialog());
        findViewById(R.id.btnNumberPad).setOnClickListener(v -> showNumberEntry());
        findViewById(R.id.btnVoice).setOnClickListener(v -> voiceCommand());
        findViewById(R.id.btnToggleVolume).setOnClickListener(v -> toggleVolumeControl());
        findViewById(R.id.btnToggleNavMode).setOnClickListener(v -> toggleNavMode());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAST_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            
            android.view.WindowManager windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            android.view.Display display = windowManager.getDefaultDisplay();
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            display.getMetrics(metrics);
            
            Intent serviceIntent = new Intent(this, ScreenCastingService.class);
            serviceIntent.setAction("START_CASTING");
            serviceIntent.putExtra("tv_ip", tvIp);
            serviceIntent.putExtra("width", metrics.widthPixels);
            serviceIntent.putExtra("height", metrics.heightPixels);
            serviceIntent.putExtra("dpi", metrics.densityDpi);
            startService(serviceIntent);
            
            Toast.makeText(this, "Screen casting started", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
            } catch (Exception e) {
                Log.e(TAG, "Error unbinding service", e);
            }
        }
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceCommandReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
}
