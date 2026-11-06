package dev.ax12.announcer;

import java.util.List;

/** Tests for the GUI's rules-text model. */
public class RuleTextTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) {
        String text =
            "# comment\n" +
            "hysteresis = 25\n" +
            "initial = off\n" +
            "\n" +
            "ch5 >= 1950 -> armed | disarmed\n" +
            "ch6 >= 1450 and ch6 <= 1550 -> horizon mode\n" +
            "vbat / 6 < 3.55 and vbat > 12 -> !battery critical @10\n" +
            "fm ~ \"RTH\" -> return to home active\n" +
            "ch10 <= 1125 or ch10 >= 1875 -> position hold | position hold off\n" +
            "ch1 > 1900 and ch2 > 1900 or ch3 > 1900 -> mixed rule\n" +
            "#off ch7 >= 1875 -> return to home\n" +
            "broken line without arrow\n";
        List<RuleText.Item> items = RuleText.parse(text);

        // untouched text comes back exactly
        check(RuleText.join(items).equals(text), "join(parse(text)) is the same text");

        int rules = 0, disabled = 0, settings = 0, comments = 0, blanks = 0, noSpec = 0;
        for (RuleText.Item it : items) {
            switch (it.kind) {
                case RULE: rules++; if (it.spec == null) noSpec++; break;
                case DISABLED_RULE: disabled++; break;
                case SETTING: settings++; break;
                case COMMENT: comments++; break;
                default: blanks++;
            }
        }
        check(settings == 2 && comments == 1 && blanks == 1, "settings/comment/blank classified: " + settings + comments + blanks);
        check(rules == 7 && disabled == 1, "rules and disabled counted: " + rules + "/" + disabled);
        check(noSpec == 2, "mixed and/or and the broken line have no GUI form: " + noSpec);

        // read the shapes
        RuleText.Spec arm = items.get(4).spec;
        check(arm.conds.size() == 1 && arm.conds.get(0).variable.equals("ch5") && arm.conds.get(0).op.equals(">=") && arm.conds.get(0).value.equals("1950"), "simple rule read");
        check(arm.onTrue.equals("armed") && arm.onFalse.equals("disarmed") && !arm.any, "texts read");
        RuleText.Spec hor = items.get(5).spec;
        check(hor.conds.size() == 2 && !hor.any, "AND rule read");
        RuleText.Spec bat = items.get(6).spec;
        check(bat.conds.get(0).divisor == 6.0 && bat.conds.get(0).value.equals("3.55") && bat.urgentTrue && bat.repeatSec == 10, "per-cell, urgent and repeat read");
        RuleText.Spec fm = items.get(7).spec;
        check(fm.conds.get(0).text && fm.conds.get(0).op.equals("~") && fm.conds.get(0).value.equals("RTH"), "text rule read");
        RuleText.Spec orr = items.get(8).spec;
        check(orr.any && orr.conds.size() == 2, "OR rule read");

        // every readable rule regenerates the same meaning
        for (RuleText.Item it : items) {
            if (it.spec == null) continue;
            String regenerated = RuleText.line(it.spec);
            RuleText.Spec again = RuleText.fromLine(regenerated);
            check(again != null && RuleText.line(again).equals(regenerated), "round trip: " + regenerated);
            check(RuleText.validate(it.spec) == null, "valid: " + regenerated);
        }
        check(RuleText.line(bat).equals("vbat / 6 < 3.55 and vbat > 12 -> !battery critical @10"), "line() matches the typed form: " + RuleText.line(bat));

        // editing one rule leaves everything else alone
        RuleText.Spec edited = arm.copy();
        edited.conds.get(0).value = "1900";
        edited.onTrue = "drone armed";
        RuleText.applySpec(items.get(4), edited);
        String after = RuleText.join(items);
        check(after.contains("ch5 >= 1900 -> drone armed | disarmed\n"), "edited line replaced");
        check(after.replace("ch5 >= 1900 -> drone armed | disarmed", "ch5 >= 1950 -> armed | disarmed").equals(text), "nothing else changed");

        // switch a rule off and on
        RuleText.Item horizon = items.get(5);
        RuleText.setEnabled(horizon, false);
        check(horizon.kind == RuleText.Kind.DISABLED_RULE && horizon.raw.startsWith("#off ch6"), "disabled rule is commented out");
        check(RuleParser.parse(RuleText.join(items)).rules.stream().noneMatch(r -> r.onTrue.equals("horizon mode")), "the engine ignores a disabled rule");
        check(horizon.spec != null, "disabled rule keeps its form");
        RuleText.setEnabled(horizon, true);
        check(horizon.kind == RuleText.Kind.RULE && horizon.raw.equals("ch6 >= 1450 and ch6 <= 1550 -> horizon mode"), "enabled again");
        RuleText.Item off = items.get(10);
        check(off.kind == RuleText.Kind.DISABLED_RULE && off.spec != null && off.spec.onTrue.equals("return to home"), "an existing #off rule is read");
        RuleText.setEnabled(off, true);
        check(off.raw.equals("ch7 >= 1875 -> return to home"), "an existing #off rule can be switched on");

