// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/** Tests of the words of the flight list, in every language. */
public class LogTextTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static LogStore.Entry entry() {
        LogStore.Entry e = new LogStore.Entry();
        e.name = "AX-HUD_2026-11-07_14-55-33_Akb7custom001.csv";
        e.started = "2026-11-07 14:55:33";
        e.aircraft = "Akb7custom001";
        e.pack = "Pack 3 (LiPo 6S, 3000 mAh)";
        e.durationMs = 767000;
        e.rows = 7670;
        e.size = 2_400_000;
        e.clean = true;
        e.maxHeight = 130.6; e.maxDist = 515.4; e.maxSpeed = 125.7; e.peakCurrent = 56.8; e.minCell = 3.44; e.mah = 2605;
        return e;
    }

    public static void main(String[] args) throws Exception {
        I18n.load(I18nTests.FILES, "en");
        LogStore.Entry e = entry();
        check(LogText.dateTime(e).equals("2026-11-07  14:55"), "the date and time: " + LogText.dateTime(e));
        LogStore.Entry noHeader = entry(); noHeader.started = "";
        check(LogText.dateTime(noHeader).equals("2026-11-07  14:55"), "from the file name when the header has no date: " + LogText.dateTime(noHeader));
        LogStore.Entry odd = entry(); odd.started = ""; odd.name = "AX-HUD_odd.csv";
        check(LogText.dateTime(odd).equals("AX-HUD_odd.csv"), "the name itself as the last resort");
        check(LogText.duration(767000).equals("12:47") && LogText.duration(59000).equals("0:59") && LogText.duration(3661000).equals("1:01:01") && LogText.duration(0).equals("0:00") && LogText.duration(-5).equals("0:00"), "durations");
        check(LogText.title(e).equals("2026-11-07  14:55  \u00B7  Akb7custom001"), "the title: " + LogText.title(e));
        LogStore.Entry bare = entry(); bare.aircraft = "";
        check(LogText.title(bare).equals("2026-11-07  14:55"), "no aircraft: just the date");
        check(LogText.lengthAndPack(e).equals("12:47  \u00B7  Pack 3 (LiPo 6S, 3000 mAh)"), "the length and the pack: " + LogText.lengthAndPack(e));
        check(LogText.summary(e).equals("Max height 131 m  \u00B7  Max distance 515 m  \u00B7  Top speed 126 km/h  \u00B7  Peak current 57 A  \u00B7  Lowest cell 3.44 V  \u00B7  2605 mAh used"), "the summary: " + LogText.summary(e));
        LogStore.Entry part = entry(); part.maxSpeed = Double.NaN; part.peakCurrent = Double.NaN; part.minCell = Double.NaN; part.mah = Double.NaN; part.maxDist = Double.NaN;
        check(LogText.summary(part).equals("Max height 131 m"), "only what is known: " + LogText.summary(part));
        LogStore.Entry cut = entry(); cut.clean = false; cut.maxHeight = Double.NaN; cut.maxDist = Double.NaN; cut.maxSpeed = Double.NaN; cut.peakCurrent = Double.NaN; cut.minCell = Double.NaN; cut.mah = Double.NaN;
        check(LogText.summary(cut).equals("The log was cut off: the app was stopped while it was writing."), "a cut-off log says so: " + LogText.summary(cut));
        check(LogText.summary(entry()).indexOf("cut off") < 0, "a clean log does not");
        check(LogText.size(80).equals("80 B") && LogText.size(2048).equals("2 KB") && LogText.size(2_400_000).equals("2.3 MB") && LogText.size(1024L * 1024).equals("1.0 MB"), "sizes");
        check(LogText.footer(3, 5_000_000).equals("3 logs, 4.8 MB"), "the line under the list: " + LogText.footer(3, 5_000_000));

        // every language has every text of the list, with its numbers in place
        for (String lang : new String[]{"tr", "de", "zh"}) {
            I18n.load(I18nTests.FILES, lang);
            String s = LogText.summary(entry());
            check(!s.contains("{0}") && s.contains("131") && s.contains("515") && s.contains("126") && s.contains("57") && s.contains("3.44") && s.contains("2605"), lang + ": the summary has its numbers: " + s);
            check(!s.contains("Max height") && !s.contains("Lowest cell") && !s.contains("mAh used"), lang + ": and is translated: " + s);
            check(!LogText.summary(cut).contains("cut off"), lang + ": the cut-off note is translated: " + LogText.summary(cut));
            check(LogText.footer(3, 5_000_000).contains("3") && LogText.footer(3, 5_000_000).contains("4.8 MB") && !LogText.footer(3, 5_000_000).contains("logs,"), lang + ": the footer: " + LogText.footer(3, 5_000_000));
            for (String key : new String[]{"Flight logs", "Replay", "Play", "Pause", "REPLAY", "Delete", "Recording now", "Loading the log...", "Log every flight", "Rows per second", "Values in the log", "Flights to keep", "Replay set", "Everything", "None"}) {
                check(!I18n.t(key).equals(key) || key.equals("Pause") && lang.equals("de"), lang + ": '" + key + "' is translated: " + I18n.t(key));
            }
            check(I18n.t("{0} per second", 10).contains("10") && I18n.t("{0} values", 45).contains("45") && I18n.t("{0} flights", 50).contains("50") && I18n.t("Logging now: {0} rows", 120).contains("120") && I18n.t("{0}x", 8).equals("8x"), lang + ": the settings values keep their numbers");
        }
        I18n.load(I18nTests.FILES, "en");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
