package com.mctrash692.freemote.util;

// ============================================================================
// FILE: ColorUtils.java
// WHAT:  Picks a text color (black or white) that is easy to read against
//        any background color. If the background is dark, it chooses white
//        text. If the background is light, it chooses black text.
// WHY:   When users pick custom colors for their theme, the text on top of
//        those colors needs to stay readable. This file figures out which
//        text color works best.
// ============================================================================

import android.graphics.Color;

public class ColorUtils {

    // ==========================================================================
    // METHOD: getContrastColor
    // WHAT:  Figures out whether black or white text will be easier to read
    //        on top of any background color. It calculates how bright the
    //        background is (luminance). If the background is bright, it picks
    //        black text. If the background is dark, it picks white text.
    // INPUT: backgroundColor = the color you are putting text on top of
    // OUTPUT: black (0xFF000000) or white (0xFFFFFFFF) whichever has better contrast
    // ==========================================================================

    public static int getContrastColor(int backgroundColor) {
        // Split the color into its red, green, and blue parts
        int r = Color.red(backgroundColor);
        int g = Color.green(backgroundColor);
        int b = Color.blue(backgroundColor);
        // Calculate perceived brightness using human eye sensitivity weights
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        // If bright (above 128), use black text; otherwise use white text
        return luminance > 128 ? 0xFF000000 : 0xFFFFFFFF;
    }
}
