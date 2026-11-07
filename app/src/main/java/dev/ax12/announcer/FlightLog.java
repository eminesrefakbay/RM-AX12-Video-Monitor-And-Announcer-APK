// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.LinkedHashMap;
import java.util.Map;

/** One flight log read back from its file: the header, the footer and the rows. Replay and the flight list use it. */
public final class FlightLog {
    /** "# key: value" lines at the top of the file (the format, the app, the aircraft, the pack, the cells, the arm switch...). */
    public final Map<String, String> meta = new LinkedHashMap<String, String>();
    /** The "# end:" line at the bottom; empty when the file was not closed cleanly (the app was killed, the battery was pulled). */
    public final Map<String, String> end = new LinkedHashMap<String, String>();
    /** The column names, without the time column. */
    public String[] columns = new String[0];
    public boolean[] isText = new boolean[0];
    public int rows;
    /** Milliseconds since arming, one per row. */
    public long[] time = new long[0];
    /** rows x columns, row after row; NaN where a value was not there. */
    public double[] data = new double[0];
    /** rows x text columns; null when there is no text column. */
    public String[][] words;
    /** 1 when every row was kept; 2 or more when a very long log was thinned out to fit the memory. */
    public int stride = 1;

    public int columnCount() { return columns.length; }

    public int indexOf(String name) {
        for (int i = 0; i < columns.length; i++) if (columns[i].equals(name)) return i;
        return -1;
    }

    public double value(int row, int col) { return data[row * columns.length + col]; }

    /** The text of a text column; null when there is none. */
    public String word(int row, int col) {
        if (words == null || !isText[col]) return null;
        int k = 0;
        for (int i = 0; i < col; i++) if (isText[i]) k++;
        String s = words[row][k];
        return s == null || s.isEmpty() ? null : s;
    }

    public long durationMs() { return rows == 0 ? 0 : time[rows - 1]; }

    public String meta(String key, String fallback) {
        String v = meta.get(key);
        return v == null ? fallback : v;
    }

    public double metaNumber(String key, double fallback) {
        try { return Double.parseDouble(meta.get(key)); } catch (Exception e) { return fallback; }
    }
}
