package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: SettingsActivity.java
// WHAT:  The Settings screen where you can customize the app. From here
//        you can change the app theme (color scheme), manage your paired
//        TV devices, and view app information in the About section.
// ============================================================================

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

// ==========================================================================
// SECTION: SETTINGS SCREEN
// WHAT:  This screen lets you customize the app theme (color scheme),
//        manage your paired TVs, and view app information.
// ==========================================================================

public class SettingsActivity extends BaseActivity {

    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    // Storage file for saving app preferences
    public static final String PREFS_NAME = "remote_prefs";
    // Prefix used when saving TV authentication tokens
    public static final String KEY_TOKEN_PREFIX = "token_";
    // Prefix for saving which app each shortcut button opens
    public static final String KEY_SLOT_APPID = "slot_app_";
    // Prefix for saving each shortcut button's icon
    public static final String KEY_SLOT_ICON = "slot_icon_";
    // Key for saving the touchpad sensitivity setting
    public static final String KEY_SENSITIVITY = "touchpad_sensitivity";

    // Theme picker dropdown menu
    private Spinner spinnerTheme;
    // Save theme button
    private Button btnSaveTheme;
    // Which theme position is currently selected in the dropdown
    private int selectedTheme;

    // ==========================================================================
    // SECTION: SCREEN SETUP
    // WHAT:  Runs when the Settings screen first opens. Gets everything
    //        ready: finds all the buttons, sets up what each one does,
    //        and loads the current settings.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupListeners();
        loadSettings();
    }

    // ==========================================================================
    // METHOD: initViews
    // WHAT:  Finds all the buttons, dropdowns, and clickable areas on the
    //        Settings screen and sets up what each one does when tapped.
    // ==========================================================================

    private void initViews() {
        spinnerTheme = findViewById(R.id.spinnerTheme);
        btnSaveTheme = findViewById(R.id.btnSave);

        LinearLayout layoutDeviceManagement = findViewById(R.id.layoutDeviceManagement);
        LinearLayout layoutAbout = findViewById(R.id.layoutAbout);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Fill the theme dropdown with the list of theme names
        ArrayAdapter<String> themeAdapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, ThemeManager.THEME_NAMES);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTheme.setAdapter(themeAdapter);

        // Device Management - opens device list
        layoutDeviceManagement.setOnClickListener(v -> {
            Intent intent = new Intent(this, DeviceManagementActivity.class);
            startActivity(intent);
        });

        // About dialog
        layoutAbout.setOnClickListener(v -> {
            DialogFragment aboutDialog = new AboutDialogFragment();
            aboutDialog.show(getSupportFragmentManager(), "about_dialog");
        });
    }

    // ==========================================================================
    // METHOD: setupListeners
    // WHAT:  Connects the theme dropdown and save button to their actions.
    //        When you select a theme from the dropdown, it remembers your
    //        choice. When you tap Save, it applies that theme (or opens the
    //        custom theme editor if you picked "Custom").
    // ==========================================================================

    private void setupListeners() {
        // Remember which theme the user selected in the dropdown
        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTheme = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // When Save is tapped, apply the selected theme or open custom editor
        btnSaveTheme.setOnClickListener(v -> {
            if (selectedTheme == ThemeManager.THEME_CUSTOM) {
                startActivity(new Intent(this, CustomThemeActivity.class));
            } else {
                ThemeManager.setTheme(this, selectedTheme);
            }
        });
    }

    // ==========================================================================
    // METHOD: loadSettings
    // WHAT:  Reads the currently saved theme from storage and sets the
    //        dropdown to show that theme as selected.
    // ==========================================================================

    private void loadSettings() {
        selectedTheme = ThemeManager.getTheme(this);
        spinnerTheme.setSelection(selectedTheme);
    }
}
