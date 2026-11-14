// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;

/**
 * The flight as a smooth path through space, built once from a flight log, for the 3D replay.
 *
 * The log has a position and an attitude about once a second (the telemetry rate at a long-range link), written 20 times a second.
 * Only the moments when the values really changed are kept ("keyframes"). Between them the aircraft moves on a cubic Hermite curve
 * (position) and on a squad curve (attitude), so the motion is smooth and has no corners, whatever the screen's frame rate.
 * The curves pass through every logged sample exactly. Local axes in metres: x east, y north, z up, with the first fix as the origin.
 */
public final class FlightPath {
    /** One pose on the path. A mutable holder, so that drawing a frame allocates nothing. */
    public static final class Pose {
        public double x, y, z;
        public double[] q = Quat.identity();
        public double speedKmh;
        public double headingDeg, pitchDeg, rollDeg;
    }

    public static final class Event {
        public final long ms;
        public final String kind;      // takeoff, mode, farthest, height, speed, window, landing
        public final String label;
        public final double x, y, z;
        Event(long ms, String kind, String label, double x, double y, double z) { this.ms = ms; this.kind = kind; this.label = label; this.x = x; this.y = y; this.z = z; }
    }

    /** Signs of the attitude angles as the log holds them. The log has pitch positive with the nose DOWN (flying fast gives +30 to +47), so it is turned here. If roll leans the wrong way, flip ROLL_SIGN. */
    public static final double PITCH_SIGN = -1.0, ROLL_SIGN = 1.0;
    /** Fewer satellites than this: the position is not trusted. */
    public static final int MIN_SATS = 6;
    /** Changes closer together than this belong to one telemetry packet. */
    static final long MERGE_MS = 250;
    static final double EARTH_M = 6371008.8;

    public int n;                                      // position keyframes
    public int na;                                     // attitude keyframes
    public long[] t = new long[0], ta = new long[0];
    public double[] x = new double[0], y = new double[0], z = new double[0];
    public double[] speed = new double[0];             // km/h
    double[] vx = new double[0], vy = new double[0], vz = new double[0];   // tangents in metres per millisecond
    double[][] q = new double[0][];
    double[][] ctl = new double[0][];
    public double originLat = Double.NaN, originLon = Double.NaN;
    public final List<Event> events = new ArrayList<Event>();
    public double maxRangeM, maxHeightM, maxSpeedKmh;

    public long startMs() { return n == 0 ? 0 : t[0]; }
    public long endMs() { return n == 0 ? 0 : t[n - 1]; }

    /** Latitude and longitude to metres east and north of the origin. */
    public double eastM(double lon) { return Math.toRadians(lon - originLon) * Math.cos(Math.toRadians(originLat)) * EARTH_M; }
    public double northM(double lat) { return Math.toRadians(lat - originLat) * EARTH_M; }

    // ------------------------------------------------------------------ building

