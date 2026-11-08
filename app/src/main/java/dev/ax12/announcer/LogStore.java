// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The folder of flight logs: the list with a short summary of each, and the cleaning up. Plain Java. */
public final class LogStore {
    private LogStore() {}

    public static final String PREFIX = "AX-HUD_";

    /** What the flight list shows about one log. Numbers that are not known are NaN. */
    public static final class Entry {
        public File file;
        public String name = "", started = "", aircraft = "", pack = "";
        public long size, durationMs;
        public int rows;
        public boolean clean;
        public double maxHeight = Double.NaN, maxDist = Double.NaN, minCell = Double.NaN, maxSpeed = Double.NaN, peakCurrent = Double.NaN, mah = Double.NaN;
        public double distanceKm = Double.NaN, energyWh = Double.NaN;
    }

    public static boolean isLog(File f) {
        String n = f.getName();
        return f.isFile() && n.startsWith(PREFIX) && n.endsWith(".csv");
    }

    /** The log files, the newest first (the name starts with the date and time). */
    public static List<File> files(File dir) {
        List<File> out = new ArrayList<File>();
        File[] all = dir.listFiles();
        if (all == null) return out;
        for (File f : all) if (isLog(f)) out.add(f);
        Collections.sort(out, Collections.reverseOrder());
        return out;
    }

    private static double num(String s) {
        try { return s == null || s.isEmpty() ? Double.NaN : Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** A summary from the first lines and the last lines of the file; a log that was cut off is counted once. */
    public static Entry summary(File f) {
        Entry e = new Entry();
        e.file = f;
        e.name = f.getName();
        e.size = f.length();
        FlightLog head = new FlightLog();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                int n = 0;
                while ((line = br.readLine()) != null && n++ < 80) {
                    if (line.startsWith("# ")) {
                        String body = line.substring(2);
                        int colon = body.indexOf(':');
                        if (colon > 0 && !body.startsWith("end:")) head.meta.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                    } else if (!line.startsWith("#")) break;
                }
            } finally { br.close(); }
        } catch (IOException ex) { /* an unreadable file is listed by its name */ }
        e.started = head.meta("started", "");
        e.aircraft = head.meta("aircraft", "");
        e.pack = head.meta("pack", "");

        // the footer: the last lines
        try {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                int len = (int) Math.min(raf.length(), 4096);
                byte[] tail = new byte[len];
                raf.seek(raf.length() - len);
                raf.readFully(tail);
                String[] lines = new String(tail, "UTF-8").split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i].startsWith("# end:")) {
                        FlightLog t = new FlightLog();
                        for (String part : lines[i].substring(6).split(",")) {
                            int eq = part.indexOf('=');
                            if (eq > 0) t.end.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                        }
                        e.clean = true;
                        e.durationMs = (long) num(t.end.get("duration_ms"));
                        e.rows = (int) num(t.end.get("rows"));
                        e.maxHeight = num(t.end.get("max_height"));
                        e.maxDist = num(t.end.get("max_dist"));
                        e.minCell = num(t.end.get("min_cell"));
                        e.maxSpeed = num(t.end.get("max_speed"));
                        e.peakCurrent = num(t.end.get("peak_current"));
                        e.mah = num(t.end.get("mah"));
                        e.distanceKm = num(t.end.get("distance_km"));
                        e.energyWh = num(t.end.get("energy_wh"));
                        break;
                    }
                }
            } finally { raf.close(); }
        } catch (IOException ex) { /* see below */ }
        if (!e.clean) {                                      // cut off: count the rows once
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 65536);
                try {
                    String line;
                    boolean header = false;
                    int commas = 0;
                    while ((line = br.readLine()) != null) {
                        if (line.isEmpty() || line.charAt(0) == '#') continue;
                        if (!header) {
                            header = true;
                            for (int i = 0; i < line.length(); i++) if (line.charAt(i) == ',') commas++;
                            continue;
                        }
                        int here = 0;
                        for (int i = 0; i < line.length(); i++) if (line.charAt(i) == ',') here++;
                        int comma = line.indexOf(',');
                        if (comma <= 0 || here < commas) continue;                 // a half row at the bottom of a cut-off file
                        try { e.durationMs = Long.parseLong(line.substring(0, comma)); e.rows++; } catch (NumberFormatException ex) { /* not a row */ }
                    }
                } finally { br.close(); }
            } catch (IOException ex) { /* leave the zeros */ }
        }
        return e;
    }

    public static List<Entry> list(File dir) {
        List<Entry> out = new ArrayList<Entry>();
        for (File f : files(dir)) out.add(summary(f));
        return out;
    }

    /** Keeps the newest {@code keep} logs and deletes the rest. Returns how many were deleted. */
    public static int prune(File dir, int keep) {
        List<File> all = files(dir);
        int deleted = 0;
        for (int i = Math.max(0, keep); i < all.size(); i++) if (all.get(i).delete()) deleted++;
        return deleted;
    }

    public static boolean delete(File f) { return isLog(f) && f.delete(); }

    public static int deleteAll(File dir) {
        int n = 0;
        for (File f : files(dir)) if (f.delete()) n++;
        return n;
    }

    public static long totalBytes(File dir) {
        long n = 0;
        for (File f : files(dir)) n += f.length();
        return n;
    }
}
