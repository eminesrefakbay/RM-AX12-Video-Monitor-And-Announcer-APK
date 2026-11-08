// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.Arrays;

/** Tests of the approval window: its time rules, the warnings and the words. */
public class WindowTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static int s(int h, int m) { return h * 3600 + m * 60; }

    public static void main(String[] args) throws Exception {
        I18n.load(I18nTests.FILES, "en");

        // ---------- reading and showing times ----------
        check(ApprovalWindow.parseHm("16:30") == 990 && ApprovalWindow.parseHm("9:05") == 545 && ApprovalWindow.parseHm(" 0:00 ") == 0 && ApprovalWindow.parseHm("23:59") == 1439, "good times");
        check(ApprovalWindow.parseHm("24:00") == -1 && ApprovalWindow.parseHm("12:60") == -1 && ApprovalWindow.parseHm("1630") == -1 && ApprovalWindow.parseHm("12:5") == -1
            && ApprovalWindow.parseHm("") == -1 && ApprovalWindow.parseHm(null) == -1 && ApprovalWindow.parseHm("ab:cd") == -1 && ApprovalWindow.parseHm("-1:30") == -1, "bad times");
        check(ApprovalWindow.hm(990).equals("16:30") && ApprovalWindow.hm(545).equals("09:05") && ApprovalWindow.hm(0).equals("00:00") && ApprovalWindow.hm(-1).equals("--:--") && ApprovalWindow.hm(1440).equals("--:--"), "times as text");
        check(ApprovalWindow.clock(4325).equals("1:12:05") && ApprovalWindow.clock(725).equals("12:05") && ApprovalWindow.clock(59.9).equals("0:59") && ApprovalWindow.clock(-5).equals("0:00") && ApprovalWindow.clock(Double.NaN).equals("0:00") && ApprovalWindow.clock(36000).equals("10:00:00"), "the clock");

        // ---------- a window within a day ----------
        ApprovalWindow w = new ApprovalWindow();
        check(w.state(s(17, 0)) == ApprovalWindow.State.OFF && Double.isNaN(w.leftSeconds(s(17, 0))) && w.chipText(s(17, 0)).isEmpty(), "off until it is configured");
        w.configure(true, 16 * 60, 18 * 60, "30,15,10,5,2,1");
        check(w.state(s(15, 0)) == ApprovalWindow.State.BEFORE && w.startsInSeconds(s(15, 0)) == 3600 && Double.isNaN(w.leftSeconds(s(15, 0))), "before: opens in an hour");
        check(w.state(s(16, 0)) == ApprovalWindow.State.OPEN && w.leftSeconds(s(16, 0)) == 7200, "open at the start");
        check(w.leftSeconds(s(17, 30)) == 1800 && w.leftSeconds(s(17, 59) + 59) == 1, "time left");
        check(w.state(s(18, 0)) == ApprovalWindow.State.ENDED && w.endedSecondsAgo(s(18, 0)) == 0 && w.endedSecondsAgo(s(19, 0)) == 3600, "ended at the end, and for how long");
        check(w.state(s(12, 0)) == ApprovalWindow.State.BEFORE && w.state(s(23, 0)) == ApprovalWindow.State.ENDED && w.state(s(3, 0)) == ApprovalWindow.State.ENDED && w.state(s(6, 0)) == ApprovalWindow.State.BEFORE, "the rest of the day: closer to the last end means ended, closer to the next start means before");
        check(w.chipText(s(17, 0)).equals("Window 1:00:00 left") && w.chipText(s(15, 30)).equals("Window opens in 30:00") && w.chipText(s(18, 5)).equals("Window ended 5:00 ago"), "the chip: " + w.chipText(s(17, 0)));

        // ---------- a window across midnight ----------
        ApprovalWindow n = new ApprovalWindow();
        n.configure(true, 23 * 60, 60, "");
        check(n.state(s(23, 30)) == ApprovalWindow.State.OPEN && n.leftSeconds(s(23, 30)) == 5400, "before midnight: an hour and a half");
        check(n.state(s(0, 30)) == ApprovalWindow.State.OPEN && n.leftSeconds(s(0, 30)) == 1800, "after midnight: half an hour");
        check(n.state(s(2, 0)) == ApprovalWindow.State.ENDED && n.endedSecondsAgo(s(2, 0)) == 3600 && n.state(s(22, 0)) == ApprovalWindow.State.BEFORE && n.startsInSeconds(s(22, 0)) == 3600, "outside it");
        check(n.state(s(1, 0)) == ApprovalWindow.State.ENDED, "closed at one");
        ApprovalWindow same = new ApprovalWindow();
        same.configure(true, 600, 600, "");
        check(same.state(s(10, 0)) == ApprovalWindow.State.OPEN && same.leftSeconds(s(10, 0)) == 86400, "an end equal to the start is the next day's: a full day");

        // ---------- the marks to warn at ----------
        check(Arrays.equals(ApprovalWindow.parseWarn("30,15,10,5,2,1"), new int[]{30, 15, 10, 5, 2, 1}), "the default marks");
        check(Arrays.equals(ApprovalWindow.parseWarn("5 15 5; 30"), new int[]{30, 15, 5}), "any separators, sorted, no repeats");
        check(ApprovalWindow.parseWarn("").length == 0 && ApprovalWindow.parseWarn("  ").length == 0, "none is allowed");
        check(ApprovalWindow.parseWarn("0") == null && ApprovalWindow.parseWarn("241") == null && ApprovalWindow.parseWarn("abc") == null && ApprovalWindow.parseWarn("5,x") == null && ApprovalWindow.parseWarn("1.5") == null
            && ApprovalWindow.parseWarn(null) == null && ApprovalWindow.parseWarn("1,2,3,4,5,6,7,8,9") == null, "bad marks");
        check(ApprovalWindow.validWarn("10,5") && ApprovalWindow.validWarn("") && !ApprovalWindow.validWarn("0") && ApprovalWindow.joinWarn(new int[]{30, 5}).equals("30,5"), "valid or not, and as text");
        ApprovalWindow bad = new ApprovalWindow();
        bad.configure(true, 600, 700, "nonsense");
        check(Arrays.equals(bad.warnings(), new int[]{30, 15, 10, 5, 2, 1}), "wrong marks fall back to the defaults");
        bad.configure(true, -5, 99999, "5");
        check(bad.startMinute() == 0 && bad.endMinute() == 1439 && Arrays.equals(bad.warnings(), new int[]{5}), "times are kept inside the day");

        // ---------- crossing a mark ----------
        w.configure(true, 16 * 60, 18 * 60, "30,15,10,5,2,1");
        check(Arrays.equals(w.crossed(1801, 1799), new int[]{30}), "the half hour");
        check(Arrays.equals(w.crossed(1000, 500), new int[]{15, 10}), "two marks in one step: the bigger first");
        check(w.crossed(1799, 1700).length == 0 && w.crossed(1800, 1800).length == 0, "no mark crossed");
        check(w.crossed(Double.NaN, 100).length == 0 && w.crossed(100, Double.NaN).length == 0, "the first look announces nothing");
        check(Arrays.equals(w.crossed(121, 119), new int[]{2}) && Arrays.equals(w.crossed(61, 59), new int[]{1}) && Arrays.equals(w.crossed(60.5, 60), new int[]{1}), "the last minutes");
        int announced = 0;                                         // walking through the whole window second by second says each mark once
        double prev = Double.NaN;
        for (int sec = s(16, 0); sec < s(18, 0); sec++) {
            double left = w.leftSeconds(sec);
            announced += w.crossed(prev, left).length;
            prev = left;
        }
        check(announced == 6, "each of the six marks once through a window: " + announced);

        // ---------- the words ----------
        check(ApprovalWindow.minutesLeftSpeech("en", 15).equals("15 minutes left in the approval window.") && ApprovalWindow.minutesLeftSpeech("en", 1).equals("One minute left in the approval window."), "minutes left");
        check(ApprovalWindow.endedSpeech("en").equals("The approval window has ended. Land now.") && ApprovalWindow.outsideSpeech("en").equals("Warning: you are outside the approval window."), "ended and outside");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
