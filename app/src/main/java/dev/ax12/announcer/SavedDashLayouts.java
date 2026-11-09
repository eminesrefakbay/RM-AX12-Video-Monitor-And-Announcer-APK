// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Named dashboard layouts, stored as length-prefixed name/layout pairs. */
public final class SavedDashLayouts {
    public static final int MAX_SAVED = 10;
    public static final int MAX_NAME_CHARS = 32;
    private static final int MAX_TEXT_CHARS = 120000;

    public static final class Entry {
        public final String name;
        public final String layout;

        private Entry(String name, String layout) {
            this.name = name;
            this.layout = layout;
        }
    }

    private SavedDashLayouts() {}

    public static String cleanName(String name) {
        if (name == null) return "";
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < name.length();) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isISOControl(cp)) clean.appendCodePoint(cp);
        }
        String result = clean.toString().trim();
        int count = result.codePointCount(0, result.length());
        if (count > MAX_NAME_CHARS) result = result.substring(0, result.offsetByCodePoints(0, MAX_NAME_CHARS));
        return result;
    }

    public static List<Entry> parse(String text) {
        List<Entry> entries = new ArrayList<Entry>();
        if (text == null || text.isEmpty()) return entries;
        if (text.length() > MAX_TEXT_CHARS) return entries;
        Set<String> names = new HashSet<String>();
        int at = 0;
        while (at < text.length() && entries.size() < MAX_SAVED) {
            int nameLength = readLength(text, at);
            if (nameLength < 0) return new ArrayList<Entry>();
            at = nextValue(text, at);
            if (nameLength > text.length() - at) return new ArrayList<Entry>();
            String name = text.substring(at, at + nameLength);
            at += nameLength;

            int layoutLength = readLength(text, at);
            if (layoutLength < 0) return new ArrayList<Entry>();
            at = nextValue(text, at);
            if (layoutLength > text.length() - at) return new ArrayList<Entry>();
            String layout = text.substring(at, at + layoutLength);
            at += layoutLength;

            String clean = cleanName(name);
            String folded = clean.toLowerCase(Locale.ROOT);
            if (clean.isEmpty() || !clean.equals(name) || !names.add(folded) ||
                layout.isEmpty() || !DashLayout.valid(layout) || DashLayout.parse(layout).isEmpty()) {
                return new ArrayList<Entry>();
            }
            entries.add(new Entry(name, layout));
        }
        if (at != text.length()) return new ArrayList<Entry>();
        return entries;
    }

    public static boolean valid(String text) {
        if (text == null || text.length() > MAX_TEXT_CHARS) return false;
        if (text.isEmpty()) return true;
        List<Entry> entries = parse(text);
        return !entries.isEmpty() && entries.size() <= MAX_SAVED;
    }

    public static String save(String stored, String name, String layout) {
        String clean = cleanName(name);
        if (clean.isEmpty() || layout == null || layout.isEmpty() || !DashLayout.valid(layout) || DashLayout.parse(layout).isEmpty()) return null;
        List<Entry> entries = parse(stored);
        int existing = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name.equalsIgnoreCase(clean)) { existing = i; break; }
        }
        if (existing < 0 && entries.size() >= MAX_SAVED) return null;
        Entry entry = new Entry(clean, layout);
        if (existing < 0) entries.add(entry); else entries.set(existing, entry);
        return serialize(entries);
    }

    public static String remove(String stored, String name) {
        List<Entry> entries = parse(stored);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name.equalsIgnoreCase(name)) {
                entries.remove(i);
                break;
            }
        }
        return serialize(entries);
    }

    private static String serialize(List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            out.append(entry.name.length()).append(':').append(entry.name);
            out.append(entry.layout.length()).append(':').append(entry.layout);
        }
        return out.toString();
    }

    private static int readLength(String text, int at) {
        int colon = text.indexOf(':', at);
        if (colon < 0 || colon == at || colon - at > 6) return -1;
        try {
            int length = Integer.parseInt(text.substring(at, colon));
            return length < 0 ? -1 : length;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int nextValue(String text, int at) {
        return text.indexOf(':', at) + 1;
    }
}
