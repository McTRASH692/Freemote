package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: RemoteActivity.java
// WHAT:  The main remote control screen. This is where you actually control
//        your TV. You can press D-pad buttons, use a touchpad to move the
//        mouse, play/pause/rewind/fast-forward media, type text using your
//        phone's keyboard, launch apps (YouTube, Netflix, etc.), use voice
//        commands, and cast your phone screen to the TV. It connects to
//        your TV over WiFi and sends every command through a background
//        service that keeps the connection alive.
// BEHAVIOR: This screen shows a D-pad by default but can switch to a
//        touchpad mode or media controls mode. The keyboard uses a hidden
//        text field so your phone's keyboard pops up but nothing visible
//        appears on this screen — the typed text goes straight to the TV.
// ============================================================================

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
import android.view.inputmethod.EditorInfo;
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

// ==========================================================================
// SECTION: REMOTE CONTROL SCREEN
// WHAT:  The main screen where you control your TV. You can press D-pad
//        buttons, use a touchpad mouse, play/pause media, type text
//        using a hidden keyboard field, launch apps, cast your screen,
//        and give voice commands.
// ==========================================================================

public class RemoteActivity extends BaseActivity {

    // ==========================================================================
    // CONSTANTS AND SETTINGS
    // ==========================================================================

    // Tag used for debug logging
    private static final String TAG = "RemoteActivity";

    // Keys used to pass TV information to this screen when it opens
    public static final String EXTRA_DEVICE_ID = "extra_device_id";
    public static final String EXTRA_IP = "extra_ip";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_MAC = "extra_mac";

    // Code to identify the screen casting permission result
    private static final int CAST_REQUEST_CODE = 1001;

    // Holds the screen-capture permission while casting starts
    public static volatile MediaProjection pendingProjection;

    // ==========================================================================
    // UI ELEMENTS
    // ==========================================================================

    // Labels at the top showing the device name and connection status
    private TextView tvDeviceName;
    private TextView tvConnectionStatus;

    // Layout containers for different control modes
    private RelativeLayout layoutDpad;       // D-pad area (old touchpad)
    private FrameLayout layoutTouchpad;       // Touchpad area (relative swipe)

    // ==========================================================================
    // KEYBOARD: HOW IT WORKS
    // This screen uses a HIDDEN text field (EditText) that is invisible to
    // you. When you tap the Keyboard button, this hidden field gains focus,
    // which makes your phone's on-screen keyboard pop up. As you type, the
    // hidden field's text changes. A "TextWatcher" detects those changes and
    // sends the new characters to the TV. This is a clever trick to use your
    // phone's normal keyboard for typing on the TV without showing a visible
    // text box on the remote screen.
    //
    // The "programmaticTextChange" flag prevents crashes: when the code
    // clears the hidden field (to reset it), the TextWatcher would normally
    // try to send that empty text to the TV. This flag tells the TextWatcher
    // to ignore that change because it was done by the program, not by your
    // typing. Without this flag, rapidly tapping the keyboard button could
    // cause crashes as the TextWatcher tries to process programmatic changes.
    // ==========================================================================

    // The hidden text field that captures keyboard input from your phone
    private EditText hiddenKeyboardInput;

    // Layout panels for the three navigation modes
    private View layoutDpadPanel;    // D-pad buttons panel
    private View layoutTouchPanel;   // Touchpad/mouse panel
    private View layoutMediaPanel;   // Media controls panel
    private View layoutVolColumn;    // Volume buttons column
    private View layoutMouseColumn;  // Mouse buttons column (touchpad)
    private View layoutChColumn;     // Channel buttons column
    private View layoutScrollColumn; // Scroll bar column (touchpad)
    private View layoutLeftSide;     // Left column wrapper
    private View layoutRightSide;    // Right column wrapper

    // ==========================================================================
    // NAVIGATION MODE
    // You can switch the control panel between three modes:
    //   DPAD (0) — shows directional arrow buttons and OK
    //   TOUCH (1) — shows the touchpad for mouse control
    //   MEDIA (2) — shows play/pause/rewind/fast-forward buttons
    // ==========================================================================

    // Whether the "relative swipe" touchpad is active (currently always false)
    private final boolean isTouchpadMode = false;
    // Which navigation mode is currently active (0=DPAD, 1=TOUCH, 2=MEDIA)
    private int navModeIndex = 0;
    // Remembers the previous mode so we can go back after media mode
    private int preMediaNavMode = 0;
    // Constants for the three navigation modes
    private static final int NAV_DPAD = 0;
    private static final int NAV_TOUCH = 1;
    private static final int NAV_MEDIA = 2;

    // Preferences storage for saving settings
    private SharedPreferences prefs;

    // ==========================================================================
    // TV INFORMATION
    // ==========================================================================

    // Information about the TV we are connected to
    private String deviceId;
    private String tvIp;
    private int tvPort;
    private String tvName;
    private TvDevice.Type tvType;
    private String tvMac;
    // Whether the device has been saved to the paired list yet
    private boolean deviceSaved = false;
    private PairedDevice pairedDevice;
    private PairedDevicesManager pairedDevicesManager;

    // Connection to the background service that talks to the TV
    private RemoteService remoteService;
    private boolean isServiceBound = false;
    private MediaProjectionManager projectionManager;

