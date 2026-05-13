// ============================================================================
// FILE: RemoteController.java
// WHAT:  The "blueprint" (interface) that every TV remote control must follow.
//        Think of it like a universal remote template — it lists all the
//        buttons a remote can have (volume, power, keyboard, mouse, etc.)
//        without saying HOW each button works. Each TV brand fills in its own
//        version (Samsung, LG, Android TV, etc.).
// ============================================================================
package com.mctrash692.freemote.remote;

public interface RemoteController {

    // ==========================================================================
    // INNER INTERFACE: Listener
    // WHAT:  A "telephone line" that the remote uses to call back to the app
    //        and report what happened (connected, disconnected, error, etc.).
    //        The app "listens" for these calls so it can update the screen.
    // ==========================================================================
    interface Listener {
        // -- TV connected successfully ---------------------------------------
        /** Called when the connection to the TV is established. */
        void onConnected();

        // -- TV disconnected -------------------------------------------------
        /** Called when the connection is lost. */
        void onDisconnected(String reason);

        // -- Something went wrong --------------------------------------------
        /** Called when an error occurs. */
        void onError(String message);

        // -- Samsung pairing code received -----------------------------------
        /** Called when a pairing token is received (Samsung only). */
        void onTokenReceived(String token);

        // -- TV model + MAC address received (Samsung only) ------------------
        /**
         * Called when device information is fetched (Samsung only).
         * Default empty implementation to avoid forcing all listeners to implement it.
         */
        default void onDeviceInfo(String modelName, String wifiMac) {}

        /**
         * Called when the TV opens an IME/text input session (text field focused).
         * Default empty implementation to avoid forcing all listeners to implement it.
         */
        default void onImeStarted() {}
    }

    // ==========================================================================
    // SECTION: CONNECTION MANAGEMENT
    // WHAT:  Start talking to the TV, stop talking, or check if we're connected.
    // ==========================================================================
    void connect();
    void disconnect();
    boolean isConnected();

    // ==========================================================================
    // SECTION: KEY COMMANDS
    // WHAT:  Press a button on the remote. You can use either:
    //        - A number code (like 24 for Volume Up)
    //        - A text name (like "KEY_VOLUMEUP")
    //        "longPress" = hold the button down vs. just tap it.
    // ==========================================================================
    void sendKey(int keyCode, boolean longPress);
    void sendKey(String keyCode);

    // ==========================================================================
    // SECTION: KEYBOARD INPUT
    // WHAT:  Send typed text to the TV (for searching, entering passwords, etc.).
    //        sendText      = sends keystrokes one at a time
    //        sendInputString = sends the whole text as one message (Samsung only)
    // ==========================================================================
    void sendText(String text);
    void sendText(String text, boolean replaceAll);
    default void sendInputString(String text) {}

    // ==========================================================================
    // SECTION: MOUSE / TOUCHPAD
    // WHAT:  Control the TV's cursor like a laptop touchpad.
    //        Move it around, click, scroll, or wake it up.
    // ==========================================================================
    void sendMouseMove(int dx, int dy);
    void sendMouseClick(String button);
    void sendMouseWheel(int deltaY);
    void sendMouseActivate();

    // ==========================================================================
    // SECTION: APP LAUNCH
    // WHAT:  Open an app on the TV by its ID (e.g., "netflix" or "com.amazon....").
    // ==========================================================================
    void sendAppLaunch(String appId);

    // ==========================================================================
    // SECTION: VOLUME CONTROLS
    // WHAT:  Turn the volume up, down, or mute. Default = does nothing —
    //        each TV brand overrides these if it supports them.
    // ==========================================================================
    default void sendVolumeUp()   {}
    default void sendVolumeDown() {}
    default void sendMute()       {}

    // ==========================================================================
    // SECTION: SYSTEM CONTROLS
    // WHAT:  Go to home screen, go back, or turn the TV off. Default = nothing.
    // ==========================================================================
    default void sendHome()  {}
    default void sendBack()  {}
    default void sendPower() {}

    // ==========================================================================
    // SECTION: MEDIA TRANSPORT CONTROLS
    // WHAT:  Play/pause, stop, skip to previous/next track, rewind, fast-forward.
    //        Each TV brand supports whichever of these its platform allows.
    //        Default = does nothing.
    // ==========================================================================
    default void sendMediaPlayPause() {}
    default void sendMediaStop()      {}
    default void sendMediaPrev()      {}
    default void sendMediaNext()      {}
    default void sendMediaRW()        {}
    default void sendMediaFF()        {}

    // ==========================================================================
    // SECTION: TV DISPLAY INFO
    // WHAT:  How big is the TV screen? Default is 1920 x 1080 (Full HD).
    //        Some TVs are different (4K = 3840 x 2160).
    // ==========================================================================
    default int getTvWidth()  { return 1920; }
    default int getTvHeight() { return 1080; }
}
