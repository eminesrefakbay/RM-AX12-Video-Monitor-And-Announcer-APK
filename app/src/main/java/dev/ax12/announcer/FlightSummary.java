// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Locale;

/** The numbers of a finished flight, from its log, for the summary page and the report text. Plain Java, tested on a PC. */
public final class FlightSummary {
    public long durationMs;
    public double maxHeight = Double.NaN, maxDist = Double.NaN, topSpeed = Double.NaN, avgSpeed = Double.NaN;
    public double aboveLimitS;                       // seconds above the height limit
    public double mah = Double.NaN, wh = Double.NaN, km = Double.NaN;
    public double packPct = Double.NaN;              // the share of the pack used (needs its capacity)
    public double minCell = Double.NaN, minCellAtMs = -1, endCell = Double.NaN;
    public double peakCurrent = Double.NaN, peakPowerW = Double.NaN;
    public double windowLeftMin = Double.NaN;        // minutes left of the approval window at the end (NaN: no window)
    public String started = "", aircraft = "", pack = "";
    public int cells;

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    private static double num(FlightLog log, String key) {
        String v = log.end.get(key);
        if (v == null || v.isEmpty()) return Double.NaN;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /**
     * @param capacityMah the pack's capacity, 0 when it is not known
     * @param limitM      the height above which time is counted (the approval's 120 m)
     */
    public static FlightSummary build(FlightLog log, double capacityMah, double limitM) {
        FlightSummary s = new FlightSummary();
        s.started = log.meta("started", "");
        s.aircraft = log.meta("aircraft", "");
        s.pack = log.meta("pack", "");
        s.cells = (int) log.metaNumber("cells", 0);
        s.durationMs = log.durationMs();
        double[] h = Manoeuvres.heights(log);
        int cSpd = log.indexOf("gps_speed"), cCur = log.indexOf("curr"), cV = log.indexOf("vbat"), cCapa = log.indexOf("capa"), cCell = log.indexOf("cell"), cWin = log.indexOf("win_left");
        double prevSpeedSum = 0;
        for (int r = 0; r < log.rows; r++) {
            if (ok(h[r]) && (!ok(s.maxHeight) || h[r] > s.maxHeight)) s.maxHeight = h[r];
            if (ok(h[r]) && h[r] > limitM && r + 1 < log.rows) s.aboveLimitS += (log.time[r + 1] - log.time[r]) / 1000.0;
            double sp = cSpd >= 0 ? log.value(r, cSpd) : Double.NaN;
            if (ok(sp) && (!ok(s.topSpeed) || sp > s.topSpeed)) s.topSpeed = sp;
            double cu = cCur >= 0 ? log.value(r, cCur) : Double.NaN, v = cV >= 0 ? log.value(r, cV) : Double.NaN;
            if (ok(cu) && (!ok(s.peakCurrent) || cu > s.peakCurrent)) s.peakCurrent = cu;
            if (ok(cu) && ok(v) && (!ok(s.peakPowerW) || cu * v > s.peakPowerW)) s.peakPowerW = cu * v;
            double cell = cCell >= 0 ? log.value(r, cCell) : (ok(v) && s.cells > 0 ? v / s.cells : Double.NaN);
            if (ok(cell) && cell > 1.0) {
                if (!ok(s.minCell) || cell < s.minCell) { s.minCell = cell; s.minCellAtMs = log.time[r]; }
                s.endCell = cell;
            }
            if (cCapa >= 0) { double c = log.value(r, cCapa); if (ok(c)) s.mah = c; }
            if (cWin >= 0) { double w = log.value(r, cWin); if (ok(w)) s.windowLeftMin = w; }
        }
        // the footer has the summary of every value, also of those that were not chosen for the log
        double f;
        if (ok(f = num(log, "max_height"))) s.maxHeight = ok(s.maxHeight) ? Math.max(s.maxHeight, f) : f;
        if (ok(f = num(log, "max_dist"))) s.maxDist = f;
        if (ok(f = num(log, "max_speed"))) s.topSpeed = ok(s.topSpeed) ? Math.max(s.topSpeed, f) : f;
        if (ok(f = num(log, "min_cell")) && f > 1.0) s.minCell = ok(s.minCell) ? Math.min(s.minCell, f) : f;
        if (ok(f = num(log, "peak_current"))) s.peakCurrent = ok(s.peakCurrent) ? Math.max(s.peakCurrent, f) : f;
        if (ok(f = num(log, "mah"))) s.mah = f;
        if (ok(f = num(log, "distance_km"))) s.km = f;
        if (ok(f = num(log, "energy_wh"))) s.wh = f;
        if (ok(s.km) && s.durationMs > 0) s.avgSpeed = s.km / (s.durationMs / 3600000.0);
        if (capacityMah > 0 && ok(s.mah)) s.packPct = 100.0 * s.mah / capacityMah;
        return s;
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static String n0(double v, String unit) { return ok(v) ? String.format(Locale.US, "%d %s", Math.round(v), unit) : "-"; }

    /** A few lines for a post-flight report (the English words: it is pasted into a report). */
    public String reportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("AX-HUD flight report\n");
        if (!started.isEmpty()) sb.append("Date: ").append(started).append('\n');
        if (!aircraft.isEmpty()) sb.append("Aircraft: ").append(aircraft).append('\n');
        sb.append("Flight time: ").append(clock(durationMs)).append('\n');
        sb.append("Highest: ").append(n0(maxHeight, "m")).append(" above the take-off point");
        if (aboveLimitS >= 1) sb.append(" (above the limit for ").append(Math.round(aboveLimitS)).append(" s)");
        sb.append('\n');
        sb.append("Farthest: ").append(ok(maxDist) ? String.format(Locale.US, "%.2f km", maxDist / 1000.0) : "-").append('\n');
        sb.append("Top speed: ").append(n0(topSpeed, "km/h")).append('\n');
        sb.append("Battery used: ").append(n0(mah, "mAh"));
        if (ok(packPct)) sb.append(" (").append(Math.round(packPct)).append(" % of the pack)");
        sb.append('\n');
        if (ok(minCell)) sb.append("Lowest cell: ").append(String.format(Locale.US, "%.2f V", minCell)).append('\n');
        return sb.toString();
    }
}
