// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests of what a flight adds up to, the pack book, the voice debrief and the efficiency in the flight list. */
public class PackTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    static PackBook.Flight flight(long wallMs, int sec, double mah, double wh, double km, double minCell, double rest, String aircraft) {
        PackBook.Flight f = new PackBook.Flight();
        f.wallMs = wallMs; f.seconds = sec; f.mah = mah; f.wh = wh; f.km = km; f.minCell = minCell; f.restCell = rest; f.aircraft = aircraft;
        return f;
    }

    public static void main(String[] args) throws Exception {
        I18n.load(I18nTests.FILES, "en");
        double nan = Double.NaN;

        // ---------- adding up a flight ----------
        FlightTotals t = new FlightTotals();
        for (long ms = 0; ms <= 100000; ms += 100) t.update(ms, 36.0, 12.0, 10.0, nan);   // 10 m/s, 120 W, for 100 s
        check(near(t.distanceKm(), 1.0, 0.001) && near(t.energyWh(), 120.0 * 100 / 3600, 0.01) && near(t.seconds(), 100, 0.01), "one kilometre and 3.33 Wh: " + t.distanceKm() + " " + t.energyWh());
        check(near(t.mahUsed(), 10 * 100 / 3.6, 1.0), "without a capacity value the current adds up: " + t.mahUsed());
        check(near(t.whPerKm(), 3.333, 0.01) && near(t.mahPerKm(), 277.8, 1.0), "efficiency: " + t.whPerKm() + " " + t.mahPerKm());
        FlightTotals c = new FlightTotals();
        for (long ms = 0; ms <= 10000; ms += 100) c.update(ms, 36.0, 12.0, 10.0, ms / 10.0);
        check(c.mahUsed() == 1000.0, "the capacity the flight controller counts wins over the sum: " + c.mahUsed());
        FlightTotals g = new FlightTotals();
        g.update(0, 36, 12, 10, nan); g.update(100, 36, 12, 10, nan); g.update(5100, 36, 12, 10, nan); g.update(5200, 36, 12, 10, nan);
        check(near(g.seconds(), 0.2, 0.001) && near(g.distanceKm(), 0.002, 0.0001), "a gap of five seconds is not counted");
        FlightTotals m = new FlightTotals();
        for (long ms = 0; ms <= 10000; ms += 100) m.update(ms, nan, nan, nan, nan);
        check(m.distanceKm() == 0 && m.energyWh() == 0 && Double.isNaN(m.mahUsed()) && Double.isNaN(m.whPerKm()) && Double.isNaN(m.mahPerKm()), "nothing known, nothing added up");
        FlightTotals half = new FlightTotals();
        for (long ms = 0; ms <= 10000; ms += 100) half.update(ms, 36.0, nan, 10.0, nan);
        check(half.energyWh() == 0 && half.distanceKm() > 0.09 && half.mahUsed() > 0, "energy needs both volts and amps");
        FlightTotals neg = new FlightTotals();
        for (long ms = 0; ms <= 10000; ms += 100) neg.update(ms, -5.0, 12.0, -3.0, nan);
        check(neg.distanceKm() == 0 && neg.energyWh() == 0, "negative speed and current add nothing");
        FlightTotals short1 = new FlightTotals();
        for (long ms = 0; ms <= 10000; ms += 100) short1.update(ms, 36.0, 12.0, 10.0, nan);
        check(Double.isNaN(short1.whPerKm()) || short1.distanceKm() >= FlightTotals.MIN_KM_FOR_EFFICIENCY ? true : Double.isNaN(short1.whPerKm()), "efficiency needs some distance");
        t.reset();
        check(t.distanceKm() == 0 && t.seconds() == 0 && Double.isNaN(t.mahUsed()), "reset");

        // ---------- the live flight keeps the same totals ----------
        FlightStats fs = new FlightStats();
        fs.configure(5, 1750, 1650, 6);
        Map<String, Double> v = new HashMap<String, Double>();
        v.put("ch5", 1000.0);
        long now = 0;
        fs.update(v, now);
        v.put("ch5", 1800.0); v.put("gps_speed", 72.0); v.put("vbat", 24.0); v.put("curr", 20.0); v.put("capa", 0.0);
        v.put("gps_lat", 36.8); v.put("gps_lon", 34.6); v.put("sats", 12.0);
        for (now = 100; now <= 60000; now += 100) { v.put("capa", now / 100.0 * 0.55); fs.update(v, now); }
        check(fs.armed() && near(fs.totals().distanceKm(), 20.0 * 59.5 / 1000, 0.05), "the live distance: " + fs.totals().distanceKm());
        check(near(fs.totals().energyWh(), 24.0 * 20.0 * 59.5 / 3600, 0.15) && fs.totals().mahUsed() > 300, "the live energy and capacity: " + fs.totals().energyWh() + " " + fs.totals().mahUsed());
        check(near(fs.homeLat(), 36.8, 1e-9) && near(fs.homeLon(), 34.6, 1e-9), "home is exposed");
        v.put("ch5", 1000.0);
        for (long k = 0; k < 1000; k += 100) { now += 100; fs.update(v, now); }
        double km = fs.totals().distanceKm();
        check(!fs.armed() && km > 0.9, "after landing the totals stay readable: " + km);
        v.put("ch5", 1800.0);
        fs.update(v, now + 100); fs.update(v, now + 500);
        check(fs.armed() && fs.totals().distanceKm() < 0.01, "and start again at the next arming");

        // ---------- the pack book ----------
        PackBook b = new PackBook();
        check(b.isEmpty() && b.entries().isEmpty() && b.find("x") == null, "empty at first");
        check(b.add("Li-ion 1", flight(1000, 600, 2900, 120, 11.8, 3.40, 3.81, "Akb7")) == 1, "the first flight of a pack");
        check(b.add("li-ion 1", flight(2000, 700, 2300, 100, 9.0, 3.45, 3.85, "Akb7")) == 2, "the pack is found without regard to capitals");
        check(b.add("Li-ion 1", flight(3000, 500, 1900, 80, 7.0, 3.30, nan, "Akb7")) == 3, "the third");
        check(b.add("LiPo 3", flight(4000, 560, 1700, 90, 8.0, 3.20, 3.70, "Akb7")) == 1, "another pack");
        PackBook.Entry e = b.find("LI-ION 1");
        check(e != null && e.flights == 3 && e.seconds == 1800 && near(e.mah, 7100, 0.001) && near(e.wh, 300, 0.001) && near(e.km, 27.8, 0.001) && near(e.minCell, 3.30, 0.001) && e.lastMs == 3000, "the totals of a pack");
        check(near(e.whPerKm(), 300 / 27.8, 0.001) && near(e.mahPerKm(), 7100 / 27.8, 0.01) && near(e.avgMinutes(), 10.0, 0.001) && near(e.cycles(3550), 2.0, 0.001) && Double.isNaN(e.cycles(0)), "efficiency, average and cycles");
        check(e.recent.size() == 3 && e.recent.get(0).wallMs == 1000 && e.recent.get(2).wallMs == 3000, "the latest flights, the oldest first");
        check(b.entries().get(0).name.equals("LiPo 3") && b.entries().get(1).name.equals("Li-ion 1"), "the pack flown last comes first");
        for (int i = 0; i < 14; i++) b.add("Li-ion 1", flight(5000 + i, 100, 100, 5, 1, 3.5, nan, ""));
        check(b.find("Li-ion 1").recent.size() == PackBook.KEEP_FLIGHTS && b.find("Li-ion 1").flights == 17 && b.find("Li-ion 1").recent.get(PackBook.KEEP_FLIGHTS - 1).wallMs == 5013, "only the latest ten flights are kept; the totals are not cut");
        PackBook few = new PackBook();
        few.add("A", flight(10, 100, nan, nan, nan, nan, nan, ""));
        check(near(few.find("A").mah, 0, 0) && few.find("A").flights == 1 && Double.isNaN(few.find("A").minCell) && Double.isNaN(few.find("A").whPerKm()), "a flight with nothing measured still counts");
        few.add("", flight(20, 100, 1, 1, 1, nan, nan, ""));
        check(few.find("-") != null, "a flight without a pack name goes under a dash");

        // the text
        String text = b.encode();
        check(text.startsWith(PackBook.HEADER + "\n") && PackBook.valid(text), "the text is valid: " + text.split("\n").length + " lines");
        PackBook back = PackBook.decode(text);
        check(back.encode().equals(text) && back.size() == b.size(), "text and back again");
        check(PackBook.valid("") && PackBook.valid(PackBook.HEADER + "\n") && PackBook.valid(null), "empty is fine");
        check(!PackBook.valid("garbage line") && !PackBook.valid("P|x|1|2|3|4") && !PackBook.valid("F|nopack|1|2|3|4|5|6|7|a") && !PackBook.valid("P|A|x|1|1|1|1|1|1"), "wrong lines are refused");
        check(!PackBook.valid("P|A|1|1|1|1|1|1|1\nP|a|1|1|1|1|1|1|1"), "the same pack twice is refused");
        check(!PackBook.valid("P|A|-1|1|1|1|1|1|1") && !PackBook.valid("P|A|1|-1|1|1|1|1|1"), "negative counts are refused");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 30; i++) big.append("P|Pack").append(i).append("|1|1|1|1|1|1|1\n");
        check(!PackBook.valid(big.toString()) && PackBook.decode(big.toString()).size() == PackBook.MAX_PACKS, "at most sixteen packs");
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= PackBook.MAX_TEXT) huge.append("# padding padding padding padding\n");
        check(!PackBook.valid(huge.toString()), "a text that is too long is refused");
        PackBook tol = PackBook.decode("junk\nP|A|2|100|50|10|2|3.3|99\nP|B|oops\nF|A|5|60|25|5|1|3.4||x\nF|Zed|1|1|1|1|1|1||x\n");
        check(tol.size() == 1 && tol.find("A").flights == 2 && tol.find("A").recent.size() == 1 && Double.isNaN(tol.find("A").recent.get(0).restCell), "reading skips what makes no sense and keeps the rest");
        PackBook pipe = new PackBook();
        pipe.add("Bad|Name\nX", flight(1, 60, 1, 1, 1, 3, nan, "Air|craft"));
        check(PackBook.valid(pipe.encode()) && PackBook.decode(pipe.encode()).size() == 1, "separators in names cannot break the text");
        PackBook many = new PackBook();
        for (int i = 0; i < 20; i++) many.add("P" + i, flight(100 + i, 60, 1, 1, 1, 3, nan, ""));
        check(many.size() == PackBook.MAX_PACKS && many.find("P0") == null && many.find("P19") != null, "the pack flown longest ago makes room");
        check(b.remove("lipo 3") && b.find("LiPo 3") == null && !b.remove("nothing"), "a pack can be removed");
        b.clear();
        check(b.isEmpty(), "and all of them");
        check(PackBook.durationText(5400).equals("1 h 30 min") && PackBook.durationText(2400).equals("40 min") && PackBook.durationText(0).equals("0 min"), "time as text");

        // ---------- the debrief ----------
        PackBook.Flight full = flight(0, 734, 2918, 248.9, 11.8, 3.40, 3.81, "Akb7");
        check(Debrief.text("en", full, "Li-ion 1", 4).equals("Flight time 12 minutes 14 seconds. Distance 11.8 kilometers. Used 2918 milliamp hours. Efficiency 21.1 watt hours per kilometer. Lowest cell 3.40 volts. Resting cell 3.81 volts. Pack Li-ion 1, flight 4."), "the whole debrief: " + Debrief.text("en", full, "Li-ion 1", 4));
        check(Debrief.text("en", flight(0, 45, nan, nan, nan, nan, nan, ""), "", 0).equals("Flight time 45 seconds."), "a short flight with no data");
        check(Debrief.text("en", flight(0, 75, 400, nan, 0.02, 3.6, nan, ""), "P", 1).equals("Flight time 1 minute 15 seconds. Used 400 milliamp hours. Lowest cell 3.60 volts. Pack P, flight 1."), "one minute, no useful distance: " + Debrief.text("en", flight(0, 75, 400, nan, 0.02, 3.6, nan, ""), "P", 1));
        check(!Debrief.text("en", flight(0, 600, 800, 40, 0.1, 3.5, nan, ""), "", 0).contains("Efficiency"), "no efficiency below 0.2 km");
        check(!Debrief.text("en", flight(0, 600, 800, 40, 3, 0.0, 0.0, ""), "", 0).contains("cell"), "a cell value of zero is not spoken");

        // ---------- the flight list ----------
        LogStore.Entry le = LogTextTests.entry();
        String before = LogText.summary(le);
        check(!before.contains("Flew") && !before.contains("Wh"), "an old log shows no new numbers");
        le.distanceKm = 11.8; le.energyWh = 248.9; le.mah = 2918;
        String sum = LogText.summary(le);
        check(sum.endsWith("2918 mAh used  \u00B7  Flew 11.8 km  \u00B7  248.9 Wh used  \u00B7  21.1 Wh/km  \u00B7  247 mAh/km"), "efficiency in the summary: " + sum);
        le.distanceKm = 0.1;
        check(!LogText.summary(le).contains("Wh/km") && LogText.summary(le).contains("Flew 0.1 km"), "no efficiency over a short distance");

        File dir = Files.createTempDirectory("axpack").toFile();
        try {
            // the footer of a log carries the numbers
            FlightLogger lg = new FlightLogger(dir, FlightLoggerTests.WALL, FlightLoggerTests.META);
            lg.configure(true, 3, null, 1);
            Map<String, String> texts = new HashMap<String, String>();
            long ms = 0;
            for (; ms <= 60000; ms += 50) {
                Map<String, Double> nums = FlightLoggerTests.nums(2000, 24.0, 36.8, 34.6);
                nums.put("gps_speed", 72.0); nums.put("curr", 20.0); nums.put("capa", ms / 60.0);
                lg.update(ms, true, nums, texts);
            }
            lg.close();
            List<File> files = LogStore.files(dir);
            check(files.size() == 1, "one log");
            LogStore.Entry en = LogStore.summary(files.get(0));
            check(en.clean && near(en.distanceKm, 1.2, 0.03) && near(en.energyWh, 24.0 * 20.0 * 60 / 3600, 0.5), "the footer: " + en.distanceKm + " km " + en.energyWh + " Wh");
            // the same numbers worked out from the rows of a log that lacks them
            FlightLog read = LogFormat.readFile(files.get(0), 1000000);
            double[] r = LogEfficiency.compute(read);
            check(Double.isNaN(r[0]) || near(r[0], 1.2, 0.05), "from the rows: " + r[0]);
        } finally {
            FlightLoggerTests.rm(dir);
        }
        FlightLog synth = new FlightLog();
        synth.columns = new String[]{"gps_speed", "vbat", "curr", "capa"};
        synth.isText = new boolean[4];
        synth.rows = 1001;
        synth.time = new long[1001];
        synth.data = new double[1001 * 4];
        for (int i = 0; i < 1001; i++) {
            synth.time[i] = i * 100L;
            synth.data[i * 4] = 36.0; synth.data[i * 4 + 1] = 12.0; synth.data[i * 4 + 2] = 10.0; synth.data[i * 4 + 3] = i * 0.5;
        }
        double[] sr = LogEfficiency.compute(synth);
        check(near(sr[0], 1.0, 0.001) && near(sr[1], 120.0 * 100 / 3600, 0.01) && sr[2] == 500.0, "a log's rows add up: " + sr[0] + " " + sr[1] + " " + sr[2]);
        FlightLog bare = new FlightLog();
        bare.columns = new String[]{"ch1"}; bare.isText = new boolean[1]; bare.rows = 3; bare.time = new long[]{0, 100, 200}; bare.data = new double[]{1, 2, 3};
        double[] br = LogEfficiency.compute(bare);
        check(Double.isNaN(br[0]) && Double.isNaN(br[1]) && Double.isNaN(br[2]), "a log without the columns gives nothing");
        LogStore.Entry open = new LogStore.Entry();
        check(!LogEfficiency.missing(open), "an entry that is not a clean log is left alone");
        LogEfficiency.fill(open);
        check(Double.isNaN(open.distanceKm), "and stays as it is");

        // ---------- the wiring, checked in the sources (touch, lifecycle and screens cannot run here) ----------
        String kt = "app/src/main/java/dev/ax12/announcer/";
        String act = new String(Files.readAllBytes(new File(kt + "MainActivity.kt").toPath()), "UTF-8");
        String ex = new String(Files.readAllBytes(new File(kt + "FlightExtras.kt").toPath()), "UTF-8");
        String sp = new String(Files.readAllBytes(new File(kt + "SettingsPanel.kt").toPath()), "UTF-8");
        int stat = act.indexOf("flightStats.update(numbers, sampleNow)"), tick = act.indexOf("extras.tick(sampleNow, numbers)"), logu = act.indexOf("flightLogger.update(sampleNow"), rules = act.indexOf("engine?.update(numbers");
        check(stat > 0 && tick > stat && tick < logu && tick < rules && act.indexOf("latestNumbers = numbers") > tick, "the extras see the numbers after the flight statistics, and before the logger, the rules and the cards");
        check(act.contains("extras.clock(now)") && act.contains("extras.onPause()") && act.contains("extras = FlightExtras(this, frame, speaker, flightStats)"), "the clock runs every tick, a pause counts the landing, and the extras exist from the start");
        int rl = act.indexOf("private fun reloadLogConfig()");
        check(rl > 0 && act.substring(rl, rl + 400).contains("extras.reload()"), "the settings are read again whenever the log settings are (screen opens, Settings changes)");
        check(act.contains("extras.packPanel.isShowing || extras.positionPanel.isShowing") && act.contains("else if (extras.packPanel.isShowing) extras.packPanel.hide()") && act.contains("else if (extras.positionPanel.isShowing) extras.positionPanel.hide()"), "the two panels count as open and close with the back key");
        check(act.contains("frame.addView(extras.packPanel.root") && act.contains("frame.addView(extras.positionPanel.root"), "and are on the screen");
        check(ex.contains("MIN_FLIGHT_S = 20") && ex.contains("REST_DELAY_MS = 4000L") && ex.contains("if (armed && pending != null) finish(numbers, false)"), "a landing counts from 20 seconds, waits 4 seconds for the rest voltage, and is counted at once if the aircraft arms again");
        check(ex.contains("LinkLoss.Event.LOST -> {") && ex.contains("onLost(wallMs)") && ex.contains("loss.isPending()") && ex.contains("now - lastNoteAt >= 5000") && ex.contains("saveNote(\"link lost\", fix, wallMs)"), "a loss is spoken and saved, the position is refreshed every 5 seconds in flight");
        check(ex.contains("Prefs.bool(activity, \"lossSpeech\", true)") && ex.contains("Prefs.bool(activity, \"debriefOn\", true)") && ex.contains("Prefs.bool(activity, \"winSpeech\", true)"), "each of the three voices has its switch");
        check(ex.contains("it.topMargin = dp(52)") && ex.contains("Gravity.TOP or Gravity.END"), "the chip sits under the MENU button");
        check(ex.contains("numbers[\"flown_km\"]") && ex.contains("numbers[\"energy_wh\"]") && ex.contains("numbers[\"wh_km\"]") && ex.contains("numbers[\"mah_km\"]") && ex.contains("numbers[\"win_left\"]"), "the derived values are put into the numbers");
        String cat = new String(Files.readAllBytes(new File(kt + "VariableCatalog.kt").toPath()), "UTF-8");
        for (String name : new String[]{"flown_km", "energy_wh", "wh_km", "mah_km", "win_left"}) check(cat.contains("\"" + name + "\" to tn("), "the catalog describes " + name);
        check(sp.contains("group(\"window\"") && sp.contains("group(\"after\"") && sp.indexOf("group(\"window\"") > sp.indexOf("group(\"logs\"") && sp.indexOf("group(\"after\"") < sp.indexOf("group(\"rules\""), "the two settings groups sit between the logs and the rules");
        String icons = new String(Files.readAllBytes(new File("app/src/main/assets/icons.txt").toPath()), "UTF-8");
        check(icons.contains("icon clock\n") && icons.contains("icon battery\n"), "the icons of the new groups exist");
        for (String key : new String[]{"winOn", "winStart", "winEnd", "winWarn", "winSpeech", "debriefOn", "lossSpeech", "packBook"}) check(SettingsFile.isKnown(key) && SettingsFile.defaultOf(key) != null, "the backup knows " + key);
        check(Integer.valueOf(960).equals(SettingsFile.defaultOf("winStart")) && Integer.valueOf(1080).equals(SettingsFile.defaultOf("winEnd")) && Boolean.FALSE.equals(SettingsFile.defaultOf("winOn")) && Boolean.TRUE.equals(SettingsFile.defaultOf("debriefOn")), "the defaults: off, 16:00 to 18:00; the debrief on");
        Map<String, Object> good = new LinkedHashMap<String, Object>();
        good.put("winWarn", "30,10,5"); good.put("packBook", "# AX-HUD pack book 1\nP|A|1|600|2900.000|120.000|11.800|3.400|1000\n"); good.put("winStart", 600); good.put("debriefOn", false);
        SettingsFile.Summary gs = SettingsFile.inspect(SettingsFile.export(good, "x", "y"));
        check(gs.usable && gs.ignored == 0 && gs.entries == 4, "good values are accepted: " + gs.entries + " entries, " + gs.ignored + " ignored");
        String badText = SettingsFile.export(good, "x", "y").replace("30,10,5", "0,99999").replace("P|A|1|600", "P|A|x|600").replace("winStart\t600", "winStart\t2000");
        SettingsFile.Summary bs = SettingsFile.inspect(badText);
        check(bs.ignored == 3, "wrong values are ignored, not taken: " + bs.ignored);

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
