package com.mctrash692.freemote.util;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class WakeOnLan {

    private static final String TAG = "WakeOnLan";
    private static final int WOL_PORT = 9;

    public interface ErrorCallback {
        void onError(String message);
    }

    public static void send(final String tvIp, final String mac, final ErrorCallback onError) {
        if (mac == null || mac.trim().isEmpty()) {
            if (onError != null) {
                onError.onError("No MAC address saved. Add it in Settings under Wake-on-LAN.");
            }
            return;
        }

        new Thread(() -> {
            try {
                byte[] macBytes  = parseMac(mac.trim());
                byte[] packet    = buildPacket(macBytes);
                String broadcast = deriveBroadcast(tvIp);

                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);

                // Send to subnet broadcast
                InetAddress subnetAddr = InetAddress.getByName(broadcast);
                socket.send(new DatagramPacket(packet, packet.length, subnetAddr, WOL_PORT));

                // Also send to global broadcast for robustness
                InetAddress globalAddr = InetAddress.getByName("255.255.255.255");
                socket.send(new DatagramPacket(packet, packet.length, globalAddr, WOL_PORT));

                socket.close();
                Log.d(TAG, "WoL sent → " + broadcast + " + 255.255.255.255 for " + mac.trim());

            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Bad MAC: " + e.getMessage());
                if (onError != null) onError.onError("Invalid MAC address: " + mac);
            } catch (Exception e) {
                Log.e(TAG, "WoL failed: " + e.getMessage(), e);
                if (onError != null) onError.onError("Wake-on-LAN failed: " + e.getMessage());
            }
        }).start();
    }

    /** 102-byte magic packet: 6×0xFF then 16× the 6-byte MAC. */
    private static byte[] buildPacket(byte[] mac) {
        byte[] pkt = new byte[6 + 16 * 6];
        for (int i = 0; i < 6; i++) pkt[i] = (byte) 0xFF;
        for (int i = 1; i <= 16; i++) System.arraycopy(mac, 0, pkt, i * 6, 6);
        return pkt;
    }

    /** Parse "AA:BB:CC:DD:EE:FF" or "AA-BB-CC-DD-EE-FF" → 6 bytes. */
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

    /** Replace last octet with 255 → subnet broadcast. */
    private static String deriveBroadcast(String ip) {
        if (ip == null || ip.isEmpty()) return "255.255.255.255";
        int dot = ip.lastIndexOf('.');
        return dot < 0 ? "255.255.255.255" : ip.substring(0, dot + 1) + "255";
    }
}
