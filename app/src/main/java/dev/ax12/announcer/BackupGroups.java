// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The settings file, sorted into groups the pilot understands (settings, rules, aircraft, batteries, layouts, tracker, pack history), so that a
 * backup or a restore can take some of them. The file format itself is [SettingsFile]. Plain Java, tested on a PC.
 */
public final class BackupGroups {
    private BackupGroups() {}

    public static final int SETTINGS = 0, RULES = 1, AIRCRAFT = 2, BATTERY = 3, LAYOUTS = 4, TRACKER = 5, HISTORY = 6;
    public static final int COUNT = 7;
    /** What a backup holds when nothing is chosen: everything that is a setup (the history of the packs is optional). */
    public static boolean[] defaultSelection() { return new boolean[]{true, true, true, true, true, false, false}; }

    public static int groupOf(String key) {
        if (key.equals("rules") || key.equals("warnRules") || key.equals("checkRules")) return RULES;
        if (key.equals("aircraftStore") || key.equals("aircraftActive")) return AIRCRAFT;
        if (key.equals("batProfiles") || key.equals("batActive") || key.equals("packs") || key.equals("packActive") || key.equals("reserveCfg") || key.equals("reserveOn") || key.startsWith("reserve")) return BATTERY;
        if (key.equals("dashTiles") || key.equals("dashLayouts") || key.equals("layoutWithAircraft") || AircraftStore.isLayoutKey(key)) return LAYOUTS;
        if (key.equals("trkOn") || key.equals("trkAddr") || key.equals("trkName")) return TRACKER;
        if (key.equals("packBook")) return HISTORY;
        return SETTINGS;
    }

    /** The values of the chosen groups only. */
    public static Map<String, Object> filter(Map<String, ?> values, boolean[] on) {
        Map<String, Object> out = new HashMap<String, Object>();
        for (Map.Entry<String, ?> e : values.entrySet()) {
            if (on[groupOf(e.getKey())]) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** How many things there are in each group: the rules, the aircraft and the packs are counted, the rest by their values. */
    public static int[] counts(Map<String, ?> values) {
        int[] n = new int[COUNT];
        for (Map.Entry<String, ?> e : values.entrySet()) {
            String k = e.getKey();
            int g = groupOf(k);
            Object v = e.getValue();
            if (g == RULES) n[g] += lines(v);
            else if (k.equals("aircraftStore")) n[g] = AircraftStore.decode(v instanceof String ? (String) v : "", "").list().size();
            else if (k.equals("packs")) n[g] += Packs.decode(v instanceof String ? (String) v : "").size();
            else if (k.equals("packBook")) n[g] = PackBook.decode(v instanceof String ? (String) v : "").size();
            else if (g == AIRCRAFT || g == BATTERY && !k.equals("batProfiles")) { /* counted above or not a thing of its own */ }
            else if (k.equals("batProfiles")) n[g] += BatteryProfiles.decode(v instanceof String ? (String) v : "").size();
            else n[g]++;
        }
        return n;
    }

    private static int lines(Object v) {
        if (!(v instanceof String)) return 0;
        int n = 0;
        for (String l : ((String) v).split("\n")) { String t = l.trim(); if (!t.isEmpty() && !t.startsWith("#")) n++; }
        return n;
    }

    /** True when a reminder is due: it is on, and the last backup is more than [days] old (or there was none). */
    public static boolean reminderDue(long lastMs, long nowMs, int days, boolean on) {
        if (!on) return false;
        if (lastMs <= 0) return true;
        return nowMs - lastMs >= days * 86400000L;
    }

    /** The groups a file can fill, from the keys it has. */
    public static Set<Integer> present(Map<String, ?> values) {
        Set<Integer> s = new HashSet<Integer>();
        for (String k : values.keySet()) s.add(groupOf(k));
        return s;
    }
}
