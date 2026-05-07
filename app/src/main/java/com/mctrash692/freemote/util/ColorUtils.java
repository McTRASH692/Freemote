package com.mctrash692.freemote.util;

import android.graphics.Color;

public class ColorUtils {
    public static int getContrastColor(int backgroundColor) {
        int brightness = (Color.red(backgroundColor) + Color.green(backgroundColor) + Color.blue(backgroundColor)) / 3;
        return brightness > 128 ? 0xFF000000 : 0xFFFFFFFF;
    }
}
