// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Several aircraft, each with its own set of settings. The settings that belong to an aircraft (KEYS) are kept in the
 * app's normal settings while it is the active one; switching saves them into its slot and loads the next one's.
 * The slots are one text: "@id TAB name" starts an aircraft, then one line per setting (the settings file's format).
 * Plain Java, tested on a PC.
 */
public final class AircraftStore {

    public static final int MAX = 8;
    public static final int MAX_NAME = 16;
    private static final Pattern ID = Pattern.compile("[a-z0-9]{1,8}");

    /** What belongs to an aircraft: rules, battery, packs, channel names, stick mapping, arm switch. Looks and language are shared. */
    private static final String[] KEY_LIST = {
        "rules", "warnRules", "checkRules", "batProfiles", "batActive", "packs", "packActive", "barsConfig",
        "stickMode", "stickRoll", "stickPitch", "stickThrottle", "stickYaw", "stickInvert",
        "armCh", "armHigh", "armLow", "currentLimit"
    };
    private static final Set<String> KEYS = new HashSet<String>(Arrays.asList(KEY_LIST));

    public static String[] keys() { return KEY_LIST.clone(); }
    public static boolean isAircraftKey(String k) { return KEYS.contains(k); }

    public static final class Aircraft {
        public final String id;
        public String name;
        public final Map<String, Object> values = new LinkedHashMap<String, Object>();

        Aircraft(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final List<Aircraft> list = new ArrayList<Aircraft>();
    private String activeId = "a1";

    private AircraftStore() {}

    public static AircraftStore createDefault() {
        AircraftStore s = new AircraftStore();
        s.list.add(new Aircraft("a1", "My aircraft"));
        s.activeId = "a1";
        return s;
    }

    public static String cleanName(String raw) {
        String s = raw == null ? "" : raw.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim().replaceAll("\\s+", " ");
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME).trim() : s;
    }

    public List<Aircraft> list() { return list; }
    public String activeId() { return activeId; }

    public Aircraft find(String id) {
        for (Aircraft a : list) if (a.id.equals(id)) return a;
        return null;
    }

    public Aircraft active() {
        Aircraft a = find(activeId);
        return a != null ? a : list.get(0);
    }

    public boolean setActive(String id) {
        if (find(id) == null) return false;
        activeId = id;
        return true;
    }

    // ------------------------------------------------------------------ text

    public String encode() {
        StringBuilder sb = new StringBuilder("# AX-HUD aircraft\n");
        for (Aircraft a : list) {
            sb.append('@').append(a.id).append('\t').append(a.name).append('\n');
            for (Map.Entry<String, Object> e : a.values.entrySet()) {
                String line = SettingsFile.line(e.getKey(), e.getValue());
                if (line != null) sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static AircraftStore read(String text, String active, boolean strict) {
        AircraftStore s = new AircraftStore();
        if (text == null) return strict ? null : createDefault();
        Aircraft cur = null;
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("@")) {
                String[] h = line.substring(1).split("\t", 2);
                cur = null;
                String id = h[0];
                String name = h.length == 2 ? cleanName(h[1]) : "";
                boolean ok = ID.matcher(id).matches() && !name.isEmpty() && s.find(id) == null && s.list.size() < MAX && !s.nameTaken(name, null);
                if (!ok) { if (strict) return null; continue; }
                cur = new Aircraft(id, name);
                s.list.add(cur);
                continue;
            }
            if (cur == null) { if (strict) return null; continue; }
            String[] f = line.split("\t", 3);
            Object v = null;
            if (f.length == 3 && KEYS.contains(f[1])) v = SettingsFile.parseValue(f[0], f[1], I18n.unescape(f[2]));
            if (v == null) { if (strict) return null; continue; }
            cur.values.put(f[1], v);
        }
        if (s.list.isEmpty()) return strict ? null : createDefault();
        s.activeId = active != null && s.find(active) != null ? active : s.list.get(0).id;
        return s;
    }

    /** Reads a stored text. Damaged parts are skipped; nothing usable gives one default aircraft. */
    public static AircraftStore decode(String text, String activeId) { return read(text, activeId, false); }

    /** null unless every line of the text is valid (used to check a file). */
    public static AircraftStore decodeStrict(String text, String activeId) { return read(text, activeId, true); }

    // ------------------------------------------------------------------ changes

    private boolean nameTaken(String name, String exceptId) {
        for (Aircraft a : list) if (!a.id.equals(exceptId) && a.name.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** @return null if the name can be used, otherwise a message */
    public String validateName(String raw, String exceptId) {
        String n = cleanName(raw);
        if (n.isEmpty()) return I18n.t("Give the aircraft a name.");
        if (nameTaken(n, exceptId)) return I18n.t("There is already an aircraft with this name: {0}.", n);
        return null;
    }

    private String newId() {
        for (int i = 1; i < 1000; i++) if (find("a" + i) == null) return "a" + i;
        return "a999";
    }

    /** @return the new aircraft, or null if the name is not usable or there are too many */
    public Aircraft add(String name, Map<String, Object> values) {
        if (list.size() >= MAX || validateName(name, null) != null) return null;
        Aircraft a = new Aircraft(newId(), cleanName(name));
        for (Map.Entry<String, Object> e : values.entrySet()) if (KEYS.contains(e.getKey())) a.values.put(e.getKey(), e.getValue());
        list.add(a);
        return a;
    }

    /** @return null if done, otherwise a message */
    public String rename(String id, String name) {
        Aircraft a = find(id);
        if (a == null) return I18n.t("That aircraft does not exist.");
        String err = validateName(name, id);
        if (err != null) return err;
        a.name = cleanName(name);
        return null;
    }

    /** @return null if done, otherwise a message */
    public String remove(String id) {
        Aircraft a = find(id);
        if (a == null) return I18n.t("That aircraft does not exist.");
        if (list.size() <= 1) return I18n.t("At least one aircraft must stay.");
        if (id.equals(activeId)) return I18n.t("You cannot delete the aircraft in use. Switch to another one first.");
        list.remove(a);
        return null;
    }

    /** Saves the current settings into the active aircraft's slot (only the settings that belong to an aircraft). */
    public void syncActive(Map<String, Object> live) {
        Aircraft a = active();
        a.values.clear();
        for (String k : KEY_LIST) if (live.get(k) != null) a.values.put(k, live.get(k));
    }

    /**
     * Switching: the live settings are saved into the current aircraft, the target becomes active, and its settings are returned
     * (to be written into the live settings). Settings the target does not have are not in the result: they stay as they are.
     */
    public Map<String, Object> switchTo(String id, Map<String, Object> live) {
        Aircraft target = find(id);
        if (target == null) return null;
        syncActive(live);
        activeId = id;
        return new LinkedHashMap<String, Object>(target.values);
    }
}
