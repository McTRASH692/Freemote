package com.mctrash692.freemote.util;

// ============================================================================
// FILE: PairedDevicesManager.java
// WHAT:  Keeps track of all the TVs you have paired your phone with.
//        Saves each TV's name, IP address, MAC address, and settings so you
//        can connect again without re-entering everything. Stores the list
//        on your phone so it remembers your TVs even after closing the app.
// WHY:   Without this file, the app would forget every TV each time you
//        closed it. This is the "address book" for your remote.
// ============================================================================

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.mctrash692.freemote.model.PairedDevice;
import com.mctrash692.freemote.model.TvDevice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PairedDevicesManager {
    
    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    private static final String TAG = "PairedDevicesManager";    // Used for logging/diagnostics
    private static final String PREFS_NAME = "freemote_paired_devices";  // The file name where device data is stored on your phone
    private static final String KEY_DEVICES = "devices";         // The key used to look up the saved device list in storage
    
    private final SharedPreferences prefs;  // The phone's simple storage system (SharedPreferences)
    
    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Opens the app's private storage so we can read and write the
    //        list of paired devices.
    // INPUT: context = the app's main activity (needed to access storage)
    // ==========================================================================

    public PairedDevicesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    // ==========================================================================
    // SECTION: READING DEVICES
    // WHAT:  Functions that look up saved TVs.
    // ==========================================================================

    // ==========================================================================
    // METHOD: getAllDevices
    // WHAT:  Returns every TV you have ever paired with, as a list. Reads
    //        the saved data from your phone's storage.
    // OUTPUT: a list of all paired TV devices (could be empty if none saved)
    // ==========================================================================

    public synchronized List<PairedDevice> getAllDevices() {
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
    
    // ==========================================================================
    // METHOD: getDeviceById
    // WHAT:  Looks up a saved TV using its unique ID number.
    // INPUT: deviceId = the TV's unique ID
    // OUTPUT: the TV's information, or nothing if not found
    // ==========================================================================

    public synchronized PairedDevice getDeviceById(String deviceId) {
        if (deviceId == null) return null;
        for (PairedDevice d : getAllDevices()) {
            if (deviceId.equals(d.getDeviceId())) {
                return d;
            }
        }
        return null;
    }
    
    // ==========================================================================
    // METHOD: getDeviceByIp
    // WHAT:  Looks up a saved TV using its network IP address.
    // INPUT: ipAddress = the TV's IP address (like "192.168.1.10")
    // OUTPUT: the TV's information, or nothing if not found
    // ==========================================================================

    public synchronized PairedDevice getDeviceByIp(String ipAddress) {
        if (ipAddress == null) return null;
        for (PairedDevice d : getAllDevices()) {
            if (ipAddress.equals(d.getIpAddress())) {
                return d;
            }
        }
        return null;
    }
    
    // ==========================================================================
    // SECTION: SAVING & UPDATING DEVICES
    // WHAT:  Functions that save a new TV or update an existing one.
    // ==========================================================================

    // ==========================================================================
    // METHOD: saveDevice
    // WHAT:  Saves a TV's information so you can find it again later. If a
    //        TV with the same ID or IP already exists, the old one is replaced.
    //        The TV you save goes to the top of the list (most recent).
    // INPUT: device = the TV's details (name, IP, settings, etc.)
    // ==========================================================================

    public synchronized void saveDevice(PairedDevice device) {
        List<PairedDevice> devices = getAllDevices();
        
        // Preserve MAC/token from existing device with same IP (may have been
        // discovered after initial pairing) so a partial update doesn't wipe them.
        for (PairedDevice d : devices) {
            if (d.getIpAddress() != null && d.getIpAddress().equals(device.getIpAddress())
                    && !d.getDeviceId().equals(device.getDeviceId())) {
                if (device.getMacAddress() == null || device.getMacAddress().isEmpty()) {
                    device.setMacAddress(d.getMacAddress());
                }
                if (device.getAuthToken() == null || device.getAuthToken().isEmpty()) {
                    device.setAuthToken(d.getAuthToken());
                }
                break;
            }
        }
        
        // Remove existing with same ID or IP
        devices.removeIf(d -> {
            if (d.getDeviceId() == null || d.getIpAddress() == null) return false;
            return d.getDeviceId().equals(device.getDeviceId()) ||
                   d.getIpAddress().equals(device.getIpAddress());
        });
        
        // Add to front (most recent)
        device.updateLastUsed();
        devices.add(0, device);
        
        saveAllDevices(devices);
        Log.d(TAG, "Saved device: " + device.getName() + " (" + device.getIpAddress() + ")");
    }
    
    // ==========================================================================
    // METHOD: saveDeviceFromTvDevice
    // WHAT:  Takes a TV that was just discovered on the network and saves it
    //        as a paired device. If you already saved this TV (same IP), it
    //        updates the existing entry instead of creating a duplicate.
    // INPUT: tvDevice = basic TV info from discovery
    //        macAddress = the TV's hardware address (for Wake-on-LAN)
    //        authToken = security token for pairing
    // ==========================================================================

    public synchronized void saveDeviceFromTvDevice(TvDevice tvDevice, String macAddress, String authToken) {
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
    
    // ==========================================================================
    // SECTION: REMOVING DEVICES
    // WHAT:  Functions that delete a TV from your saved list.
    // ==========================================================================

    // ==========================================================================
    // METHOD: removeDevice
    // WHAT:  Removes a TV from your saved list using its unique ID.
    // INPUT: deviceId = the unique ID of the TV to remove
    // ==========================================================================

    public synchronized void removeDevice(String deviceId) {
        List<PairedDevice> devices = getAllDevices();
        devices.removeIf(d -> d.getDeviceId().equals(deviceId));
        saveAllDevices(devices);
        Log.d(TAG, "Removed device ID: " + deviceId);
    }
    
    // ==========================================================================
    // METHOD: removeDeviceByIp
    // WHAT:  Removes a TV from your saved list using its IP address.
    // INPUT: ipAddress = the IP address of the TV to remove
    // ==========================================================================

    public synchronized void removeDeviceByIp(String ipAddress) {
        List<PairedDevice> devices = getAllDevices();
        devices.removeIf(d -> d.getIpAddress().equals(ipAddress));
        saveAllDevices(devices);
        Log.d(TAG, "Removed device IP: " + ipAddress);
    }
    
    // ==========================================================================
    // METHOD: updateLastUsed
    // WHAT:  Records the current date/time as the last time you used this TV.
    //        This helps sort your most-used TVs to the top.
    // INPUT: deviceId = the unique ID of the TV you just used
    // ==========================================================================

    public synchronized void updateLastUsed(String deviceId) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null) {
            device.updateLastUsed();
            saveDevice(device);
        }
    }
    
    // ==========================================================================
    // METHOD: isDeviceSaved
    // WHAT:  Checks if a TV at a given IP address is already in your saved
    //        list. Helps avoid saving the same TV twice.
    // INPUT: ipAddress = the IP address to check
    // OUTPUT: true if already saved, false if not
    // ==========================================================================

    public synchronized boolean isDeviceSaved(String ipAddress) {
        return getDeviceByIp(ipAddress) != null;
    }
    
    // ==========================================================================
    // METHOD: clearAll
    // WHAT:  Deletes ALL saved TVs from the app's storage. Use with caution!
    //        This is like wiping your entire address book.
    // ==========================================================================

    public synchronized void clearAll() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Cleared all devices");
    }
    
    // ==========================================================================
    // SECTION: UPDATING DEVICE SETTINGS
    // WHAT:  Functions that change specific settings on an already-saved TV.
    // ==========================================================================

    // ==========================================================================
    // METHOD: updateMacAddress
    // WHAT:  Saves or updates the hardware (MAC) address of a TV. This is
    //        needed for the Wake-on-LAN feature to turn the TV on remotely.
    // INPUT: deviceId  = which TV to update
    //        macAddress = the new MAC address (like "AA:BB:CC:DD:EE:FF")
    // ==========================================================================

    public synchronized void updateMacAddress(String deviceId, String macAddress) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null && macAddress != null && !macAddress.isEmpty()) {
            device.setMacAddress(macAddress);
            saveDevice(device);
            Log.d(TAG, "Updated MAC for " + device.getName() + ": " + macAddress);
        }
    }
    
    // ==========================================================================
    // METHOD: updateSensitivity
    // WHAT:  Changes how sensitive the touchpad is when controlling this TV.
    //        Higher numbers mean the cursor moves more when you swipe.
    // INPUT: deviceId    = which TV to update
    //        sensitivity = how sensitive the touchpad should be (0 to 40)
    // ==========================================================================

    public synchronized void updateSensitivity(String deviceId, int sensitivity) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null) {
            device.setTouchpadSensitivity(sensitivity);
            saveDevice(device);
            Log.d(TAG, "Updated sensitivity for " + device.getName() + ": " + sensitivity);
        }
    }
    
    // ==========================================================================
    // METHOD: updateShortcuts
    // WHAT:  Saves the app shortcut buttons for a TV. Shortcuts let you
    //        launch apps (like Netflix, YouTube) directly from the remote.
    // INPUT: deviceId  = which TV to update
    //        shortcuts = a map of slot numbers (1-6) to app IDs
    // ==========================================================================

    public synchronized void updateShortcuts(String deviceId, Map<Integer, String> shortcuts) {
        PairedDevice device = getDeviceById(deviceId);
        if (device != null) {
            device.setShortcuts(shortcuts);
            saveDevice(device);
            Log.d(TAG, "Updated shortcuts for " + device.getName());
        }
    }
    
    // ==========================================================================
    // METHOD: saveAllDevices (private helper)
    // WHAT:  Writes the entire list of paired TVs to the phone's storage in
    //        a text format called JSON. This is called whenever a device is
    //        saved, updated, or removed to make sure storage matches the list.
    // INPUT: devices = the full list of paired TVs to save
    // ==========================================================================

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
