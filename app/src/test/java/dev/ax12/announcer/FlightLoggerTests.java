// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests of the flight logger: when it logs, how often, what it writes, and that nothing it meets can reach the flight. */
public class FlightLoggerTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static File tmp() throws Exception { return Files.createTempDirectory("axlog").toFile(); }
    static void rm(File d) { File[] l = d.listFiles(); if (l != null) for (File f : l) f.delete(); d.delete(); }
    static final FlightLogger.Wall WALL = new FlightLogger.Wall() { public long now() { return 1794000000000L; } };
    static final FlightLogger.MetaSource META = new FlightLogger.MetaSource() {
        public Map<String, String> get() {
            Map<String, String> m = new LinkedHashMap<String, String>();
            m.put("app", "AX-HUD 1.1.0"); m.put("aircraft", "Akb7custom001"); m.put("pack", "Pack 3 (LiPo 6S, 3000 mAh)"); m.put("cells", "6");
            m.put("arm_ch", "5"); m.put("arm_high", "1750"); m.put("arm_low", "1650");
            return m;
        }
    };

    static Map<String, Double> nums(double ch5, double vbat, double lat, double lon) {
        Map<String, Double> m = new HashMap<String, Double>();
        m.put("ch1", 1500.0); m.put("ch5", ch5); m.put("vbat", vbat); m.put("gps_lat", lat); m.put("gps_lon", lon);
        return m;
    }

    /** Ticks every stepMs for totalMs, armed; returns the next time. */
    static long run(FlightLogger lg, long start, long totalMs, int stepMs, boolean armed) {
        Map<String, String> texts = new HashMap<String, String>();
        texts.put("fm", "ANGLE");
        long t = start;
        for (; t < start + totalMs; t += stepMs) lg.update(t, armed, nums(armed ? 2000 : 1000, 24.5, 36.8, 34.6), texts);
        return t;
    }

    static List<File> logs(File d) { return LogStore.files(d); }

    public static void main(String[] args) throws Exception {
        // ---------- off: nothing is written ----------
        File d = tmp();
        FlightLogger lg = new FlightLogger(d, WALL, META);
        lg.configure(false, 3, null, 1);
        run(lg, 1000, 2000, 20, true);
        check(logs(d).isEmpty() && !lg.isLogging(), "logging off: no file");
        rm(d);

        // ---------- on: from arming to disarming ----------
        d = tmp();
        lg = new FlightLogger(d, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1", "vbat", "gps_lat", "fm"), 1);
        long t = run(lg, 1000, 500, 20, false);
        check(logs(d).isEmpty() && !lg.isLogging(), "not armed: no file yet");
        t = run(lg, t, 3000, 20, true);
        check(lg.isLogging() && logs(d).size() == 1, "armed: a file is open");
        lg.update(t, false, nums(1000, 24.5, 36.8, 34.6), new HashMap<String, String>());
        check(!lg.isLogging(), "disarmed: the file is closed");
        File f = logs(d).get(0);
        check(f.getName().matches("AX-HUD_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_Akb7custom001\\.csv"), "the file name has the date, the time and the aircraft: " + f.getName());
        FlightLog log = LogFormat.readFile(f, 1_000_000);
        check(Arrays.equals(log.columns, new String[]{"ch1", "vbat", "gps_lat", "fm"}), "only the chosen columns: " + Arrays.toString(log.columns));
        check(log.rows >= 29 && log.rows <= 31, "10 Hz for 3 seconds is about 30 rows: " + log.rows);
        check(log.rows == lg.rows(), "the file holds the rows the logger counted");
        check(log.time[0] == 0 && log.durationMs() >= 2900 && log.durationMs() <= 3000, "the time starts at arming: " + log.durationMs());
        check(log.isText[3] && "ANGLE".equals(log.word(0, 3)), "the flight mode is a text column");
        check(log.value(0, 1) == 24.5 && Math.abs(log.value(0, 2) - 36.8) < 1e-9, "the values");
        check(log.meta("app", "").equals("AX-HUD 1.1.0") && log.meta("pack", "").startsWith("Pack 3") && log.meta("rate_hz", "").equals("10") && log.meta("format", "").equals("1") && log.meta("started", "").length() == 19, "the header facts");
        check(log.meta("note", "").contains("GPS positions"), "the file says that it holds positions");
        check(!log.end.isEmpty() && log.end.get("rows").equals(Integer.toString(log.rows)) && log.end.get("dropped").equals("0"), "the footer: " + log.end);
        rm(d);

        // ---------- the rates ----------
        int[] hz = LogFlightRates();
        for (int ri = 0; ri < 5; ri++) {
            d = tmp();
            lg = new FlightLogger(d, WALL, META);
            lg.configure(true, ri, Arrays.asList("ch1"), 1);
            t = run(lg, 0, 4000, 10, true);
            lg.update(t, false, nums(1000, 0, 0, 0), new HashMap<String, String>());
            log = LogFormat.readFile(logs(d).get(0), 1_000_000);
            int want = hz[ri] * 4;
            check(Math.abs(log.rows - want) <= Math.max(2, want / 10), hz[ri] + " Hz for 4 seconds is about " + want + " rows: " + log.rows);
            rm(d);
        }

        // ---------- two flights ----------
        d = tmp();
        lg = new FlightLogger(d, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1"), 1);
        t = run(lg, 0, 500, 20, true);
        t = run(lg, t, 20, 20, false);
        t = run(lg, t, 500, 20, true);
        run(lg, t, 20, 20, false);
        check(logs(d).size() == 2 && !logs(d).get(0).getName().equals(logs(d).get(1).getName()), "two flights, two files, even in the same second: " + logs(d));
        rm(d);

        // ---------- switched off in the middle of a flight ----------
        d = tmp();
        lg = new FlightLogger(d, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1"), 1);
        t = run(lg, 0, 1000, 20, true);
        lg.configure(false, 3, Arrays.asList("ch1"), 1);
        lg.update(t, true, nums(2000, 24.5, 1, 1), new HashMap<String, String>());
        check(!lg.isLogging(), "switched off in flight: the log is closed");
        log = LogFormat.readFile(logs(d).get(0), 1_000_000);
        check(!log.end.isEmpty() && log.rows > 5, "and it is complete");
        rm(d);

        // ---------- the summary comes from every value ----------
        d = tmp();
        lg = new FlightLogger(d, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1"), 1);                    // none of the summary values is chosen
        for (int i = 0; i < 50; i++) {
            Map<String, Double> m = nums(2000, 24, 1, 1);
            m.put("height", 10.0 + i); m.put("dist", 5.0 * i); m.put("cell", 4.1 - i * 0.01); m.put("gps_speed", 20.0 + i % 7); m.put("curr", 8.0 + i % 11); m.put("capa", 20.0 * i);
            lg.update(i * 100L, true, m, new HashMap<String, String>());
        }
        lg.update(6000, false, nums(1000, 0, 0, 0), new HashMap<String, String>());
        log = LogFormat.readFile(logs(d).get(0), 1_000_000);
        check(log.end.get("max_height").equals("59") && log.end.get("max_dist").equals("245") && log.end.get("min_cell").equals("3.61") && log.end.get("max_speed").equals("26") && log.end.get("peak_current").equals("18") && log.end.get("mah").equals("980"), "the footer holds the summary: " + log.end);
        rm(d);

        // ---------- values that are not there are empty ----------
        d = tmp();
        lg = new FlightLogger(d, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1", "gps_alt", "tx_power"), 1);
        t = run(lg, 0, 300, 20, true);
        lg.update(t, false, nums(1000, 0, 0, 0), new HashMap<String, String>());
        log = LogFormat.readFile(logs(d).get(0), 1_000_000);
        check(log.value(0, 0) == 1500 && Double.isNaN(log.value(0, 1)) && Double.isNaN(log.value(0, 2)), "a value that is not published is an empty field");
        rm(d);

        // ---------- the old logs are cleaned up ----------
        d = tmp();
        for (int i = 1; i <= 25; i++) { FileWriter w = new FileWriter(new File(d, String.format("AX-HUD_2020-01-%02d_10-00-00.csv", i))); w.write("t_ms\n"); w.close(); }
        new FileWriter(new File(d, "notes.txt")).close();
        lg = new FlightLogger(d, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1"), 0);                      // keep 20
        t = run(lg, 0, 500, 20, true);
        lg.update(t, false, nums(1000, 0, 0, 0), new HashMap<String, String>());
        check(logs(d).size() == 20, "only the newest 20 are kept: " + logs(d).size());
        check(logs(d).get(0).getName().startsWith("AX-HUD_20") && !logs(d).get(0).getName().startsWith("AX-HUD_2020"), "and the new one is among them");
        check(new File(d, "notes.txt").exists(), "files that are not logs are left alone");
        rm(d);

        // ---------- a folder that cannot be made: nothing reaches the flight ----------
        d = tmp();
        File notADir = new File(d, "flights");
        new FileWriter(notADir).close();                                    // a file where the folder should be
        lg = new FlightLogger(notADir, WALL, META);
        lg.configure(true, 3, Arrays.asList("ch1"), 1);
        boolean threw = false;
        try { run(lg, 0, 500, 20, true); } catch (RuntimeException e) { threw = true; }
        check(!threw && !lg.isLogging() && !lg.problem().isEmpty(), "a folder that cannot be made: no exception, no log, a problem text: " + lg.problem());
        lg.configure(false, 3, null, 1);
        check(lg.problem().isEmpty(), "switching logging off clears the problem");
        rm(d);

        // ---------- a writer that cannot keep up: rows are dropped and counted ----------
        d = tmp();
        lg = new FlightLogger(d, WALL, META, 64, false);                     // a small queue and no writer thread at all
        lg.configure(true, 4, Arrays.asList("ch1", "vbat"), 1);
        long t0 = System.nanoTime();
        t = run(lg, 0, 10000, 10, true);
        long took = (System.nanoTime() - t0) / 1_000_000;
        check(lg.dropped() > 0 && lg.rows() > 0, "rows are dropped and counted: " + lg.rows() + " kept, " + lg.dropped() + " dropped");
        check(took < 2000, "and the caller was never held up (" + took + " ms for 1000 ticks)");
        lg.update(t, false, nums(1000, 0, 0, 0), new HashMap<String, String>());
        log = LogFormat.readFile(logs(d).get(0), 1_000_000);
        check(log.rows == lg.rows() && log.rows > 0, "the file holds the rows that were accepted: " + log.rows);
        rm(d);

        // ---------- a name with odd characters ----------
        d = tmp();
        FlightLogger.MetaSource odd = new FlightLogger.MetaSource() { public Map<String, String> get() { Map<String, String> m = new LinkedHashMap<String, String>(); m.put("aircraft", "My/quad: <v2> \u00FC"); return m; } };
        lg = new FlightLogger(d, WALL, odd);
        lg.configure(true, 3, Arrays.asList("ch1"), 1);
        t = run(lg, 0, 200, 20, true);
        lg.update(t, false, nums(1000, 0, 0, 0), new HashMap<String, String>());
        check(logs(d).size() == 1 && logs(d).get(0).getName().matches("AX-HUD_[0-9_-]+_Myquadv2\\.csv"), "odd characters stay out of the file name: " + logs(d));
        rm(d);

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    static int[] LogFlightRates() { return FlightLogger.RATES_HZ; }
}
