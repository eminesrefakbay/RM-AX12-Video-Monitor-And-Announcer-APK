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
        check(body.contains("Build.VERSION.SDK_INT < 29") && body.contains("checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)"), "it asks only where Android needs it (before Android 10) and only when it is not granted yet");
        String manifest = read("app/src/main/AndroidManifest.xml");
        check(manifest.contains("android.permission.WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion=\"28\""), "the storage permission is declared for Android 9 and older only");
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
