package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Guards for the look of the app: plain rectangles, a see-through menu and panels, the Exit button, and the capitals in every text. */
public class LookTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String p) throws Exception { return new String(Files.readAllBytes(new File(p).toPath()), StandardCharsets.UTF_8); }
    static String unesc(String s) { return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\"); }

    static List<String> sources(String dir) {
        List<String> out = new ArrayList<String>();
        for (String l : new File(dir + "source.txt").exists() ? lines(dir + "source.txt") : new ArrayList<String>()) if (!l.isEmpty() && !l.startsWith("#")) out.add(unesc(l));
        return out;
    }
    static List<String> lines(String path) {
        try { return Arrays.asList(read(path).split("\\r?\\n")); } catch (Exception e) { return new ArrayList<String>(); }
    }
    static Map<String, String> table(String path) {
        Map<String, String> m = new HashMap<String, String>();
        for (String l : lines(path)) {
            if (l.isEmpty() || l.startsWith("#") || !l.contains("\t")) continue;
            String[] p = l.split("\t", 2);
            m.put(unesc(p[0]), unesc(p[1]));
        }
        return m;
    }
    static int alpha(long color) { return (int) ((color >> 24) & 0xFF); }

    public static void main(String[] args) throws Exception {
        String kt = "app/src/main/java/dev/ax12/announcer/";
        String res = "app/src/main/res/";

        // ---------- plain rectangles ----------
        String card = read(kt + "CardStyle.kt");
        check(card.contains("const val RADIUS_DP = 0f"), "the card style has no rounded corners");
        for (String f : new File(res + "drawable").list()) {
            if (f.endsWith(".xml")) check(!read(res + "drawable/" + f).contains("<corners"), "no rounded corners in the drawable " + f);
        }
        check(!read(res + "values/styles.xml").contains("radius"), "no radius in the theme");
        Matcher rr = Pattern.compile("^rect [\\d.]+ [\\d.]+ [\\d.]+ [\\d.]+ ([\\d.]+) ", Pattern.MULTILINE).matcher(read("app/src/main/assets/icons.txt"));
        int rects = 0, round = 0;
        while (rr.find()) { rects++; if (Double.parseDouble(rr.group(1)) != 0) round++; }
        check(rects >= 4 && round == 0, "the icons use square rectangles (" + rects + " checked)");
        int roundCalls = 0, literalRadii = 0;
        for (String f : new File(kt).list()) {
            if (!f.endsWith(".kt")) continue;
            String src = read(kt + f);
            Matcher m = Pattern.compile("drawRoundRect\\(([^;\\n]*)").matcher(src);
            while (m.find()) { if (!f.equals("Icons.kt")) roundCalls++; }
            Matcher c = Pattern.compile("cornerRadius\\s*=\\s*([^\\n]*)").matcher(src);
            while (c.find()) if (!f.equals("CardStyle.kt") || !c.group(1).contains("radius(density)")) literalRadii++;
        }
        check(roundCalls == 0, "no card or panel is drawn with rounded corners (" + roundCalls + " rounded calls)");
        check(literalRadii == 0, "no corner radius is set except through the card style");
        check(read(kt + "Icons.kt").contains("drawRoundRect(rect, n[4]"), "icons take their corner from the icon file (all zero)");
        check(read(kt + "SettingsPanel.kt").contains("squareSwitch(sw)") && read(kt + "LayoutEditor.kt").contains("The square handle") == false, "switches are square");

        // ---------- see-through menu and panels ----------
        Matcher cm = Pattern.compile("val (\\w+): Int = 0x([0-9A-Fa-f]{8})\\.toInt\\(\\)").matcher(card);
        Map<String, Long> colors = new HashMap<String, Long>();
        while (cm.find()) colors.put(cm.group(1), Long.parseLong(cm.group(2), 16));
        check(alpha(colors.get("SEE_THROUGH")) <= 0xA0 && alpha(colors.get("SEE_THROUGH")) >= 0x40, "the backdrop of Settings, About and the rules lets the video through (alpha 0x" + Long.toHexString(colors.get("SEE_THROUGH") >> 24) + ")");
        check(alpha(colors.get("BLOCK")) >= 0xB0, "text blocks on that backdrop stay dark enough to read");
        check(alpha(colors.get("MENU")) <= 0xD0 && alpha(colors.get("MENU")) >= 0x80, "the MENU box lets the video through");
        for (String f : new String[]{"SettingsPanel.kt", "AboutPanel.kt", "RulesPanel.kt"}) {
            String s = read(kt + f);
            check(s.contains("setBackgroundColor(CardStyle.SEE_THROUGH)") && !s.contains("setBackgroundColor(CardStyle.PANEL)"), f + ": the panel is see-through");
        }
        check(read(kt + "MainMenu.kt").contains("CardStyle.MENU"), "the menu box uses the see-through colour");
        check(read(kt + "SettingsPanel.kt").contains("CardStyle.BLOCK") && read(kt + "AboutPanel.kt").contains("content.setBackgroundColor(CardStyle.BLOCK)"), "rows and text sit on dark blocks");

        // ---------- the Exit button ----------
        String menu = read(kt + "MainMenu.kt"), act = read(kt + "MainActivity.kt");
        check(menu.contains("private val onExit: () -> Unit") && menu.contains("Tile(tr(\"Exit\"), true)") && menu.contains("\"exit\""), "the menu has an Exit item with its icon");
        check(act.contains("{ exitApp() }") && act.contains("finishAndRemoveTask()") && act.contains("Exit AX-HUD?"), "Exit asks first and then closes the app");
        check(Arrays.asList(read("app/src/main/assets/icons.txt").split("\\n")).contains("icon exit"), "the exit icon exists");

        // ---------- dialog buttons are not written in capitals ----------
        check(read(kt + "Immersive.kt").contains("isAllCaps = false"), "dialog buttons are not forced into ALL CAPS");

        // ---------- capitals in every text ----------
        String i18n = "app/src/main/assets/i18n/";
        List<String> src = sources(i18n);
        check(src.size() > 400, "texts read: " + src.size());
        Set<String> statusLines = new HashSet<String>(Arrays.asList(      // HUD status lines and examples that are written in capitals on purpose
            "NOT READY: 1 check failed", "NOT READY: {0} checks failed", "BATTERY CRITICAL 3.48 V per cell", "Text, for example BATTERY {vbat/6:2} V"));
        int lower = 0, mixedCaps = 0, british = 0;
        Pattern caps = Pattern.compile("\\b[A-Z]{3,}(?:\\s+[A-Z]{2,})+\\b"), low = Pattern.compile("\\b[a-z]{4,}\\b");
        Pattern uk = Pattern.compile("\\b(metres?|grey|colour\\w*|centre|behaviour|favourite|licence|cancelled|catalogue|organis\\w+|optimis\\w+)\\b", Pattern.CASE_INSENSITIVE);
        for (String s : src) {
            String t = s.replaceAll("\\{[^}]*\\}", "");
            String trimmed = s.replaceFirst("^\\s+", "");
            if (!trimmed.isEmpty() && Character.isLetter(trimmed.charAt(0)) && Character.isLowerCase(trimmed.charAt(0))) { lower++; System.out.println("  starts lowercase: " + s.replace("\n", "\\n")); }
            if (caps.matcher(t).find() && low.matcher(t.replaceAll("[A-Z]{2,}", "")).find() && !statusLines.contains(s)) { mixedCaps++; System.out.println("  mixed capitals: " + s.replace("\n", "\\n")); }
            if (uk.matcher(s).find()) { british++; System.out.println("  British spelling: " + s.replace("\n", "\\n")); }
        }
        check(lower == 0, "every text starts with a capital letter (" + lower + " do not)");
        check(mixedCaps == 0, "no text is half in capitals and half not (" + mixedCaps + " are)");
        check(british == 0, "one spelling (American) in every text (" + british + " differ)");

        // the translations follow: where the English starts with a capital, so do German and Turkish
        for (String lang : new String[]{"de", "tr"}) {
            Map<String, String> t = table(i18n + lang + ".tsv");
            int bad = 0;
            for (String en : src) {
                String tr = t.get(en);
                if (tr == null) continue;
                String e = en.replaceFirst("^[^\\p{L}{]*", ""), v = tr.replaceFirst("^[^\\p{L}{]*", "");
                if (!e.isEmpty() && !v.isEmpty() && e.charAt(0) != '{' && v.charAt(0) != '{' && Character.isUpperCase(e.charAt(0)) && Character.isLowerCase(v.charAt(0))) { bad++; System.out.println("  " + lang + " starts lowercase: " + tr.replace("\n", "\\n")); }
            }
            check(bad == 0, lang + ": a translation starts with a capital wherever the English does (" + bad + " do not)");
        }
        // the first word of the product's tagline and name are the same everywhere
        check(!src.contains("AX12 video monitor, HUD and announcer") && !src.contains("AX12 Video Monitor and Announcer, version {0}\n{1}"), "the old product wording is gone");
        check(src.contains("Video monitor, HUD and voice announcer for the RadioMaster AX12."), "one tagline for the splash and the About page");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
