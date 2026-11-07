package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules text as a list of lines the GUI can edit one at a time, without touching comments or
 * settings. The text stays the single source of truth: the GUI builds the same lines you could type.
 *
 *   ch5 >= 1950 -> armed | disarmed          a rule
 *   #off ch5 >= 1950 -> armed                a switched-off rule (a comment, so the engine ignores it)
 *
 * Plain Java, no Android classes, unit tested on a PC.
 */
public final class RuleText {

    public enum Kind { BLANK, COMMENT, SETTING, RULE, DISABLED_RULE }

    public static final String OFF_PREFIX = "#off ";

    /** One condition: var [/ divisor] op value   or   var op "text". */
    public static final class Cond {
        public String variable = "";
        public String op = ">=";
        public String value = "";
        public double divisor = 1.0;
        public boolean text = false;

        public Cond() {}

        public Cond(String variable, String op, String value, double divisor, boolean text) {
            this.variable = variable;
            this.op = op;
            this.value = value;
            this.divisor = divisor;
            this.text = text;
        }

        public Cond copy() { return new Cond(variable, op, value, divisor, text); }
    }

    /** What the rule editor shows. */
    public static final class Spec {
        public final List<Cond> conds = new ArrayList<Cond>();
        public boolean any = false;              // false: all conditions (AND), true: any condition (OR)
        public String onTrue = "";
        public boolean urgentTrue = false;
        public String onFalse = "";
        public boolean urgentFalse = false;
        public int repeatSec = 0;                // 0 = once

        public static Spec blank() {
            Spec s = new Spec();
            s.conds.add(new Cond());
            return s;
        }

        public Spec copy() {
            Spec s = new Spec();
            for (Cond c : conds) s.conds.add(c.copy());
            s.any = any;
            s.onTrue = onTrue;
            s.urgentTrue = urgentTrue;
            s.onFalse = onFalse;
            s.urgentFalse = urgentFalse;
            s.repeatSec = repeatSec;
            return s;
        }
    }

    /** One line of the text. spec is null for rules the GUI cannot show (mixed and/or, or invalid). */
    public static final class Item {
        public Kind kind;
        public String raw;
        public Spec spec;

        public Item(Kind kind, String raw, Spec spec) {
            this.kind = kind;
            this.raw = raw;
            this.spec = spec;
        }
    }

    private static final Pattern SETTING = Pattern.compile("^\\s*([A-Za-z_]+)\\s*=\\s*(\\S+)\\s*$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TRAILING_REPEAT = Pattern.compile("\\s@\\d+\\s*$");

    private RuleText() {}

    // ------------------------------------------------------------------ text <-> items

    public static List<Item> parse(String text) {
        List<Item> items = new ArrayList<Item>();
        if (text == null) return items;
        for (String raw : text.split("\\r?\\n", -1)) {
            String t = raw.trim();
            if (t.isEmpty()) {
                items.add(new Item(Kind.BLANK, raw, null));
            } else if (t.startsWith("#off ") || t.equals("#off")) {
                String rest = t.length() > 5 ? t.substring(5).trim() : "";
                items.add(new Item(Kind.DISABLED_RULE, raw, fromLine(rest)));
            } else if (t.startsWith("#") || t.startsWith("//")) {
                items.add(new Item(Kind.COMMENT, raw, null));
            } else if (isSettingLine(t)) {
                items.add(new Item(Kind.SETTING, raw, null));
            } else {
                items.add(new Item(Kind.RULE, raw, fromLine(t)));
            }
        }
        // a trailing empty element from the final newline is not a line of its own
        if (items.size() > 1 && items.get(items.size() - 1).kind == Kind.BLANK && items.get(items.size() - 1).raw.isEmpty()) {
            items.remove(items.size() - 1);
        }
        return items;
    }

    public static String join(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) sb.append(it.raw).append('\n');
        return sb.toString();
    }

    private static boolean isSettingLine(String t) {
        if (t.contains("->")) return false;
        Matcher m = SETTING.matcher(t);
        if (!m.matches()) return false;
        String n = m.group(1).toLowerCase(Locale.ROOT);
        return n.equals("hysteresis") || n.equals("var_hysteresis") || n.equals("hold")
                || n.equals("initial") || n.equals("scale");
    }

    // ------------------------------------------------------------------ rule line <-> spec

