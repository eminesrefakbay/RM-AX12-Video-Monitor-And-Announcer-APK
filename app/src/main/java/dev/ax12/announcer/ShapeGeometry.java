// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * The shapes of cards, tiles and rows: where the corners go for each style. Plain Java, so that the geometry can be tested on a PC; the
 * drawing is in CardStyle and FrameDrawable.
 */
public final class ShapeGeometry {
    private ShapeGeometry() {}

    public static final int CHAMFER2 = 0;     // two opposite corners cut (top left and bottom right): the default
    public static final int CHAMFER4 = 1;     // all four corners cut a little
    public static final int BRACKETS = 2;     // a quiet fill with bright brackets at the corners
    public static final int SOFT = 3;         // rounded corners
    public static final int PLAIN = 4;        // sharp rectangles
    public static final int COUNT = 5;

    /** A stored value that makes no sense falls back to the default. */
    public static int clampStyle(int v) { return v >= 0 && v < COUNT ? v : CHAMFER2; }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** How far a corner is cut: about a sixth of the short side, but between 3 and 9 dp. */
    public static float cut(float w, float h, float density) { return clamp(Math.min(w, h) * 0.16f, 3f * density, 9f * density); }

    /** The radius of a rounded corner. */
    public static float radius(float w, float h, float density) { return clamp(Math.min(w, h) * 0.14f, 3f * density, 8f * density); }

    /** The length of the arms of a corner bracket. */
    public static float bracket(float w, float h, float density) { return clamp(Math.min(w, h) * 0.28f, 8f * density, 16f * density); }

    public static boolean cutsCorners(int style) { return style == CHAMFER2 || style == CHAMFER4; }

    /** The outline as x,y pairs, clockwise from the top left; null for the styles that are drawn another way (rounded, plain, brackets). */
    public static float[] outline(int style, float l, float t, float r, float b, float cut) {
        if (style == CHAMFER2) {
            return new float[]{l + cut, t, r, t, r, b - cut, r - cut, b, l, b, l, t + cut};
        }
        if (style == CHAMFER4) {
            float c = cut * 0.75f;
            return new float[]{l + c, t, r - c, t, r, t + c, r, b - c, r - c, b, l + c, b, l, b - c, l, t + c};
        }
        return null;
    }

    /** Four corner brackets, each three points (arm, corner, arm) as x,y pairs: top left, top right, bottom right, bottom left. */
    public static float[] brackets(float l, float t, float r, float b, float len) {
        return new float[]{
            l, t + len, l, t, l + len, t,
            r - len, t, r, t, r, t + len,
            r, b - len, r, b, r - len, b,
            l + len, b, l, b, l, b - len};
    }

    /** The area of an outline (the shoelace formula), for the tests. */
    public static double area(float[] p) {
        double a = 0;
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a += (double) p[2 * i] * p[2 * j + 1] - (double) p[2 * j] * p[2 * i + 1];
        }
        return Math.abs(a) / 2.0;
    }
}
