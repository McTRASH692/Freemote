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
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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

public class RemoteActivity extends BaseActivity {

    private static final String TAG = "RemoteActivity";
    public static final String EXTRA_IP   = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";

    private static final int CAST_REQUEST_CODE = 1001;

    // Static holder so ScreenCastingService can retrieve the MediaProjection
    // token — it cannot cross an Intent boundary.
    public static volatile MediaProjection pendingProjection;

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

    // Handler for scheduling key presses off the main thread.
    private final Handler keyHandler = new Handler(Looper.getMainLooper());

    // Touchpad gesture state
    private GestureDetector touchpadGestureDetector;
    private static final int MOVE_STEP = 20; // pixels per gesture unit sent to cursor service

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
        setContentView(R.layout.activity_remote);

        Log.d(TAG, "onCreate started");

        prefs = getSharedPreferences("remote_prefs", MODE_PRIVATE);

        Intent intent = getIntent();
        tvIp   = intent.getStringExtra(EXTRA_IP);
        tvPort = intent.getIntExtra(EXTRA_PORT, 8001);
        tvName = intent.getStringExtra(EXTRA_NAME);

        Log.d(TAG, "TV IP: " + tvIp + ", Port: " + tvPort + ", Name: " + tvName);

        String typeStr = intent.getStringExtra(EXTRA_TYPE);
        tvType = TvDevice.Type.SAMSUNG;
        if (typeStr != null) {
            try {
                tvType = TvDevice.Type.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Unknown TV type '" + typeStr + "', defaulting to SAMSUNG");
            }
        }

        useVolumeSlider = prefs.getBoolean("use_volume_slider", false);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        tvDeviceName       = findViewById(R.id.tvDeviceName);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        layoutDpad         = findViewById(R.id.layoutDpad);
        layoutTouchpad     = findViewById(R.id.layoutTouchpad);
        volumeSeekBar      = findViewById(R.id.volumeSeekBar);

