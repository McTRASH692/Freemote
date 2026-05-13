package com.mctrash692.freemote.model;

// ============================================================================
// FILE: TvProtocol.java
// WHAT:  Stores all the technical rules for connecting to different TV
//        brands. Each brand (Samsung, LG, Android TV, Sony, etc.) uses
//        different ports and connection methods (WebSocket, raw socket, HTTP,
//        ADB). This file knows which port and method to use for each brand.
// WHY:   Different TV brands speak different "languages" on different
//        "channels" (ports). This file is the translation guide that tells
//        the app how to talk to each brand of TV.
// ============================================================================

import java.util.ArrayList;
import java.util.List;

public class TvProtocol {
    
    // ==========================================================================
    // ENUM: ConnectionType
    // WHAT:  The different ways the app can talk to a TV. Think of these as
    //        different "languages" for communicating.
    // ==========================================================================

    public enum ConnectionType {
        WEBSOCKET,      // Two-way conversation channel (Samsung Tizen, LG webOS)
        TLS_SOCKET,     // Secure, encrypted socket (Android TV)
        PLAIN_SOCKET,   // Simple raw data connection (older Samsung TVs)
        HTTP_REST,      // Web-style requests (like a browser sends to websites)
        ADB             // Android Debug Bridge (for developer access to Android TVs)
    }
    
    // ==========================================================================
    // ENUM: Brand
    // WHAT:  All the TV manufacturers that this app knows how to connect to.
    // ==========================================================================

    public enum Brand {
        SAMSUNG,        // Samsung (Tizen OS)
        LG,             // LG (webOS)
        ANDROID_TV,     // Android TV / Google TV
        SONY,           // Sony (Android TV or Bravia)
        PANASONIC,      // Panasonic (Viera)
        PHILIPS,        // Philips (Android TV based)
        ROKU,           // Roku
        SHARP,          // Sharp
        HAIER,          // Haier
        TCL,            // TCL
        HISENSE,        // Hisense
        UNKNOWN         // Unrecognized brand
    }
    
    // ==========================================================================
    // INSTANCE VARIABLES
    // ==========================================================================

    private final Brand brand;                        // Which TV brand this protocol is for
    private final List<ConnectionMethod> connectionMethods;  // All the ways to connect to this brand
    
    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Creates a protocol definition for a specific TV brand with all
    //        the ways you can connect to it.
    // INPUT: brand   = the TV brand
    //        methods = list of connection methods (port, type, security)
    // ==========================================================================

    public TvProtocol(Brand brand, List<ConnectionMethod> methods) {
        this.brand = brand;
        this.connectionMethods = methods;
    }
    
    public Brand getBrand() { return brand; }
    public List<ConnectionMethod> getConnectionMethods() { return connectionMethods; }
    
    // ==========================================================================
    // INNER CLASS: ConnectionMethod
    // WHAT:  Describes one specific way to connect to a TV: which port to
    //        use, what kind of connection, whether it is secure, and the
    //        URL path if applicable.
    // ==========================================================================

    public static class ConnectionMethod {
        private final int port;                // Network port number (e.g., 8002, 3001, 6466)
        private final ConnectionType type;     // The kind of connection (WebSocket, socket, HTTP, etc.)
        private final boolean secure;          // Whether the connection uses encryption (TLS/SSL)
        private final String path;             // URL path for WebSocket or HTTP connections
        
        // ==========================================================================
        // METHOD: Constructor (simple)
        // WHAT:  Creates a connection method with a port and type. Assumes
        //        non-secure and no special path.
        // INPUT: port = the network port number
        //        type = the connection type (WebSocket, socket, etc.)
        // ==========================================================================

        public ConnectionMethod(int port, ConnectionType type) {
            this(port, type, false, null);
        }
        
        // ==========================================================================
        // METHOD: Constructor (full)
        // WHAT:  Creates a connection method with all details specified.
        // INPUT: port   = the network port number
        //        type   = the connection type
        //        secure = whether to use encryption
        //        path   = the URL path (e.g., "/api/v2/channels/...")
        // ==========================================================================

