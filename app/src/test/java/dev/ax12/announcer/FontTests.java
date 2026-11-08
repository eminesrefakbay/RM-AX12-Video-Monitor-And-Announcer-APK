// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** Tests of the fonts: the files and their letters, their licences, the slots of the digits and the wiring. */
public class FontTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static final String ASSETS = "app/src/main/assets/";
    static final String KT = "app/src/main/java/dev/ax12/announcer/";
    static String read(String path) throws Exception { return new String(Files.readAllBytes(new File(path).toPath()), "UTF-8"); }

    /** Whether a TrueType file has a glyph for the character (reads the cmap, format 4). */
    static boolean hasGlyph(ByteBuffer b, int cmapOffset, int cp) {
        int n = b.getShort(cmapOffset + 2) & 0xFFFF;
        for (int i = 0; i < n; i++) {
            int rec = cmapOffset + 4 + i * 8;
            int platform = b.getShort(rec) & 0xFFFF, off = b.getInt(rec + 4);
            int st = cmapOffset + off;
            if ((b.getShort(st) & 0xFFFF) != 4 || !(platform == 3 || platform == 0)) continue;
            int segX2 = b.getShort(st + 6) & 0xFFFF, seg = segX2 / 2;
            int endBase = st + 14, startBase = endBase + segX2 + 2, deltaBase = startBase + segX2, rangeBase = deltaBase + segX2;
            for (int s = 0; s < seg; s++) {
                int end = b.getShort(endBase + 2 * s) & 0xFFFF, start = b.getShort(startBase + 2 * s) & 0xFFFF;
                if (cp < start || cp > end) continue;
                int delta = b.getShort(deltaBase + 2 * s), ro = b.getShort(rangeBase + 2 * s) & 0xFFFF;
                if (ro == 0) return ((cp + delta) & 0xFFFF) != 0;
                int g = b.getShort(rangeBase + 2 * s + ro + 2 * (cp - start)) & 0xFFFF;
                return g != 0 && ((g + delta) & 0xFFFF) != 0;
            }
        }
        return false;
    }

    static int cmapOffset(ByteBuffer b) {
        int n = b.getShort(4) & 0xFFFF;
        for (int i = 0; i < n; i++) {
            int rec = 12 + i * 16;
            byte[] tag = new byte[4];
            for (int k = 0; k < 4; k++) tag[k] = b.get(rec + k);
            if (new String(tag).equals("cmap")) return b.getInt(rec + 8);
        }
        return -1;
    }

    static boolean covers(ByteBuffer b, int cmap, String chars) {
        for (int i = 0; i < chars.length(); i++) if (!hasGlyph(b, cmap, chars.charAt(i))) return false;
        return true;
    }

    public static void main(String[] args) throws Exception {
        // ---------- the digit slots ----------
        final Map<Character, Float> adv = new HashMap<Character, Float>();
        for (char c = '0'; c <= '9'; c++) adv.put(c, 10f);
        adv.put('1', 5f); adv.put(':', 4f); adv.put('.', 3f); adv.put('-', 6f); adv.put(' ', 3f);
        NumberLayout.Advance a = new NumberLayout.Advance() { @Override public float of(char c) { Float w = adv.get(c); return w == null ? 7f : w; } };
        float slot = NumberLayout.slot(a);
        check(slot == 10f, "the slot is the widest digit");
        check(NumberLayout.width("111", slot, a) == 30f && NumberLayout.width("888", slot, a) == 30f && NumberLayout.width("181", slot, a) == 30f, "111, 888 and 181 are equally wide");
        check(NumberLayout.width("1:11:08", slot, a) == NumberLayout.width("8:88:88", slot, a) && NumberLayout.width("1:11:08", slot, a) == 5 * 10 + 2 * 4, "so are times: " + NumberLayout.width("1:11:08", slot, a));
        check(NumberLayout.width("126.4", slot, a) == 4 * 10 + 3 && NumberLayout.width("-12", slot, a) == 6 + 20 && NumberLayout.width("", slot, a) == 0, "other characters keep their own width");
        check(NumberLayout.advance('1', slot, a) == 10f && NumberLayout.advance(':', slot, a) == 4f && NumberLayout.advance('x', slot, a) == 7f, "advance of a digit, a colon and an unknown character");
        check(NumberLayout.isDigit('0') && NumberLayout.isDigit('9') && !NumberLayout.isDigit('a') && !NumberLayout.isDigit(':'), "what a digit is");
        check(NumberLayout.left(-1, 100, 30) == 100 && NumberLayout.left(0, 100, 30) == 85 && NumberLayout.left(1, 100, 30) == 70, "left, centre and right of a point");
        Map<Character, Float> same = new HashMap<Character, Float>();
        for (char c = '0'; c <= '9'; c++) same.put(c, 8f);
        final Map<Character, Float> sm = same;
        NumberLayout.Advance eq = new NumberLayout.Advance() { @Override public float of(char c) { Float w = sm.get(c); return w == null ? 3f : w; } };
        check(NumberLayout.width("12345", NumberLayout.slot(eq), eq) == 40f, "a font whose digits are equal already gives the same widths");

        // ---------- the font files ----------
        String[] files = {"TekoSemiBold", "TekoBold", "ManropeRegular", "ManropeMedium", "ManropeSemiBold", "ManropeBold"};
        for (String f : files) {
            File file = new File(ASSETS + "fonts/" + f + ".ttf");
            check(file.exists() && file.length() > 10000 && file.length() < 90000, f + ": the file exists and is small (" + file.length() + " bytes)");
            if (!file.exists()) continue;
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file.toPath()));
            check(b.getInt(0) == 0x00010000, f + ": a TrueType file");
            int cmap = cmapOffset(b);
            check(cmap > 0, f + ": has a character table");
            if (cmap <= 0) continue;
            check(covers(b, cmap, "\u00C7\u00E7\u011E\u011F\u0130\u0131\u00D6\u00F6\u015E\u015F\u00DC\u00FC"), f + ": all the Turkish letters");
            check(covers(b, cmap, "\u00C4\u00E4\u00D6\u00F6\u00DC\u00FC\u00DF"), f + ": all the German letters");
            check(covers(b, cmap, "0123456789:.,%-+/ ABCXYZabcxyz"), f + ": digits, letters and the signs of a clock and a speed");
            check(covers(b, cmap, "\u00B0\u00B7\u2013\u2014\u2026\u00B1\u00B2\u00B5"), f + ": the degree sign, the middle dot, dashes, the ellipsis and units");
        }

        // ---------- the Chinese font ----------
        StringBuilder zh = new StringBuilder();
        for (String line : read("app/src/main/assets/i18n/zh.tsv").split("\n")) {
            if (line.startsWith("#") || !line.contains("\t")) continue;
            zh.append(line.substring(line.indexOf('\t') + 1));
        }
        java.util.Set<Integer> zhChars = new java.util.TreeSet<Integer>();
        for (int i = 0; i < zh.length(); i++) if (zh.charAt(i) > 0x20) zhChars.add((int) zh.charAt(i));
        check(zhChars.size() > 500, "the Chinese texts use many characters (" + zhChars.size() + ")");
        for (String f : new String[]{"NotoSansSCRegular", "NotoSansSCBold"}) {
            File file = new File(ASSETS + "fonts/" + f + ".ttf");
            check(file.exists() && file.length() > 100000 && file.length() < 600000, f + ": the file exists and is small (" + file.length() + " bytes)");
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(file.toPath()));
            int cmap = cmapOffset(b);
            check(cmap > 0, f + ": has a character table");
            StringBuilder missing = new StringBuilder();
            for (int cp : zhChars) if (!hasGlyph(b, cmap, cp)) missing.append((char) cp);
            check(missing.length() == 0, f + ": has every character of the Chinese texts" + (missing.length() > 0 ? " (missing: " + missing + ")" : ""));
            check(covers(b, cmap, "0123456789:.,%-+/ ABCXYZabcxyz"), f + ": has digits and Latin letters too");
        }
        check(read(KT + "Fonts.kt").contains("fonts/NotoSansSCRegular.ttf") && read(KT + "Fonts.kt").contains("fonts/NotoSansSCBold.ttf") && read(KT + "Fonts.kt").contains("if (cjkMode) return if (weight >= 600) cjkBold else cjkRegular"), "in Chinese every text uses the bundled font");
        check(read(KT + "Fonts.kt").contains("fun numbers(") && read(KT + "CardStyle.kt").contains("Fonts.numbers(numberFont)"), "numbers stay in Teko in every language");
        String lang = read(KT + "Lang.kt");
        check(lang.contains("if (Fonts.hasChinese) return true") && lang.contains("Fonts.setLanguage(code)"), "Chinese can be chosen when the app has its own font, and the font follows the language");
        check(read(KT + "Toasts.kt").contains("Look.apply(t.view)") && !read(KT + "MainActivity.kt").contains("Toast.makeText("), "toasts get the app's fonts too");

        // ---------- the licences ----------
        for (String lic : new String[]{"Manrope-OFL.txt", "Teko-OFL.txt", "NotoSansSC-OFL.txt"}) {
            String t = read(ASSETS + "licenses/" + lic);
            check(t.contains("SIL OPEN FONT LICENSE Version 1.1") && t.contains("Copyright") && t.length() > 3000, lic + ": the licence text is there");
        }
        int found = 0;
        for (Credits.Entry e : Credits.entries()) if (e.name.startsWith("Manrope") || e.name.startsWith("Teko") || e.name.startsWith("Noto Sans SC")) {
            found++;
            check(e.license.contains("Open Font License") && e.texts.size() == 1 && new File(ASSETS + e.texts.get(0).asset).exists(), e.name + ": credited with its licence text");
        }
        check(found == 3, "all three fonts are in the credits");
        String notices = read("THIRD_PARTY_NOTICES.md");
        check(notices.contains("Manrope") && notices.contains("Teko") && notices.contains("Noto Sans SC") && notices.contains("SIL Open Font License"), "the third-party notices name both fonts");

        // ---------- the wiring (checked in the sources) ----------
        String fonts = read(KT + "Fonts.kt"), cs = read(KT + "CardStyle.kt");
        for (String f : files) check(fonts.contains("fonts/" + f + ".ttf"), "Fonts loads " + f);
        check(fonts.contains("catch (e: Exception)") && fonts.contains("ready = false") && fonts.contains("fallback"), "if the fonts cannot be loaded the system fonts stay");
        check(read(KT + "App.kt").indexOf("Fonts.init(this)") > read(KT + "App.kt").indexOf("Diagnostics.install(this)"), "the fonts are loaded at the start of the app, after the crash record is on");
        check(read(KT + "Immersive.kt").contains("Fonts.apply(w.decorView)"), "dialogs get the fonts");
        String act = read(KT + "MainActivity.kt");
        check(act.indexOf("Look.apply(frame)") > act.indexOf("applyOverlayPrefs()\n        Look.apply"), "the screen gets the fonts and the button shapes once it is built");
        int panels = 0;
        for (String f : new String[]{"SettingsPanel.kt", "RulesPanel.kt", "InfoPanel.kt", "MainMenu.kt", "WelcomeGuide.kt", "AboutPanel.kt", "LogViewerPanel.kt"}) if (read(KT + f).contains("Look.apply(root)")) panels++;
        check(panels == 7, "every panel gives its content the fonts each time it is drawn (" + panels + " of 7)");
        check(fonts.contains("DISPLAY_SCALE = 1.2f") && fonts.contains("Typeface.MONOSPACE") && fonts.contains("originalSize"), "titles are set bigger once, monospace text stays");
        check(cs.contains("Fonts.numbers(numberFont)") && cs.contains("fun drawNumber(") && cs.contains("fun measureNumber(") && cs.contains("fun upper(") && cs.contains("val numberScale"), "the number font and the slot drawing are in CardStyle");
        StringBuilder left = new StringBuilder();
        for (File f : new File(KT).listFiles()) {
            String n = f.getName();
            if (!n.endsWith(".kt") || n.equals("CardStyle.kt")) continue;
            if (read(KT + n).contains("sans-serif-condensed")) left.append(n).append(' ');
        }
        check(left.length() == 0, "no view picks its own system font any more: " + left);
        String vt = read(KT + "ValueTileView.kt"), st = read(KT + "FlightStatsView.kt"), ch = read(KT + "ChartView.kt"), co = read(KT + "CompassView.kt");
        check(vt.contains("CardStyle.drawNumber(canvas, shown") && !vt.contains("valuePaint.measureText(") && vt.contains("CardStyle.upper(spec.title())"), "the value card draws its number in slots and its title in capitals");
        check(st.contains("CardStyle.drawNumber(canvas, l.value") && !st.contains("valuePaint.measureText("), "the statistics card draws its numbers in slots");
        check(ch.contains("CardStyle.drawNumber(canvas, ChartScale.format(name, last)"), "the charts draw their values in slots");
        check(co.contains("CardStyle.drawNumber(canvas, hs") && !co.contains("isFakeBoldText"), "the compass draws its heading in slots, with real bold fonts");
        check(read(KT + "WarningBar.kt").contains("Fonts.text(700, bar.typeface)") && !read(KT + "WarningBar.kt").contains("setTypeface(null, Typeface.BOLD)"), "the warning bar uses the bold font");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
