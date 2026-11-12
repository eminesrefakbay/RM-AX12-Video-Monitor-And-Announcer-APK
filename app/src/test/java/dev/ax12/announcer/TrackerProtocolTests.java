// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests of the antenna tracker support: the Bluetooth packets (also read by the C++ tests), the feed from the telemetry and the wiring. */
public class TrackerProtocolTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File(f).toPath()), "UTF-8"); }
    static final String KT = "app/src/main/java/dev/ax12/announcer/";

    static byte[] unhex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return b;
    }
    static long val(String line, String key) {
        Matcher m = Pattern.compile(" " + key + "=(-?[0-9]+)").matcher(line);
        return m.find() ? Long.parseLong(m.group(1)) : Long.MIN_VALUE;
    }
    static Map<String, Double> sample(double lat, double lon, double alt, double sats, double speed, double course, Double lq) {
        Map<String, Double> v = new HashMap<String, Double>();
        v.put("gps_lat", lat); v.put("gps_lon", lon); v.put("gps_alt", alt); v.put("sats", sats); v.put("gps_speed", speed); v.put("gps_heading", course);
        if (lq != null) v.put("up_lq", lq);
        return v;
    }

    public static void main(String[] args) throws Exception {
        // ---------- the packets ----------
        check(TrackerProtocol.crc8("123456789".getBytes("US-ASCII"), 9) == 0xF4, "CRC-8 check value of the standard test string");
        check(TrackerProtocol.PACKET_LEN == 20 && TrackerProtocol.encodeAircraft(0, 0, 0, 0, 0, 0, 0, 0).length == 20 && TrackerProtocol.encodeStatus(new TrackerProtocol.Status()).length == 20, "every packet is 20 bytes, so it fits the default size of a Bluetooth LE write");
        int aircraftLines = 0, statusLines = 0;
        for (String line : read("tracker/test/vectors.txt").split("\\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split(" ");
            byte[] b = unhex(p[1]);
            if (p[0].equals("aircraft")) {
                aircraftLines++;
                TrackerProtocol.Aircraft a = TrackerProtocol.decodeAircraft(b);
                check(a != null && a.flags == val(line, "flags") && a.sats == val(line, "sats") && Math.round(a.lat * 1e7) == val(line, "lat_e7") && Math.round(a.lon * 1e7) == val(line, "lon_e7"), "aircraft packet: flags, satellites, position");
                check(a != null && Math.round(a.heightM * 10) == val(line, "height_dm") && a.ageMs == val(line, "age_ms") && Math.round(a.speedKmh * 10) == val(line, "speed_dkmh") && Math.round(a.courseDeg * 100) == val(line, "course_cdeg"), "aircraft packet: height, age, speed, course");
                byte[] again = TrackerProtocol.encodeAircraft(a.flags, a.sats, a.lat, a.lon, a.heightM, a.ageMs, a.speedKmh, a.courseDeg);
                check(TrackerProtocol.hex(again).equals(p[1]), "writing the same values gives the same bytes as in the shared file");
                byte[] bad = b.clone(); bad[5] ^= 0x40;
                check(TrackerProtocol.decodeAircraft(bad) == null, "a damaged packet is refused");
            } else if (p[0].equals("status")) {
                statusLines++;
                TrackerProtocol.Status s = TrackerProtocol.decodeStatus(b);
                check(s != null && s.flags == val(line, "flags") && s.sats == val(line, "sats") && s.mode == val(line, "mode") && Math.round(s.lat * 1e7) == val(line, "lat_e7") && Math.round(s.lon * 1e7) == val(line, "lon_e7"), "status packet: flags, mode, position");
                check(s != null && Math.round(s.headingDeg * 10) == val(line, "heading_ddeg") && Math.round(s.azimuthDeg * 10) == val(line, "azimuth_ddeg") && Math.round(s.elevationDeg * 10) == val(line, "elevation_ddeg") && Math.round(s.distanceM / 10) == val(line, "distance_10m"), "status packet: heading, azimuth, elevation, distance");
                check(s != null && TrackerProtocol.hex(TrackerProtocol.encodeStatus(s)).equals(p[1]), "a status packet is written back to the same bytes");
            }
        }
        check(aircraftLines >= 2 && statusLines >= 2, "the shared file has aircraft and status packets");
        byte[] ok = TrackerProtocol.encodeAircraft(15, 12, 36.8, 34.6, 10, 100, 50, 90);
        check(TrackerProtocol.decodeAircraft(java.util.Arrays.copyOf(ok, 19)) == null && TrackerProtocol.decodeStatus(java.util.Arrays.copyOf(ok, 19)) == null, "a short packet is refused");
        byte[] other = ok.clone(); other[0] = 2; other[19] = (byte) TrackerProtocol.crc8(other, 19);
        check(TrackerProtocol.decodeAircraft(other) == null && TrackerProtocol.decodeStatus(other) == null, "another version is refused");

        // ---------- limits ----------
        TrackerProtocol.Aircraft a = TrackerProtocol.decodeAircraft(TrackerProtocol.encodeAircraft(0, 300, -33.9, -70.7, 99999, 999999, 99999, -10));
        check(a != null && a.sats == 255 && a.heightM == 3276.7 && a.ageMs == 65535 && a.speedKmh == 6553.5 && Math.abs(a.courseDeg - 350.0) < 0.001, "values out of range are held at their limits, a course of -10 is 350");
        a = TrackerProtocol.decodeAircraft(TrackerProtocol.encodeAircraft(0, 0, 36.1234567, 34.7654321, -250.4, 0, 0, 360));
        check(Math.abs(a.lat - 36.1234567) < 1e-7 && Math.abs(a.lon - 34.7654321) < 1e-7 && Math.abs(a.heightM + 250.4) < 0.05 && a.courseDeg == 0.0, "positions keep 1e-7 degrees (about 1 cm), a negative height is kept, a course of 360 is 0");

        // ---------- commands ----------
        check(java.util.Arrays.equals(TrackerProtocol.cmdMode(TrackerProtocol.MODE_CENTRE), new byte[]{1, 2}) && java.util.Arrays.equals(TrackerProtocol.cmdMode(TrackerProtocol.MODE_AUTO), new byte[]{1, 1}), "mode commands");
        check(java.util.Arrays.equals(TrackerProtocol.cmdCompassCalibration(true), new byte[]{2, 1}) && java.util.Arrays.equals(TrackerProtocol.cmdCompassCalibration(false), new byte[]{2, 0}), "compass calibration commands");
        check(java.util.Arrays.equals(TrackerProtocol.cmdDeclination(5.5), new byte[]{3, 55, 0}) && java.util.Arrays.equals(TrackerProtocol.cmdDeclination(-5.5), new byte[]{3, (byte) 0xC9, (byte) 0xFF}) && java.util.Arrays.equals(TrackerProtocol.cmdDeclination(120), new byte[]{3, (byte) 0x84, 0x03}), "the declination is in 0.1 degrees, east positive, held at 90 degrees");
        check(java.util.Arrays.equals(TrackerProtocol.cmdHeightOffset(1.5), new byte[]{4, 15, 0}) && java.util.Arrays.equals(TrackerProtocol.cmdHeightOffset(100000), new byte[]{4, (byte) 0x88, 0x13}), "the antenna height is in 0.1 m, held at 500 m");
        check(Pattern.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", TrackerProtocol.SERVICE) && !TrackerProtocol.SERVICE.equals(TrackerProtocol.AIRCRAFT) && !TrackerProtocol.AIRCRAFT.equals(TrackerProtocol.STATUS) && !TrackerProtocol.STATUS.equals(TrackerProtocol.COMMAND), "four different, valid UUIDs");

        // ---------- the feed from the telemetry ----------
        TrackerFeed feed = new TrackerFeed();
        TrackerProtocol.Aircraft none = TrackerProtocol.decodeAircraft(feed.build(1000));
        check(none != null && none.flags == 0 && none.lat == 0 && none.lon == 0 && none.ageMs == 65535 && !feed.hasFix() && !feed.fresh(1000), "before any telemetry: no flags, no position, the oldest age");
        feed.update(sample(36.8, 34.6, 100, 12, 50, 90, 100.0), false, 1000);
        a = TrackerProtocol.decodeAircraft(feed.build(1500));
        check(a != null && a.has(TrackerProtocol.A_FIX) && a.has(TrackerProtocol.A_LINK) && a.has(TrackerProtocol.A_HEIGHT) && !a.has(TrackerProtocol.A_ARMED), "on the ground with a fix: fix, link and height, not armed");
        check(a.ageMs == 500 && a.heightM == 0.0 && a.sats == 12 && a.speedKmh == 50.0 && a.courseDeg == 90.0 && Math.abs(a.lat - 36.8) < 1e-7, "age 500 ms, height 0 on the ground, the rest as sent");
        feed.update(sample(36.8, 34.6, 104, 12, 50, 90, 100.0), false, 2000);
        check(Math.abs(feed.heightM() - 0.0) < 1e-9, "on the ground the height stays zero when the altitude drifts (the ground follows it)");
        feed.update(sample(36.8001, 34.6, 104, 12, 50, 90, 100.0), true, 3000);
        feed.update(sample(36.8002, 34.6, 134, 12, 50, 90, 100.0), true, 4000);
        a = TrackerProtocol.decodeAircraft(feed.build(4000));
        check(a.has(TrackerProtocol.A_ARMED) && Math.abs(a.heightM - 30.0) < 0.05, "after arming the ground is frozen: 134 m is 30 m high");
        feed.update(sample(36.8002, 34.6, 134, 12, 50, 90, 100.0), true, 5000);
        check(TrackerProtocol.decodeAircraft(feed.build(7000)).ageMs == 3000 && feed.fresh(7000) && !feed.fresh(10500), "unchanged telemetry: the age grows, the position is called fresh for 6 s");
        feed.update(sample(36.8003, 34.6, 134, 12, 50, 90, 100.0), true, 8000);
        check(TrackerProtocol.decodeAircraft(feed.build(8100)).ageMs == 100, "a new position starts the age again");
        feed.update(sample(36.8003, 34.6, 90, 12, 50, 90, 100.0), false, 9000);
        check(Math.abs(feed.heightM()) < 1e-9, "after disarming the height is zero again");
        TrackerFeed weak = new TrackerFeed();
        weak.update(sample(36.8, 34.6, 100, 4, 0, 0, 100.0), false, 1000);
        a = TrackerProtocol.decodeAircraft(weak.build(1100));
        check(!weak.hasFix() && !a.has(TrackerProtocol.A_FIX) && a.lat == 0 && a.lon == 0, "fewer than 5 satellites: no fix, no position is sent");
        TrackerFeed zero = new TrackerFeed();
        zero.update(sample(0, 0, 0, 12, 0, 0, 100.0), false, 1000);
        check(!zero.hasFix(), "latitude and longitude of 0 is no fix");
        TrackerFeed nolink = new TrackerFeed();
        nolink.update(sample(36.8, 34.6, 100, 12, 0, 0, null), false, 1000);
        check(!TrackerProtocol.decodeAircraft(nolink.build(1100)).has(TrackerProtocol.A_LINK), "no link quality in the telemetry: the link flag is off");
        nolink.update(sample(36.8, 34.6, 100, 12, 0, 0, 0.0), false, 1200);
        check(!TrackerProtocol.decodeAircraft(nolink.build(1300)).has(TrackerProtocol.A_LINK), "a link quality of 0 is no link either");

        // ---------- the files and the wiring (checked in the sources) ----------
        for (String f : new String[]{"platformio.ini", "include/aim.h", "include/protocol.h", "include/config.h", "src/main.cpp", "test/test_core.cpp", "test/vectors.txt", "README.md"})
            check(new File("tracker/" + f).exists(), "tracker/" + f + " exists");
        String ph = read("tracker/include/protocol.h");
        check(ph.contains("\"" + TrackerProtocol.SERVICE + "\"") && ph.contains("\"" + TrackerProtocol.AIRCRAFT + "\"") && ph.contains("\"" + TrackerProtocol.STATUS + "\"") && ph.contains("\"" + TrackerProtocol.COMMAND + "\"") && ph.contains("\"" + TrackerProtocol.DEVICE_NAME + "\""), "the UUIDs and the name of the tracker are the same in the app and in the firmware");
        check(ph.contains("A_FIX = 1, A_ARMED = 2, A_LINK = 4, A_HEIGHT = 8") && ph.contains("S_GPS = 1, S_COMPASS = 2, S_FRESH = 4, S_TRACKING = 8, S_FLIPPED = 16, S_CALIBRATING = 32, S_LIMIT = 64") && ph.contains("MODE_HOLD = 0, MODE_AUTO = 1, MODE_CENTRE = 2, MODE_SWEEP = 3") && ph.contains("CMD_MODE = 1, CMD_COMPASS_CAL = 2, CMD_DECLINATION = 3, CMD_HEIGHT_OFFSET = 4"), "the flags, modes and commands are numbered the same in the firmware");
        String main = read("tracker/src/main.cpp");
        check(main.contains("decodeAircraft(") && main.contains("deadReckon(") && main.contains("servoAngles(") && main.contains("encodeStatus("), "the firmware uses the tested core for the packets and the geometry");
        check(read("tracker/platformio.ini").contains("NimBLE-Arduino") && read("tracker/platformio.ini").contains("espressif32@"), "the platform and the Bluetooth library are pinned");

        String link = read(KT + "TrackerLink.kt"), act = read(KT + "MainActivity.kt"), panel = read(KT + "SettingsPanel.kt");
        check(link.contains("UUID.fromString(TrackerProtocol.SERVICE)") && link.contains("WRITE_TYPE_NO_RESPONSE") && link.contains("ENABLE_NOTIFICATION_VALUE") && link.contains("TRANSPORT_LE"), "the link uses the service, writes without response and subscribes to the status");
        check(link.contains("BACKOFF_MAX_MS = 10000L") && link.contains("Diagnostics.event(\"tracker\""), "it reconnects with a limit on the wait, and says what happens in the diagnostics");
        check(link.contains("writeBusy") && link.contains("override fun onCharacteristicWrite"), "only one write at a time");
        check(act.contains("trackerFeed.update(numbers, flightStats.armed(), sampleNow)") && act.contains("trackerLink.sendAircraft(trackerFeed.build(now))") && act.contains("now - lastTrackerSendAt >= 200L"), "the aircraft goes to the tracker five times a second");
        int pause = act.indexOf("override fun onPause() {"), destroy = act.indexOf("override fun onDestroy() {");
        check(act.indexOf("stopTracker()", pause) > pause && act.indexOf("stopTracker()", pause) < pause + 120 && act.indexOf("stopTracker()", destroy) > destroy && act.indexOf("stopTracker()", destroy) < destroy + 120, "the tracker is let go when the app pauses and when it is destroyed");
        int find = act.indexOf("private fun findTracker()"), ask = act.indexOf("requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)", find), arm = act.indexOf("flightStats.armed()", find), hold = act.indexOf("holdLink(HOLD_REBUILD_MS)", find);
        check(find > 0 && arm > find && hold > arm && ask > hold, "the search never asks for the permission in flight and keeps the radio link while the dialog is open");
        check(act.contains("TRACKER_PERMISSION_REQUEST) {") && act.contains("openTrackerSearch()"), "the answer of the permission dialog starts the search");
        String manifest = read("app/src/main/AndroidManifest.xml");
        for (String perm : new String[]{"BLUETOOTH", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION"}) check(manifest.contains("android.permission." + perm + "\" android:maxSdkVersion=\"30\""), perm + " is declared for Android 11 and older only");
        check(manifest.contains("android.hardware.bluetooth_le\" android:required=\"false\""), "Bluetooth LE is not required to install the app");
        check(SettingsFile.isKnown("trkOn") && SettingsFile.isKnown("trkAddr") && SettingsFile.isKnown("trkName") && Boolean.FALSE.equals(SettingsFile.defaultOf("trkOn")) && "".equals(SettingsFile.defaultOf("trkAddr")), "the choice is part of the settings backup, off by default");
        check("AA:BB:CC:DD:EE:FF".equals(SettingsFile.parseValue("S", "trkAddr", "AA:BB:CC:DD:EE:FF")) && SettingsFile.parseValue("S", "trkAddr", "AA:BB:CC") == null && SettingsFile.parseValue("S", "trkAddr", "aa:bb:cc:dd:ee:ff") == null && "".equals(SettingsFile.parseValue("S", "trkAddr", "")), "only a Bluetooth address (or nothing) is accepted as the tracker's address");
        check(!read(KT + "BugReport.java").contains("\"trkAddr\"") && read(KT + "BugReport.java").contains("\"trkOn\""), "a bug report says whether the tracker is on, but never its address");
        check(read("app/src/main/assets/icons.txt").contains("icon tracker") && panel.contains("Tab(\"tracker\", tr(\"Antenna tracker\"), \"tracker\"") && panel.contains("trackerContent()"), "the settings have an Antenna tracker tab with its own icon");
        String src = read("app/src/main/assets/i18n/source.txt");
        for (String t : new String[]{"Antenna tracker", "Use an antenna tracker", "Follow the aircraft", "Center the antennas (straight ahead)", "Compass needs calibration", "Waiting for the aircraft's GPS", "Looking for the tracker...", "Enter a number between {0} and {1}."}) {
            check(src.contains(t), "the text is in the code: " + t);
            for (String lang : new String[]{"tr", "de", "zh"}) check(read("app/src/main/assets/i18n/" + lang + ".tsv").contains(t + "\t"), t + " is translated (" + lang + ")");
        }
        String r = read("README.md");
        check(r.contains("## Antenna tracker") && r.contains("tracker/README.md") && r.contains("Bluetooth LE"), "the README describes the antenna tracker");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
