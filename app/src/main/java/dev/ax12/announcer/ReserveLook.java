// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * How the return reserve card looks and how long it stays: its size, its opacity, its corner and the time after which it
 * goes away (it comes back when the state changes, so a warning is never missed). Plain Java, tested on a PC.
 */
public final class ReserveLook {

    public static final double[] SIZE = {0.6, 0.8, 1.0};
    public static final float[] ALPHA = {0.5f, 0.75f, 1f};
    public static final int[] HIDE_SECONDS = {0, 5, 10, 30};
    public static final int DEFAULT_SIZE = 1;
    public static final int DEFAULT_OPACITY = 1;
    public static final int DEFAULT_HIDE = 2;
    public static final int DEFAULT_POS = 0;

    private ReserveLook() {}

    public static double scale(int size) { return SIZE[clamp(size, SIZE.length - 1)]; }

    public static float alpha(int opacity) { return ALPHA[clamp(opacity, ALPHA.length - 1)]; }

    public static int hideSeconds(int index) { return HIDE_SECONDS[clamp(index, HIDE_SECONDS.length - 1)]; }

    private static int clamp(int v, int max) { return v < 0 ? 0 : Math.min(v, max); }

    /** Decides when the card shows: always, or for a number of seconds after it appears or its state changes. */
    public static final class AutoHide {
        private long since = -1;
        private int lastState = Integer.MIN_VALUE;

        public void reset() {
            since = -1;
            lastState = Integer.MIN_VALUE;
        }

        /** @param state any number that changes when the reserve changes state (OK, warning, critical). */
        public boolean visible(long nowMs, int state, int hideSeconds) {
            if (since < 0 || state != lastState) {
                since = nowMs;
                lastState = state;
            }
            if (hideSeconds <= 0) return true;
            return nowMs - since < hideSeconds * 1000L;
        }
    }
}
