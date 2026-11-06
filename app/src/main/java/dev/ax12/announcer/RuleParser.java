package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the rules text. One rule per line:
 *
 *   ch5 >= 1500 -> drone armed
 *   ch5 >= 1500 -> drone armed | drone disarmed        (second text = when it becomes false)
 *   ch6 >= 1450 and ch6 <= 1550 -> horizon mode        (and / or are allowed, "and" binds first)
 *   ch9 < 1300 -> !battery critical @10                (! = urgent, @10 = repeat every 10 s)
 *   vbat / 6 < 3.5 -> battery low                      (the variable is divided first: volts per cell)
 *   fm ~ "RTH" -> return to home                       (text rules: ==  !=  ~ means contains)
 *   cell < $low -> battery low                         ($low, $crit, $full: the limits of the active battery profile)
 *
 * Settings lines:  hysteresis = 25 (channels, microseconds)   var_hysteresis = 1 (other variables, percent)
 *                  hold = 100   initial = on|off   scale = auto|percent|permille|us
 * Lines starting with # or // are comments.
 */
public final class RuleParser {

    public static final class Settings {
        public double hysteresis = 25;        // same units as the channel values (microseconds)
        public long holdMs = 100;             // a condition must stay changed this long before it fires
        public double varHysteresisPct = 1.0; // for other variables (vbat, lq ...): percent of the threshold
        public String scale = "auto";         // unit of the radio's channel numbers: auto | percent | permille | us
        public boolean announceInitial = false; // speak rules that are already true at start-up
    }

    public static final class Result {
        public final List<Rule> rules = new ArrayList<Rule>();
        public final List<String> errors = new ArrayList<String>();
        public final Settings settings = new Settings();
    }