    // ==========================================================================
    // TOUCHPAD STATE
    // Tracks where your finger is on the touchpad and for how long, so
    // it can detect taps (short) vs. right-clicks (long press).
    // ==========================================================================

    // Last finger position on the relative-swipe touchpad
    private float lastTouchX = 0;
    private float lastTouchY = 0;
    private long lastTouchTime = 0;
    // How long you must press for it to count as a right-click (500ms)
    private static final long LONG_PRESS_TIMEOUT = 500;

    // Cursor position on the TV screen (from the absolute-position touchpad)
    private int cursorX = -1;
    private int cursorY = -1;

    // ==========================================================================
    // KEY REPEAT HANDLER
    // When you hold down a button (like Volume Up), this starts repeating
    // the command — first after 400ms, then every 150ms until you let go.
    // ==========================================================================

    private final Handler keyHandler = new Handler(Looper.getMainLooper());
    // Delay before the first repeat (400ms)
    private static final int REPEAT_INITIAL_MS = 400;
    // Delay between subsequent repeats (150ms)
    private static final int REPEAT_DELAY_MS = 150;
    // The currently active repeat task (null if nothing is being held)
    private Runnable activeRepeatRunnable = null;

    // ==========================================================================
    // SHORTCUT BUTTONS
    // The 6 shortcut buttons at the bottom that launch apps directly.
    // ==========================================================================

    private final int[] shortcutIds = {
        R.id.btnShortcut1, R.id.btnShortcut2, R.id.btnShortcut3,
        R.id.btnShortcut4, R.id.btnShortcut5, R.id.btnShortcut6
    };

    // ==========================================================================
    // SECTION: SERVICE CONNECTION
    // WHAT:  Connects to the background RemoteService so this screen can
    //        send commands to the TV. When connected, updates the status
    //        display and loads per-device settings.
    // ==========================================================================

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

    // ==========================================================================
    // SECTION: VOICE COMMAND RECEIVER
    // WHAT:  Listens for voice commands sent by VoiceCommandActivity.
    //        When speech is recognized, the spoken words arrive here and
    //        are sent to the TV as typed text.
    // ==========================================================================

