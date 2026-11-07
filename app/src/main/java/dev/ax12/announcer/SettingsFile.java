// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * All the app's settings in one text file, for backup and for moving to another radio.
 *   S TAB key TAB text      I TAB key TAB number      B TAB key TAB true|false
 * (inside a value: \n new line, \t tab, \\ backslash). Only the settings listed in SCHEMA are written or read, each with
 * its type and range, so a damaged or hand-edited file can never put something odd into the app.
 * Plain Java, tested on a PC.
 */
public final class SettingsFile {

    public static final int MAX_CHARS = 600000;
    public static final String HEADER = "# AX-HUD settings backup (format 1)";

    private static final Map<String, String> TYPE = new LinkedHashMap<String, String>();     // key -> S, I or B
    private static final Map<String, int[]> RANGE = new HashMap<String, int[]>();              // key -> {min, max}

    private static void s(String k) { TYPE.put(k, "S"); }
    private static void b(String k) { TYPE.put(k, "B"); }
    private static void i(String k, int min, int max) { TYPE.put(k, "I"); RANGE.put(k, new int[]{min, max}); }

    static {
        // rules and texts
        s("rules"); s("warnRules"); s("checkRules"); s("barsConfig"); s("batProfiles");
        // language, updates, camera
        s("language"); s("speechLanguage"); s("updateChannel"); s("camera");
        i("rotation", 0, 270); b("fill");
        // screen
        b("menuAlways"); b("splashOn"); b("updateAuto");
        // sticks, bars, warning bar
        b("sticksOn"); i("sticksSize", 0, 2); i("sticksPos", 0, 4); i("sticksOpacity", 0, 2);
        i("stickMode", 1, 4); i("stickRoll", 1, 16); i("stickPitch", 1, 16); i("stickThrottle", 1, 16); i("stickYaw", 1, 16); i("stickInvert", 0, 15);
        b("barsOn"); b("warnBarOn"); i("warnBarPos", 0, 1);
        // flight cards
        b("statsOn"); i("statsSize", 0, 2); i("statsPos", 0, 4); i("statsOpacity", 0, 2); i("statsRows", 0, 63); i("currentLimit", 5, 300);
        b("radarOn"); i("radarSize", 0, 2); i("radarPos", 0, 4); i("radarOpacity", 0, 2);
        b("checkOn"); i("checkSize", 0, 2); i("checkPos", 0, 4); i("checkOpacity", 0, 2);
        i("armCh", 1, 16); i("armHigh", 1000, 2100); i("armLow", 900, 2100);
        // where the cards are (-1 = in its corner) and how big (percent)
        for (String tile : new String[]{"sticks", "stats", "radar", "check", "chart"}) { i(tile + "X", -1, 1000); i(tile + "Y", -1, 1000); i(tile + "Scale", 50, 250); }
        // live charts
        b("chartOn"); i("chartSize", 0, 2); i("chartPos", 0, 4); i("chartOpacity", 0, 2); i("chartWindow", 0, 2); s("chartSeries");
        // battery, packs and aircraft
        i("batActive", 0, BatteryProfiles.MAX - 1);
        s("packs"); i("packActive", 0, Packs.MAX - 1);
        s("aircraftStore"); s("aircraftActive");
        // flight logs
        b("logOn"); i("logRate", 0, FlightLogger.RATES_HZ.length - 1); i("logKeep", 0, FlightLogger.KEEP.length - 1); s("logFields");
    }

    private static final Map<String, Object> DEFAULTS = new HashMap<String, Object>();

    private static void d(String k, Object v) { DEFAULTS.put(k, v); }

