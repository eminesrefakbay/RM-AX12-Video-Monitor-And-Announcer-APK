// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * The Bluetooth LE protocol between the app and the antenna tracker (an ESP32 with two servos, its own GPS and a compass). Three
 * characteristics under one service: the aircraft state goes to the tracker (write without response, 5 times a second), the tracker's
 * status comes back (notify), and commands go to the tracker (write). Every packet is at most 20 bytes, so it fits the default ATT size
 * of any Bluetooth LE connection without negotiating. All numbers are little-endian. The same layout is implemented in C++ in
 * tracker/include/protocol.h; tracker/test/vectors.txt holds packets that both sides must read and write in the same way.
 * Plain Java, tested on a PC.
 */
public final class TrackerProtocol {
    private TrackerProtocol() {}

    public static final String DEVICE_NAME = "AX-TRACK";
    public static final String SERVICE = "5a1d0000-7c3e-4b5a-9e1f-a0b1c2d3e4f5";
    public static final String AIRCRAFT = "5a1d0001-7c3e-4b5a-9e1f-a0b1c2d3e4f5";     // write without response, 20 bytes
    public static final String STATUS = "5a1d0002-7c3e-4b5a-9e1f-a0b1c2d3e4f5";       // notify, 20 bytes
    public static final String COMMAND = "5a1d0003-7c3e-4b5a-9e1f-a0b1c2d3e4f5";      // write

    public static final int VERSION = 1;
    public static final int PACKET_LEN = 20;

    // aircraft flags
    public static final int A_FIX = 1, A_ARMED = 2, A_LINK = 4, A_HEIGHT = 8;
    // status flags
    public static final int S_GPS = 1, S_COMPASS = 2, S_FRESH = 4, S_TRACKING = 8, S_FLIPPED = 16, S_CALIBRATING = 32, S_LIMIT = 64;
    // modes
    public static final int MODE_HOLD = 0, MODE_AUTO = 1, MODE_CENTRE = 2, MODE_SWEEP = 3;
    // commands (first byte of a command write)
    public static final int CMD_MODE = 1, CMD_COMPASS_CAL = 2, CMD_DECLINATION = 3, CMD_HEIGHT_OFFSET = 4;

