// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;

/** Tests for the live charts: the rolling record, the scales, the formatting and the configuration. */
public class ChartTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main(String[] args) {
        // ---------- the rolling record ----------
        TimeSeries t = new TimeSeries(5);
        check(t.count() == 0 && Double.isNaN(t.last()) && t.minMax(0, 1000) == null, "empty");
        for (int i = 0; i < 3; i++) t.add(1000L * i, 10 + i);
        check(t.count() == 3 && t.timeAt(0) == 0 && near(t.valueAt(0), 10) && near(t.last(), 12) && t.timeAt(2) == 2000, "three values in order");
        for (int i = 3; i < 8; i++) t.add(1000L * i, 10 + i);
        check(t.count() == 5 && t.timeAt(0) == 3000 && near(t.valueAt(0), 13) && near(t.last(), 17) && t.timeAt(4) == 7000, "when full the oldest are dropped, the order is kept");
        double[] mm = t.minMax(7000, 100000);
        check(mm != null && near(mm[0], 13) && near(mm[1], 17), "min and max over everything kept");
        mm = t.minMax(7000, 2000);
        check(mm != null && near(mm[0], 15) && near(mm[1], 17) && t.firstInWindow(7000, 2000) == 2, "min and max over the last two seconds only");
        check(t.minMax(60000, 2000) == null && t.firstInWindow(60000, 2000) == 5, "a window with nothing in it");
        t.clear();
        check(t.count() == 0 && Double.isNaN(t.last()), "clear");
        TimeSeries g = new TimeSeries(10);
        g.add(0, 5); g.add(200, Double.NaN); g.add(400, 7); g.add(600, Double.POSITIVE_INFINITY);
        mm = g.minMax(600, 10000);
        check(mm != null && near(mm[0], 5) && near(mm[1], 7), "gaps (NaN) and infinity do not count");
        TimeSeries onlyGap = new TimeSeries(4); onlyGap.add(0, Double.NaN);
        check(onlyGap.minMax(0, 1000) == null && onlyGap.count() == 1, "only gaps: no range, but the point is kept");
        TimeSeries big = new TimeSeries(300);          // 60 seconds at five a second
        for (int i = 0; i < 100000; i++) big.add(200L * i, i);
        check(big.count() == 300 && near(big.last(), 99999) && big.timeAt(0) == 200L * 99700, "it never grows beyond its size, however long it runs");
        check(new TimeSeries(0).count() == 0, "a tiny capacity is made usable");

        // ---------- the scales ----------
        double[] r = ChartScale.range("up_lq", 98, 100);
        check(near(r[0], 0) && near(r[1], 100), "link quality is always 0 to 100: a small wobble stays small");
        r = ChartScale.range("batpct", 40, 90); check(near(r[0], 0) && near(r[1], 100), "battery percent 0 to 100");
        r = ChartScale.range("ch5", 1000, 1000); check(near(r[0], 1000) && near(r[1], 2000), "a channel is 1000 to 2000");
        r = ChartScale.range("cell", 3.60, 3.62);
        check(r[1] - r[0] >= 1.0 && r[0] < 3.60 && r[1] > 3.62 && (r[0] + r[1]) / 2 > 3.5 && (r[0] + r[1]) / 2 < 3.7, "volts per cell never zoom in closer than a volt: " + r[0] + " .. " + r[1]);
        r = ChartScale.range("cell", 3.0, 4.2);
        check(r[0] < 3.0 && r[1] > 4.2 && r[1] - r[0] < 1.5, "a wide range gets a little padding: " + r[0] + " .. " + r[1]);
        r = ChartScale.range("curr", 2, 4); check(r[0] >= 0 && r[1] - r[0] >= 20, "current starts at zero: " + r[0] + " .. " + r[1]);
        r = ChartScale.range("curr", 0, 58); check(near(r[0], 0) && r[1] > 58 && r[1] < 70, "a flight's current: " + r[0] + " .. " + r[1]);
        r = ChartScale.range("curr", 11, 55); check(near(r[0], 0) && r[1] > 55, "current always starts at zero, so the height of a punch means something: " + r[0] + " .. " + r[1]);
        r = ChartScale.range("sats", 12, 16); check(near(r[0], 0) && r[1] >= 10, "satellites from zero");
        r = ChartScale.range("cell", 3.6, 3.9); check(r[0] > 2.5, "volts per cell do not start at zero: " + r[0]);
        r = ChartScale.range("height", 100, 101); check(r[1] - r[0] >= 20 && r[0] < 100 && r[1] > 101, "height keeps at least 20 metres of span");
        r = ChartScale.range("height", -3, 2); check(r[0] < -3, "height can go below zero: " + r[0]);
        r = ChartScale.range("gps_speed", 0, 0); check(r[0] >= 0 && r[1] >= 30, "speed from zero: " + r[0] + " .. " + r[1]);
        r = ChartScale.range("something_else", 5, 5); check(r[1] - r[0] >= 1.0, "an unknown value gets a sensible span");
        r = ChartScale.range("something_else", 1000, 1010); check(r[1] - r[0] >= 100, "bigger numbers: span grows with them: " + (r[1] - r[0]));
        for (String n : new String[]{"up_lq", "cell", "curr", "vbat", "height", "gps_speed", "up_rssi1", "yaw", "unknown"}) {
            double[] x = ChartScale.range(n, 1, 2);
            check(x[1] > x[0] && !Double.isNaN(x[0]) && !Double.isNaN(x[1]), "a valid axis for " + n);
        }

