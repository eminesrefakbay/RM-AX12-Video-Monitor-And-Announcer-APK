// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * Keeps count of the app's screens that are on show, to decide when the radio link may be shut down: not while any screen of the
 * app is visible (the main screen, the rules editor), only when the app has left the display. Leaving to a file picker or a rebuild
 * can ask for a short wait first (see {@link #hold}). Plain Java, tested on a PC.
 */
public final class AppVisibility {

    /** An action that says "I am about to leave and come back" is remembered for this long. */
    public static final long HOLD_VALID_MS = 3000;

    private int visible = 0;
    private long holdMs = 0;
    private long holdUntil = 0;

    /** Our own action is about to take the app off the display for a moment: keep the radio link for ms if that happens now. */
    public synchronized void hold(long nowMs, long ms) {
        holdMs = ms;
        holdUntil = nowMs + HOLD_VALID_MS;
    }

    /** A screen of the app became visible (the caller then cancels any shut-down that is waiting). */
    public synchronized void started() { visible++; }

    /**
     * A screen of the app is no longer visible.
     * @return -1 when another screen of the app is still visible (keep the radio link); 0 when the app has left the display and
     *         the link is to be shut down now; above 0 when it is to be shut down after that many milliseconds unless a screen
     *         of the app becomes visible first
     */
    public synchronized long stopped(long nowMs) {
        if (visible > 0) visible--;
        if (visible > 0) return -1;
        long wait = nowMs <= holdUntil ? holdMs : 0;
        holdMs = 0;
        holdUntil = 0;
        return wait;
    }

    public synchronized int visibleScreens() { return visible; }
}
