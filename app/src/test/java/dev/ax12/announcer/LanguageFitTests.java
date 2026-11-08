// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.List;

/**
 * An estimate of whether the translated texts fit where they are shown, because the real screen of the radio is not at hand.
 * Each character gets a width in "em" (a letter is about half of its size wide, a Chinese character as wide as it is high); the
 * estimate is a little generous, so what passes here has room on the screen. It is a safety net, not a measurement.
 */
public class LanguageFitTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    /** The width of one character, in em. */
    static double em(char c) {
        if ((c >= 0x2E80 && c <= 0x9FFF) || (c >= 0xFF00 && c <= 0xFFEF) || (c >= 0x3000 && c <= 0x303F)) return 1.0;
        if (c == ' ') return 0.26;
        if ("il.,:;'!|j".indexOf(c) >= 0) return 0.29;
        if ("tfrI()[]/-".indexOf(c) >= 0) return 0.36;
        if (c == 'm' || c == 'w') return 0.84;
        if (c == 'M' || c == 'W') return 0.96;
        if (Character.isUpperCase(c)) return 0.66;
        if (Character.isDigit(c)) return 0.56;
        return 0.54;
    }
    static double width(String s, boolean bold) { double w = 0; for (int i = 0; i < s.length(); i++) w += em(s.charAt(i)); return bold ? w * 1.07 : w; }

    /** How many lines the text needs in a box that is availDp wide, at sizeSp; 99 when a single word is wider than the box. */
    static int lines(String text, double sizeSp, double availDp, boolean bold) {
        if (text.isEmpty()) return 1;
        int lines = 1; double cur = 0;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { lines++; cur = 0; continue; }
            cur = 0;
            for (String word : para.split(" ")) {
                // Chinese has no spaces: break it anywhere
                double ww = width(word, bold) * sizeSp;
                if (ww > availDp) {
                    boolean cjk = false;
                    for (int i = 0; i < word.length(); i++) if (em(word.charAt(i)) >= 1.0) cjk = true;
                    if (!cjk) return 99;
                    int per = (int) Math.max(1, Math.floor(availDp / sizeSp));
                    lines += (int) Math.ceil(word.length() / (double) per) - 1;
                    cur = (word.length() % per) * sizeSp; continue;
                }
                double add = (cur == 0 ? 0 : width(" ", bold) * sizeSp) + ww;
                if (cur + add <= availDp) cur += add; else { lines++; cur = ww; }
            }
            lines++;
        }
        return lines - 1;
    }

    public static void main(String[] args) throws Exception {
        String[] tiles = {"Sound rules", "Speech", "Battery", "Aircraft", "Dashboard", "Video", "Flight logs", "Settings", "Exit"};
        for (String lang : new String[]{"en", "tr", "de", "zh"}) {
            I18n.load(I18nTests.FILES, lang);

            // the menu tiles: 104 dp wide with 8 dp on each side, two lines, from 13 sp shrinking to 10 sp
            for (String t : tiles) {
                String v = I18n.t(t);
                check(lines(v, 10, 88, true) <= 2, lang + ": the tile name fits in two lines at the smallest size: " + v);
            }
            for (String t : new String[]{"On", "Off"}) {
                String v = I18n.t(t);
                check(lines(v, 9, 88, false) <= 2, lang + ": the tile state fits: " + v);
            }

            // the welcome guide: the card is at least 470 dp of text wide; a title is two lines from 22 sp down to 16 sp, the text 16 sp
            List<FirstRun.Page> pages = FirstRun.pages();
            for (int i = 0; i < pages.size(); i++) {
                FirstRun.Page p = pages.get(i);
                check(lines(p.title, 16, 470, true) <= 2, lang + ": the title of page " + (i + 1) + " fits in two lines: " + p.title);
                check(lines(p.body, 16, 470, false) <= 9, lang + ": the text of page " + (i + 1) + " needs at most 9 lines (" + lines(p.body, 16, 470, false) + ")");
            }

            // buttons of the guide: 112 dp wide with padding, text 14 sp
            for (String b : new String[]{"Back", "Next", "Done"}) {
                String v = I18n.t(b);
                check(lines(v, 14, 88, true) <= 2, lang + ": the guide button fits: " + v);
            }
            check(width(I18n.t("Skip"), true) * 14 <= 140, lang + ": the Skip button stays small: " + I18n.t("Skip"));
            check(lines(I18n.t("Step {0} of {1}", 3, 5), 12, 150, false) <= 1, lang + ": the step counter fits one line");

            // the banner that counts the seconds, along the top of the screen (at least 480 dp, 14 sp)
            check(lines(I18n.t("Connecting to the radio... {0} s", 12), 14, 480, true) <= 1, lang + ": the connecting banner fits one line");

            // the replay bar (buttons 96 dp wide, speed 72 dp) and the buttons of the flight list and of the field picker
            for (String b : new String[]{"Close", "Play", "Pause"}) {
                String v = I18n.t(b);
                check(lines(v, 14, 76, true) <= 2, lang + ": the replay button fits: " + v);
            }
            check(lines(I18n.t("{0}x", 8), 14, 52, true) <= 1, lang + ": the speed button fits");
            for (String b : new String[]{"Replay", "Delete", "Cancel"}) check(lines(I18n.t(b), 14, 160, true) <= 2, lang + ": the list button fits: " + I18n.t(b));
            for (String b : new String[]{"Replay set", "Everything", "None"}) check(lines(I18n.t(b), 14, 100, true) <= 2, lang + ": the picker button fits in a third of the dialog: " + I18n.t(b));
            check(lines(I18n.t("REPLAY") + "  2026-11-07  14:55  \u00B7  Akb7custom001", 13, 520, true) <= 1, lang + ": the REPLAY tag fits one line");
            check(lines(I18n.t("Rows per second"), 16, 330, false) <= 2 && lines(I18n.t("Values in the log"), 16, 330, false) <= 2 && lines(I18n.t("Flights to keep"), 16, 330, false) <= 2, lang + ": the logging rows keep their label readable");

            // no word of a button-sized text is longer than a button (a word cannot be broken): the short labels of the app
            int wide = 0;
            for (String key : new String[]{"Settings", "Sound rules", "Aircraft", "Battery", "Speech", "Close", "Back", "Done", "Edit", "Save", "Reset", "Restore"}) {
                String v = I18n.t(key);
                if (lines(v, 14, 140, true) > 1) { wide++; System.out.println("  note: " + lang + ": '" + v + "' needs two lines in a 140 dp button"); }
            }
            check(wide <= 4, lang + ": few short labels need two lines (" + wide + ")");
        }
        I18n.load(I18nTests.FILES, "en");
        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
