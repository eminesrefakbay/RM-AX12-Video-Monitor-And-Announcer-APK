// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds the dives, the rolls and the flips of a flight in its log, for the replay's list and the dive report. It works from the height, the
 * attitude (pitch and roll in degrees) and the speed. The attitude reaches the log as often as the telemetry link sends it, so a quick roll in
 * a slow log can be missed or counted short: the list is a help to find the moments, not a measurement. Plain Java, tested on a PC.
 */
public final class Manoeuvres {
    private Manoeuvres() {}

    public static final String DIVE = "dive", ROLL = "roll", FLIP = "flip";

    /** A dive needs to lose at least this much height, within this time. */
    public static final double DIVE_MIN_DROP_M = 40;
    public static final long DIVE_MAX_MS = 20000;
    /** The climb that ends a dive (the bottom is the lowest point before it). */
    public static final double DIVE_RISE_M = 8;
    /** A roll turns the aircraft at least this far, at least this fast. */
    public static final double ROLL_MIN_DEG = 300, ROLL_MIN_RATE = 90;
    /** Two samples further apart than this do not belong to one turn. */
    public static final long TURN_GAP_MS = 1500;
    /** A flip: the nose goes this near to vertical and the aircraft turns over by this much. */
    public static final double FLIP_PITCH_DEG = 75, FLIP_TURN_DEG = 130;
    public static final long FLIP_WINDOW_MS = 1800;

    public static final class Item {
        public String kind;
        public long startMs, endMs;
        public double startH = Double.NaN, bottomH = Double.NaN, apexH = Double.NaN;   // dive: start and bottom; flip: apex
        public double maxSpeed = Double.NaN, peakCurrent = Double.NaN;
        public double angleDeg = Double.NaN;       // dive: the pitch at the fastest descent
        public double fallS, recoverS;             // dive
        public double rate = Double.NaN;           // roll: the fastest turn, degrees per second
        public int count = 1;                      // roll: how many turns

        /** One line for the list. */
        public String detail() {
            if (DIVE.equals(kind)) {
                return String.format(Locale.US, "%d → %d m", Math.round(startH), Math.round(bottomH)) + (Double.isNaN(maxSpeed) ? "" : String.format(Locale.US, " · %d km/h", Math.round(maxSpeed)));
            }
            if (ROLL.equals(kind)) {
                return String.format(Locale.US, "%d", count) + (Double.isNaN(rate) ? "" : String.format(Locale.US, " · %d °/s", Math.round(rate)));
            }
            return Double.isNaN(apexH) ? "" : String.format(Locale.US, "%d m", Math.round(apexH));
        }
    }

    private static double[] column(FlightLog log, String name) {
        int c = log.indexOf(name);
        double[] v = new double[log.rows];
        for (int r = 0; r < log.rows; r++) v[r] = c < 0 ? Double.NaN : log.value(r, c);
        return v;
    }

    /** The height above the arming point: the logged height, or the GPS altitude minus its first value when the height was not logged. */
    public static double[] heights(FlightLog log) {
        double[] h = column(log, "height");
        boolean any = false;
        for (double v : h) if (!Double.isNaN(v)) { any = true; break; }
        if (any) return h;
        double[] alt = column(log, "gps_alt");
        double first = Double.NaN;
        for (double v : alt) if (!Double.isNaN(v)) { first = v; break; }
        for (int i = 0; i < alt.length; i++) alt[i] = Double.isNaN(first) ? Double.NaN : alt[i] - first;
        return alt;
    }

