// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * The timing of the splash screen: when each part appears and how it moves. Plain Java, so that the order and the limits can be tested on a
 * PC; the drawing is in SplashView. Times are milliseconds from the start.
 */
public final class SplashTimeline {
    private SplashTimeline() {}

    public static final int TOTAL_MS = 2900;

    public static final long FRAME_START = 150, FRAME_END = 1000;       // the frame around the logo draws itself
    public static final long BRACKET_START = 900, BRACKET_END = 1150;   // and its two corner brackets
    public static final long GLYPH_START = 350, GLYPH_END = 1150;       // the logo fades and grows in
    public static final long SCAN_START = 700, SCAN_END = 1500;         // a light passes over it
    public static final long CHIP_START = 700, CHIP_END = 1050;         // the RADIOMASTER AX12 tag
    public static final long LETTER_FIRST = 800, LETTER_GAP = 90, LETTER_DUR = 520;
    public static final int LETTERS = 6;                                // A X - H U D
    public static final long RULE_START = 1450, RULE_END = 1950;        // the line under the title
    public static final long TAG1_START = 1750, TAG1_END = 2150;
    public static final long TAG2_START = 1900, TAG2_END = 2300;
    public static final long SHINE_START = 1950, SHINE_END = 2650;      // a light passes over the title
    public static final long VERSION_START = 2100, VERSION_END = 2750;  // the version chip draws itself

    /** How far along (0 to 1) a part is at time t, for a part that runs from start to end. */
    public static float seg(long t, long start, long end) {
        if (t <= start) return 0f;
        if (t >= end) return 1f;
        return (float) (t - start) / (float) (end - start);
    }

    /** Fast at first, slow at the end. */
    public static float easeOut(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        float u = 1f - c;
        return 1f - u * u * u;
    }

    /** Slow, fast, slow. */
    public static float easeInOut(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return c < 0.5f ? 4f * c * c * c : 1f - (float) Math.pow(-2f * c + 2f, 3) / 2f;
    }

    /** 0 to 1 and back to 0: for a light that passes. */
    public static float bell(float x) {
        float c = Math.max(0f, Math.min(1f, x));
        return (float) Math.sin(Math.PI * c);
    }

    public static long letterStart(int i) { return LETTER_FIRST + i * LETTER_GAP; }

    public static float letter(int i, long t) { return seg(t, letterStart(i), letterStart(i) + LETTER_DUR); }

    /** A slow breath of the glow behind the logo, 0 to 1. */
    public static float breathe(long t) { return 0.5f + 0.5f * (float) Math.sin(2.0 * Math.PI * t / 2600.0); }
}