        // add and remove
        RuleText.Spec s = RuleText.Spec.blank();
        s.conds.get(0).variable = "up_lq"; s.conds.get(0).op = "<"; s.conds.get(0).value = "40";
        s.onTrue = "link critical"; s.urgentTrue = true; s.repeatSec = 5;
        check(RuleText.validate(s) == null, "new rule is valid");
        items.add(RuleText.newRule(s));
        check(RuleText.join(items).contains("up_lq < 40 -> !link critical @5\n"), "new rule appended");
        check(RuleParser.parse(RuleText.join(items)).rules.stream().anyMatch(r -> r.onTrue.equals("link critical") && r.urgentTrue && r.repeatSec == 5), "engine reads the new rule");

        // validation messages
        RuleText.Spec v = RuleText.Spec.blank();
        check(RuleText.validate(v) != null, "empty rule rejected");
        v.conds.get(0).variable = "ch5"; v.conds.get(0).value = "abc"; v.onTrue = "x";
        check(RuleText.validate(v).contains("not a number"), "bad number explained: " + RuleText.validate(v));
        v.conds.get(0).value = "1500"; v.onTrue = "";
        check(RuleText.validate(v).contains("what to say"), "missing text explained");
        v.onTrue = "a | b";
        check(RuleText.validate(v).contains("cannot contain"), "pipe in text refused");
        v.onTrue = "wait @5";
        check(RuleText.validate(v).contains("@number"), "text ending in @5 refused");
        v.onTrue = "ok"; v.conds.get(0).variable = "ch 5";
        check(RuleText.validate(v).contains("variable"), "bad variable name refused");
        v.conds.get(0).variable = "vbat"; v.conds.get(0).divisor = 0;
        check(RuleText.validate(v).contains("divisor"), "zero divisor refused");

        // settings
        check(RuleText.getSetting(items, "hysteresis", "?").equals("25"), "setting read");
        check(RuleText.getSetting(items, "scale", "auto").equals("auto"), "missing setting gives the default");
        RuleText.setSetting(items, "hysteresis", "40");
        check(RuleText.join(items).contains("hysteresis = 40\n") && !RuleText.join(items).contains("hysteresis = 25"), "setting replaced");
        RuleText.setSetting(items, "scale", "permille");
        String joined = RuleText.join(items);
        check(joined.indexOf("scale = permille") < joined.indexOf("ch5 >="), "new setting placed before the rules");
        check(RuleParser.parse(joined).settings.scale.equals("permille") && RuleParser.parse(joined).settings.hysteresis == 40, "engine reads the settings");

        // friendly sentences
        check(RuleText.describe(arm).contains("armed") && RuleText.describe(arm).contains("ch5 \u2265 1950") && RuleText.describe(arm).contains("disarmed"), "description: " + RuleText.describe(arm));
        check(RuleText.describe(bat).contains("vbat \u00F7 6 < 3.55") && RuleText.describe(bat).contains("every 10 s") && RuleText.describe(bat).contains("(urgent)"), "description of a per-cell rule: " + RuleText.describe(bat));

        // the shipped default rules load cleanly into the GUI model
        String defaults = readDefaultRules();
        check(!defaults.isEmpty(), "default rules file found");
        List<RuleText.Item> def = RuleText.parse(defaults);
        int simple = 0, bad = 0;
        for (RuleText.Item it : def) if (it.kind == RuleText.Kind.RULE) { if (it.spec != null) simple++; else bad++; }
        check(bad == 0 && simple >= 10, "all default rules are editable in the GUI: " + simple + " ok, " + bad + " not");
        check(RuleText.join(def).replace("\n", "").equals(defaults.replace("\n", "")), "default rules survive parse and join unchanged");

        // empty and odd input
        check(RuleText.parse("").size() <= 1 && RuleText.parse(null).isEmpty(), "empty text");
        check(RuleText.join(RuleText.parse("a -> b")).equals("a -> b\n"), "a final newline is added");
        check(RuleText.fmt(3.5).equals("3.5") && RuleText.fmt(1950).equals("1950") && RuleText.fmt(0.1 + 0.2).equals("0.3"), "number formatting");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** The default rules live in DefaultRules.kt; read the text between the triple quotes. */
    static String readDefaultRules() {
        try {
            String src = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                System.getProperty("defaultRules", "app/src/main/java/dev/ax12/announcer/DefaultRules.kt"))), java.nio.charset.StandardCharsets.UTF_8);
            int a = src.indexOf("\"\"\"") + 3, b = src.indexOf("\"\"\"", a);
            return src.substring(a, b);
        } catch (Exception e) {
            return "";
        }
    }
}
