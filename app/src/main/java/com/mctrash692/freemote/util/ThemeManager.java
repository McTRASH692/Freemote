package com.mctrash692.freemote.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.R;

public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String PREF_NAME        = "theme_prefs";
    private static final String KEY_CURRENT_THEME = "current_theme";

    public static final int THEME_ORIGINAL    = 0;
    public static final int THEME_CYBERPUNK   = 1;
    public static final int THEME_SUNSET      = 2;
    public static final int THEME_FOREST      = 3;
    public static final int THEME_MIDNIGHT    = 4;
    public static final int THEME_OLED_RED    = 5;
    public static final int THEME_OCEAN       = 6;
    public static final int THEME_LIGHT_CLEAN = 7;
    public static final int THEME_LIGHT_WARM  = 8;
    public static final int THEME_PURPLE_HAZE = 9;
    public static final int THEME_CUSTOM      = 10;

    public static final String[] THEME_NAMES = {
        "Original", "Cyberpunk", "Sunset", "Forest", "Midnight",
        "OLED Red", "Ocean", "Light", "Warm Paper", "Purple Haze", "Custom..."
    };

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
        R.style.Theme_Freemote_Custom
    };

    public static void applyTheme(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeIndex = prefs.getInt(KEY_CURRENT_THEME, THEME_ORIGINAL);
        // N5: clamp to valid range so a corrupted pref value can't crash every activity.
        if (themeIndex < 0 || themeIndex >= THEME_STYLES.length) {
            Log.w(TAG, "Stored theme index " + themeIndex + " out of range, resetting to default");
            themeIndex = THEME_ORIGINAL;
            prefs.edit().putInt(KEY_CURRENT_THEME, THEME_ORIGINAL).apply();
        }
        activity.setTheme(THEME_STYLES[themeIndex]);
    }

    public static void setTheme(Activity activity, int themeIndex) {
        if (themeIndex < 0 || themeIndex >= THEME_STYLES.length) {
            Log.w(TAG, "setTheme: invalid index " + themeIndex + ", ignoring");
            return;
        }
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CURRENT_THEME, themeIndex).apply();
        activity.recreate();
    }

    public static int getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int themeIndex = prefs.getInt(KEY_CURRENT_THEME, THEME_ORIGINAL);
        if (themeIndex < 0 || themeIndex >= THEME_STYLES.length) return THEME_ORIGINAL;
        return themeIndex;
    }
}
