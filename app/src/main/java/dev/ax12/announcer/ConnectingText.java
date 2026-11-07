// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * What the "Connecting to the radio..." banner says. The SDK needs about 12 seconds to open the radio's serial port, so the banner
 * counts the seconds and shows a strip that fills towards that, never quite reaching the end until the radio answers; after 20
 * seconds it says what to check. Plain Java, tested on a PC.
 */
public final class ConnectingText {
    public static final double EXPECTED_S = 12.0;
    public static final double STUCK_S = 20.0;
    public static final int MAX_PERCENT = 95;

    private ConnectingText() {}

    /** How much of the strip is full: 0 to 95 percent over the expected 12 seconds. */
    public static int percent(double seconds) {
        if (!(seconds > 0)) return 0;
        return (int) Math.min(MAX_PERCENT, Math.round(seconds / EXPECTED_S * MAX_PERCENT));
    }

    /** True when it has taken so long that the user should check something. */
    public static boolean stuck(double seconds) { return seconds >= STUCK_S; }

    /** Whole seconds for the text; never negative. */
    public static int wholeSeconds(double seconds) { return seconds > 0 ? (int) seconds : 0; }
}
