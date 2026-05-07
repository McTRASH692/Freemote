package com.mctrash692.freemote.ui;

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
import com.mctrash692.freemote.util.CustomThemeManager;
import com.mctrash692.freemote.util.ThemeManager;

import java.util.HashMap;
import java.util.Map;

public class CustomThemeActivity extends BaseActivity {
    
    private Map<String, Button> colorButtons = new HashMap<>();
    private LinearLayout previewBox;
    private Button sampleButton;
    private TextView sampleText;
    private TextView sampleSecondary;
    
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);
        
        TextView title = new TextView(this);
        title.setText("Custom Theme Editor");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, 32);
        mainLayout.addView(title);
        
        // Preview box
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
        
        // Color picker buttons
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
            colorBtn.setTextColor(getContrastColor(currentColor));
            colorBtn.setOnClickListener(v -> showColorPicker(component, colorBtn));
            
            colorButtons.put(component, colorBtn);
            
            row.addView(label);
            row.addView(colorBtn);
            mainLayout.addView(row);
        }
        
        // Action buttons
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
    
    private void showColorPicker(String component, Button pickerBtn) {
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
                pickerBtn.setTextColor(getContrastColor(selectedColor));
                updatePreview();
            })
            .show();
    }
    
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
    
    private void resetToDefaults() {
        for (Map.Entry<String, Button> entry : colorButtons.entrySet()) {
            String component = entry.getKey();
            int defaultColor = CustomThemeManager.getDefaultColor(component);
            CustomThemeManager.saveTempColor(this, component, defaultColor);
            Button btn = entry.getValue();
            btn.setBackgroundColor(defaultColor);
            btn.setTextColor(getContrastColor(defaultColor));
        }
        updatePreview();
        Toast.makeText(this, "Reset to default colors", Toast.LENGTH_SHORT).show();
    }
    
    private void saveTheme() {
        // Save permanent theme
        CustomThemeManager.savePermanentTheme(this);
        
        // IMPORTANT: Save the theme index to theme_prefs
        SharedPreferences themePrefs = getSharedPreferences("theme_prefs", MODE_PRIVATE);
        themePrefs.edit().putInt("current_theme", ThemeManager.THEME_CUSTOM).apply();
        
        Toast.makeText(this, "Custom theme saved! Restarting...", Toast.LENGTH_LONG).show();
        
        // Restart the app to apply theme
        Intent intent = new Intent(this, DeviceDiscoveryActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private int getContrastColor(int backgroundColor) {
        int brightness = (Color.red(backgroundColor) + Color.green(backgroundColor) + Color.blue(backgroundColor)) / 3;
        return brightness > 128 ? 0xFF000000 : 0xFFFFFFFF;
    }
}