    /** Every manoeuvre of the flight, in time order. */
    public static List<Item> detect(FlightLog log) {
        List<Item> out = new ArrayList<Item>();
        if (log == null || log.rows < 3) return out;
        double[] h = heights(log), spd = column(log, "gps_speed"), cur = column(log, "curr");
        double[] pitch = column(log, "pitch"), roll = column(log, "roll"), yaw = column(log, "yaw");
        dives(log, h, spd, cur, pitch, out);
        rolls(log, roll, out);
        flips(log, h, pitch, roll, yaw, out);
        Collections.sort(out, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return Long.compare(a.startMs, b.startMs); }
        });
        return out;
    }

    public static int count(List<Item> items, String kind) {
        int n = 0;
        for (Item i : items) if (kind.equals(i.kind)) n++;
        return n;
    }

    // ------------------------------------------------------------------ dives

    private static void dives(FlightLog log, double[] h, double[] spd, double[] cur, double[] pitch, List<Item> out) {
        int n = log.rows;
        long[] t = log.time;
        int k = 0, floor = 0;                     // the climb out of one dive is not the start of the next: look back no further than the last bottom
        while (k < n) {
            if (Double.isNaN(h[k])) { k++; continue; }
            int peak = -1;
            for (int i = k; i >= floor && t[k] - t[i] <= DIVE_MAX_MS; i--) {
                if (!Double.isNaN(h[i]) && (peak < 0 || h[i] > h[peak])) peak = i;
            }
            if (peak < 0 || h[peak] - h[k] < DIVE_MIN_DROP_M) { k++; continue; }
            int bottom = k, j = k;
            while (j + 1 < n) {
                j++;
                if (Double.isNaN(h[j])) continue;
                if (h[j] < h[bottom]) bottom = j;
                if (h[j] - h[bottom] >= DIVE_RISE_M) break;
            }
            boolean landing = h[bottom] < 3 && t[n - 1] - t[bottom] < 20000;       // the end of the flight is a landing, not a dive
            if (!landing) out.add(diveItem(log, h, spd, cur, pitch, peak, bottom));
            floor = bottom;
            k = Math.max(j, k + 1);
        }
    }

    private static Item diveItem(FlightLog log, double[] h, double[] spd, double[] cur, double[] pitch, int start, int bottom) {
        long[] t = log.time;
        Item d = new Item();
        d.kind = DIVE;
        d.startMs = t[start];
        d.endMs = t[bottom];
        d.startH = h[start];
        d.bottomH = h[bottom];
        d.fallS = (t[bottom] - t[start]) / 1000.0;
        double peakRate = 0;
        int peakAt = start;
        for (int i = start; i < bottom; i++) {
            long dt = t[i + 1] - t[i];
            if (dt <= 0 || Double.isNaN(h[i]) || Double.isNaN(h[i + 1])) continue;
            double rate = (h[i] - h[i + 1]) / (dt / 1000.0);
            if (rate > peakRate) { peakRate = rate; peakAt = i; }
        }
        int slow = bottom;
        for (int i = peakAt; i < bottom; i++) {
            long dt = t[i + 1] - t[i];
            if (dt <= 0 || Double.isNaN(h[i]) || Double.isNaN(h[i + 1])) continue;
            if ((h[i] - h[i + 1]) / (dt / 1000.0) < 0.2 * peakRate) { slow = i; break; }
        }
        d.recoverS = (t[bottom] - t[slow]) / 1000.0;
        if (!Double.isNaN(pitch[peakAt])) d.angleDeg = Math.abs(pitch[peakAt]);
        long until = t[bottom] + 3000;
        for (int i = start; i < log.rows && t[i] <= until; i++) {
            if (!Double.isNaN(spd[i]) && (Double.isNaN(d.maxSpeed) || spd[i] > d.maxSpeed)) d.maxSpeed = spd[i];
            if (!Double.isNaN(cur[i]) && (Double.isNaN(d.peakCurrent) || cur[i] > d.peakCurrent)) d.peakCurrent = cur[i];
        }
        return d;
    }

    // ------------------------------------------------------------------ rolls

    private static double wrap(double a) {
        while (a > 180) a -= 360;
        while (a <= -180) a += 360;
        return a;
    }

    /** Runs of turning in one direction in the roll angle; a run of at least 300 degrees at a fair rate is a roll. */
    private static void rolls(FlightLog log, double[] roll, List<Item> out) {
        long[] t = log.time;
        int last = -1;                      // the last sample whose value differs from the one before
        double total = 0, peakRate = 0;
        long runStart = -1, runEnd = -1;
        int dir = 0;
        for (int i = 0; i < log.rows; i++) {
            if (Double.isNaN(roll[i])) continue;
            if (last < 0) { last = i; continue; }
            if (roll[i] == roll[last]) continue;
            double step = wrap(roll[i] - roll[last]);
            long dt = t[i] - t[last];
            long span = Math.max(1, dt <= TURN_GAP_MS ? dt : 2 * (t[i] - t[i - 1]));       // after a long hold the turn did not take all of it: it began a moment ago
            int sign = step > 0 ? 1 : -1;
            boolean continues = runStart >= 0 && dt <= TURN_GAP_MS && (sign == dir || Math.abs(step) < 5);
            if (!continues) {
                closeRoll(runStart, runEnd, total, peakRate, out);
                runStart = t[i] - span;
                total = 0;
                peakRate = 0;
                dir = sign;
            }
            if (Math.abs(step) >= 5 || sign == dir) {
                total += step;
                peakRate = Math.max(peakRate, Math.abs(step) / (span / 1000.0));
                dir = sign;
            }
            runEnd = t[i];
            last = i;
        }
        closeRoll(runStart, runEnd, total, peakRate, out);
    }

    private static void closeRoll(long start, long end, double total, double peakRate, List<Item> out) {
        if (start < 0 || end <= start) return;
        double secs = (end - start) / 1000.0;
        if (Math.abs(total) < ROLL_MIN_DEG || Math.abs(total) / secs < ROLL_MIN_RATE) return;
        Item r = new Item();
        r.kind = ROLL;
        r.startMs = start;
        r.endMs = end;
        r.count = Math.max(1, (int) Math.round(Math.abs(total) / 360.0));
        r.rate = peakRate;
        out.add(r);
    }

    // ------------------------------------------------------------------ flips

    /** The nose goes (nearly) vertical and, within moments, the aircraft has turned over: roll or heading changed by half a turn. */
    private static void flips(FlightLog log, double[] h, double[] pitch, double[] roll, double[] yaw, List<Item> out) {
        long[] t = log.time;
        int n = log.rows;
        int i = 0;
        while (i < n) {
            if (Double.isNaN(pitch[i]) || Math.abs(pitch[i]) < FLIP_PITCH_DEG) { i++; continue; }
            int from = i;
            while (from > 0 && t[i] - t[from - 1] <= FLIP_WINDOW_MS) from--;
            int to = i;
            while (to + 1 < n && t[to + 1] - t[i] <= FLIP_WINDOW_MS) to++;
            double dRoll = spread(roll, from, to), dYaw = spread(yaw, from, to);
            if (dRoll >= FLIP_TURN_DEG || dYaw >= FLIP_TURN_DEG) {
                Item f = new Item();
                f.kind = FLIP;
                f.startMs = t[from];
                f.endMs = t[to];
                for (int k = from; k <= to; k++) if (!Double.isNaN(h[k]) && (Double.isNaN(f.apexH) || h[k] > f.apexH)) f.apexH = h[k];
                out.add(f);
                while (i < n && t[i] <= t[to]) i++;                  // one flip, not one for every sample of it
            } else {
                i++;
            }
        }
    }

    /** The largest turn between any two angles of the span (an angle is a circle, so the nearer way round counts). */
    private static double spread(double[] a, int from, int to) {
        double best = 0;
        for (int i = from; i <= to; i++) {
            if (Double.isNaN(a[i])) continue;
            for (int j = i + 1; j <= to; j++) if (!Double.isNaN(a[j])) best = Math.max(best, Math.abs(wrap(a[j] - a[i])));
        }
        return best;
    }
}
