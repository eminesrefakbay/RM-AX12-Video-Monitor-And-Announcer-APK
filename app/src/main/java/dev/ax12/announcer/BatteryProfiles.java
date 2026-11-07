package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Battery profiles: the cell count and the volts-per-cell limits of a kind of pack (Li-ion, LiPo ...).
 * One profile is active. Rules can use the derived value  cell  (battery volts divided by the cells) and the names
 * $low, $crit and $full for the limits of the active profile:   cell < $low and cell >= $crit -> battery low
 * Switching the profile changes the numbers, not the rules. Plain Java, tested on a PC.
 */
public final class BatteryProfiles {

    public static final int MAX = 8;
    public static final int MAX_NAME = 16;
    /** The names a rule may use for a limit. */
    public static final String[] MACROS = {"low", "crit", "full"};
    public static final double MIN_V = 2.5, MAX_V = 4.4;

    public static final class Profile {
        public String name;
        public int cells;
        public double full;       // volts per cell that counts as a full pack
        public double low;        // warning
        public double crit;       // critical

        public Profile(String name, int cells, double full, double low, double crit) {
            this.name = name;
            this.cells = cells;
            this.full = full;
            this.low = low;
            this.crit = crit;
        }

        public Profile copy() { return new Profile(name, cells, full, low, crit); }
    }

    private BatteryProfiles() {}

    /** Li-ion lands at about 3.6 V per cell, LiPo about 3.75 V: the AKB7's two kinds of pack. */
    public static List<Profile> defaults() {
        List<Profile> out = new ArrayList<Profile>();
        out.add(new Profile("Li-ion 6S", 6, 4.15, 3.70, 3.55));
        out.add(new Profile("LiPo 6S", 6, 4.15, 3.85, 3.70));
        return out;
    }

    public static String cleanName(String raw) {
        String s = raw == null ? "" : raw.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s+", " ");
        if (s.length() > MAX_NAME) s = s.substring(0, MAX_NAME).trim();
        return s;
    }

    private static boolean okOne(Profile p) {
        return !p.name.isEmpty() && p.cells >= 1 && p.cells <= 12
            && p.crit >= MIN_V && p.crit < p.low && p.low < p.full && p.full <= MAX_V;
    }

    /** @return null if the list is fine, otherwise a message for the user */
    public static String validate(List<Profile> list) {
        if (list.isEmpty()) return I18n.t("Add at least one battery profile.");
        if (list.size() > MAX) return I18n.t("At most {0} battery profiles.", MAX);
        for (int i = 0; i < list.size(); i++) {
            Profile p = list.get(i);
            if (cleanName(p.name).isEmpty()) return I18n.t("Profile {0}: the name cannot be empty.", i + 1);
            if (p.cells < 1 || p.cells > 12) return I18n.t("Profile {0}: cells must be 1 to 12.", i + 1);
            if (!(p.crit >= MIN_V && p.crit < p.low && p.low < p.full && p.full <= MAX_V)) {
                return I18n.t("Profile {0}: use critical below low below full, between 2.5 and 4.4 volts per cell.", i + 1);
            }
            for (int j = 0; j < i; j++) {
                if (cleanName(list.get(j).name).equalsIgnoreCase(cleanName(p.name))) return I18n.t("Two profiles have the same name: {0}.", cleanName(p.name));
            }
        }
        return null;
    }

    public static String encode(List<Profile> list) {
        StringBuilder sb = new StringBuilder();
        for (Profile p : list) {
            sb.append(cleanName(p.name)).append('|').append(p.cells).append('|')
              .append(String.format(Locale.US, "%.2f|%.2f|%.2f", p.full, p.low, p.crit)).append('\n');
        }
        return sb.toString();
    }

    /** All lines must be valid; null otherwise (used to check a file before importing it). */
    public static List<Profile> parseStrict(String text) {
        List<Profile> out = new ArrayList<Profile>();
        if (text == null) return null;
        for (String line : text.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            Profile p = parseLine(line);
            if (p == null) return null;
            out.add(p);
        }
        return out.isEmpty() || out.size() > MAX || validate(out) != null ? null : out;
    }

    private static Profile parseLine(String line) {
        String[] f = line.split("\\|", -1);
        if (f.length != 5) return null;
        try {
            Profile p = new Profile(cleanName(f[0]), Integer.parseInt(f[1].trim()), Double.parseDouble(f[2].trim()),
                                    Double.parseDouble(f[3].trim()), Double.parseDouble(f[4].trim()));
            return okOne(p) ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reads stored profiles. A damaged line is skipped; nothing usable gives the defaults. */
    public static List<Profile> decode(String text) {
        List<Profile> out = new ArrayList<Profile>();
        if (text != null) {
            for (String line : text.split("\\r?\\n")) {
                if (out.size() >= MAX) break;
                Profile p = parseLine(line);
                if (p == null) continue;
                boolean dup = false;
                for (Profile q : out) if (q.name.equalsIgnoreCase(p.name)) dup = true;
                if (!dup) out.add(p);
            }
        }
        return out.isEmpty() ? defaults() : out;
    }

    public static boolean isMacro(String value) {
        if (value == null || !value.startsWith("$")) return false;
        String n = value.substring(1).toLowerCase(Locale.ROOT);
        for (String m : MACROS) if (m.equals(n)) return true;
        return false;
    }

    /** The values of $low, $crit and $full for a profile. */
    public static Map<String, Double> macros(Profile p) {
        Map<String, Double> m = new HashMap<String, Double>();
        m.put("low", p.low);
        m.put("crit", p.crit);
        m.put("full", p.full);
        return m;
    }

    /**
     * Adds the derived value  cell  (volts per cell). It is only there while a battery is really connected
     * (at least 2.5 V per cell), so rules about it stay silent when the flight controller runs from USB.
     */
    public static void addDerived(Map<String, Double> numbers, int cells) {
        Double v = numbers.get("vbat");
        if (v != null && cells > 0 && v >= MIN_V * cells) numbers.put("cell", v / cells);
    }

    public static String limitsText(Profile p) {
        return I18n.t("Low {0}, critical {1}, full {2} V per cell", String.format(Locale.US, "%.2f", p.low),
                      String.format(Locale.US, "%.2f", p.crit), String.format(Locale.US, "%.2f", p.full));
    }
}
