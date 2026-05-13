package com.mctrash692.freemote.util;

// ============================================================================
// FILE: CustomThemeManager.java
// WHAT:  Lets you create your own custom color theme for the app. You pick
//        colors for the background, buttons, text, touchpad, and other parts
//        of the screen. Colors can be previewed (temporary) before saving
//        (permanent). If you do not save, the preview colors are thrown away.
// WHY:   Not everyone likes the preset themes. This file gives you the
//        freedom to make the app look exactly how you want.
// ============================================================================

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class CustomThemeManager {
    
    // ==========================================================================
    // CONSTANTS
    // WHAT:  Names of the two storage areas (temporary preview vs. permanent)
    //        and the names of each color setting you can change.
    // ==========================================================================

    // Temporary storage (colors you are previewing but haven't saved yet)
    private static final String PREFS_TEMP = "custom_theme_temp";
    // Permanent storage (colors you chose to keep)
    private static final String PREFS_PERM = "custom_theme_permanent";

    // Each of these is a name for a part of the screen whose color you can change
    public static final String KEY_BACKGROUND = "background";     // Main background color of the screen
    public static final String KEY_BUTTON_BG = "buttonBg";       // Background color of buttons
    public static final String KEY_PRIMARY = "primary";           // Main accent color (highlights, borders)
    public static final String KEY_TEXT_PRIMARY = "textPrimary";  // Color of main text on the screen
    public static final String KEY_TEXT_SECONDARY = "textSecondary";  // Color of less important text
    public static final String KEY_NAV_ZONE = "navZone";          // Color of the navigation area
    public static final String KEY_DIVIDER = "divider";           // Color of lines that separate sections
    public static final String KEY_TOUCHPAD_BG = "touchpadBg";   // Background color of the touchpad area
    public static final String KEY_TOUCHPAD_TEXT = "touchpadText";  // Text color on the touchpad

    // ==========================================================================
    // METHOD: getDefaultColor
    // WHAT:  Returns the default (factory) color for any part of the theme.
    //        Used when you haven't changed a color or when resetting to
    //        defaults.
    // INPUT: component = which part of the theme (background, button, etc.)
    // OUTPUT: the default color as a number (hex ARGB value like 0xFF000000)
    // ==========================================================================

    public static int getDefaultColor(String component) {
        switch (component) {
            case KEY_BACKGROUND: return 0xFF000000;
            case KEY_BUTTON_BG: return 0xFF1A0D33;
            case KEY_PRIMARY: return 0xFF00FF00;
            case KEY_TEXT_PRIMARY: return 0xFFFFFFFF;
            case KEY_TEXT_SECONDARY: return 0x80FFFFFF;
            case KEY_NAV_ZONE: return 0xFF1A0D33;
            case KEY_DIVIDER: return 0xFF00FF00;
            case KEY_TOUCHPAD_BG: return 0xFF1A0D33;
            case KEY_TOUCHPAD_TEXT: return 0xCCFFFFFF;
            default: return 0xFF000000;
        }
    }

    // ==========================================================================
    // SECTION: TEMPORARY (PREVIEW) COLORS
    // WHAT:  Functions for trying out colors without saving them. You see
    //        the changes immediately but they are discarded if you don't save.
    // ==========================================================================

    // ==========================================================================
    // METHOD: saveTempColor
    // WHAT:  Stores a single color change as a preview (not yet permanent).
    //        You can change multiple colors and see how they look together.
    // INPUT: context   = the app (needed to access storage)
    //        component = which part of the theme to change
    //        color     = the new color as a number
    // ==========================================================================

    public static void saveTempColor(Context context, String component, int color) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        prefs.edit().putInt(component, color).apply();
    }

    // ==========================================================================
    // METHOD: getTempColor
    // WHAT:  Gets a preview color you set earlier. If you haven't changed
    //        that part, it returns the default color instead.
    // INPUT: context   = the app
    //        component = which part of the theme to look up
    // OUTPUT: the color (either your preview choice or the default)
    // ==========================================================================

    public static int getTempColor(Context context, String component) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        return prefs.getInt(component, getDefaultColor(component));
    }

    // ==========================================================================
    // SECTION: PERMANENT THEME
    // WHAT:  Functions for saving your chosen colors so they stay even after
    //        you close and reopen the app.
    // ==========================================================================

    // ==========================================================================
    // METHOD: savePermanentTheme
    // WHAT:  Takes all the colors you previewed and saves them permanently.
    //        Also marks the "Custom" theme as active in the theme settings.
    // INPUT: context = the app (needed to access storage)
    // ==========================================================================

    public static void savePermanentTheme(Context context) {
        SharedPreferences tempPrefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        SharedPreferences permPrefs = context.getSharedPreferences(PREFS_PERM, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = permPrefs.edit();
        editor.putInt(KEY_BACKGROUND, tempPrefs.getInt(KEY_BACKGROUND, getDefaultColor(KEY_BACKGROUND)));
        editor.putInt(KEY_BUTTON_BG, tempPrefs.getInt(KEY_BUTTON_BG, getDefaultColor(KEY_BUTTON_BG)));
        editor.putInt(KEY_PRIMARY, tempPrefs.getInt(KEY_PRIMARY, getDefaultColor(KEY_PRIMARY)));
        editor.putInt(KEY_TEXT_PRIMARY, tempPrefs.getInt(KEY_TEXT_PRIMARY, getDefaultColor(KEY_TEXT_PRIMARY)));
        editor.putInt(KEY_TEXT_SECONDARY, tempPrefs.getInt(KEY_TEXT_SECONDARY, getDefaultColor(KEY_TEXT_SECONDARY)));
        editor.putInt(KEY_NAV_ZONE, tempPrefs.getInt(KEY_NAV_ZONE, getDefaultColor(KEY_NAV_ZONE)));
        editor.putInt(KEY_DIVIDER, tempPrefs.getInt(KEY_DIVIDER, getDefaultColor(KEY_DIVIDER)));
        editor.putInt(KEY_TOUCHPAD_BG, tempPrefs.getInt(KEY_TOUCHPAD_BG, getDefaultColor(KEY_TOUCHPAD_BG)));
        editor.putInt(KEY_TOUCHPAD_TEXT, tempPrefs.getInt(KEY_TOUCHPAD_TEXT, getDefaultColor(KEY_TOUCHPAD_TEXT)));
        editor.apply();
        
        // Also save that custom theme is active
        SharedPreferences themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        themePrefs.edit().putInt("current_theme", ThemeManager.THEME_CUSTOM).apply();
    }

    // ==========================================================================
    // METHOD: discardTempTheme
    // WHAT:  Throws away all the temporary preview colors you were trying
    //        out. Use this when you decide you don't like the changes.
    // INPUT: context = the app
    // ==========================================================================

    public static void discardTempTheme(Context context) {
        SharedPreferences tempPrefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        tempPrefs.edit().clear().apply();
    }

    // ==========================================================================
    // METHOD: getPermanentColor
    // WHAT:  Gets a color from your permanently saved custom theme. If you
    //        never changed that part, it returns the default.
    // INPUT: context   = the app
    //        component = which part of the theme to look up
    // OUTPUT: the saved color (or default if never changed)
    // ==========================================================================

    public static int getPermanentColor(Context context, String component) {
        SharedPreferences permPrefs = context.getSharedPreferences(PREFS_PERM, Context.MODE_PRIVATE);
        return permPrefs.getInt(component, getDefaultColor(component));
    }

    // ==========================================================================
    // METHOD: isCustomThemeActive
    // WHAT:  Checks whether the custom theme is currently turned on.
    // INPUT: context = the app
    // OUTPUT: true if the custom theme is active, false otherwise
    // ==========================================================================

    public static boolean isCustomThemeActive(Context context) {
        SharedPreferences themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        return themePrefs.getInt("current_theme", 0) == ThemeManager.THEME_CUSTOM;
    }
}
