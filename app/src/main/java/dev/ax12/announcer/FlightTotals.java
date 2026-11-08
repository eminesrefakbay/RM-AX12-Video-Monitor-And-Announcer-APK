// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * What a flight added up to: the distance flown (the ground speed over time), the energy (volts times amps over time) and the capacity.
 * It is fed the values of every tick or log row and takes the time between two of them; a gap longer than the allowance is not counted,
 * because nobody knows what happened in it. Plain Java, so that it can be tested on a PC.
 */
public final class FlightTotals {
    /** Efficiency needs some distance to mean anything. */
    public static final double MIN_KM_FOR_EFFICIENCY = 0.2;

    private final long maxGapMs;
    private long lastMs = -1;
    private double seconds, meters, wattSeconds, mahIntegrated;
    private double capaMax = Double.NaN;

    public FlightTotals() { this(1500); }

    public FlightTotals(long maxGapMs) { this.maxGapMs = maxGapMs; }

    public void reset() {
        lastMs = -1;
        seconds = meters = wattSeconds = mahIntegrated = 0;
        capaMax = Double.NaN;
    }

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    /** One sample: ground speed in km/h, battery volts, amps, and the capacity used in mAh (any of them may be NaN). */
    public void update(long nowMs, double speedKmh, double vbat, double curr, double capaMah) {
        if (ok(capaMah) && capaMah >= 0) capaMax = ok(capaMax) ? Math.max(capaMax, capaMah) : capaMah;
        if (lastMs < 0) { lastMs = nowMs; return; }
        long dtMs = nowMs - lastMs;
        lastMs = nowMs;
        if (dtMs <= 0 || dtMs > maxGapMs) return;
        double dt = dtMs / 1000.0;
        seconds += dt;
        if (ok(speedKmh) && speedKmh >= 0) meters += speedKmh / 3.6 * dt;
        if (ok(curr)) {
            double amps = Math.max(0, curr);
            mahIntegrated += amps * dt / 3.6;
            if (ok(vbat) && vbat > 0) wattSeconds += vbat * amps * dt;
        }
    }

    public double seconds() { return seconds; }
    public double distanceKm() { return meters / 1000.0; }
    public double energyWh() { return wattSeconds / 3600.0; }

    /** The capacity the flight controller counted, or, without it, the current added up over time; NaN when neither exists. */
    public double mahUsed() {
        if (ok(capaMax)) return capaMax;
        return mahIntegrated > 0 ? mahIntegrated : Double.NaN;
    }

    public double whPerKm() { return distanceKm() >= MIN_KM_FOR_EFFICIENCY && energyWh() > 0 ? energyWh() / distanceKm() : Double.NaN; }

    public double mahPerKm() {
        double mah = mahUsed();
        return distanceKm() >= MIN_KM_FOR_EFFICIENCY && ok(mah) && mah > 0 ? mah / distanceKm() : Double.NaN;
    }
}