    static {
        d("language", "auto"); d("speechLanguage", "same"); d("updateChannel", "stable");
        d("rotation", 0); d("fill", false);
        d("menuAlways", false); d("splashOn", true); d("updateAuto", true);
        d("sticksOn", false); d("sticksSize", 1); d("sticksPos", 0); d("sticksOpacity", 1);
        d("stickMode", 2); d("stickRoll", 1); d("stickPitch", 2); d("stickThrottle", 3); d("stickYaw", 4); d("stickInvert", 0);
        d("barsOn", true); d("warnBarOn", true); d("warnBarPos", 0);
        d("statsOn", true); d("statsSize", 1); d("statsPos", 0); d("statsOpacity", 1); d("statsRows", 7); d("currentLimit", 50);
        d("radarOn", true); d("radarSize", 1); d("radarPos", 3); d("radarOpacity", 1);
        d("checkOn", true); d("checkSize", 1); d("checkPos", 4); d("checkOpacity", 1);
        d("armCh", 5); d("armHigh", 1750); d("armLow", 1650);
        for (String tile : new String[]{"sticks", "stats", "radar", "check", "chart"}) { d(tile + "X", -1); d(tile + "Y", -1); d(tile + "Scale", 100); }
        d("batActive", 0); d("packActive", 0);
        d("logOn", false); d("logRate", 3); d("logKeep", 1); d("logFields", "");
        d("chartOn", false); d("chartSize", 1); d("chartPos", 1); d("chartOpacity", 1); d("chartWindow", 1); d("chartSeries", ChartConfig.DEFAULT);
    }

    private SettingsFile() {}

    /** The value a setting has until it is changed; null for settings that are texts with their own defaults, and the camera. */
    public static Object defaultOf(String key) { return DEFAULTS.get(key); }

    public static Set<String> keys() { return TYPE.keySet(); }
    public static boolean isKnown(String key) { return TYPE.containsKey(key); }

    public static final class Summary {
        public final boolean usable;
        public final int entries, ignored, soundRules, warningRules, checks, ruleProblems;
        public final String message;
        public final Map<String, Object> values;

        Summary(boolean usable, int entries, int ignored, int soundRules, int warningRules, int checks, int ruleProblems,
                String message, Map<String, Object> values) {
            this.usable = usable;
            this.entries = entries;
            this.ignored = ignored;
            this.soundRules = soundRules;
            this.warningRules = warningRules;
            this.checks = checks;
            this.ruleProblems = ruleProblems;
            this.message = message;
            this.values = values;
        }
    }

    // ------------------------------------------------------------------ writing

