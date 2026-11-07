// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The words of the flight list: when, how long, and a short summary of each log. Plain Java, so that it can be tested. */
public final class LogText {
    private LogText() {}

    private static String n0(double v) { return String.valueOf(Math.round(v)); }

    /** "2026-11-07  14:55" from the header, or from the file name when the header is missing; the name itself as the last resort. */
    public static String dateTime(LogStore.Entry e) {
        String s = e.started;
        if (s != null && s.length() >= 16 && s.charAt(4) == '-' && s.charAt(10) == ' ') return s.substring(0, 10) + "  " + s.substring(11, 16);
        String n = e.name == null ? "" : e.name;
        if (n.startsWith(LogStore.PREFIX) && n.length() >= 26 && n.charAt(11) == '-' && n.charAt(17) == '_') {
            String d = n.substring(7, 17), t = n.substring(18, 26);
            if (d.charAt(4) == '-' && t.charAt(2) == '-') return d + "  " + t.substring(0, 2) + ":" + t.substring(3, 5);
        }
        return n;
    }

    /** m:ss, or h:mm:ss from an hour on. */
    public static String duration(long ms) {
        long sec = Math.max(0, ms) / 1000;
        long h = sec / 3600, m = (sec / 60) % 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }

    /** The first line of a card: when, and the aircraft. */
    public static String title(LogStore.Entry e) {
        String a = e.aircraft == null ? "" : e.aircraft;
        return a.isEmpty() ? dateTime(e) : dateTime(e) + "  \u00B7  " + a;
    }

    /** The second line: how long, and the pack. */
    public static String lengthAndPack(LogStore.Entry e) {
        String p = e.pack == null ? "" : e.pack;
        return p.isEmpty() ? duration(e.durationMs) : duration(e.durationMs) + "  \u00B7  " + p;
    }

    /** The third line: the numbers that are known, or a note that the log was cut off. */
    public static String summary(LogStore.Entry e) {
        List<String> parts = new ArrayList<String>();
        if (!Double.isNaN(e.maxHeight)) parts.add(I18n.t("Max height {0} m", n0(e.maxHeight)));
        if (!Double.isNaN(e.maxDist)) parts.add(I18n.t("Max distance {0} m", n0(e.maxDist)));
        if (!Double.isNaN(e.maxSpeed)) parts.add(I18n.t("Top speed {0} km/h", n0(e.maxSpeed)));
        if (!Double.isNaN(e.peakCurrent)) parts.add(I18n.t("Peak current {0} A", n0(e.peakCurrent)));
        if (!Double.isNaN(e.minCell)) parts.add(I18n.t("Lowest cell {0} V", String.format(Locale.US, "%.2f", e.minCell)));
        if (!Double.isNaN(e.mah)) parts.add(I18n.t("{0} mAh used", n0(e.mah)));
        StringBuilder sb = new StringBuilder();
        for (String p : parts) { if (sb.length() > 0) sb.append("  \u00B7  "); sb.append(p); }
        if (!e.clean) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(I18n.t("The log was cut off: the app was stopped while it was writing."));
        }
        return sb.toString();
    }

    /** 1.2 MB, 340 KB, 80 B. */
    public static String size(long bytes) {
        if (bytes >= 1024L * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        if (bytes >= 1024) return Math.round(bytes / 1024.0) + " KB";
        return bytes + " B";
    }

    /** The line under the list. */
    public static String footer(int count, long bytes) { return I18n.t("{0} logs, {1}", count, size(bytes)); }
}
