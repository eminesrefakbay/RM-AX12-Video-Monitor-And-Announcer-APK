// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * The hold that opens the layout editor: one finger on one spot for five seconds. A finger that moves away from the spot (a swipe, a
 * scroll, a slow drag) cancels it, and so does a second finger or letting go. Plain Java, so that the rule can be tested.
 */
public final class HoldGesture {
    private HoldGesture() {}

    /** How long the finger must stay. */
    public static final long HOLD_MS = 5000;

    /** How far the finger may wander from where it came down and still count as the same spot (a still finger rolls a little). */
    public static final int SLOP_DP = 24;

    /** True when the finger has left the spot: dx and dy are the distance from where it came down, slopPx the allowance. */
    public static boolean moved(double dx, double dy, double slopPx) {
        return dx * dx + dy * dy > slopPx * slopPx;
    }
}
