// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests that the documentation, the CI file names and the release notes agree with the app. */
public class DocsTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String f) throws Exception { return new String(Files.readAllBytes(new File(f).toPath()), "UTF-8"); }

    public static void main(String[] args) throws Exception {
        String ci = read(".github/workflows/build.yml");
        check(ci.contains("dist/ax-hud.apk") && ci.contains("dist/ax-hud-plain.apk"), "the CI publishes ax-hud.apk and ax-hud-plain.apk");
        String[] readmes = {"README.md", "README.tr.md", "README.de.md", "README.zh.md"};
        for (String name : readmes) {
            String t = read(name);
            check(!t.contains("debug-lockpatch"), name + ": no APK name that the CI does not publish");
            check(t.contains("ax-hud.apk") && t.contains("ax-hud-plain.apk") && t.contains("-PaxLockPatch=true"), name + ": the serial-lock part names the real files");
        }
        String r = read("README.md");
        check(!r.contains("Nothing in the app has rounded corners") && r.contains("*Settings > Display > Shape*"), "the README does not deny the shape styles");
        check(!r.contains("## Start-up splash screen") && !r.contains("for **2 seconds**") && r.contains("## Splash screen"), "the README has one splash section, the animated one");
        check(!r.contains("If a device has no Chinese font") && r.contains("font that comes with the app"), "the README says Chinese works without a system font");
        check(!r.contains("use a condensed bold font") && r.contains("Teko"), "the README names the number font");
        check(!r.contains("Buttons are flat and dark"), "the README does not say that buttons are flat");
        Matcher count = Pattern.compile("\\| ([0-9],[0-9]{3}) automated checks pass \\|").matcher(r);
        check(count.find(), "the README states how many checks pass");

        Matcher v = Pattern.compile("val baseVersion = \"([0-9.]+)\"").matcher(read("app/build.gradle.kts"));
        check(v.find(), "the version is found");
        String ver = v.group(1);
        check(new File("docs/release-notes/v" + ver + ".md").exists(), "there are release notes for " + ver);
        check(read("CHANGELOG.md").contains("## " + ver + "\n"), "the changelog has an entry for " + ver);
        check(read("docs/release-notes/v" + ver + ".md").contains("not affiliated with or endorsed by RadioMaster"), "the release notes carry the independence line");

        String panel = read("app/src/main/java/dev/ax12/announcer/SettingsPanel.kt");
        String src = read("app/src/main/assets/i18n/source.txt");
        check(panel.contains("Start-up splash screen (animated, about 3 seconds)") && !panel.contains("2 seconds)") && !src.contains("logo and version, 2 seconds"), "the Settings label of the splash describes the animated splash");
        check(Pattern.compile("SHOW_MS = (3[0-9]{3})L").matcher(read("app/src/main/java/dev/ax12/announcer/SplashOverlay.kt")).find(), "the splash stays about three seconds, as the label says");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
