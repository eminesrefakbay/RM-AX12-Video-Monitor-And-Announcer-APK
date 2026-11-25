// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

public class ReserveLookTests {
    static int checks = 0, failures = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL: " + what); }
    }

    public static void main(String[] a) {
        check(ReserveLook.scale(2) == 1.0, "large is the old size");
        check(ReserveLook.scale(0) < ReserveLook.scale(1), "small is smaller than medium");
        check(ReserveLook.scale(-5) == ReserveLook.scale(0) && ReserveLook.scale(9) == ReserveLook.scale(2), "size is clamped");
        check(ReserveLook.alpha(0) == 0.5f && ReserveLook.alpha(2) == 1f && ReserveLook.alpha(7) == 1f, "opacity");
        check(ReserveLook.hideSeconds(0) == 0 && ReserveLook.hideSeconds(2) == 10 && ReserveLook.hideSeconds(99) == 30, "hide times");

        ReserveLook.AutoHide h = new ReserveLook.AutoHide();
        check(h.visible(0, 0, 0) && h.visible(1_000_000, 0, 0), "always on when the time is 0");
        h.reset();
        check(h.visible(1000, 0, 10), "shows at first");
        check(h.visible(10_900, 0, 10), "still shown just before the time is up");
        check(!h.visible(11_000, 0, 10), "gone when the time is up");
        check(!h.visible(60_000, 0, 10), "stays gone");
        check(h.visible(61_000, 1, 10), "a new state brings it back");
        check(!h.visible(71_000, 1, 10), "and it goes again");
        h.reset();
        check(h.visible(100_000, 0, 5), "a reset (new flight) starts again");
        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
