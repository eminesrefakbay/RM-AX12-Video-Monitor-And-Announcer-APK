// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Map;

/**
 * Turns the telemetry of the aircraft into the packet for the tracker. It keeps the time of the newest GPS position (the telemetry
 * values stay the same between GPS frames, so a change of position is what tells that a new one came), the altitude of the ground (the
 * GPS altitude while the aircraft is disarmed, frozen at arming) and works out the height above the launch point. Plain Java.
 */
public final class TrackerFeed {
    /** A position older than this is no longer called fresh in the flags (the tracker keeps aiming where the aircraft was). */
    public static final long FRESH_MS = 6000;

    private double lat = Double.NaN, lon = Double.NaN, alt = Double.NaN, groundAlt = Double.NaN;
    private double speedKmh = 0, courseDeg = 0, lq = Double.NaN;
    private int sats = 0;
    private boolean armed = false;
    private long fixAtMs = Long.MIN_VALUE;

    private static double d(Map<String, Double> v, String k) { Double x = v.get(k); return x == null ? Double.NaN : x; }

    /** Call for every sample of the radio. */
    public void update(Map<String, Double> v, boolean armedNow, long nowMs) {
        double la = d(v, "gps_lat"), lo = d(v, "gps_lon"), al = d(v, "gps_alt"), sp = d(v, "gps_speed"), co = d(v, "gps_heading"), sa = d(v, "sats");
        lq = d(v, "up_lq");
        armed = armedNow;
        boolean changed = la != lat || lo != lon || al != alt || sp != speedKmh || co != courseDeg;
        if (!Double.isNaN(la) && !Double.isNaN(lo)) {
            if (changed || fixAtMs == Long.MIN_VALUE) fixAtMs = nowMs;
            lat = la;
            lon = lo;
        }
        if (!Double.isNaN(al)) alt = al;
        if (!Double.isNaN(sp)) speedKmh = sp;
        if (!Double.isNaN(co)) courseDeg = co;
        if (!Double.isNaN(sa)) sats = (int) Math.round(sa);
        if (!armed && !Double.isNaN(alt)) groundAlt = alt;           // on the ground the height is zero; at arming it freezes
    }

    /** True when the aircraft has a usable GPS position. */
    public boolean hasFix() {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && !(lat == 0.0 && lon == 0.0) && sats >= 5;
    }

    /** The height above the launch point in metres, NaN when unknown. */
    public double heightM() { return Double.isNaN(alt) || Double.isNaN(groundAlt) ? Double.NaN : alt - groundAlt; }

    public byte[] build(long nowMs) {
        boolean fix = hasFix();
        double h = heightM();
        boolean link = !Double.isNaN(lq) && lq > 0;
        long age = fixAtMs == Long.MIN_VALUE ? 65535 : Math.max(0, nowMs - fixAtMs);
        int flags = (fix ? TrackerProtocol.A_FIX : 0) | (armed ? TrackerProtocol.A_ARMED : 0) | (link ? TrackerProtocol.A_LINK : 0)
            | (!Double.isNaN(h) ? TrackerProtocol.A_HEIGHT : 0);
        return TrackerProtocol.encodeAircraft(flags, sats, fix ? lat : 0.0, fix ? lon : 0.0, Double.isNaN(h) ? 0.0 : h, age, speedKmh, courseDeg);
    }

    /** True when the newest position is recent enough to be called fresh. */
    public boolean fresh(long nowMs) { return fixAtMs != Long.MIN_VALUE && nowMs - fixAtMs <= FRESH_MS; }
}
