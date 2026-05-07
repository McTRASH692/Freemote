package com.mctrash692.freemote.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Full device model with all settings for paired devices
 */
public class PairedDevice {
    
    private String deviceId;           // Unique ID (generated)
    private String name;               // User-editable name
    private String ipAddress;
    private int port;
    private TvDevice.Type type;
    private String macAddress;         // For WOL
    private String authToken;          // Samsung pairing token
    private long lastUsed;
    private boolean autoConnect;       // Auto-connect on app start
    private int touchpadSensitivity;   // Per-device sensitivity (0-40, default 15)
    private Map<Integer, String> shortcuts;  // Slot (1-6) -> app ID
    
    // Default constructor
    public PairedDevice() {
        this.deviceId = java.util.UUID.randomUUID().toString();
        this.port = 8002;
        this.type = TvDevice.Type.SAMSUNG;
        this.lastUsed = System.currentTimeMillis();
        this.autoConnect = false;
        this.touchpadSensitivity = 15;
        this.shortcuts = new HashMap<>();
    }
    
    // Constructor from basic TvDevice
    public PairedDevice(TvDevice tvDevice) {
        this();
        this.name = tvDevice.getName();
        this.ipAddress = tvDevice.getIpAddress();
        this.port = tvDevice.getPort();
        this.type = tvDevice.getType();
    }
    
    // Getters
    public String getDeviceId() { return deviceId; }
    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public TvDevice.Type getType() { return type; }
    public String getMacAddress() { return macAddress; }
    public String getAuthToken() { return authToken; }
    public long getLastUsed() { return lastUsed; }
    public boolean isAutoConnect() { return autoConnect; }
    public int getTouchpadSensitivity() { return touchpadSensitivity; }
    public Map<Integer, String> getShortcuts() { return shortcuts; }
    
    // Setters
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public void setName(String name) { this.name = name; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setPort(int port) { this.port = port; }
    public void setType(TvDevice.Type type) { this.type = type; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public void setLastUsed(long lastUsed) { this.lastUsed = lastUsed; }
    public void setAutoConnect(boolean autoConnect) { this.autoConnect = autoConnect; }
    public void setTouchpadSensitivity(int touchpadSensitivity) { 
        this.touchpadSensitivity = Math.max(0, Math.min(40, touchpadSensitivity));
    }
    public void setShortcuts(Map<Integer, String> shortcuts) { this.shortcuts = shortcuts; }
    
    // Helper methods
    public void updateLastUsed() {
        this.lastUsed = System.currentTimeMillis();
    }
    
    public void setShortcut(int slot, String appId) {
        if (slot >= 1 && slot <= 6) {
            shortcuts.put(slot, appId);
        }
    }
    
    public String getShortcut(int slot) {
        return shortcuts.get(slot);
    }
    
    public TvDevice toTvDevice() {
        return new TvDevice(name, ipAddress, port, type);
    }
    
    // JSON serialization
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("deviceId", deviceId);
        obj.put("name", name);
        obj.put("ipAddress", ipAddress);
        obj.put("port", port);
        obj.put("type", type.name());
        obj.put("macAddress", macAddress != null ? macAddress : "");
        obj.put("authToken", authToken != null ? authToken : "");
        obj.put("lastUsed", lastUsed);
        obj.put("autoConnect", autoConnect);
        obj.put("touchpadSensitivity", touchpadSensitivity);
        
        JSONObject shortcutsObj = new JSONObject();
        for (Map.Entry<Integer, String> entry : shortcuts.entrySet()) {
            shortcutsObj.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        obj.put("shortcuts", shortcutsObj);
        
        return obj;
    }
    
    public static PairedDevice fromJson(JSONObject obj) throws JSONException {
        PairedDevice device = new PairedDevice();
        device.setDeviceId(obj.getString("deviceId"));
        device.setName(obj.getString("name"));
        device.setIpAddress(obj.getString("ipAddress"));
        device.setPort(obj.getInt("port"));
        device.setType(TvDevice.Type.valueOf(obj.getString("type")));
        device.setMacAddress(obj.optString("macAddress", null));
        device.setAuthToken(obj.optString("authToken", null));
        device.setLastUsed(obj.getLong("lastUsed"));
        device.setAutoConnect(obj.optBoolean("autoConnect", false));
        device.setTouchpadSensitivity(obj.optInt("touchpadSensitivity", 15));
        
        JSONObject shortcutsObj = obj.optJSONObject("shortcuts");
        if (shortcutsObj != null) {
            Map<Integer, String> shortcuts = new HashMap<>();
            for (int i = 1; i <= 6; i++) {
                String appId = shortcutsObj.optString(String.valueOf(i), null);
                if (appId != null) {
                    shortcuts.put(i, appId);
                }
            }
            device.setShortcuts(shortcuts);
        }
        
        return device;
    }
}
