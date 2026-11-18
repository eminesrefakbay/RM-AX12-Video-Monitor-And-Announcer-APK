// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Tests of the return reserve, the manoeuvre finder, the flight summary and the exports. */
public class FlightToolsTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    /** A synthetic flight at 20 rows a second: climb, cruise, a dive, a roll, a flip, and a landing. */
    static FlightLog flight() throws Exception {
        StringBuilder sb = new StringBuilder("# started: 2026-11-12 16:53:00\n# aircraft: TEST\n# cells: 6\n# end: duration_ms=60000\nt_ms,height,gps_speed,curr,vbat,capa,pitch,roll,yaw,gps_lat,gps_lon,gps_alt\n");
        double roll = 0;
        for (int i = 0; i <= 1200; i++) {
            double t = i / 20.0, h, spd = 80, pitch = 0, yaw = 90;
            if (t < 10) h = 10 * t;
            else if (t < 20) h = 100;
            else if (t < 23) { h = 100 - 80 * (t - 20) / 3; spd = 130; pitch = 45; }
            else if (t < 26) { h = 20 + 80 * (t - 23) / 3; spd = 100; }
            else if (t < 55) h = 100;
            else h = Math.max(0, 100 - 20 * (t - 55));
            if (t >= 30 && t < 30.9) roll = roll + 20; else if (t < 30) roll = 0;
            if (roll > 180) roll -= 360;
            double r = roll;
            if (t >= 40 && t < 41) {                      // a flip: the nose goes up to vertical and over, the heading turns round
                pitch = 90 * Math.sin((t - 40) * Math.PI);
                yaw = 90 + 180 * (t - 40);
            } else if (t >= 41) yaw = 270;
            sb.append((long) (t * 1000)).append(',').append(String.format(java.util.Locale.US, "%.1f,%.0f,%.1f,%.1f,%.0f,%.0f,%.0f,%.0f,%.6f,%.6f,%.1f\n",
                h, spd, 30.0 + (t > 20 && t < 23 ? 25 : 0), 24.0 - t * 0.02, t * 30.0, pitch, r, yaw, 36.85 + t * 1e-5, 34.60 + t * 1e-5, 20 + h));
        }
        return LogFormat.read(new BufferedReader(new StringReader(sb.toString())), 1_000_000);
    }

    public static void main(String[] args) throws Exception {
        // ---------- the return reserve ----------
        ReturnReserve.Cfg c = new ReturnReserve.Cfg();
        check(ReturnReserve.validate(c) == null, "the defaults are valid");
        ReturnReserve.Result r = ReturnReserve.compute(c, 5000, 2250, Double.NaN, 1400, 38);
        check(r.valid && near(r.remainingPct, 55, 0.01), "55 % left: " + r.remainingPct);
        check(near(r.homeS, 86.4, 0.2) && near(r.needHomePct, 18.2, 0.2), "the way home: " + r.homeS + " s, " + r.needHomePct + " %");
        check(near(r.marginPct, 21.8, 0.3) && r.state == ReturnReserve.State.OK && r.spareS > 100, "a margin of " + r.marginPct + " % is fine");
        r = ReturnReserve.compute(c, 5000, 4000, Double.NaN, 1400, 38);
        check(r.valid && r.marginPct < 0 && r.state == ReturnReserve.State.CRIT && r.spareS == 0, "too little left: critical, no time left");
        check(ReturnReserve.spareText(r).equals(I18n.t("Turn back now")), "and it says to turn back");
        r = ReturnReserve.compute(c, 0, 100, Double.NaN, 100, 38);
        check(!r.valid && r.state == ReturnReserve.State.OFF, "without the capacity nothing can be said");
        r = ReturnReserve.compute(c, 5000, Double.NaN, 40, 500, 30);
        check(r.valid && near(r.remainingPct, 40, 0.01), "the battery percent is used when the mAh are not known");
        r = ReturnReserve.compute(c, 5000, 100, Double.NaN, 500, Double.NaN);
        check(!r.valid, "no current yet: not valid");
        c.currentA = 40;
        r = ReturnReserve.compute(c, 5000, 100, Double.NaN, 500, Double.NaN);
        check(r.valid && r.currentA == 40, "a fixed current is used when one is set");
        c.currentA = 0;
        ReturnReserve.Cfg bad = new ReturnReserve.Cfg();
        bad.warnPct = 2; bad.critPct = 5;
        check(ReturnReserve.validate(bad) != null, "critical above warning is refused");

        ReturnReserve.Learner lr = new ReturnReserve.Learner();
        lr.update(0, 40, true); lr.update(1000, 40, true); lr.update(2000, 1, true);
        check(near(lr.value(), 40, 0.01), "a small current (the aircraft standing) does not pull the average down");
        for (int i = 0; i < 100; i++) lr.update(3000 + i * 1000L, 20, true);
        check(lr.value() < 30 && lr.value() > 19, "the average follows slowly: " + lr.value());
        lr.update(200000, 20, false);
        check(Double.isNaN(lr.value()), "disarmed: it starts again");

        ReturnReserve.Watcher w = new ReturnReserve.Watcher();
        ReturnReserve.Cfg wc = new ReturnReserve.Cfg();
        ReturnReserve.Result m = new ReturnReserve.Result();
        m.valid = true;
        m.marginPct = 30;
        check(w.update(m, wc) == ReturnReserve.Event.NONE && w.state() == ReturnReserve.State.OK, "a good margin: no call");
        m.marginPct = 9;
        check(w.update(m, wc) == ReturnReserve.Event.WARN, "below 10 %: one warning");
        check(w.update(m, wc) == ReturnReserve.Event.NONE, "and not again");
        m.marginPct = 10.5;
        check(w.update(m, wc) == ReturnReserve.Event.NONE && w.state() == ReturnReserve.State.WARN, "a little over the limit: still warning (no flicker)");
        m.marginPct = 2;
        check(w.update(m, wc) == ReturnReserve.Event.CRIT, "below 3 %: critical");
        m.marginPct = 12;
        check(w.update(m, wc) == ReturnReserve.Event.BACK_TO_OK, "well above again: back to normal");
        w.reset();
        m.marginPct = 1;
        check(w.update(m, wc) == ReturnReserve.Event.CRIT, "a flight that starts critical says so once");

        Map<String, ReturnReserve.Cfg> all = new LinkedHashMap<String, ReturnReserve.Cfg>();
        ReturnReserve.Cfg one = new ReturnReserve.Cfg();
        one.speedKmh = 65.5; one.currentA = 38; one.voice = false;
        all.put("Pack 1", one);
        Map<String, ReturnReserve.Cfg> back = ReturnReserve.decode(ReturnReserve.encode(all));
        check(back.size() == 1 && back.get("Pack 1").speedKmh == 65.5 && back.get("Pack 1").currentA == 38 && !back.get("Pack 1").voice, "the settings of a pack survive being stored");
        check(ReturnReserve.forPack(back, "Other").speedKmh == 70 && ReturnReserve.decode("junk\n1|2\n").isEmpty(), "a pack without settings gets the defaults, damaged lines are skipped");

        // ---------- the manoeuvres ----------
        FlightLog log = flight();
        List<Manoeuvres.Item> items = Manoeuvres.detect(log);
        check(Manoeuvres.count(items, Manoeuvres.DIVE) == 1, "one dive (the landing is not one): " + Manoeuvres.count(items, Manoeuvres.DIVE));
        check(Manoeuvres.count(items, Manoeuvres.ROLL) == 1, "one roll: " + Manoeuvres.count(items, Manoeuvres.ROLL));
        check(Manoeuvres.count(items, Manoeuvres.FLIP) == 1, "one flip: " + Manoeuvres.count(items, Manoeuvres.FLIP));
        Manoeuvres.Item dive = null;
        for (Manoeuvres.Item i : items) if (Manoeuvres.DIVE.equals(i.kind)) dive = i;
        check(dive != null && near(dive.startH, 100, 1) && near(dive.bottomH, 20, 1) && near(dive.startMs, 20000, 600) && near(dive.fallS, 3, 0.6), "the dive: " + (dive == null ? "none" : dive.detail() + ", " + dive.fallS + " s"));
        check(dive != null && near(dive.maxSpeed, 130, 1) && near(dive.angleDeg, 45, 1) && near(dive.peakCurrent, 55, 1), "its speed, angle and current");
        check(dive != null && dive.recoverS >= 0 && dive.recoverS <= dive.fallS, "the recovery is part of the fall");
        boolean sorted = true;
        for (int i = 1; i < items.size(); i++) if (items.get(i).startMs < items.get(i - 1).startMs) sorted = false;
        check(sorted, "the list is in time order");
        for (Manoeuvres.Item i : items) if (Manoeuvres.ROLL.equals(i.kind)) check(i.count == 1 && i.rate > 300 && near(i.startMs, 30000, 800), "the roll: " + i.detail());
        check(Manoeuvres.detect(LogFormat.read(new BufferedReader(new StringReader("t_ms,vbat\n0,24\n100,24\n200,24\n")), 1000)).isEmpty(), "a log without height and attitude has none");

        // a slow bank and a normal climb are not manoeuvres
        StringBuilder calm = new StringBuilder("t_ms,height,pitch,roll,yaw\n");
        for (int i = 0; i < 400; i++) calm.append(i * 50).append(',').append(30 + i * 0.1).append(',').append(10 * Math.sin(i / 20.0)).append(',').append(Math.round(40 * Math.sin(i / 15.0))).append(',').append((i / 4) % 360).append('\n');
        check(Manoeuvres.detect(LogFormat.read(new BufferedReader(new StringReader(calm.toString())), 100000)).isEmpty(), "gentle flying finds nothing");

        // ---------- the summary ----------
        FlightSummary s = FlightSummary.build(log, 5000, 120);
        check(s.durationMs == 60000 && near(s.maxHeight, 100, 0.5) && s.aboveLimitS == 0, "summary: time and height " + s.maxHeight);
        check(near(s.topSpeed, 130, 0.5) && near(s.peakCurrent, 55, 0.5) && near(s.peakPowerW, 55 * 23.6, 60), "top speed and power");
        check(s.cells == 6 && s.minCell > 3 && s.minCell < 4, "the lowest cell from the pack voltage: " + s.minCell);
        s = FlightSummary.build(log, 5000, 50);
        check(s.aboveLimitS > 25, "time above a limit of 50 m: " + s.aboveLimitS);
        check(s.reportText().contains("Flight time: 1:00") && s.reportText().contains("AX-HUD flight report"), "the report text");

        // ---------- the exports ----------
        TimeZone utc = TimeZone.getTimeZone("UTC");
        LogExport.Result g = LogExport.export(log, LogExport.GPX, "x", false, 0, utc);
        check(g.name.equals("x.gpx") && g.text.contains("<trkpt lat=\"36.85") && g.text.contains("<time>2026-11-12T16:53:00Z</time>") && g.text.contains("<ele>"), "GPX has points, time and height");
        LogExport.Result gs = LogExport.export(log, LogExport.GPX, "x", true, 0, utc);
        check(gs.text.contains("lat=\"0.0000000\"") && !gs.text.contains("36.85"), "GPX without positions starts at 0, 0");
        LogExport.Result k = LogExport.export(log, LogExport.KML, "x", false, 0, utc);
        check(k.text.contains("<LineString>") && k.text.contains("relativeToGround") && k.text.contains("34.60"), "KML has the path");
        LogExport.Result ca = LogExport.export(log, LogExport.CSV_ALL, "x", false, 0, utc);
        check(ca.text.startsWith("t_s,height,gps_speed") && ca.text.split("\n").length == 1202, "CSV all: every row");
        LogExport.Result cs = LogExport.export(log, LogExport.CSV_SMALL, "x", true, 0, utc);
        check(cs.text.startsWith("t_s,height,gps_speed,vbat,curr,capa") && !cs.text.contains("gps_lat"), "CSV small without positions has no position columns: " + cs.text.split("\n")[0]);
        check(LogExport.export(log, LogExport.REPORT, "x", false, 5000, utc).text.contains("% of the pack"), "the report has the share of the pack");
        check(LogExport.extension(LogExport.GPX).equals(".gpx") && LogExport.extension(LogExport.REPORT).equals(".txt"), "extensions");

        // ---------- the backup groups ----------
        check(BackupGroups.groupOf("rules") == BackupGroups.RULES && BackupGroups.groupOf("packs") == BackupGroups.BATTERY && BackupGroups.groupOf("reserveCfg") == BackupGroups.BATTERY, "rules and packs go to their groups");
        check(BackupGroups.groupOf("trkAddr") == BackupGroups.TRACKER && BackupGroups.groupOf("packBook") == BackupGroups.HISTORY && BackupGroups.groupOf("layoutWithAircraft") == BackupGroups.LAYOUTS, "tracker, history, layouts");
        check(BackupGroups.groupOf("language") == BackupGroups.SETTINGS, "anything else is a setting");
        java.util.Map<String, Object> bv = new java.util.HashMap<String, Object>();
        bv.put("rules", "a\n# c\nb"); bv.put("trkAddr", "AA"); bv.put("language", "de");
        boolean[] sel = BackupGroups.defaultSelection();
        java.util.Map<String, Object> kept = BackupGroups.filter(bv, sel);
        check(kept.containsKey("rules") && kept.containsKey("language") && !kept.containsKey("trkAddr"), "the default backup leaves the tracker out");
        check(BackupGroups.counts(bv)[BackupGroups.RULES] == 2 && BackupGroups.counts(bv)[BackupGroups.TRACKER] == 1, "rule lines are counted without comments");
        check(BackupGroups.present(bv).size() == 3, "groups present in a file");
        long day = 86400000L;
        check(BackupGroups.reminderDue(0, 100 * day, 30, true) && !BackupGroups.reminderDue(80 * day, 100 * day, 30, true) && BackupGroups.reminderDue(60 * day, 100 * day, 30, true) && !BackupGroups.reminderDue(0, 100 * day, 30, false), "the 30-day reminder");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
