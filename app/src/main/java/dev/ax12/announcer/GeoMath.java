// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Locale;

/** Distances, bearings and coordinates as text. Plain Java, so that it can be tested on a PC. */
public final class GeoMath {
    private GeoMath() {}

    private static final double EARTH_M = 6371008.8;
    private static final String[] WORDS = {I18n.n("North"), I18n.n("North east"), I18n.n("East"), I18n.n("South east"),
        I18n.n("South"), I18n.n("South west"), I18n.n("West"), I18n.n("North west")};

    /** A position a GPS could really have: finite, on the globe, and not the 0,0 a receiver reports without a fix. */
    public static boolean validFix(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && !Double.isInfinite(lat) && !Double.isInfinite(lon)
            && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 && !(lat == 0.0 && lon == 0.0);
    }

    /** Great-circle distance in metres. */
    public static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** The compass bearing, 0 to 360 degrees, from the first point to the second. */
    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2), dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    /** "36.812345, 34.612345": decimal degrees that a map app accepts as they are. */
    public static String coords(double lat, double lon) {
        return String.format(Locale.US, "%.6f, %.6f", lat, lon);
    }

    /** "north east" in the given language, for the voice. */
    public static String cardinalWord(String language, double deg) {
        if (Double.isNaN(deg)) return "";
        int i = (int) Math.floor((((deg % 360) + 360) % 360 + 22.5) / 45.0) % 8;
        String w = I18n.tIn(language, WORDS[i]);
        return "en".equals(language) || "tr".equals(language) ? w.toLowerCase(java.util.Locale.ROOT) : w;       // in the middle of a sentence (German nouns keep their capital)
    }
}