    public static FlightPath build(FlightLog log) {
        FlightPath p = new FlightPath();
        int cLat = log.indexOf("gps_lat"), cLon = log.indexOf("gps_lon"), cSats = log.indexOf("sats");
        int cPitch = log.indexOf("pitch"), cRoll = log.indexOf("roll"), cYaw = log.indexOf("yaw"), cHead = log.indexOf("gps_heading");
        int cH = log.indexOf("height"), cAlt = log.indexOf("gps_alt"), cSpd = log.indexOf("gps_speed"), cVs = log.indexOf("vspeed");
        if (cLat < 0 || cLon < 0 || log.rows < 2) return p;

        // The position and the attitude reach the log in separate telemetry packets, so each has its own keyframes.
        ArrayList<double[]> pk = new ArrayList<double[]>();      // t, lat, lon, up, speed km/h, vspeed, course
        ArrayList<double[]> ak = new ArrayList<double[]>();      // t, pitch, roll, heading
        double[] lastP = null, lastA = null;
        double alt0 = Double.NaN, lastHeading = Double.NaN;
        for (int r = 0; r < log.rows; r++) {
            double lat = log.value(r, cLat), lon = log.value(r, cLon);
            boolean fix = GeoMath.validFix(lat, lon);
            if (fix && cSats >= 0) { double sv = log.value(r, cSats); if (!Double.isNaN(sv) && sv < MIN_SATS) fix = false; }
            if (fix) {
                double up = cH >= 0 ? log.value(r, cH) : Double.NaN;
                double alt = cAlt >= 0 ? log.value(r, cAlt) : Double.NaN;
                if (Double.isNaN(alt0) && !Double.isNaN(alt)) alt0 = alt;
                if (Double.isNaN(up) && !Double.isNaN(alt)) up = alt - alt0;
                double course = cHead >= 0 ? log.value(r, cHead) : Double.NaN;
                double[] row = {log.time[r], lat, lon, nz(up), nz(cSpd >= 0 ? log.value(r, cSpd) : Double.NaN),
                    nz(cVs >= 0 ? log.value(r, cVs) : Double.NaN), course};
                if (lastP == null || lat != lastP[1] || lon != lastP[2] || row[3] != lastP[3]) {
                    if (lastP != null && row[0] - lastP[0] < MERGE_MS && pk.size() > 1) pk.set(pk.size() - 1, row); else pk.add(row);
                    lastP = row;
                }
                if (!Double.isNaN(course)) lastHeading = course;
            }
            if (cPitch >= 0 || cRoll >= 0 || cYaw >= 0) {
                double pi = cPitch >= 0 ? log.value(r, cPitch) : Double.NaN, ro = cRoll >= 0 ? log.value(r, cRoll) : Double.NaN;
                double ya = cYaw >= 0 ? log.value(r, cYaw) : Double.NaN;
                if (Double.isNaN(ya)) ya = lastHeading;
                if (Double.isNaN(pi) && Double.isNaN(ro) && Double.isNaN(ya)) continue;
                double[] row = {log.time[r], pi, ro, ya};
                if (lastA == null || !(pi == lastA[1] && ro == lastA[2] && ya == lastA[3])) {
                    if (lastA != null && row[0] - lastA[0] < MERGE_MS && ak.size() > 1) ak.set(ak.size() - 1, row); else ak.add(row);
                    lastA = row;
                }
            }
        }
        if (pk.size() < 2) return p;

        int n = pk.size();
        p.n = n;
        p.t = new long[n]; p.x = new double[n]; p.y = new double[n]; p.z = new double[n]; p.speed = new double[n];
        p.originLat = pk.get(0)[1]; p.originLon = pk.get(0)[2];
        double[] vsMs = new double[n], spdMs = new double[n], hdgRad = new double[n];
        boolean[] hdgOk = new boolean[n];
        for (int i = 0; i < n; i++) {
            double[] row = pk.get(i);
            p.t[i] = (long) row[0];
            p.x[i] = p.eastM(row[2]); p.y[i] = p.northM(row[1]); p.z[i] = row[3];
            p.speed[i] = row[4];
            spdMs[i] = row[4] / 3.6; vsMs[i] = row[5];
            hdgOk[i] = !Double.isNaN(row[6]); hdgRad[i] = Math.toRadians(hdgOk[i] ? row[6] : 0);
        }
        // tangents: the slope across the neighbours, pulled towards what the GPS says about speed and course when the aircraft is moving
        p.vx = new double[n]; p.vy = new double[n]; p.vz = new double[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dt = Math.max(1, p.t[b] - p.t[a]);
            double fx = (p.x[b] - p.x[a]) / dt, fy = (p.y[b] - p.y[a]) / dt, fz = (p.z[b] - p.z[a]) / dt;   // m per ms
            double w = hdgOk[i] ? clamp((spdMs[i] - 1.5) / 3.0, 0, 1) * 0.5 : 0;
            double gx = spdMs[i] * Math.sin(hdgRad[i]) / 1000.0, gy = spdMs[i] * Math.cos(hdgRad[i]) / 1000.0;
            p.vx[i] = (1 - w) * fx + w * gx;
            p.vy[i] = (1 - w) * fy + w * gy;
            p.vz[i] = 0.7 * fz + 0.3 * (vsMs[i] / 1000.0);
        }
        // attitude keyframes
        int na = ak.size();
        if (na == 0) { ak.add(new double[]{p.t[0], 0, 0, 0}); ak.add(new double[]{p.t[n - 1], 0, 0, 0}); na = 2; }
        if (na == 1) { double[] r0 = ak.get(0); ak.add(new double[]{r0[0] + 1, r0[1], r0[2], r0[3]}); na = 2; }
        p.na = na;
        p.ta = new long[na];
        p.q = new double[na][];
        for (int i = 0; i < na; i++) {
            double[] row = ak.get(i);
            p.ta[i] = (long) row[0];
            double pitch = Double.isNaN(row[1]) ? 0 : row[1] * PITCH_SIGN, roll = Double.isNaN(row[2]) ? 0 : row[2] * ROLL_SIGN;
            double[] qi = Quat.fromHeadingPitchRoll(Double.isNaN(row[3]) ? 0 : row[3], pitch, roll);
            if (i > 0) qi = Quat.nearSide(qi, p.q[i - 1]);
            p.q[i] = qi;
        }
        p.ctl = new double[na][];
        for (int i = 0; i < na; i++) p.ctl[i] = Quat.control(p.q[Math.max(0, i - 1)], p.q[i], p.q[Math.min(na - 1, i + 1)]);

        p.findEvents(log);
        return p;
    }

