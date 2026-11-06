package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The warnings that are showing right now. Fed by the rule engine's state changes (a warning is
 * "on" while its condition is true) and turned into the texts for the status bar.
 *
 * Severity comes from the rule text:   !text = critical,  *text = info,  text = warning.
 */
public final class WarningModel implements RuleEngine.StateListener {

    public enum Level { INFO, WARNING, CRITICAL }

    public static final class Item {
        public final String text;
        public final Level level;
        public final int order;

        Item(String text, Level level, int order) {
            this.text = text;
            this.level = level;
            this.order = order;
        }
    }

    private final List<Rule> rules;
    private final boolean[] active;

    public WarningModel(List<Rule> rules) {
        this.rules = rules;
        this.active = new boolean[rules.size()];
    }

    @Override
    public void changed(int index, Rule rule, boolean on) {
        if (index >= 0 && index < active.length) active[index] = on;
    }

    public static Level levelOf(Rule r) {
        if (r.urgentTrue) return Level.CRITICAL;
        if (r.onTrue.startsWith("*")) return Level.INFO;
        return Level.WARNING;
    }

    /** The words to show, without the "*" that marks an info line. */
    public static String textOf(Rule r) {
        String t = r.onTrue;
        if (t.startsWith("*")) t = t.substring(1);
        return t.trim();
    }

    /** The active warnings with live values filled in, worst first. */
    public List<Item> items(Map<String, Double> numbers, Map<String, String> texts) {
        List<Item> out = new ArrayList<Item>();
        for (int i = 0; i < rules.size(); i++) {
            if (!active[i]) continue;
            Rule r = rules.get(i);
            out.add(new Item(TextTemplate.expand(textOf(r), numbers, texts), levelOf(r), i));
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                if (a.level != b.level) return b.level.ordinal() - a.level.ordinal();
                return a.order - b.order;
            }
        });
        return out;
    }

    public static Level worst(List<Item> items) {
        Level w = Level.INFO;
        for (Item it : items) if (it.level.ordinal() > w.ordinal()) w = it.level;
        return w;
    }

    public int activeCount() {
        int n = 0;
        for (boolean b : active) if (b) n++;
        return n;
    }
}
