package com.mctrash692.freemote.util;

import android.content.Context;
import android.content.SharedPreferences;

public class CustomThemeManager {
    private static final String PREFS_TEMP = "custom_theme_temp";
    private static final String PREFS_PERM = "custom_theme_permanent";
    
    public static final String KEY_BACKGROUND = "background";
    public static final String KEY_BUTTON_BG = "buttonBg";
    public static final String KEY_PRIMARY = "primary";
    public static final String KEY_TEXT_PRIMARY = "textPrimary";
    public static final String KEY_TEXT_SECONDARY = "textSecondary";
    public static final String KEY_NAV_ZONE = "navZone";
    public static final String KEY_DIVIDER = "divider";
    public static final String KEY_TOUCHPAD_BG = "touchpadBg";
    public static final String KEY_TOUCHPAD_TEXT = "touchpadText";
    
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
    
    public static void saveTempColor(Context context, String component, int color) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        prefs.edit().putInt(component, color).apply();
    }
    
    public static int getTempColor(Context context, String component) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        return prefs.getInt(component, getDefaultColor(component));
    }
    
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
    }
    
    public static void discardTempTheme(Context context) {
        SharedPreferences tempPrefs = context.getSharedPreferences(PREFS_TEMP, Context.MODE_PRIVATE);
        tempPrefs.edit().clear().apply();
    }
    
    public static int getPermanentColor(Context context, String component) {
        SharedPreferences permPrefs = context.getSharedPreferences(PREFS_PERM, Context.MODE_PRIVATE);
        return permPrefs.getInt(component, getDefaultColor(component));
    }
    
    public static boolean isCustomThemeActive(Context context) {
        SharedPreferences themePrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        return themePrefs.getInt("current_theme", 0) == 10;
    }
}
