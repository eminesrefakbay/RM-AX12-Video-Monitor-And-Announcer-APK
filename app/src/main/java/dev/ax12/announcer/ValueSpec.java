// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Locale;

/**
 * The setting of a value card: which value, its label and unit, the decimals, and two limits that turn the number amber and red.
 * Stored as variable;label;unit;decimals;warn;crit;direction, where the direction says which side is bad:
 * "<" a number at or below the limit is bad (a battery), ">" at or above (a current), "-" no limits.
 */
public final class ValueSpec {
    public String variable = "vbat", label = "", unit = "", direction = "-";
    public int decimals = 1;
    public double warn = Double.NaN, crit = Double.NaN;

    public ValueSpec() {}

    public ValueSpec(String variable, String label, String unit, int decimals, double warn, double crit, String direction) {
        this.variable = clean(variable, "vbat");
        this.label = clean(label, "");
        this.unit = clean(unit, "");
        this.decimals = Math.max(0, Math.min(3, decimals));
        this.warn = warn;
        this.crit = crit;
        this.direction = direction.equals("<") || direction.equals(">") ? direction : "-";
    }

    private static String clean(String s, String fallback) {
        if (s == null) return fallback;
        String t = s.replace(';', ' ').replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > 24) t = t.substring(0, 24);
        return t.isEmpty() ? fallback : t;
    }

    private static double num(String s) {
        try { return s.trim().isEmpty() ? Double.NaN : Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static String fmtLimit(double v) { return Double.isNaN(v) ? "" : (v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v)); }

    /** From the stored text; anything missing or wrong takes the starting value. */
    public static ValueSpec parse(String text) {
        if (text == null) return new ValueSpec();
        String[] p = text.split(";", -1);
        String variable = p.length > 0 ? p[0] : "";
        if (!LogFields.validName(variable.trim())) variable = "vbat";
        int dec = 1;
        try { dec = p.length > 3 ? Integer.parseInt(p[3].trim()) : 1; } catch (NumberFormatException e) { dec = 1; }
        return new ValueSpec(variable.trim(), p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : "", dec,
            p.length > 4 ? num(p[4]) : Double.NaN, p.length > 5 ? num(p[5]) : Double.NaN, p.length > 6 ? p[6].trim() : "-");
    }

    public String serialize() {
        return variable + ";" + label + ";" + unit + ";" + decimals + ";" + fmtLimit(warn) + ";" + fmtLimit(crit) + ";" + direction;
    }

    /** The number as shown: the chosen decimals. */
    public String shown(double v) {
        if (Double.isNaN(v)) return "-";
        return String.format(Locale.US, "%." + decimals + "f", v);
    }

    /** 0 fine, 1 warning (amber), 2 critical (red). A limit that is not set is ignored. */
    public int level(double v) {
        if (Double.isNaN(v) || direction.equals("-")) return 0;
        boolean low = direction.equals("<");
        if (!Double.isNaN(crit) && (low ? v <= crit : v >= crit)) return 2;
        if (!Double.isNaN(warn) && (low ? v <= warn : v >= warn)) return 1;
        return 0;
    }

    /** What the card says above its number: the label, or the name of the value. */
    public String title() { return label.isEmpty() ? variable : label; }
}