        String savedLabel = prefs.getString("device_label_" + tvIp, null);
        String displayName = savedLabel != null && !savedLabel.isEmpty() ? savedLabel
                : tvName != null ? tvName : tvIp;
        tvDeviceName.setText(displayName);
        tvConnectionStatus.setText("● Connecting...");
        tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark));

        Intent serviceIntent = new Intent(this, RemoteService.class);
        serviceIntent.setAction(RemoteService.ACTION_CONNECT);
        serviceIntent.putExtra(RemoteService.EXTRA_IP,   tvIp);
        serviceIntent.putExtra(RemoteService.EXTRA_PORT, tvPort);
        serviceIntent.putExtra(RemoteService.EXTRA_TYPE, tvType.name());
        serviceIntent.putExtra(RemoteService.EXTRA_NAME, tvName);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "Service started and bound");

        LocalBroadcastManager.getInstance(this).registerReceiver(voiceCommandReceiver,
                new IntentFilter("com.mctrash692.freemote.VOICE_COMMAND"));

        setupVolumeSlider();

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
        refreshIcons();

        Log.d(TAG, "onCreate completed successfully");
    }

    // ── Volume slider ────────────────────────────────────────────────────────

    private void setupVolumeSlider() {
        if (!useVolumeSlider) {
            volumeSeekBar.setVisibility(View.GONE);
            return;
        }
        volumeSeekBar.setVisibility(View.VISIBLE);
        // FIX: set progress to midpoint so thumb starts centred, not at position 0.
        volumeSeekBar.setMax(100);
        volumeSeekBar.setProgress(50);

        final int[] lastProgress = {50};
        // Debounce: only send a key every 150 ms regardless of how many steps the drag covers.
        final long[] lastSentAt = {0L};

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || remoteService == null || !remoteService.isConnected()) return;
                long now = System.currentTimeMillis();
                if (now - lastSentAt[0] < 150) return; // debounce
                if (progress > lastProgress[0]) {
                    remoteService.sendKey("KEY_VOLUMEUP");
                } else if (progress < lastProgress[0]) {
                    remoteService.sendKey("KEY_VOLUMEDOWN");
                }
                lastProgress[0] = progress;
                lastSentAt[0]   = now;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ── Key / app helpers ────────────────────────────────────────────────────

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

    // ── Pickers ──────────────────────────────────────────────────────────────

    private void showAppPicker() {
        final String[] appNames = {"YouTube", "Prime Video", "Disney+", "Netflix", "Spotify", "HBO Max", "Plex", "Kodi"};
        final String[] appIds = {
            SamsungRemote.APP_YOUTUBE,
            SamsungRemote.APP_PRIME,
            SamsungRemote.APP_DISNEY,
            SamsungRemote.APP_NETFLIX,
            SamsungRemote.APP_SPOTIFY,
            SamsungRemote.APP_HBOMAX,
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

    // ── Screen casting ───────────────────────────────────────────────────────

    private void startScreenCasting() {
        if (projectionManager != null) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, CAST_REQUEST_CODE);
        } else {
            Toast.makeText(this, "Screen casting not supported", Toast.LENGTH_LONG).show();
        }
    }

    // ── Number entry ─────────────────────────────────────────────────────────

    private void showNumberEntry() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Numbers");

        final EditText input = new EditText(this);
        input.setHint("Enter channel number or digits");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Send", (dialog, which) -> {
            String numbers = input.getText().toString();
            char[] digits = numbers.toCharArray();
            for (int i = 0; i < digits.length; i++) {
                final char c = digits[i];
                keyHandler.postDelayed(() -> {
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
                }, (long) i * 100);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // ── Volume toggle ────────────────────────────────────────────────────────

    private void toggleVolumeControl() {
        useVolumeSlider = !useVolumeSlider;
        prefs.edit().putBoolean("use_volume_slider", useVolumeSlider).apply();
        if (useVolumeSlider) {
            volumeSeekBar.setVisibility(View.VISIBLE);
            volumeSeekBar.setMax(100);
            volumeSeekBar.setProgress(50);
        } else {
            volumeSeekBar.setVisibility(View.GONE);
        }
        Toast.makeText(this, useVolumeSlider ? "Volume Slider ON" : "Volume Buttons ON", Toast.LENGTH_SHORT).show();
    }

    // ── Voice ────────────────────────────────────────────────────────────────

    private void voiceCommand() {
        Intent intent = new Intent(this, VoiceCommandActivity.class);
        intent.putExtra("tv_ip", tvIp);
        startActivity(intent);
    }

    // ── Keyboard dialog ──────────────────────────────────────────────────────

    /**
     * Shows a phone-side text input dialog. The EditText gets focus and the
     * soft keyboard is raised to it automatically. On Send, the text is
     * dispatched via Samsung's SendInputString command which types into
     * whichever input field is currently focused on the TV.
     */
    private void showKeyboardDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send Text to TV");

        final EditText et = new EditText(this);
        et.setHint("Type text to send to TV");
        et.setSingleLine(false);
        et.setMinLines(2);
        builder.setView(et);

        builder.setPositiveButton("Send", (d, w) -> {
            String text = et.getText().toString();
            if (!text.isEmpty()) sendInputString(text);
        });
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        // Raise the soft keyboard automatically when the dialog opens.
        dialog.setOnShowListener(d -> {
            et.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    // ── Touchpad ─────────────────────────────────────────────────────────────

    private void setupTouchpad() {
        touchpadGestureDetector = new GestureDetector(this,
            new GestureDetector.SimpleOnGestureListener() {

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                        float distanceX, float distanceY) {
                    // Map swipe direction to D-pad keys and move the cursor overlay.
                    int dx = Math.round(-distanceX);
                    int dy = Math.round(-distanceY);

                    // Send cursor move to overlay service (best-effort — may not be running).
                    Intent moveIntent = new Intent(RemoteActivity.this, MouseCursorService.class);
                    moveIntent.putExtra("action", "MOVE");
                    moveIntent.putExtra("dx", dx * 3);
                    moveIntent.putExtra("dy", dy * 3);
                    startService(moveIntent);

                    // Also send D-pad keys so navigation works without overlay permission.
                    if (Math.abs(distanceX) > Math.abs(distanceY)) {
                        sendKey(distanceX > 0 ? "KEY_LEFT" : "KEY_RIGHT");
                    } else {
                        sendKey(distanceY > 0 ? "KEY_UP" : "KEY_DOWN");
                    }
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    // Double-tap = select / OK.
                    sendKey("KEY_ENTER");
                    Intent clickIntent = new Intent(RemoteActivity.this, MouseCursorService.class);
                    clickIntent.putExtra("action", "CLICK");
                    startService(clickIntent);
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    sendKey("KEY_ENTER");
                    return true;
                }
            });

        layoutTouchpad.setOnTouchListener((v, event) -> {
            if (!isTouchpadMode) return false;
            touchpadGestureDetector.onTouchEvent(event);
            return true;
        });
    }

    // ── Nav mode toggle ──────────────────────────────────────────────────────

    private void toggleNavMode() {
        isTouchpadMode = !isTouchpadMode;
        layoutDpad.setVisibility(isTouchpadMode ? View.GONE : View.VISIBLE);
        layoutTouchpad.setVisibility(isTouchpadMode ? View.VISIBLE : View.GONE);

        // Start/stop the cursor overlay service alongside the mode switch.
        Intent cursorIntent = new Intent(this, MouseCursorService.class);
        if (isTouchpadMode) {
            cursorIntent.putExtra("action", "SHOW");
            startService(cursorIntent);
        } else {
            cursorIntent.putExtra("action", "HIDE");
            startService(cursorIntent);
        }
    }

    // ── Shortcuts ────────────────────────────────────────────────────────────

    private void loadShortcuts() {
        // Slot default app IDs — map directly to verified working IDs.
        final String[] defaultAppIds = {
            SamsungRemote.APP_YOUTUBE,   // slot 1
            SamsungRemote.APP_PRIME,     // slot 2
            SamsungRemote.APP_DISNEY,    // slot 3
            SamsungRemote.APP_SPOTIFY,   // slot 4
            SamsungRemote.APP_HBOMAX,    // slot 5
            SamsungRemote.APP_NETFLIX    // slot 6
        };
        final String[] defaultIcons = {
            "ic_youtube", "ic_prime", "ic_disney",
            "ic_spotify", "ic_hbomax", "ic_netflix"
        };

        for (int i = 0; i < 6; i++) {
            final int slot       = i + 1;
            final String defApp  = defaultAppIds[i];
            final String defIcon = defaultIcons[i];
            ImageButton btn = findViewById(shortcutIds[i]);

            String appId    = prefs.getString("slot_app_"  + slot, defApp);
            String key      = prefs.getString("slot_key_"  + slot, null);
            String iconName = prefs.getString("slot_icon_" + slot, defIcon);

            int resId = getResources().getIdentifier(iconName, "drawable", getPackageName());
            btn.setImageResource(resId != 0 ? resId : android.R.drawable.ic_menu_edit);
            btn.setTag("brand_icon"); // preserves original icon colours in BaseActivity

            final String finalAppId = appId;
            final String finalKey   = key;
            btn.setOnClickListener(v -> {
                if (finalAppId != null && !finalAppId.isEmpty()) {
                    sendAppLaunch(finalAppId);
                } else if (finalKey != null) {
                    sendKey(finalKey);
                } else {
                    openSettings();
                }
            });
            btn.setOnLongClickListener(v -> { openSettings(); return true; });
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.putExtra("tv_ip", tvIp);
        startActivity(intent);
    }

    // ── Button wiring ─────────────────────────────────────────────────────────

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

    private void refreshIcons() {
        int[] ids = {
            R.id.btnPower, R.id.btnSettings, R.id.btnInfo, R.id.btnGuide, R.id.btnMenu,
            R.id.btnVolUp, R.id.btnVolDown, R.id.btnMute, R.id.btnChUp, R.id.btnChDown,
            R.id.btnDpadUp, R.id.btnDpadDown, R.id.btnDpadLeft, R.id.btnDpadRight, R.id.btnOk,
            R.id.btnBack, R.id.btnHome, R.id.btnSource, R.id.btnApps, R.id.btnCast,
            R.id.btnKeyboard, R.id.btnNumberPad, R.id.btnVoice, R.id.btnToggleVolume,
            R.id.btnToggleNavMode,
            R.id.btnShortcut1, R.id.btnShortcut2, R.id.btnShortcut3,
            R.id.btnShortcut4, R.id.btnShortcut5, R.id.btnShortcut6
        };
        for (int id : ids) {
            ImageButton btn = findViewById(id);
            if (btn != null) btn.setImageDrawable(btn.getDrawable());
        }
    }

    // ── onActivityResult (screen cast) ───────────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAST_REQUEST_CODE) {
            if (resultCode != RESULT_OK || data == null) {
                Toast.makeText(this, "Screen cast permission denied", Toast.LENGTH_SHORT).show();
                return;
            }
            MediaProjection projection = projectionManager.getMediaProjection(resultCode, data);
            RemoteActivity.pendingProjection = projection;

            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            Intent serviceIntent = new Intent(this, ScreenCastingService.class);
            serviceIntent.setAction("START_CASTING");
            serviceIntent.putExtra("tv_ip",  tvIp);
            serviceIntent.putExtra("width",  metrics.widthPixels);
            serviceIntent.putExtra("height", metrics.heightPixels);
            serviceIntent.putExtra("dpi",    metrics.densityDpi);
            startService(serviceIntent);

            Toast.makeText(this, "Screen casting started", Toast.LENGTH_LONG).show();
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pendingProjection = null;
        keyHandler.removeCallbacksAndMessages(null);
        if (isServiceBound) {
            try { unbindService(serviceConnection); } catch (Exception e) {
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
