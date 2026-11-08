// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Locale;

/** What the voice says after a landing, from the numbers of the flight. Plain Java, so that it can be tested on a PC. */
public final class Debrief {
    private Debrief() {}

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    public static String text(String language, PackBook.Flight f, String pack, int flightNumber) {
        StringBuilder sb = new StringBuilder();
        int m = f.seconds / 60, s = f.seconds % 60;
        if (m >= 2) sb.append(I18n.tIn(language, "Flight time {0} minutes {1} seconds.", m, s));
        else if (m == 1) sb.append(I18n.tIn(language, "Flight time {0} minute {1} seconds.", m, s));
        else sb.append(I18n.tIn(language, "Flight time {0} seconds.", s));
        if (ok(f.km) && f.km >= 0.05) sb.append(' ').append(I18n.tIn(language, "Distance {0} kilometers.", String.format(Locale.US, "%.1f", f.km)));
        if (ok(f.mah) && f.mah >= 1) sb.append(' ').append(I18n.tIn(language, "Used {0} milliamp hours.", Math.round(f.mah)));
        if (ok(f.km) && f.km >= FlightTotals.MIN_KM_FOR_EFFICIENCY && ok(f.wh) && f.wh > 0) {
            sb.append(' ').append(I18n.tIn(language, "Efficiency {0} watt hours per kilometer.", String.format(Locale.US, "%.1f", f.wh / f.km)));
        }
        if (ok(f.minCell) && f.minCell > 1.0) sb.append(' ').append(I18n.tIn(language, "Lowest cell {0} volts.", String.format(Locale.US, "%.2f", f.minCell)));
        if (ok(f.restCell) && f.restCell > 1.0) sb.append(' ').append(I18n.tIn(language, "Resting cell {0} volts.", String.format(Locale.US, "%.2f", f.restCell)));
        if (pack != null && !pack.isEmpty() && flightNumber > 0) sb.append(' ').append(I18n.tIn(language, "Pack {0}, flight {1}.", pack, flightNumber));
        return sb.toString();
    }
}
