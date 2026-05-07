package com.mctrash692.freemote.remote;

public interface RemoteController {
    
    /**
     * Listener interface for remote control events.
     */
    interface Listener {
        /** Called when the connection to the TV is established. */
        void onConnected();
        
        /** Called when the connection is lost. */
        void onDisconnected(String reason);
        
        /** Called when an error occurs. */
        void onError(String message);
        
        /** Called when a pairing token is received (Samsung only). */
        void onTokenReceived(String token);
        
        /**
         * Called when device information is fetched (Samsung only).
         * Default empty implementation to avoid forcing all listeners to implement it.
         */
        default void onDeviceInfo(String modelName, String wifiMac) {}
    }
    
    // Connection management
    void connect();
    void disconnect();
    boolean isConnected();
    
    // Key commands
    void sendKey(int keyCode, boolean longPress);
    void sendKey(String keyCode);
    
    // Keyboard input
    void sendText(String text);
    void sendText(String text, boolean replaceAll);
    
    // Mouse/touchpad
    void sendMouseMove(int dx, int dy);
    void sendMouseClick(String button);
    void sendMouseWheel(int deltaY);
    void sendMouseActivate();
    
    // App launch
    void sendAppLaunch(String appId);
    
    // Media controls (default no-op)
    default void sendVolumeUp() {}
    default void sendVolumeDown() {}
    default void sendMute() {}
    
    // System controls (default no-op)
    default void sendHome() {}
    default void sendBack() {}
    default void sendPower() {}
}
