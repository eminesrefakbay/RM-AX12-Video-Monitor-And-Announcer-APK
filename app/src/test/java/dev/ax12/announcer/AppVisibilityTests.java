// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Tests for when the radio link may be shut down, and guards for the dialog keyboard fix. */
public class AppVisibilityTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String p) throws Exception { return new String(Files.readAllBytes(new File(p).toPath()), StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        AppVisibility v = new AppVisibility();
        check(v.visibleScreens() == 0, "nothing on show at first");

        // the main screen opens, the rules editor opens over it, the main screen stops, the editor closes
        v.started();                                         // main screen
        v.started();                                         // rules editor starts before the main screen stops
        check(v.stopped(1000) == -1, "the main screen stops but the rules editor is still on show: keep the radio link");
        check(v.visibleScreens() == 1, "one screen on show");
        long t = 0;
        for (int i = 0; i < 100; i++) t += 60_000;           // the editor stays open for as long as you like
        check(v.visibleScreens() == 1, "(a long edit changes nothing)");
        v.started();                                         // closing the editor: the main screen starts again first
        check(v.stopped(t) == -1, "the editor stops, the main screen is on show again: keep the radio link");
        check(v.stopped(t + 1) == 0, "the main screen stops with nothing else on show (Home, another app): shut down at once");
        check(v.visibleScreens() == 0, "nothing on show");

        // a rebuild asks for a wait: the old screen stops, the new one starts
        v.started();
        v.hold(10_000, 10_000);
        check(v.stopped(10_500) == 10_000, "a rebuild: the link is shut down only after 10 s, unless the app comes back first");
        v.started();
        check(v.stopped(30_000) == 0, "the wait was used up by that stop: the next stop is at once");

        // a file picker
        v.started();
        v.hold(50_000, 40_000);
        check(v.stopped(51_000) == 40_000, "a file picker: 40 s");

        // a hold that was not followed by leaving is forgotten after 3 s
        v.started();
        v.hold(100_000, 40_000);
        check(v.stopped(100_000 + AppVisibility.HOLD_VALID_MS + 1) == 0, "an old hold does not apply to a later Home press");
        v.started();
        v.hold(200_000, 40_000);
        check(v.stopped(200_000 + AppVisibility.HOLD_VALID_MS) == 40_000, "a hold is valid for exactly its 3 s");

        // a hold set while another screen of the app is up does not leak into the next real exit
        v.started(); v.started();
        v.hold(300_000, 40_000);                             // the app opens its own rules editor
        check(v.stopped(300_100) == -1, "(the main screen stops behind the editor)");
        check(v.stopped(300_000 + 60_000) == 0, "a minute later the editor closes into Home: no leftover wait");

        // robustness: more stops than starts never goes below zero
        AppVisibility w = new AppVisibility();
        check(w.stopped(0) == 0 && w.stopped(0) == 0 && w.visibleScreens() == 0, "extra stops are harmless");
        w.started();
        check(w.visibleScreens() == 1 && w.stopped(0) == 0, "and the count is right afterwards");

        // ---------- guards: the app uses it, and the dialog keyboard fix stays in place ----------
        String app = read("app/src/main/java/dev/ax12/announcer/App.kt");
        check(app.contains("registerActivityLifecycleCallbacks") && app.contains("onActivityStarted") && app.contains("onActivityStopped") && app.contains("visibility.stopped(")
              && app.contains("SdkThread.cancelScheduledStop()") && app.contains("AxSdkJni.nativeStop()"), "App watches the screens of the app and shuts the radio link down only when none is on show");
        String act = read("app/src/main/java/dev/ax12/announcer/MainActivity.kt");
        int pause = act.indexOf("override fun onPause()");
        String pauseBody = act.substring(pause, act.indexOf("}", pause + 30) + 1);
        check(!pauseBody.contains("SdkThread") && !pauseBody.contains("nativeStop"), "the main screen pausing does not shut the radio link down");
        check(act.contains("App.visibility.hold(") && !act.contains("SdkThread.scheduleStop("), "our own rebuilds and pickers ask for a wait through the same counter");

        String imm = read("app/src/main/java/dev/ax12/announcer/Immersive.kt");
        int show = imm.indexOf("dialog.show()");
        int clear = imm.indexOf("FLAG_ALT_FOCUSABLE_IM", show);
        check(show > 0 && clear > show && imm.indexOf("clearFlags", show) > 0 && imm.contains("SOFT_INPUT_ADJUST_RESIZE"),
              "every dialog can bring up the keyboard: the 'no keyboard' flag is cleared AFTER the dialog is shown (Android sets it when a dialog starts without text fields)");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
