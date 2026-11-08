// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Tests of the diagnostics: the privacy filter, the event log, the crash record and the report. */
public class DiagTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File("app/src/main/java/dev/ax12/announcer/" + f).toPath()), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        TimeZone utc = TimeZone.getTimeZone("UTC");

        // ---------- the privacy filter ----------
        check(Privacy.redact("at 36.812345, 34.612345 the link was lost").equals("at <position removed> the link was lost"), "a position pair: " + Privacy.redact("at 36.812345, 34.612345 the link was lost"));
        check(Privacy.redact("lat=36.8123456 lon=-34.6123456").equals("lat=<number removed> lon=<number removed>"), "single long decimals: " + Privacy.redact("lat=36.8123456 lon=-34.6123456"));
        check(Privacy.redact("36.8123,34.6123").equals("<position removed>"), "four decimals are enough for a pair");
        check(Privacy.redact("ratio 0.75 and 12.5 V and 1.2345 km").equals("ratio 0.75 and 12.5 V and 1.2345 km"), "ordinary numbers stay");
        check(Privacy.redact("mac 00:08:22:6c:a1:fb ip 192.168.1.26 and fe80::1:2:3:4").equals("mac <address removed> ip <address removed> and <address removed>"), "addresses: " + Privacy.redact("mac 00:08:22:6c:a1:fb ip 192.168.1.26 and fe80::1:2:3:4"));
        check(Privacy.redact("mail me at pilot.name+x@example.com").equals("mail me at <address removed>"), "an e-mail address");
        check(Privacy.redact("imei 123456789012345 serial 12345").equals("imei <number removed> serial 12345"), "a long number goes, a short one stays");
        check(Privacy.redact("version 1.4.0 build 1234 at 17:42:10 took 12.1 s").equals("version 1.4.0 build 1234 at 17:42:10 took 12.1 s"), "versions, times and durations stay");
        check(Privacy.redact(null).isEmpty() && Privacy.findings(null).isEmpty() && Privacy.findings("clean text 12 m").isEmpty(), "nothing in, nothing out");
        check(Privacy.findings("x 36.812345, 34.612345 y 10.0.0.1 z a@b.co 1234567890123").equals(Arrays.asList("position", "address", "e-mail", "long number")), "everything is found: " + Privacy.findings("x 36.812345, 34.612345 y 10.0.0.1 z a@b.co 1234567890123"));
        check(Privacy.findings(Privacy.redact("x 36.812345, 34.612345 y 10.0.0.1 z a@b.co 1234567890123")).isEmpty(), "and nothing is left after redaction");

        // ---------- the event log ----------
        DiagLog log = new DiagLog();
        check(log.size() == 0 && log.snapshot().isEmpty(), "empty at first");
        log.add(1000, "info", "App", "started");
        log.add(2000, "warn", "link", "no data after 20 s");
        log.add(3000, "error", "update", "HTTP 504");
        log.add(4000, "debug", "x", "levels that do not exist count as info");
        List<DiagLog.Event> ev = log.snapshot();
        check(ev.size() == 4 && ev.get(0).tag.equals("app") && ev.get(0).level.equals("info") && ev.get(3).level.equals("info") && ev.get(2).level.equals("error"), "added in order, tags in lower case");
        check(log.count("warn") == 1 && log.count("error") == 1 && log.count("info") == 2, "counted by level");
        log.add(5000, "info", "pos", "last position 36.812345, 34.612345 at home 192.168.0.5");
        check(!log.snapshot().get(4).message.contains("36.8") && !log.snapshot().get(4).message.contains("192.168") && log.snapshot().get(4).message.contains("<position removed>"), "private data never enters the log: " + log.snapshot().get(4).message);
        log.add(6000, "info", "multi", "line one\nline two\t\u0007 end");
        check(log.snapshot().get(5).message.equals("line one line two end"), "one line, no control characters: " + log.snapshot().get(5).message);
        StringBuilder longMsg = new StringBuilder();
        for (int i = 0; i < 400; i++) longMsg.append('x');
        log.add(7000, "info", "averyveryverylongtag", longMsg.toString());
        check(log.snapshot().get(6).message.length() == DiagLog.MAX_MESSAGE && log.snapshot().get(6).message.endsWith("...") && log.snapshot().get(6).tag.length() <= DiagLog.MAX_TAG, "long texts are cut");
        for (int i = 0; i < 300; i++) log.add(10000 + i, "info", "n", "event " + i);
        check(log.size() == DiagLog.CAPACITY && log.snapshot().get(DiagLog.CAPACITY - 1).message.equals("event 299") && log.snapshot().get(0).message.equals("event 100"), "only the latest " + DiagLog.CAPACITY + " are kept");
        log.clear();
        check(log.size() == 0, "cleared");

        // ---------- the crash record ----------
        RuntimeException inner = new IllegalStateException("inner problem at 36.812345, 34.612345");
        RuntimeException outer = new RuntimeException("outer", inner);
        String crash = CrashFile.format(1794136075000L, "main", outer);
        check(crash.startsWith("time: 2026-") && crash.contains("thread: main") && crash.contains("java.lang.RuntimeException: outer") && crash.contains("caused by: java.lang.IllegalStateException"), "when, where and what");
        check(crash.contains("  at dev.ax12.announcer.DiagTests.main") && crash.contains("<position removed>") && !crash.contains("36.812345"), "the stack is there, private data is not");
        check(crash.length() <= CrashFile.MAX_CHARS, "a record has a limit: " + crash.length());
        Throwable deep = new Error("deep");
        for (int i = 0; i < 8; i++) deep = new Error("level " + i, deep);
        check(CrashFile.format(0, "t", deep).split("caused by:").length - 1 <= CrashFile.MAX_CAUSES, "the chain of causes is cut");
        check(CrashFile.format(0, "worker\nthread", new Exception()).contains("thread: worker thread") && CrashFile.format(0, "t", new Exception((String) null)).contains("java.lang.Exception\n"), "odd names and no message");

        // ---------- the report ----------
        BugReport.Section app = new BugReport.Section("App").add("Version: 1.5.0 (build 123)").add("Update channel: stable");
        log.add(1794136000000L, "info", "app", "started");
        log.add(1794136005000L, "error", "link", "SDK could not open the serial link");
        String rep = BugReport.build("AX-HUD bug report", 1794136075000L, utc, Arrays.asList(app, BugReport.events(log.snapshot(), utc, 60)), "It froze when I armed. Home was 36.812345, 34.612345.");
        check(rep.startsWith("AX-HUD bug report\nCreated: 2026-11-08 ") && rep.contains("(clock of the radio, UTC)"), "header and clock");
        check(rep.contains("\n== App ==\nVersion: 1.5.0 (build 123)\nUpdate channel: stable\n") && rep.contains("\n== Recent events (oldest first) ==\n"), "sections");
        check(rep.contains("ERR  link: SDK could not open the serial link") && rep.contains(" app: started"), "events with their level");
        check(rep.contains("== Note from the pilot ==\nIt froze when I armed. Home was <position removed>."), "the note is there and its position is removed: " + rep.substring(rep.indexOf("== Note")));
        check(Privacy.findings(rep).isEmpty(), "the whole report is clean");
        check(BugReport.build("h", 0, utc, new ArrayList<BugReport.Section>(), "").contains("== Note from the pilot ==\nnone\n") && BugReport.events(new ArrayList<DiagLog.Event>(), utc, 5).lines.get(0).equals("none"), "empty note and no events");
        for (int i = 0; i < 100; i++) log.add(1794136100000L + i, "info", "n", "e" + i);
        BugReport.Section last = BugReport.events(log.snapshot(), utc, 10);
        check(last.lines.size() == 11 && last.lines.get(10).startsWith("(") && last.lines.get(10).contains("older events left out") && last.lines.get(9).endsWith("n: e99"), "only the newest events, and a note about the rest");
        // the settings digest
        Map<String, Object> prefs = new LinkedHashMap<String, Object>();
        prefs.put("logOn", true); prefs.put("language", "en"); prefs.put("packs", "Li-ion 1:3550"); prefs.put("aircraftStore", "Akb7custom001"); prefs.put("rules", "secret rule");
        prefs.put("packBook", "P|x"); prefs.put("winStart", 960); prefs.put("sunlight", false);
        List<String> digest = BugReport.settingsLines(prefs);
        check(digest.equals(Arrays.asList("language: en", "sunlight: false", "logOn: true")), "only the settings that tell how the app behaves: " + digest);
        boolean allKnown = true;
        for (String k : BugReport.SETTINGS) if (!SettingsFile.isKnown(k)) { allKnown = false; System.out.println("  unknown setting in the digest: " + k); }
        check(allKnown, "every setting of the digest exists in the backup schema");
        boolean noPrivate = true;
        for (String k : new String[]{"packs", "aircraftStore", "aircraftActive", "packActive", "packBook", "rules", "warnRules", "checkRules", "winStart", "winEnd", "batProfiles", "dashTiles", "logFields", "language2"}) if (Arrays.asList(BugReport.SETTINGS).contains(k)) noPrivate = false;
        check(noPrivate, "names, rules, times and layouts are not in the digest");

        // ---------- the wiring (checked in the sources: the screens cannot run here) ----------
        String app2 = read("App.kt"), dg = read("Diagnostics.kt"), act = read("MainActivity.kt"), sp = read("SettingsPanel.kt"), fx = read("FlightExtras.kt"), up = read("Updater.kt");
        check(app2.contains("Diagnostics.install(this)") && app2.indexOf("Diagnostics.install(this)") < app2.indexOf("StartupState.begin()"), "a crash is recorded from the very start of the app");
        check(dg.contains("setDefaultUncaughtExceptionHandler") && dg.contains("previous?.uncaughtException(thread, error)") && dg.contains("CrashFile.format(") && dg.contains("catch (e: Throwable)"), "the crash handler writes the record and then lets the system do its part");
        check(dg.contains("BugReport.settingsLines(Prefs.exportMap(activity))") && dg.contains("BugReport.build(\"AX-HUD bug report\"") && dg.contains("arrayOf(\"lq\", \"up_lq\", \"dbm\", \"tlm\")"), "the report takes the settings digest and only four telemetry values");
        for (String bad : new String[]{"gps_lat", "gps_lon", "packBook", "aircraftStore", "Prefs.packs", "rulesText", "last_position", "PositionNote", "LinkLoss.Fix"}) check(!dg.contains(bad), "the report code does not touch " + bad);
        check(act.contains("else if (diagPanel.isShowing) diagPanel.hide()") && act.contains("diagPanel.isShowing || replay != null") && act.contains("frame.addView(diagPanel.root") && act.contains("override fun openDiagnostics()"), "the page is on the screen, counts as open and closes with the back key");
        check(act.contains("Intent.ACTION_SEND") && act.contains("i.type = \"text/plain\"") && act.contains("/issues/new") && act.contains("RM-AX12-Video-Monitor-And-Announcer-APK"), "share sends plain text, the issue page is this project's");
        check(act.contains("Diagnostics.event(\"link\", if (error == null) \"radio link started\" else error") && act.contains("radio configuration loaded after") && act.contains("Diagnostics.event(\"logger\", it, \"warn\")"), "the radio link and the logger leave events");
        check(act.contains("copyToClipboard(diagReport())") && act.contains("holdLink(HOLD_OPENED_MS)"), "copying uses the clipboard, leaving for another app holds the radio link");
        check(sp.contains("group(\"diag\", tr(\"Diagnostics\"), \"bug\")") && sp.contains("host.openDiagnostics()"), "Settings has the Diagnostics group");
        check(read("../../../../assets/icons.txt").contains("icon bug\n"), "the bug icon exists");
        check(fx.contains("Diagnostics.event(\"flight\"") && fx.contains("lost in flight") && fx.contains("\"back after \""), "arming, disarming and link loss leave events");
        check(up.contains("Diagnostics.event(\"update\""), "the update check leaves events");
        // no event is ever written with a position in it
        boolean clean = true;
        for (String f : new String[]{"MainActivity.kt", "FlightExtras.kt", "Updater.kt", "App.kt"}) for (String line : read(f).split("\n")) if (line.contains("Diagnostics.event(") && line.matches(".*(lat|lon|coords|\\.lat|fix\\b).*")) { clean = false; System.out.println("  position-like event line: " + line.trim()); }
        check(clean, "no event is written with a position");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