    private static final Pattern SETTING = Pattern.compile("^(hysteresis|var_hysteresis|hold|initial|scale)\\s*=\\s*(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATOM = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s*(?:/\\s*(\\d+(?:\\.\\d+)?))?\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?|\\$[A-Za-z_]+)$");
    private static final Pattern TEXT_ATOM = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_]*)\\s*(==|!=|~)\\s*\"([^\"]*)\"$");
    private static final Pattern REPEAT = Pattern.compile("\\s@(\\d+)\\s*$");

    private RuleParser() {}

    private static final java.util.Map<String, Double> DEFAULT_MACROS = BatteryProfiles.macros(BatteryProfiles.defaults().get(0));

    /** For checking and for the rules editor: $low, $crit and $full stand for the first default profile's numbers. */
    public static Result parse(String text) {
        return parse(text, DEFAULT_MACROS);
    }

    /** @param macros the numbers $low, $crit and $full stand for (from the active battery profile) */
    public static Result parse(String text, java.util.Map<String, Double> macros) {
        Result res = new Result();
        if (text == null) return res;
        String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String raw = lines[i];
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;

            Matcher sm = SETTING.matcher(line);
            if (sm.matches()) {
                applySetting(res, lineNo, sm.group(1).toLowerCase(Locale.ROOT), sm.group(2));
                continue;
            }

            int arrow = line.indexOf("->");
            if (arrow < 0) { res.errors.add(I18n.t("line {0}: missing '->'  ({1})", lineNo, line)); continue; }
            String cond = line.substring(0, arrow).trim();
            String speech = line.substring(arrow + 2).trim();
            if (cond.isEmpty()) { res.errors.add(I18n.t("line {0}: nothing before '->'", lineNo)); continue; }

            int repeat = 0;
            Matcher rm = REPEAT.matcher(" " + speech);
            if (rm.find()) {
                try { repeat = Integer.parseInt(rm.group(1)); } catch (NumberFormatException e) { repeat = 0; }
                speech = (" " + speech).substring(0, rm.start()).trim();
            }

            String trueText = speech;
            String falseText = null;
            int bar = speech.indexOf('|');
            if (bar >= 0) {
                trueText = speech.substring(0, bar).trim();
                falseText = speech.substring(bar + 1).trim();
                if (falseText.isEmpty()) falseText = null;
            }
            boolean urgentTrue = false, urgentFalse = false;
            if (trueText.startsWith("!")) { urgentTrue = true; trueText = trueText.substring(1).trim(); }
            if (falseText != null && falseText.startsWith("!")) { urgentFalse = true; falseText = falseText.substring(1).trim(); }
            if (trueText.isEmpty()) { res.errors.add(I18n.t("line {0}: nothing to say after '->'", lineNo)); continue; }
            if (repeat > 0 && repeat < 2) repeat = 2;

            List<List<Rule.Atom>> groups = new ArrayList<List<Rule.Atom>>();
            boolean ok = true;
            for (String orPart : cond.split("(?i)\\s+or\\s+")) {
                List<Rule.Atom> atoms = new ArrayList<Rule.Atom>();
                for (String andPart : orPart.split("(?i)\\s+and\\s+")) {
                    String part = andPart.trim();
                    Matcher tm = TEXT_ATOM.matcher(part);
                    if (tm.matches()) {
                        atoms.add(new Rule.Atom(tm.group(1).toLowerCase(Locale.ROOT), tm.group(2), 0, 1.0, tm.group(3)));
                        continue;
                    }
                    Matcher am = ATOM.matcher(part);
                    if (!am.matches()) {
                        res.errors.add(I18n.t("line {0}: cannot read condition '{1}'  (use  ch5 >= 1500   or   vbat / 6 < 3.5   or   fm ~ \"RTH\" )", lineNo, part));
                        ok = false;
                        break;
                    }
                    double divisor = 1.0;
                    if (am.group(2) != null) {
                        divisor = Double.parseDouble(am.group(2));
                        if (divisor == 0.0) {
                            res.errors.add(I18n.t("line {0}: cannot divide by zero  ({1})", lineNo, part));
                            ok = false;
                            break;
                        }
                    }
                    String vtxt = am.group(4);
                    double value;
                    String ref = null;
                    if (vtxt.startsWith("$")) {
                        ref = vtxt.substring(1).toLowerCase(Locale.ROOT);
                        Double mv = macros.get(ref);
                        if (mv == null) {
                            res.errors.add(I18n.t("line {0}: unknown name ${1}  (use $low, $crit or $full)", lineNo, ref));
                            ok = false;
                            break;
                        }
                        value = mv;
                    } else {
                        value = Double.parseDouble(vtxt);
                    }
                    atoms.add(new Rule.Atom(am.group(1).toLowerCase(Locale.ROOT), am.group(3), value, divisor, null, ref));
                }
                if (!ok) break;
                groups.add(atoms);
            }
            if (!ok) continue;
            res.rules.add(new Rule(lineNo, groups, trueText, urgentTrue, falseText, urgentFalse, repeat, line));
        }
        return res;
    }

    private static void applySetting(Result res, int lineNo, String name, String value) {
        try {
            if (name.equals("hysteresis")) {
                double h = Double.parseDouble(value);
                if (h < 0) throw new NumberFormatException();
                res.settings.hysteresis = h;
            } else if (name.equals("var_hysteresis")) {
                double h = Double.parseDouble(value);
                if (h < 0) throw new NumberFormatException();
                res.settings.varHysteresisPct = h;
            } else if (name.equals("scale")) {
                String v = value.toLowerCase(Locale.ROOT);
                if (!(v.equals("auto") || v.equals("percent") || v.equals("permille") || v.equals("us"))) throw new NumberFormatException();
                res.settings.scale = v;
            } else if (name.equals("hold")) {
                long ms = Long.parseLong(value);
                if (ms < 0) throw new NumberFormatException();
                res.settings.holdMs = ms;
            } else if (name.equals("initial")) {
                String v = value.toLowerCase(Locale.ROOT);
                if (v.equals("on") || v.equals("true") || v.equals("yes")) res.settings.announceInitial = true;
                else if (v.equals("off") || v.equals("false") || v.equals("no")) res.settings.announceInitial = false;
                else throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            res.errors.add(I18n.t("line {0}: bad value for {1} ({2})", lineNo, name, value));
        }
    }
}
