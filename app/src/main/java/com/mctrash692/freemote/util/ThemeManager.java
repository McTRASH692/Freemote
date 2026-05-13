package com.mctrash692.freemote.util;

// ============================================================================
// FILE: ThemeManager.java
// WHAT:  Controls the look-and-feel (theme) of the entire app. There are
//        several preset themes to choose from (like "Cyberpunk", "Sunset",
//        "Forest", etc.) plus a "Custom" option. When you pick a theme, this
//        file saves your choice and tells the app to switch to it.
// WHY:   Themes make the app more pleasant to use and let you match the
//        remote's appearance to your mood or setup.
// ============================================================================

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.R;

public class ThemeManager {
    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    private static final String TAG = "ThemeManager";       // Used for logging/diagnostics
    private static final String PREF_NAME = "theme_prefs";  // The file name where theme choice is stored
    private static final String KEY_CURRENT_THEME = "current_theme";  // Storage key for the chosen theme number

    // Each theme is a number. These names make it clear which number is which.
    public static final int THEME_ORIGINAL = 0;      // Default dark purple/green theme
    public static final int THEME_CYBERPUNK = 1;     // Neon green/purple futuristic look
    public static final int THEME_SUNSET = 2;         // Warm orange/red evening colors
    public static final int THEME_FOREST = 3;         // Green/nature-inspired tones
    public static final int THEME_MIDNIGHT = 4;       // Dark blue late-night scheme
    public static final int THEME_OLED_RED = 5;       // Dark with red accents (saves battery on OLED screens)
    public static final int THEME_OCEAN = 6;          // Blue/aqua water-inspired colors
    public static final int THEME_LIGHT_CLEAN = 7;    // Bright white clean theme
    public static final int THEME_LIGHT_WARM = 8;     // Warm paper-toned light theme
    public static final int THEME_PURPLE_HAZE = 9;    // Purple/violet color scheme
    public static final int THEME_CUSTOM = 10;         // Your own custom colors

    // The display names shown to you in the theme picker
    public static final String[] THEME_NAMES = {
        "Original", "Cyberpunk", "Sunset", "Forest", "Midnight",
        "OLED Red", "Ocean", "Light", "Warm Paper", "Purple Haze", "Custom"
    };

    // These are the actual Android style resources inside the app that define
    // each theme's colors and appearance. Each theme number maps to a style.
    private static final int[] THEME_STYLES = {
        R.style.Theme_Freemote_Theme1,
        R.style.Theme_Freemote_Theme2,
        R.style.Theme_Freemote_Theme3,
        R.style.Theme_Freemote_Theme4,
        R.style.Theme_Freemote_Theme5,
        R.style.Theme_Freemote_Theme6,
        R.style.Theme_Freemote_Theme7,
        R.style.Theme_Freemote_Theme8,
        R.style.Theme_Freemote_Theme9,
        R.style.Theme_Freemote_Theme10,
        R.style.Theme_Freemote  // Custom uses base theme, colors applied via BaseActivity
    };

    // ==========================================================================
    // METHOD: applyTheme
    // WHAT:  Reads your saved theme choice and applies it to the current
    //        screen. Called every time a screen opens to make sure it shows
    //        the right colors.
    // INPUT: activity = the current screen that needs the theme applied
    // ==========================================================================

    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeIndex = prefs.getInt(KEY_CURRENT_THEME, THEME_ORIGINAL);
        
        // For custom theme, we don't need to check range since it's now in THEME_STYLES
        if (themeIndex < 0 || themeIndex >= THEME_STYLES.length) {
            Log.w(TAG, "Stored theme index " + themeIndex + " out of range, resetting to default");
            themeIndex = THEME_ORIGINAL;
            prefs.edit().putInt(KEY_CURRENT_THEME, THEME_ORIGINAL).apply();
        }
        
        Log.d(TAG, "applyTheme: setting theme index " + themeIndex + " (" + THEME_NAMES[themeIndex] + ")");
        activity.setTheme(THEME_STYLES[themeIndex]);
    }

    // ==========================================================================
    // METHOD: setTheme
    // WHAT:  Changes the app's theme to the one you picked. It saves your
    //        choice and then refreshes (recreates) the screen so the new
    //        colors take effect immediately.
    // INPUT: activity   = the current screen
    //        themeIndex = the number of the theme you want (0 to 10)
    // ==========================================================================

    public static void setTheme(Activity activity, int themeIndex) {
        if (themeIndex < 0 || themeIndex >= THEME_STYLES.length) {
            Log.w(TAG, "setTheme: invalid index " + themeIndex + ", ignoring");
            return;
        }
        Log.d(TAG, "setTheme: saving theme index " + themeIndex + " (" + THEME_NAMES[themeIndex] + ")");
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CURRENT_THEME, themeIndex).apply();
        activity.recreate();
    }

    // ==========================================================================
    // METHOD: getTheme
    // WHAT:  Reads which theme number is currently saved. Used to restore
    //        your theme choice when the app starts up.
    // INPUT: context = the app
    // OUTPUT: the theme number (0 = Original, 1 = Cyberpunk, etc.)
    // ==========================================================================

    public static int getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeIndex = prefs.getInt(KEY_CURRENT_THEME, THEME_ORIGINAL);
        if (themeIndex < 0 || themeIndex >= THEME_STYLES.length) return THEME_ORIGINAL;
        return themeIndex;
    }
    
    // ==========================================================================
    // METHOD: getThemeStyle
    // WHAT:  Looks up the Android style resource that matches a theme number.
    //        The style tells Android which colors and layout to use.
    // INPUT: themeIndex = the theme number
    // OUTPUT: the Android style resource ID
    // ==========================================================================

    public static int getThemeStyle(int themeIndex) {
        if (themeIndex >= 0 && themeIndex < THEME_STYLES.length) {
            return THEME_STYLES[themeIndex];
        }
        return THEME_STYLES[THEME_ORIGINAL];
    }
}
