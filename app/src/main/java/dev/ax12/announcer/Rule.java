package dev.ax12.announcer;

import java.util.List;

/** One parsed rule:  (a AND b) OR (c AND d)  ->  text. */
public final class Rule {

    public static final class Atom {
        public final String name;   // e.g. "ch5"
        public final String op;     // >=  <=  >  <  ==  !=
        public final double value;

        public Atom(String name, String op, double value) {
            this.name = name;
            this.op = op;
            this.value = value;
        }

        @Override
        public String toString() {
            String v = (value == Math.rint(value)) ? Long.toString((long) value) : Double.toString(value);
            return name + " " + op + " " + v;
        }
    }

    public final int line;                    // 1-based line number in the rules text
    public final List<List<Atom>> orGroups;   // outer list = OR, inner list = AND
    public final String onTrue;               // spoken when the condition becomes true
    public final boolean urgentTrue;
    public final String onFalse;              // spoken when it becomes false, or null
    public final boolean urgentFalse;
    public final int repeatSec;               // 0 = speak once; N = repeat every N s while true
    public final String source;               // the original line, for error messages

    public Rule(int line, List<List<Atom>> orGroups, String onTrue, boolean urgentTrue,
                String onFalse, boolean urgentFalse, int repeatSec, String source) {
        this.line = line;
        this.orGroups = orGroups;
        this.onTrue = onTrue;
        this.urgentTrue = urgentTrue;
        this.onFalse = onFalse;
        this.urgentFalse = urgentFalse;
        this.repeatSec = repeatSec;
        this.source = source;
    }
}
