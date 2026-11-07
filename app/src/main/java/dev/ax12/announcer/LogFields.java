// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which values go into a flight log: the list is stored as one text (names separated by commas), edited in Settings, and checked
 * against what a replay needs. The names are the variable names of the rules and charts (ch1, vbat, gps_lat, fm ...).
 */
public final class LogFields {
    private LogFields() {}

    public static final int MAX_FIELDS = 300;
    public static final int MAX_NAME = 40;

    /** Every standard value of the app: what a replay needs, and a little more. This is the starting selection. */
    public static List<String> replaySet() {
        List<String> l = new ArrayList<String>();
        for (int i = 1; i <= 16; i++) l.add("ch" + i);
        String[] rest = {"vbat", "curr", "capa", "batpct",
            "up_lq", "up_rssi1", "up_rssi2", "up_snr", "dn_lq", "dn_rssi", "dn_snr", "rf_mode", "tx_power",
            "lq", "dbm", "lost", "txv", "rxv", "tlm",
            "sats", "gps_speed", "gps_alt", "gps_heading", "gps_lat", "gps_lon",
            "pitch", "roll", "yaw", "vspeed", "fm"};
        for (String s : rest) l.add(s);
        return l;
    }

    /** True for a name that can be a column: letters, digits and underscores. */
    public static boolean validName(String s) {
        if (s == null || s.isEmpty() || s.length() > MAX_NAME) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) return false;
        }
        return true;
    }

    /** The stored text to a list: invalid names and repeats are dropped, the order is kept. */
    public static List<String> parse(String stored) {
        Set<String> out = new LinkedHashSet<String>();
        if (stored != null) {
            for (String part : stored.split(",")) {
                String s = part.trim();
                if (validName(s) && out.size() < MAX_FIELDS) out.add(s);
            }
        }
        return new ArrayList<String>(out);
    }

    public static String join(Collection<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (!validName(n)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        return sb.toString();
    }

    /** The selection to use: the stored one, or the replay set when nothing valid is stored. */
    public static List<String> selection(String stored) {
        List<String> l = parse(stored);
        return l.isEmpty() ? replaySet() : l;
    }

    /** What a replay cannot do without: the arm channel, the four sticks, the position, speed, current, link, volts and a heading. */
    public static List<String> missingForReplay(Collection<String> selected, int armChannel) {
        Set<String> have = new LinkedHashSet<String>(selected);
        List<String> need = new ArrayList<String>();
        need.add("ch" + armChannel);
        for (int i = 1; i <= 4; i++) if (i != armChannel) need.add("ch" + i);
        String[] core = {"gps_lat", "gps_lon", "gps_alt", "sats", "gps_speed", "curr", "up_lq", "vbat"};
        for (String s : core) need.add(s);
        List<String> missing = new ArrayList<String>();
        for (String n : need) if (!have.contains(n)) missing.add(n);
        if (!have.contains("yaw") && !have.contains("gps_heading")) missing.add("yaw");
        return missing;
    }
}
