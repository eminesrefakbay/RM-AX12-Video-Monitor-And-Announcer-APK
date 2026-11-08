// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The last known position as text: the small file that is kept on the radio (so that it is still there after a restart), the lines of the
 * card and panel, and the sentence the voice speaks. Plain Java, so that it can be tested on a PC.
 */
public final class PositionNote {
    private PositionNote() {}

    private static String n0(double v) { return String.valueOf(Math.round(v)); }

    private static void put(StringBuilder sb, String key, String value) { sb.append(key).append(": ").append(value).append('\n'); }

    /** The file: "key: value" lines, easy to read and to open in any text viewer. */
    public static String build(String reason, String wallText, String aircraft, LinkLoss.Fix f, boolean homeSet, double homeLat, double homeLon) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AX-HUD last known position. This file holds a GPS position.\n");
        put(sb, "reason", reason);
        put(sb, "saved", wallText);
        if (aircraft != null && !aircraft.isEmpty()) put(sb, "aircraft", aircraft);
        if (f == null) {
            put(sb, "position", "none");
            return sb.toString();
        }
        put(sb, "lat", String.format(Locale.US, "%.6f", f.lat));
        put(sb, "lon", String.format(Locale.US, "%.6f", f.lon));
        put(sb, "coords", GeoMath.coords(f.lat, f.lon));
        if (!Double.isNaN(f.height)) put(sb, "height_m", n0(f.height));
        if (!Double.isNaN(f.gpsAlt)) put(sb, "gps_alt_m", n0(f.gpsAlt));
        if (!Double.isNaN(f.speedKmh)) put(sb, "speed_kmh", n0(f.speedKmh));
        if (!Double.isNaN(f.headingDeg)) put(sb, "heading_deg", n0(f.headingDeg));
        if (f.sats >= 0) put(sb, "sats", Integer.toString(f.sats));
        double[] h = LinkLoss.fromHome(f, homeSet, homeLat, homeLon);
        if (homeSet) {
            put(sb, "home", GeoMath.coords(homeLat, homeLon));
            put(sb, "from_home_m", n0(h[0]));
            put(sb, "bearing_from_home_deg", n0(h[1]));
        }
        return sb.toString();
    }

    public static Map<String, String> parse(String text) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (text == null) return out;
        for (String line : text.split("\\r?\\n")) {
            if (line.startsWith("#")) continue;
            int c = line.indexOf(": ");
            if (c > 0) out.put(line.substring(0, c).trim(), line.substring(c + 2).trim());
        }
        return out;
    }

    /** "lat, lon" from a saved file, or null when the file holds no position. */
    public static String coordsOf(String text) {
        Map<String, String> m = parse(text);
        String c = m.get("coords");
        if (c == null) return null;
        String[] p = c.split(",");
        if (p.length != 2) return null;
        try {
            double lat = Double.parseDouble(p[0].trim()), lon = Double.parseDouble(p[1].trim());
            return GeoMath.validFix(lat, lon) ? GeoMath.coords(lat, lon) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The lines of the card and the panel, in the language of the screen. */
    public static String lines(LinkLoss.Fix f, boolean homeSet, double homeLat, double homeLon) {
        if (f == null) return I18n.t("No position was received in this flight.");
        StringBuilder sb = new StringBuilder(GeoMath.coords(f.lat, f.lon));
        double[] h = LinkLoss.fromHome(f, homeSet, homeLat, homeLon);
        if (homeSet) sb.append('\n').append(I18n.t("{0} m from home, bearing {1} deg {2}", n0(h[0]), n0(h[1]), Attitude.cardinal(h[1])));
        StringBuilder more = new StringBuilder();
        if (!Double.isNaN(f.height)) more.append(I18n.t("Height {0} m", n0(f.height)));
        if (!Double.isNaN(f.speedKmh)) {
            if (more.length() > 0) more.append("  \u00B7  ");
            more.append(I18n.t("Speed {0} km/h", n0(f.speedKmh)));
        }
        if (f.sats >= 0) {
            if (more.length() > 0) more.append("  \u00B7  ");
            more.append(I18n.t("{0} satellites", f.sats));
        }
        if (more.length() > 0) sb.append('\n').append(more);
        return sb.toString();
    }

    /** What the voice says when the link is lost. */
    public static String speech(String language, LinkLoss.Fix f, boolean homeSet, double homeLat, double homeLon) {
        if (f == null) return I18n.tIn(language, "Link lost.");
        double[] h = LinkLoss.fromHome(f, homeSet, homeLat, homeLon);
        if (!homeSet) return I18n.tIn(language, "Link lost. The last position is saved.");
        String dir = GeoMath.cardinalWord(language, h[1]);
        if (!Double.isNaN(f.height)) {
            return I18n.tIn(language, "Link lost. Last position {0} meters {1} of home, height {2} meters.", n0(h[0]), dir, n0(f.height));
        }
        return I18n.tIn(language, "Link lost. Last position {0} meters {1} of home.", n0(h[0]), dir);
    }
}
