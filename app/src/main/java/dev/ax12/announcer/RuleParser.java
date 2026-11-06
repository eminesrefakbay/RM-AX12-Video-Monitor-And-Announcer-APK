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
 *
 * Settings lines:  hysteresis = 25   hold = 100   initial = on|off
 * Lines starting with # or // are comments.
 */
public final class RuleParser {

    public static final class Settings {
        public double hysteresis = 25;        // same units as the channel values (microseconds)
        public long holdMs = 100;             // a condition must stay changed this long before it fires
        public boolean announceInitial = false; // speak rules that are already true at start-up
    }

    public static final class Result {
        public final List<Rule> rules = new ArrayList<Rule>();
        public final List<String> errors = new ArrayList<String>();
        public final Settings settings = new Settings();
    }

    private static final Pattern SETTING = Pattern.compile("^(hysteresis|hold|initial)\\s*=\\s*(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATOM = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern REPEAT = Pattern.compile("\\s@(\\d+)\\s*$");

    private RuleParser() {}

    public static Result parse(String text) {
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
            if (arrow < 0) { res.errors.add("line " + lineNo + ": missing '->'  (" + line + ")"); continue; }
            String cond = line.substring(0, arrow).trim();
            String speech = line.substring(arrow + 2).trim();
            if (cond.isEmpty()) { res.errors.add("line " + lineNo + ": nothing before '->'"); continue; }

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
            if (trueText.isEmpty()) { res.errors.add("line " + lineNo + ": nothing to say after '->'"); continue; }
            if (repeat > 0 && repeat < 2) repeat = 2;

            List<List<Rule.Atom>> groups = new ArrayList<List<Rule.Atom>>();
            boolean ok = true;
            for (String orPart : cond.split("(?i)\\s+or\\s+")) {
                List<Rule.Atom> atoms = new ArrayList<Rule.Atom>();
                for (String andPart : orPart.split("(?i)\\s+and\\s+")) {
                    Matcher am = ATOM.matcher(andPart.trim());
                    if (!am.matches()) {
                        res.errors.add("line " + lineNo + ": cannot read condition '" + andPart.trim()
                                + "'  (use  ch5 >= 1500 )");
                        ok = false;
                        break;
                    }
                    atoms.add(new Rule.Atom(am.group(1).toLowerCase(Locale.ROOT), am.group(2), Double.parseDouble(am.group(3))));
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
            res.errors.add("line " + lineNo + ": bad value for " + name + " (" + value + ")");
        }
    }
}