    /** @return null when the line is invalid or too complex for the GUI (mixed and / or) */
    public static Spec fromLine(String line) {
        RuleParser.Result r = RuleParser.parse(line);
        if (!r.errors.isEmpty() || r.rules.size() != 1) return null;
        Rule rule = r.rules.get(0);
        Spec s = new Spec();
        if (rule.orGroups.size() == 1) {
            s.any = false;
            for (Rule.Atom a : rule.orGroups.get(0)) s.conds.add(toCond(a));
        } else {
            s.any = true;
            for (List<Rule.Atom> g : rule.orGroups) {
                if (g.size() != 1) return null;          // (a and b) or c: not shown in the GUI
                s.conds.add(toCond(g.get(0)));
            }
        }
        s.onTrue = rule.onTrue;
        s.urgentTrue = rule.urgentTrue;
        s.onFalse = rule.onFalse == null ? "" : rule.onFalse;
        s.urgentFalse = rule.urgentFalse;
        s.repeatSec = rule.repeatSec;
        return s;
    }

    private static Cond toCond(Rule.Atom a) {
        if (a.isText()) return new Cond(a.name, a.op, a.text, 1.0, true);
        return new Cond(a.name, a.op, a.valueRef != null ? "$" + a.valueRef : fmt(a.value), a.divisor, false);
    }

