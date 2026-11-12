// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Map;

/**
 * Reads CRSF telemetry frames from the receiver (battery, link quality, flight mode, GPS, attitude).
 * A frame is  [address][length][type][payload ...][crc8]   where length counts type + payload + crc.
 * Plain Java, no Android classes, so it is unit tested on a PC.
 */
public final class CrsfParser {

    /**
     * A signal strength byte as dBm. The CRSF specification sends the magnitude (55 means -55 dBm), but the AX12's own module sends the signed
     * byte (201 means -55 dBm). A magnitude never exceeds 128, so a bigger value is a signed byte.
     */
    public static double rssiDbm(byte b) {
        int v = b & 0xFF;
        return v > 128 ? v - 256 : -v;
    }

    public static final int TYPE_GPS = 0x02;
    public static final int TYPE_VARIO = 0x07;
    public static final int TYPE_BATTERY = 0x08;
    public static final int TYPE_LINK_STATS = 0x14;
    public static final int TYPE_ATTITUDE = 0x1E;
    public static final int TYPE_FLIGHT_MODE = 0x21;

    /** TX power index in the link-statistics frame, in milliwatts. */
    private static final int[] TX_POWER_MW = {0, 10, 25, 100, 500, 1000, 2000, 250, 50};

    public int framesOk = 0;
    public int framesBadCrc = 0;
    public int framesIgnored = 0;

    /** CRC-8, polynomial 0xD5, over the given bytes. */
    public static int crc8(byte[] data, int off, int len) {
        int crc = 0;
        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x80) != 0) ? ((crc << 1) ^ 0xD5) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    /** Build a frame (used by the tests, and handy for a replay tool). */
    public static byte[] makeFrame(int address, int type, byte[] payload) {
        byte[] f = new byte[payload.length + 4];
        f[0] = (byte) address;
        f[1] = (byte) (payload.length + 2);
        f[2] = (byte) type;
        System.arraycopy(payload, 0, f, 3, payload.length);
        f[f.length - 1] = (byte) crc8(f, 2, payload.length + 1);
        return f;
    }

    /**
     * Parse one frame and store its values.
     * @return true if the frame was understood and stored
     */
    public boolean feed(byte[] f, Map<String, Double> num, Map<String, String> txt) {
        if (f == null || f.length < 4) { framesIgnored++; return false; }
        int len = f[1] & 0xFF;
        if (len < 2 || f.length < len + 2) { framesIgnored++; return false; }
        int type = f[2] & 0xFF;
        int payloadLen = len - 2;
        int expectedCrc = f[len + 1] & 0xFF;
        if (crc8(f, 2, len - 1) != expectedCrc) { framesBadCrc++; return false; }
        int p = 3;   // payload start
        boolean used = true;
        switch (type) {
            case TYPE_BATTERY:
                if (payloadLen < 8) { used = false; break; }
                num.put("vbat", be16(f, p) / 10.0);
                num.put("curr", be16(f, p + 2) / 10.0);
                num.put("capa", (double) (((f[p + 4] & 0xFF) << 16) | ((f[p + 5] & 0xFF) << 8) | (f[p + 6] & 0xFF)));
                num.put("batpct", (double) (f[p + 7] & 0xFF));
                break;
            case TYPE_LINK_STATS:
                if (payloadLen < 10) { used = false; break; }
                num.put("up_rssi1", rssiDbm(f[p]));
                num.put("up_rssi2", rssiDbm(f[p + 1]));
                num.put("up_lq", (double) (f[p + 2] & 0xFF));
                num.put("up_snr", (double) f[p + 3]);
                num.put("rf_mode", (double) (f[p + 5] & 0xFF));
                int tp = f[p + 6] & 0xFF;
                num.put("tx_power", (double) (tp < TX_POWER_MW.length ? TX_POWER_MW[tp] : -1));
                num.put("dn_rssi", rssiDbm(f[p + 7]));
                num.put("dn_lq", (double) (f[p + 8] & 0xFF));
                num.put("dn_snr", (double) f[p + 9]);
                break;
            case TYPE_FLIGHT_MODE: {
                int end = p;
                while (end < p + payloadLen && f[end] != 0) end++;
                txt.put("fm", new String(f, p, end - p, java.nio.charset.StandardCharsets.US_ASCII));
                break;
            }
            case TYPE_GPS:
                if (payloadLen < 15) { used = false; break; }
                num.put("gps_lat", be32(f, p) / 1e7);
                num.put("gps_lon", be32(f, p + 4) / 1e7);
                num.put("gps_speed", (be16(f, p + 8) & 0xFFFF) / 10.0);      // km/h
                num.put("gps_heading", (be16(f, p + 10) & 0xFFFF) / 100.0);  // degrees
                num.put("gps_alt", (double) ((be16(f, p + 12) & 0xFFFF) - 1000));  // metres
                num.put("sats", (double) (f[p + 14] & 0xFF));
                break;
            case TYPE_ATTITUDE:
                if (payloadLen < 6) { used = false; break; }
                num.put("pitch", Math.toDegrees(be16(f, p) / 10000.0));
                num.put("roll", Math.toDegrees(be16(f, p + 2) / 10000.0));
                num.put("yaw", Math.toDegrees(be16(f, p + 4) / 10000.0));
                break;
            case TYPE_VARIO:
                if (payloadLen < 2) { used = false; break; }
                num.put("vspeed", be16(f, p) / 100.0);                          // m/s
                break;
            default:
                used = false;
        }
        if (used) framesOk++; else framesIgnored++;
        return used;
    }

    private static int be16(byte[] f, int i) {            // signed
        return (short) (((f[i] & 0xFF) << 8) | (f[i + 1] & 0xFF));
    }

    private static int be32(byte[] f, int i) {            // signed
        return ((f[i] & 0xFF) << 24) | ((f[i + 1] & 0xFF) << 16) | ((f[i + 2] & 0xFF) << 8) | (f[i + 3] & 0xFF);
    }
}
