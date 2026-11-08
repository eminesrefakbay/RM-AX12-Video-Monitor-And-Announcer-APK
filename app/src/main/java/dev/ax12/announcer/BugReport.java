// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * The text a pilot can send with a problem: versions, the device, the state of the radio link and its start-up timing, a digest of the
 * settings, the last events and, after a crash, the stack trace. It holds no GPS position, no aircraft or pack name and nothing personal;
 * every line also passes the privacy filter. Plain Java, so that it can be tested on a PC.
 */
public final class BugReport {
    private BugReport() {}

    /** The settings that tell how the app behaves; names of aircraft and packs, rules, positions of cards and the like are left out. */
    public static final String[] SETTINGS = {
        "language", "speechLanguage", "updateChannel", "updateAuto", "sunlight", "shape", "dashPage", "camera", "fill", "rotation", "splashOn", "menuAlways",
        "warnBarOn", "statsOn", "chartOn", "radarOn", "sticksOn", "checkOn", "barsOn", "layoutSnap", "logOn", "logRate", "logKeep",
        "winOn", "winSpeech", "debriefOn", "lossSpeech", "armCh", "armHigh", "armLow", "stickMode"
    };

    public static final int MAX_EVENTS = 60;
    public static final int MAX_NOTE = 600;

    public static final class Section {
        public final String title;
        public final List<String> lines = new ArrayList<String>();

        public Section(String title) { this.title = title; }

        public Section add(String line) {
            lines.add(line);
            return this;
        }
    }

    public static String time(long wallMs, TimeZone tz) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.US);
        f.setTimeZone(tz);
        return f.format(new Date(wallMs));
    }

    /** "key: value" for each setting of the digest that is present, in the order of SETTINGS. */
    public static List<String> settingsLines(Map<String, Object> prefs) {
        List<String> out = new ArrayList<String>();
        for (String k : SETTINGS) {
            Object v = prefs.get(k);
            if (v != null) out.add(k + ": " + v);
        }
        return out;
    }

    public static Section events(List<DiagLog.Event> all, TimeZone tz, int max) {
        Section s = new Section("Recent events (oldest first)");
        int from = Math.max(0, all.size() - max);
        if (all.isEmpty()) s.add("none");
        for (int i = from; i < all.size(); i++) {
            DiagLog.Event e = all.get(i);
            s.add(time(e.wallMs, tz) + " " + (e.level.equals("info") ? "    " : e.level.equals("warn") ? "WARN" : "ERR ") + " " + e.tag + ": " + e.message);
        }
        if (from > 0) s.add("(" + from + " older events left out)");
        return s;
    }

    public static String build(String header, long wallMs, TimeZone tz, List<Section> sections, String note) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        f.setTimeZone(tz);
        StringBuilder sb = new StringBuilder();
        sb.append(header).append('\n');
        sb.append("Created: ").append(f.format(new Date(wallMs))).append(" (clock of the radio, ").append(tz.getID()).append(")\n");
        sb.append("This report holds no GPS positions, no aircraft or pack names and nothing personal.\n");
        for (Section s : sections) {
            sb.append("\n== ").append(s.title).append(" ==\n");
            for (String l : s.lines) sb.append(l).append('\n');
        }
        String n = note == null ? "" : DiagLog.clean(note, MAX_NOTE);
        sb.append("\n== Note from the pilot ==\n").append(n.isEmpty() ? "none" : n).append('\n');
        return Privacy.redact(sb.toString());
    }
}
