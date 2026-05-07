package com.mctrash692.freemote.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.model.TvDevice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PairedDevicesManager {
    
    private static final String TAG = "PairedDevicesManager";
    private static final String PREFS_NAME = "freemote_paired_devices";
    private static final String KEY_DEVICES = "devices";
    
    private final SharedPreferences prefs;
    
    public PairedDevicesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public List<PairedDevice> getAllDevices() {
        List<PairedDevice> devices = new ArrayList<>();
        String json = prefs.getString(KEY_DEVICES, "[]");
        
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                PairedDevice device = PairedDevice.fromJson(array.getJSONObject(i));
                devices.add(device);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load devices", e);
        }
        
        return devices;
    }
    
    public PairedDevice getDeviceById(String deviceId) {
        if (deviceId == null) return null;
        for (PairedDevice d : getAllDevices()) {
            if (deviceId.equals(d.getDeviceId())) {
                return d;
            }
        }
        return null;
    }
    
    public PairedDevice getDeviceByIp(String ipAddress) {
        if (ipAddress == null) return null;
        for (PairedDevice d : getAllDevices()) {
            if (ipAddress.equals(d.getIpAddress())) {
                return d;
            }
        }
        return null;
    }
    
    public void saveDevice(PairedDevice device) {
        List<PairedDevice> devices = getAllDevices();
        
        // Remove existing with same ID or IP
        devices.removeIf(d -> d.getDeviceId().equals(device.getDeviceId()) ||
                             d.getIpAddress().equals(device.getIpAddress()));
        
        // Add to front (most recent)
        device.updateLastUsed();
        devices.add(0, device);
        
        saveAllDevices(devices);
        Log.d(TAG, "Saved device: " + device.getName() + " (" + device.getIpAddress() + ")");
    }
    
    public void saveDeviceFromTvDevice(TvDevice tvDevice, String macAddress, String authToken) {
        PairedDevice existing = getDeviceByIp(tvDevice.getIpAddress());
        PairedDevice device;
        
        if (existing != null) {
            device = existing;
            device.setName(tvDevice.getName());
            device.setPort(tvDevice.getPort());
            device.setType(tvDevice.getType());
            if (macAddress != null && !macAddress.isEmpty()) device.setMacAddress(macAddress);
            if (authToken != null && !authToken.isEmpty()) device.setAuthToken(authToken);
        } else {
            device = new PairedDevice(tvDevice);
            if (macAddress != null && !macAddress.isEmpty()) device.setMacAddress(macAddress);
            if (authToken != null && !authToken.isEmpty()) device.setAuthToken(authToken);
        }
        
        saveDevice(device);
    }
    
    public void removeDevice(String deviceId) {
        List<PairedDevice> devices = getAllDevices();
        devices.removeIf(d -> d.getDeviceId().equals(deviceId));
        saveAllDevices(devices);
        Log.d(TAG, "Removed device ID: " + deviceId);
    }
    
    public void removeDeviceByIp(String ipAddress) {
        List<PairedDevice> devices = getAllDevices();
        devices.removeIf(d -> d.getIpAddress().equals(ipAddress));
        saveAllDevices(devices);
        Log.d(TAG, "Removed device IP: " + ipAddress);
    }
    
    public void updateLastUsed(String deviceId) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null) {
            device.updateLastUsed();
            saveDevice(device);
        }
    }
    
    public boolean isDeviceSaved(String ipAddress) {
        return getDeviceByIp(ipAddress) != null;
    }
    
    public void clearAll() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all devices");
    }
    
    public void updateMacAddress(String deviceId, String macAddress) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null && macAddress != null && !macAddress.isEmpty()) {
            device.setMacAddress(macAddress);
            saveDevice(device);
            Log.d(TAG, "Updated MAC for " + device.getName() + ": " + macAddress);
        }
    }
    
    public void updateSensitivity(String deviceId, int sensitivity) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null) {
            device.setTouchpadSensitivity(sensitivity);
            saveDevice(device);
            Log.d(TAG, "Updated sensitivity for " + device.getName() + ": " + sensitivity);
        }
    }
    
    public void updateShortcuts(String deviceId, Map<Integer, String> shortcuts) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null) {
            device.setShortcuts(shortcuts);
            saveDevice(device);
            Log.d(TAG, "Updated shortcuts for " + device.getName());
        }
    }
    
    private void saveAllDevices(List<PairedDevice> devices) {
        JSONArray array = new JSONArray();
        for (PairedDevice device : devices) {
            try {
                array.put(device.toJson());
            } catch (Exception e) {
                Log.e(TAG, "Failed to serialize device", e);
            }
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply();
    }
}
