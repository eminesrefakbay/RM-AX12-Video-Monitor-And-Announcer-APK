// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The return reserve: what is left in the pack compared with what the flight home takes, and a margin that tells how much flying is left
 * before the pilot must turn back. The numbers belong to a pack (its capacity is needed to turn mAh into percent), so every pack keeps its
 * own home speed, safety factor and limits. It is a guide from a straight-line distance and an average speed and current, not a promise.
 * Plain Java, tested on a PC.
 */
public final class ReturnReserve {
    private ReturnReserve() {}

    public enum State { OFF, OK, WARN, CRIT }
    public enum Event { NONE, WARN, CRIT, BACK_TO_OK }

    /** The settings of one pack. */
    public static final class Cfg {
        public double speedKmh = 70;       // the speed on the way home
        public double currentA = 0;        // the current on the way home; 0 = learned during the flight
        public int factorPct = 20;         // the way home is longer than the straight line
        public int landPct = 15;           // what is kept for the landing
        public int warnPct = 10;           // the margin below which the warning comes
        public int critPct = 3;            // and the critical one
        public boolean voice = true;

        public Cfg copy() {
            Cfg c = new Cfg();
            c.speedKmh = speedKmh; c.currentA = currentA; c.factorPct = factorPct; c.landPct = landPct; c.warnPct = warnPct; c.critPct = critPct; c.voice = voice;
            return c;
        }
    }

    /** @return null if the settings are fine, otherwise a message for the user */
    public static String validate(Cfg c) {
        if (!(c.speedKmh >= 10 && c.speedKmh <= 300)) return I18n.t("Speed on the way home: 10 to 300 km/h.");
        if (!(c.currentA >= 0 && c.currentA <= 200)) return I18n.t("Current on the way home: 0 (learned) to 200 A.");
        if (c.factorPct < 0 || c.factorPct > 200) return I18n.t("Extra distance: 0 to 200 %.");
        if (c.landPct < 0 || c.landPct > 50) return I18n.t("Landing reserve: 0 to 50 %.");
        if (c.critPct < 0 || c.critPct >= c.warnPct || c.warnPct > 50) return I18n.t("Use critical below warning, both below 50 %.");
        return null;
    }

    public static final class Result {
        public boolean valid;
        public String reason = "";
        public double remainingPct = Double.NaN, needHomePct = Double.NaN, landPct = Double.NaN, marginPct = Double.NaN;
        public double homeS = Double.NaN, spareS = Double.NaN, currentA = Double.NaN;
        public State state = State.OFF;
    }

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    /**
     * @param capacityMah the pack's capacity (0 = not known: nothing can be said)
     * @param usedMah     mAh used so far (NaN = not known: the battery percent is used instead)
     * @param batPct      the battery percent of the flight controller (NaN = not known)
     * @param distM       the distance from home in metres
     * @param learnedA    the average current learned in this flight (NaN = not yet)
     */
    public static Result compute(Cfg c, double capacityMah, double usedMah, double batPct, double distM, double learnedA) {
        Result r = new Result();
        if (!(capacityMah > 0)) { r.reason = I18n.t("Set the pack's capacity (Settings > Packs)"); return r; }
        if (ok(usedMah)) r.remainingPct = Math.max(0, Math.min(100, 100.0 * (1.0 - usedMah / capacityMah)));
        else if (ok(batPct)) r.remainingPct = Math.max(0, Math.min(100, batPct));
        else { r.reason = I18n.t("Waiting for the battery"); return r; }
        if (!ok(distM)) { r.reason = I18n.t("Waiting for the position"); return r; }
        double amps = c.currentA > 0 ? c.currentA : learnedA;
        if (!ok(amps) || amps <= 0) { r.reason = I18n.t("Learning the current"); return r; }
        r.currentA = amps;
        r.homeS = Math.max(0, distM) * (1.0 + c.factorPct / 100.0) / (c.speedKmh / 3.6);
        double homeMah = amps * r.homeS / 3.6;
        r.needHomePct = 100.0 * homeMah / capacityMah;
        r.landPct = c.landPct;
        r.marginPct = r.remainingPct - r.needHomePct - r.landPct;
        r.spareS = r.marginPct > 0 ? r.marginPct / 100.0 * capacityMah * 3.6 / amps : 0;
        r.state = r.marginPct < c.critPct ? State.CRIT : r.marginPct < c.warnPct ? State.WARN : State.OK;
        r.valid = true;
        return r;
    }

