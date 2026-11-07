// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/** Tests of the pre-flight card's X: when a closed card stays closed and when it comes back. */
public class CheckCardTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) {
        CheckCardState s = new CheckCardState();
        check(!s.isClosed() && s.allowed(false) && s.allowed(true), "a new card is shown");

        s.close();
        check(s.isClosed() && !s.allowed(false), "after the X it is not shown");
        check(s.allowed(true), "while the layout is edited it is shown, so that it can be placed");

        // it stays closed while the aircraft is disarmed, without arming
        for (int i = 0; i < 1000; i++) s.update(false);
        check(s.isClosed() && !s.allowed(false), "it stays closed without arming, however long it takes");

        // an armed aircraft ends the pre-flight period: the card is back for the next flight
        s.update(true);
        check(!s.isClosed() && s.allowed(false), "arming brings it back for the next pre-flight check");
        s.update(false);
        check(s.allowed(false), "and it is shown after the flight");

        // closing, then a new aircraft, pack or set of checks
        s.close();
        s.reopen();
        check(!s.isClosed() && s.allowed(false), "a new aircraft, pack or set of checks brings it back");

        // closing twice does no harm; reopening an open card does no harm
        s.close(); s.close();
        check(s.isClosed(), "closing twice");
        s.reopen(); s.reopen();
        check(!s.isClosed(), "reopening twice");

        // arming while it is open changes nothing
        s.update(true); s.update(true);
        check(!s.isClosed(), "arming with an open card");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
