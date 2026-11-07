// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * The geometry of a card the user can move and resize. A card's place is two fractions (0 to 1000) of the free space on the
 * screen (the screen minus the card), so it stays where it was meant on any screen size and can never leave the screen.
 * Its size is a scale factor on the card's own size. Plain Java, tested on a PC.
 */
public final class TileLayout {

    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.5;

    public static final class Rect {
        public final double left, top, width, height;

        public Rect(double left, double top, double width, double height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public double right() { return left + width; }
        public double bottom() { return top + height; }
    }

    private TileLayout() {}

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** The biggest scale that still fits the card on the screen. */
    public static double maxScale(double screenW, double screenH, double baseW, double baseH) {
        if (baseW <= 0 || baseH <= 0) return MAX_SCALE;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.min(screenW / baseW, screenH / baseH)));
    }

    public static double clampScale(double s, double screenW, double screenH, double baseW, double baseH) {
        return clamp(s, MIN_SCALE, maxScale(screenW, screenH, baseW, baseH));
    }

    /** Where the card is, for a place (fx, fy in 0..1000) and a scale. */
    public static Rect rect(double screenW, double screenH, double baseW, double baseH, int fx, int fy, double scale) {
        double s = clampScale(scale, screenW, screenH, baseW, baseH);
        double w = baseW * s, h = baseH * s;
        double freeX = Math.max(0, screenW - w), freeY = Math.max(0, screenH - h);
        return new Rect(clamp(fx, 0, 1000) / 1000.0 * freeX, clamp(fy, 0, 1000) / 1000.0 * freeY, w, h);
    }

    /** The place (fx, fy) that puts the card's top-left corner at (left, top) for a scale. */
    public static int[] fractions(double screenW, double screenH, double baseW, double baseH, double scale, double left, double top) {
        double s = clampScale(scale, screenW, screenH, baseW, baseH);
        double freeX = screenW - baseW * s, freeY = screenH - baseH * s;
        int fx = freeX <= 0 ? 0 : (int) Math.round(clamp(left / freeX, 0, 1) * 1000);
        int fy = freeY <= 0 ? 0 : (int) Math.round(clamp(top / freeY, 0, 1) * 1000);
        return new int[]{fx, fy};
    }

    /** The scale when the corner handle (bottom right) is dragged to touchX: the card's width follows the finger. */
    public static double scaleFromDrag(double screenW, double screenH, double baseW, double baseH, double left, double touchX) {
        if (baseW <= 0) return 1.0;
        return clampScale((touchX - left) / baseW, screenW, screenH, baseW, baseH);
    }

    /** Pulls the card to a screen edge when it is within the threshold of it. @return {left, top} */
    public static double[] snap(double left, double top, double w, double h, double screenW, double screenH, double threshold) {
        double l = left, t = top;
        if (Math.abs(left) <= threshold) l = 0;
        else if (Math.abs(screenW - (left + w)) <= threshold) l = screenW - w;
        if (Math.abs(top) <= threshold) t = 0;
        else if (Math.abs(screenH - (top + h)) <= threshold) t = screenH - h;
        return new double[]{l, t};
    }

    /** Keeps a card that is being dragged on the screen. @return {left, top} */
    public static double[] keepOnScreen(double left, double top, double w, double h, double screenW, double screenH) {
        return new double[]{clamp(left, 0, Math.max(0, screenW - w)), clamp(top, 0, Math.max(0, screenH - h))};
    }

    public static boolean contains(Rect r, double x, double y) {
        return x >= r.left && x <= r.right() && y >= r.top && y <= r.bottom();
    }

    /** Is the point on the round handle at the bottom right corner (within the radius)? */
    public static boolean onHandle(Rect r, double x, double y, double radius) {
        double dx = x - r.right(), dy = y - r.bottom();
        return dx * dx + dy * dy <= radius * radius;
    }

    /** The scale as the stored whole number of percent, and back. */
    public static int toPercent(double scale) { return (int) Math.round(clamp(scale, MIN_SCALE, MAX_SCALE) * 100); }
    public static double fromPercent(int percent) { return clamp(percent / 100.0, MIN_SCALE, MAX_SCALE); }
}
