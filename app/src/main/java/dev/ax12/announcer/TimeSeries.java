// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * A rolling record of one value over time: a ring of (time, value) pairs. The oldest pair is dropped when it is full.
 * A missing value is stored as NaN, so a graph can show a gap. Plain Java, tested on a PC.
 */
public final class TimeSeries {

    private final long[] times;
    private final double[] values;
    private int start;      // index of the oldest pair
    private int count;

    public TimeSeries(int capacity) {
        times = new long[Math.max(2, capacity)];
        values = new double[times.length];
    }

    public void add(long timeMs, double value) {
        int slot;
        if (count < times.length) {
            slot = (start + count) % times.length;
            count++;
        } else {
            slot = start;                         // full: overwrite the oldest
            start = (start + 1) % times.length;
        }
        times[slot] = timeMs;
        values[slot] = value;
    }

    public int count() { return count; }
    public void clear() { start = 0; count = 0; }

    /** i = 0 is the oldest. */
    public long timeAt(int i) { return times[(start + i) % times.length]; }
    public double valueAt(int i) { return values[(start + i) % times.length]; }

    /** The newest value, or NaN. */
    public double last() { return count == 0 ? Double.NaN : valueAt(count - 1); }

    /** The index of the first pair inside the window (count() when none is). */
    public int firstInWindow(long nowMs, long windowMs) {
        int i = 0;
        while (i < count && nowMs - timeAt(i) > windowMs) i++;
        return i;
    }

    /** Smallest and largest real value inside the window, or null when there is none. */
    public double[] minMax(long nowMs, long windowMs) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = firstInWindow(nowMs, windowMs); i < count; i++) {
            double v = valueAt(i);
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            if (v < lo) lo = v;
            if (v > hi) hi = v;
        }
        return lo > hi ? null : new double[]{lo, hi};
    }
}
