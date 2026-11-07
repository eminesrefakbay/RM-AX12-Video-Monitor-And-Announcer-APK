// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Translations. The English text in the code is the key: t("Settings") gives "Ayarlar" in Turkish, and the English
 * text itself when there is no translation, so a missing translation can never break or blank a screen.
 * Placeholders are {0}, {1}, ... (the same in every language, so word order can change).
 *
 * The tables are plain text files, one per language, in assets/i18n/<code>.tsv:  English text TAB translation.
 * Inside a line, \n is a new line, \t a tab and \\ a backslash. Plain Java, tested on a PC.
 */
public final class I18n {

    public interface Source {
        InputStream open(String path) throws IOException;
    }

    /** English, Turkish, German, Simplified Chinese. */
    public static final String[] LANGUAGES = {"en", "tr", "de", "zh"};

    private static volatile Map<String, String> table = Collections.emptyMap();
    private static volatile String lang = "en";
    private static volatile Source source;

    private I18n() {}

    public static boolean isSupported(String code) {
        for (String l : LANGUAGES) if (l.equals(code)) return true;
        return false;
    }

    /** "auto" follows the Android language when it is one of ours, otherwise English. */
    public static String resolve(String pref, String systemLanguage) {
        if (pref != null && isSupported(pref)) return pref;
        return systemLanguage != null && isSupported(systemLanguage) ? systemLanguage : "en";
    }

    public static synchronized void load(Source src, String language) {
        source = src;
        lang = isSupported(language) ? language : "en";
        if (lang.equals("en") || src == null) {
            table = Collections.emptyMap();
            return;
        }
        Map<String, String> t;
        try (InputStream in = src.open("i18n/" + lang + ".tsv")) {
            t = parseTable(in);
        } catch (IOException e) {
            t = Collections.emptyMap();
        }
        table = t;
    }

    public static String language() {
        return lang;
    }

    /** The text in the current language, with {0}, {1}... filled in. */
    public static String t(String en, Object... args) {
        String s = table.get(en);
        if (s == null) s = en;
        return args == null || args.length == 0 ? s : fill(s, args);
    }

    private static final Map<String, Map<String, String>> otherTables = new HashMap<String, Map<String, String>>();

    /** The text in a given language, which may not be the screen's (for example the language the voice speaks). */
    public static synchronized String tIn(String language, String en, Object... args) {
        if (language == null || language.equals(lang)) return t(en, args);
        Map<String, String> tbl = null;
        if (!language.equals("en") && isSupported(language) && source != null) {
            tbl = otherTables.get(language);
            if (tbl == null) {
                try (InputStream in = source.open("i18n/" + language + ".tsv")) {
                    tbl = parseTable(in);
                } catch (IOException e) {
                    tbl = Collections.emptyMap();
                }
                otherTables.put(language, tbl);
            }
        }
        String s = tbl == null ? null : tbl.get(en);
        if (s == null) s = en;
        return args == null || args.length == 0 ? s : fill(s, args);
    }

    /** Marks a text for translation without translating it now (for tables built when the program starts). */
    public static String n(String en) {
        return en;
    }

    static String fill(String s, Object[] args) {
        if (s.indexOf('{') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' && i + 2 < s.length() && s.charAt(i + 2) == '}' && Character.isDigit(s.charAt(i + 1))) {
                int n = s.charAt(i + 1) - '0';
                if (n < args.length) {
                    sb.append(String.valueOf(args[n]));
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ files

    /** Reads a table: "English TAB translation" per line; # starts a comment. */
    public static Map<String, String> parseTable(InputStream in) throws IOException {
        Map<String, String> out = new HashMap<String, String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        boolean first = true;
        while ((line = r.readLine()) != null) {
            if (first && line.startsWith("\uFEFF")) line = line.substring(1);
            first = false;
            if (line.isEmpty() || line.startsWith("#")) continue;
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            out.put(unescape(line.substring(0, tab)), unescape(line.substring(tab + 1)));
        }
        return out;
    }

    public static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "");
    }

    public static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char d = s.charAt(++i);
                sb.append(d == 'n' ? '\n' : d == 't' ? '\t' : d);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * A whole text file in a language, for the starting rules (assets/i18n/rules.tr.txt ...). Falls back to English.
     * @param name rules, warnings or checks
     */
    public static String text(String name, String language) {
        Source src = source;
        if (src == null) return "";
        String[] tries = {language, "en"};
        for (String l : tries) {
            try (InputStream in = src.open("i18n/" + name + "." + l + ".txt")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                String s = new String(out.toByteArray(), "UTF-8");
                return s.startsWith("\uFEFF") ? s.substring(1) : s;
            } catch (IOException e) {
                // try the next language
            }
        }
        return "";
    }
}
