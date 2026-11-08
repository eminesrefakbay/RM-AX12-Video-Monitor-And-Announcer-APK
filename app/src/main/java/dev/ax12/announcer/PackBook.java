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
 * The book of the packs: for each battery pack the flights, flying time, capacity, energy and distance it has added up to, its lowest
 * cell and its last flights. It is kept as one text in the settings (so that it is part of the backup) and does not depend on the flight
 * logs, which are pruned. Plain Java, so that it can be tested on a PC.
 */
public final class PackBook {
    public static final int MAX_PACKS = 16;
    public static final int KEEP_FLIGHTS = 10;
    public static final int MAX_TEXT = 100000;
    public static final String HEADER = "# AX-HUD pack book 1";

    /** One finished flight. A value that is not known is NaN. */
    public static final class Flight {
        public long wallMs;
        public int seconds;
        public double mah = Double.NaN, wh = Double.NaN, km = Double.NaN, minCell = Double.NaN, restCell = Double.NaN;
        public String aircraft = "";

        public Flight copy() {
            Flight f = new Flight();
            f.wallMs = wallMs;
            f.seconds = seconds;
            f.mah = mah;
            f.wh = wh;
            f.km = km;
            f.minCell = minCell;
            f.restCell = restCell;
            f.aircraft = aircraft;
            return f;
        }
    }

    /** The totals of one pack and its latest flights, the oldest first. */
    public static final class Entry {
        public String name = "";
        public int flights;
        public long seconds;
        public double mah, wh, km;
        public double minCell = Double.NaN;
        public long lastMs;
        public final List<Flight> recent = new ArrayList<Flight>();

        public double whPerKm() { return km >= FlightTotals.MIN_KM_FOR_EFFICIENCY && wh > 0 ? wh / km : Double.NaN; }
        public double mahPerKm() { return km >= FlightTotals.MIN_KM_FOR_EFFICIENCY && mah > 0 ? mah / km : Double.NaN; }
        public double avgMinutes() { return flights > 0 ? seconds / 60.0 / flights : Double.NaN; }

        /** How many full charges the pack has delivered, from the capacity it was rated for (NaN when that is not known). */
        public double cycles(int capacityMah) { return capacityMah > 0 ? mah / capacityMah : Double.NaN; }
    }

    private final List<Entry> entries = new ArrayList<Entry>();

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    public static String key(String pack) { return Packs.cleanName(pack); }

