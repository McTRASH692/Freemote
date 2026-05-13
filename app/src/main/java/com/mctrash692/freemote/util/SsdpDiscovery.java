package com.mctrash692.freemote.util;

// ============================================================================
// FILE: SsdpDiscovery.java
// WHAT:  Finds Samsung TVs on your network using a method called SSDP.
//        It sends out a special network "shout" asking "Are there any Samsung
//        TVs here?" and listens for replies. When a TV responds, it grabs the
//        TV's name, IP address, and port so you can connect.
// WHY:   Samsung TVs advertise themselves on the network using SSDP. This is
//        an alternative way to discover them when the normal network scan or
//        NSD discovery doesn't work.
// ============================================================================

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mctrash692.freemote.model.TvDevice;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SsdpDiscovery {
    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    private static final String TAG = "SsdpDiscovery";          // Used for logging/diagnostics
    private static final String SSDP_ADDR = "239.255.255.250";  // Special multicast address for SSDP discovery
    private static final int SSDP_PORT = 1900;                  // Standard port for SSDP messages

    // The service types we ask about. Each one is like a "classified ad" category
    // that different TV features advertise under.
    private static final String[] SEARCH_TARGETS = {
        "urn:samsung.com:device:RemoteControlReceiver:1",  // Samsung remote control service
        "urn:dial-multiscreen-org:service:dial:1",         // DIAL (second screen / casting) service
        "urn:schemas-upnp-org:device:MediaRenderer:1"      // General media player service
    };

    // ==========================================================================
    // INSTANCE VARIABLES
    // WHAT:  Data this discovery scanner keeps track of while running.
    // ==========================================================================

    private volatile DatagramSocket socket;       // The network socket used to send/receive SSDP messages
    private Thread discoveryThread;                // The background thread doing the listening
    private volatile boolean isRunning = false;    // Whether discovery is currently active
    private final Consumer<TvDevice> onDeviceFound;  // Callback for when a TV is found
    private final Handler mainHandler;              // Sends results to the main app thread
    private final Set<String> processedIps = new HashSet<>();  // IPs we already reported (avoid duplicates)

    // ==========================================================================
    // METHOD: Constructor
    // WHAT:  Prepares the SSDP discovery system and remembers where to send
    //        results when a TV is found.
    // INPUT: onDeviceFound = a function to call whenever a TV is discovered
    // ==========================================================================

    public SsdpDiscovery(Consumer<TvDevice> onDeviceFound) {
        this.onDeviceFound = onDeviceFound;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    // ==========================================================================
    // METHOD: start
    // WHAT:  Begins searching for Samsung TVs on your network. It sends out
    //        SSDP discovery messages and listens on a background thread for
    //        any TVs that respond. Runs until it finds a TV or times out.
    // ==========================================================================

    public void start() {
        if (isRunning) return;
        isRunning = true;

        discoveryThread = new Thread(() -> {
            try {
                InetAddress multicastAddr = InetAddress.getByName(SSDP_ADDR);
                socket = new DatagramSocket();
                socket.setSoTimeout(8000);

                for (String st : SEARCH_TARGETS) {
                    String req =
                        "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: " + st + "\r\n\r\n";
                    byte[] data = req.getBytes();
                    socket.send(new DatagramPacket(data, data.length, multicastAddr, SSDP_PORT));
                    Log.d(TAG, "SSDP M-SEARCH sent: " + st);
                }

                while (isRunning) {
                    try {
                        byte[] buf = new byte[4096];
                        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                        socket.receive(pkt);

                        String response = new String(pkt.getData(), 0, pkt.getLength());
                        String ip = pkt.getAddress().getHostAddress();

                        if (isSamsungTv(response)) {
                            synchronized (processedIps) {
                                if (processedIps.contains(ip)) continue;
                                processedIps.add(ip);
                            }

                            String tvName = getTvNameFromApi(ip);
                            if (tvName == null || tvName.isEmpty()) {
                                tvName = extractTvNameFromSsdp(response, ip);
                            }

                            int workingPort = findWorkingPort(ip);

                            if (workingPort > 0) {
                                Log.d(TAG, "Found Samsung TV: " + tvName + " @ " + ip + ":" + workingPort);
                                final String finalTvName = tvName;
                                final int finalPort = workingPort;
                                mainHandler.post(() -> onDeviceFound.accept(
                                    new TvDevice(finalTvName, ip, finalPort, TvDevice.Type.SAMSUNG)
                                ));
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        Log.d(TAG, "SSDP discovery finished");
                        break;
                    } catch (IOException e) {
                        if (isRunning) Log.e(TAG, "SSDP receive error: " + e.getMessage());
                        break;
                    }
                }
            } catch (IOException e) {
                if (isRunning) Log.e(TAG, "SSDP error: " + e.getMessage());
            }
        });
        discoveryThread.start();
    }

    // ==========================================================================
    // METHOD: getTvNameFromApi (private)
    // WHAT:  Asks a TV for its name by calling its built-in web API. If the
    //        TV responds, this gives us the real name the owner gave it
    //        (like "Living Room TV") instead of a generic label.
    // INPUT: ip = the TV's IP address
    // OUTPUT: the TV's name, or nothing if the API doesn't respond
    // ==========================================================================

    private String getTvNameFromApi(String ip) {
        int[] ports = {8001, 8002};

        for (int port : ports) {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("http://" + ip + ":" + port + "/api/v2/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject json = new JSONObject(response.toString());
                    String name = json.optString("name", null);
                    if (name == null || name.isEmpty()) {
                        JSONObject device = json.optJSONObject("device");
                        if (device != null) {
                            name = device.optString("name", null);
                            if ((name == null || name.isEmpty()) && device.has("modelName")) {
                                name = "Samsung " + device.getString("modelName");
                            }
                        }
                    }

                    if (name != null && !name.isEmpty() && !name.equals("null")) {
                        Log.d(TAG, "Got TV name from API: " + name);
                        return name;
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "API query failed for port " + port + ": " + e.getMessage());
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (IOException ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }
        return null;
    }

    // ==========================================================================
    // METHOD: extractTvNameFromSsdp (private)
    // WHAT:  Pulls the TV's name out of the SSDP network response message.
    //        The TV sends back text containing things like "FRIENDLY.NAME"
    //        or "DEVICE.NAME" and this function finds and returns that text.
    // INPUT: response = the raw SSDP reply from the TV
    //        ip       = the TV's IP address (used as fallback name)
    // OUTPUT: the TV's name, or a generic name with the IP if nothing found
    // ==========================================================================

    private String extractTvNameFromSsdp(String response, String ip) {
        Pattern friendlyPattern = Pattern.compile("FRIENDLY\\.NAME:\\s*(.+?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher friendlyMatcher = friendlyPattern.matcher(response);
        if (friendlyMatcher.find()) {
            String name = friendlyMatcher.group(1).trim();
            if (!name.isEmpty() && !name.equalsIgnoreCase("Samsung TV") && !name.contains("UPnP")) {
                name = name.replaceAll("[\\r\\n]", "").trim();
                if (name.length() > 0 && name.length() < 100) {
                    Log.d(TAG, "Found FRIENDLY.NAME: " + name);
                    return name;
                }
            }
        }

        Pattern devicePattern = Pattern.compile("DEVICE\\.NAME:\\s*(.+?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher deviceMatcher = devicePattern.matcher(response);
        if (deviceMatcher.find()) {
            String name = deviceMatcher.group(1).trim();
            if (!name.isEmpty() && !name.contains("UPnP") && !name.contains("linux")) {
                name = name.replaceAll("[\\r\\n]", "").trim();
                Log.d(TAG, "Found DEVICE.NAME: " + name);
                return name;
            }
        }

        Pattern modelPattern = Pattern.compile("MODEL\\.NAME:\\s*(.+?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher modelMatcher = modelPattern.matcher(response);
        if (modelMatcher.find()) {
            String model = modelMatcher.group(1).trim();
            if (!model.isEmpty() && !model.contains("UPnP")) {
                model = model.replaceAll("[\\r\\n]", "").trim();
                Log.d(TAG, "Found MODEL.NAME: " + model);
                return "Samsung " + model;
            }
        }

        Pattern modelNumPattern = Pattern.compile("MODEL\\.NUMBER:\\s*(.+?)(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher modelNumMatcher = modelNumPattern.matcher(response);
        if (modelNumMatcher.find()) {
            String model = modelNumMatcher.group(1).trim();
            if (!model.isEmpty()) {
                model = model.replaceAll("[\\r\\n]", "").trim();
                Log.d(TAG, "Found MODEL.NUMBER: " + model);
                return "Samsung " + model;
            }
        }

        Log.d(TAG, "No good name found in SSDP, using IP fallback");
        return "Samsung TV (" + ip + ")";
    }

    // ==========================================================================
    // METHOD: findWorkingPort (private)
    // WHAT:  Tries different ports on the TV to find one that is open and
    //        accepts connections. Samsung TVs use ports 8001 and 8002.
    // INPUT: ip = the TV's IP address
    // OUTPUT: the port number that works, or -1 if none respond
    // ==========================================================================

    private int findWorkingPort(String ip) {
        int[] ports = {8001, 8002};
        for (int port : ports) {
            if (checkPort(ip, port)) return port;
        }
        return -1;
    }

    // ==========================================================================
    // METHOD: checkPort (private)
    // WHAT:  Tries to open a network connection to a TV at a given IP and
    //        port. If it connects, the port is open and usable.
    // INPUT: ip   = the TV's IP address
    //        port = the port to test
    // OUTPUT: true if the port is open, false if not
    // ==========================================================================

    private boolean checkPort(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(ip, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==========================================================================
    // METHOD: isSamsungTv (private)
    // WHAT:  Examines a network response to decide if it came from a Samsung
    //        TV. It looks for keywords like "samsung", "tizen" (Samsung's
    //        operating system), or "remotecontrolreceiver".
    // INPUT: response = the raw network reply text
    // OUTPUT: true if this looks like a Samsung TV reply
    // ==========================================================================

    private boolean isSamsungTv(String response) {
        String lower = response.toLowerCase();
        return lower.contains("samsung") ||
               lower.contains("tizen") ||
               lower.contains("remotecontrolreceiver") ||
               (lower.contains("upnp") && lower.contains("tv"));
    }

    // ==========================================================================
    // METHOD: stop
    // WHAT:  Stops the SSDP discovery process. Closes the network socket and
    //        stops the background listener thread.
    // ==========================================================================

    public void stop() {
        isRunning = false;
        DatagramSocket s = socket;
        if (s != null && !s.isClosed()) {
            s.close();
        }
        if (discoveryThread != null) {
            discoveryThread.interrupt();
        }
        synchronized (processedIps) {
            processedIps.clear();
        }
    }
}