    private final BroadcastReceiver voiceCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String command = intent.getStringExtra("command");
            if (command != null && remoteService != null && remoteService.isConnected()) {
                remoteService.sendText(command);
            }
        }
    };

    // ==========================================================================
    // IME STARTED RECEIVER
    // WHAT:  Listens for the TV entering text input mode (e.g., a search field
    //        is focused). When received, automatically opens the phone keyboard
    //        so the user can start typing without pressing the keyboard button.
    // ==========================================================================

    private final BroadcastReceiver imeStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "TV IME started — auto-opening keyboard");
            // Warm up the IME channel so first keypress has no delay
            if (remoteService != null) {
                remoteService.sendInputString("");
            }
            runOnUiThread(() -> showKeyboard(android.text.InputType.TYPE_CLASS_TEXT));
        }
    };

    // ==========================================================================
    // SECTION: SCREEN SETUP (onCreate)
    // WHAT:  Runs when the remote control screen first opens. Reads the
    //        TV details from the intent, finds all the buttons and panels,
    //        connects to the TV service, and sets up everything so the
    //        controls work when you tap them.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remote);
        Log.d(TAG, "onCreate started");

        // Open the app's preferences storage
        prefs = getSharedPreferences("remote_prefs", MODE_PRIVATE);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        pairedDevicesManager = new PairedDevicesManager(this);

        // Read TV information from the intent that opened this screen
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
        
        // If we have a device ID, load the full device info from storage
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

        // Find all the UI elements and store references to them
        tvDeviceName = findViewById(R.id.tvDeviceName);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        layoutDpad = findViewById(R.id.layoutDpad);
        layoutTouchpad = findViewById(R.id.layoutTouchpad);
        hiddenKeyboardInput = findViewById(R.id.hiddenKeyboardInput);
        layoutDpadPanel = findViewById(R.id.layoutDpadPanel);
        layoutTouchPanel = findViewById(R.id.layoutTouchPanel);
        layoutMediaPanel = findViewById(R.id.layoutMediaPanel);
        layoutVolColumn = findViewById(R.id.layoutVolColumn);
        layoutMouseColumn = findViewById(R.id.layoutMouseColumn);
        layoutChColumn = findViewById(R.id.layoutChColumn);
        layoutScrollColumn = findViewById(R.id.layoutScrollColumn);
        layoutLeftSide = findViewById(R.id.layoutLeftSide);
        layoutRightSide = findViewById(R.id.layoutRightSide);
        Log.d(TAG, "Views inflated");

        // Show the device name at the top
        tvDeviceName.setText(tvName != null ? tvName : tvIp);
        updateConnectionStatus(false, "Connecting...");

        // Start the background service that connects to the TV
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

        // Register to receive voice command broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(voiceCommandReceiver,
            new IntentFilter("com.mctrash692.freemote.VOICE_COMMAND"));

        // Register to receive TV IME started broadcasts (auto-open keyboard)
        LocalBroadcastManager.getInstance(this).registerReceiver(imeStartedReceiver,
            new IntentFilter("com.mctrash692.freemote.IME_STARTED"));

        // Check if we need overlay permission for the mouse cursor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay permission needed")
                .setMessage("Freemote needs overlay permission for the touchpad cursor.")
                .setPositiveButton("Grant", (d, w) ->
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)))
                .setNegativeButton("Skip", null)
                .show();
        }

        // Set up all the interactive parts of the screen
        setupHiddenKeyboard();
        setupButtons();
        setupTouchpad();
        loadShortcuts();
        Log.d(TAG, "onCreate finished");
    }

    // ==========================================================================
    // METHOD: loadPerDeviceSettings
    // WHAT:  Loads settings that are unique to this specific TV (touchpad
    //        sensitivity and shortcut button assignments). Runs when the
    //        connection to the TV is established.
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: savePerDeviceSettings
    // WHAT:  Saves the current touchpad sensitivity and shortcut button
    //        assignments to this specific TV's settings in storage.
    //        Runs when you leave the remote screen (onPause).
    // ==========================================================================

    private void savePerDeviceSettings() {
        if (pairedDevice == null) return;
        pairedDevice.setTouchpadSensitivity(prefs.getInt(SettingsActivity.KEY_SENSITIVITY, 15));
        for (int slot = 1; slot <= 6; slot++) {
            String appId = prefs.getString(SettingsActivity.KEY_SLOT_APPID + slot, null);
            if (appId != null) pairedDevice.setShortcut(slot, appId);
        }
        pairedDevicesManager.saveDevice(pairedDevice);
    }

    // ==========================================================================
    // METHOD: saveCurrentDevice
    // WHAT:  Saves the TV to your paired devices list if it hasn't been
    //        saved yet. Runs once when the connection is established.
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: updateConnectionStatus
    // WHAT:  Updates the status label at the top of the screen. Shows
    //        "Connected" in green or "Disconnected"/"Connecting..." in
    //        orange so you know whether your commands are going through.
    // ==========================================================================

    private void updateConnectionStatus(boolean connected, String message) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText(message);
            tvConnectionStatus.setTextColor(connected ? 
                getColor(android.R.color.holo_green_dark) : 
                getColor(android.R.color.holo_orange_dark));
        });
    }

    // ==========================================================================
    // KEYBOARD TRACKING STATE
    // These variables track what's been typed in the hidden keyboard field
    // so we can send the right text to the TV.
    //
    // prevTextLen: stores how long the text was the last time we checked.
    // keyboardBuffer: accumulates all typed characters since the keyboard
    //   was opened (used to detect backspace/delete).
    // programmaticTextChange: a flag that tells the TextWatcher to IGNORE
    //   changes made by the program (like clearing the field). Without this,
    //   the TextWatcher would try to send the empty text to the TV when the
    //   field is cleared, which could cause a crash.
    // ==========================================================================

    private int prevTextLen = 0;
    private final StringBuilder keyboardBuffer = new StringBuilder();
    private boolean programmaticTextChange = false;

    // ==========================================================================
    // METHOD: setupHiddenKeyboard
    // WHAT:  Sets up the invisible text field that captures keyboard input.
    //        When you type on your phone's keyboard, the hidden field's text
    //        changes. A TextWatcher detects these changes and sends the new
    //        characters to the TV one by one. When the field loses focus
    //        (e.g., you tapped elsewhere), it hides itself and resets.
    //
    // HOW KEYBOARD INPUT WORKS:
    // 1. You tap the Keyboard button → hidden field gets focus
    // 2. Your phone's on-screen keyboard pops up
    // 3. You type characters → they go into the hidden field
    // 4. The TextWatcher sees each new character and sends it to the TV
    // 5. Backspace: when text gets shorter, it removes the last char from
    //    our buffer and sends the updated text to the TV
    // 6. The field stays hidden so you never see a text box on the remote
    //
    // WHY "programmaticTextChange" EXISTS:
    // When the code resets the text field (sets it to ""), the TextWatcher
    // would normally trigger and try to send "" to the TV — which could
    // crash. The flag tells the TextWatcher: "ignore this, I'm just
    // resetting." Without it, double-tapping the keyboard button would
    // cause a crash because the field would be cleared while the TextWatcher
    // is still running.
    // ==========================================================================

    private void setupHiddenKeyboard() {
        hiddenKeyboardInput.setFocusable(true);
        hiddenKeyboardInput.setFocusableInTouchMode(true);
        hiddenKeyboardInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        hiddenKeyboardInput.setImeOptions(EditorInfo.IME_ACTION_NONE
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);

        // When the hidden field loses focus, hide it and reset
        hiddenKeyboardInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                v.setVisibility(View.GONE);
                prevTextLen = 0;
                keyboardBuffer.setLength(0);
            }
        });

        // Watch for text changes and send new characters to the TV
        hiddenKeyboardInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (programmaticTextChange) return;  // Ignore programmatic changes
                int len = s.length();
                if (remoteService == null || !remoteService.isConnected()) return;
                if (len > prevTextLen) {
                    // New characters were typed — send them to the TV
                    String added = s.subSequence(prevTextLen, len).toString();
                    keyboardBuffer.append(added);
                    remoteService.sendInputString(keyboardBuffer.toString());
                } else if (len < prevTextLen) {
                    // A character was deleted (backspace) — send updated text
                    int bufLen = keyboardBuffer.length();
                    if (bufLen > 0) keyboardBuffer.deleteCharAt(bufLen - 1);
                    if (keyboardBuffer.length() > 0) {
                        remoteService.sendInputString(keyboardBuffer.toString());
                    }
                }
                prevTextLen = len;
            }
        });
    }

    // ==========================================================================
    // METHOD: showKeyboard
    // WHAT:  Shows the on-screen keyboard for typing. Sets the hidden field
    //        to accept the right type of input (regular text for the main
    //        keyboard, numbers-only for channel entry), clears it, gives
    //        it focus, and opens the keyboard. The "programmaticTextChange"
    //        flag prevents the TextWatcher from reacting to the clear.
    // INPUT: inputType = what kind of keyboard to show (text or numbers)
    // ==========================================================================

    private void showKeyboard(int inputType) {
        hiddenKeyboardInput.setInputType(inputType);
        hiddenKeyboardInput.setVisibility(View.VISIBLE);
        programmaticTextChange = true;  // Clear without triggering TextWatcher
        hiddenKeyboardInput.setText("");
        programmaticTextChange = false; // Re-enable TextWatcher
        hiddenKeyboardInput.requestFocus();
        hiddenKeyboardInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(hiddenKeyboardInput, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    // ==========================================================================
    // METHOD: setRepeatListener
    // WHAT:  Makes a button auto-repeat its command when you hold it down.
    //        After 400ms of holding, it starts repeating every 150ms until
    //        you let go. Used for volume, channel up/down, and scrolling.
    // INPUT: btn = the button view, keyCode = the remote key to send
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: sendKey
    // WHAT:  Sends a single button press command to the TV (e.g., volume up,
    //        D-pad up, enter, back). Only works if connected.
    // INPUT: keyCode = the remote key string (e.g., "KEY_VOLUMEUP")
    // ==========================================================================

    private void sendKey(String keyCode) {
        if (remoteService != null && remoteService.isConnected()) {
            remoteService.sendKey(keyCode);
            Log.d(TAG, "sendKey: " + keyCode);
        }
    }

    // ==========================================================================
    // METHOD: sendAppLaunch
    // WHAT:  Opens an app on the TV by sending its app ID. Only works if
    //        connected.
    // INPUT: appId = the TV's internal ID for the app (e.g., YouTube)
    // ==========================================================================

    private void sendAppLaunch(String appId) {
        if (remoteService != null && remoteService.isConnected()) {
            remoteService.sendAppLaunch(appId);
        }
    }

    // ==========================================================================
    // METHOD: logClick
    // WHAT:  Writes a message to the debug log so developers can see which
    //        buttons were tapped. Not visible to users.
    // ==========================================================================

    private void logClick(String name) {
        Log.d(TAG, "CLICK: " + name);
    }

    // ==========================================================================
    // METHOD: setupButtons
    // WHAT:  Connects every button on the remote screen to its action.
    //        Each button either sends a key to the TV, opens a menu, or
    //        switches between control modes. Buttons with auto-repeat
    //        (volume, channel) get a special listener.
    //
    // BUTTON REFERENCE:
    //   Settings     → Opens the settings screen
    //   Power        → Sends KEY_POWER (short tap) or Wake-on-LAN (long press)
    //   Vol Up/Down  → Volume control (hold to repeat)
    //   Ch Up/Down   → Channel control (hold to repeat)
    //   Ch Entry     → Opens keyboard for direct channel number input
    //   Mute         → Toggles sound on/off
    //   Info         → Shows program/TV info
    //   Guide        → Opens the TV guide
    //   Menu         → Opens the TV's menu
    //   D-pad arrows → Navigate menus (UP/DOWN/LEFT/RIGHT)
    //   OK           → Select / Enter
    //   Back         → Go back / Return
    //   Home         → Go to TV home screen
    //   Source       → Change input source (HDMI, etc.)
    //   Apps         → Opens app picker list
    //   Cast         → Opens screen casting options
    //   Keyboard     → Opens phone keyboard for typing on TV
    //   MediaToggle  → Switches to media control panel
    //   Voice        → Opens voice command (speech recognition)
    //   Nav Toggle   → Switches between D-pad and Touchpad modes
    // ==========================================================================

    private void setupButtons() {
        findViewById(R.id.btnSettings).setOnClickListener(v -> { logClick("Settings"); openSettings(); });
        
        View btnPower = findViewById(R.id.btnPower);
        btnPower.setOnClickListener(v -> {
            if (remoteService != null && remoteService.isConnected()) {
                logClick("Power (off)");
                sendKey("KEY_POWER");
            } else {
                logClick("Power (WoL)");
                String mac = tvMac;
                if (mac == null && pairedDevice != null) mac = pairedDevice.getMacAddress();
            WakeOnLan.send(tvIp, mac, msg ->
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()));
        }
    });
    btnPower.setOnLongClickListener(v -> {
        logClick("Power (force WoL)");
        String mac = tvMac;
        if (mac == null && pairedDevice != null) mac = pairedDevice.getMacAddress();
        WakeOnLan.send(tvIp, mac, msg ->
            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show()));
            return true;
        });

        findViewById(R.id.btnInfo).setOnClickListener(v -> { logClick("Info"); sendKey("KEY_INFO"); });
        findViewById(R.id.btnGuide).setOnClickListener(v -> { logClick("Guide"); sendKey("KEY_GUIDE"); });
        findViewById(R.id.btnMenu).setOnClickListener(v -> { logClick("Menu"); sendKey("KEY_MENU"); });
        
        setRepeatListener(findViewById(R.id.btnVolUp), "KEY_VOLUMEUP");
        setRepeatListener(findViewById(R.id.btnVolDown), "KEY_VOLUMEDOWN");
        findViewById(R.id.btnMute).setOnClickListener(v -> { logClick("Mute"); sendKey("KEY_MUTE"); });
        
        setRepeatListener(findViewById(R.id.btnChUp), "KEY_CHANNELUP");
        setRepeatListener(findViewById(R.id.btnChDown), "KEY_CHANNELDOWN");
        findViewById(R.id.btnChEntry).setOnClickListener(v -> { logClick("ChEntry"); sendKey("KEY_CHANNEL_ENTRY"); });
        
        setRepeatListener(findViewById(R.id.btnDpadUp), "KEY_UP");
        setRepeatListener(findViewById(R.id.btnDpadDown), "KEY_DOWN");
        setRepeatListener(findViewById(R.id.btnDpadLeft), "KEY_LEFT");
        setRepeatListener(findViewById(R.id.btnDpadRight), "KEY_RIGHT");
        findViewById(R.id.btnOk).setOnClickListener(v -> { logClick("OK"); sendKey("KEY_ENTER"); });
        
        findViewById(R.id.btnBack).setOnClickListener(v -> { logClick("Back"); sendKey("KEY_RETURN"); });
        findViewById(R.id.btnHome).setOnClickListener(v -> { logClick("Home"); sendKey("KEY_HOME"); });
        findViewById(R.id.btnSource).setOnClickListener(v -> { logClick("Source"); sendKey("KEY_SOURCE"); });
        
        findViewById(R.id.btnApps).setOnClickListener(v -> { logClick("Apps"); showAppPicker(); });
        findViewById(R.id.btnCast).setOnClickListener(v -> { logClick("Cast"); showCastPicker(); });

        View keyboardBtn = findViewById(R.id.btnKeyboard);
        if (keyboardBtn != null) {
            keyboardBtn.setOnClickListener(v -> {
                logClick("Keyboard");
                // Warm up the TV's IME channel so first typed character has no delay
                if (remoteService != null) {
                    remoteService.sendInputString("");
                }
                showKeyboard(android.text.InputType.TYPE_CLASS_TEXT);
            });
        } else {
            Log.e(TAG, "Keyboard button not found!");
        }
        
        findViewById(R.id.btnMediaToggle).setOnClickListener(v -> { logClick("MediaToggle"); toggleMediaOverlay(); });
        findViewById(R.id.btnVoice).setOnClickListener(v -> { logClick("Voice"); voiceCommand(); });

        View toggleBtn = findViewById(R.id.btnToggleNavMode);
        if (toggleBtn != null) {
            toggleBtn.setOnClickListener(v -> { logClick("ToggleNavMode"); toggleNavMode(); });
        } else {
            Log.e(TAG, "Toggle button not found!");
        }
        
        setupTouchPanelButtons();
        updateSideColumns();
    }

    // ==========================================================================
    // SECTION: TOUCHPAD MOUSE CONTROLS
    // WHAT:  This section sets up the buttons and gestures that control the
    //        mouse cursor on the TV screen. There are two touchpad modes
    //        and also left/middle/right click buttons and a scroll strip.
    //
    // HOW THE TOUCHPAD WORKS:
    //   The "absolute position" touchpad (layoutTouchpadAlt) maps your finger
    //   position directly to a position on the TV screen. If you touch the
    //   top-left of the touchpad area, the cursor goes to the top-left of the
    //   TV. The mapping is: (fingerX / touchpadWidth) × TV screen width. This
    //   gives pixel-perfect positioning.
    //
    //   A short tap (< 300ms) = left click. A long tap (>= 300ms) = right
    //   click. This lets you use the whole touchpad area without needing
    //   separate left/right click buttons (though those are also provided).
    //
    //   The scroll strip on the right side detects vertical finger movement.
    //   Swiping up scrolls up, swiping down scrolls down. It requires a
    //   minimum movement of 20 pixels before triggering to avoid jitter.
    //
    //   The "relative" touchpad (layoutTouchpad) is an alternative mode that
    //   works like a laptop trackpad — dragging your finger moves the cursor
    //   relative to its current position. Sensitivity can be adjusted.
    // ==========================================================================

    // ==========================================================================
    // METHOD: setupTouchPanelButtons
    // WHAT:  Sets up the mouse click buttons (Left, Middle, Right) that
    //        appear in the touchpad panel, plus the scroll strip and the
    //        actual touchpad areas.
    // ==========================================================================

    private void setupTouchPanelButtons() {
        View leftClick = findViewById(R.id.btnLeftClick);
        if (leftClick != null) {
            leftClick.setOnClickListener(v -> {
                logClick("LeftClick");
                if (remoteService != null && remoteService.isConnected()) {
                    remoteService.sendMouseClick("Left");
                }
            });
        }
        View middleClick = findViewById(R.id.btnMiddleClick);
        if (middleClick != null) {
            middleClick.setOnClickListener(v -> {
                logClick("MiddleClick");
                if (remoteService != null && remoteService.isConnected()) {
                    remoteService.sendMouseClick("Middle");
                }
            });
        }
        View rightClick = findViewById(R.id.btnRightClick);
        if (rightClick != null) {
            rightClick.setOnClickListener(v -> {
                logClick("RightClick");
                if (remoteService != null && remoteService.isConnected()) {
                    remoteService.sendMouseClick("Right");
                }
            });
        }
        setupScrollStrip();
        setupTouchpadAlt();
        setupMediaPanelButtons();
    }

    // ==========================================================================
    // METHOD: setupScrollStrip
    // WHAT:  Sets up the scroll strip (a narrow vertical area) that detects
    //        up/down finger swipes and sends mouse wheel events to the TV.
    //        The finger must move at least 20 pixels to trigger a scroll.
    // ==========================================================================

    private void setupScrollStrip() {
        View scrollStrip = findViewById(R.id.scrollStrip);
        if (scrollStrip == null) return;
        scrollStrip.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    scrollStrip.setTag(event.getY());
                    Log.d(TAG, "ScrollStrip DOWN");
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float startY = (float) scrollStrip.getTag();
                    float delta = startY - event.getY();
                    if (Math.abs(delta) > 20) {
                        if (remoteService != null && remoteService.isConnected()) {
                            remoteService.sendMouseWheel(delta > 0 ? -1 : 1);
                            Log.d(TAG, "ScrollStrip scroll: " + (delta > 0 ? "UP" : "DOWN"));
                        }
                        scrollStrip.setTag(event.getY());
                    }
                    return true;
            }
            return false;
        });
    }

    // ==========================================================================
    // METHOD: setupTouchpadAlt
    // WHAT:  Sets up the "absolute position" touchpad. This maps your finger
    //        directly to a position on the TV screen. Wherever you touch on
    //        the touchpad, the TV cursor moves to the corresponding spot on
    //        the TV. A short tap (< 300ms) = left click, long tap = right
    //        click. If the cursor hasn't been positioned yet, it starts in
    //        the center of the TV screen and the mouse is activated.
    // ==========================================================================

    private void setupTouchpadAlt() {
        FrameLayout altTouchpad = findViewById(R.id.layoutTouchpadAlt);
        if (altTouchpad == null) return;
        altTouchpad.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    altTouchpad.setTag(event.getX());
                    altTouchpad.setTag(R.id.btnPower, event.getY());
                    altTouchpad.setTag(R.id.btnSettings, System.currentTimeMillis());
                    // If cursor hasn't been placed yet, put it in the center of the TV
                    if (cursorX < 0 && remoteService != null) {
                        cursorX = remoteService.getTvWidth() / 2;
                        cursorY = remoteService.getTvHeight() / 2;
                        remoteService.sendMouseActivate();
                        remoteService.sendMouseMove(cursorX, cursorY);
                    }
                    touchToTv(event.getX(), event.getY());
                    Log.d(TAG, "Touchpad DOWN at " + (int)event.getX() + "," + (int)event.getY());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    touchToTv(event.getX(), event.getY());
                    return true;
                case MotionEvent.ACTION_UP:
                    long duration = System.currentTimeMillis() - (long) altTouchpad.getTag(R.id.btnSettings);
                    Log.d(TAG, "Touchpad UP: duration=" + duration);
                    if (remoteService != null && remoteService.isConnected()) {
                        remoteService.sendMouseClick(duration < 300 ? "Left" : "Right");
                    }
                    return true;
            }
            return false;
        });
    }

    // ==========================================================================
    // METHOD: touchToTv
    // WHAT:  Converts a finger position on the touchpad to an absolute
    //        position on the TV screen. The math is: (finger position /
    //        touchpad size) × TV screen size. This is called every time
    //        your finger moves on the touchpad to update the cursor.
    // INPUT: touchX, touchY = finger position on the touchpad
    // ==========================================================================

    private void touchToTv(float touchX, float touchY) {
        if (remoteService == null || !remoteService.isConnected()) return;
        View touchpad = findViewById(R.id.layoutTouchpadAlt);
        if (touchpad == null) return;
        int viewW = touchpad.getWidth();
        int viewH = touchpad.getHeight();
        if (viewW <= 0 || viewH <= 0) return;
        int tvW = remoteService.getTvWidth();
        int tvH = remoteService.getTvHeight();
        int absX = (int) ((touchX / viewW) * tvW);
        int absY = (int) ((touchY / viewH) * tvH);
        absX = Math.max(0, Math.min(tvW, absX));
        absY = Math.max(0, Math.min(tvH, absY));
        cursorX = absX;
        cursorY = absY;
        Log.d(TAG, "Touchpad MOVE: touch=" + (int)touchX + "," + (int)touchY
            + " → tv=" + absX + "," + absY);
        remoteService.sendMouseMove(absX, absY);
    }

    // ==========================================================================
    // SECTION: MEDIA CONTROLS
    // WHAT:  Buttons for controlling media playback on the TV. These appear
    //        when you tap the Media Toggle button to enter media mode.
    //
    // MEDIA BUTTON REFERENCE:
    //   Rewind     → Skips backward
    //   Prev       → Previous track/chapter
    //   Play/Pause → Toggle between playing and pausing
    //   Next       → Next track/chapter
    //   Fast Fwd   → Skips forward
    //   Vol Down   → Lower media volume
    //   Mute       → Mute/unmute sound
    //   Vol Up     → Raise media volume
    // ==========================================================================

    // ==========================================================================
    // METHOD: setupMediaPanelButtons
    // WHAT:  Connects all the media control buttons to their TV commands.
    //        Each button sends the appropriate media transport command
    //        (play, pause, skip, rewind, volume, mute) to the TV.
    // ==========================================================================

    private void setupMediaPanelButtons() {
        setRepeatListener(findViewById(R.id.btnMediaRW), "KEY_REWIND");
        setRepeatListener(findViewById(R.id.btnMediaPrev), "KEY_REWIND");
        findViewById(R.id.btnMediaPlayPause).setOnClickListener(v -> {
            logClick("MediaPlayPause");
            if (remoteService != null) remoteService.sendMediaPlayPause();
        });
        setRepeatListener(findViewById(R.id.btnMediaNext), "KEY_FORWARD");
        setRepeatListener(findViewById(R.id.btnMediaFF), "KEY_FF");
        findViewById(R.id.btnMediaVolDown).setOnClickListener(v -> {
            logClick("MediaVolDown");
            if (remoteService != null) remoteService.sendVolumeDown();
        });
        findViewById(R.id.btnMediaMute).setOnClickListener(v -> {
            logClick("MediaMute");
            if (remoteService != null) remoteService.sendMute();
        });
        findViewById(R.id.btnMediaVolUp).setOnClickListener(v -> {
            logClick("MediaVolUp");
            if (remoteService != null) remoteService.sendVolumeUp();
        });
    }

    // ==========================================================================
    // METHOD: showAppPicker
    // WHAT:  Opens a dialog listing popular apps that can be launched on
    //        the TV (YouTube, Netflix, Prime Video, Disney+, etc.).
    //        Tapping an app sends the command to open it on the TV.
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: showCastPicker
    // WHAT:  Opens a dialog with cast options. You can cast to specific
    //        apps (Prime Video, YouTube, Disney+) by launching them, or
    //        choose "Screen Mirror" to mirror your phone screen to the TV.
    // ==========================================================================

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

    // ==========================================================================
    // METHOD: startScreenCasting
    // WHAT:  Starts the screen mirroring process. Asks for permission to
    //        capture the screen (using Android's MediaProjection API),
    //        then starts the ScreenCastingService which sends the screen
    //        to the TV.
    // ==========================================================================

    private void startScreenCasting() {
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), CAST_REQUEST_CODE);
        } else {
            Toast.makeText(this, "Screen casting not supported", Toast.LENGTH_LONG).show();
        }
    }

    // ==========================================================================
    // METHOD: voiceCommand
    // WHAT:  Opens the voice command screen which listens to what you say
    //        and converts it into text typed on the TV.
    // ==========================================================================

    private void voiceCommand() {
        Intent intent = new Intent(this, VoiceCommandActivity.class);
        intent.putExtra("tv_ip", tvIp);
        startActivity(intent);
    }

    // ==========================================================================
    // METHOD: toggleNavMode
    // WHAT:  Switches between D-pad mode and Touchpad mode. When you tap
    //        the toggle button, it hides the current panel and shows the
    //        other one. If switching to touchpad, it centers the mouse
    //        cursor and activates it on the TV.
    // ==========================================================================

    private void toggleNavMode() {
        if (navModeIndex == NAV_MEDIA) {
            navModeIndex = preMediaNavMode;
        } else {
            navModeIndex = (navModeIndex == NAV_DPAD) ? NAV_TOUCH : NAV_DPAD;
        }
        if (layoutDpadPanel != null)
            layoutDpadPanel.setVisibility(navModeIndex == NAV_DPAD ? View.VISIBLE : View.GONE);
        if (layoutTouchPanel != null)
            layoutTouchPanel.setVisibility(navModeIndex == NAV_TOUCH ? View.VISIBLE : View.GONE);
        if (layoutMediaPanel != null)
            layoutMediaPanel.setVisibility(View.GONE);
        updateSideColumns();
        String[] labels = {"D-Pad ON", "Touchpad ON"};
        Log.d(TAG, "toggleNavMode: " + labels[navModeIndex]);
        if (navModeIndex == NAV_TOUCH && remoteService != null && remoteService.isConnected()) {
            cursorX = remoteService.getTvWidth() / 2;
            cursorY = remoteService.getTvHeight() / 2;
            remoteService.sendMouseActivate();
        }
    }

    // ==========================================================================
    // METHOD: toggleMediaOverlay
    // WHAT:  Shows or hides the media control panel. When you tap the media
    //        button, it remembers which mode you were in (D-pad or touchpad)
    //        and switches to media controls. Tapping again goes back to the
    //        previous mode.
    // ==========================================================================

    private void toggleMediaOverlay() {
        if (navModeIndex == NAV_MEDIA) {
            navModeIndex = preMediaNavMode;
        } else {
            preMediaNavMode = navModeIndex;
            navModeIndex = NAV_MEDIA;
        }
        if (layoutDpadPanel != null)
            layoutDpadPanel.setVisibility(navModeIndex == NAV_DPAD ? View.VISIBLE : View.GONE);
        if (layoutTouchPanel != null)
            layoutTouchPanel.setVisibility(navModeIndex == NAV_TOUCH ? View.VISIBLE : View.GONE);
        if (layoutMediaPanel != null)
            layoutMediaPanel.setVisibility(navModeIndex == NAV_MEDIA ? View.VISIBLE : View.GONE);
        updateSideColumns();
        String[] labels = {"D-Pad ON", "Touchpad ON", "Media ON"};
        Log.d(TAG, "toggleMediaOverlay: " + labels[navModeIndex]);
    }

    // ==========================================================================
    // METHOD: updateSideColumns
    // WHAT:  Shows/hides the side columns based on current nav mode.
    //        In D-pad/Media mode: volume + channel columns visible.
    //        In Touchpad mode: mouse buttons + scroll bar visible instead.
    // ==========================================================================

    private void updateSideColumns() {
        if (navModeIndex == NAV_MEDIA) {
            if (layoutLeftSide != null) layoutLeftSide.setVisibility(View.GONE);
            if (layoutRightSide != null) layoutRightSide.setVisibility(View.GONE);
            return;
        }
        if (layoutLeftSide != null) layoutLeftSide.setVisibility(View.VISIBLE);
        if (layoutRightSide != null) layoutRightSide.setVisibility(View.VISIBLE);
        boolean isTouch = navModeIndex == NAV_TOUCH;
        if (layoutVolColumn != null)
            layoutVolColumn.setVisibility(isTouch ? View.GONE : View.VISIBLE);
        if (layoutMouseColumn != null)
            layoutMouseColumn.setVisibility(isTouch ? View.VISIBLE : View.GONE);
        if (layoutChColumn != null)
            layoutChColumn.setVisibility(isTouch ? View.GONE : View.VISIBLE);
        if (layoutScrollColumn != null)
            layoutScrollColumn.setVisibility(isTouch ? View.VISIBLE : View.GONE);
    }

    // ==========================================================================
    // METHOD: openSettings
    // WHAT:  Opens the Settings screen.
    // ==========================================================================

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    // ==========================================================================
    // METHOD: loadShortcuts
    // WHAT:  Loads the 6 shortcut buttons at the bottom of the screen.
    //        Each shortcut can be assigned to a different app (YouTube,
    //        Netflix, etc.) in the Settings. If no app is assigned, it
    //        uses a default app for that slot. Long-pressing a shortcut
    //        opens the Settings screen.
    // ==========================================================================

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

    // ==========================================================================
    // SECTION: RELATIVE TOUCHPAD (ALTERNATIVE MODE)
    // WHAT:  This is a second touchpad mode (currently disabled) that works
    //        like a laptop trackpad. Instead of mapping finger position
    //        directly to the TV screen, it detects the direction and speed
    //        of your finger swipe and moves the cursor relative to where it
    //        currently is. The sensitivity setting affects how fast the
    //        cursor moves relative to your finger speed.
    //
    //        Currently this mode is disabled (isTouchpadMode = false).
    //        The "absolute" touchpad (setupTouchpadAlt) is the active one.
    // ==========================================================================

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
                        if (cursorX < 0) cursorX = remoteService != null ? remoteService.getTvWidth() / 2 : 960;
                        if (cursorY < 0) cursorY = remoteService != null ? remoteService.getTvHeight() / 2 : 540;
                        cursorX += Math.round(dx * sens);
                        cursorY += Math.round(dy * sens);
                        if (remoteService != null) {
                            cursorX = Math.max(0, Math.min(remoteService.getTvWidth(), cursorX));
                            cursorY = Math.max(0, Math.min(remoteService.getTvHeight(), cursorY));
                        }
                        if (remoteService != null && remoteService.isConnected()) {
                            remoteService.sendMouseMove(cursorX, cursorY);
                        }
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        Log.d(TAG, "Touchpad dpad: dx=" + Math.round(dx) + "," + Math.round(dy)
                            + " → cursor=" + cursorX + "," + cursorY);
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
                    lastTouchX = 0;
                    lastTouchY = 0;
                    lastTouchTime = 0;
                    return true;
            }
            return false;
        });
    }

    // ==========================================================================
    // SECTION: ACTIVITY RESULTS & LIFECYCLE
    // WHAT:  These methods handle the results from other screens (like the
    //        screen casting permission dialog) and clean up when the
    //        remote screen is closed or paused.
    // ==========================================================================

    // ==========================================================================
    // METHOD: onActivityResult
    // WHAT:  Runs when a screen you opened (like the screen cast permission
    //        dialog) comes back with a result. If screen casting permission
    //        was granted, it starts the ScreenCastingService. If denied,
    //        it shows a message.
    // ==========================================================================

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
        } else if (requestCode == CAST_REQUEST_CODE) {
            Toast.makeText(this, "Screen cast permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================================================
    // METHOD: onPause
    // WHAT:  Runs when you leave this screen (e.g., switch to another app).
    //        Saves the current touchpad sensitivity and shortcut settings
    //        to this device's storage so they're remembered next time.
    // ==========================================================================

    @Override
    protected void onPause() {
        super.onPause();
        savePerDeviceSettings();
    }

    // ==========================================================================
    // METHOD: onDestroy
    // WHAT:  Runs when the remote screen is fully closed. Cleans up the
    //        pending projection, stops any repeating key actions,
    //        disconnects from the background service, and unregisters
    //        the voice command receiver.
    // ==========================================================================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pendingProjection = null;
        keyHandler.removeCallbacksAndMessages(null);
        if (isServiceBound) {
            try { unbindService(serviceConnection); } catch (Exception e) { Log.w(TAG, "unbindService failed", e); }
        }
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(voiceCommandReceiver); } catch (Exception e) { Log.w(TAG, "unregisterReceiver failed", e); }
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(imeStartedReceiver); } catch (Exception e) { Log.w(TAG, "unregisterImeReceiver failed", e); }
    }
}
