// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Plain-Java tests (no JUnit needed): run RuleEngineTest.main. Exits 1 on any failure. */
public class RuleEngineTest {
    static int failures = 0, checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL: " + what); }
    }

    static class Rec implements Announcer {
        final List<String> said = new ArrayList<String>();
        public void say(String text, boolean urgent) { said.add((urgent ? "!" : "") + text); }
        String joined() { return said.toString(); }
    }

    static Map<String, Double> ch(Object... kv) {
        Map<String, Double> m = new HashMap<String, Double>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return m;
    }

    public static void main(String[] args) {
        // 1. parsing
        RuleParser.Result p = RuleParser.parse("# c\nch5 >= 1500 -> drone armed | drone disarmed\n\nch6 >= 1450 and ch6 <= 1550 or ch7 > 1800 -> horizon @5\nhysteresis = 30\nhold = 0\ninitial = on\n");
        check(p.errors.isEmpty(), "parse sample has no errors: " + p.errors);
        check(p.rules.size() == 2, "two rules parsed");
        check(p.rules.get(0).onFalse.equals("drone disarmed"), "false text parsed");
        check(p.rules.get(1).repeatSec == 5, "repeat parsed");
        check(p.rules.get(1).orGroups.size() == 2 && p.rules.get(1).orGroups.get(0).size() == 2, "and/or structure");
        check(p.settings.hysteresis == 30 && p.settings.holdMs == 0 && p.settings.announceInitial, "settings parsed");
        check(p.rules.get(0).line == 2, "line number kept");

        RuleParser.Result bad = RuleParser.parse("ch5 >= 1500 drone armed\nch5 ?? 3 -> x\nch5 >= 1500 ->\nhold = abc\n -> hi\n");
        check(bad.errors.size() == 5, "5 errors expected, got " + bad.errors.size() + " " + bad.errors);
        check(bad.rules.isEmpty(), "no rules from bad input");

        RuleParser.Result urg = RuleParser.parse("ch9 < 1300 -> !battery critical | !battery ok @10");
        check(urg.rules.get(0).urgentTrue && urg.rules.get(0).urgentFalse, "urgent flags");
        check(urg.rules.get(0).onTrue.equals("battery critical"), "urgent text stripped: " + urg.rules.get(0).onTrue);
        check(urg.rules.get(0).repeatSec == 10, "repeat with urgent");

        // 2. edge trigger + silent start
        Rec r = new Rec();
        RuleEngine e = new RuleEngine(RuleParser.parse("hold = 0\nch5 >= 1500 -> drone armed | drone disarmed"), r);
        long t = 0;
        e.update(ch("ch5", 1000), t += 50);
        e.update(ch("ch5", 1000), t += 50);
        check(r.said.isEmpty(), "silent while false");
        e.update(ch("ch5", 1800), t += 50);
        check(r.joined().equals("[drone armed]"), "armed spoken once: " + r.joined());
        e.update(ch("ch5", 1800), t += 50);
        e.update(ch("ch5", 1810), t += 50);
        check(r.said.size() == 1, "not repeated while true");
        e.update(ch("ch5", 1000), t += 50);
        check(r.joined().equals("[drone armed, drone disarmed]"), "disarmed spoken: " + r.joined());

        // 3. silent start when already true, and initial = on
        Rec r2 = new Rec();
        RuleEngine e2 = new RuleEngine(RuleParser.parse("hold = 0\nch5 >= 1500 -> armed"), r2);
        e2.update(ch("ch5", 1900), 50);
        check(r2.said.isEmpty(), "already-true at start is silent");
        Rec r3 = new Rec();
        RuleEngine e3 = new RuleEngine(RuleParser.parse("hold = 0\ninitial = on\nch5 >= 1500 -> armed"), r3);
        e3.update(ch("ch5", 1900), 50);
        check(r3.joined().equals("[armed]"), "initial = on speaks once: " + r3.joined());

        // 4. hysteresis (25 us): after true, 1490 and 1480 stay true, 1470 turns false
        Rec r4 = new Rec();
        RuleEngine e4 = new RuleEngine(RuleParser.parse("hold = 0\nch5 >= 1500 -> on | off"), r4);
        t = 0;
        e4.update(ch("ch5", 1000), t += 20);
        e4.update(ch("ch5", 1500), t += 20);
        e4.update(ch("ch5", 1490), t += 20);
        e4.update(ch("ch5", 1480), t += 20);
        check(r4.joined().equals("[on]"), "no chatter inside the hysteresis band: " + r4.joined());
        e4.update(ch("ch5", 1470), t += 20);
        check(r4.joined().equals("[on, off]"), "false below threshold minus hysteresis: " + r4.joined());
        e4.update(ch("ch5", 1499), t += 20);
        check(r4.said.size() == 2, "needs the full threshold to turn on again: " + r4.joined());
        e4.update(ch("ch5", 1500), t += 20);
        check(r4.said.size() == 3, "turns on again at the threshold");

        // 5. hold (debounce): a 100 ms blip with hold = 200 does not fire
        Rec r5 = new Rec();
        RuleEngine e5 = new RuleEngine(RuleParser.parse("hold = 200\nch5 >= 1500 -> go"), r5);
        t = 0;
        e5.update(ch("ch5", 1000), t += 50);
        e5.update(ch("ch5", 1800), t += 50);
        e5.update(ch("ch5", 1800), t += 50);
        e5.update(ch("ch5", 1000), t += 50);
        check(r5.said.isEmpty(), "short blip ignored");
        e5.update(ch("ch5", 1800), t += 50);
        for (int i = 0; i < 6; i++) e5.update(ch("ch5", 1800), t += 50);
        check(r5.joined().equals("[go]"), "held change fires once: " + r5.joined());

        // 6. and / or
        Rec r6 = new Rec();
        RuleEngine e6 = new RuleEngine(RuleParser.parse("hold = 0\nch6 >= 1450 and ch6 <= 1550 -> horizon | not horizon\nch7 > 1800 or ch8 > 1800 -> either"), r6);
        t = 0;
        e6.update(ch("ch6", 1000, "ch7", 1000, "ch8", 1000), t += 20);
        e6.update(ch("ch6", 1500, "ch7", 1000, "ch8", 1000), t += 20);
        e6.update(ch("ch6", 1500, "ch7", 1000, "ch8", 1900), t += 20);
        e6.update(ch("ch6", 2000, "ch7", 1000, "ch8", 1900), t += 20);
        check(r6.joined().equals("[horizon, either, not horizon]"), "and/or behaviour: " + r6.joined());

        // 7. repeat every 2 s while true
        Rec r7 = new Rec();
        RuleEngine e7 = new RuleEngine(RuleParser.parse("hold = 0\nch9 < 1300 -> low battery @2"), r7);
        t = 0;
        e7.update(ch("ch9", 2000), t += 100);
        e7.update(ch("ch9", 1000), t += 100);
        for (int i = 0; i < 45; i++) e7.update(ch("ch9", 1000), t += 100);   // 4.5 s more
        check(r7.said.size() == 3, "spoken at 0, 2 and 4 s: " + r7.said.size());
        e7.update(ch("ch9", 2000), t += 100);
        int n = r7.said.size();
        for (int i = 0; i < 40; i++) e7.update(ch("ch9", 2000), t += 100);
        check(r7.said.size() == n, "stops repeating when false");

        // 8. missing channel: rule skipped, others still work
        Rec r8 = new Rec();
        RuleEngine e8 = new RuleEngine(RuleParser.parse("hold = 0\nch20 > 1500 -> never\nch5 >= 1500 -> yes"), r8);
        e8.update(ch("ch5", 1000), 50);
        e8.update(ch("ch5", 1800), 100);
        check(r8.joined().equals("[yes]"), "missing channel skipped: " + r8.joined());

        // 9. urgent flag reaches the announcer
        Rec r9 = new Rec();
        RuleEngine e9 = new RuleEngine(RuleParser.parse("hold = 0\nch9 < 1300 -> !battery critical"), r9);
        e9.update(ch("ch9", 2000), 50);
        e9.update(ch("ch9", 1000), 100);
        check(r9.joined().equals("[!battery critical]"), "urgent passes through: " + r9.joined());

        // 10. reset makes the next reading silent again
        Rec r10 = new Rec();
        RuleEngine e10 = new RuleEngine(RuleParser.parse("hold = 0\nch5 >= 1500 -> armed"), r10);
        e10.update(ch("ch5", 1000), 50);
        e10.update(ch("ch5", 1800), 100);
        e10.reset();
        e10.update(ch("ch5", 1800), 150);
        check(r10.said.size() == 1, "reset: no second announcement: " + r10.joined());

        // 11. a realistic run with the aux ranges of the AKB7 config
        String sample =
            "hold = 100\n" +
            "ch5 >= 1950 -> armed | disarmed\n" +
            "ch6 >= 1950 -> angle mode\n" +
            "ch6 >= 1450 and ch6 <= 1550 -> horizon mode\n" +
            "ch7 >= 1875 -> !return to home | return to home off\n" +
            "ch10 <= 1125 or ch10 >= 1875 -> position hold | position hold off\n" +
            "ch10 >= 1950 -> altitude hold\n" +
            "ch9 <= 1100 -> airmode on | airmode off\n";
        RuleParser.Result sp = RuleParser.parse(sample);
        check(sp.errors.isEmpty(), "sample rules parse: " + sp.errors);
        Rec r11 = new Rec();
        RuleEngine e11 = new RuleEngine(sp, r11);
        t = 0;
        Object[] idle = {"ch5", 1000, "ch6", 1000, "ch7", 1000, "ch9", 1500, "ch10", 1500};
        for (int i = 0; i < 5; i++) e11.update(ch(idle), t += 50);
        check(r11.said.isEmpty(), "idle switches are silent");
        // airmode pot to minimum, arm, angle, then horizon, then RTH, then back
        Object[][] steps = {
            {"ch5", 1000, "ch6", 1000, "ch7", 1000, "ch9", 1000, "ch10", 1500},
            {"ch5", 2000, "ch6", 1000, "ch7", 1000, "ch9", 1000, "ch10", 1500},
            {"ch5", 2000, "ch6", 2000, "ch7", 1000, "ch9", 1000, "ch10", 1500},
            {"ch5", 2000, "ch6", 1500, "ch7", 1000, "ch9", 1000, "ch10", 1500},
            {"ch5", 2000, "ch6", 1500, "ch7", 2000, "ch9", 1000, "ch10", 1500},
            {"ch5", 2000, "ch6", 1500, "ch7", 1000, "ch9", 1000, "ch10", 1500},
            {"ch5", 1000, "ch6", 1500, "ch7", 1000, "ch9", 1000, "ch10", 1500},
        };
        for (Object[] st : steps) {
            for (int i = 0; i < 4; i++) e11.update(ch(st), t += 50);
        }
        String got = r11.joined();
        String want = "[airmode on, armed, angle mode, horizon mode, !return to home, return to home off, disarmed]";
        check(got.equals(want), "realistic sequence:\n   got  " + got + "\n   want " + want);

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