    /** The one line under the number: what the margin means in time. */
    public static String spareText(Result r) {
        if (!r.valid) return r.reason;
        if (r.marginPct <= 0) return I18n.t("Turn back now");
        int s = (int) Math.round(r.spareS);
        return I18n.t("About {0} of flying left before you must turn back", String.format(Locale.US, "%d:%02d", s / 60, s % 60));
    }

    // ------------------------------------------------------------------ the average current

    /** The current of this flight as a slowly moving average (30 s), so the way home is judged on what the aircraft really draws. */
    public static final class Learner {
        public static final double TAU_S = 30, MIN_A = 3;
        private double avg = Double.NaN;
        private long lastMs = -1;

        public void reset() { avg = Double.NaN; lastMs = -1; }
        public double value() { return avg; }

        public void update(long nowMs, double curr, boolean armed) {
            if (!armed) { reset(); return; }
            if (!ok(curr) || curr < MIN_A) return;
            if (!ok(avg) || lastMs < 0) { avg = curr; lastMs = nowMs; return; }
            double dt = Math.max(0, Math.min(5000, nowMs - lastMs)) / 1000.0;
            lastMs = nowMs;
            double a = 1.0 - Math.exp(-dt / TAU_S);
            avg += a * (curr - avg);
        }
    }

    // ------------------------------------------------------------------ the calls

    /** Turns the margin into calls: one warning, one critical, and one "back to normal"; the margin must move 1 % past the limit to change back. */
    public static final class Watcher {
        private State state = State.OFF;

        public void reset() { state = State.OFF; }
        public State state() { return state; }

        public Event update(Result r, Cfg c) {
            if (r == null || !r.valid) return Event.NONE;
            State next = state;
            double m = r.marginPct;
            if (state == State.OFF || state == State.OK) {
                if (m < c.critPct) next = State.CRIT;
                else if (m < c.warnPct) next = State.WARN;
                else next = State.OK;
            } else if (state == State.WARN) {
                if (m < c.critPct) next = State.CRIT;
                else if (m >= c.warnPct + 1) next = State.OK;
            } else {
                if (m >= c.critPct + 1) next = m >= c.warnPct + 1 ? State.OK : State.WARN;
            }
            Event e = Event.NONE;
            if (next != state) {
                if (next == State.CRIT) e = Event.CRIT;
                else if (next == State.WARN && state != State.CRIT) e = Event.WARN;
                else if (next == State.OK && state != State.OFF) e = Event.BACK_TO_OK;
            }
            state = next;
            return e;
        }
    }

    // ------------------------------------------------------------------ the settings of all packs

    public static final String DEFAULT_KEY = "";

    /** The stored text: one line per pack, name|speed|current|factor|land|warn|crit|voice. */
    public static String encode(Map<String, Cfg> all) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Cfg> e : all.entrySet()) {
            Cfg c = e.getValue();
            sb.append(Packs.cleanName(e.getKey())).append('|').append(trim(c.speedKmh)).append('|').append(trim(c.currentA)).append('|').append(c.factorPct)
                .append('|').append(c.landPct).append('|').append(c.warnPct).append('|').append(c.critPct).append('|').append(c.voice ? 1 : 0).append('\n');
        }
        return sb.toString();
    }

    private static String trim(double v) { return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.US, "%.1f", v); }

    /** A damaged line is skipped. */
    public static Map<String, Cfg> decode(String text) {
        Map<String, Cfg> out = new LinkedHashMap<String, Cfg>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length != 8) continue;
            try {
                Cfg c = new Cfg();
                c.speedKmh = Double.parseDouble(f[1].trim());
                c.currentA = Double.parseDouble(f[2].trim());
                c.factorPct = Integer.parseInt(f[3].trim());
                c.landPct = Integer.parseInt(f[4].trim());
                c.warnPct = Integer.parseInt(f[5].trim());
                c.critPct = Integer.parseInt(f[6].trim());
                c.voice = !f[7].trim().equals("0");
                if (validate(c) == null && out.size() < Packs.MAX) out.put(Packs.cleanName(f[0]), c);
            } catch (NumberFormatException e) { /* skipped */ }
        }
        return out;
    }

    /** The settings of a pack; a pack that has none yet gets the defaults. */
    public static Cfg forPack(Map<String, Cfg> all, String pack) {
        Cfg c = all.get(Packs.cleanName(pack == null ? "" : pack));
        return c == null ? new Cfg() : c.copy();
    }
}
