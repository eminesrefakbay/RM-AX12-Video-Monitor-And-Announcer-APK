// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The distance and the energy of a logged flight, worked out from its rows (for logs written before the log footer held them). */
public final class LogEfficiency {
    private LogEfficiency() {}

    private static final Map<String, double[]> CACHE = new ConcurrentHashMap<String, double[]>();
    private static final int MAX_CELLS = 6000000;

    /** {km, wh, mah}: NaN where the log has no such columns. */
    public static double[] compute(FlightLog log) {
        int speed = log.indexOf("gps_speed"), vbat = log.indexOf("vbat"), curr = log.indexOf("curr"), capa = log.indexOf("capa");
        FlightTotals t = new FlightTotals(3000);
        for (int r = 0; r < log.rows; r++) {
            t.update(log.time[r],
                speed < 0 ? Double.NaN : log.value(r, speed),
                vbat < 0 ? Double.NaN : log.value(r, vbat),
                curr < 0 ? Double.NaN : log.value(r, curr),
                capa < 0 ? Double.NaN : log.value(r, capa));
        }
        boolean hasDistance = speed >= 0 && t.distanceKm() > 0;
        boolean hasEnergy = vbat >= 0 && curr >= 0 && t.energyWh() > 0;
        return new double[]{hasDistance ? t.distanceKm() : Double.NaN, hasEnergy ? t.energyWh() : Double.NaN, t.mahUsed()};
    }

    /** Fills the distance and the energy of a list entry that lacks them, by reading its file (once per file while the app runs). */
    public static void fill(LogStore.Entry e) {
        if (!Double.isNaN(e.distanceKm) && !Double.isNaN(e.energyWh)) return;
        if (e.file == null || !e.clean || e.rows < 2) return;
        String key = e.name + "|" + e.size;
        double[] r = CACHE.get(key);
        if (r == null) {
            try {
                r = compute(LogFormat.readFile(e.file, MAX_CELLS));
            } catch (IOException | RuntimeException ex) {
                r = new double[]{Double.NaN, Double.NaN, Double.NaN};
            }
            CACHE.put(key, r);
        }
        if (Double.isNaN(e.distanceKm)) e.distanceKm = r[0];
        if (Double.isNaN(e.energyWh)) e.energyWh = r[1];
    }

    /** True when the entry still lacks something that reading the file could give. */
    public static boolean missing(LogStore.Entry e) {
        return (Double.isNaN(e.distanceKm) || Double.isNaN(e.energyWh)) && e.clean && e.rows >= 2 && !CACHE.containsKey(e.name + "|" + e.size);
    }
}
