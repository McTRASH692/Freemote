package com.mctrash692.freemote.util;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.model.TvDevice;

import java.util.HashMap;
import java.util.Map;

public class IconMapper {

    private static final Map<String, Integer> iconMap = new HashMap<>();

    static {
        iconMap.put("netflix", R.drawable.netflix);
        iconMap.put("youtube", R.drawable.youtube);
        iconMap.put("prime", R.drawable.primevideo);
        iconMap.put("amazon", R.drawable.primevideo);
        iconMap.put("disney", R.drawable.disneyplus);
        iconMap.put("hulu", R.drawable.hulu);
        iconMap.put("hbo", R.drawable.hbomax);
        iconMap.put("max", R.drawable.hbomax);
        iconMap.put("plex", R.drawable.plex);
        iconMap.put("kodi", R.drawable.kodi);
        iconMap.put("apple", R.drawable.appletv);
        iconMap.put("tv+", R.drawable.appletv);

        iconMap.put(TvDevice.Type.ANDROID_TV.name().toLowerCase(), R.drawable.androidtv);
        iconMap.put(TvDevice.Type.SAMSUNG.name().toLowerCase(), R.drawable.samsung);
    }

    public static int getIconForDevice(TvDevice device) {
        String name = device.getName().toLowerCase();

        Integer iconId = iconMap.get(name);
        if (iconId != null) return iconId;

        // By device type
        switch (device.getType()) {
            case ANDROID_TV: return R.drawable.androidtv;
            case SAMSUNG:    return R.drawable.samsung;
            default:         return R.drawable.default_icon;
        }
    }
}