    /** @param prefs what the app has stored (String, Integer, Boolean values); keys outside the schema are left out */
    public static String export(Map<String, ?> prefs, String stamp, String appVersion) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        sb.append("# app ").append(appVersion).append(", exported ").append(stamp).append('\n');
        for (Map.Entry<String, String> e : TYPE.entrySet()) {
            Object v = prefs.get(e.getKey());
            if (v == null) continue;
            String t = e.getValue();
            if (t.equals("S") && v instanceof String) sb.append("S\t").append(e.getKey()).append('\t').append(I18n.escape((String) v)).append('\n');
            else if (t.equals("I") && v instanceof Integer) sb.append("I\t").append(e.getKey()).append('\t').append(v).append('\n');
            else if (t.equals("B") && v instanceof Boolean) sb.append("B\t").append(e.getKey()).append('\t').append(v).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ reading

    private static Summary refuse(String why) {
        return new Summary(false, 0, 0, 0, 0, 0, 0, why, new HashMap<String, Object>());
    }

    /** Checks a file and reads the settings that are fine. Nothing is applied here. */
    public static Summary inspect(String text) {
        if (text == null || text.trim().isEmpty()) return refuse(I18n.t("The file is empty."));
        if (text.length() > MAX_CHARS) return refuse(I18n.t("The file is too large to be a settings file."));
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == 0 || (c < 32 && c != '\n' && c != '\r' && c != '\t')) return refuse(I18n.t("This does not look like a text file."));
        }
        Map<String, Object> values = new HashMap<String, Object>();
        int ignored = 0;
        for (String line : RulesFile.normalize(text).split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", 3);
            if (f.length < 3) { ignored++; continue; }
            Object v = read(f[0], f[1], I18n.unescape(f[2]));
            if (v == null) ignored++;
            else values.put(f[1], v);
        }
        if (values.isEmpty()) return refuse(ignored > 0 ? I18n.t("No usable settings found in this file.") : I18n.t("No settings found in this file."));
        int sound = 0, warn = 0, chk = 0, problems = 0;
        RuleParser.Result r;
        if (values.get("rules") != null) { r = RuleParser.parse((String) values.get("rules")); sound = r.rules.size(); problems += r.errors.size(); }
        if (values.get("warnRules") != null) { r = RuleParser.parse((String) values.get("warnRules")); warn = r.rules.size(); problems += r.errors.size(); }
        if (values.get("checkRules") != null) { r = RuleParser.parse((String) values.get("checkRules")); chk = r.rules.size(); problems += r.errors.size(); }
        StringBuilder sb = new StringBuilder(I18n.t("{0} settings, including {1} sound rules, {2} warning rules and {3} checks", values.size(), sound, warn, chk));
        if (ignored > 0) sb.append(I18n.t(". {0} lines were ignored (unknown or invalid)", ignored));
        if (problems > 0) sb.append(I18n.t(". {0} rule lines have problems and will not work", problems));
        return new Summary(true, values.size(), ignored, sound, warn, chk, problems, sb.toString(), values);
    }

    /** One setting as a line of the file, or null when the key or the type of the value is not in the schema. */
    public static String line(String key, Object v) {
        String t = TYPE.get(key);
        if (t == null) return null;
        if (t.equals("S") && v instanceof String) return "S\t" + key + "\t" + I18n.escape((String) v);
        if (t.equals("I") && v instanceof Integer) return "I\t" + key + "\t" + v;
        if (t.equals("B") && v instanceof Boolean) return "B\t" + key + "\t" + v;
        return null;
    }

    /** @return the checked value, or null if the type, the key, the range or the content is wrong */
    public static Object parseValue(String type, String key, String value) {
        return read(type, key, value);
    }

    private static Object read(String type, String key, String value) {
        String want = TYPE.get(key);
        if (want == null || !want.equals(type)) return null;
        if (type.equals("B")) {
            if (value.equals("true")) return Boolean.TRUE;
            if (value.equals("false")) return Boolean.FALSE;
            return null;
        }
        if (type.equals("I")) {
            int n;
            try {
                n = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                return null;
            }
            int[] r = RANGE.get(key);
            if (n < r[0] || n > r[1]) return null;
            if (key.equals("rotation") && n % 90 != 0) return null;
            return n;
        }
        // text
        if (key.equals("rules") || key.equals("warnRules") || key.equals("checkRules")) return value.length() <= 200000 ? value : null;
        if (key.equals("barsConfig")) return value.length() <= 4000 ? value : null;
        if (key.equals("batProfiles")) return BatteryProfiles.parseStrict(value) != null ? value : null;
        if (key.equals("chartSeries")) return ChartConfig.valid(value) ? value : null;
        if (key.equals("logFields")) return value.isEmpty() ? value : (LogFields.parse(value).isEmpty() ? null : LogFields.join(LogFields.parse(value)));
        if (key.equals("packs")) return Packs.parseStrict(value) != null ? value : null;
        if (key.equals("aircraftStore")) return value.length() <= 500000 && AircraftStore.decodeStrict(value, null) != null ? value : null;
        if (key.equals("aircraftActive")) return value.matches("[a-z0-9]{1,8}") ? value : null;
        if (key.equals("camera")) return value.matches("[A-Za-z0-9_.:-]{1,40}") ? value : null;
        if (key.equals("language")) return value.equals("auto") || I18n.isSupported(value) ? value : null;
        if (key.equals("speechLanguage")) return value.equals("same") || I18n.isSupported(value) ? value : null;
        if (key.equals("updateChannel")) return value.equals("stable") || value.equals("nightly") ? value : null;
        return null;
    }
}
