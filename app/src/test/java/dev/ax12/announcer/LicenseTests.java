// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Tests that the license is the same everywhere: a header in every source file, the documents, the contributor agreement. */
public class LicenseTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static String read(String p) throws Exception { return new String(Files.readAllBytes(new File(p).toPath()), StandardCharsets.UTF_8); }

    static void collect(File dir, List<File> out) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.getName().equals(".git") || f.getName().equals("build") || f.getName().equals(".gradle")) continue;
            if (f.isDirectory()) { collect(f, out); continue; }
            String n = f.getName();
            if (n.endsWith(".kt") || n.endsWith(".java") || n.endsWith(".cpp") || n.endsWith(".h") || n.endsWith(".kts") || n.endsWith(".py") || n.endsWith(".sh") || n.endsWith(".ps1") || n.equals("CMakeLists.txt")) out.add(f);
        }
    }

    public static void main(String[] args) throws Exception {
        // ---------- a header in every source file ----------
        List<File> files = new ArrayList<File>();
        collect(new File("."), files);
        int missing = 0;
        for (File f : files) {
            String[] lines = read(f.getPath()).split("\n", 9);
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(lines.length, 8); i++) top.append(lines[i]).append('\n');
            String t = top.toString();
            if (!(t.contains("SPDX-License-Identifier: GPL-3.0-or-later") && t.contains("Copyright (C) 2026 eminesrefakbay") && t.contains("additional permission for the RadioMaster AX SDK described in the LICENSE file"))) {
                missing++; System.out.println("  no license header: " + f.getPath());
            }
        }
        check(files.size() > 90, "source files found: " + files.size());
        check(missing == 0, "every source file starts with the license header (" + missing + " do not)");

        // ---------- the documents ----------
        String contributing = read("CONTRIBUTING.md");
        check(contributing.contains("irrevocable") && contributing.contains("sublicense") && contributing.contains("proprietary terms") && contributing.contains("git commit -s") && contributing.contains("Signed-off-by") && contributing.contains("lawyer"), "CONTRIBUTING.md holds the contributor agreement and how to sign");
        check(contributing.contains("not open for outside contributions yet"), "and says the repository is not open for contributions yet");
        String readme = read("README.md");
        check(readme.contains("GPL-3.0-or-later") && readme.contains("additional permission") && readme.contains("Other terms") && readme.contains("CONTRIBUTING.md"), "the README names the license, the permission, the other-terms rule and the contributor file");
        for (String f : new String[]{"README.tr.md", "README.de.md", "README.zh.md"}) {
            String s = read(f);
            check(s.contains("GPL-3.0-or-later") && s.contains("CONTRIBUTING.md") && !s.contains("MIT"), f + " says GPL, links the contributor file and no longer says MIT");
        }
        String notices = read("THIRD_PARTY_NOTICES.md");
        check(notices.contains("additional permission") && notices.contains("section 7") && notices.contains("LGPL v3") && notices.contains("Apache License 2.0"), "the notices explain how the licenses fit together");

        // ---------- no leftover claim that the app is MIT ----------
        int mit = 0;
        for (String f : new String[]{"README.md", "README.tr.md", "README.de.md", "README.zh.md", "THIRD_PARTY_NOTICES.md", "CONTRIBUTING.md", "app/src/main/java/dev/ax12/announcer/Credits.java", "app/src/main/java/dev/ax12/announcer/AboutPanel.kt", "app/src/main/assets/i18n/source.txt", "app/src/main/assets/i18n/tr.tsv", "app/src/main/assets/i18n/de.tsv", "app/src/main/assets/i18n/zh.tsv"}) {
            String s = read(f);
            if (s.contains("MIT License") || s.contains("MIT Lisans") || s.contains("MIT-Lizenz") || s.contains("MIT 许可") || s.contains("(MIT)")) { mit++; System.out.println("  still says MIT: " + f); }
        }
        check(mit == 0, "nothing still says that AX-HUD is under the MIT License (" + mit + " do)");
        String about = read("app/src/main/java/dev/ax12/announcer/AboutPanel.kt");
        check(about.contains("GNU General Public License, version 3 or later") && about.contains("RadioMaster AX SDK"), "the About page says GPL v3 or later with the SDK permission");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
