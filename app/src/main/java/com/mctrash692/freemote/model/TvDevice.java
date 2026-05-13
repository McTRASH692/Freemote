package com.mctrash692.freemote.model;

// ============================================================================
// FILE: TvDevice.java
// WHAT:  A simple description of a TV found on your network. It stores the
//        TV's name, IP address, port number, and what type it is (Samsung,
//        Android TV, LG, Roku, etc.). Think of it as a "post-it note" with
//        the basic details of a TV you just discovered.
// WHY:   When a TV is first found on the network, we need to remember its
//        basic info before deciding whether to pair with it. This file is
//        that basic info holder.
// ============================================================================

import java.util.Objects;

public class TvDevice {

    // ==========================================================================
    // ENUM: Type
    // WHAT:  A list of all the TV brands and types that this app can work
    //        with. Each is a different kind of TV with its own way of
    //        connecting.
    // ==========================================================================

    public enum Type {
        SAMSUNG,        // Samsung Tizen OS TVs
        ANDROID_TV,     // Android TV / Google TV
        LG,             // LG webOS TVs
        ROKU,           // Roku TVs and devices
        PANASONIC,      // Panasonic Viera TVs
        PHILIPS,        // Philips TVs (Android TV based)
        SONY,           // Sony TVs (Android TV or Bravia)
        TCL,            // TCL TVs (often Android TV)
        HISENSE,        // Hisense TVs (Android TV or Vidaa)
        SHARP,          // Sharp TVs
        HAIER,          // Haier TVs
        VIZIO,          // Vizio SmartCast TVs
        APPLE_TV,       // Apple TV device
        FIRE_TV,        // Amazon Fire TV
        XIAOMI,         // Xiaomi Mi TV
        UNKNOWN         // Brand could not be identified
    }

    // ==========================================================================
    // INSTANCE VARIABLES
    // WHAT:  The basic information about a discovered TV.
    // ==========================================================================

    private final String name;
    private final String ipAddress;
    private final int port;
    private final Type type;

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Creates a basic TV device record from its essential info.
    // INPUT: name      = the TV's name
    //        ipAddress = the TV's IP address
    //        port      = the port number to connect on
    //        type      = the TV's brand/type
    // ==========================================================================

    public TvDevice(String name, String ipAddress, int port, Type type) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.type = type;
    }

    // ==========================================================================
    // METHODS: Getters
    // WHAT:  Read each piece of basic TV information.
    // ==========================================================================

    public String getName()      { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort()         { return port; }
    public Type getType()        { return type; }

    // ==========================================================================
    // METHOD: getDisplayName
    // WHAT:  Creates a human-readable label for this TV, including its name,
    //        brand, IP address, and port. Used to show the TV in lists.
    // OUTPUT: a text string like "Living Room TV (SAMSUNG) - 192.168.1.10:8002"
    // ==========================================================================

    public String getDisplayName() {
        return name + " (" + type.name() + ") — " + ipAddress + ":" + port;
    }

    // ==========================================================================
    // METHODS: equals and hashCode
    // WHAT:  Two TVs are considered the same if they have the same IP
    //        address, port, and type. This prevents duplicate entries.
    // ==========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TvDevice)) return false;
        TvDevice d = (TvDevice) o;
        return port == d.port &&
               Objects.equals(ipAddress, d.ipAddress) &&
               type == d.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipAddress, port, type);
    }
}
