// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/** Tests for moving and resizing cards: where they are, how big, and that they never leave the screen. */
public class LayoutTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-6; }
    static boolean near(double a, double b, double eps) { return Math.abs(a - b) <= eps; }

    public static void main(String[] args) {
        double W = 1280, H = 720, bw = 200, bh = 100;
        // ---------- place and size ----------
        TileLayout.Rect r = TileLayout.rect(W, H, bw, bh, 0, 0, 1.0);
        check(near(r.left, 0) && near(r.top, 0) && near(r.width, 200) && near(r.height, 100), "place 0,0 is the top left corner");
        r = TileLayout.rect(W, H, bw, bh, 1000, 1000, 1.0);
        check(near(r.right(), W) && near(r.bottom(), H), "place 1000,1000 is the bottom right corner, flush with the screen");
        r = TileLayout.rect(W, H, bw, bh, 500, 500, 1.0);
        check(near(r.left, (W - bw) / 2) && near(r.top, (H - bh) / 2), "place 500,500 is the centre");
        r = TileLayout.rect(W, H, bw, bh, 1000, 0, 2.0);
        check(near(r.width, 400) && near(r.height, 200) && near(r.right(), W) && near(r.top, 0), "a bigger card stays flush with the edge it was placed on");
        r = TileLayout.rect(W, H, bw, bh, 5000, -20, 1.0);
        check(near(r.right(), W) && near(r.top, 0), "places outside 0..1000 are kept inside");

        // ---------- the same place on another screen ----------
        TileLayout.Rect small = TileLayout.rect(800, 480, bw, bh, 1000, 1000, 1.0);
        TileLayout.Rect big = TileLayout.rect(1920, 1080, bw, bh, 1000, 1000, 1.0);
        check(near(small.right(), 800) && near(small.bottom(), 480) && near(big.right(), 1920) && near(big.bottom(), 1080), "a card placed in a corner stays in that corner on any screen");

        // ---------- scale limits ----------
        check(near(TileLayout.maxScale(W, H, bw, bh), 2.5), "a small card can grow to 2.5 times");
        check(near(TileLayout.maxScale(W, H, 1000, 100), 1.28) && near(TileLayout.maxScale(W, H, 100, 720), 1.0), "a card never gets bigger than the screen");
        check(near(TileLayout.clampScale(0.1, W, H, bw, bh), 0.5) && near(TileLayout.clampScale(9, W, H, bw, bh), 2.5) && near(TileLayout.clampScale(1.3, W, H, bw, bh), 1.3), "scale is kept between 0.5 and 2.5");
        check(near(TileLayout.maxScale(W, H, 0, 0), 2.5) && TileLayout.maxScale(100, 100, 5000, 5000) >= 0.5, "odd sizes do not break");
        TileLayout.Rect huge = TileLayout.rect(W, H, 2000, 100, 500, 500, 1.0);
        check(huge.width <= W + 1e-9 && huge.left >= 0, "a card wider than the screen is scaled down to fit: " + huge.width);

        // ---------- from a spot on the screen back to a place ----------
        int[] f = TileLayout.fractions(W, H, bw, bh, 1.0, 0, 0);
        check(f[0] == 0 && f[1] == 0, "top left corner");
        f = TileLayout.fractions(W, H, bw, bh, 1.0, W - bw, H - bh);
        check(f[0] == 1000 && f[1] == 1000, "bottom right corner");
        f = TileLayout.fractions(W, H, bw, bh, 1.0, 5000, -300);
        check(f[0] == 1000 && f[1] == 0, "a spot off the screen is pulled back");
        f = TileLayout.fractions(W, H, 1280, 100, 1.0, 40, 10);
        check(f[0] == 0, "a card as wide as the screen has no room sideways");
        // round trip: place -> rect -> place
        int bad = 0;
        for (int fx = 0; fx <= 1000; fx += 37) for (int fy = 0; fy <= 1000; fy += 41) for (double s : new double[]{0.5, 1.0, 1.7, 2.5}) {
            TileLayout.Rect q = TileLayout.rect(W, H, bw, bh, fx, fy, s);
            int[] back = TileLayout.fractions(W, H, bw, bh, s, q.left, q.top);
            if (Math.abs(back[0] - fx) > 1 || Math.abs(back[1] - fy) > 1) bad++;
        }
        check(bad == 0, "place -> rectangle -> place gives the same place everywhere (" + bad + " differences)");
        // moving by dragging: the card follows the finger exactly
        TileLayout.Rect start = TileLayout.rect(W, H, bw, bh, 300, 400, 1.0);
        double[] moved = TileLayout.keepOnScreen(start.left + 150, start.top - 60, bw, bh, W, H);
        int[] nf = TileLayout.fractions(W, H, bw, bh, 1.0, moved[0], moved[1]);
        TileLayout.Rect after = TileLayout.rect(W, H, bw, bh, nf[0], nf[1], 1.0);
        check(near(after.left, start.left + 150, 1.5) && near(after.top, start.top - 60, 1.5), "dragging 150 right and 60 up moves the card by that much");

        // ---------- keeping on screen and snapping ----------
        double[] k = TileLayout.keepOnScreen(-50, -20, bw, bh, W, H);
        check(near(k[0], 0) && near(k[1], 0), "dragged past the top left: stops at the edge");
        k = TileLayout.keepOnScreen(2000, 2000, bw, bh, W, H);
        check(near(k[0], W - bw) && near(k[1], H - bh), "dragged past the bottom right: stops at the edge");
        double[] sn = TileLayout.snap(10, 12, bw, bh, W, H, 16);
        check(near(sn[0], 0) && near(sn[1], 0), "close to the top left: snaps to it");
        sn = TileLayout.snap(W - bw - 9, H - bh - 5, bw, bh, W, H, 16);
        check(near(sn[0], W - bw) && near(sn[1], H - bh), "close to the bottom right: snaps to it");
        sn = TileLayout.snap(100, 200, bw, bh, W, H, 16);
        check(near(sn[0], 100) && near(sn[1], 200), "far from the edges: untouched");
        sn = TileLayout.snap(10, 300, bw, bh, W, H, 16);
        check(near(sn[0], 0) && near(sn[1], 300), "snaps one direction at a time");

        // ---------- resizing with the corner handle ----------
        double s1 = TileLayout.scaleFromDrag(W, H, bw, bh, 100, 100 + 400);
        check(near(s1, 2.0), "dragging the handle to twice the width: scale 2.0");
        check(near(TileLayout.scaleFromDrag(W, H, bw, bh, 100, 100 + 20), 0.5) && near(TileLayout.scaleFromDrag(W, H, bw, bh, 100, 90), 0.5), "a tiny or backwards drag stops at 0.5");
        check(near(TileLayout.scaleFromDrag(W, H, bw, bh, 100, 5000), 2.5), "a huge drag stops at 2.5");
        check(near(TileLayout.scaleFromDrag(W, H, 600, 300, 100, 5000), 2.4) || TileLayout.scaleFromDrag(W, H, 600, 300, 100, 5000) <= 2.4 + 1e-9, "and at what fits the screen");
        check(near(TileLayout.scaleFromDrag(W, H, 0, 0, 0, 100), 1.0), "a card without size does not break");

        // ---------- hitting a card ----------
        TileLayout.Rect c = new TileLayout.Rect(100, 100, 200, 100);
        check(TileLayout.contains(c, 150, 150) && TileLayout.contains(c, 100, 100) && TileLayout.contains(c, 300, 200) && !TileLayout.contains(c, 99, 150) && !TileLayout.contains(c, 150, 201), "inside and outside");
        check(TileLayout.onHandle(c, 300, 200, 20) && TileLayout.onHandle(c, 310, 190, 20) && !TileLayout.onHandle(c, 150, 150, 20) && !TileLayout.onHandle(c, 330, 230, 20), "the handle is a circle at the bottom right corner");

        // ---------- stored as whole percent ----------
        check(TileLayout.toPercent(1.0) == 100 && TileLayout.toPercent(1.234) == 123 && TileLayout.toPercent(0.2) == 50 && TileLayout.toPercent(9) == 250, "scale to percent");
        check(near(TileLayout.fromPercent(100), 1.0) && near(TileLayout.fromPercent(25), 0.5) && near(TileLayout.fromPercent(999), 2.5) && near(TileLayout.fromPercent(150), 1.5), "percent to scale");

        // ---------- in the settings file ----------
        SettingsFile.Summary s = SettingsFile.inspect("I\tstatsX\t250\nI\tstatsY\t900\nI\tstatsScale\t130\nI\tradarX\t-1\nI\tsticksScale\t250\n");
        check(s.usable && s.ignored == 0 && s.entries == 5, "card places and sizes are read: " + s.message);
        check(SettingsFile.inspect("B\tfill\ttrue\nI\tstatsX\t1001\nI\tstatsScale\t49\nI\tchartY\t-2\nI\tcheckScale\t251\n").ignored == 4, "out of range places and sizes are ignored");
        check(Integer.valueOf(-1).equals(SettingsFile.defaultOf("statsX")) && Integer.valueOf(100).equals(SettingsFile.defaultOf("chartScale")) && SettingsFile.isKnown("sticksY"), "defaults: in its corner, normal size");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
