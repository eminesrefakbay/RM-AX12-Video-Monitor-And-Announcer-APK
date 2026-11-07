// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests for the whole-settings backup file. */
public class SettingsFileTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static Map<String, Object> sample() {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("rules", "# my rules\nch5 >= 1950 -> armed | disarmed\nvbat / 6 < 3.70 -> battery low @30\n");
        m.put("warnRules", "cell < $low -> BATTERY LOW {cell:2}\nch5 >= 1950 -> *ARMED @5\n");
        m.put("checkRules", "sats >= 10 -> GPS {sats}\n");
        m.put("barsConfig", AuxBars.encode(AuxBars.defaults()));
        m.put("batProfiles", BatteryProfiles.encode(BatteryProfiles.defaults()));
        m.put("language", "tr"); m.put("speechLanguage", "same"); m.put("updateChannel", "stable"); m.put("camera", "1");
        m.put("rotation", 90); m.put("fill", true); m.put("menuAlways", false); m.put("splashOn", true); m.put("updateAuto", false);
        m.put("sticksOn", true); m.put("sticksSize", 2); m.put("sticksPos", 3); m.put("stickMode", 2); m.put("stickInvert", 5);
        m.put("armCh", 5); m.put("armHigh", 1750); m.put("armLow", 1650); m.put("currentLimit", 50); m.put("statsRows", 7);
        m.put("batActive", 1);
        return m;
    }

    public static void main(String[] args) throws Exception {
        I18n.load(null, "en");
        // ---------- a round trip ----------
        Map<String, Object> in = sample();
        String text = SettingsFile.export(in, "2026-11-08 12:00", "0.9.0");
        check(text.startsWith("# AX-HUD settings backup (format 1)\n# app 0.9.0, exported 2026-11-08 12:00\n"), "header");
        SettingsFile.Summary s = SettingsFile.inspect(text);
        check(s.usable && s.ignored == 0 && s.entries == in.size(), "everything comes back: " + s.message + " (" + s.entries + "/" + in.size() + ")");
        check(s.values.equals(in), "the values are identical, including rules with new lines, $ names and { }");
        check(s.soundRules == 2 && s.warningRules == 2 && s.checks == 1 && s.ruleProblems == 0, "rule counts: " + s.message);
        check(s.message.equals("32 settings, including 2 sound rules, 2 warning rules and 1 checks") || s.message.startsWith(s.entries + " settings, including 2 sound rules, 2 warning rules and 1 checks"), "message: " + s.message);
        check(SettingsFile.export(s.values, "2026-11-08 12:00", "0.9.0").equals(text), "exporting what was imported gives the same file");
        check(SettingsFile.export(new HashMap<String, Object>(), "x", "y").split("\n").length == 2, "nothing to export: only the header");

        // special characters in text values
        Map<String, Object> odd = new HashMap<String, Object>();
        odd.put("rules", "ch5 >= 1 -> tab\there \\ backslash \u00C7\u0131\u011F \u4E2D\u6587 \u201Cquotes\u201D\r\nline2");
        SettingsFile.Summary o = SettingsFile.inspect(SettingsFile.export(odd, "x", "y"));
        check(o.usable && ((String) o.values.get("rules")).contains("tab\there \\ backslash \u00C7\u0131\u011F \u4E2D\u6587 \u201Cquotes\u201D") && ((String) o.values.get("rules")).contains("\nline2"), "tabs, backslashes, Turkish and Chinese text survive");

        // ---------- files from Windows ----------
        String win = "\uFEFF" + text.replace("\n", "\r\n");
        SettingsFile.Summary w = SettingsFile.inspect(win);
        check(w.usable && w.values.equals(in), "a byte-order mark and CRLF line endings are fine");

        // ---------- what is refused or ignored ----------
        check(!SettingsFile.inspect("").usable && !SettingsFile.inspect(null).usable && !SettingsFile.inspect("   \n").usable, "an empty file is refused");
        check(!SettingsFile.inspect("PK\u0003\u0004\u0000binary").usable && SettingsFile.inspect("PK\u0003\u0004\u0000binary").message.contains("text file"), "binary is refused");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < SettingsFile.MAX_CHARS / 8; i++) big.append("B\tfill\ttrue\n");
        check(!SettingsFile.inspect(big.toString()).usable && SettingsFile.inspect(big.toString()).message.contains("too large"), "a huge file is refused");
        check(!SettingsFile.inspect("hello\nworld\n").usable, "text that is not a settings file is refused");
        check(!SettingsFile.inspect("# only a comment\n").usable && SettingsFile.inspect("# only a comment\n").message.contains("No settings"), "only comments");
        SettingsFile.Summary mix = SettingsFile.inspect(text + "S\tevilKey\tvalue\nI\tfill\t1\nB\tsplashOn\tmaybe\nI\tstickMode\t9\nI\tstickMode\tabc\nI\trotation\t45\nS\tlanguage\txx\nS\tupdateChannel\tbeta\nS\tcamera\t../../etc\nS\tbatProfiles\tgarbage\nB\n");
        check(mix.usable && mix.entries == in.size() && mix.ignored == 11, "bad lines are ignored, good ones kept (" + mix.entries + " kept, " + mix.ignored + " ignored)");
        check(mix.values.get("stickMode").equals(2) && mix.values.get("rotation").equals(90) && mix.values.get("language").equals("tr") && mix.values.get("camera").equals("1"), "an invalid value never replaces a good one");
        check(!mix.values.containsKey("evilKey"), "keys outside the schema are never read");
        check(SettingsFile.inspect("S\trules\tgarbage line\nI\tsticksPos\t4\n").ruleProblems == 1, "rule lines with problems are counted but allowed");
        check(SettingsFile.inspect("S\trules\tch5 >= 1 -> a\n").values.size() == 1 && SettingsFile.inspect("S\trules\tch5 >= 1 -> a\n").message.startsWith("1 settings, including 1 sound rules"), "a file with one setting");
        SettingsFile.Summary dup = SettingsFile.inspect("I\tstickMode\t1\nI\tstickMode\t3\n");
        check(dup.values.get("stickMode").equals(3), "a repeated key: the last one wins");
        String ok1 = "B\tfill\ttrue\n";
        check(SettingsFile.inspect(ok1 + "I\tarmHigh\t3000\n").ignored == 1 && SettingsFile.inspect(ok1 + "I\tcurrentLimit\t4\n").ignored == 1 && SettingsFile.inspect("I\tcurrentLimit\t300\nI\tstatsRows\t63\n").ignored == 0, "range limits");
        check(!SettingsFile.inspect("I\tarmHigh\t3000\n").usable, "a file with nothing valid in it is refused");
        check(SettingsFile.inspect("S\tbatProfiles\tLi-ion 6S|6|4.15|3.70|3.55\n").usable && !SettingsFile.inspect("S\tbatProfiles\tLi-ion 6S|6|4.15|3.50|3.55\n").usable, "battery profiles must be valid");
        // a wrong type for a known key
        check(SettingsFile.inspect(ok1 + "I\trules\t5\nS\tfill\ttrue\n").ignored == 2, "the wrong type is ignored");

        // only schema keys are written
        Map<String, Object> withJunk = sample(); withJunk.put("rulesBackup", "old rules"); withJunk.put("settingsBackup", "old"); withJunk.put("somethingElse", 5);
        String t2 = SettingsFile.export(withJunk, "x", "y");
        check(!t2.contains("rulesBackup") && !t2.contains("settingsBackup") && !t2.contains("somethingElse"), "backups and unknown keys are never exported");
        Map<String, Object> wrongType = new HashMap<String, Object>(); wrongType.put("rotation", "ninety"); wrongType.put("fill", 1);
        check(SettingsFile.export(wrongType, "x", "y").split("\n").length == 2, "a stored value of the wrong type is left out");

        // ---------- the defaults make an export complete ----------
        Map<String, Object> defs = new HashMap<String, Object>();
        StringBuilder noDefault = new StringBuilder();
        for (String k : SettingsFile.keys()) {
            Object dv = SettingsFile.defaultOf(k);
            if (dv == null) { noDefault.append(k).append(' '); continue; }
            defs.put(k, dv);
        }
        check(noDefault.toString().trim().equals("rules warnRules checkRules barsConfig batProfiles camera packs aircraftStore aircraftActive"), "only the texts with their own defaults and the camera have none: " + noDefault);
        SettingsFile.Summary ds = SettingsFile.inspect(SettingsFile.export(defs, "x", "y"));
        check(ds.usable && ds.ignored == 0 && ds.entries == defs.size(), "every default is accepted by the reader (" + ds.entries + "/" + defs.size() + ", ignored " + ds.ignored + ")");

        // ---------- the schema covers every setting the app uses ----------
        Set<String> used = new HashSet<String>();
        File dir = new File("app/src/main/java/dev/ax12/announcer");
        Pattern lit = Pattern.compile("(?:getString|getInt|getBoolean)\\(\"([A-Za-z0-9_]+)\"|Prefs\\.(?:int|bool|setInt|setBool)\\([^,]+,\\s*\"([A-Za-z0-9_]+)\"|(?:putString|putInt|putBoolean)\\(\"([A-Za-z0-9_]+)\"");
        for (File f : dir.listFiles()) {
            if (!f.getName().endsWith(".kt")) continue;
            Matcher m = lit.matcher(new String(Files.readAllBytes(f.toPath()), "UTF-8"));
            while (m.find()) used.add(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
        }
        for (String tile : new String[]{"stats", "radar", "check", "chart"}) for (String suffix : new String[]{"On", "Size", "Pos", "Opacity"}) used.add(tile + suffix);
        for (String tile : new String[]{"sticks", "stats", "radar", "check", "chart"}) for (String suffix : new String[]{"X", "Y", "Scale"}) used.add(tile + suffix);
        Set<String> notExported = new HashSet<String>();
        notExported.add("rulesBackup"); notExported.add("settingsBackup");
        notExported.add("guideSeen");               // which welcome guide this device has shown: device state, not a setting to back up
        StringBuilder missing = new StringBuilder();
        for (String k : used) if (!SettingsFile.isKnown(k) && !notExported.contains(k)) missing.append(k).append(' ');
        check(used.size() > 40, "found the settings in the source: " + used.size());
        check(missing.length() == 0, "every setting used in the code is in the backup schema (missing: " + missing + ")");
        StringBuilder stale = new StringBuilder();
        for (String k : SettingsFile.keys()) if (!used.contains(k)) stale.append(k).append(' ');
        check(stale.length() == 0, "every setting in the schema is used in the code (stale: " + stale + ")");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
