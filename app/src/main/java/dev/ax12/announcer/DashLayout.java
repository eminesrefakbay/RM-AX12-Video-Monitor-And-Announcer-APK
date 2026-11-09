// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The cards of the dashboard page and where they are. Stored as one text, a card per line: id|kind|x|y|scale|setting.
 * x and y are places in the free space of the screen (0 = left or top, 1000 = right or bottom), scale is a percent (50 to 250).
 * The setting belongs to the kind: for a value card it is the {@link ValueSpec}. Plain Java, so that it can be tested.
 */
public final class DashLayout {
    private DashLayout() {}

    public static final int MAX_TILES = 24;
    public static final String[] KINDS = {"horizon", "compass", "stats", "radar", "chart", "sticks", "value"};

    public static final class Tile {
        public final String id;
        public final String kind;
        public int x, y, scale;
        public String setting;

        public Tile(String id, String kind, int x, int y, int scale, String setting) {
            this.id = id;
            this.kind = kind;
            this.x = clamp(x, 0, 1000);
            this.y = clamp(y, 0, 1000);
            this.scale = clamp(scale, 50, 250);
            this.setting = setting == null ? "" : setting.replace('|', ' ').replace('\n', ' ').replace('\r', ' ');
        }

        public Tile copy() { return new Tile(id, kind, x, y, scale, setting); }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    public static boolean validKind(String k) {
        for (String s : KINDS) if (s.equals(k)) return true;
        return false;
    }

    /** The English name of a kind, in the language of the screen. */
    public static String label(String kind) {
        if (kind.equals("horizon")) return I18n.t("Horizon");
        if (kind.equals("compass")) return I18n.t("Compass");
        if (kind.equals("stats")) return I18n.t("Flight statistics");
        if (kind.equals("radar")) return I18n.t("Radar map");
        if (kind.equals("chart")) return I18n.t("Live charts");
        if (kind.equals("sticks")) return I18n.t("Stick view");
        return I18n.t("Value");
    }

    /** The cards from their text. A line that is not a card, a kind that is not known and a repeated id are dropped; the list is capped. */
    public static List<Tile> parse(String text) {
        List<Tile> out = new ArrayList<Tile>();
        Set<String> ids = new HashSet<String>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 5) continue;
            String id = p[0].trim(), kind = p[1].trim();
            if (id.isEmpty() || id.length() > 24 || !validKind(kind) || !ids.add(id)) continue;
            try {
                int x = Integer.parseInt(p[2].trim()), y = Integer.parseInt(p[3].trim()), s = Integer.parseInt(p[4].trim());
                out.add(new Tile(id, kind, x, y, s, p.length > 5 ? p[5] : ""));
            } catch (NumberFormatException e) { /* not a card */ }
            if (out.size() >= MAX_TILES) break;
        }
        return out;
    }

    public static String serialize(List<Tile> tiles) {
        StringBuilder sb = new StringBuilder();
        for (Tile t : tiles) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.id).append('|').append(t.kind).append('|').append(t.x).append('|').append(t.y).append('|').append(t.scale).append('|').append(t.setting);
        }
        return sb.toString();
    }

    /** A first dashboard: the horizon and the compass on top, charts, radar and sticks, and four value cards. */
    public static List<Tile> defaults() {
        List<Tile> l = new ArrayList<Tile>();
        l.add(new Tile("horizon1", "horizon", 0, 0, 100, ""));
        l.add(new Tile("compass1", "compass", 190, 0, 100, ""));
        l.add(new Tile("chart1", "chart", 1000, 0, 100, ""));
        l.add(new Tile("value1", "value", 0, 520, 100, new ValueSpec("cell", "", "V", 2, 3.5, 3.3, "<").serialize()));
        l.add(new Tile("value2", "value", 190, 520, 100, new ValueSpec("curr", "", "A", 1, 45, 55, ">").serialize()));
        l.add(new Tile("value3", "value", 380, 520, 100, new ValueSpec("up_lq", "", "%", 0, 80, 50, "<").serialize()));
        l.add(new Tile("value4", "value", 570, 520, 100, new ValueSpec("fm", "", "", 0, Double.NaN, Double.NaN, "-").serialize()));
        l.add(new Tile("stats1", "stats", 0, 1000, 100, ""));
        l.add(new Tile("sticks1", "sticks", 500, 1000, 100, ""));
        l.add(new Tile("radar1", "radar", 1000, 1000, 100, ""));
        return l;
    }

    /** A built-in dashboard layout. Preset identifiers are stable and never translated. */
    public static List<Tile> preset(String id) {
        if ("flight".equals(id)) return defaults();
        List<Tile> l = new ArrayList<Tile>();
        if ("minimal".equals(id)) {
            l.add(new Tile("horizon1", "horizon", 0, 0, 100, ""));
            l.add(new Tile("compass1", "compass", 1000, 0, 100, ""));
            l.add(new Tile("value1", "value", 0, 1000, 100, new ValueSpec("cell", "", "V", 2, 3.5, 3.3, "<").serialize()));
            l.add(new Tile("value2", "value", 1000, 1000, 100, new ValueSpec("up_lq", "", "%", 0, 80, 50, "<").serialize()));
            return l;
        }
        if ("bench".equals(id)) {
            for (int i = 0; i < 8; i++) {
                l.add(new Tile("channel" + (i + 1), "value", (i % 2) * 1000, (i / 2) * 333, 100,
                    new ValueSpec("ch" + (i + 1), "CH" + (i + 1), "", 0, Double.NaN, Double.NaN, "-").serialize()));
            }
            return l;
        }
        return new ArrayList<Tile>();
    }

    /** The stored cards, or the first dashboard when nothing usable is stored. */
    public static List<Tile> selection(String stored) {
        List<Tile> l = parse(stored);
        return l.isEmpty() ? defaults() : l;
    }

    /** A stored text is fine when it is empty (the first dashboard) or holds at least one card. */
    public static boolean valid(String text) { return text != null && (text.isEmpty() || !parse(text).isEmpty()); }

    /** An id that no card has yet: the kind and a number. */
    public static String newId(List<Tile> tiles, String kind) {
        Set<String> have = new HashSet<String>();
        for (Tile t : tiles) have.add(t.id);
        for (int n = 1; n < 1000; n++) if (!have.contains(kind + n)) return kind + n;
        return kind + System.nanoTime();
    }

    public static Tile find(List<Tile> tiles, String id) {
        for (Tile t : tiles) if (t.id.equals(id)) return t;
        return null;
    }

    public static List<Tile> copy(List<Tile> tiles) {
        List<Tile> l = new ArrayList<Tile>();
        for (Tile t : tiles) l.add(t.copy());
        return l;
    }
}
