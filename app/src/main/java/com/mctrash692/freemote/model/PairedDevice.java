package com.mctrash692.freemote.model;

// ============================================================================
// FILE: PairedDevice.java
// WHAT:  Holds all the information about a TV you have paired with. This
//        includes its name, IP address, port number, MAC address (for
//        Wake-on-LAN), security token, touchpad sensitivity, and app
//        shortcuts. It can save itself to a text format (JSON) and load
//        itself back.
// WHY:   The app needs a place to keep all the details about each paired TV.
//        This file is like a "digital business card" for every TV you own.
// ============================================================================

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class PairedDevice {
    
    // ==========================================================================
    // INSTANCE VARIABLES
    // WHAT:  Every piece of information we store about a single paired TV.
    // ==========================================================================

    private String deviceId;           // A unique ID automatically generated for this TV
    private String name;               // The name you gave this TV (you can edit this)
    private String ipAddress;          // The TV's network address
    private int port;                  // The port number to use when connecting
    private TvDevice.Type type;        // What brand/type of TV (Samsung, LG, etc.)
    private String macAddress;         // The TV's hardware MAC address (for Wake-on-LAN)
    private String authToken;          // Security token needed for Samsung TV pairing
    private long lastUsed;             // When you last used this remote (for sorting)
    private boolean autoConnect;       // Whether to auto-connect to this TV when the app starts
    private int touchpadSensitivity;   // Touchpad sensitivity for this TV (0 to 40, default 15)
    private Map<Integer, String> shortcuts;  // App shortcuts: slot number (1-6) -> app ID
    
    // ==========================================================================
    // SECTION: CONSTRUCTORS
    // WHAT:  Different ways to create a PairedDevice entry.
    // ==========================================================================

    // ==========================================================================
    // METHOD: Default Constructor
    // WHAT:  Creates a blank new paired device with a random unique ID and
    //        default settings. You fill in the details later.
    // ==========================================================================

    public PairedDevice() {
        this.deviceId = java.util.UUID.randomUUID().toString();
        this.port = 8002;
        this.type = TvDevice.Type.SAMSUNG;
        this.lastUsed = System.currentTimeMillis();
        this.autoConnect = false;
        this.touchpadSensitivity = 15;
        this.shortcuts = new HashMap<>();
    }
    
    // ==========================================================================
    // METHOD: Constructor from basic TvDevice
    // WHAT:  Creates a paired device from a basic TV device that was just
    //        discovered on the network. Copies the name, IP, port, and type.
    // INPUT: tvDevice = the basic TV info from network discovery
    // ==========================================================================

    public PairedDevice(TvDevice tvDevice) {
        this();
        this.name = tvDevice.getName();
        this.ipAddress = tvDevice.getIpAddress();
        this.port = tvDevice.getPort();
        this.type = tvDevice.getType();
    }
    
    // ==========================================================================
    // SECTION: GETTERS & SETTERS
    // WHAT:  Methods that read and write each piece of device information.
    //        Getters retrieve values, setters update them.
    // ==========================================================================

    // ==========================================================================
    // METHODS: Getters
    // WHAT:  Each of these returns one piece of information about the TV.
    // ==========================================================================

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
    
    // ==========================================================================
    // METHODS: Setters
    // WHAT:  Each of these updates one piece of information about the TV.
    //        The touchpad sensitivity is limited to 0-40 range.
    // ==========================================================================

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
    
    // ==========================================================================
    // SECTION: HELPER METHODS
    // WHAT:  Extra functions that make the device easier to work with.
    // ==========================================================================

    // ==========================================================================
    // METHOD: updateLastUsed
    // WHAT:  Records the current time as the last time you used this TV.
    //        This helps sort your most-used TVs to the top of the list.
    // ==========================================================================

    public void updateLastUsed() {
        this.lastUsed = System.currentTimeMillis();
    }
    
    // ==========================================================================
    // METHOD: setShortcut
    // WHAT:  Assigns an app to one of the shortcut slots (1 to 6). These
    //        shortcuts appear as buttons on the remote screen.
    // INPUT: slot  = which button slot (1 through 6)
    //        appId = the app to launch (e.g., "netflix", "youtube")
    // ==========================================================================

    public void setShortcut(int slot, String appId) {
        if (slot >= 1 && slot <= 6) {
            shortcuts.put(slot, appId);
        }
    }
    
    // ==========================================================================
    // METHOD: getShortcut
    // WHAT:  Returns the app assigned to a shortcut slot.
    // INPUT: slot = which button slot (1 through 6)
    // OUTPUT: the app ID assigned to that slot, or nothing if empty
    // ==========================================================================

    public String getShortcut(int slot) {
        return shortcuts.get(slot);
    }
    
    // ==========================================================================
    // METHOD: toTvDevice
    // WHAT:  Converts this full paired device back into a basic TV device
    //        with just name, IP, port, and type. Useful when you just need
    //        the basic info to connect.
    // OUTPUT: a basic TvDevice representation of this paired TV
    // ==========================================================================

    public TvDevice toTvDevice() {
        return new TvDevice(name, ipAddress, port, type);
    }
    
    // ==========================================================================
    // SECTION: SAVING & LOADING (JSON)
    // WHAT:  Converts the device to/from JSON text format for storage.
    // ==========================================================================

    // ==========================================================================
    // METHOD: toJson
    // WHAT:  Turns this device's information into JSON format (a text-based
    //        data format) so it can be saved to your phone's storage.
    // OUTPUT: a JSON object containing all device data
    // ==========================================================================

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("deviceId", deviceId);
        obj.put("name", name);
        obj.put("ipAddress", ipAddress);
        obj.put("port", port);
        obj.put("type", type.name());
        if (macAddress != null && !macAddress.isEmpty()) obj.put("macAddress", macAddress);
        if (authToken != null && !authToken.isEmpty()) obj.put("authToken", authToken);
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
    
    // ==========================================================================
    // METHOD: fromJson
    // WHAT:  Reads JSON text and creates a PairedDevice from it. This is
    //        used when loading your saved TVs from the phone's storage.
    // INPUT: obj = a JSON object containing saved device data
    // OUTPUT: a PairedDevice with all the data from the JSON
    // ==========================================================================

    public static PairedDevice fromJson(JSONObject obj) throws JSONException {
        PairedDevice device = new PairedDevice();
        device.setDeviceId(obj.getString("deviceId"));
        device.setName(obj.getString("name"));
        device.setIpAddress(obj.getString("ipAddress"));
        device.setPort(obj.getInt("port"));
        device.setType(TvDevice.Type.valueOf(obj.getString("type")));
        device.setMacAddress(obj.has("macAddress") ? obj.getString("macAddress") : null);
        device.setAuthToken(obj.has("authToken") ? obj.getString("authToken") : null);
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
