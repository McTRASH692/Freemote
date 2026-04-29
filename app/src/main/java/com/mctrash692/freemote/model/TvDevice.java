package com.mctrash692.freemote.model;

import java.util.Objects;

public class TvDevice {

    public enum Type {
        SAMSUNG,
        ANDROID_TV,
        UNKNOWN
    }

    private final String name;
    private final String ipAddress;
    private final int port;
    private final Type type;

    public TvDevice(String name, String ipAddress, int port, Type type) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.type = type;
    }

    public String getName()      { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort()         { return port; }
    public Type getType()        { return type; }

    public String getDisplayName() {
        return name + " (" + type.name() + ") — " + ipAddress + ":" + port;
    }

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
