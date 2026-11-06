package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;

/**
 * Export and import of the sound rules as a plain text file. Plain Java, tested on a PC.
 * The file is exactly the rules text, with one comment line on top, so it can be edited in any
 * editor and shared.
 */
public final class RulesFile {

    public static final int MAX_CHARS = 200000;
    public static final String HEADER = "# AX12 Announcer sound rules";

    public static final class Summary {
        public final int rules;
        public final int disabled;
        public final List<String> errors;
        public final boolean usable;
        public final String message;

        Summary(int rules, int disabled, List<String> errors, boolean usable, String message) {
            this.rules = rules;
            this.disabled = disabled;
            this.errors = errors;
            this.usable = usable;
            this.message = message;
        }
    }

    private RulesFile() {}

    /** Removes a byte-order mark and turns Windows line endings into plain ones. */
    public static String normalize(String text) {
        if (text == null) return "";
        String t = text;
        if (t.startsWith("\uFEFF")) t = t.substring(1);
        return t.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** What the file contains, and whether it can be imported. */
    public static Summary inspect(String text) {
        List<String> none = new ArrayList<String>();
        if (text == null || text.trim().isEmpty()) return new Summary(0, 0, none, false, I18n.t("The file is empty."));
        if (text.length() > MAX_CHARS) return new Summary(0, 0, none, false, I18n.t("The file is too large to be a rules file."));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 0 || (c < 32 && c != '\n' && c != '\r' && c != '\t')) {
                return new Summary(0, 0, none, false, I18n.t("This does not look like a text file."));
            }
        }
        String clean = normalize(text);
        RuleParser.Result r = RuleParser.parse(clean);
        int disabled = 0;
        for (RuleText.Item it : RuleText.parse(clean)) if (it.kind == RuleText.Kind.DISABLED_RULE) disabled++;
        int rules = r.rules.size();
        if (rules == 0 && disabled == 0) {
            String why = r.errors.isEmpty() ? I18n.t("No rules found in this file.") : I18n.t("No usable rules found. First problem: {0}", r.errors.get(0));
            return new Summary(0, 0, r.errors, false, why);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(rules == 1 ? I18n.t("1 rule") : I18n.t("{0} rules", rules));
        if (disabled > 0) sb.append(I18n.t(" ({0} switched off)", disabled));
        if (!r.errors.isEmpty()) {
            sb.append(r.errors.size() == 1 ? I18n.t(". 1 line has a problem and will not work. First: {0}", r.errors.get(0))
                                           : I18n.t(". {0} lines have a problem and will not work. First: {1}", r.errors.size(), r.errors.get(0)));
        } else {
            sb.append(I18n.t(", no problems found."));
        }
        return new Summary(rules, disabled, r.errors, true, sb.toString());
    }

    /** The text to write to a file: the rules with one header line on top. */
    public static String forExport(String rules, String stamp) {
        String body = normalize(rules);
        if (body.startsWith(HEADER)) {
            int nl = body.indexOf('\n');
            body = nl < 0 ? "" : body.substring(nl + 1);
        }
        if (!body.isEmpty() && !body.endsWith("\n")) body += "\n";
        return HEADER + " (exported " + stamp + ")\n" + body;
    }
}
