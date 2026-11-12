// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** Tests of the tabbed Settings screen: its structure, its search, and how the panel is wired. */
public class SettingsTabsTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File(f).toPath()), "UTF-8"); }
    static final String KT = "app/src/main/java/dev/ax12/announcer/";

    public static void main(String[] args) throws Exception {
        // ---------- the structure ----------
        String[] tabs = SettingsIndex.TAB_IDS;
        check(tabs.length == 8 && tabs[0].equals("general") && tabs[7].equals("system"), "eight tabs, General first and System last");
        List<String> groups = SettingsIndex.groupIds();
        check(groups.size() == 13, "thirteen blocks of rows (the two Diagnostics groups are one now): " + groups.size());
        boolean mapped = true, unique = true;
        for (String g : groups) { String t = SettingsIndex.tabOf(g); boolean ok = false; for (String x : tabs) if (x.equals(t)) ok = true; if (!ok) mapped = false; if (groups.indexOf(g) != groups.lastIndexOf(g)) unique = false; }
        check(mapped && unique, "every block belongs to a tab, once");
        boolean everyTabUsed = true;
        for (String t : tabs) { boolean used = false; for (String g : groups) if (t.equals(SettingsIndex.tabOf(g))) used = true; if (!used) everyTabUsed = false; }
        check(everyTabUsed && SettingsIndex.tabOf("diag") == null && SettingsIndex.tabOf("nonsense") == null, "every tab holds something; the old second Diagnostics group is gone");
        check("flight".equals(SettingsIndex.tabOf("window")) && "flight".equals(SettingsIndex.tabOf("after")) && "video".equals(SettingsIndex.tabOf("overlays")) && "system".equals(SettingsIndex.tabOf("diagnostics")) && "system".equals(SettingsIndex.tabOf("about")), "the approval window and the landing block are in Aircraft and flight, the overlays in Video, diagnostics and about in System");

        // ---------- folding and matching ----------
        check(SettingsIndex.fold("\u0130statistik").equals("istatistik") && SettingsIndex.fold("ISIK").equals("isik") && SettingsIndex.fold("\u0131\u015F\u0131k").equals("isik"), "the Turkish dotted and dotless i fold to i");
        check(SettingsIndex.fold("\u00DCber Gr\u00F6\u00DFe").equals("uber grosse") && SettingsIndex.fold("Stra\u00DFe").equals("strasse"), "German umlauts and the sharp s fold");
        check(SettingsIndex.fold("  Stick   size  ").equals("stick size") && SettingsIndex.fold(null).equals("") && SettingsIndex.fold("   ").equals(""), "blanks are collapsed and trimmed");
        check(SettingsIndex.fold("\u58F0\u97F3 Rules").equals("\u58F0\u97F3 rules"), "Chinese characters stay as they are");
        String[] w1 = SettingsIndex.words("Stick size");
        check(w1.length == 2 && SettingsIndex.matches("Stick size", w1) && SettingsIndex.matches("Stick size and position", w1) && SettingsIndex.matches("Size of the stick view", w1), "every word has to be in the label, in any order");
        check(!SettingsIndex.matches("Stick opacity", w1) && !SettingsIndex.matches("Warning bar position", w1), "a label without one of the words does not match");
        check(!SettingsIndex.matches("Anything", SettingsIndex.words("")) && !SettingsIndex.matches("Anything", SettingsIndex.words("   ")) && SettingsIndex.words("   ").length == 0, "an empty query finds nothing");
        check(SettingsIndex.matches("\u0130statistik kartlar\u0131", SettingsIndex.words("istatistik kart")) && SettingsIndex.matches("Einstellungen \u00FCber", SettingsIndex.words("uber")), "a query typed without the accents finds the label with them");
        check(SettingsIndex.matches("Set the magnetic declination...", SettingsIndex.words("DECLIN")), "capitals do not matter and part of a word is enough");

        // ---------- the panel (checked in the sources) ----------
        String sp = read(KT + "SettingsPanel.kt");
        int[] at = new int[tabs.length + 1];
        for (int i = 0; i < tabs.length; i++) at[i] = sp.indexOf("Tab(\"" + tabs[i] + "\"");
        at[tabs.length] = sp.indexOf("private fun selectTab(");
        boolean inOrder = true;
        for (int i = 0; i < tabs.length; i++) if (at[i] < 0 || at[i + 1] <= at[i]) inOrder = false;
        check(inOrder, "the panel declares the eight tabs, in the order of the rail");
        boolean placed = true;
        for (String g : groups) {
            int fn = sp.indexOf("private fun " + g + "Content()");
            if (fn < 0 || sp.indexOf("private fun " + g + "Content()", fn + 1) >= 0) placed = false;
            int tabIdx = -1; for (int i = 0; i < tabs.length; i++) if (tabs[i].equals(SettingsIndex.tabOf(g))) tabIdx = i;
            int call = sp.indexOf(g + "Content() }", at[tabIdx]);
            if (call < 0 || call >= at[tabIdx + 1]) placed = false;
        }
        check(placed, "every block of rows is one function, shown in the tab that SettingsIndex names");
        check(!sp.contains("openGroup") && !sp.contains("group(\"") && !sp.contains("chevron_down"), "the accordion is gone");
        int dg = sp.indexOf("private fun diagnosticsContent()");
        String diagBody = dg > 0 ? sp.substring(dg, sp.indexOf("private fun updatesContent()")) : "";
        check(diagBody.contains("host.openDiagnostics()") && diagBody.contains("host.variablesOn()") && diagBody.contains("host.testChannelsOn()") && diagBody.contains("host.showStartupTimings()"), "the merged Diagnostics section holds the bug report and the tools");
        check(sp.contains("private var selectedTab = \"general\"") && sp.contains("Part(\"Language / Dil / Sprache / \\u8BED\\u8A00\")"), "General opens first and still has the language heading in four languages");
        check(sp.contains("const val SECTION_TAG") && sp.contains("t.tag = SECTION_TAG") && sp.contains("t.tag = NOTE_TAG") && sp.contains("row.tag = INDENT_TAG"), "headings, notes and indented rows are marked for the search");
        int lo = sp.indexOf("private fun labelOf("); String lab = lo > 0 ? sp.substring(lo, lo + 520) : "";
        check(lab.indexOf("v is Switch") > 0 && lab.indexOf("v is Switch") < lab.indexOf("v is Button"), "the label of a switch is read before that of a button (a switch is a button too)");
        int sr = sp.indexOf("private fun buildSearchResults()"); String res = sr > 0 ? sp.substring(sr, sp.indexOf("// ----", sr)) : "";
        check(res.contains("SettingsIndex.words(query)") && res.contains("SettingsIndex.matches(label, words)") && res.contains("temp.removeView(v)") && res.contains("list.addView(v)") && res.contains("found == 0"), "the search moves the real rows, so they work in the results, and says when nothing is found");
        check(res.contains("INDENT_TAG") && res.contains("parentMatched"), "the size, position and opacity rows follow their switch when it matches");
        check(sp.contains("IME_FLAG_NO_EXTRACT_UI") && sp.contains("IME_FLAG_NO_FULLSCREEN") && sp.contains("root.isFocusableInTouchMode = true"), "the search field never opens a full-screen keyboard or takes the focus when Settings opens");
        int sh = sp.indexOf("fun show()"), hi = sp.indexOf("fun hide()");
        check(sp.substring(sh, hi).contains("hideKeyboard()") && sp.substring(hi, sp.indexOf("private fun hideKeyboard()")).contains("hideKeyboard()") && sp.substring(sp.indexOf("private fun selectTab("), sp.indexOf("private fun railItem(")).contains("hideKeyboard()"), "the keyboard is put away when Settings opens, closes, or a tab is chosen");
        check(sp.contains("searchHandler.postDelayed(runSearch, 220L)") && sp.contains("searchHandler.removeCallbacks(runSearch)"), "the search waits for a pause in typing");
        check(sp.contains("if (searching) buildSearchResults() else buildParts("), "a screen shows one tab, or the results of a search");
        check(sp.contains("search.setText(\"\")") && sp.indexOf("query = \"\"", sp.indexOf("private fun selectTab(")) > 0, "choosing a tab clears the search");
        String act = read(KT + "MainActivity.kt");
        check(act.contains("SettingsPanel(this, settingsHost)") && !act.contains("openGroup"), "the main screen opens the panel as before");

        // ---------- the texts ----------
        String src = read("app/src/main/assets/i18n/source.txt");
        for (String k : new String[]{"General", "Aircraft and flight", "Video and overlays", "System", "Search settings", "Nothing found."}) {
            check(src.contains("\n" + k + "\n") && sp.contains("tr(\"" + k + "\")"), "the text \"" + k + "\" is used and translated");
            for (String lang : new String[]{"tr", "de", "zh"}) if (!read("app/src/main/assets/i18n/" + lang + ".tsv").contains("\n" + k + "\t")) check(false, k + " has no " + lang + " translation");
        }
        check(!src.contains("\nAircraft and battery\n") && !src.contains("\nOverlays on the video\n") && !src.contains("\nVideo and speech\n"), "the three old group titles are gone");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