    private static double nz(double v) { return Double.isNaN(v) ? 0 : v; }
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    private static boolean same(double[] a, double[] b) {
        for (int i = 1; i < 10; i++) if (a[i] != b[i] && !(Double.isNaN(a[i]) && Double.isNaN(b[i]))) return false;
        return true;
    }

    // ------------------------------------------------------------------ the curve

    /** The keyframe at or before the time (0 to n-2), found by binary search. */
    static int segment(long[] times, int count, double ms) {
        int lo = 0, hi = count - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= ms) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** The pose at a time in milliseconds (any real number: the position of a running replay is not a whole millisecond). */
    public void sample(double ms, Pose out) {
        if (n == 0) return;
        // position
        if (ms <= t[0]) { out.x = x[0]; out.y = y[0]; out.z = z[0]; out.speedKmh = speed[0]; }
        else if (ms >= t[n - 1]) { out.x = x[n - 1]; out.y = y[n - 1]; out.z = z[n - 1]; out.speedKmh = speed[n - 1]; }
        else {
            int i = segment(t, n, ms);
            double h = t[i + 1] - t[i];
            double u = (ms - t[i]) / h;
            double u2 = u * u, u3 = u2 * u;
            double h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
            out.x = h00 * x[i] + h10 * h * vx[i] + h01 * x[i + 1] + h11 * h * vx[i + 1];
            out.y = h00 * y[i] + h10 * h * vy[i] + h01 * y[i + 1] + h11 * h * vy[i + 1];
            out.z = h00 * z[i] + h10 * h * vz[i] + h01 * z[i + 1] + h11 * h * vz[i + 1];
            double s = u * u * (3 - 2 * u);
            out.speedKmh = speed[i] + (speed[i + 1] - speed[i]) * s;
        }
        // attitude
        if (ms <= ta[0]) out.q = q[0];
        else if (ms >= ta[na - 1]) out.q = q[na - 1];
        else {
            int i = segment(ta, na, ms);
            double u = (ms - ta[i]) / (double) (ta[i + 1] - ta[i]);
            out.q = Quat.squad(q[i], Quat.nearSide(q[i + 1], q[i]), ctl[i], Quat.nearSide(ctl[i + 1], ctl[i]), u);
        }
        out.headingDeg = Quat.headingDeg(out.q);
        out.pitchDeg = Quat.pitchDeg(out.q);
        out.rollDeg = Quat.rollDeg(out.q);
    }

