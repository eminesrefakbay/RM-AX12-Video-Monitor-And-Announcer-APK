// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The battery packs of one aircraft: a name, the battery profile it belongs to (by name) and its capacity.
 * Choosing a pack chooses its profile, so the limits follow the pack that is on the aircraft.
 * Stored as lines "name|profile|capacity". Plain Java, tested on a PC.
 */
public final class Packs {

    public static final int MAX = 8;
    public static final int MAX_NAME = 16;
    public static final int MAX_MAH = 30000;

    public static final class Pack {
        public String name;
        public String profile;     // the name of a battery profile (may be missing: then the profile stays as it is)
        public int capacityMah;    // 0 = not known

        public Pack(String name, String profile, int capacityMah) {
            this.name = name;
            this.profile = profile;
            this.capacityMah = capacityMah;
        }

        public Pack copy() { return new Pack(name, profile, capacityMah); }
    }

    private Packs() {}

    public static String cleanName(String raw) {
        String s = raw == null ? "" : raw.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim().replaceAll("\\s+", " ");
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    public static String encode(List<Pack> list) {
        StringBuilder sb = new StringBuilder();
        for (Pack p : list) sb.append(cleanName(p.name)).append('|').append(cleanName(p.profile)).append('|').append(p.capacityMah).append('\n');
        return sb.toString();
    }

    private static Pack parseLine(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length != 3) return null;
        String name = cleanName(f[0]);
        int mah;
        try {
            mah = Integer.parseInt(f[2].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (name.isEmpty() || mah < 0 || mah > MAX_MAH) return null;
        return new Pack(name, cleanName(f[1]), mah);
    }

    /** Stored packs. A damaged line or a repeated name is skipped; no packs at all is fine (an empty list). */
    public static List<Pack> decode(String text) {
        List<Pack> out = new ArrayList<Pack>();
        if (text == null) return out;
        for (String line : text.split("\\r?\\n")) {
            if (out.size() >= MAX) break;
            Pack p = parseLine(line);
            if (p == null) continue;
            boolean dup = false;
            for (Pack q : out) if (q.name.equalsIgnoreCase(p.name)) dup = true;
            if (!dup) out.add(p);
        }
        return out;
    }

    /** Every line must be valid (used to check a file). An empty text is valid: an aircraft without packs. */
    public static List<Pack> parseStrict(String text) {
        List<Pack> out = new ArrayList<Pack>();
        if (text == null) return null;
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            Pack p = parseLine(line);
            if (p == null) return null;
            out.add(p);
        }
        return out.size() > MAX || validate(out) != null ? null : out;
    }

    /** @return null if fine, otherwise a message for the user */
    public static String validate(List<Pack> list) {
        if (list.size() > MAX) return I18n.t("At most {0} packs.", MAX);
        for (int i = 0; i < list.size(); i++) {
            Pack p = list.get(i);
            if (cleanName(p.name).isEmpty()) return I18n.t("Pack {0}: the name cannot be empty.", i + 1);
            if (p.capacityMah < 0 || p.capacityMah > MAX_MAH) return I18n.t("Pack {0}: the capacity is 0 to {1} mAh (0 = unknown).", i + 1, MAX_MAH);
            for (int j = 0; j < i; j++) {
                if (cleanName(list.get(j).name).equalsIgnoreCase(cleanName(p.name))) return I18n.t("Two packs have the same name: {0}.", cleanName(p.name));
            }
        }
        return null;
    }

    /** "Pack 1 (Li-ion 6S, 3000 mAh)" */
    public static String describe(Pack p) {
        return p.capacityMah > 0 ? I18n.t("{0} ({1}, {2} mAh)", p.name, p.profile, p.capacityMah) : I18n.t("{0} ({1})", p.name, p.profile);
    }

    /** The index of the profile a pack points to, or -1 when no profile has that name. */
    public static int profileIndex(Pack p, List<BatteryProfiles.Profile> profiles) {
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).name.equalsIgnoreCase(p.profile)) return i;
        return -1;
    }

    public static String capacityText(int mah) { return String.format(Locale.US, "%d", mah); }
}
