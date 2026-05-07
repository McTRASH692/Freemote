package com.mctrash692.freemote.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.util.ThemeManager;

public class SettingsActivity extends BaseActivity {

    public static final String PREFS_NAME = "remote_prefs";
    public static final String KEY_TOKEN_PREFIX = "token_";
    public static final String KEY_LABEL = "device_label_";
    public static final String KEY_MAC = "mac_";
    public static final String KEY_SLOT_APPID = "slot_app_";
    public static final String KEY_SLOT_KEY = "slot_key_";
    public static final String KEY_SLOT_ICON = "slot_icon_";
    public static final String KEY_SENSITIVITY = "touchpad_sensitivity";
    public static final String DEFAULT_PORT = "default_port_wss";

    private Spinner spinnerTheme;
    private Button btnSaveTheme;
    private int selectedTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupListeners();
        loadSettings();
    }

    private void initViews() {
        spinnerTheme = findViewById(R.id.spinnerTheme);
        btnSaveTheme = findViewById(R.id.btnSave);

        LinearLayout layoutDeviceManagement = findViewById(R.id.layoutDeviceManagement);
        LinearLayout layoutBluetooth = findViewById(R.id.layoutBluetooth);
        LinearLayout layoutAbout = findViewById(R.id.layoutAbout);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, ThemeManager.THEME_NAMES);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);

        // Device Management - opens device list
        layoutDeviceManagement.setOnClickListener(v -> {
            Intent intent = new Intent(this, DeviceManagementActivity.class);
            startActivity(intent);
        });

        // Bluetooth placeholder (future feature)
        layoutBluetooth.setOnClickListener(v -> {
            Toast.makeText(this, "Bluetooth remote support coming in a future update", Toast.LENGTH_LONG).show();
        });

        // About dialog
        layoutAbout.setOnClickListener(v -> {
            DialogFragment aboutDialog = new AboutDialogFragment();
            aboutDialog.show(getSupportFragmentManager(), "about_dialog");
        });
    }

    private void setupListeners() {
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTheme = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnSaveTheme.setOnClickListener(v -> {
            if (selectedTheme == ThemeManager.THEME_CUSTOM) {
                startActivity(new Intent(this, CustomThemeActivity.class));
            } else {
                ThemeManager.setTheme(this, selectedTheme);
            }
        });
    }

    private void loadSettings() {
        selectedTheme = ThemeManager.getTheme(this);
        spinnerTheme.setSelection(selectedTheme);
    }
}
