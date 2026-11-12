// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The structure and the search of the Settings screen. Plain Java, so that it can be tested on a PC: the tabs and which of the old groups
 * each one holds, and how a search matches a label (without caring for capitals or accents, so that "istatistik" finds "İstatistik" and
 * "uber" finds "über", and with every word of the query having to be in the label).
 */
public final class SettingsIndex {
    private SettingsIndex() {}

    /** The tabs of the Settings screen, in the order of the rail. */
    public static final String[] TAB_IDS = {"general", "flight", "video", "dashboard", "tracker", "logs", "rules", "system"};

    /** The blocks of rows (the old groups; the two Diagnostics groups are one now) and the tab that holds each. */
    private static final String[][] GROUP_TAB = {
        {"language", "general"},
        {"aircraft", "flight"}, {"window", "flight"}, {"after", "flight"},
        {"video", "video"}, {"overlays", "video"},
        {"dashboard", "dashboard"},
        {"tracker", "tracker"},
        {"logs", "logs"},
        {"rules", "rules"},
        {"diagnostics", "system"}, {"updates", "system"}, {"about", "system"}};

    public static List<String> groupIds() {
        List<String> r = new ArrayList<String>();
        for (String[] g : GROUP_TAB) r.add(g[0]);
        return r;
    }

    /** The tab of a block of rows, or null if there is no such block. */
    public static String tabOf(String groupId) {
        for (String[] g : GROUP_TAB) if (g[0].equals(groupId)) return g[1];
        return null;
    }

    /** Lower case, without accents and without marks; the Turkish dotless i and the German sharp s are folded too. */
    public static String fold(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).replace('\u0131', 'i').replace("\u00DF", "ss").replace("\u00E6", "ae").replace("\u00F8", "o").replace('\u0142', 'l').replace('\u0111', 'd');
        t = Normalizer.normalize(t, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(t.length());
        boolean space = true;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (Character.isWhitespace(c)) {
                if (!space) b.append(' ');
                space = true;
            } else {
                b.append(c);
                space = false;
            }
        }
        int n = b.length();
        if (n > 0 && b.charAt(n - 1) == ' ') b.setLength(n - 1);
        return b.toString();
    }

    /** The words of a query, folded; empty for an empty query. */
    public static String[] words(String query) {
        String f = fold(query);
        return f.isEmpty() ? new String[0] : f.split(" ");
    }

    /** True if every word is somewhere in the label (an empty query matches nothing). */
    public static boolean matches(String label, String[] words) {
        if (words == null || words.length == 0) return false;
        String f = fold(label);
        for (String w : words) if (!f.contains(w)) return false;
        return true;
    }
}
