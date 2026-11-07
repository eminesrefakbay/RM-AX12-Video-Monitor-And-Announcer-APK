// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tests for battery packs and for aircraft profiles. */
public class AircraftTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static Map<String, Object> settings(String rules, int stickMode, String bars) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("rules", rules); m.put("warnRules", "cell < $low -> LOW {cell:2}\n"); m.put("checkRules", "sats >= 10 -> GPS\n");
        m.put("batProfiles", BatteryProfiles.encode(BatteryProfiles.defaults())); m.put("batActive", 0);
        m.put("packs", "Pack 1|Li-ion 6S|3000\n"); m.put("packActive", 0); m.put("barsConfig", bars);
        m.put("stickMode", stickMode); m.put("stickRoll", 1); m.put("stickPitch", 2); m.put("stickThrottle", 3); m.put("stickYaw", 4); m.put("stickInvert", 0);
        m.put("armCh", 5); m.put("armHigh", 1750); m.put("armLow", 1650); m.put("currentLimit", 50);
        return m;
    }

    public static void main(String[] args) {
        I18n.load(null, "en");
        // ---------- packs ----------
        List<Packs.Pack> p = new ArrayList<Packs.Pack>();
        p.add(new Packs.Pack("Pack 1", "Li-ion 6S", 3000)); p.add(new Packs.Pack("LiPo A", "LiPo 6S", 0));
        check(Packs.validate(p) == null, "valid packs");
        String txt = Packs.encode(p);
        check(txt.equals("Pack 1|Li-ion 6S|3000\nLiPo A|LiPo 6S|0\n"), "encoded: " + txt);
        List<Packs.Pack> back = Packs.decode(txt);
        check(back.size() == 2 && back.get(0).name.equals("Pack 1") && back.get(1).capacityMah == 0 && Packs.encode(back).equals(txt), "decode agrees");
        check(Packs.decode(null).isEmpty() && Packs.decode("").isEmpty() && Packs.decode("garbage").isEmpty(), "no packs is fine: an empty list");
        check(Packs.decode("a|b|1\nbad\nA|b|2\nc|d|x\ne|f|99999\n").size() == 1, "damaged lines and repeated names are skipped");
        check(Packs.parseStrict("").isEmpty() && Packs.parseStrict(txt) != null && Packs.parseStrict("bad") == null && Packs.parseStrict(txt + "x|y|-1\n") == null && Packs.parseStrict(null) == null, "strict reading");
        List<Packs.Pack> bad = new ArrayList<Packs.Pack>(); for (Packs.Pack x : p) bad.add(x.copy());
        bad.add(new Packs.Pack("pack 1", "x", 0)); check(Packs.validate(bad).contains("same name"), "same name: " + Packs.validate(bad));
        bad = new ArrayList<Packs.Pack>(); for (Packs.Pack x : p) bad.add(x.copy()); bad.get(0).name = " "; check(Packs.validate(bad).contains("Pack 1") && Packs.validate(bad).contains("name"), "empty name: " + Packs.validate(bad));
        bad = new ArrayList<Packs.Pack>(); for (int i = 0; i < 9; i++) bad.add(new Packs.Pack("P" + i, "x", 0)); check(Packs.validate(bad).contains("At most 8"), "too many packs");
        bad = new ArrayList<Packs.Pack>(); bad.add(new Packs.Pack("A", "x", 40000)); check(Packs.validate(bad).contains("capacity"), "capacity too large: " + Packs.validate(bad));
        check(Packs.describe(p.get(0)).equals("Pack 1 (Li-ion 6S, 3000 mAh)") && Packs.describe(p.get(1)).equals("LiPo A (LiPo 6S)"), "describe: " + Packs.describe(p.get(0)));
        check(Packs.profileIndex(p.get(1), BatteryProfiles.defaults()) == 1 && Packs.profileIndex(p.get(0), BatteryProfiles.defaults()) == 0 && Packs.profileIndex(new Packs.Pack("x", "nope", 0), BatteryProfiles.defaults()) == -1, "a pack finds its profile by name");
        check(Packs.cleanName("  a|b\tc ").equals("a b c") && Packs.cleanName("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length() <= 16, "names are cleaned");

        // ---------- the aircraft store ----------
        AircraftStore st = AircraftStore.createDefault();
        check(st.list().size() == 1 && st.active().name.equals("My aircraft") && st.activeId().equals("a1"), "one aircraft to start with");
        check(AircraftStore.keys().length == 18 && AircraftStore.isAircraftKey("rules") && AircraftStore.isAircraftKey("packs") && !AircraftStore.isAircraftKey("language") && !AircraftStore.isAircraftKey("sticksOn"), "what belongs to an aircraft");
        for (String k : AircraftStore.keys()) check(SettingsFile.isKnown(k), "aircraft setting is in the schema: " + k);
        AircraftStore.Aircraft b = st.add("XL9", settings("ch5 >= 1 -> xl9 armed\n", 1, "1|5|ARM|0\n"));
        check(b != null && b.id.equals("a2") && b.name.equals("XL9") && b.values.size() == 18, "a second aircraft");
        check(st.add("xl9", new HashMap<String, Object>()) == null && st.add("  ", new HashMap<String, Object>()) == null, "names must be new and not empty");
        check(st.validateName("xl9", null).contains("already") && st.validateName("xl9", "a2") == null && st.validateName("", null).contains("name"), "name messages: " + st.validateName("xl9", null));
        Map<String, Object> mixed = new HashMap<String, Object>(); mixed.put("rules", "x"); mixed.put("language", "tr"); mixed.put("sticksOn", true);
        AircraftStore.Aircraft c = st.add("Whoop", mixed);
        check(c != null && c.values.size() == 1 && c.values.containsKey("rules"), "only the settings of an aircraft are kept (language and looks are shared)");
        check(st.rename("a3", "Tiny whoop") == null && st.find("a3").name.equals("Tiny whoop") && st.rename("a3", "xl9").contains("already") && st.rename("zz", "X").contains("not exist"), "rename");
        check(st.remove("a1").contains("in use") && st.remove("zz").contains("not exist"), "the aircraft in use cannot be deleted");
        check(st.remove("a3") == null && st.list().size() == 2, "another one can");
        check(st.remove("a2") == null && st.remove("a1").contains("At least one"), "the last aircraft cannot be deleted");
        st = AircraftStore.createDefault();
        for (int i = 0; i < 7; i++) check(st.add("Plane " + i, new HashMap<String, Object>()) != null, "adding aircraft " + (i + 2));
        check(st.add("Ninth", new HashMap<String, Object>()) == null && st.list().size() == AircraftStore.MAX, "at most eight aircraft");
        check(st.remove("a8") == null && st.add("Again", new HashMap<String, Object>()).id.equals("a8"), "a freed id is used again");

        // ---------- text ----------
        AircraftStore s2 = AircraftStore.createDefault();
        s2.active().values.putAll(settings("ch5 >= 1500 -> armed | disarmed\n# note\tTab and \\ backslash \u00C7\u0131\u011F \u4E2D\u6587\n", 2, "1|5|ARM|0\n"));
        s2.add("XL9", settings("cell < $low -> low\n", 1, "0|6|MODE|1\n"));
        s2.setActive("a2");
        String enc = s2.encode();
        AircraftStore s3 = AircraftStore.decode(enc, s2.activeId());
        check(s3.list().size() == 2 && s3.activeId().equals("a2") && s3.find("a1").values.equals(s2.find("a1").values) && s3.find("a2").values.equals(s2.find("a2").values), "encode and decode keep everything, including tabs, $ names and other alphabets");
        check(s3.encode().equals(enc), "stable");
        check(AircraftStore.decodeStrict(enc, "a2") != null, "strict reading of a good text");
        check(AircraftStore.decode(null, null).list().size() == 1 && AircraftStore.decode("", "a9").active().name.equals("My aircraft") && AircraftStore.decode("junk\nmore", null).list().size() == 1, "nothing usable gives one default aircraft");
        check(AircraftStore.decodeStrict(null, null) == null && AircraftStore.decodeStrict("", null) == null && AircraftStore.decodeStrict("junk", null) == null, "strict: nothing usable is refused");
        String damaged = "@a1\tOne\nI\tstickMode\t99\nS\tlanguage\ten\nX\tbad\nI\tstickMode\t3\n@A!\tBad id\n@a2\tOne\n@a3\tThree\nS\trules\tch5 >= 1 -> x\n";
        AircraftStore d = AircraftStore.decode(damaged, "a3");
        check(d.list().size() == 2 && d.find("a1").values.size() == 1 && d.find("a1").values.get("stickMode").equals(3) && d.find("a3") != null, "a damaged text is repaired: bad ids, repeated names, values out of range and other settings are dropped (" + d.list().size() + ")");
        check(AircraftStore.decodeStrict(damaged, null) == null, "but a strict reading refuses it");
        check(AircraftStore.decode(enc, "nope").activeId().equals("a1"), "an unknown active id falls back to the first aircraft");

        // ---------- switching: nothing is lost on the way ----------
        AircraftStore sw = AircraftStore.createDefault();
        Map<String, Object> live = settings("A rules\n", 2, "1|5|ARM|0\n");
        Map<String, Object> bValues = settings("B rules\n", 1, "0|7|OTHER|1\n");
        sw.add("B", bValues);
        Map<String, Object> toB = sw.switchTo("a2", live);
        check(toB.equals(bValues) && sw.activeId().equals("a2"), "switching to B returns B's settings");
        live.putAll(toB);                                    // the app writes them into the live settings
        live.put("rules", "B edited\n"); live.put("stickMode", 3);   // the pilot edits B
        Map<String, Object> toA = sw.switchTo("a1", live);
        check(toA.get("rules").equals("A rules\n") && toA.get("stickMode").equals(2) && toA.get("barsConfig").equals("1|5|ARM|0\n"), "back to A: A's settings are exactly as they were");
        check(sw.find("a2").values.get("rules").equals("B edited\n") && sw.find("a2").values.get("stickMode").equals(3), "B's edits were saved when leaving it");
        live.putAll(toA);
        check(sw.switchTo("a2", live).get("rules").equals("B edited\n"), "and come back with it");
        check(sw.switchTo("zz", live) == null && sw.activeId().equals("a2"), "an unknown aircraft changes nothing");
        // live settings the app has but an aircraft does not own are never saved into it
        Map<String, Object> liveWithShared = new HashMap<String, Object>(live); liveWithShared.put("language", "de"); liveWithShared.put("sticksOn", true);
        sw.syncActive(liveWithShared);
        check(!sw.active().values.containsKey("language") && !sw.active().values.containsKey("sticksOn"), "shared settings are not saved per aircraft");
        // an aircraft that has no value for a setting leaves the live one alone
        AircraftStore partial = AircraftStore.createDefault(); partial.add("Bare", new HashMap<String, Object>());
        check(partial.switchTo("a2", live).isEmpty(), "an aircraft without saved settings returns none");

        // ---------- in the full backup ----------
        Map<String, Object> prefs = new HashMap<String, Object>();
        prefs.put("aircraftStore", enc); prefs.put("aircraftActive", "a2"); prefs.put("packs", "Pack 1|Li-ion 6S|3000\n"); prefs.put("packActive", 0); prefs.put("language", "de");
        SettingsFile.Summary sum = SettingsFile.inspect(SettingsFile.export(prefs, "x", "y"));
        check(sum.usable && sum.ignored == 0 && sum.values.equals(prefs), "aircraft and packs travel in the settings file: " + sum.message);
        String broken = "S\taircraftStore\t" + I18n.escape("@a1\tOne\nI\tstickMode\t99\n") + "\nB\tfill\ttrue\n";
        SettingsFile.Summary bs = SettingsFile.inspect(broken);
        check(bs.ignored == 1 && !bs.values.containsKey("aircraftStore"), "a damaged aircraft store in a file is ignored, not half-loaded");
        check(SettingsFile.inspect("B\tfill\ttrue\nS\tpacks\tbad line\n").ignored == 1 && SettingsFile.inspect("B\tfill\ttrue\nS\tpacks\t\n").ignored == 0, "packs are checked, and no packs is fine");
        check(SettingsFile.inspect("B\tfill\ttrue\nS\taircraftActive\tA!\n").ignored == 1, "an aircraft id is checked");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
