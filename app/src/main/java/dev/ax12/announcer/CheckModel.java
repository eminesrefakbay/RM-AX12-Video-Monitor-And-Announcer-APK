package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The pre-flight checklist: every rule is one check. The condition true = pass, false = fail, and a
 * condition whose variable has no value is "unknown" (no tick, no cross). Fed by the rule engine.
 */
public final class CheckModel implements RuleEngine.StateListener {

    public enum State { UNKNOWN, PASS, FAIL }

    public static final class Item {
        public final String label;
        public final State state;

        Item(String label, State state) {
            this.label = label;
            this.state = state;
        }
    }

    private final List<Rule> rules;
    private final State[] states;

    public CheckModel(List<Rule> rules) {
        this.rules = rules;
        this.states = new State[rules.size()];
        Arrays.fill(states, State.UNKNOWN);
    }

    @Override
    public void changed(int index, Rule rule, boolean active) {
        if (index >= 0 && index < states.length) states[index] = active ? State.PASS : State.FAIL;
    }

    @Override
    public void unknown(int index, Rule rule) {
        if (index >= 0 && index < states.length) states[index] = State.UNKNOWN;
    }

    public List<Item> items(Map<String, Double> numbers, Map<String, String> texts) {
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < rules.size(); i++) {
            out.add(new Item(TextTemplate.expand(rules.get(i).onTrue, numbers, texts), states[i]));
        }
        return out;
    }

    private int count(State s) {
        int n = 0;
        for (State x : states) if (x == s) n++;
        return n;
    }

    public int pass() { return count(State.PASS); }
    public int fail() { return count(State.FAIL); }
    public int unknown() { return count(State.UNKNOWN); }
    public int size() { return states.length; }

    /** Every check passes. */
    public boolean ready() { return states.length > 0 && pass() == states.length; }

    /** Nothing can be judged (the radio or the aircraft is not connected). */
    public boolean allUnknown() { return states.length > 0 && unknown() == states.length; }
}
