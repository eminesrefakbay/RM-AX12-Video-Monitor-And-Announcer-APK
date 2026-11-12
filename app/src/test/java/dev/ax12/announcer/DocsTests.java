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

        // the notice about the RadioMaster SDK, in every language and in the app
        check(r.contains("## The RadioMaster SDK: what is settled and what is not") && r.contains("states no licence") && r.contains("remove the libraries"), "the README has the SDK notice");
        check(r.contains("(#the-radiomaster-sdk-what-is-settled-and-what-is-not)"), "the top of the README points to the notice");
        for (String name : new String[]{"README.tr.md", "README.de.md", "README.zh.md"}) check(read(name).contains("README.md#the-radiomaster-sdk-what-is-settled-and-what-is-not"), name + ": has the notice and points to the English one");
        String credits = read("app/src/main/java/dev/ax12/announcer/Credits.java");
        check(credits.contains("(no license file; see the README notice)") && credits.contains("RadioMaster has not answered my questions about redistribution"), "the About page says the same as the README");
        Matcher title = Pattern.compile("# AX-HUD: AX12 video monitor, HUD and announcer \\(v([0-9.]+)\\)").matcher(r);
        check(title.find() && title.group(1).equals(ver), "the README title shows the current version " + ver + (title.find(0) ? " (it shows " + title.group(1) + ")" : ""));

        String src = read("app/src/main/assets/i18n/source.txt");
        // the texts of the app and of the tracker name no place (a value for one town is wrong for everyone else)
        check(!src.contains("Mersin") && !read("tracker/README.md").contains("Mersin") && !read("tracker/src/main.cpp").contains("Mersin") && !read("app/src/main/java/dev/ax12/announcer/TrackerProtocol.java").contains("Mersin"), "no place name in the app texts, the tracker README or the tracker code");
        check(read("tracker/src/main.cpp").contains("prefs.getShort(\"decl\", 0)") && src.contains("Magnetic declination (degrees, east is positive)"), "the declination starts at 0 and the dialog asks for degrees, east positive");
        // the community files, and the label on the tracker
        for (String f : new String[]{"bug_report.yml", "feature_request.yml", "question.yml"}) {
            String y = read(".github/ISSUE_TEMPLATE/" + f);
            check(y.contains("name:") && y.contains("description:") && y.contains("body:") && y.contains("type: textarea"), f + ": an issue form with its fields");
        }
        String bug = read(".github/ISSUE_TEMPLATE/bug_report.yml");
        check(bug.contains("Diagnostics and bug report") && bug.contains("Antenna tracker (experimental)") && bug.contains("id: version") && bug.contains("required: true"), "the bug form asks for the version and the diagnostics report");
        check(read(".github/ISSUE_TEMPLATE/config.yml").contains("blank_issues_enabled: false") && new File(".github/PULL_REQUEST_TEMPLATE.md").exists(), "issues come through the forms, and pull requests have a template");
        Matcher mail = Pattern.compile("[A-Za-z0-9._-]+@[A-Za-z0-9-]+\\.[a-z]+").matcher(read("CODE_OF_CONDUCT.md"));
        String sec = read("SECURITY.md");
        check(mail.find() && sec.contains("**" + mail.group() + "**") && sec.contains("no pairing and no encryption") && sec.contains("Report a vulnerability"), "SECURITY.md uses the contact address of the code of conduct and states the tracker's Bluetooth limit");
        int at = r.indexOf("## Antenna tracker");
        check(at > 0 && r.indexOf("> **Experimental.**", at) > at && r.indexOf("> **Experimental.**", at) < at + 200, "the README labels the antenna tracker experimental right under its heading");
        String trk = read("tracker/README.md");
        check(trk.contains("> **Experimental.**") && trk.contains("## Known limitations") && trk.contains("**No pairing and no encryption**"), "the tracker README is labelled experimental and lists its limits");

        String panel = read("app/src/main/java/dev/ax12/announcer/SettingsPanel.kt");
        check(panel.contains("Start-up splash screen (animated, about 3 seconds)") && !panel.contains("2 seconds)") && !src.contains("logo and version, 2 seconds"), "the Settings label of the splash describes the animated splash");
        check(Pattern.compile("SHOW_MS = (3[0-9]{3})L").matcher(read("app/src/main/java/dev/ax12/announcer/SplashOverlay.kt")).find(), "the splash stays about three seconds, as the label says");

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) System.exit(1);
    }
}
