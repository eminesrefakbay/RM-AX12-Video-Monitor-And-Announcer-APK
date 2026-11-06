package dev.ax12.announcer;

/** Tests for the rules export and import helpers. */
public class RulesFileTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) {
        String defaults = RuleTextTests.readDefaultRules();
        check(!defaults.isEmpty(), "default rules found");

        // a normal export can be imported again
        String exported = RulesFile.forExport(defaults, "2026-11-06 09:30");
        check(exported.startsWith("# AX12 Announcer sound rules (exported 2026-11-06 09:30)\n"), "header added");
        RulesFile.Summary s = RulesFile.inspect(exported);
        RuleParser.Result orig = RuleParser.parse(defaults);
        check(s.usable && s.rules == orig.rules.size() && s.errors.isEmpty(), "export is importable with the same rule count: " + s.message);
        check(RuleParser.parse(exported).rules.size() == orig.rules.size(), "the engine reads the exported file the same");
        check(RuleParser.parse(exported).settings.scale.equals(orig.settings.scale), "settings survive the export");

        // exporting an imported file does not pile up headers
        String again = RulesFile.forExport(exported, "2026-11-07 10:00");
        int headers = again.split("# AX12 Announcer sound rules", -1).length - 1;
        check(headers == 1 && again.startsWith("# AX12 Announcer sound rules (exported 2026-11-07 10:00)\n"), "only one header after a second export");
        check(RulesFile.forExport("ch5 >= 1 -> a", "x").endsWith("ch5 >= 1 -> a\n"), "a missing final newline is added");
        check(RulesFile.forExport("", "x").equals("# AX12 Announcer sound rules (exported x)\n"), "empty rules export");

        // files edited on Windows: BOM and CRLF
        String win = "\uFEFF" + exported.replace("\n", "\r\n");
        RulesFile.Summary w = RulesFile.inspect(win);
        check(w.usable && w.rules == orig.rules.size(), "BOM and CRLF are handled: " + w.message);
        check(RulesFile.normalize(win).equals(exported), "normalize gives back the plain text");

        // problems are reported, not hidden
        RulesFile.Summary p = RulesFile.inspect("ch5 >= 1500 -> armed\nthis line is wrong\n#off ch6 > 1 -> x\n");
        check(p.usable && p.rules == 1 && p.disabled == 1 && p.errors.size() == 1, "counts rules, switched-off rules and problems: " + p.message);
        check(p.message.contains("1 rule") && p.message.contains("1 switched off") && p.message.contains("line 2"), "message names the first problem: " + p.message);

        // files that must be refused
        check(!RulesFile.inspect("").usable && !RulesFile.inspect(null).usable && !RulesFile.inspect("   \n").usable, "empty file refused");
        check(!RulesFile.inspect("hello\nworld\n").usable, "text without rules refused: " + RulesFile.inspect("hello\nworld\n").message);
        check(!RulesFile.inspect("PK\u0003\u0004\u0000\u0000binary").usable && RulesFile.inspect("PK\u0003\u0004\u0000\u0000binary").message.contains("text file"), "binary refused");
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < RulesFile.MAX_CHARS / 10 + 10; i++) big.append("ch5 >= 1 -> a\n");
        check(!RulesFile.inspect(big.toString()).usable && RulesFile.inspect(big.toString()).message.contains("too large"), "huge file refused");
        check(!RulesFile.inspect("hysteresis = 25\ninitial = off\n").usable, "settings only is not a rules file");
        check(RulesFile.inspect("ch5 >= 1 -> a").rules == 1, "a single rule is a valid file");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
