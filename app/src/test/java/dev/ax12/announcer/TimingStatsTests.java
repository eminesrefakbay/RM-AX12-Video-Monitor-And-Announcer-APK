// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/** Tests for the rolling timing statistics. */
public class TimingStatsTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) {
        TimingStats t = new TimingStats(5000);
        check(t.summary(0).events == 0 && t.summary(0).perSecond == 0.0, "empty summary");

        // 60 frames per second for 6 seconds (16 or 17 ms apart)
        long now = 0; int k = 0;
        while (now < 6000) { long gap = (k++ % 3 == 0) ? 17 : 16; now += gap; t.add(now, gap); }
        TimingStats.Summary s = t.summary(now);
        check(Math.abs(s.perSecond - 60.0) < 2.0, "about 60 per second: " + s.perSecond);
        check(s.p95Ms == 17 && s.maxMs == 17, "p95 and max 17 ms: " + s.p95Ms + "/" + s.maxMs);
        check(s.events > 280 && s.events < 320, "window holds about 5 s of frames: " + s.events);

        // one long gap shows up as the worst gap, and the 95th percentile ignores a single outlier
        now += 120; t.add(now, 120);
        for (int i = 0; i < 30; i++) { now += 16; t.add(now, 16); }
        s = t.summary(now);
        check(s.maxMs == 120, "a 120 ms stall is the worst gap: " + s.maxMs);
        check(s.p95Ms <= 17, "one stall does not move the 95th percentile: " + s.p95Ms);

        // enough stalls move the percentile
        TimingStats bad = new TimingStats(5000);
        long tb = 0;
        for (int i = 0; i < 100; i++) { long gap = (i % 10 == 0) ? 80 : 16; tb += gap; bad.add(tb, gap); }
        check(bad.summary(tb).p95Ms == 80, "frequent stalls show in the 95th percentile: " + bad.summary(tb).p95Ms);

        // old samples drop out, and silence reads as zero
        TimingStats w = new TimingStats(1000);
        w.add(100, 16); w.add(200, 16);
        check(w.summary(5000).events == 0 && w.summary(5000).perSecond == 0.0, "everything older than the window is forgotten");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
