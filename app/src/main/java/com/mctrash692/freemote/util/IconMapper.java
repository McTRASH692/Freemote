package com.mctrash692.freemote.util;

import com.mctrash692.freemote.R;
import com.mctrash692.freemote.model.TvDevice;

public class IconMapper {
    
    public static int getIconForDevice(TvDevice device) {
        String name = device.getName().toLowerCase();
        
        // Map by device name (service name from NSD)
        if (name.contains("netflix")) return R.drawable.netflix;
        if (name.contains("youtube")) return R.drawable.youtube;
        if (name.contains("prime") || name.contains("amazon")) return R.drawable.primevideo;
        if (name.contains("disney")) return R.drawable.disneyplus;
        if (name.contains("hulu")) return R.drawable.hulu;
        if (name.contains("hbo") || name.contains("max")) return R.drawable.hbomax;
        if (name.contains("plex")) return R.drawable.plex;
        if (name.contains("kodi")) return R.drawable.kodi;
        if (name.contains("apple") || name.contains("tv+")) return R.drawable.appletv;
        
        // By device type
        if (device.getType() == TvDevice.Type.ANDROID_TV) return R.drawable.androidtv;
        if (device.getType() == TvDevice.Type.SAMSUNG) return R.drawable.samsung;
        
        return R.drawable.default_icon;
    }
}
