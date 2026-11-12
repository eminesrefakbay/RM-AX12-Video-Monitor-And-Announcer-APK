// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests for the polish: the connecting banner, one motion rule, 48 dp touch targets, the grid, rows, tiles, the warning bar, no video. */
public class PolishTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String p) throws Exception { return new String(Files.readAllBytes(new File(p).toPath()), StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        String kt = "app/src/main/java/dev/ax12/announcer/";
        String res = "app/src/main/res/";

        // ---------- 3. the connecting banner ----------
        check(ConnectingText.percent(0) == 0 && ConnectingText.percent(-5) == 0 && ConnectingText.percent(Double.NaN) == 0, "no time yet: an empty strip");
        check(ConnectingText.percent(6) == 48 && ConnectingText.percent(3) == 24, "the strip fills towards the usual 12 seconds: " + ConnectingText.percent(6));
        check(ConnectingText.percent(12) == 95 && ConnectingText.percent(60) == 95 && ConnectingText.percent(1e9) == 95, "and never quite reaches the end until the radio answers");
        check(!ConnectingText.stuck(19.9) && ConnectingText.stuck(20) && ConnectingText.stuck(120), "after 20 seconds it says what to check");
        check(ConnectingText.wholeSeconds(7.9) == 7 && ConnectingText.wholeSeconds(0) == 0 && ConnectingText.wholeSeconds(-3) == 0 && ConnectingText.wholeSeconds(12.0) == 12, "whole seconds, never negative");
        String act = read(kt + "MainActivity.kt");
        check(act.contains("fader.set(connectBanner.view, connecting)") && act.contains("(sdkStarting || sdkRunning) && !StartupState.connected") && act.contains("StartupState.connected = true"), "the banner shows while the link comes up and goes when the radio answers");
        check(read(kt + "App.kt").contains("StartupState.connected = false"), "a link that was shut down has to connect again");
        check(read(kt + "ConnectingBanner.kt").contains("Connecting to the radio... {0} s") && read(kt + "ConnectingBanner.kt").contains("Still waiting. Close the RadioMaster app"), "the banner counts the seconds and says what to check");

        // ---------- 12. one motion rule ----------
        check(Motion.IN_MS >= Motion.MIN_MS && Motion.IN_MS <= Motion.MAX_MS && Motion.OUT_MS >= Motion.MIN_MS && Motion.OUT_MS <= Motion.MAX_MS && Motion.OUT_MS <= Motion.IN_MS, "fades and slides take 150 to 250 ms, going out quicker than coming in");
        int literal = 0;
        for (String f : new File(kt).list()) {
            if (!f.endsWith(".kt")) continue;
            Matcher m = Pattern.compile("setDuration\\(([^)]*)\\)").matcher(read(kt + f));
            while (m.find()) if (!m.group(1).contains("Motion.") && !m.group(1).equals("FADE_MS")) { literal++; System.out.println("  literal duration in " + f + ": " + m.group(1)); }
        }
        check(literal == 0, "every animation takes its time from Motion (" + literal + " do not)");
        check(read(kt + "SplashOverlay.kt").contains("FADE_MS = Motion.OUT_MS.toLong()"), "the splash fade follows the rule");
        for (String f : new String[]{"SettingsPanel.kt", "AboutPanel.kt", "RulesPanel.kt"}) {
            String s = read(kt + f);
            check(s.contains("Anim.panelIn(root)") && s.contains("Anim.panelOut(root)") && !s.contains("root.visibility = View.VISIBLE"), f + ": the panel slides in from the edge and out again");
        }
        check(read(kt + "MainMenu.kt").contains("Anim.menuIn(root)") && read(kt + "LayoutEditor.kt").contains("Anim.show(this)"), "the menu and the layout editor use the same helpers");

        // ---------- 10. touch targets and the grid ----------
        String styles = read(res + "values/styles.xml");
        Matcher mh = Pattern.compile("name=\"android:minHeight\">(\\d+)dp").matcher(styles), mw = Pattern.compile("name=\"android:minWidth\">(\\d+)dp").matcher(styles);
        check(mh.find() && Integer.parseInt(mh.group(1)) >= 48 && mw.find() && Integer.parseInt(mw.group(1)) >= 48, "every button is at least 48 dp to tap");
        check(read(kt + "CardStyle.kt").contains("const val MIN_TOUCH_DP = 48") && read(kt + "CardStyle.kt").contains("const val GRID_DP = 8"), "the touch size and the grid are named once");
        int small = 0;
        for (String f : new File(kt).list()) {
            if (!f.endsWith(".kt")) continue;
            Matcher z = Pattern.compile("(minHeight|minimumHeight|minWidth|minimumWidth)\\s*=\\s*0\\b").matcher(read(kt + f));
            while (z.find()) { small++; System.out.println("  zero minimum size in " + f); }
        }
        check(small == 0, "no button is made smaller than its minimum (" + small + " are)");
        check(read(kt + "SettingsPanel.kt").contains("t.minHeight = dp(RAIL_ITEM_DP)") && read(kt + "SettingsPanel.kt").contains("const val RAIL_ITEM_DP = 40") && 8 * (40 + 4) <= 360 && read(kt + "SettingsPanel.kt").contains("sw.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)") && read(kt + "SettingsPanel.kt").contains("b.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)") && read(kt + "SettingsPanel.kt").contains("row.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)"), "switches and rows are 48 dp or more; the eight tabs of the rail are 40 dp tall and full width so that all of them fit the screen");
        int off = 0;
        for (String f : new String[]{"SettingsPanel.kt", "AboutPanel.kt", "MainMenu.kt", "ConnectingBanner.kt", "NoVideoView.kt", "LayoutEditor.kt"}) {
            Matcher d = Pattern.compile("dp\\((\\d+)\\)").matcher(read(kt + f));
            while (d.find()) if (Integer.parseInt(d.group(1)) % 4 != 0) { off++; System.out.println("  off the grid in " + f + ": dp(" + d.group(1) + ")"); }
        }
        check(off == 0, "spaces and sizes of the new screens are multiples of 4 dp, half of the 8 dp grid (" + off + " are not)");

        // ---------- 11. long text ----------
        check(styles.contains("android:autoSizeTextType\">uniform") && styles.contains("android:autoSizeMinTextSize\">11sp") && styles.contains("android:maxLines\">2"), "buttons wrap to two lines and then shrink to 11 sp");
        String card = read(kt + "CardStyle.kt");
        check(card.contains("fun fitText(") && card.contains("setAutoSizeTextTypeUniformWithConfiguration") && card.contains("Build.VERSION.SDK_INT >= 26"), "other texts can do the same on Android 8 and newer");
        check(read(kt + "MainMenu.kt").contains("CardStyle.fitText(name") && read(kt + "SettingsPanel.kt").contains("CardStyle.fitText(t, 11, 15)") && read(kt + "NoVideoView.kt").contains("CardStyle.fitText(advice") && act.contains("CardStyle.fitText(warningText"), "tiles, the tabs of Settings, the no-video line and the warning bar fit their text");

        // ---------- 1. settings rows ----------
        String settings = read(kt + "SettingsPanel.kt");
        Matcher vr = Pattern.compile("valueRow\\(tr\\(").matcher(settings);
        int rows = 0; while (vr.find()) rows++;
        check(rows >= 15, "the cycling settings are label-and-value rows (" + rows + ")");
        check(!Pattern.compile("action\\(tr\\(\"[^\"]*\\{0\\}[^\"]*\\(tap").matcher(settings).find() && !settings.contains("(tap to change"), "no setting still says '(tap to change)' in its label");
        check(settings.contains("v.setTextColor(Sunlight.c(CardStyle.ACCENT))") && settings.contains("Gravity.END"), "the value is on the right, in blue");

        // ---------- 4. HUD numbers ----------
        check(card.contains("sans-serif-condensed") && card.contains("\"tnum\"") && read(kt + "FlightStatsView.kt").contains("CardStyle.styleNumbers(valuePaint)") && read(kt + "ChartView.kt").contains("CardStyle.styleNumbers(valuePaint)")
              && read(kt + "ChartView.kt").contains("CardStyle.styleSmallNumbers(axis)") && read(kt + "RadarView.kt").contains("CardStyle.styleSmallNumbers(text)"), "HUD numbers: condensed font, digits of equal width");

        // ---------- 6. the menu tiles ----------
        String menu = read(kt + "MainMenu.kt");
        for (String t : new String[]{"Sound rules", "Speech", "Battery", "Aircraft", "Settings", "Exit"}) check(menu.contains("Tile(tr(\"" + t + "\")"), "tile: " + t);
        Matcher size = Pattern.compile("LayoutParams\\(dp\\((\\d+)\\), dp\\((\\d+)\\)\\)\\.also \\{ it\\.setMargins\\(dp\\((\\d+)\\)").matcher(menu);
        check(size.find(), "the tile size is in the menu");
        int w = Integer.parseInt(size.group(1)), h = Integer.parseInt(size.group(2)), mg = Integer.parseInt(size.group(3));
        check(w >= 96 && h >= 96, "tiles are large (" + w + " x " + h + " dp)");
        int boxHeight = 56 + 2 * (h + 2 * mg) + 16;               // the top margin, two rows, the padding
        check(boxHeight <= 416, "the menu fits the AX12's 416 dp tall screen (" + boxHeight + " dp)");
        check(menu.contains("CardStyle.OK") && menu.contains("CardStyle.BAD") && menu.contains("tr(\"On\")") && menu.contains("tr(\"Off\")") && menu.contains("\"speaker_off\""), "speech on or off shows in words, in a coloured bar and in the icon");
        check(menu.contains("Tile(tr(\"Speech\"), false)") && menu.contains("Tile(tr(\"Battery\"), false)"), "speech and battery keep the menu open, so the new state can be seen");

        // ---------- 8. the warning bar ----------
        String wb = read(kt + "WarningBar.kt");
        check(wb.contains("warn_critical") && wb.contains("warn_warning") && wb.contains("\"about\""), "each warning level has its own shape, not only its own colour");
        check(!wb.contains("/ 500) % 2L") && wb.contains("Math.cos("), "a critical warning breathes instead of blinking");
        check(act.contains("warningText.minimumHeight = dp(CardStyle.MIN_TOUCH_DP)"), "the warning bar is a thick bar");

        // ---------- 9. no video ----------
        String nv = read(kt + "NoVideoView.kt");
        check(nv.contains("video_off") && nv.contains("tr(\"No video\")") && act.contains("noVideo.update(camera.status())") && !read("app/src/main/assets/i18n/source.txt").contains("NO VIDEO"), "no video: a big icon, a title and one line of advice");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
