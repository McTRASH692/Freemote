package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: CustomThemeActivity.java
// WHAT:  The Custom Theme Editor screen where you can create your own
//        color scheme for the app. You can pick colors for the background,
//        buttons, accent elements, text, navigation area, dividers, and
//        the touchpad area. A live preview box shows what your theme will
//        look like as you adjust each color. You can save the theme (it
//        restarts the app to apply it), cancel without saving, or reset
//        all colors back to their defaults.
// ============================================================================

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.util.ColorUtils;
import com.mctrash692.freemote.util.CustomThemeManager;
import com.mctrash692.freemote.util.ThemeManager;

import java.util.HashMap;
import java.util.Map;

// ==========================================================================
// SECTION: CUSTOM THEME EDITOR
// WHAT:  Lets you create your own color scheme for the app. You can
//        pick individual colors for the background, buttons, text,
//        navigation area, dividers, and touchpad. A preview box shows
//        what the result will look like as you make changes.
// ==========================================================================

public class CustomThemeActivity extends BaseActivity {
    
    // Links each theme component name to its "Pick" button for quick updates
    private Map<String, Button> colorButtons = new HashMap<>();
    // Preview box showing a sample of the current theme
    private LinearLayout previewBox;
    private Button sampleButton;
    private TextView sampleText;
    private TextView sampleSecondary;
    
    // Internal keys used to save/load each color setting
    private final String[] COMPONENTS = {
        CustomThemeManager.KEY_BACKGROUND,
        CustomThemeManager.KEY_BUTTON_BG,
        CustomThemeManager.KEY_PRIMARY,
        CustomThemeManager.KEY_TEXT_PRIMARY,
        CustomThemeManager.KEY_TEXT_SECONDARY,
        CustomThemeManager.KEY_NAV_ZONE,
        CustomThemeManager.KEY_DIVIDER,
        CustomThemeManager.KEY_TOUCHPAD_BG,
        CustomThemeManager.KEY_TOUCHPAD_TEXT
    };
    
    // Human-readable names shown on screen for each color setting
    private final String[] COMPONENT_NAMES = {
        "Background Color",
        "Button Background",
        "Accent Color",
        "Text Primary Color",
        "Text Secondary Color",
        "Navigation Zone",
        "Divider Color",
        "Touchpad Background",
        "Touchpad Text"
    };
    
