package com.mctrash692.freemote.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.util.ThemeManager;

public class SettingsActivity extends BaseActivity {

    public static final String PREFS_NAME       = "remote_prefs";
    public static final String KEY_TOKEN_PREFIX = "token_";
    public static final String KEY_LABEL        = "device_label_";
    public static final String KEY_SLOT_APPID   = "slot_app_";
    public static final String KEY_SLOT_KEY     = "slot_key_";
    public static final String KEY_SLOT_ICON    = "slot_icon_";
    public static final String KEY_SENSITIVITY  = "touchpad_sensitivity";
    public static final String DEFAULT_PORT     = "default_port_wss";

    private static final String[] SLOT_LABELS = {
        "YouTube", "Prime Video", "Netflix", "Disney+",
        "Plex", "Kodi", "HBO Max", "Hulu", "Apple TV+",
        "— empty —"
    };
    private static final String[] SLOT_APP_IDS = {
        "111299001912", "org.lotusandroid.firetv", "org.lotusandroid.netlify",
        "org.lotusandroid.disney", "com.plexapp.android", "org.xbmc.kodi",
        "org.lotusandroid.hbomax", "com.hulu.plus", "com.apple.atve.sony.appletv",
        null
    };
    private static final String[] SLOT_ICONS = {
        "youtube", "primevideo", "netflix", "disneyplus",
        "plex", "kodi", "hbomax", "hulu", "appletv",
        "default_icon"
    };

    private SharedPreferences prefs;
    private String tvIp;

    private Spinner  spinnerTheme;
    private Button   btnSaveTheme;
    private int      selectedTheme;

    private final Spinner[] slotSpinners = new Spinner[6];

    private SeekBar  seekSensitivity;
    private TextView tvSensitivityValue;

    private Switch   switchPort;
    private TextView tvPortValue;

    private EditText etDeviceLabel;
    private Button   btnClearPairing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // applyTheme is called by BaseActivity.onCreate — do not call it again here.
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        tvIp  = getIntent().getStringExtra("tv_ip");

        initViews();
        setupListeners();
        loadSettings();
    }

    private void initViews() {
        spinnerTheme       = findViewById(R.id.spinnerTheme);
        btnSaveTheme       = findViewById(R.id.btnSave);
        seekSensitivity    = findViewById(R.id.seekSensitivity);
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue);
        switchPort         = findViewById(R.id.switchPort);
        tvPortValue        = findViewById(R.id.tvPortValue);
        etDeviceLabel      = findViewById(R.id.etDeviceLabel);
        btnClearPairing    = findViewById(R.id.btnClearPairing);

        slotSpinners[0] = findViewById(R.id.spinnerSlot1);
        slotSpinners[1] = findViewById(R.id.spinnerSlot2);
        slotSpinners[2] = findViewById(R.id.spinnerSlot3);
        slotSpinners[3] = findViewById(R.id.spinnerSlot4);
        slotSpinners[4] = findViewById(R.id.spinnerSlot5);
        slotSpinners[5] = findViewById(R.id.spinnerSlot6);

        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, ThemeManager.THEME_NAMES);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);

        for (Spinner s : slotSpinners) {
            ArrayAdapter<String> a = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, SLOT_LABELS);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            s.setAdapter(a);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTheme = position;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSaveTheme.setOnClickListener(v -> {
            if (selectedTheme == ThemeManager.THEME_CUSTOM) {
                Intent intent = new Intent(this, CustomThemeActivity.class);
                startActivity(intent);
            } else {
                ThemeManager.setTheme(this, selectedTheme);
            }
        });

        for (int i = 0; i < 6; i++) {
            final int slot = i + 1;
            slotSpinners[i].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    SharedPreferences.Editor ed = prefs.edit();
                    if (pos < SLOT_APP_IDS.length - 1) {
                        ed.putString(KEY_SLOT_APPID + slot, SLOT_APP_IDS[pos]);
                        ed.putString(KEY_SLOT_ICON  + slot, SLOT_ICONS[pos]);
                        ed.remove(KEY_SLOT_KEY + slot);
                    } else {
                        ed.remove(KEY_SLOT_APPID + slot);
                        ed.remove(KEY_SLOT_KEY   + slot);
                        ed.remove(KEY_SLOT_ICON  + slot);
                    }
                    ed.apply();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        seekSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float s = 0.5f + (progress / 10.0f);
                tvSensitivityValue.setText(String.format("%.1fx", s));
                if (fromUser) prefs.edit().putInt(KEY_SENSITIVITY, progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchPort.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                tvPortValue.setText("8001 (WebSocket)");
                prefs.edit().putBoolean(DEFAULT_PORT, false).apply();
            } else {
                tvPortValue.setText("8002 (Secure WSS)");
                prefs.edit().putBoolean(DEFAULT_PORT, true).apply();
            }
        });

        btnClearPairing.setOnClickListener(v -> {
            // N8: guard null tvIp — the settings screen can be opened without a TV selected.
            if (tvIp == null || tvIp.isEmpty()) {
                Toast.makeText(this, "No device selected — nothing to clear", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().remove(KEY_TOKEN_PREFIX + tvIp).apply();
            Toast.makeText(this, "Pairing token cleared for " + tvIp, Toast.LENGTH_SHORT).show();
        });

        etDeviceLabel.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (tvIp != null) {
                    prefs.edit().putString(KEY_LABEL + tvIp, s.toString().trim()).apply();
                }
            }
        });
    }

    private void loadSettings() {
        selectedTheme = ThemeManager.getTheme(this);
        spinnerTheme.setSelection(selectedTheme);

        for (int i = 0; i < 6; i++) {
            int slot = i + 1;
            String savedAppId = prefs.getString(KEY_SLOT_APPID + slot, null);
            int selection = SLOT_LABELS.length - 1;
            if (savedAppId != null) {
                for (int j = 0; j < SLOT_APP_IDS.length; j++) {
                    if (savedAppId.equals(SLOT_APP_IDS[j])) { selection = j; break; }
                }
            }
            slotSpinners[i].setSelection(selection);
        }

        int sensitivity = prefs.getInt(KEY_SENSITIVITY, 15);
        seekSensitivity.setProgress(sensitivity);
        tvSensitivityValue.setText(String.format("%.1fx", 0.5f + (sensitivity / 10.0f)));

        boolean useSecure = prefs.getBoolean(DEFAULT_PORT, true);
        switchPort.setChecked(!useSecure);
        tvPortValue.setText(useSecure ? "8002 (Secure WSS)" : "8001 (WebSocket)");

        String label = prefs.getString(KEY_LABEL + (tvIp != null ? tvIp : ""), "");
        if (!label.isEmpty()) etDeviceLabel.setText(label);
    }
}
