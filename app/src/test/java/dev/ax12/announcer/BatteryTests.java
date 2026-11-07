// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tests for battery profiles, the derived cell value and the limit names in rules. */
public class BatteryTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    static class Rec implements Announcer {
        final List<String> said = new ArrayList<String>();
        public void say(String text, boolean urgent) { said.add((urgent ? "!" : "") + text); }
    }

    public static void main(String[] args) {
        // ---------- profiles ----------
        List<BatteryProfiles.Profile> d = BatteryProfiles.defaults();
        check(d.size() == 2 && d.get(0).name.equals("Li-ion 6S") && d.get(1).name.equals("LiPo 6S"), "two default profiles");
        check(near(d.get(0).low, 3.70) && near(d.get(0).crit, 3.55) && near(d.get(1).low, 3.85) && near(d.get(1).crit, 3.70), "the limits you use today");
        check(BatteryProfiles.validate(d) == null, "the defaults are valid");
        String text = BatteryProfiles.encode(d);
        List<BatteryProfiles.Profile> back = BatteryProfiles.decode(text);
        check(BatteryProfiles.encode(back).equals(text) && back.size() == 2 && back.get(1).cells == 6, "encode and decode agree");
        check(BatteryProfiles.decode(null).size() == 2 && BatteryProfiles.decode("").size() == 2 && BatteryProfiles.decode("garbage\nmore").size() == 2, "nothing usable gives the defaults");
        List<BatteryProfiles.Profile> mixed = BatteryProfiles.decode("bad line\nMy 4S|4|4.20|3.60|3.40\nLiPo 6S|6|4.15|9.0|3.7\n");
        check(mixed.size() == 1 && mixed.get(0).name.equals("My 4S") && mixed.get(0).cells == 4, "damaged lines are skipped: " + mixed.size());
        check(BatteryProfiles.decode("A|6|4.15|3.7|3.55\na|6|4.15|3.7|3.55\n").size() == 1, "a repeated name is skipped");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 12; i++) many.append("P").append(i).append("|6|4.15|3.70|3.55\n");
        check(BatteryProfiles.decode(many.toString()).size() == BatteryProfiles.MAX, "at most eight profiles");
        check(BatteryProfiles.cleanName("  a|b\nc  ").equals("a b c") && BatteryProfiles.cleanName("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length() <= 16 && BatteryProfiles.cleanName(null).isEmpty(), "names are cleaned");

        // validation messages
        List<BatteryProfiles.Profile> v = BatteryProfiles.defaults();
        v.get(0).cells = 13;  check(BatteryProfiles.validate(v).contains("Profile 1") && BatteryProfiles.validate(v).contains("cells"), "13 cells: " + BatteryProfiles.validate(v)); v.get(0).cells = 6;
        v.get(1).crit = 3.90; check(BatteryProfiles.validate(v).contains("Profile 2"), "critical above low: " + BatteryProfiles.validate(v)); v.get(1).crit = 3.70;
        v.get(1).full = 4.6;  check(BatteryProfiles.validate(v) != null, "full above 4.4 V"); v.get(1).full = 4.15;
        v.get(0).crit = 2.0;  check(BatteryProfiles.validate(v) != null, "critical below 2.5 V"); v.get(0).crit = 3.55;
        v.get(1).name = "li-ion 6s"; check(BatteryProfiles.validate(v).contains("same name"), "two equal names: " + BatteryProfiles.validate(v)); v.get(1).name = "LiPo 6S";
        v.get(1).name = "  "; check(BatteryProfiles.validate(v).contains("name"), "an empty name"); v.get(1).name = "LiPo 6S";
        check(BatteryProfiles.validate(new ArrayList<BatteryProfiles.Profile>()) != null, "an empty list is refused");
        check(BatteryProfiles.parseStrict(text) != null && BatteryProfiles.parseStrict("bad") == null && BatteryProfiles.parseStrict("") == null && BatteryProfiles.parseStrict(text + "x|1|2|3|4\n") == null, "strict reading for files");

        // ---------- the derived value ----------
        Map<String, Double> n = new HashMap<String, Double>();
        n.put("vbat", 21.6);
        BatteryProfiles.addDerived(n, 6);
        check(near(n.get("cell"), 3.6), "21.6 V on 6 cells is 3.6 V per cell");
        n = new HashMap<String, Double>(); n.put("vbat", 0.0); BatteryProfiles.addDerived(n, 6);
        check(!n.containsKey("cell"), "a flight controller on USB (0 V) has no cell value");
        n = new HashMap<String, Double>(); n.put("vbat", 14.9); BatteryProfiles.addDerived(n, 6);
        check(!n.containsKey("cell"), "below 2.5 V per cell is not a connected pack");
        n = new HashMap<String, Double>(); n.put("vbat", 15.0); BatteryProfiles.addDerived(n, 6);
        check(n.containsKey("cell"), "exactly 2.5 V per cell counts");
        n = new HashMap<String, Double>(); BatteryProfiles.addDerived(n, 6);
        check(n.isEmpty(), "no battery value, no cell value");
        n = new HashMap<String, Double>(); n.put("vbat", 16.8); BatteryProfiles.addDerived(n, 4);
        check(near(n.get("cell"), 4.2), "the same battery on a 4 cell profile");

        // ---------- the limit names in rules ----------
        check(BatteryProfiles.isMacro("$low") && BatteryProfiles.isMacro("$CRIT") && BatteryProfiles.isMacro("$full") && !BatteryProfiles.isMacro("$x") && !BatteryProfiles.isMacro("low") && !BatteryProfiles.isMacro(null), "which names are allowed");
        RuleParser.Result r = RuleParser.parse("cell < $low and cell >= $crit -> battery low");
        check(r.errors.isEmpty() && r.rules.size() == 1, "a rule with names parses: " + r.errors);
        RuleParser.Result unk = RuleParser.parse("cell < $nothing -> x");
        check(unk.errors.size() == 1 && unk.errors.get(0).contains("$nothing") && unk.errors.get(0).contains("$low"), "an unknown name is an error that lists the right ones: " + unk.errors);
        Map<String, Double> liPo = BatteryProfiles.macros(BatteryProfiles.defaults().get(1));
        RuleParser.Result rl = RuleParser.parse("cell < $low -> x", liPo);
        check(near(rl.rules.get(0).orGroups.get(0).get(0).value, 3.85) && "low".equals(rl.rules.get(0).orGroups.get(0).get(0).valueRef), "the name is replaced by the profile's number and remembered");
        check(RuleParser.parse("CELL < $LOW -> x").errors.isEmpty(), "names are not case sensitive");
        check(!RuleParser.parse("cell < $ -> x").errors.isEmpty() && !RuleParser.parse("cell < 3.5 $low -> x").errors.isEmpty(), "a lone $ is an error");
        check(RuleParser.parse("vbat / 6 < 3.70 -> old style").errors.isEmpty(), "old rules with numbers still work");

        // the rules editor keeps the name, and checks it
        RuleText.Spec spec = RuleText.fromLine("cell < $low and cell >= $crit -> battery low @30");
        check(spec != null && spec.conds.get(0).value.equals("$low") && spec.conds.get(1).value.equals("$crit"), "the editor shows $low and $crit");
        check(RuleText.line(spec).equals("cell < $low and cell >= $crit -> battery low @30"), "and writes the same line back: " + RuleText.line(spec));
        check(RuleText.validate(spec) == null, "the editor accepts names");
        spec.conds.get(0).value = "$nope";
        check(RuleText.validate(spec) != null && RuleText.validate(spec).contains("not a number"), "but not an unknown name: " + RuleText.validate(spec));
        spec.conds.get(0).value = "$LOW";
        check(RuleText.validate(spec) == null, "upper case is fine");
        check(RuleText.describe(RuleText.fromLine("cell < $low -> x")).equals("When cell < $low: say \u201Cx\u201D"), "the description shows the name: " + RuleText.describe(RuleText.fromLine("cell < $low -> x")));

        // ---------- switching the profile changes when the rules fire, not the rules ----------
        String rules = "hold = 0\ncell < $low and cell >= $crit -> battery low\ncell < $crit -> !battery critical\n";
        for (int p = 0; p < 2; p++) {
            BatteryProfiles.Profile prof = BatteryProfiles.defaults().get(p);
            Rec rec = new Rec();
            RuleEngine eng = new RuleEngine(RuleParser.parse(rules, BatteryProfiles.macros(prof)), rec);
            Map<String, Double> t = new HashMap<String, Double>();
            t.put("vbat", 25.0); BatteryProfiles.addDerived(t, prof.cells); eng.update(t, new HashMap<String, String>(), 100);
            t.clear(); t.put("vbat", 6 * 3.78); BatteryProfiles.addDerived(t, prof.cells); eng.update(t, new HashMap<String, String>(), 200);   // 3.78 V per cell
            boolean lowSaid = rec.said.contains("battery low");
            check(lowSaid == (p == 1), (p == 0 ? "Li-ion" : "LiPo") + " at 3.78 V per cell: low warning " + (p == 1 ? "yes" : "no") + ": " + rec.said);
            t.clear(); t.put("vbat", 6 * 3.62); BatteryProfiles.addDerived(t, prof.cells); eng.update(t, new HashMap<String, String>(), 300);   // 3.62
            check(rec.said.contains("battery low"), "at 3.62 both profiles warn: " + rec.said);
            t.clear(); t.put("vbat", 6 * 3.60); BatteryProfiles.addDerived(t, prof.cells); eng.update(t, new HashMap<String, String>(), 400);
            t.clear(); t.put("vbat", 6 * 3.50); BatteryProfiles.addDerived(t, prof.cells); eng.update(t, new HashMap<String, String>(), 500);   // 3.50
            check(rec.said.contains("!battery critical"), "at 3.50 both profiles are critical: " + rec.said);
        }
        // LiPo at 3.65 is already critical, Li-ion is not
        for (int p = 0; p < 2; p++) {
            BatteryProfiles.Profile prof = BatteryProfiles.defaults().get(p);
            Rec rec = new Rec();
            RuleEngine eng = new RuleEngine(RuleParser.parse(rules, BatteryProfiles.macros(prof)), rec);
            Map<String, Double> t = new HashMap<String, Double>();
            t.put("vbat", 25.0); BatteryProfiles.addDerived(t, 6); eng.update(t, new HashMap<String, String>(), 100);
            t.clear(); t.put("vbat", 6 * 3.65); BatteryProfiles.addDerived(t, 6); eng.update(t, new HashMap<String, String>(), 200);
            check(rec.said.contains("!battery critical") == (p == 1), "3.65 V per cell is critical only for the LiPo: " + rec.said);
        }
        // no battery: the rules stay silent and a warning is not left on
        Rec silent = new Rec();
        RuleEngine se = new RuleEngine(RuleParser.parse(rules, BatteryProfiles.macros(BatteryProfiles.defaults().get(0))), silent);
        Map<String, Double> usb = new HashMap<String, Double>(); usb.put("vbat", 0.0); BatteryProfiles.addDerived(usb, 6);
        for (int i = 0; i < 5; i++) se.update(usb, new HashMap<String, String>(), 100 * (i + 1));
        check(silent.said.isEmpty(), "powered from USB: silent");
        // a changed profile means new rules are loaded with the new numbers (the app reloads the engines)
        Rec after = new Rec();
        BatteryProfiles.Profile custom = new BatteryProfiles.Profile("4S", 4, 4.15, 3.60, 3.40);
        RuleEngine ce = new RuleEngine(RuleParser.parse(rules, BatteryProfiles.macros(custom)), after);
        Map<String, Double> c4 = new HashMap<String, Double>(); c4.put("vbat", 16.8); BatteryProfiles.addDerived(c4, 4); ce.update(c4, new HashMap<String, String>(), 100);
        c4.clear(); c4.put("vbat", 4 * 3.55); BatteryProfiles.addDerived(c4, 4); ce.update(c4, new HashMap<String, String>(), 200);
        check(after.said.contains("battery low") && !after.said.contains("!battery critical"), "a 4S profile: 3.55 V per cell is low but not critical: " + after.said);

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
