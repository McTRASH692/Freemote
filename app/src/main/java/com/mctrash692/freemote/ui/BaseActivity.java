package com.mctrash692.freemote.ui;

// ============================================================================
// FILE: BaseActivity.java
// WHAT:  The base (parent) class for every screen in the app. It handles
//        automatically applying the chosen theme colors to all buttons,
//        text fields, labels, switches, and other visual elements so every
//        screen looks consistent. Each Activity screen in the app inherits
//        from this class so they all get the same theming behavior without
//        each one having to do it individually.
// ============================================================================

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.util.CustomThemeManager;
import com.mctrash692.freemote.util.ThemeManager;

// ==========================================================================
// SECTION: BASE ACTIVITY
// WHAT:  All screens in the app inherit from this class. It makes sure the
//        chosen color theme is applied to every button, label, text field,
//        and other element automatically so each screen looks consistent.
// ==========================================================================

public abstract class BaseActivity extends AppCompatActivity {

    // ==========================================================================
    // SECTION: THEME COLORS
    // WHAT:  Stores the colors for every part of the current theme. These
    //        are loaded from storage when each screen opens.
    // ==========================================================================

    // Whether a custom theme is active
    private boolean isCustomTheme = false;
    // Individual color values for each UI element type
    private int bgColor, buttonBg, primary, textPrimary, textSecondary;
    private int navZone, divider, touchpadBg, touchpadText;
    // Screen density (used to convert pixels to dips)
    private float density;

    // ==========================================================================
    // SECTION: SCREEN LIFECYCLE
    // WHAT:  Runs when the screen first opens. Loads the theme colors before
    //        anything else so the screen is already colored when it appears.
    // ==========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme first (before the screen layout loads)
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);

        // Check for custom theme after theme is applied
        isCustomTheme = CustomThemeManager.isCustomThemeActive(this);
        if (isCustomTheme) {
            bgColor = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_BACKGROUND);
            buttonBg = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_BUTTON_BG);
            primary = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_PRIMARY);
            textPrimary = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TEXT_PRIMARY);
            textSecondary = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TEXT_SECONDARY);
            navZone = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_NAV_ZONE);
            divider = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_DIVIDER);
            touchpadBg = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TOUCHPAD_BG);
            touchpadText = CustomThemeManager.getPermanentColor(this, CustomThemeManager.KEY_TOUCHPAD_TEXT);
        }

        density = getResources().getDisplayMetrics().density;
    }

    // ==========================================================================
    // SECTION: THEME APPLICATION
    // WHAT:  Every time a screen layout is loaded (setContentView), these
    //        methods check if a custom theme is active and apply its colors
    //        to all visible elements on the screen.
    // ==========================================================================

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        if (isCustomTheme) applyCustomColors(getWindow().getDecorView());
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (isCustomTheme) applyCustomColors(getWindow().getDecorView());
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        if (isCustomTheme) applyCustomColors(getWindow().getDecorView());
    }

    // ==========================================================================
    // METHOD: makeBackground
    // WHAT:  Creates a rounded rectangle shape with a border (stroke) in the
    //        given color. This is used to give buttons and other elements a
    //        consistent look with rounded corners and colored outlines.
    // INPUT: color = fill color, cornerRadius = rounding amount, 
    //        strokeColor = border color, strokeWidth = border thickness
    // ==========================================================================

    private GradientDrawable makeBackground(int color, int cornerRadius, int strokeColor, float strokeWidth) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setCornerRadius(cornerRadius * density);
        gd.setStroke(Math.round(strokeWidth * density), strokeColor);
        return gd;
    }

    // ==========================================================================
    // METHOD: applyCustomColors
    // WHAT:  Goes through every visible element on the screen and changes its
    //        colors to match the custom theme. Runs automatically whenever a
    //        screen loads. Image buttons get a colored background and tint,
    //        regular buttons get colored backgrounds, text fields get colored
    //        text and hints, and the touchpad area gets special colors.
    // INPUT: view = the starting element (usually the whole screen's root)
    // ==========================================================================

    private void applyCustomColors(View view) {
        if (view == null) return;

        // Apply different styling depending on the type of element
        if (view instanceof ImageButton) {
            ImageButton btn = (ImageButton) view;
            Object tag = btn.getTag();
            boolean isBrandIcon = "brand_icon".equals(tag);

            btn.setBackground(makeBackground(buttonBg, 8, primary, 2));

            // Brand icons (like YouTube, Netflix logos) keep their original colors
            if (!isBrandIcon) {
                btn.setColorFilter(textPrimary);
            } else {
                btn.clearColorFilter();
            }
        } else if (view instanceof Button) {
            ((Button) view).setBackground(makeBackground(buttonBg, 8, primary, 2));
            ((Button) view).setTextColor(textPrimary);
        } else if (view instanceof EditText) {
            GradientDrawable gd = makeBackground(buttonBg, 4, buttonBg, 0);
            view.setBackground(gd);
            ((EditText) view).setTextColor(textPrimary);
            ((EditText) view).setHintTextColor(textSecondary);
        } else if (view instanceof Spinner) {
            GradientDrawable gd = makeBackground(buttonBg, 8, buttonBg, 0);
            view.setBackground(gd);
        } else if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String text = tv.getText() != null ? tv.getText().toString() : "";
            // Labels like "VOL" and "CH" use secondary (dimmer) color
            if (text.equals("VOL") || text.equals("CH") || 
                tv.getHint() != null || (tv.getTag() != null && "brand_label".equals(tv.getTag()))) {
                tv.setTextColor(textSecondary);
            } else {
                tv.setTextColor(textPrimary);
            }
        } else if (view instanceof Switch) {
            // Keep switch default styling
        }

        // Special styling for the touchpad area
        if (view.getId() == R.id.layoutTouchpad) {
            view.setBackground(makeBackground(touchpadBg, 16, primary, 2));
            if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(touchpadText);
                    }
                }
            }
        }

        // Recursively process all child elements inside this one
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyCustomColors(group.getChildAt(i));
            }
        }
    }
}
