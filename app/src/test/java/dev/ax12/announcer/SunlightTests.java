// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;

/** Tests of the sunlight palette: that the light colours really are readable, and that nothing is left dark by accident. */
public class SunlightTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String hex(int c) { return String.format("%08X", c); }

    static void readable(int darkText, int darkBg, double min, String what) {
        int t = SunlightPalette.map(darkText), b = SunlightPalette.map(darkBg);
        double c = SunlightPalette.contrast(t, b);
        check(c >= min, what + ": " + hex(t) + " on " + hex(b) + " has contrast " + String.format("%.1f", c) + ", needs " + min);
    }

    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File("app/src/main/java/dev/ax12/announcer/" + f).toPath()), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        // ---------- the contrast of every pair a panel draws ----------
        int[] backs = {0xCC1A222C, 0xF21A222C, 0xD91A222C, 0xE6232C36, 0xBA1A222C, 0x8C1A222C, 0xF22C3844};
        String[] names = {"block", "panel", "card", "row", "menu", "backdrop", "open header"};
        for (int i = 0; i < backs.length; i++) {
            readable(0xFFFFFFFF, backs[i], 12.0, "main text on the " + names[i]);
            readable(0xFFDDE3E8, backs[i], 9.0, "card text on the " + names[i]);
            readable(0xFFB0BEC5, backs[i], 7.0, "labels on the " + names[i]);
            readable(0xFFBDBDBD, backs[i], 6.0, "notes on the " + names[i]);
            readable(0xFF9CDCFE, backs[i], 7.0, "titles on the " + names[i]);
            readable(0xFFE2E8EE, backs[i], 9.0, "icons on the " + names[i]);
            readable(0xFF4FC3F7, backs[i], 6.0, "accent on the " + names[i]);
            readable(0xFF66BB6A, backs[i], 6.0, "good on the " + names[i]);
            readable(0xFFFFB300, backs[i], 4.5, "warning on the " + names[i]);
            readable(0xFFEF5350, backs[i], 5.0, "bad on the " + names[i]);
            readable(0xFFFF8A80, backs[i], 5.0, "error text on the " + names[i]);
        }
        readable(0xFFFFFFFF, 0xFF11161C, 12.0, "text on the dashboard page");
        readable(0xFFFFC107, 0xD91A222C, 4.5, "the compass nose on a card");
        readable(0xFF90A4AE, 0xD91A222C, 4.5, "an edge on a card");
        readable(0xFF78909C, 0xD91A222C, 3.0, "a ring on a card");
        readable(0xFF4FC3F7, 0xD91A222C, 6.0, "the radar trail");

        // ---------- the signal colours stay different from each other ----------
        int ok = SunlightPalette.map(0xFF66BB6A), warn = SunlightPalette.map(0xFFFFB300), bad = SunlightPalette.map(0xFFEF5350), acc = SunlightPalette.map(0xFF4FC3F7);
        check(ok != warn && warn != bad && bad != acc && ok != bad && ok != acc && warn != acc, "good, warning, bad and accent are four different colours");

        // ---------- the warning bar ----------
        check(SunlightPalette.contrast(SunlightPalette.BAR_CRITICAL_TEXT, SunlightPalette.BAR_CRITICAL) >= 5.0, "white on the critical red: " + SunlightPalette.contrast(SunlightPalette.BAR_CRITICAL_TEXT, SunlightPalette.BAR_CRITICAL));
        check(SunlightPalette.contrast(SunlightPalette.BAR_WARNING_TEXT, SunlightPalette.BAR_WARNING) >= 12.0, "black on the warning yellow: " + SunlightPalette.contrast(SunlightPalette.BAR_WARNING_TEXT, SunlightPalette.BAR_WARNING));
        check(SunlightPalette.contrast(SunlightPalette.BAR_INFO_TEXT, SunlightPalette.BAR_INFO) >= 8.0, "white on the info blue");
        check(SunlightPalette.BAR_ICON_STROKE > 2.0f && SunlightPalette.BAR_ICON_DP > 28 && SunlightPalette.BAR_OUTLINE_DP >= 3 && SunlightPalette.BAR_TEXT_SP >= 18f, "stronger marks than the normal bar (stroke 2, icon 28 dp)");
        // the three bar colours are as different from each other as colours can be
        check(SunlightPalette.contrast(SunlightPalette.BAR_CRITICAL, SunlightPalette.BAR_WARNING) >= 3.0 && SunlightPalette.contrast(SunlightPalette.BAR_INFO, SunlightPalette.BAR_WARNING) >= 5.0, "the levels can be told apart");

        // ---------- the table ----------
        int[] dark = {0x8C1A222C, 0xCC1A222C, 0xBA1A222C, 0xF21A222C, 0xD91A222C, 0xE6232C36, 0xFFFFFFFF, 0xFFB0BEC5, 0xFF9CDCFE, 0xFF4FC3F7, 0xFF66BB6A, 0xFFFFB300, 0xFFEF5350, 0xFFE2E8EE, 0xFF90A4AE};
        boolean allKnown = true;
        for (int c : dark) if (!SunlightPalette.known(c)) allKnown = false;
        check(allKnown, "every colour of CardStyle and of the panels has its own light colour");
        check(SunlightPalette.map(0xFFFFFFFF) == 0xFF0B141B && SunlightPalette.map(0xCC1A222C) == 0xFFFFFFFF, "white text becomes near black, the dark block becomes white");
        // light backgrounds are light, dark text is dark
        for (int c : new int[]{0xCC1A222C, 0xF21A222C, 0xD91A222C, 0xE6232C36, 0xBA1A222C, 0x8C1A222C, 0xF22C3844, 0xFF11161C}) {
            int m = SunlightPalette.over(SunlightPalette.map(c), 0xFFFFFFFF);
            check(SunlightPalette.luminance(m) > 0.6, "the backdrop " + hex(c) + " is light: " + SunlightPalette.luminance(m));
        }
        for (int c : new int[]{0xFFFFFFFF, 0xFFDDE3E8, 0xFFB0BEC5, 0xFFBDBDBD, 0xFF9CDCFE, 0xFFE2E8EE}) check(SunlightPalette.luminance(SunlightPalette.map(c)) < 0.12, "the text " + hex(c) + " is dark");
        check((SunlightPalette.map(0xCC1A222C) >>> 24) == 0xFF && (SunlightPalette.map(0xF21A222C) >>> 24) == 0xFF, "blocks and panels are fully solid");
        check((SunlightPalette.map(0x8C1A222C) >>> 24) >= 0xF0 && (SunlightPalette.map(0xBA1A222C) >>> 24) >= 0xF0 && (SunlightPalette.map(0xD91A222C) >>> 24) >= 0xF0, "the see-through ones become nearly solid");

        // ---------- a colour that is not in the table ----------
        int unknownLight = 0xFFEEEEEE, unknownDark = 0xFF202830, unknownRed = 0xFFFF5252;
        check(SunlightPalette.luminance(SunlightPalette.map(unknownLight)) < SunlightPalette.luminance(unknownLight) - 0.5, "an unknown light colour turns dark");
        check(SunlightPalette.luminance(SunlightPalette.map(unknownDark)) > SunlightPalette.luminance(unknownDark) + 0.3, "an unknown dark colour turns light");
        check(((SunlightPalette.map(0x80336699) >>> 24) & 0xFF) == 0x80, "the alpha of an unknown colour is kept");
        int mr = SunlightPalette.map(unknownRed);
        check(((mr >> 16) & 0xFF) > ((mr >> 8) & 0xFF) * 2 && ((mr >> 16) & 0xFF) > (mr & 0xFF) * 2, "a red stays red");
        check(SunlightPalette.map(0xFF000000) != 0xFF000000 && SunlightPalette.luminance(SunlightPalette.map(0xFF000000)) > 0.8 && SunlightPalette.luminance(SunlightPalette.map(0xFFFFFFFF)) < 0.05, "black and white swap");

        // ---------- the contrast helper itself ----------
        check(Math.abs(SunlightPalette.contrast(0xFF000000, 0xFFFFFFFF) - 21.0) < 0.01 && Math.abs(SunlightPalette.contrast(0xFF777777, 0xFF777777) - 1.0) < 0.01, "contrast of black on white is 21, of a colour on itself 1");
        check(Math.abs(SunlightPalette.contrast(0xFF767676, 0xFFFFFFFF) - 4.54) < 0.05, "the well known grey 767676 on white is 4.5");

        // ---------- the wiring (checked in the sources: the screens cannot run here) ----------
        String act = read("MainActivity.kt");
        check(act.contains("Sunlight.on = Prefs.bool(this, \"sunlight\", false)") && act.indexOf("Sunlight.on = Prefs.bool") < act.indexOf("mainMenu = MainMenu("), "the theme is read before any screen is built");
        int sl = act.indexOf("private fun setSunlight(on: Boolean)");
        String setter = sl > 0 ? act.substring(sl, sl + 500) : "";
        check(setter.contains("Prefs.setBool(this, \"sunlight\", on)") && setter.contains("dashboard.applyTheme()") && setter.contains("warningBar.restyle()") && setter.contains("settingsPanel.skin()"), "switching keeps the choice and restyles the dashboard, the warning bar and the open settings");
        check(act.contains("{ Sunlight.on },\n            { toggleSunlight() },") && act.contains("override fun setSunlightOn(on: Boolean) = this@MainActivity.setSunlight(on)"), "the MENU tile and the setting use the same switch");
        for (String f : new String[]{"SettingsPanel.kt", "RulesPanel.kt", "AboutPanel.kt", "LogViewerPanel.kt", "InfoPanel.kt", "WelcomeGuide.kt"}) {
            String s = read(f);
            check(s.contains("fun skin()") && s.matches("(?s).*fun show\\(\\) \\{\\s*skin\\(\\).*"), f + ": the frame colours are set again each time it opens");
        }
        String menu = read("MainMenu.kt");
        check(menu.contains("Tile(tr(\"Sunlight\"), false)") && menu.contains("listOf(rulesTile, speechTile, batteryTile, aircraftTile, sunlightTile)") && menu.contains("private fun refresh() {\n        skin()"), "the MENU has the Sunlight tile, five tiles in the first row, and draws its colours again each time");
        int dashFiles = 0;
        for (String f : new String[]{"HorizonView.kt", "CompassView.kt", "FlightStatsView.kt", "RadarView.kt", "ChartView.kt", "SticksView.kt", "ValueTileView.kt"}) {
            String s = read(f);
            if (s.contains("fun applyLight(on: Boolean)") && s.contains("var light = false") && s.contains("Sunlight.cIf(on,")) dashFiles++;
        }
        check(dashFiles == 7, "all seven card views can be drawn in the light style (" + dashFiles + ")");
        String dash = read("DashboardPage.kt");
        check(dash.contains("skinCard(v)") && dash.contains("fun applyTheme()") && dash.contains("is HorizonView -> v.applyLight(Sunlight.on)") && dash.contains("is ValueTileView -> v.applyLight(Sunlight.on)") && dash.contains("Sunlight.c(0xFF0B1016.toInt())"), "the dashboard page and each of its cards follow the theme");
        // the cards over the video keep their own look
        for (String f : new String[]{"ChecklistView.kt", "LayoutEditor.kt", "ConnectingBanner.kt", "LinkLossCard.kt", "WindowChip.kt", "ReplayOverlay.kt", "NoVideoView.kt", "SplashOverlay.kt"}) {
            check(!read(f).contains("Sunlight."), f + " is not themed (it is drawn over the video)");
        }
        check(!act.contains("applyLight("), "the video page never sets a card to the light style");
        // every dialog follows the theme
        File[] all = new File("app/src/main/java/dev/ax12/announcer").listFiles();
        StringBuilder raw = new StringBuilder();
        for (File f : all) if (f.getName().endsWith(".kt") && !f.getName().equals("Sunlight.kt") && read(f.getName()).contains("AlertDialog.Builder(")) raw.append(f.getName()).append(' ');
        check(raw.length() == 0, "no dialog is built without the theme builder: " + raw);
        check(act.contains("TimePickerDialog(this, Sunlight.dialogTheme(),"), "the clock dialog follows the theme");
        // the warning bar
        String wb = read("WarningBar.kt");
        check(wb.contains("SunlightPalette.BAR_CRITICAL") && wb.contains("SunlightPalette.BAR_ICON_STROKE") && wb.contains("SunlightPalette.BAR_OUTLINE_DP") && wb.contains("fun restyle()") && wb.contains("Sunlight.on != lastLight"), "the warning bar has its stronger style and restyles when the theme is switched");
        check(read("Icons.kt").contains("stroke: Float = 2f"), "icons can be drawn with thicker marks");
        check(SettingsFile.isKnown("sunlight") && Boolean.FALSE.equals(SettingsFile.defaultOf("sunlight")), "the switch is part of the backup and off by default");
        check(read("SettingsPanel.kt").contains("toggle(tr(\"Sunlight theme\"), host.sunlightOn())"), "the setting is in Settings > Display");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
