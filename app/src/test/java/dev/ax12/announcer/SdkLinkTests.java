// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/** Tests of the SDK link choice (port and baud rate). Run: java dev.ax12.announcer.SdkLinkTests */
public final class SdkLinkTests {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL: " + what); }
    }

    public static void main(String[] args) {
        check(SdkLink.DEFAULT == 0, "default is the first choice");
        check(SdkLink.port(0).equals("ttyS0") && SdkLink.baud(0) == 921600, "default: ttyS0 at 921600, as in RadioMaster's example");
        check(SdkLink.port(1).equals("ttyS0") && SdkLink.baud(1) == 1870000, "choice 1: ttyS0 at 1870000");
        check(SdkLink.port(2).equals("ttyS0") && SdkLink.baud(2) == 400000, "choice 2: ttyS0 at 400000");
        check(SdkLink.port(3).isEmpty() && SdkLink.baud(3) == SdkLink.SDK_DEFAULT_BAUD, "choice 3: no port (the SDK scans), the SDK's own baud rate");
        check(SdkLink.namesPort(0) && SdkLink.namesPort(1) && SdkLink.namesPort(2) && !SdkLink.namesPort(3), "only the last choice scans");
        check(SdkLink.clamp(-1) == 0 && SdkLink.clamp(4) == 0 && SdkLink.clamp(99) == 0 && SdkLink.clamp(2) == 2, "a value out of range falls back to the default");
        check(SdkLink.port(-5).equals("ttyS0") && SdkLink.baud(77) == 921600, "getters are safe for a damaged value");
        int c = 0;
        boolean[] seen = new boolean[SdkLink.COUNT];
        for (int i = 0; i < SdkLink.COUNT; i++) { seen[c] = true; c = SdkLink.next(c); }
        boolean all = true;
        for (boolean s : seen) all &= s;
        check(all && c == 0, "tapping visits every choice and comes back to the start");
        check(SdkLink.next(-3) == 1 && SdkLink.next(3) == 0, "next from a bad value starts from the default");
        check(SdkLink.label(0, "scan").equals("ttyS0 921600"), "label shows port and baud");
        check(SdkLink.label(1, "scan").equals("ttyS0 1870000"), "label shows 1870000");
        check(SdkLink.label(3, "Automatic").equals("Automatic"), "label of the scanning choice is the supplied word");
        System.out.println("SdkLinkTests: " + checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