    // ==========================================================================
    // METHOD: onCreate
    // WHAT:  Runs when the Custom Theme Editor opens. Builds the entire
    //        screen from scratch — a title, a preview box showing the
    //        current colors, a list of color picker buttons for each
    //        theme component, and Save/Cancel/Reset action buttons.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Create a scrollable layout container
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);
        
        // Screen title
        TextView title = new TextView(this);
        title.setText("Custom Theme Editor");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);
        
        // Preview box — shows a live example of the current theme
        previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.setPadding(16, 16, 16, 16);
        previewBox.setBackgroundColor(CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_BACKGROUND));
        
        sampleButton = new Button(this);
        sampleButton.setText("Sample Button");
        sampleButton.setPadding(16, 12, 16, 12);
        sampleButton.setBackgroundColor(CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_BUTTON_BG));
        sampleButton.setTextColor(CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_TEXT_PRIMARY));
        previewBox.addView(sampleButton);
        
        sampleText = new TextView(this);
        sampleText.setText("Sample primary text");
        sampleText.setTextColor(CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_TEXT_PRIMARY));
        sampleText.setPadding(0, 16, 0, 8);
        previewBox.addView(sampleText);
        
        sampleSecondary = new TextView(this);
        sampleSecondary.setText("Sample secondary text");
        sampleSecondary.setTextColor(CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_TEXT_SECONDARY));
        previewBox.addView(sampleSecondary);
        
        mainLayout.addView(previewBox);
        
        // Color picker buttons — one row for each theme component
        for (int i = 0; i < COMPONENTS.length; i++) {
            final String component = COMPONENTS[i];
            final String displayName = COMPONENT_NAMES[i];
            
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);
            
            TextView label = new TextView(this);
            label.setText(displayName);
            label.setTextColor(Color.WHITE);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            
            Button colorBtn = new Button(this);
            int currentColor = CustomThemeManager.getTempColor(this, component);
            colorBtn.setBackgroundColor(currentColor);
            colorBtn.setText("Pick");
            colorBtn.setTextColor(ColorUtils.getContrastColor(currentColor));
            colorBtn.setOnClickListener(v -> showColorPicker(component, colorBtn));

            colorButtons.put(component, colorBtn);
            
            row.addView(label);
            row.addView(colorBtn);
            mainLayout.addView(row);
        }
        
        // Action buttons — Save, Cancel, Reset
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 32, 0, 0);
        
        Button saveBtn = new Button(this);
        saveBtn.setText("Save Theme");
        saveBtn.setBackgroundColor(0xFF00FF00);
        saveBtn.setTextColor(0xFF000000);
        saveBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        saveBtn.setOnClickListener(v -> saveTheme());
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("Cancel");
        cancelBtn.setBackgroundColor(0xFF666666);
        cancelBtn.setTextColor(0xFFFFFFFF);
        cancelBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        cancelBtn.setOnClickListener(v -> {
            CustomThemeManager.discardTempTheme(this);
            finish();
        });
        
        Button resetBtn = new Button(this);
        resetBtn.setText("Reset");
        resetBtn.setBackgroundColor(0xFFFF0000);
        resetBtn.setTextColor(0xFFFFFFFF);
        resetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        resetBtn.setOnClickListener(v -> resetToDefaults());
        
        buttonRow.addView(saveBtn);
        buttonRow.addView(cancelBtn);
        buttonRow.addView(resetBtn);
        mainLayout.addView(buttonRow);
        
        scrollView.addView(mainLayout);
        setContentView(scrollView);
    }
    
    // ==========================================================================
    // METHOD: showColorPicker
    // WHAT:  Opens a dialog with a list of preset colors to choose from.
    //        When you tap a color, it saves that choice temporarily and
    //        updates the preview to show the new look.
    // INPUT: component = which theme element this color is for
    //        pickerBtn = the "Pick" button whose color changes to match
    // ==========================================================================

    private void showColorPicker(String component, Button pickerBtn) {
        // List of preset colors and their display names
        final int[] colors = {
            0xFF000000, 0xFFFFFFFF, 0xFF00FF00, 0xFF0000FF, 0xFFFF0000,
            0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF, 0xFF1A0D33, 0xFF0D1B2A,
            0xCCFFFFFF, 0x80FFFFFF, 0xFF4CAF50, 0xFF2196F3, 0xFFFF5722
        };
        final String[] colorNames = {
            "Black", "White", "Green", "Blue", "Red",
            "Yellow", "Pink", "Cyan", "Deep Purple", "Dark Blue",
            "Light White", "Semi-White", "Material Green", "Material Blue", "Orange"
        };
        
        new AlertDialog.Builder(this)
            .setTitle("Pick: " + component)
            .setItems(colorNames, (dialog, which) -> {
                int selectedColor = colors[which];
                CustomThemeManager.saveTempColor(this, component, selectedColor);
                pickerBtn.setBackgroundColor(selectedColor);
                pickerBtn.setTextColor(ColorUtils.getContrastColor(selectedColor));
                updatePreview();
            })
            .show();
    }
    
    // ==========================================================================
    // METHOD: updatePreview
    // WHAT:  Refreshes the preview box to show the current temporary theme
    //        colors. Called every time a color is picked.
    // ==========================================================================

    private void updatePreview() {
        int bgColor = CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_BACKGROUND);
        int btnBg = CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_BUTTON_BG);
        int textPrimary = CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_TEXT_PRIMARY);
        int textSecondary = CustomThemeManager.getTempColor(this, CustomThemeManager.KEY_TEXT_SECONDARY);
        
        previewBox.setBackgroundColor(bgColor);
        sampleButton.setBackgroundColor(btnBg);
        sampleButton.setTextColor(textPrimary);
        sampleText.setTextColor(textPrimary);
        sampleSecondary.setTextColor(textSecondary);
    }
    
    // ==========================================================================
    // METHOD: resetToDefaults
    // WHAT:  Resets every theme color back to the app's default values.
    //        Updates all the "Pick" buttons and the preview to match.
    // ==========================================================================

    private void resetToDefaults() {
        for (Map.Entry<String, Button> entry : colorButtons.entrySet()) {
            String component = entry.getKey();
            int defaultColor = CustomThemeManager.getDefaultColor(component);
            CustomThemeManager.saveTempColor(this, component, defaultColor);
            Button btn = entry.getValue();
            btn.setBackgroundColor(defaultColor);
            btn.setTextColor(ColorUtils.getContrastColor(defaultColor));
        }
        updatePreview();
    }
    
    // ==========================================================================
    // METHOD: saveTheme
    // WHAT:  Saves the current temporary theme as the permanent theme,
    //        marks it as active, then restarts the app so the new colors
    //        take effect everywhere.
    // ==========================================================================

    private void saveTheme() {
        // Save permanent theme
        CustomThemeManager.savePermanentTheme(this);
        
        // IMPORTANT: Save the theme index to theme_prefs
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        themePrefs.edit().putInt("current_theme", ThemeManager.THEME_CUSTOM).apply();
        
        // Restart the app to apply theme
        Intent intent = new Intent(this, DeviceDiscoveryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
}
