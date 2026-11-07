// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

/** Tests of the folder of flight logs: the summary of each, the order, and the cleaning up. */
public class LogStoreTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static void write(File f, String text) throws Exception { FileWriter w = new FileWriter(f); w.write(text); w.close(); }

    static final String HEAD = "# AX-HUD flight log\n# format: 1\n# started: 2026-11-07 14:55:33\n# aircraft: Akb7custom001\n# pack: Pack 3 (LiPo 6S, 3000 mAh)\n# text: fm\nt_ms,ch1,fm\n";

    public static void main(String[] args) throws Exception {
        File d = Files.createTempDirectory("axstore").toFile();

        // ---------- a log that was closed cleanly ----------
        File a = new File(d, "AX-HUD_2026-11-07_14-55-33_Akb7custom001.csv");
        write(a, HEAD + "0,1500,ANGLE\n100,1510,ANGLE\n200,1520,RTH\n# end: duration_ms=200, rows=3, dropped=0, max_height=130.6, max_dist=515, min_cell=3.44, max_speed=125.7, peak_current=56.8, mah=2605, \n");
        LogStore.Entry e = LogStore.summary(a);
        check(e.clean && e.durationMs == 200 && e.rows == 3, "a clean log: the duration and the rows come from the footer");
        check(e.maxHeight == 130.6 && e.maxDist == 515 && e.minCell == 3.44 && e.maxSpeed == 125.7 && e.peakCurrent == 56.8 && e.mah == 2605, "and the summary numbers");
        check(e.started.equals("2026-11-07 14:55:33") && e.aircraft.equals("Akb7custom001") && e.pack.startsWith("Pack 3"), "the facts from the header");
        check(e.size == a.length() && e.name.equals(a.getName()), "the size and the name");

        // ---------- a log that was cut off ----------
        File b = new File(d, "AX-HUD_2026-11-06_10-00-00_Akb7custom001.csv");
        write(b, HEAD + "0,1500,ANGLE\n100,1510,ANGLE\n200,1520,RTH\n300,15");
        e = LogStore.summary(b);
        check(!e.clean && e.rows == 3 && e.durationMs == 200, "a cut-off log: the rows are counted, the half row is not: " + e.rows + " rows");
        check(Double.isNaN(e.maxHeight) && Double.isNaN(e.mah), "and the summary numbers are not known");

        // ---------- something broken ----------
        File junk = new File(d, "AX-HUD_2026-11-05_09-00-00.csv");
        FileOutputStream o = new FileOutputStream(junk); o.write(new byte[]{0, 1, 2, (byte) 255, (byte) 254, 10, 0, 0}); o.close();
        boolean threw = false;
        try { e = LogStore.summary(junk); } catch (RuntimeException ex) { threw = true; }
        check(!threw && e.rows == 0, "a broken file is listed, not a crash");
        File empty = new File(d, "AX-HUD_2026-11-04_09-00-00.csv");
        empty.createNewFile();
        try { e = LogStore.summary(empty); threw = false; } catch (RuntimeException ex) { threw = true; }
        check(!threw && e.rows == 0 && e.size == 0, "an empty file is listed");

        // ---------- the list ----------
        write(new File(d, "notes.txt"), "x");
        write(new File(d, "AX-HUD_2026-11-03.txt"), "x");
        write(new File(d, "other.csv"), "x");
        List<LogStore.Entry> list = LogStore.list(d);
        check(list.size() == 4, "only the logs are listed: " + list.size());
        check(list.get(0).name.equals(a.getName()) && list.get(1).name.equals(b.getName()), "the newest first");
        check(LogStore.totalBytes(d) == a.length() + b.length() + junk.length() + empty.length(), "the total size of the logs");
        check(LogStore.list(new File(d, "nothing")).isEmpty() && LogStore.files(new File(d, "nothing")).isEmpty(), "a folder that does not exist: an empty list");

        // ---------- cleaning up ----------
        check(LogStore.prune(d, 2) == 2 && LogStore.files(d).size() == 2, "keep the newest two");
        check(LogStore.files(d).get(0).equals(a) && b.exists() && !junk.exists(), "the older ones are the ones that go");
        check(new File(d, "notes.txt").exists() && new File(d, "other.csv").exists(), "files that are not logs stay");
        check(!LogStore.delete(new File(d, "notes.txt")) && new File(d, "notes.txt").exists(), "delete refuses a file that is not a log");
        check(LogStore.delete(b) && !b.exists(), "delete removes one log");
        check(LogStore.deleteAll(d) == 1 && LogStore.files(d).isEmpty() && new File(d, "notes.txt").exists(), "delete all removes the logs and nothing else");
        check(LogStore.prune(d, 5) == 0, "nothing to prune in an empty folder");

        for (File f : d.listFiles()) f.delete();
        d.delete();
        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
