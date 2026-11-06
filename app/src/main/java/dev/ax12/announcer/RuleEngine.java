package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Evaluates the rules against channel values and speaks through an Announcer.
 * Call update() about 20 times per second. Not thread safe: call it from one thread.
 *
 * Behaviour:
 *  - A rule speaks when its condition BECOMES true (edge), not while it stays true,
 *    unless it has an @N repeat.
 *  - Hysteresis stops chatter around a threshold; hold requires a change to last a moment.
 *  - The first reading only sets the starting state (silent) unless "initial = on".
 *  - A rule whose channel has no value is skipped.
 */
public final class RuleEngine {

    private static final class State {
        final boolean[][] atomOn;
        boolean initialized;
        boolean committed;
        boolean candidate;
        long candidateSince;
        long lastSpoken;

        State(Rule r) {
            atomOn = new boolean[r.orGroups.size()][];
            for (int g = 0; g < atomOn.length; g++) atomOn[g] = new boolean[r.orGroups.get(g).size()];
        }
    }

    /** Told when a rule's condition becomes true or false (after hold), and when its inputs disappear. */
    public interface StateListener {
        void changed(int index, Rule rule, boolean active);
    }

    private final List<Rule> rules;
    private final RuleParser.Settings settings;
    private final Announcer out;
    private final StateListener listener;
    private final List<State> states = new ArrayList<State>();
    private Map<String, Double> curValues = Collections.<String, Double>emptyMap();
    private Map<String, String> curTexts = Collections.<String, String>emptyMap();

    public RuleEngine(RuleParser.Result parsed, Announcer out) {
        this(parsed, out, null);
    }

    /**
     * With a listener the engine works in "level" mode, for warnings: the listener hears the starting
     * state of every rule, every change, and a rule whose variable has gone missing counts as off.
     */
    public RuleEngine(RuleParser.Result parsed, Announcer out, StateListener listener) {
        this.rules = parsed.rules;
        this.settings = parsed.settings;
        this.out = out;
        this.listener = listener;
        for (Rule r : rules) states.add(new State(r));
    }

    private void say(String text, boolean urgent) {
        if (out == null) return;
        out.say(TextTemplate.expand(text, curValues, curTexts), urgent);
    }

    /** Forget all state (use after reloading channels or a link loss) so the next reading is silent again. */
    public void reset() {
        for (int i = 0; i < states.size(); i++) {
            State s = states.get(i);
            s.initialized = false;
        }
    }

    public int ruleCount() { return rules.size(); }

    public void update(Map<String, Double> values, long nowMs) {
        update(values, Collections.<String, String>emptyMap(), nowMs);
    }

    /** @param values numeric variables (ch5, vbat, lq ...)   @param texts text variables (fm ...) */
    public void update(Map<String, Double> values, Map<String, String> texts, long nowMs) {
        curValues = values;
        curTexts = texts;
        for (int i = 0; i < rules.size(); i++) {
            Rule r = rules.get(i);
            State s = states.get(i);
            Boolean cond = evaluate(r, s, values, texts);
            if (cond == null) {
                if (listener != null && s.initialized) {       // the input is gone: a warning must not stay on
                    s.initialized = false;
                    if (s.committed) {
                        s.committed = false;
                        listener.changed(i, r, false);
                    }
                }
                continue;
            }

            if (!s.initialized) {
                s.initialized = true;
                s.committed = cond;
                s.candidate = cond;
                s.candidateSince = nowMs;
                if (listener != null) listener.changed(i, r, cond);
                if (cond && settings.announceInitial) {
                    say(r.onTrue, r.urgentTrue);
                    s.lastSpoken = nowMs;
                }
                continue;
            }

            if (cond != s.candidate) {
                s.candidate = cond;
                s.candidateSince = nowMs;
            }

            if (s.candidate != s.committed && nowMs - s.candidateSince >= settings.holdMs) {
                s.committed = s.candidate;
                if (listener != null) listener.changed(i, r, s.committed);
                if (s.committed) {
                    say(r.onTrue, r.urgentTrue);
                    s.lastSpoken = nowMs;
                } else if (r.onFalse != null) {
                    say(r.onFalse, r.urgentFalse);
                }
            } else if (s.committed && r.repeatSec > 0 && nowMs - s.lastSpoken >= r.repeatSec * 1000L) {
                say(r.onTrue, r.urgentTrue);
                s.lastSpoken = nowMs;
            }
        }
    }

    /** @return null if a needed value is missing. */
    private Boolean evaluate(Rule r, State s, Map<String, Double> values, Map<String, String> texts) {
        boolean anyGroup = false;
        for (int g = 0; g < r.orGroups.size(); g++) {
            List<Rule.Atom> atoms = r.orGroups.get(g);
            boolean all = true;
            for (int k = 0; k < atoms.size(); k++) {
                Rule.Atom a = atoms.get(k);
                boolean on;
                if (a.isText()) {
                    String t = texts.get(a.name);
                    if (t == null) return null;
                    on = textState(a, t);
                } else {
                    Double v = values.get(a.name);
                    if (v == null) return null;
                    on = atomState(a, s.atomOn[g][k], v.doubleValue() / a.divisor);
                }
                s.atomOn[g][k] = on;
                if (!on) all = false;
            }
            if (all) anyGroup = true;
        }
        return anyGroup;
    }

    private static boolean isChannelLike(String name) {
        return name.matches("(ch|raw|lv)\\d+");
    }

    private boolean textState(Rule.Atom a, String t) {
        if (a.op.equals("==")) return t.equalsIgnoreCase(a.text);
        if (a.op.equals("!=")) return !t.equalsIgnoreCase(a.text);
        return t.toLowerCase(java.util.Locale.ROOT).contains(a.text.toLowerCase(java.util.Locale.ROOT));   // "~"
    }

    private boolean atomState(Rule.Atom a, boolean prev, double x) {
        // Channel values use the microsecond hysteresis; other variables (volts, link quality) use a percentage of the threshold.
        double h = isChannelLike(a.name) ? settings.hysteresis / a.divisor
                : Math.abs(a.value) * settings.varHysteresisPct / 100.0;
        String op = a.op;
        if (op.equals(">=")) return prev ? !(x < a.value - h) : x >= a.value;
        if (op.equals(">"))  return prev ? !(x <= a.value - h) : x > a.value;
        if (op.equals("<=")) return prev ? !(x > a.value + h) : x <= a.value;
        if (op.equals("<"))  return prev ? !(x >= a.value + h) : x < a.value;
        if (op.equals("==")) return Math.abs(x - a.value) <= h;
        return Math.abs(x - a.value) > h;   // "!="
    }
}
