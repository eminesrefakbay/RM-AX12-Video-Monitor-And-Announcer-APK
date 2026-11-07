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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The file format of a flight log: plain CSV that a spreadsheet opens directly.
 *
 * <pre>
 * # AX-HUD flight log
 * # format: 1
 * # aircraft: Akb7custom001        (and more "# key: value" lines: app, started, pack, cells, rate_hz, arm_ch ...)
 * # text: fm                      (the columns that hold words, not numbers)
 * t_ms,ch1,ch2,...,fm
 * 0,1500,1500,...,ANGLE
 * 100,...
 * # end: duration_ms=..., rows=..., max_height=...
 * </pre>
 *
 * A file that was cut off (no "# end" line, a half row at the bottom) is still read.
 */
public final class LogFormat {
    private LogFormat() {}

    public static final int VERSION = 1;
    public static final String TIME_COLUMN = "t_ms";

    // ------------------------------------------------------------------ writing

    /** A number as short text: whole numbers without a point, positions with seven decimals, the rest with up to three. */
    public static String fmt(String column, double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        if (column.equals("gps_lat") || column.equals("gps_lon")) return String.format(Locale.US, "%.7f", v);
        double r = Math.rint(v * 1000.0) / 1000.0;
        if (r == 0) return "0";
        if (r == Math.rint(r) && Math.abs(r) < 1e12) return Long.toString((long) r);
        String s = String.format(Locale.US, "%.3f", r);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /** Text as a CSV field: in quotes when it holds a comma, a quote or a line break. */
    public static String quote(String s) {
        if (s == null) return "";
        boolean need = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ',' || c == '"' || c == '\n' || c == '\r') { need = true; break; }
        }
        if (!need) return s;
        return '"' + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    public static String metaLine(String key, String value) {
        String v = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return "# " + key + ": " + v;
    }

    public static String headerRow(String[] columns) {
        StringBuilder sb = new StringBuilder(TIME_COLUMN);
        for (String c : columns) sb.append(',').append(c);
        return sb.toString();
    }

    /** One data row. A value that is not there (NaN, or no text) is an empty field. */
    public static String row(long timeMs, String[] columns, boolean[] isText, double[] numbers, String[] texts) {
        StringBuilder sb = new StringBuilder(columns.length * 6 + 16);
        sb.append(timeMs);
        for (int i = 0; i < columns.length; i++) {
            sb.append(',');
            if (isText[i]) sb.append(texts[i] == null ? "" : quote(texts[i]));
            else sb.append(fmt(columns[i], numbers[i]));
        }
        return sb.toString();
    }

    public static String endLine(Map<String, String> values) {
        StringBuilder sb = new StringBuilder("# end: ");
        boolean first = true;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ reading

    /** Splits one CSV line; a field in quotes may hold commas and doubled quotes. */
    public static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = false;
                } else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }

    public static FlightLog readFile(File f, int maxCells) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"), 65536);
        try { return read(br, maxCells); } finally { br.close(); }
    }

    /**
     * Reads a log. A very long log (more than maxCells numbers) is thinned out, every second row, again and again, until it fits.
     */
    public static FlightLog read(BufferedReader br, int maxCells) throws IOException {
        FlightLog log = new FlightLog();
        int ncols = 0, tcount = 0, cap = 256, kept = 0, seen = 0, stride = 1;
        long[] time = new long[cap];
        double[] data = new double[cap * 1];
        String[][] words = null;
        boolean header = false;
        String line;
        while ((line = br.readLine()) != null) {
            if (line.isEmpty()) continue;
            if (line.charAt(0) == '#') {
                String body = line.length() > 1 && line.charAt(1) == ' ' ? line.substring(2) : line.substring(1);
                if (body.startsWith("end:")) {
                    for (String part : body.substring(4).split(",")) {
                        int eq = part.indexOf('=');
                        if (eq > 0) log.end.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                    }
                } else {
                    int colon = body.indexOf(':');
                    if (colon > 0) log.meta.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                }
                continue;
            }
            if (!header) {
                List<String> h = splitCsv(line);
                if (h.isEmpty() || !h.get(0).trim().equals(TIME_COLUMN)) throw new IOException("not a flight log: no t_ms column");
                ncols = h.size() - 1;
                log.columns = new String[ncols];
                log.isText = new boolean[ncols];
                List<String> textNames = Arrays.asList(log.meta("text", "").split(","));
                for (int i = 0; i < ncols; i++) {
                    log.columns[i] = h.get(i + 1).trim();
                    log.isText[i] = textNames.contains(log.columns[i]);
                    if (log.isText[i]) tcount++;
                }
                data = new double[cap * Math.max(1, ncols)];
                if (tcount > 0) words = new String[cap][];
                header = true;
                continue;
            }
            List<String> f = splitCsv(line);
            if (f.size() != ncols + 1) continue;               // a half row at the bottom of a cut-off file
            long t;
            try { t = Long.parseLong(f.get(0).trim()); } catch (NumberFormatException e) { continue; }
            if (seen++ % stride != 0) continue;
            if (kept == cap) {
                if ((long) cap * 2 * Math.max(1, ncols) > maxCells) {                // full: keep every second row, and take every second from now on
                    int k = 0;
                    for (int r = 0; r < kept; r += 2) {
                        time[k] = time[r];
                        System.arraycopy(data, r * ncols, data, k * ncols, ncols);
                        if (words != null) words[k] = words[r];
                        k++;
                    }
                    kept = k;
                    stride *= 2;
                    if ((seen - 1) % stride != 0) continue;
                } else {
                    cap *= 2;
                    time = Arrays.copyOf(time, cap);
                    data = Arrays.copyOf(data, cap * Math.max(1, ncols));
                    if (words != null) words = Arrays.copyOf(words, cap);
                }
            }
            time[kept] = t;
            String[] w = tcount > 0 ? new String[tcount] : null;
            int k = 0;
            for (int c = 0; c < ncols; c++) {
                String s = f.get(c + 1);
                if (log.isText[c]) { w[k++] = s; data[kept * ncols + c] = Double.NaN; }
                else {
                    double v = Double.NaN;
                    if (!s.isEmpty()) { try { v = Double.parseDouble(s); } catch (NumberFormatException e) { v = Double.NaN; } }
                    data[kept * ncols + c] = v;
                }
            }
            if (words != null) words[kept] = w;
            kept++;
        }
        if (!header) throw new IOException("not a flight log: empty");
        log.rows = kept;
        log.time = Arrays.copyOf(time, kept);
        log.data = Arrays.copyOf(data, kept * ncols);
        if (words != null) log.words = Arrays.copyOf(words, kept);
        log.stride = stride;
        return log;
    }
}
