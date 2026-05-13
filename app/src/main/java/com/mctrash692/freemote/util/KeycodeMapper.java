package com.mctrash692.freemote.util;

// ============================================================================
// FILE: KeycodeMapper.java
// WHAT:  Translates Android's internal button-number codes into plain names
//        like "KEY_HOME", "KEY_BACK", "KEY_VOLUMEUP", etc. The TV
//        understands these named commands, so this file builds a map that
//        turns a number into the right command name.
// WHY:   Android phones use numbers to represent button presses, but TVs
//        need text-based command names. This file bridges that gap so the
//        phone and TV can understand each other.
// ============================================================================

import java.util.HashMap;
import java.util.Map;

public final class KeycodeMapper {

    // ==========================================================================
    // CONSTANTS
    // WHAT:  A translation table that converts Android's internal key numbers
    //        into the text-based command names that TVs understand.
    // ==========================================================================

    // This map links every Android key number to its TV command name.
    // For example: pressing the "Home" button sends number 3, which maps to "KEY_HOME".
    private static final Map<Integer, String> BASE_MAP = new HashMap<>();
    static {
        BASE_MAP.put(3, "KEY_HOME");          // The Home button
        BASE_MAP.put(4, "KEY_BACK");          // Go back
        BASE_MAP.put(19, "KEY_UP");           // D-pad up
        BASE_MAP.put(20, "KEY_DOWN");         // D-pad down
        BASE_MAP.put(21, "KEY_LEFT");         // D-pad left
        BASE_MAP.put(22, "KEY_RIGHT");        // D-pad right
        BASE_MAP.put(23, "KEY_ENTER");        // Select / Enter
        BASE_MAP.put(24, "KEY_VOLUMEUP");     // Turn volume up
        BASE_MAP.put(25, "KEY_VOLUMEDOWN");   // Turn volume down
        BASE_MAP.put(26, "KEY_POWER");        // Turn TV on/off
        BASE_MAP.put(82, "KEY_MENU");         // Open the TV menu
        BASE_MAP.put(164, "KEY_MUTE");        // Mute/unmute sound
        BASE_MAP.put(165, "KEY_INFO");        // Show channel info
        BASE_MAP.put(166, "KEY_CHUP");        // Next channel
        BASE_MAP.put(167, "KEY_CHDOWN");      // Previous channel
        BASE_MAP.put(178, "KEY_SOURCE");      // Switch input source (HDMI, etc.)
        BASE_MAP.put(187, "KEY_GUIDE");       // Open the TV guide
        // Number keys 0 through 9 (Android key codes 7 through 16)
        for (int i = 0; i <= 9; i++) BASE_MAP.put(7 + i, "KEY_" + i);
    }

    // ==========================================================================
    // METHOD: newKeycodeMap
    // WHAT:  Creates a fresh copy of the key-to-command translation table.
    //        Returns a copy instead of the original so nobody accidentally
    //        changes the master list.
    // OUTPUT: a new map with all the key number-to-command name translations
    // ==========================================================================

    public static Map<Integer, String> newKeycodeMap() {
        return new HashMap<>(BASE_MAP);
    }

    // Private constructor prevents anyone from creating an instance of this utility class
    private KeycodeMapper() {}
}