    /** The path as a polyline, one point every stepMs: x, y, z and the speed in km/h, four numbers per point. For the ribbon on the ground view. */
    public float[] polyline(long stepMs) {
        if (n < 2) return new float[0];
        int m = (int) ((endMs() - startMs()) / stepMs) + 2;
        float[] out = new float[m * 4];
        Pose p = new Pose();
        for (int i = 0; i < m; i++) {
            sample(Math.min(endMs(), startMs() + (double) i * stepMs), p);
            out[i * 4] = (float) p.x; out[i * 4 + 1] = (float) p.y; out[i * 4 + 2] = (float) p.z; out[i * 4 + 3] = (float) p.speedKmh;
        }
        return out;
    }

    // ------------------------------------------------------------------ events

    /** The flight mode as a person says it: the log keeps the four-letter names of the telemetry. */
    static String modeName(String fm) {
        switch (fm) {
            case "ANGL": return "ANGLE";
            case "HORI": return "HORIZON";
            case "ACRO": return "ACRO";
            case "AIR": return "AIR";
            case "MANU": return "MANUAL";
            case "POSH": return "POS HOLD";
            case "ALTH": return "ALT HOLD";
            case "CRUS": return "CRUISE";
            case "WP": return "MISSION";
            case "LAND": return "LANDING";
            case "!FS!": return "FAILSAFE";
            case "!ERR": return "ERROR";
            default: return fm;
        }
    }

    private void findEvents(FlightLog log) {
        double home = 0;
        int far = 0, high = 0, fast = 0;
        double maxR = -1, maxZ = -1e9, maxS = -1;
        for (int i = 0; i < n; i++) {
            double r = Math.hypot(x[i], y[i]);
            if (r > maxR) { maxR = r; far = i; }
            if (z[i] > maxZ) { maxZ = z[i]; high = i; }
            if (speed[i] > maxS) { maxS = speed[i]; fast = i; }
        }
        maxRangeM = maxR; maxHeightM = maxZ; maxSpeedKmh = maxS;
        events.add(new Event(t[0], "takeoff", "TAKE-OFF", x[0], y[0], z[0]));
        int cFm = log.indexOf("fm"), cWin = log.indexOf("win_left");
        String prevFm = null;
        double prevWin = Double.NaN;
        Pose p = new Pose();
        for (int r = 0; r < log.rows; r++) {
            if (log.time[r] < t[0]) continue;
            if (cFm >= 0) {
                String fm = log.word(r, cFm);
                if (fm != null && !fm.equals(prevFm)) {
                    if (prevFm != null && !fm.equals("OK")) { sample(log.time[r], p); events.add(new Event(log.time[r], "mode", modeName(fm), p.x, p.y, p.z)); }
                    prevFm = fm;
                }
            }
            if (cWin >= 0) {
                double w = log.value(r, cWin);
                if (!Double.isNaN(w)) {
                    if (!Double.isNaN(prevWin) && prevWin >= 0 && w < 0) { sample(log.time[r], p); events.add(new Event(log.time[r], "window", "WINDOW ENDS", p.x, p.y, p.z)); }
                    prevWin = w;
                }
            }
        }
        events.add(new Event(t[far], "farthest", "FARTHEST " + Math.round(maxR) + " m", x[far], y[far], z[far]));
        events.add(new Event(t[high], "height", "MAX HEIGHT " + Math.round(maxZ) + " m", x[high], y[high], z[high]));
        events.add(new Event(t[fast], "speed", "MAX SPEED " + Math.round(maxS) + " km/h", x[fast], y[fast], z[fast]));
        events.add(new Event(t[n - 1], "landing", "LANDING", x[n - 1], y[n - 1], z[n - 1]));
        java.util.Collections.sort(events, new java.util.Comparator<Event>() { public int compare(Event a, Event b) { return Long.compare(a.ms, b.ms); } });
    }
}
