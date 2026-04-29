package com.mctrash692.freemote.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
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

import androidx.appcompat.app.AppCompatActivity;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.util.ThemeManager;

public class SettingsActivity extends AppCompatActivity {

    public static final String PREFS_NAME      = "remote_prefs";
    public static final String KEY_TOKEN_PREFIX = "token_";
    public static final String KEY_LABEL        = "device_label_";
    public static final String KEY_SLOT_APPID   = "slot_app_";
    public static final String KEY_SLOT_KEY     = "slot_key_";
    public static final String KEY_SLOT_ICON    = "slot_icon_";
    public static final String KEY_SENSITIVITY  = "touchpad_sensitivity";
    public static final String DEFAULT_PORT     = "default_port_wss";

    private SharedPreferences prefs;
    private String tvIp;

    private Spinner  spinnerTheme;
    private Button   btnSaveTheme;
    private int      selectedTheme;

    private SeekBar  seekSensitivity;
    private TextView tvSensitivityValue;

    private Switch   switchPort;
    private TextView tvPortValue;

    private EditText etDeviceLabel;
    private Button   btnClearPairing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
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

        btnSaveTheme.setTextColor(Color.BLACK);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, ThemeManager.THEME_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupListeners() {
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTheme = position + 1;
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSaveTheme.setOnClickListener(v -> {
            ThemeManager.setTheme(this, selectedTheme);
        });

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
            prefs.edit().remove(KEY_TOKEN_PREFIX + tvIp).apply();
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
        spinnerTheme.setSelection(selectedTheme - 1);

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
