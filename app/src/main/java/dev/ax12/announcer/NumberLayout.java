// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * Where the characters of a number go when every digit gets a slot of the same width: a number that changes every moment (a speed, a
 * time) then never shifts sideways, even in a font whose digits have different widths (in Teko the 1 is narrow). Plain Java, so that the
 * layout can be tested on a PC; the drawing is in CardStyle.
 */
public final class NumberLayout {
    private NumberLayout() {}

    /** The advance of one character in the font that is used. */
    public interface Advance { float of(char c); }

    public static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    /** The widest digit of the font: the width of one slot. */
    public static float slot(Advance a) {
        float w = 0;
        for (char c = '0'; c <= '9'; c++) w = Math.max(w, a.of(c));
        return w;
    }

    /** The advance of a character in the number: digits take a slot, everything else its own width. */
    public static float advance(char c, float slot, Advance a) { return isDigit(c) ? slot : a.of(c); }

    public static float width(CharSequence s, float slot, Advance a) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += advance(s.charAt(i), slot, a);
        return w;
    }

    /** x of the left edge of the number for the given alignment (-1 left, 0 centre, 1 right) of the point x. */
    public static float left(int align, float x, float width) {
        return align > 0 ? x - width : align == 0 ? x - width / 2f : x;
    }
}
