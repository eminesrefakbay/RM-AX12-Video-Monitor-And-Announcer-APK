// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Which values the live charts show (up to four, by variable name) and the length of the window. Plain Java, tested on a PC. */
public final class ChartConfig {

    public static final int MAX_SERIES = 4;
    public static final int[] WINDOWS = {30, 60, 120};
    public static final String DEFAULT = "up_lq,cell,curr";
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,23}");

    private ChartConfig() {}

    /** Reads the stored text, leniently: names are lower-cased, repeated or invalid ones are dropped, at most four are kept. */
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) return out;
        for (String part : text.split(",")) {
            String n = part.trim().toLowerCase(Locale.ROOT);
            if (!NAME.matcher(n).matches() || out.contains(n)) continue;
            if (out.size() >= MAX_SERIES) break;
            out.add(n);
        }
        return out;
    }

    public static String encode(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        return sb.toString();
    }

    /** Strict check for a settings file: every name valid, no repeats, at most four. Empty is fine (no charts). */
    public static boolean valid(String text) {
        if (text == null) return false;
        if (text.trim().isEmpty()) return true;
        String[] parts = text.split(",", -1);
        if (parts.length > MAX_SERIES) return false;
        List<String> seen = new ArrayList<String>();
        for (String part : parts) {
            if (!NAME.matcher(part).matches() || !part.equals(part.toLowerCase(Locale.ROOT)) || seen.contains(part)) return false;
            seen.add(part);
        }
        return true;
    }

    public static int windowSeconds(int index) {
        return WINDOWS[Math.max(0, Math.min(WINDOWS.length - 1, index))];
    }
}