    public List<Entry> entries() {
        List<Entry> out = new ArrayList<Entry>(entries);
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return Long.compare(b.lastMs, a.lastMs); }
        });
        return out;
    }

    public int size() { return entries.size(); }
    public boolean isEmpty() { return entries.isEmpty(); }

    public Entry find(String pack) {
        String k = key(pack);
        for (Entry e : entries) if (e.name.equalsIgnoreCase(k)) return e;
        return null;
    }

    /** Adds a flight to a pack and returns the number of that flight on the pack (1 for the first). */
    public int add(String pack, Flight f) {
        String k = key(pack);
        if (k.isEmpty()) k = "-";
        Entry e = find(k);
        if (e == null) {
            if (entries.size() >= MAX_PACKS) {                  // the pack that has been flown longest ago makes room
                Entry oldest = entries.get(0);
                for (Entry x : entries) if (x.lastMs < oldest.lastMs) oldest = x;
                entries.remove(oldest);
            }
            e = new Entry();
            e.name = k;
            entries.add(e);
        }
        e.flights++;
        e.seconds += Math.max(0, f.seconds);
        if (ok(f.mah) && f.mah > 0) e.mah += f.mah;
        if (ok(f.wh) && f.wh > 0) e.wh += f.wh;
        if (ok(f.km) && f.km > 0) e.km += f.km;
        if (ok(f.minCell)) e.minCell = ok(e.minCell) ? Math.min(e.minCell, f.minCell) : f.minCell;
        e.lastMs = Math.max(e.lastMs, f.wallMs);
        e.recent.add(f.copy());
        while (e.recent.size() > KEEP_FLIGHTS) e.recent.remove(0);
        return e.flights;
    }

    public boolean remove(String pack) {
        Entry e = find(pack);
        return e != null && entries.remove(e);
    }

    public void clear() { entries.clear(); }

    // ------------------------------------------------------------------ the text

    private static String num(double v) { return ok(v) ? String.format(Locale.US, "%.3f", v) : ""; }

    private static double read(String s) {
        if (s == null || s.isEmpty()) return Double.NaN;
        try { double v = Double.parseDouble(s); return ok(v) ? v : Double.NaN; } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static String clean(String s) { return s == null ? "" : s.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim(); }

    public String encode() {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Entry e : entries) {
            sb.append("P|").append(clean(e.name)).append('|').append(e.flights).append('|').append(e.seconds).append('|').append(num(e.mah)).append('|')
                .append(num(e.wh)).append('|').append(num(e.km)).append('|').append(num(e.minCell)).append('|').append(e.lastMs).append('\n');
            for (Flight f : e.recent) {
                sb.append("F|").append(clean(e.name)).append('|').append(f.wallMs).append('|').append(f.seconds).append('|').append(num(f.mah)).append('|')
                    .append(num(f.wh)).append('|').append(num(f.km)).append('|').append(num(f.minCell)).append('|').append(num(f.restCell)).append('|')
                    .append(clean(f.aircraft)).append('\n');
            }
        }
        return sb.toString();
    }

    private static boolean parseInto(PackBook book, String text, boolean strict) {
        if (text == null) return true;
        if (text.length() > MAX_TEXT) return false;
        for (String line : text.split("\\r?\\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            try {
                if (p[0].equals("P") && p.length == 9) {
                    Entry e = new Entry();
                    e.name = key(p[1]);
                    if (e.name.isEmpty() || book.find(e.name) != null || book.entries.size() >= MAX_PACKS) throw new IllegalArgumentException();
                    e.flights = Integer.parseInt(p[2]);
                    e.seconds = Long.parseLong(p[3]);
                    e.mah = Math.max(0, ok(read(p[4])) ? read(p[4]) : 0);
                    e.wh = Math.max(0, ok(read(p[5])) ? read(p[5]) : 0);
                    e.km = Math.max(0, ok(read(p[6])) ? read(p[6]) : 0);
                    e.minCell = read(p[7]);
                    e.lastMs = Long.parseLong(p[8]);
                    if (e.flights < 0 || e.seconds < 0 || e.lastMs < 0) throw new IllegalArgumentException();
                    book.entries.add(e);
                } else if (p[0].equals("F") && p.length == 10) {
                    Entry e = book.find(p[1]);
                    if (e == null) throw new IllegalArgumentException();
                    Flight f = new Flight();
                    f.wallMs = Long.parseLong(p[2]);
                    f.seconds = Integer.parseInt(p[3]);
                    f.mah = read(p[4]);
                    f.wh = read(p[5]);
                    f.km = read(p[6]);
                    f.minCell = read(p[7]);
                    f.restCell = read(p[8]);
                    f.aircraft = clean(p[9]);
                    if (f.seconds < 0 || f.wallMs < 0) throw new IllegalArgumentException();
                    if (e.recent.size() < KEEP_FLIGHTS) e.recent.add(f);
                } else {
                    throw new IllegalArgumentException();
                }
            } catch (RuntimeException ex) {
                if (strict) return false;
            }
        }
        return true;
    }

    /** The book from its text; a line that does not make sense is skipped. */
    public static PackBook decode(String text) {
        PackBook b = new PackBook();
        parseInto(b, text, false);
        return b;
    }

    /** True when every line of the text makes sense (used when settings are read from a file). */
    public static boolean valid(String text) { return parseInto(new PackBook(), text, true); }

    // ------------------------------------------------------------------ text for the screen

    public static String durationText(long seconds) {
        long h = seconds / 3600, m = (seconds / 60) % 60;
        return h > 0 ? String.format(Locale.US, "%d h %02d min", h, m) : String.format(Locale.US, "%d min", m);
    }
}
