// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests of the flight log file format and the list of fields. */
public class LogFormatTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static BufferedReader r(String s) { return new BufferedReader(new StringReader(s)); }

    public static void main(String[] args) throws Exception {
        // ---------- numbers ----------
        check(LogFormat.fmt("ch1", 1500).equals("1500"), "a whole number has no point");
        check(LogFormat.fmt("vbat", 24.6).equals("24.6"), "one decimal: " + LogFormat.fmt("vbat", 24.6));
        check(LogFormat.fmt("vbat", 24.123456).equals("24.123"), "three decimals at most: " + LogFormat.fmt("vbat", 24.123456));
        check(LogFormat.fmt("gps_lat", 36.8012345678).equals("36.8012346"), "a position has seven decimals: " + LogFormat.fmt("gps_lat", 36.8012345678));
        check(LogFormat.fmt("gps_lon", 34.6).equals("34.6000000"), "a position keeps its zeros");
        check(LogFormat.fmt("x", Double.NaN).isEmpty() && LogFormat.fmt("x", Double.POSITIVE_INFINITY).isEmpty(), "NaN and infinity are empty");
        check(LogFormat.fmt("x", -0.0001).equals("0") && LogFormat.fmt("x", -3.5).equals("-3.5"), "zero and negative numbers");
        check(LogFormat.fmt("x", 0.5).equals("0.5") && LogFormat.fmt("x", 100.0).equals("100"), "0.5 and 100");

        // ---------- text and quotes ----------
        check(LogFormat.quote("ANGLE").equals("ANGLE"), "plain text stays plain");
        check(LogFormat.quote("A,B").equals("\"A,B\""), "a comma needs quotes");
        check(LogFormat.quote("say \"hi\"").equals("\"say \"\"hi\"\"\""), "a quote is doubled");
        check(LogFormat.quote("a\nb").equals("\"a b\""), "a line break becomes a space");
        List<String> f = LogFormat.splitCsv("12,\"A,B\",\"say \"\"hi\"\"\",,x");
        check(f.size() == 5 && f.get(1).equals("A,B") && f.get(2).equals("say \"hi\"") && f.get(3).isEmpty() && f.get(4).equals("x"), "splitting a line with quotes: " + f);

        // ---------- a file written and read back ----------
        String[] cols = {"ch1", "vbat", "gps_lat", "fm"};
        boolean[] txt = {false, false, false, true};
        StringBuilder file = new StringBuilder();
        file.append("# AX-HUD flight log\n").append(LogFormat.metaLine("format", "1")).append('\n')
            .append(LogFormat.metaLine("aircraft", "Akb7custom001")).append('\n')
            .append(LogFormat.metaLine("cells", "6")).append('\n')
            .append(LogFormat.metaLine("text", "fm")).append('\n')
            .append(LogFormat.headerRow(cols)).append('\n');
        file.append(LogFormat.row(0, cols, txt, new double[]{1500, 24.6, 36.8012345, 0}, new String[]{null, null, null, "ANGLE"})).append('\n');
        file.append(LogFormat.row(100, cols, txt, new double[]{1600, Double.NaN, 36.8012346, 0}, new String[]{null, null, null, "A,B"})).append('\n');
        file.append(LogFormat.row(200, cols, txt, new double[]{1700, 24.5, Double.NaN, 0}, new String[]{null, null, null, null})).append('\n');
        Map<String, String> end = new LinkedHashMap<String, String>();
        end.put("duration_ms", "200"); end.put("rows", "3"); end.put("max_height", "12.5");
        file.append(LogFormat.endLine(end)).append('\n');
        FlightLog log = LogFormat.read(r(file.toString()), 1_000_000);
        check(log.rows == 3 && log.columnCount() == 4, "three rows, four columns");
        check(log.meta("aircraft", "").equals("Akb7custom001") && log.metaNumber("cells", 0) == 6 && log.meta("format", "").equals("1"), "the header facts are read");
        check(log.isText[3] && !log.isText[0], "the text column is known from the header");
        check(log.value(0, 0) == 1500 && Math.abs(log.value(0, 2) - 36.8012345) < 1e-9, "numbers are read, positions exactly");
        check(Double.isNaN(log.value(1, 1)) && Double.isNaN(log.value(2, 2)), "an empty field is NaN");
        check("ANGLE".equals(log.word(0, 3)) && "A,B".equals(log.word(1, 3)) && log.word(2, 3) == null, "text is read, with its comma, and empty text is null");
        check(log.durationMs() == 200 && log.time[1] == 100, "the times");
        check(log.end.get("duration_ms").equals("200") && log.end.get("max_height").equals("12.5"), "the footer is read");
        check(log.indexOf("vbat") == 1 && log.indexOf("nope") == -1, "finding a column");

        // ---------- a file that was cut off ----------
        String cut = file.substring(0, file.indexOf("# end:")) + "300,1800,24.4,36.80";       // no footer, a half row
        FlightLog c = LogFormat.read(r(cut), 1_000_000);
        check(c.rows == 3 && c.end.isEmpty(), "a cut-off file keeps its whole rows and has no footer");
        FlightLog onlyHeader = LogFormat.read(r("# AX-HUD flight log\nt_ms,ch1\n"), 1000);
        check(onlyHeader.rows == 0 && onlyHeader.durationMs() == 0, "a log with no rows");

        // ---------- something that is not a log ----------
        boolean threw = false;
        try { LogFormat.read(r("hello,world\n1,2\n"), 1000); } catch (IOException e) { threw = true; }
        check(threw, "a file without the t_ms column is refused");
        threw = false;
        try { LogFormat.read(r(""), 1000); } catch (IOException e) { threw = true; }
        check(threw, "an empty file is refused");

        // ---------- a very long log is thinned out ----------
        StringBuilder big = new StringBuilder("# text: \nt_ms,a,b\n");
        for (int i = 0; i < 20000; i++) big.append(i * 100L).append(',').append(i).append(',').append(i * 2).append('\n');
        FlightLog thin = LogFormat.read(r(big.toString()), 20000);       // 20000 numbers: room for about 10000 rows of two columns
        check(thin.rows <= 10000 && thin.rows >= 4000 && thin.stride >= 2, "thinned to fit: " + thin.rows + " rows, stride " + thin.stride);
        boolean ordered = true, evenSpaced = true;
        for (int i = 1; i < thin.rows; i++) {
            if (thin.time[i] <= thin.time[i - 1]) ordered = false;
            if (thin.time[i] - thin.time[i - 1] != thin.time[1] - thin.time[0]) evenSpaced = false;
        }
        check(ordered && evenSpaced && thin.time[0] == 0, "the thinned log keeps its order and an even spacing");
        check(thin.value(5, 1) == thin.value(5, 0) * 2, "the values still belong to their rows");
        FlightLog whole = LogFormat.read(r(big.toString()), 10_000_000);
        check(whole.rows == 20000 && whole.stride == 1, "a log that fits is kept whole");

        // ---------- the field list ----------
        List<String> set = LogFields.replaySet();
        check(set.contains("ch1") && set.contains("ch16") && set.contains("gps_lat") && set.contains("gps_lon") && set.contains("fm") && set.contains("vbat"), "the replay set holds channels, position, volts and the mode");
        check(!set.contains("raw1") && !set.contains("lv1"), "the replay set leaves out the advanced values");
        check(set.size() == new java.util.HashSet<String>(set).size(), "no name twice");
        check(LogFields.missingForReplay(set, 5).isEmpty(), "the replay set has everything a replay needs");
        List<String> few = LogFields.parse("ch1,ch2,vbat");
        List<String> miss = LogFields.missingForReplay(few, 5);
        check(miss.contains("ch5") && miss.contains("gps_lat") && miss.contains("gps_lon") && miss.contains("yaw") && !miss.contains("ch1") && !miss.contains("vbat"), "a small selection says what a replay misses: " + miss);
        check(LogFields.missingForReplay(Arrays.asList("ch1", "ch2", "ch3", "ch4", "ch5", "gps_lat", "gps_lon", "gps_alt", "sats", "gps_speed", "curr", "up_lq", "vbat", "gps_heading"), 5).isEmpty(), "a heading from the GPS is enough for the direction");
        check(LogFields.parse(" ch1 , ch1, bad name, a-b, ,vbat,").equals(Arrays.asList("ch1", "vbat")), "invalid names and repeats are dropped: " + LogFields.parse(" ch1 , ch1, bad name, a-b, ,vbat,"));
        check(LogFields.join(Arrays.asList("ch1", "bad name", "vbat")).equals("ch1,vbat"), "joining drops invalid names");
        check(LogFields.selection("").equals(set) && LogFields.selection(null).equals(set) && LogFields.selection("!!!").equals(set), "nothing valid stored: the replay set");
        check(LogFields.selection("ch3,fm").equals(Arrays.asList("ch3", "fm")), "a stored selection is used");
        check(LogFields.validName("gps_lat") && !LogFields.validName("") && !LogFields.validName("a b") && !LogFields.validName("x,y") && !LogFields.validName(new String(new char[60]).replace('\0', 'a')), "name rules");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 500; i++) many.append("f").append(i).append(',');
        check(LogFields.parse(many.toString()).size() == LogFields.MAX_FIELDS, "the list is capped");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
