// ============================================================================
// FILE: BaseRemote.java
// WHAT:  A "starter kit" for all TV remote controls. Most TV brands use the
//        same basic commands (Volume Up/Down, Mute, Home, Back, Power) and
//        the same keyboard layout. This file gives those to all remotes for
//        free so each brand only has to write the special parts.
// ============================================================================
package com.mctrash692.freemote.remote;

import android.util.Log;

import com.mctrash692.freemote.util.KeycodeMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseRemote implements RemoteController {

    // -- Labels & identifiers ------------------------------------------------
    protected final String TAG;          // Name used for log messages (debugging)
    protected final String ip;           // The TV's network address
    protected final Listener listener;   // The "phone line" back to the app

    // -- Connection state ----------------------------------------------------
    protected final AtomicBoolean connected = new AtomicBoolean(false);
    // AtomicBoolean = a true/false flag that multiple parts of the app can
    // safely read/write at the same time without crashing.

    // -- Key mapping dictionary ----------------------------------------------
    protected final Map<Integer, String> keycodeMap =
        new HashMap<>(KeycodeMapper.newKeycodeMap());
    // Translates Android key codes (like KEYCODE_VOLUME_UP = 24) into
    // text commands the TV understands (like "KEY_VOLUMEUP").

    // ==========================================================================
    // CONSTRUCTOR
    // WHAT:  Runs when a brand-specific remote is created. Saves the TV's
    //        address, a logging label, and the callback "phone line."
    // ==========================================================================
    protected BaseRemote(String tag, String ip, Listener listener) {
        this.TAG = tag;
        this.ip = ip;
        this.listener = listener;
    }

    // ==========================================================================
    // METHOD: isConnected
    // WHAT:  Checks if the remote is currently talking to the TV.
    // RETURNS: true = connected, false = not connected
    // ==========================================================================
    @Override
    public boolean isConnected() {
        return connected.get();
    }

    // ==========================================================================
    // METHOD: sendKey (by number)
    // WHAT:  Looks up the Android key code number (e.g. 24 for Volume Up) in
    //        the dictionary, then sends the matching text version to the TV.
    // ==========================================================================
    @Override
    public void sendKey(int keyCode, boolean longPress) {
        String mapped = keycodeMap.get(keyCode);
        if (mapped != null) {
            sendKey(mapped);
        } else {
            Log.w(TAG, "No key mapping for int keycode: " + keyCode);
        }
    }

    // ==========================================================================
    // METHOD: sendText (without replace)
    // WHAT:  Sends text to the TV. By default, adds to what's already there
    //        (replaceAll = false) unless a brand remote overrides this.
    // ==========================================================================
    @Override
    public void sendText(String text) {
        sendText(text, false);
    }

    // ==========================================================================
    // COMMON BUTTON SHORTCUTS
    // WHAT:  These are the standard TV buttons that almost all brands use.
    //        Instead of each brand rewriting them, they get them here for free.
    //        Each one translates a button press into a text command the TV
    //        understands (like "KEY_VOLUMEUP" for Volume Up).
    // ==========================================================================
    @Override public void sendVolumeUp()   { sendKey("KEY_VOLUMEUP"); }
    @Override public void sendVolumeDown() { sendKey("KEY_VOLUMEDOWN"); }
    @Override public void sendMute()       { sendKey("KEY_MUTE"); }
    @Override public void sendHome()       { sendKey("KEY_HOME"); }
    @Override public void sendBack()       { sendKey("KEY_RETURN"); }
    @Override public void sendPower()      { sendKey("KEY_POWER"); }
}
