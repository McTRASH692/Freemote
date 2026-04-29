package com.mctrash692.freemote.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.mctrash692.freemote.R;

public class ThemeManager {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "current_theme";

    public static final int THEME_ORIGINAL = 1;
    public static final int THEME_CYBERPUNK = 2;
    public static final int THEME_SUNSET = 3;
    public static final int THEME_FOREST = 4;
    public static final int THEME_MIDNIGHT = 5;
    public static final int THEME_OLED_RED = 6;
    public static final int THEME_OCEAN = 7;
    public static final int THEME_LIGHT_CLEAN = 8;
    public static final int THEME_LIGHT_WARM = 9;
    public static final int THEME_PURPLE = 10;

    public static final String[] THEME_NAMES = {
        "Original", "Cyberpunk", "Sunset", "Forest", "Midnight",
        "OLED Red", "Ocean", "Light Clean", "Light Warm", "Purple Haze"
    };

    public static void applyTheme(Activity activity) {
        int themeId = getTheme(activity);
        switch (themeId) {
            case THEME_CYBERPUNK:
                activity.setTheme(R.style.Theme_Freemote_Theme2);
                break;
            case THEME_SUNSET:
                activity.setTheme(R.style.Theme_Freemote_Theme3);
                break;
            case THEME_FOREST:
                activity.setTheme(R.style.Theme_Freemote_Theme4);
                break;
            case THEME_MIDNIGHT:
                activity.setTheme(R.style.Theme_Freemote_Theme5);
                break;
            case THEME_OLED_RED:
                activity.setTheme(R.style.Theme_Freemote_Theme6);
                break;
            case THEME_OCEAN:
                activity.setTheme(R.style.Theme_Freemote_Theme7);
                break;
            case THEME_LIGHT_CLEAN:
                activity.setTheme(R.style.Theme_Freemote_Theme8);
                break;
            case THEME_LIGHT_WARM:
                activity.setTheme(R.style.Theme_Freemote_Theme9);
                break;
            case THEME_PURPLE:
                activity.setTheme(R.style.Theme_Freemote_Theme10);
                break;
            default:
                activity.setTheme(R.style.Theme_Freemote_Theme1);
                break;
        }
    }

    public static void setTheme(Context context, int themeId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_THEME, themeId).apply();
    }

    public static int getTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, THEME_ORIGINAL);
    }
}
