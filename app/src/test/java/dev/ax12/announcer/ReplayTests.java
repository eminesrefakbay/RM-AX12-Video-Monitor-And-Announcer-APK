// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests of the replay clock, and one flight that is logged and then replayed into fresh flight statistics. */
public class ReplayTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static FlightLog small() throws Exception {
        String s = "# text: fm\n# cells: 6\nt_ms,ch5,vbat,fm\n0,2000,24.5,ANGLE\n100,2000,24.4,ANGLE\n200,2000,,RTH\n1000,2000,24.0,\n";
        return LogFormat.read(new BufferedReader(new StringReader(s)), 1_000_000);
    }

    public static void main(String[] args) throws Exception {
        // ---------- the clock ----------
        ReplayEngine r = new ReplayEngine(small());
        check(r.durationMs() == 1000 && r.positionMs() == 0 && !r.playing() && r.speed() == 1, "a new replay: at the start, paused, normal speed");
        r.advance(500);
        check(r.positionMs() == 0, "paused: the position does not move");
        r.play();
        r.advance(300);
        check(r.positionMs() == 300 && r.playing(), "playing: 300 ms later it is at 300");
        r.cycleSpeed();
        check(r.speed() == 2, "the second speed is 2x");
        r.advance(200);
        check(r.positionMs() == 700, "at 2x, 200 ms of real time are 400 ms of flight");
        r.cycleSpeed(); r.cycleSpeed();
        check(r.speed() == 8, "then 4x and 8x");
        r.cycleSpeed();
        check(r.speed() == 1, "and round to 1x again");
        r.advance(10_000);
        check(r.positionMs() == 1000 && !r.playing() && r.atEnd(), "at the end it stops");
        r.advance(500);
        check(r.positionMs() == 1000, "and stays there");
        r.play();
        check(r.positionMs() == 0 && r.playing(), "play at the end starts again from the beginning");
        r.toggle();
        check(!r.playing(), "toggle pauses");
        r.toggle();
        check(r.playing(), "and plays");
        r.seek(5000);
        check(r.positionMs() == 1000, "seeking past the end stops at the end");
        r.seek(-20);
        check(r.positionMs() == 0, "seeking before the start stops at the start");
        r.seek(450);
        check(r.positionMs() == 450, "seek");
        r.advance(-100);
        check(r.positionMs() == 450, "time does not run backwards");

        // ---------- rows and values ----------
        check(r.rowAt(-5) == 0 && r.rowAt(0) == 0 && r.rowAt(50) == 0 && r.rowAt(100) == 1 && r.rowAt(150) == 1 && r.rowAt(200) == 2 && r.rowAt(999) == 2 && r.rowAt(1000) == 3 && r.rowAt(99999) == 3, "the row at a position is the last one at or before it");
        check(r.currentRow() == 2, "the current row at 450 ms");
        Map<String, Double> nums = new HashMap<String, Double>();
        Map<String, String> txts = new HashMap<String, String>();
        r.fill(0, nums, txts);
        check(nums.get("ch5") == 2000 && nums.get("vbat") == 24.5 && "ANGLE".equals(txts.get("fm")) && nums.size() == 2 && txts.size() == 1, "a row's values");
        r.fill(2, nums, txts);
        check(!nums.containsKey("vbat") && "RTH".equals(txts.get("fm")), "a value that was not there is left out, not zero");
        r.fill(3, nums, txts);
        check(txts.isEmpty() && nums.get("vbat") == 24.0, "an empty text is left out");
        r.fill(99, nums, txts);
        check(nums.isEmpty() && txts.isEmpty(), "a row that does not exist: nothing");
        ReplayEngine none = new ReplayEngine(LogFormat.read(new BufferedReader(new StringReader("t_ms,a\n")), 100));
        check(none.rowAt(0) == -1 && none.durationMs() == 0 && none.atEnd(), "a log without rows");
        none.play();
        none.advance(100);
        check(!none.playing(), "and it cannot play");

        // ---------- feeding rows ----------
        final List<Long> clock = new ArrayList<Long>();
        r.feed(0, 3, new ReplayEngine.Sink() { public void accept(long now, Map<String, Double> n, Map<String, String> t) { clock.add(now); } });
        check(clock.size() == 4 && clock.get(0) == ReplayEngine.CLOCK_BASE && clock.get(3) == ReplayEngine.CLOCK_BASE + 1000, "feeding rows 0 to 3: four rows");
        boolean up = true;
        for (int i = 1; i < clock.size(); i++) if (clock.get(i) <= clock.get(i - 1)) up = false;
        check(up && ReplayEngine.CLOCK_BASE > 0, "the replay clock only grows and is never zero");
        clock.clear();
        r.feed(2, 99, new ReplayEngine.Sink() { public void accept(long now, Map<String, Double> n, Map<String, String> t) { clock.add(now); } });
        check(clock.size() == 2, "rows past the end are not fed");
        check(r.rowForWindow(900, 300) == 2 && r.rowForWindow(100, 300) == 0, "where a rolling chart starts when it is rebuilt");

        // ---------- a whole flight: logged, read back and replayed ----------
        File dir = Files.createTempDirectory("axreplay").toFile();
        FlightLogger.Wall wall = new FlightLogger.Wall() { public long now() { return 1794000000000L; } };
        FlightLogger.MetaSource meta = new FlightLogger.MetaSource() {
            public Map<String, String> get() { Map<String, String> m = new java.util.LinkedHashMap<String, String>(); m.put("aircraft", "Test"); m.put("cells", "6"); m.put("arm_ch", "5"); m.put("arm_high", "1750"); m.put("arm_low", "1650"); return m; }
        };
        FlightLogger lg = new FlightLogger(dir, wall, meta);
        lg.configure(true, 3, LogFields.replaySet(), 1);
        FlightStats live = new FlightStats();
        live.configure(5, 1750, 1650, 6);
        double lat0 = 36.8, lon0 = 34.6;
        long t = 0;
        for (int i = 0; i < 700; i++, t += 100) {                         // 70 s: 10 s on the ground, 50 s of flight, 10 s down
            double sec = i / 10.0;
            boolean armed = sec >= 2 && sec < 68;
            double fly = Math.max(0, Math.min(50, sec - 12));             // seconds in the air
            double north = fly * 8 + (fly > 25 ? (fly - 25) * 3 : 0);
            double east = fly > 20 ? Math.sin((fly - 20) / 6.0) * 60 : 0;
            double height = Math.min(90, fly * 6);
            Map<String, Double> n = new HashMap<String, Double>();
            n.put("ch1", 1500.0 + (i % 40)); n.put("ch2", 1500.0); n.put("ch3", 1200.0 + fly * 10); n.put("ch4", 1500.0);
            n.put("ch5", armed ? 2000.0 : 1000.0);
            n.put("vbat", 25.2 - sec * 0.02); n.put("curr", 6.0 + (fly > 0 ? 10 + (i % 7) : 0)); n.put("capa", sec * 30);
            n.put("up_lq", 100.0 - (i % 13)); n.put("sats", 17.0);
            n.put("gps_lat", lat0 + north / 111320.0); n.put("gps_lon", lon0 + east / (111320.0 * Math.cos(Math.toRadians(lat0))));
            n.put("gps_alt", 20 + height); n.put("gps_speed", fly > 0 ? 28.0 + (i % 9) : 0.0); n.put("yaw", (sec * 5) % 360);
            Map<String, String> tx = new HashMap<String, String>();
            tx.put("fm", fly > 40 ? "RTH" : "ANGLE");
            live.update(n, t);
            if (live.armed()) {
                if (!Double.isNaN(live.heightNow())) n.put("height", live.heightNow());
                if (!Double.isNaN(live.distanceNow())) n.put("dist", live.distanceNow());
            }
            lg.update(t, live.armed(), n, tx);
        }
        lg.close();
        List<File> files = LogStore.files(dir);
        check(files.size() == 1, "one flight, one file");
        FlightLog log = LogFormat.readFile(files.get(0), 4_000_000);
        check(log.rows > 600 && log.rows < 700, "the flight is in the file: " + log.rows + " rows");
        check(log.indexOf("ch5") >= 0 && log.indexOf("gps_lat") >= 0 && log.indexOf("fm") >= 0, "with the columns a replay needs");

        ReplayEngine eng = new ReplayEngine(log);
        final FlightStats rs = new FlightStats();
        rs.configure((int) log.metaNumber("arm_ch", 5), log.metaNumber("arm_high", 1750), log.metaNumber("arm_low", 1650), (int) log.metaNumber("cells", 6));
        eng.feed(0, log.rows - 1, new ReplayEngine.Sink() { public void accept(long now, Map<String, Double> n, Map<String, String> tx) { rs.update(n, now); } });
        check(rs.hasHome() && live.hasHome(), "both have a home point");
        check(Math.abs(rs.maxDistance() - live.maxDistance()) < 1.0, "the farthest distance is the same: live " + live.maxDistance() + ", replay " + rs.maxDistance());
        check(Math.abs(rs.maxHeight() - live.maxHeight()) < 0.5, "the highest point is the same: live " + live.maxHeight() + ", replay " + rs.maxHeight());
        check(rs.maxSpeed() == live.maxSpeed() && rs.peakCurrent() == live.peakCurrent(), "top speed and peak current are the same");
        check(Math.abs(rs.minCellVolts() - live.minCellVolts()) < 0.01 && rs.minLinkQuality() == live.minLinkQuality(), "lowest cell and lowest link are the same");
        check(Math.abs(rs.trailSize() - live.trailSize()) <= 2, "the track has the same length: live " + live.trailSize() + ", replay " + rs.trailSize());
        check(Math.abs(rs.eastM() - live.eastM()) < 1.0 && Math.abs(rs.northM() - live.northM()) < 1.0, "the last position is the same");
        check(rs.armed() && Math.abs(rs.armedMs(ReplayEngine.clock(log.durationMs())) - 66000L) < 1500, "the replay knows the flight lasted about 66 seconds: " + rs.armedMs(ReplayEngine.clock(log.durationMs())));
        // the flight mode changes to RTH late in the flight
        int rowEarly = eng.rowAt(10_000), rowLate = eng.rowAt(60_000);
        Map<String, Double> nn = new HashMap<String, Double>();
        Map<String, String> tt = new HashMap<String, String>();
        eng.fill(rowEarly, nn, tt);
        String early = tt.get("fm");
        eng.fill(rowLate, nn, tt);
        check("ANGLE".equals(early) && "RTH".equals(tt.get("fm")), "the flight mode is replayed: " + early + " then " + tt.get("fm"));
        // seeking: building the statistics up to a position gives the same as playing up to it
        final FlightStats mid = new FlightStats();
        mid.configure(5, 1750, 1650, 6);
        eng.feed(0, eng.rowAt(30_000), new ReplayEngine.Sink() { public void accept(long now, Map<String, Double> n, Map<String, String> tx) { mid.update(n, now); } });
        check(mid.maxDistance() < rs.maxDistance() && mid.maxDistance() > 50 && mid.trailSize() < rs.trailSize(), "stats at 30 s are partway: " + mid.maxDistance() + " m of " + rs.maxDistance());
        for (File f : dir.listFiles()) f.delete();
        dir.delete();

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
