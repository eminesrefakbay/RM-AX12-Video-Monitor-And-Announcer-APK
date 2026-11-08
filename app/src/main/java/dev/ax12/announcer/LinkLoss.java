// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Map;

/**
 * Remembers where the aircraft was while the link was good, and notices when the link is lost in flight, so that the last position can
 * be shown, spoken and saved. A link counts as lost when telemetry has stopped or the uplink quality is zero for a moment, but only after
 * it has been good with a GPS fix at least once in this flight (a radio that never had a link has not lost one). Plain Java, so that it
 * can be tested on a PC.
 */
public final class LinkLoss {
    public static final long LOST_AFTER_MS = 1500;
    public static final long BACK_AFTER_MS = 1000;
    public static final long SETTLE_MS = 1000;
    public static final int MIN_SATS = 5;

    /** One GPS position with what was known about the flight at that moment. */
    public static final class Fix {
        public final double lat, lon, gpsAlt, height, speedKmh, headingDeg;
        public final int sats;
        public final long atMs;

        public Fix(double lat, double lon, double gpsAlt, double height, double speedKmh, double headingDeg, int sats, long atMs) {
            this.lat = lat;
            this.lon = lon;
            this.gpsAlt = gpsAlt;
            this.height = height;
            this.speedKmh = speedKmh;
            this.headingDeg = headingDeg;
            this.sats = sats;
            this.atMs = atMs;
        }
    }

    public enum Event { NONE, LOST, RECOVERED }

    private Fix last, atLoss;
    private boolean wasArmed, everUp, lost, pending;
    private long upSince = -1, badSince = -1, goodSince = -1, lostAtMs = -1, recoveredAtMs = -1;

    private static double nz(Double d) { return d == null ? Double.NaN : d; }

    /** Good unless telemetry says it is lost or the uplink quality is zero; with neither known, the link is taken as good. */
    public static boolean linkGood(Map<String, Double> v) {
        Double tlm = v.get("tlm"), lq = v.get("up_lq");
        return !((tlm != null && tlm < 0.5) || (lq != null && lq <= 0));
    }

    private static Fix fixOf(long nowMs, Map<String, Double> v) {
        Double lat = v.get("gps_lat"), lon = v.get("gps_lon"), sats = v.get("sats");
        if (lat == null || lon == null || !GeoMath.validFix(lat, lon)) return null;
        if (sats != null && sats < MIN_SATS) return null;
        return new Fix(lat, lon, nz(v.get("gps_alt")), nz(v.get("height")), nz(v.get("gps_speed")), nz(v.get("gps_heading")),
            sats == null ? -1 : (int) Math.round(sats), nowMs);
    }

    public Event update(long nowMs, boolean armed, Map<String, Double> v) {
        if (armed && !wasArmed) {                               // a new flight: the old one is forgotten
            last = atLoss = null;
            everUp = lost = pending = false;
            upSince = badSince = goodSince = lostAtMs = recoveredAtMs = -1;
        }
        wasArmed = armed;
        boolean good = linkGood(v);
        if (!lost) {
            if (!armed) return Event.NONE;
            if (good) {
                badSince = -1;
                Fix f = fixOf(nowMs, v);
                if (f != null) {
                    last = f;
                    if (upSince < 0) upSince = nowMs;
                    if (nowMs - upSince >= SETTLE_MS) everUp = true;
                }
                return Event.NONE;
            }
            if (badSince < 0) badSince = nowMs;
            if (everUp && last != null && nowMs - badSince >= LOST_AFTER_MS) {
                lost = true;
                pending = true;
                lostAtMs = badSince;
                atLoss = last;
                goodSince = -1;
                recoveredAtMs = -1;
                return Event.LOST;
            }
            return Event.NONE;
        }
        if (good) {                                             // lost: the link may come back, armed or not
            if (goodSince < 0) goodSince = nowMs;
            else if (nowMs - goodSince >= BACK_AFTER_MS) {
                lost = false;
                pending = false;
                recoveredAtMs = nowMs;
                badSince = -1;
                goodSince = -1;
                upSince = -1;
                return Event.RECOVERED;
            }
        } else {
            goodSince = -1;
        }
        return Event.NONE;
    }

    public boolean isLost() { return lost; }

    /** True from the loss until the link is back or the pilot closes the card, also after the arm switch went low. */
    public boolean isPending() { return pending; }
    public void dismiss() { pending = false; }
    public Fix lastFix() { return last; }
    public Fix fixAtLoss() { return atLoss; }
    public boolean everUp() { return everUp; }
    public long lostAtMs() { return lostAtMs; }
    public long recoveredAtMs() { return recoveredAtMs; }
    public long lostForMs(long nowMs) { return lost && lostAtMs >= 0 ? Math.max(0, nowMs - lostAtMs) : 0; }

    /** Metres and compass bearing from home to a fix; both NaN without a home. */
    public static double[] fromHome(Fix f, boolean homeSet, double homeLat, double homeLon) {
        if (f == null || !homeSet) return new double[]{Double.NaN, Double.NaN};
        return new double[]{GeoMath.distanceM(homeLat, homeLon, f.lat, f.lon), GeoMath.bearingDeg(homeLat, homeLon, f.lat, f.lon)};
    }
}
