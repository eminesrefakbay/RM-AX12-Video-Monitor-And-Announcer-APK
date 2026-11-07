// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Tests for the translation engine, the language files and the starting rules in four languages. */
public class I18nTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static final String DIR = "app/src/main/assets/";
    static final I18n.Source FILES = new I18n.Source() {
        public InputStream open(String path) throws IOException { return new FileInputStream(new File(DIR + path)); }
    };

    public static void main(String[] args) throws Exception {
        // ---------- the engine ----------
        I18n.load(FILES, "en");
        check(I18n.t("Settings").equals("Settings") && I18n.language().equals("en"), "English is the text itself");
        check(I18n.t("Imported {0} rules", 5).equals("Imported 5 rules"), "placeholders are filled in English");
        check(I18n.t("a {1} b {0}", "x", "y").equals("a y b x"), "placeholders can come in any order");
        check(I18n.t("{vbat/6:2} stays", 1).equals("{vbat/6:2} stays") && I18n.t("{0}{", "x").equals("x{"), "other braces are left alone");
        check(I18n.t("missing {3}", "a").equals("missing {3}"), "a placeholder without an argument is left as it is");
        check(I18n.t("no such text in any language").equals("no such text in any language"), "an unknown text falls back to itself");
        check(I18n.t("n={0}", (Object) null).equals("n=null") && I18n.t("n={0}", 1.5).equals("n=1.5"), "arguments are turned into text");
        check(I18n.n("keep me").equals("keep me"), "the marker returns the text");

        I18n.load(FILES, "tr");
        check(I18n.language().equals("tr") && I18n.t("Settings").equals("Ayarlar"), "Turkish: " + I18n.t("Settings"));
        check(I18n.t("Imported {0} rules", 5).equals("5 kural içe aktarıldı"), "Turkish with a placeholder: " + I18n.t("Imported {0} rules", 5));
        check(I18n.t("untranslated text").equals("untranslated text"), "Turkish falls back to English for an unknown text");
        I18n.load(FILES, "de");
        check(I18n.t("Settings").equals("Einstellungen") && I18n.t("Save").equals("Speichern"), "German");
        I18n.load(FILES, "zh");
        check(I18n.t("Settings").equals("设置") && I18n.t("Cancel").equals("取消"), "Chinese");
        check(I18n.t("Installed: version {0} (build {1})   Channel: {2}", "0.8.0", 7, "Stable").equals("已安装: 版本 0.8.0 (构建 7)   渠道: Stable"), "Chinese with three placeholders");
        I18n.load(FILES, "xx");
        check(I18n.language().equals("en") && I18n.t("Settings").equals("Settings"), "an unknown language means English");
        I18n.load(FILES, null);
        check(I18n.language().equals("en"), "null means English");
        I18n.load(null, "tr");
        check(I18n.t("Settings").equals("Settings"), "no source of files means English");

        // a text in another language than the screen's (the voice)
        I18n.load(FILES, "de");
        check(I18n.tIn("tr", "Sound test").equals("Ses testi") && I18n.tIn("de", "Sound test").equals("Klangtest")
              && I18n.tIn("en", "Sound test").equals("Sound test") && I18n.tIn("zh", "Sound test").equals("语音测试"), "tIn gives a text in any language");
        check(I18n.tIn("xx", "Sound test").equals("Sound test") && I18n.tIn(null, "Sound test").equals("Klangtest"), "tIn: unknown language is English, null is the screen's");
        I18n.load(FILES, "en");

        // choosing a language
        check(I18n.resolve("auto", "de").equals("de") && I18n.resolve("auto", "tr").equals("tr") && I18n.resolve("auto", "zh").equals("zh"), "auto follows Android");
        check(I18n.resolve("auto", "fr").equals("en") && I18n.resolve("auto", null).equals("en") && I18n.resolve(null, "de").equals("de"), "auto: other languages become English");
        check(I18n.resolve("tr", "de").equals("tr") && I18n.resolve("en", "de").equals("en") && I18n.resolve("xx", "de").equals("de"), "a choice beats Android");

        // escapes
        check(I18n.escape("a\nb\tc\\d").equals("a\\nb\\tc\\\\d") && I18n.unescape(I18n.escape("a\nb\tc\\d")).equals("a\nb\tc\\d"), "escape and unescape agree");
        Map<String, String> m = I18n.parseTable(new java.io.ByteArrayInputStream("# comment\n\nKey one\tWert eins\nLine\\nbreak\tZeile\\numbruch\nnotab\n".getBytes("UTF-8")));
        check(m.size() == 2 && m.get("Key one").equals("Wert eins") && m.get("Line\nbreak").equals("Zeile\numbruch"), "the table reader: " + m);
        check(I18n.parseTable(new java.io.ByteArrayInputStream("\uFEFFA\tB\n".getBytes("UTF-8"))).get("A").equals("B"), "a byte-order mark at the start is ignored");

        // ---------- the language files ----------
        List<String> source = new ArrayList<String>();
        for (String line : new String(java.nio.file.Files.readAllBytes(new File(DIR + "i18n/source.txt").toPath()), "UTF-8").split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            source.add(I18n.unescape(line));
        }
        check(source.size() > 300, "the source list has " + source.size() + " texts");
        for (String lang : new String[]{"tr", "de", "zh"}) {
            Map<String, String> t;
            try (InputStream in = new FileInputStream(DIR + "i18n/" + lang + ".tsv")) { t = I18n.parseTable(in); }
            check(t.size() == source.size(), lang + ": same number of texts as the source (" + t.size() + " / " + source.size() + ")");
            int missing = 0, badPh = 0, badFmt = 0, empty = 0, same = 0;
            for (String en : source) {
                String tx = t.get(en);
                if (tx == null) { missing++; continue; }
                if (tx.trim().isEmpty()) empty++;
                if (!placeholders(en).equals(placeholders(tx))) { badPh++; System.out.println("   placeholders [" + lang + "] " + en); }
                if (!formats(en).equals(formats(tx))) { badFmt++; System.out.println("   format [" + lang + "] " + en); }
                if (tx.equals(en)) same++;
            }
            check(missing == 0 && empty == 0, lang + ": no missing or empty translation (" + missing + "/" + empty + ")");
            check(badPh == 0, lang + ": every translation keeps its {0} {1} placeholders");
            check(badFmt == 0, lang + ": every translation keeps its % format specifiers");
            check(same < source.size() / 5, lang + ": most texts differ from the English (" + same + " identical)");
            // the one format string really formats in this language
            I18n.load(FILES, lang);
            String f = I18n.t("video: %.1f frames/s   gap between frames: 95%% under %d ms, worst %d ms (last 5 s)");
            String done = String.format(java.util.Locale.US, f, 59.9, 18, 42);
            check(done.contains("59.9") && done.contains("18") && done.contains("42") && done.contains("95"), lang + ": the video statistics line formats: " + done);
        }
        I18n.load(FILES, "en");
        check(containsCjk(new String(java.nio.file.Files.readAllBytes(new File(DIR + "i18n/zh.tsv").toPath()), "UTF-8")), "the Chinese file holds Chinese");
        check(new String(java.nio.file.Files.readAllBytes(new File(DIR + "i18n/tr.tsv").toPath()), "UTF-8").contains("ş"), "the Turkish file holds Turkish letters");

        // ---------- the starting rules, warnings and checks in four languages ----------
        for (String kind : new String[]{"rules", "warnings", "checks"}) {
            I18n.load(FILES, "en");
            String en = I18n.text(kind, "en");
            RuleParser.Result enR = RuleParser.parse(en);
            check(!en.isEmpty() && enR.errors.isEmpty(), kind + " (en) parses: " + enR.errors);
            for (String lang : new String[]{"tr", "de", "zh"}) {
                String text = I18n.text(kind, lang);
                RuleParser.Result r = RuleParser.parse(text);
                check(!text.isEmpty() && !text.equals(en), kind + " (" + lang + ") is its own text");
                check(r.errors.isEmpty(), kind + " (" + lang + ") parses without errors: " + r.errors);
                check(r.rules.size() == enR.rules.size(), kind + " (" + lang + ") has the same number of rules: " + r.rules.size() + " / " + enR.rules.size());
                // the translation must not change the logic: same conditions, urgency, repeat times and settings
                List<RuleText.Item> a = RuleText.parse(en), b = RuleText.parse(text);
                List<RuleText.Spec> sa = specs(a), sb = specs(b);
                boolean same = sa.size() == sb.size();
                for (int i = 0; same && i < sa.size(); i++) same = sameLogic(sa.get(i), sb.get(i));
                check(same, kind + " (" + lang + ") has the same conditions, urgency and timing as English");
                check(r.settings.scale.equals(enR.settings.scale) && r.settings.announceInitial == enR.settings.announceInitial, kind + " (" + lang + ") keeps the settings");
                int editable = 0;
                for (RuleText.Item it : b) if (it.kind == RuleText.Kind.RULE && it.spec != null && RuleText.validate(it.spec) == null) editable++;
                check(editable == enR.rules.size(), kind + " (" + lang + ") is fully editable in the rules editor: " + editable);
            }
        }
        I18n.load(FILES, "en");
        check(I18n.text("nothing", "tr").equals(""), "an unknown text file is empty");
        // the descriptions the lists show, in each language
        I18n.load(FILES, "de");
        RuleText.Spec spec = RuleText.fromLine("vbat / 6 < 3.55 and ch5 >= 1950 -> !Akku kritisch @10");
        check(RuleText.describe(spec).equals("Wenn vbat \u00F7 6 < 3.55 und ch5 \u2265 1950: sage \u201EAkku kritisch\u201C, alle 10 s, solange es wahr ist (dringend)".replace("\u201E", "\u201C").replace("sage \u201CAkku", "sage „Akku").replace("kritisch\u201C", "kritisch“")), "German description: " + RuleText.describe(spec));
        I18n.load(FILES, "tr");
        check(RuleText.describe(spec).startsWith("vbat \u00F7 6 < 3.55 ve ch5 \u2265 1950 olduğunda: "), "Turkish description: " + RuleText.describe(spec));
        check(RuleText.line(spec).equals("vbat / 6 < 3.55 and ch5 >= 1950 -> !Akku kritisch @10"), "the rule text itself is never translated: " + RuleText.line(spec));
        I18n.load(FILES, "zh");
        check(RuleText.describe(spec).startsWith("当 vbat \u00F7 6 < 3.55 且 ch5 \u2265 1950 时: 播报"), "Chinese description: " + RuleText.describe(spec));
        check(RuleText.line(spec).contains(" and ") && !RuleText.line(spec).contains("且"), "the rule text keeps its English keywords in Chinese");
        check(RulesFile.inspect("ch5 >= 1 -> a\nbad line\n").message.contains("行"), "rule file messages follow the language: " + RulesFile.inspect("ch5 >= 1 -> a\nbad line\n").message);
        I18n.load(FILES, "en");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    static String placeholders(String s) {
        List<String> l = new ArrayList<String>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{\\d\\}").matcher(s);
        while (m.find()) l.add(m.group());
        java.util.Collections.sort(l);
        return l.toString();
    }

    static String formats(String s) {
        List<String> l = new ArrayList<String>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("%[-0-9.]*[dfs]|%%").matcher(s);
        while (m.find()) l.add(m.group());
        java.util.Collections.sort(l);
        return l.toString();
    }

    static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) >= 0x4E00 && s.charAt(i) <= 0x9FFF) return true;
        return false;
    }

    static List<RuleText.Spec> specs(List<RuleText.Item> items) {
        List<RuleText.Spec> out = new ArrayList<RuleText.Spec>();
        for (RuleText.Item it : items) if (it.kind == RuleText.Kind.RULE && it.spec != null) out.add(it.spec);
        return out;
    }

    static boolean sameLogic(RuleText.Spec a, RuleText.Spec b) {
        if (a.any != b.any || a.conds.size() != b.conds.size() || a.urgentTrue != b.urgentTrue || a.repeatSec != b.repeatSec) return false;
        if (a.onTrue.startsWith("*") != b.onTrue.startsWith("*") || (a.onFalse.isEmpty() != b.onFalse.isEmpty())) return false;
        for (int i = 0; i < a.conds.size(); i++) {
            RuleText.Cond x = a.conds.get(i), y = b.conds.get(i);
            if (!x.variable.equals(y.variable) || !x.op.equals(y.op) || !x.value.equals(y.value) || x.divisor != y.divisor || x.text != y.text) return false;
        }
        return true;
    }
}