        // ---------- writing a value ----------
        check(ChartScale.format("up_lq", 99.6).equals("100") && ChartScale.format("sats", 14).equals("14") && ChartScale.format("ch5", 1999.6).equals("2000"), "whole numbers for percentages, counts and channels");
        check(ChartScale.format("cell", 3.6149).equals("3.61") && ChartScale.format("curr", 12.34).equals("12.3") && ChartScale.format("height", 123.4).equals("123") && ChartScale.format("vspeed", -2.5).equals("-2.50"), "fewer decimals for bigger numbers");
        check(ChartScale.format("cell", Double.NaN).equals("-") && ChartScale.format("x", Double.POSITIVE_INFINITY).equals("-"), "a missing value is a dash");

        // ---------- the configuration ----------
        check(ChartConfig.parse("up_lq,cell,curr").equals(java.util.Arrays.asList("up_lq", "cell", "curr")), "the default list");
        check(ChartConfig.parse(" UP_LQ , cell ,, cell,1bad,curr, up_rssi1 , sats ").equals(java.util.Arrays.asList("up_lq", "cell", "curr", "up_rssi1")), "names are lower-cased, repeated and invalid ones dropped, at most four");
        check(ChartConfig.parse(null).isEmpty() && ChartConfig.parse("").isEmpty() && ChartConfig.parse(",,").isEmpty(), "nothing is fine");
        check(ChartConfig.encode(ChartConfig.parse("a,b,c")).equals("a,b,c") && ChartConfig.encode(new ArrayList<String>()).isEmpty(), "encode");
        check(ChartConfig.valid("up_lq,cell") && ChartConfig.valid("") && ChartConfig.valid("   ") && ChartConfig.valid(ChartConfig.DEFAULT), "valid lists");
        check(!ChartConfig.valid("up_lq,up_lq") && !ChartConfig.valid("a,b,c,d,e") && !ChartConfig.valid("UP_LQ") && !ChartConfig.valid("1x") && !ChartConfig.valid("a,,b") && !ChartConfig.valid(null) && !ChartConfig.valid("a b"), "invalid lists are refused for files");
        check(ChartConfig.windowSeconds(0) == 30 && ChartConfig.windowSeconds(1) == 60 && ChartConfig.windowSeconds(2) == 120 && ChartConfig.windowSeconds(-1) == 30 && ChartConfig.windowSeconds(9) == 120, "the windows");

        // ---------- in the settings file ----------
        SettingsFile.Summary s = SettingsFile.inspect("B\tchartOn\ttrue\nI\tchartWindow\t2\nS\tchartSeries\tup_lq,cell\nI\tchartSize\t1\nI\tchartPos\t3\nI\tchartOpacity\t2\n");
        check(s.usable && s.ignored == 0 && s.entries == 6, "chart settings are read: " + s.message);
        check(SettingsFile.inspect("B\tfill\ttrue\nS\tchartSeries\tUP_LQ,\nI\tchartWindow\t3\n").ignored == 2, "a bad series list and a bad window are ignored");
        check("up_lq,cell,curr".equals(SettingsFile.defaultOf("chartSeries")) && Boolean.FALSE.equals(SettingsFile.defaultOf("chartOn")) && Integer.valueOf(1).equals(SettingsFile.defaultOf("chartWindow")), "the defaults");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