    /** CRC-8 with the polynomial 0x07 (initial value 0) over the first len bytes. */
    public static int crc8(byte[] b, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc ^= b[i] & 0xFF;
            for (int k = 0; k < 8; k++) crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
        }
        return crc;
    }

    private static void put16(byte[] b, int at, int v) { b[at] = (byte) v; b[at + 1] = (byte) (v >> 8); }
    private static void put32(byte[] b, int at, int v) { put16(b, at, v); put16(b, at + 2, v >> 16); }
    private static int u16(byte[] b, int at) { return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8); }
    private static int i16(byte[] b, int at) { return (short) u16(b, at); }
    private static int i32(byte[] b, int at) { return u16(b, at) | (u16(b, at + 2) << 16); }
    private static int clamp(long v, int lo, int hi) { return (int) Math.max(lo, Math.min(hi, v)); }

    /**
     * The aircraft state for the tracker. Layout: 0 version, 1 flags, 2 satellites, 3..6 latitude (degrees * 1e7), 7..10 longitude,
     * 11..12 height above the launch point (decimetres), 13..14 age of the position (ms), 15..16 ground speed (0.1 km/h),
     * 17..18 course (0.01 degrees), 19 CRC-8 of bytes 0..18.
     */
    public static byte[] encodeAircraft(int flags, int sats, double lat, double lon, double heightM, long ageMs, double speedKmh, double courseDeg) {
        byte[] b = new byte[PACKET_LEN];
        b[0] = (byte) VERSION;
        b[1] = (byte) flags;
        b[2] = (byte) clamp(sats, 0, 255);
        put32(b, 3, (int) Math.round(lat * 1e7));
        put32(b, 7, (int) Math.round(lon * 1e7));
        put16(b, 11, clamp(Math.round(heightM * 10.0), -32768, 32767));
        put16(b, 13, clamp(ageMs, 0, 65535));
        put16(b, 15, clamp(Math.round(speedKmh * 10.0), 0, 65535));
        double c = ((courseDeg % 360.0) + 360.0) % 360.0;
        put16(b, 17, clamp(Math.round(c * 100.0), 0, 35999));
        b[19] = (byte) crc8(b, 19);
        return b;
    }

    /** What the tracker tells the app about itself. */
    public static final class Status {
        public int flags, sats, mode;
        public double lat, lon;                 // the tracker's own position, degrees
        public double headingDeg;               // where the tracker's base points, degrees true
        public double azimuthDeg, elevationDeg; // where the antennas are told to look
        public double distanceM;                // to the aircraft, in steps of 10 m
        public boolean has(int flag) { return (flags & flag) != 0; }
    }

    /** Layout: 0 version, 1 flags, 2 satellites, 3 mode, 4..7 latitude, 8..11 longitude, 12..13 heading (0.1 degrees), 14..15 azimuth (0.1 degrees), 16..17 elevation (0.1 degrees, signed), 18..19 distance (10 m). */
    public static byte[] encodeStatus(Status s) {
        byte[] b = new byte[PACKET_LEN];
        b[0] = (byte) VERSION;
        b[1] = (byte) s.flags;
        b[2] = (byte) clamp(s.sats, 0, 255);
        b[3] = (byte) s.mode;
        put32(b, 4, (int) Math.round(s.lat * 1e7));
        put32(b, 8, (int) Math.round(s.lon * 1e7));
        put16(b, 12, clamp(Math.round((((s.headingDeg % 360.0) + 360.0) % 360.0) * 10.0), 0, 3599));
        put16(b, 14, clamp(Math.round((((s.azimuthDeg % 360.0) + 360.0) % 360.0) * 10.0), 0, 3599));
        put16(b, 16, clamp(Math.round(s.elevationDeg * 10.0), -32768, 32767));
        put16(b, 18, clamp(Math.round(s.distanceM / 10.0), 0, 65535));
        return b;
    }

    /** @return null when the bytes are not a status packet of this version */
    public static Status decodeStatus(byte[] b) {
        if (b == null || b.length != PACKET_LEN || (b[0] & 0xFF) != VERSION) return null;
        Status s = new Status();
        s.flags = b[1] & 0xFF;
        s.sats = b[2] & 0xFF;
        s.mode = b[3] & 0xFF;
        s.lat = i32(b, 4) / 1e7;
        s.lon = i32(b, 8) / 1e7;
        s.headingDeg = u16(b, 12) / 10.0;
        s.azimuthDeg = u16(b, 14) / 10.0;
        s.elevationDeg = i16(b, 16) / 10.0;
        s.distanceM = u16(b, 18) * 10.0;
        return s;
    }

    /** What the tracker reads from an aircraft packet (the app only writes these; this is for tests and for a simulator). */
    public static final class Aircraft {
        public int flags, sats, ageMs;
        public double lat, lon, heightM, speedKmh, courseDeg;
        public boolean has(int flag) { return (flags & flag) != 0; }
    }

    /** @return null when the length, the version or the CRC is wrong */
    public static Aircraft decodeAircraft(byte[] b) {
        if (b == null || b.length != PACKET_LEN || (b[0] & 0xFF) != VERSION || (b[19] & 0xFF) != crc8(b, 19)) return null;
        Aircraft a = new Aircraft();
        a.flags = b[1] & 0xFF;
        a.sats = b[2] & 0xFF;
        a.lat = i32(b, 3) / 1e7;
        a.lon = i32(b, 7) / 1e7;
        a.heightM = i16(b, 11) / 10.0;
        a.ageMs = u16(b, 13);
        a.speedKmh = u16(b, 15) / 10.0;
        a.courseDeg = u16(b, 17) / 100.0;
        return a;
    }

    public static byte[] cmdMode(int mode) { return new byte[]{(byte) CMD_MODE, (byte) mode}; }
    public static byte[] cmdCompassCalibration(boolean start) { return new byte[]{(byte) CMD_COMPASS_CAL, (byte) (start ? 1 : 0)}; }

    /** The magnetic declination of the place, in degrees east (west is negative), kept in the tracker. */
    public static byte[] cmdDeclination(double degreesEast) {
        byte[] b = new byte[3];
        b[0] = (byte) CMD_DECLINATION;
        put16(b, 1, clamp(Math.round(degreesEast * 10.0), -900, 900));
        return b;
    }

    /** The height of the tracker's antennas above the launch point, in metres, kept in the tracker. */
    public static byte[] cmdHeightOffset(double metres) {
        byte[] b = new byte[3];
        b[0] = (byte) CMD_HEIGHT_OFFSET;
        put16(b, 1, clamp(Math.round(metres * 10.0), -500, 5000));
        return b;
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }
}
