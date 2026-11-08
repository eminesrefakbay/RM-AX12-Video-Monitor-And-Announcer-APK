// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;

/**
 * The last events of the app (it started, the link came up, the aircraft armed, an update could not be checked ...), kept in memory for the
 * diagnostics page and the bug report. It holds what happened, never where: nothing that looks like a position or an address stays in it.
 * Plain Java, so that it can be tested on a PC.
 */
public final class DiagLog {
    public static final int CAPACITY = 200;
    public static final int MAX_MESSAGE = 240;
    public static final int MAX_TAG = 12;

    public static final class Event {
        public final long wallMs;
        public final String level, tag, message;

        Event(long wallMs, String level, String tag, String message) {
            this.wallMs = wallMs;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }
    }

    private final List<Event> events = new ArrayList<Event>();

    /** One line, no control characters, no private data, not too long. */
    static String clean(String s, int max) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 32 || c == 127 ? ' ' : c);
        }
        String t = Privacy.redact(sb.toString().trim().replaceAll(" {2,}", " "));
        return t.length() <= max ? t : t.substring(0, max - 3) + "...";
    }

    /** level is "info", "warn" or "error"; anything else counts as info. */
    public synchronized void add(long wallMs, String level, String tag, String message) {
        String lv = "warn".equals(level) || "error".equals(level) ? level : "info";
        events.add(new Event(wallMs, lv, clean(tag, MAX_TAG).toLowerCase(java.util.Locale.ROOT), clean(message, MAX_MESSAGE)));
        while (events.size() > CAPACITY) events.remove(0);
    }

    /** The events, the oldest first. */
    public synchronized List<Event> snapshot() { return new ArrayList<Event>(events); }

    public synchronized int size() { return events.size(); }

    public synchronized int count(String level) {
        int n = 0;
        for (Event e : events) if (e.level.equals(level)) n++;
        return n;
    }

    public synchronized void clear() { events.clear(); }
}
