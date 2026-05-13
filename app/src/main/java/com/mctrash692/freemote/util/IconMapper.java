package com.mctrash692.freemote.util;

// ============================================================================
// FILE: IconMapper.java
// WHAT:  Picks the right icon to show next to each TV or app in the app.
//        For example, if a TV is named "Samsung QLED", it shows the Samsung
//        icon. If an app shortcut says "Netflix", it shows the Netflix logo.
// WHY:   Icons make it easy to tell devices and apps apart at a glance
//        instead of just reading text labels.
// ============================================================================

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.model.TvDevice;

import java.util.HashMap;
import java.util.Map;

public class IconMapper {

    // ==========================================================================
    // CONSTANTS
    // WHAT:  A lookup table that connects app names and TV brand names to
    //        their matching icon images.
    // ==========================================================================

    // Maps a keyword (like "netflix" or "youtube") to a drawable icon resource
    private static final Map<String, Integer> iconMap = new HashMap<>();

    // Fill the map with all known app/TV brand keywords and their icons
    static {
        iconMap.put("netflix", R.drawable.netflix);        // Netflix streaming service
        iconMap.put("youtube", R.drawable.youtube);        // YouTube video platform
        iconMap.put("prime", R.drawable.primevideo);       // Amazon Prime Video
        iconMap.put("amazon", R.drawable.primevideo);      // Amazon (also maps to Prime Video icon)
        iconMap.put("disney", R.drawable.disneyplus);      // Disney+
        iconMap.put("hulu", R.drawable.hulu);              // Hulu
        iconMap.put("hbo", R.drawable.hbomax);             // HBO Max
        iconMap.put("max", R.drawable.hbomax);             // "Max" (also maps to HBO Max icon)
        iconMap.put("plex", R.drawable.plex);              // Plex media server
        iconMap.put("kodi", R.drawable.kodi);              // Kodi media center
        iconMap.put("apple", R.drawable.appletv);          // Apple TV
        iconMap.put("tv+", R.drawable.appletv);            // Apple TV+ (maps to same icon)

        // Also map TV brand names to their icons
        iconMap.put(TvDevice.Type.ANDROID_TV.name().toLowerCase(), R.drawable.androidtv);
        iconMap.put(TvDevice.Type.SAMSUNG.name().toLowerCase(), R.drawable.samsung);
    }

    // ==========================================================================
    // METHOD: getIconForDevice
    // WHAT:  Picks the best icon to show for a TV or device. It first checks
    //        the device's name for any known app keyword (like "Netflix"),
    //        and if nothing matches, it shows the icon for the TV's brand
    //        (like the Samsung logo).
    // INPUT: device = the TV or device to find an icon for
    // OUTPUT: the ID of the icon image to display
    // ==========================================================================

    public static int getIconForDevice(TvDevice device) {
        String name = device.getName().toLowerCase();

        Integer exactMatch = iconMap.get(name);
        if (exactMatch != null) return exactMatch;

        for (Map.Entry<String, Integer> entry : iconMap.entrySet()) {
            if (name.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        switch (device.getType()) {
            case ANDROID_TV: return R.drawable.androidtv;
            case SAMSUNG:    return R.drawable.samsung;
            default:         return R.drawable.default_icon;
        }
    }
}
