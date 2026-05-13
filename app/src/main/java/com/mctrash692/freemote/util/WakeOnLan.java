package com.mctrash692.freemote.util;

// ============================================================================
// FILE: WakeOnLan.java
// WHAT:  Turns on a TV remotely using a feature called Wake-on-LAN. It sends
//        a special "magic packet" over your network to the TV's MAC address
//        (a unique hardware ID), which tells the TV to power on.
// WHY:   If your TV is off and you want to use the remote app, you need to
//        wake it up first. This file does that without you having to get up.
// ============================================================================

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class WakeOnLan {

    // ==========================================================================
    // CONSTANTS
    // ==========================================================================

    private static final String TAG = "WakeOnLan";  // Used for logging/diagnostics
    private static final int WOL_PORT = 9;           // Standard network port for Wake-on-LAN magic packets

    // ==========================================================================
    // INTERFACE: ErrorCallback
    // WHAT:  A way for the Wake-on-LAN system to tell you if something went
    //        wrong (e.g., bad MAC address or network failure).
    // ==========================================================================

    public interface ErrorCallback {
        void onError(String message);
    }

    // ==========================================================================
    // METHOD: send
    // WHAT:  Sends a "magic packet" to wake up a TV over the network. Needs
    //        the TV's IP address and its MAC address. The magic packet is a
    //        special message that tells the TV to turn on.
    // INPUT: tvIp    = the TV's IP address
    //        mac     = the TV's MAC address (like "AA:BB:CC:DD:EE:FF")
    //        onError = a function to call if something goes wrong
    // ==========================================================================

    public static void send(final String tvIp, final String mac, final ErrorCallback onError) {
        if (mac == null || mac.trim().isEmpty()) {
            if (onError != null) {
                onError.onError("No MAC address saved. Add it in Settings under Wake-on-LAN.");
            }
            return;
        }

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                byte[] macBytes  = parseMac(mac.trim());
                byte[] packet    = buildPacket(macBytes);

                for (String addr : getBroadcastAddresses(tvIp)) {
                    InetAddress broadcastAddr = InetAddress.getByName(addr);
                    socket.send(new DatagramPacket(packet, packet.length, broadcastAddr, WOL_PORT));
                }

                Log.d(TAG, "WoL sent to " + String.join(", ", getBroadcastAddresses(tvIp)) + " for " + mac.trim());
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Bad MAC: " + e.getMessage());
                if (onError != null) onError.onError("Invalid MAC address: " + mac);
            } catch (Exception e) {
                Log.e(TAG, "WoL failed: " + e.getMessage(), e);
                if (onError != null) onError.onError("Wake-on-LAN failed: " + e.getMessage());
            }
        }).start();
    }

    // ==========================================================================
    // SECTION: INTERNAL HELPERS
    // WHAT:  Functions that build the magic wake-up packet and handle MAC
    //        address formatting.
    // ==========================================================================

    // ==========================================================================
    // METHOD: buildPacket (private)
    // WHAT:  Builds the "magic packet" that wakes up the TV. The packet
    //        starts with 6 bytes of 0xFF followed by the MAC address
    //        repeated 16 times, making it 102 bytes total.
    // INPUT: mac = the TV's MAC address as 6 bytes
    // OUTPUT: the complete magic packet as a byte array
    // ==========================================================================

    private static byte[] buildPacket(byte[] mac) {
        byte[] pkt = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) pkt[i] = (byte) 0xFF;
        for (int i = 1; i <= 16; i++) System.arraycopy(mac, 0, pkt, i * 6, 6);
        return pkt;
    }

    // ==========================================================================
    // METHOD: parseMac (private)
    // WHAT:  Converts a MAC address from text format into 6 raw bytes. It
    //        handles both "AA:BB:CC:DD:EE:FF" and "AA-BB-CC-DD-EE-FF".
    // INPUT: mac = the MAC address as text
    // OUTPUT: the MAC address as 6 bytes
    // ==========================================================================

    private static byte[] parseMac(String mac) {
        String[] parts = mac.split("[:\\-]");
        if (parts.length != 6)
            throw new IllegalArgumentException("Expected 6 hex octets, got: " + mac);
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            int val = Integer.parseInt(parts[i].trim(), 16);
            if (val < 0 || val > 255)
                throw new IllegalArgumentException("Octet out of range: " + parts[i]);
            bytes[i] = (byte) val;
        }
        return bytes;
    }

    // ==========================================================================
    // METHOD: getBroadcastAddresses (private)
    // WHAT:  Calculates the broadcast addresses to send the wake-up packet
    //        to. It sends to the global broadcast (255.255.255.255) and also
    //        to network-specific broadcasts in case routers block the global
    //        one. Tries /24 subnet and /16 subnet.
    // INPUT: ip = the TV's IP address (used to calculate subnet broadcasts)
    // OUTPUT: a list of broadcast addresses to send to
    // ==========================================================================

    private static List<String> getBroadcastAddresses(String ip) {
        List<String> addrs = new ArrayList<>();
        addrs.add("255.255.255.255");
        if (ip == null || ip.isEmpty()) return addrs;
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            addrs.add(parts[0] + "." + parts[1] + "." + parts[2] + ".255");
            addrs.add(parts[0] + "." + parts[1] + ".255.255");
        }
        return addrs;
    }
}