        public ConnectionMethod(int port, ConnectionType type, boolean secure, String path) {
            this.port = port;
            this.type = type;
            this.secure = secure;
            this.path = path;
        }
        
        public int getPort() { return port; }
        public ConnectionType getType() { return type; }
        public boolean isSecure() { return secure; }
        public String getPath() { return path != null ? path : "/"; }
        
        // ==========================================================================
        // METHOD: getScheme
        // WHAT:  Returns the URL scheme prefix ("ws", "wss", "http", "https")
        //        based on the connection type and security setting.
        // OUTPUT: "ws" for WebSocket, "wss" for secure WebSocket, etc.
        // ==========================================================================

        public String getScheme() {
            if (type == ConnectionType.WEBSOCKET) {
                return secure ? "wss" : "ws";
            } else if (type == ConnectionType.HTTP_REST) {
                return secure ? "https" : "http";
            }
            return null;
        }
        
        // ==========================================================================
        // METHOD: getFullUrl
        // WHAT:  Builds the complete connection URL including scheme, IP
        //        address, port, and path. For example:
        //        "wss://192.168.1.10:8002/api/v2/channels/samsung.remote.control"
        // INPUT: ip = the TV's IP address
        // OUTPUT: the full URL string, or nothing if this type has no URL scheme
        // ==========================================================================

        public String getFullUrl(String ip) {
            String scheme = getScheme();
            if (scheme != null) {
                return scheme + "://" + ip + ":" + port + getPath();
            }
            return null;
        }
    }
    
    // ==========================================================================
    // SECTION: PREDEFINED TV PROTOCOLS
    // WHAT:  Each method below defines the connection rules for a specific
    //        TV brand. The rules tell the app which ports to try and which
    //        type of connection to use.
    // ==========================================================================
    
    // ==========================================================================
    // METHOD: getSamsungProtocol
    // WHAT:  Defines how to connect to Samsung Tizen TVs (2016 and newer).
    //        Uses WebSocket on port 8002 (secure) or 8001 (plain), a raw
    //        socket on port 55000 for older models, and HTTP on port 8001
    //        for launching apps.
    // ==========================================================================