    /** The rule line the engine will read. Call validate() first. */
    public static String line(Spec s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.conds.size(); i++) {
            Cond c = s.conds.get(i);
            if (i > 0) sb.append(s.any ? " or " : " and ");
            sb.append(c.variable.trim());
            if (c.text) {
                sb.append(' ').append(c.op).append(" \"").append(c.value).append('"');
            } else {
                if (c.divisor != 1.0) sb.append(" / ").append(fmt(c.divisor));
                sb.append(' ').append(c.op).append(' ').append(c.value.trim());
            }
        }
        sb.append(" -> ");
        if (s.urgentTrue) sb.append('!');
        sb.append(s.onTrue.trim());
        if (!s.onFalse.trim().isEmpty()) {
            sb.append(" | ");
            if (s.urgentFalse) sb.append('!');
            sb.append(s.onFalse.trim());
        }
        if (s.repeatSec > 0) sb.append(" @").append(s.repeatSec);
        return sb.toString();
    }

    /** @return null if the rule is fine, otherwise a short message for the user */
    public static String validate(Spec s) {
        if (s.conds.isEmpty()) return I18n.t("Add at least one condition.");
        for (Cond c : s.conds) {
            if (!NAME.matcher(c.variable.trim()).matches()) return I18n.t("Pick a variable (letters, digits and _ only).");
            if (c.text) {
                if (!(c.op.equals("==") || c.op.equals("!=") || c.op.equals("~"))) return I18n.t("Text can use is, is not or contains.");
                if (c.value.contains("\"")) return I18n.t("Text must not contain quotation marks.");
            } else {
                if (c.op.equals("~")) return I18n.t("'contains' works on text variables only.");
                if (c.value.trim().isEmpty()) return I18n.t("Enter a value for {0}.", c.variable.trim());
                if (!BatteryProfiles.isMacro(c.value.trim().toLowerCase(Locale.ROOT))) {
                    try {
                        Double.parseDouble(c.value.trim());
                    } catch (NumberFormatException e) {
                        return I18n.t("'{0}' is not a number.", c.value.trim());
                    }
                }
                if (c.divisor == 0.0) return I18n.t("The divisor cannot be 0.");
            }
        }
        if (s.onTrue.trim().isEmpty()) return I18n.t("Type what to say.");
        for (String t : new String[]{s.onTrue, s.onFalse}) {
            String tpl = TextTemplate.check(t);
            if (tpl != null) return tpl;
            if (t.contains("|") || t.contains("->")) return I18n.t("The spoken text cannot contain | or ->.");
            if (TRAILING_REPEAT.matcher(" " + t).find()) return I18n.t("The spoken text cannot end with @number.");
        }
        RuleParser.Result r = RuleParser.parse(line(s));
        if (!r.errors.isEmpty()) return r.errors.get(0);
        if (r.rules.size() != 1) return I18n.t("The rule could not be read.");
        return null;
    }

    /** The conditions of a rule as words: "vbat < 3.5 and ch5 >= 1500". The joining words come from the language. */
    private static String conditionsText(Spec s) {
        String all = null;
        for (int i = 0; i < s.conds.size(); i++) {
            Cond c = s.conds.get(i);
            String one;
            if (c.text) {
                String q = c.value;
                one = c.op.equals("~") ? I18n.t("{0} contains \u201C{1}\u201D", c.variable, q)
                    : c.op.equals("==") ? I18n.t("{0} is \u201C{1}\u201D", c.variable, q)
                    : I18n.t("{0} is not \u201C{1}\u201D", c.variable, q);
            } else {
                one = c.variable + (c.divisor != 1.0 ? " \u00F7 " + fmt(c.divisor) : "") + " " + symbol(c.op) + " " + c.value;
            }
            all = all == null ? one : (s.any ? I18n.t("{0} or {1}", all, one) : I18n.t("{0} and {1}", all, one));
        }
        return all == null ? "" : all;
    }

    /** One readable sentence for the list of pre-flight checks. */
    public static String describeCheck(Spec s) {
        return I18n.t("Passes when {0}: \u2713 \u201C{1}\u201D", conditionsText(s), s.onTrue);
    }

    /** One readable sentence for the list of warnings. */
    public static String describeWarning(Spec s) {
        String level = s.urgentTrue ? I18n.t("CRITICAL") : s.onTrue.startsWith("*") ? I18n.t("INFO") : I18n.t("WARNING");
        String text = s.onTrue.startsWith("*") ? s.onTrue.substring(1) : s.onTrue;
        String timeout = s.repeatSec > 0 ? I18n.t(", hides after {0} s", s.repeatSec) : "";
        return "[" + level + "] " + I18n.t("When {0}: show \u201C{1}\u201D", conditionsText(s), text) + timeout;
    }

    /** One readable sentence for the list. */
    public static String describe(Spec s) {
        StringBuilder sb = new StringBuilder(I18n.t("When {0}: say \u201C{1}\u201D", conditionsText(s), s.onTrue));
        if (!s.onFalse.trim().isEmpty()) sb.append(I18n.t(", then \u201C{0}\u201D when it turns false", s.onFalse));
        if (s.repeatSec > 0) sb.append(I18n.t(", every {0} s while true", s.repeatSec));
        if (s.urgentTrue) sb.append(I18n.t(" (urgent)"));
        return sb.toString();
    }

    private static String symbol(String op) {
        if (op.equals(">=")) return "\u2265";
        if (op.equals("<=")) return "\u2264";
        if (op.equals("!=")) return "\u2260";
        if (op.equals("==")) return "=";
        return op;
    }

    // ------------------------------------------------------------------ editing helpers

    public static Item newRule(Spec s) { return new Item(Kind.RULE, line(s), s); }

    public static void setEnabled(Item it, boolean on) {
        if (on && it.kind == Kind.DISABLED_RULE) {
            String t = it.raw.trim();
            it.raw = t.length() > 5 ? t.substring(5).trim() : "";
            it.kind = Kind.RULE;
        } else if (!on && it.kind == Kind.RULE) {
            it.raw = OFF_PREFIX + it.raw.trim();
            it.kind = Kind.DISABLED_RULE;
        }
    }

    public static void applySpec(Item it, Spec s) {
        it.spec = s;
        String l = line(s);
        it.raw = (it.kind == Kind.DISABLED_RULE) ? OFF_PREFIX + l : l;
    }

    public static String getSetting(List<Item> items, String name, String def) {
        for (Item it : items) {
            if (it.kind != Kind.SETTING) continue;
            Matcher m = SETTING.matcher(it.raw.trim());
            if (m.matches() && m.group(1).equalsIgnoreCase(name)) return m.group(2);
        }
        return def;
    }

    public static void setSetting(List<Item> items, String name, String value) {
        String raw = name + " = " + value;
        for (Item it : items) {
            if (it.kind != Kind.SETTING) continue;
            Matcher m = SETTING.matcher(it.raw.trim());
            if (m.matches() && m.group(1).equalsIgnoreCase(name)) {
                it.raw = raw;
                return;
            }
        }
        int at = items.size();
        for (int i = 0; i < items.size(); i++) {
            Kind k = items.get(i).kind;
            if (k == Kind.RULE || k == Kind.DISABLED_RULE) { at = i; break; }
        }
        items.add(at, new Item(Kind.SETTING, raw, null));
    }

    public static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e12) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.4f", v);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }
}
