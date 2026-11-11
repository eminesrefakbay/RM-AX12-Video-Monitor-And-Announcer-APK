// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;

/** Tests of the screenshot gesture's permission handling and of two small clean-ups. */
public class ScreenshotTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File(f).toPath()), "UTF-8"); }
    static final String KT = "app/src/main/java/dev/ax12/announcer/";

    public static void main(String[] args) throws Exception {
        String act = read(KT + "MainActivity.kt");
        int a = act.indexOf("private fun requestScreenshot()"), b = act.indexOf("private fun captureScreenshot()");
        check(a > 0 && b > a, "the screenshot request is found");
        String body = act.substring(a, b);
        int armed = body.indexOf("flightStats.armed()"), hold = body.indexOf("holdLink(HOLD_REBUILD_MS)"), ask = body.indexOf("requestPermissions(");
        check(armed > 0 && hold > 0 && ask > 0, "the request checks the armed state, holds the link and asks for the permission");
        check(armed < hold && hold < ask, "in that order: never ask in flight, keep the radio link, then ask");
        check(body.contains("return") && body.indexOf("return", armed) < hold, "when armed it stops before asking");
        check(body.contains("if (!storageGranted())") && body.contains("requestPermissions(screenshotPermissions, SCREENSHOT_PERMISSION_REQUEST)"), "it asks only when the permissions are not granted yet, for all of them at once");
        int sg = act.indexOf("private fun storageGranted()");
        String granted = sg > 0 ? act.substring(sg, sg + 260) : "";
        check(granted.contains("Build.VERSION.SDK_INT >= 29") && granted.contains("screenshotPermissions.all"), "Android 10 and newer need none, older versions need every one of them");
        check(act.contains("arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)"), "the request holds the read and the write permission (the radio lists them as two switches)");
        int rp = act.indexOf("SCREENSHOT_PERMISSION_REQUEST) {");
        String answer = rp > 0 ? act.substring(rp, rp + 520) : "";
        check(answer.contains("indexOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)") && answer.contains("captureScreenshot()"), "the answer saves a screenshot when the write permission is granted, even if the read switch stayed off");
        String manifest = read("app/src/main/AndroidManifest.xml");
        check(manifest.contains("android.permission.WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion=\"28\"") && manifest.contains("android.permission.READ_EXTERNAL_STORAGE\" android:maxSdkVersion=\"28\""), "both storage permissions are declared, for Android 9 and older only");
        String cap = read(KT + "ScreenCapture.kt");
        check(cap.contains("directory.isDirectory") && !cap.contains("!directory.exists()"), "the folder check does not depend on read access");
        check(cap.contains("catch (e: Exception)") && cap.contains("Diagnostics.event(\"screenshot\""), "every failure is caught and written to the diagnostics events");
        check(cap.contains("Environment.MEDIA_MOUNTED"), "an unavailable storage is reported");
        String camera = act.substring(act.indexOf("requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)") - 220, act.indexOf("requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)"));
        check(camera.contains("holdLink(HOLD_REBUILD_MS)"), "the camera permission is protected in the same way");

        String src = read("app/src/main/assets/i18n/source.txt");
        String msg = "The first screenshot asks for storage access: do it on the ground, with the aircraft disarmed.";
        check(src.contains(msg) && act.contains("tr(\"" + msg + "\")"), "the message for an armed aircraft exists");
        for (String lang : new String[]{"tr", "de", "zh"}) check(read("app/src/main/assets/i18n/" + lang + ".tsv").contains(msg + "\t"), "and it is translated (" + lang + ")");

        String horizon = read(KT + "HorizonView.kt");
        int d = horizon.indexOf("override fun onDetachedFromWindow()");
        check(d > 0 && horizon.indexOf("removeCallbacks(dataExpiry)", d) > d && horizon.indexOf("expiryScheduled = false", d) > d, "the horizon's expiry timer leaves with the view");
        check(act.split("20 times a second, on its own thread").length == 2 && act.split("NO VIDEO panel, read the radio").length == 1, "the comment above the radio thread describes the radio thread");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