    public static TvProtocol getSamsungProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        // Primary WebSocket Secure
        methods.add(new ConnectionMethod(8002, ConnectionType.WEBSOCKET, true, "/api/v2/channels/samsung.remote.control"));
        // Secondary WebSocket Plain
        methods.add(new ConnectionMethod(8001, ConnectionType.WEBSOCKET, false, "/api/v2/channels/samsung.remote.control"));
        // Legacy Samsung (pre-2016)
        methods.add(new ConnectionMethod(55000, ConnectionType.PLAIN_SOCKET));
        // REST API for app launch
        methods.add(new ConnectionMethod(8001, ConnectionType.HTTP_REST, false, "/api/v2/"));
        return new TvProtocol(Brand.SAMSUNG, methods);
    }
    
    // ==========================================================================
    // METHOD: getLgProtocol
    // WHAT:  Defines how to connect to LG webOS TVs. Uses WebSocket on port
    //        3001 (secure) or 3000 (plain), plus HTTP on port 8080 for REST
    //        API calls.
    // ==========================================================================

    public static TvProtocol getLgProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        // Primary WebSocket Secure (SSAP)
        methods.add(new ConnectionMethod(3001, ConnectionType.WEBSOCKET, true, "/"));
        // Secondary WebSocket Plain
        methods.add(new ConnectionMethod(3000, ConnectionType.WEBSOCKET, false, "/"));
        // REST API
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/"));
        return new TvProtocol(Brand.LG, methods);
    }
    
    // ==========================================================================
    // METHOD: getAndroidTvProtocol
    // WHAT:  Defines how to connect to Android TV / Google TV devices. Uses
    //        a secure TLS socket on port 6466, ADB on port 5555, and HTTP
    //        on port 8009 for casting.
    // ==========================================================================

    public static TvProtocol getAndroidTvProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        // Primary Android TV Remote Protocol (TLS)
        methods.add(new ConnectionMethod(6466, ConnectionType.TLS_SOCKET));
        // ADB debugging (if enabled)
        methods.add(new ConnectionMethod(5555, ConnectionType.ADB));
        // Casting
        methods.add(new ConnectionMethod(8009, ConnectionType.HTTP_REST, false, "/"));
        return new TvProtocol(Brand.ANDROID_TV, methods);
    }
    
    // ==========================================================================
    // METHOD: getSonyProtocol
    // WHAT:  Defines how to connect to Sony TVs. Android TV-based Sonys use
    //        TLS socket on port 6466 and ADB on 5555. Older Bravia models
    //        use HTTP on port 8080.
    // ==========================================================================

    public static TvProtocol getSonyProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        // Android TV Remote (if running Android TV)
        methods.add(new ConnectionMethod(6466, ConnectionType.TLS_SOCKET));
        // ADB
        methods.add(new ConnectionMethod(5555, ConnectionType.ADB));
        // Older Sony Bravia IR over IP
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/sony/"));
        return new TvProtocol(Brand.SONY, methods);
    }
    
    // ==========================================================================
    // METHOD: getPanasonicProtocol
    // WHAT:  Defines how to connect to Panasonic Viera TVs. Uses a raw
    //        socket on port 55000 or WebSocket on port 8001.
    // ==========================================================================

    public static TvProtocol getPanasonicProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        // Panasonic Viera remote control
        methods.add(new ConnectionMethod(55000, ConnectionType.PLAIN_SOCKET));
        methods.add(new ConnectionMethod(8001, ConnectionType.WEBSOCKET, false, "/"));
        return new TvProtocol(Brand.PANASONIC, methods);
    }
    
    // ==========================================================================
    // METHOD: getPhilipsProtocol
    // WHAT:  Defines how to connect to Philips TVs. Uses TLS socket on port
    //        6466 (Android TV remote) and HTTP on ports 8080 and 1925
    //        (Joint Space API for advanced control).
    // ==========================================================================

    public static TvProtocol getPhilipsProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        methods.add(new ConnectionMethod(6466, ConnectionType.TLS_SOCKET));
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/"));
        methods.add(new ConnectionMethod(1925, ConnectionType.HTTP_REST, false, "/")); // Joint Space API
        return new TvProtocol(Brand.PHILIPS, methods);
    }

    // ==========================================================================
    // METHOD: getRokuProtocol
    // WHAT:  Defines how to connect to Roku TVs. Uses HTTP on port 8060
    //        (External Control Protocol API).
    // ==========================================================================

    public static TvProtocol getRokuProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        methods.add(new ConnectionMethod(8060, ConnectionType.HTTP_REST, false, "/"));
        return new TvProtocol(Brand.ROKU, methods);
    }

    // ==========================================================================
    // METHOD: getSharpProtocol
    // WHAT:  Defines how to connect to Sharp TVs. Uses HTTP on port 8080
    //        and a raw socket on port 55000.
    // ==========================================================================

    public static TvProtocol getSharpProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/"));
        methods.add(new ConnectionMethod(55000, ConnectionType.PLAIN_SOCKET));
        return new TvProtocol(Brand.SHARP, methods);
    }
    
    // ==========================================================================
    // METHOD: getHaierProtocol
    // WHAT:  Defines how to connect to Haier TVs. Uses HTTP on port 8080
    //        and a raw socket on port 55000.
    // ==========================================================================

    public static TvProtocol getHaierProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/"));
        methods.add(new ConnectionMethod(55000, ConnectionType.PLAIN_SOCKET));
        return new TvProtocol(Brand.HAIER, methods);
    }
    
    // ==========================================================================
    // METHOD: getTclProtocol
    // WHAT:  Defines how to connect to TCL TVs (often Android TV based).
    //        Uses TLS socket on port 6466, ADB on port 5555, and HTTP on
    //        port 8080.
    // ==========================================================================

    public static TvProtocol getTclProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        methods.add(new ConnectionMethod(6466, ConnectionType.TLS_SOCKET));
        methods.add(new ConnectionMethod(5555, ConnectionType.ADB));
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/"));
        return new TvProtocol(Brand.TCL, methods);
    }
    
    // ==========================================================================
    // METHOD: getHisenseProtocol
    // WHAT:  Defines how to connect to Hisense TVs (Android TV or Vidaa OS).
    //        Uses TLS socket on port 6466, HTTP on port 8080, and a raw
    //        socket on port 55000.
    // ==========================================================================

    public static TvProtocol getHisenseProtocol() {
        List<ConnectionMethod> methods = new ArrayList<>();
        methods.add(new ConnectionMethod(6466, ConnectionType.TLS_SOCKET));
        methods.add(new ConnectionMethod(8080, ConnectionType.HTTP_REST, false, "/"));
        methods.add(new ConnectionMethod(55000, ConnectionType.PLAIN_SOCKET));
        return new TvProtocol(Brand.HISENSE, methods);
    }
    
    // ==========================================================================
    // SECTION: UTILITY METHODS
    // WHAT:  Helper functions that use the protocol definitions above.
    // ==========================================================================

    // ==========================================================================
    // METHOD: getAllProtocols
    // WHAT:  Returns the connection rules for every TV brand this app knows.
    //        Used during network scanning to know which ports to check.
    // OUTPUT: a list of all TV brand protocol definitions
    // ==========================================================================

    public static List<TvProtocol> getAllProtocols() {
        List<TvProtocol> protocols = new ArrayList<>();
        protocols.add(getSamsungProtocol());
        protocols.add(getLgProtocol());
        protocols.add(getAndroidTvProtocol());
        protocols.add(getSonyProtocol());
        protocols.add(getPanasonicProtocol());
        protocols.add(getPhilipsProtocol());
        protocols.add(getRokuProtocol());
        protocols.add(getSharpProtocol());
        protocols.add(getHaierProtocol());
        protocols.add(getTclProtocol());
        protocols.add(getHisenseProtocol());
        return protocols;
    }
    
    // ==========================================================================
    // METHOD: getAllPorts
    // WHAT:  Collects every unique port number used by all TV brands. This
    //        list is used by the network scanner to know which ports to check
    //        when searching for TVs. Returns ports in sorted order.
    // OUTPUT: a sorted list of all port numbers to scan
    // ==========================================================================

    public static List<Integer> getAllPorts() {
        List<Integer> ports = new ArrayList<>();
        for (TvProtocol protocol : getAllProtocols()) {
            for (ConnectionMethod method : protocol.getConnectionMethods()) {
                if (!ports.contains(method.getPort())) {
                    ports.add(method.getPort());
                }
            }
        }
        // Sort ports
        java.util.Collections.sort(ports);
        return ports;
    }
    
    // ==========================================================================
    // METHOD: detectBrandByPorts
    // WHAT:  Tries to figure out what brand a TV is based on which network
    //        ports are open on it. Each brand uses specific ports, so the
    //        open ports act like a fingerprint. Used when other discovery
    //        methods (like NSD) don't work.
    // INPUT: openPorts = a list of port numbers that were open on the TV
    // OUTPUT: the detected brand, or UNKNOWN if no match found
    // ==========================================================================

    public static Brand detectBrandByPorts(List<Integer> openPorts) {
        // Samsung detection
        if (openPorts.contains(8001) || openPorts.contains(8002)) {
            return Brand.SAMSUNG;
        }
        // LG detection
        if (openPorts.contains(3000) || openPorts.contains(3001)) {
            return Brand.LG;
        }
        // Android TV detection
        if (openPorts.contains(6466)) {
            return Brand.ANDROID_TV;
        }
        // ADB detection (could be Android TV or Sony)
        if (openPorts.contains(5555)) {
            return Brand.ANDROID_TV;
        }
        // Roku detection
        if (openPorts.contains(8060)) {
            return Brand.ROKU;
        }
        // Legacy/other brands
        if (openPorts.contains(55000)) {
            return Brand.PANASONIC;  // Could also be Sharp, Haier, etc.
        }
        return Brand.UNKNOWN;
    }
}
