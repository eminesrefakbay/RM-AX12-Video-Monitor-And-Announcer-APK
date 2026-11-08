// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests for the icons, the credits and the license files: they exist, they are the real texts, and they agree with each other. */
public class AboutTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        I18n.load(null, "en");
        String assets = "app/src/main/assets/";

        // ---------- the icon file ----------
        List<String> errors = new ArrayList<String>();
        Map<String, List<IconSet.Op>> icons = IconSet.parse(read(assets + "icons.txt"), errors);
        check(errors.isEmpty(), "the icon file has no mistakes: " + errors);
        String[] needed = {"rules", "speaker", "speaker_off", "battery", "aircraft", "gear", "language", "overlays", "video", "update", "diagnostics", "about", "chevron_right", "chevron_down", "exit", "warn_critical", "warn_warning", "video_off"};
        for (String n : needed) check(icons.containsKey(n), "icon exists: " + n);
        int off = 0, empty = 0;
        for (Map.Entry<String, List<IconSet.Op>> e : icons.entrySet()) {
            if (e.getValue().isEmpty()) empty++;
            for (IconSet.Op op : e.getValue()) if (!IconSet.onGrid(op)) { off++; System.out.println("  off the grid: " + e.getKey() + " " + op.type); }
        }
        check(off == 0 && empty == 0, "every shape lies on the 24 x 24 grid and every icon has shapes");

        // ---------- the icon parser ----------
        errors = new ArrayList<String>();
        Map<String, List<IconSet.Op>> t = IconSet.parse("# c\nicon a\nrect 1 1 5 5 1 fill\ncircle 12 12 4\nline 1 1 2 2\npolyline 1 1 2 2 3 3\npoly 1 1 5 1 3 5 fill\narc 1 1 9 9 0 90\n", errors);
        check(errors.isEmpty() && t.get("a").size() == 6 && t.get("a").get(0).fill && !t.get("a").get(1).fill && t.get("a").get(4).fill, "all shapes are read: " + errors);
        errors = new ArrayList<String>();
        t = IconSet.parse("rect 1 1 2 2 1\nicon a\nunknown 1 2\nrect 1 2 3\nline 1 2 3 x\nline 1 2 3 4 fill\npoly 1 1 2 2\npolyline 1 1 2\nicon a\nicon b\nicon\nicon c\ncircle 5 5 2\n", errors);
        check(t.size() == 1 && t.containsKey("c") && !t.containsKey("a") && !t.containsKey("b") && errors.size() == 11, "mistakes are reported and skipped, an icon without shapes is dropped: " + t.keySet() + " " + errors.size());
        check(IconSet.parse("", null).isEmpty() && IconSet.parse("# only a comment\n", null).isEmpty(), "nothing is fine");
        check(IconSet.onGrid(new IconSet.Op("circle", new double[]{12, 12, 9}, false)) && !IconSet.onGrid(new IconSet.Op("circle", new double[]{22, 12, 9}, false)) && !IconSet.onGrid(new IconSet.Op("line", new double[]{0, 0, 25, 3}, false)), "the grid check");

        // ---------- the license files ----------
        String own = read(assets + "licenses/AX-HUD-license.txt");
        check(read("LICENSE").equals(own), "the LICENSE file in the repository is the license shown in the app");
        check(own.contains("Copyright (C) 2026 eminesrefakbay") && own.contains("either version 3 of the License, or (at your option) any later") && own.contains("GNU GENERAL PUBLIC LICENSE") && own.contains("Version 3, 29 June 2007") && own.contains("END OF TERMS AND CONDITIONS") && own.length() > 34000, "the LICENSE has the GPL v3 text with the notice");
        String flat = own.replaceAll("\\s+", " ");
        check(flat.contains("ADDITIONAL PERMISSION UNDER GNU GPL VERSION 3 SECTION 7") && flat.contains("RadioMaster AX SDK") && flat.contains("github.com/Radiomaster-RC/RadiomasterAX_SDK") && flat.contains("does not have to include the source code of the SDK") && flat.contains("you may extend this additional permission"), "and the additional permission for the RadioMaster SDK");
        check(!new File(assets + "licenses/MIT.txt").exists(), "the MIT text is gone from the app");
        String apache = read(assets + "licenses/Apache-2.0.txt");
        check(apache.contains("Apache License") && apache.contains("Version 2.0, January 2004") && apache.contains("TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION") && apache.length() > 9000, "Apache 2.0 text is there and complete");
        String lgpl = read(assets + "licenses/LGPL-3.0.txt");
        check(lgpl.contains("GNU LESSER GENERAL PUBLIC LICENSE") && lgpl.contains("Version 3, 29 June 2007") && lgpl.contains("GNU GENERAL PUBLIC LICENSE") && lgpl.length() > 40000, "LGPL 3 text is there, with the GPL 3 it refers to");
        String qt = read(assets + "licenses/Qt-LICENSE.txt");
        check(qt.contains("Qt is available under") && qt.contains("LGPL") && qt.length() > 40000, "the Qt license information is there");

        // ---------- the credits ----------
        List<Credits.Entry> list = Credits.entries();
        check(list.size() == 7 && list.get(0).name.equals("AX-HUD") && list.get(0).license.startsWith("GNU GPL v3 or later"), "seven entries (the app, the SDK, its libraries and two fonts), the app first, under the GPL v3 or later");
        Set<String> names = new HashSet<String>();
        int bad = 0, texts = 0;
        for (Credits.Entry e : list) {
            if (!names.add(e.name)) bad++;
            if (e.holder.isEmpty() || e.license.isEmpty() || e.description.isEmpty() || !e.url.startsWith("https://")) { bad++; System.out.println("  incomplete: " + e.name); }
            for (Credits.Text x : e.texts) {
                texts++;
                File f = new File(assets + x.asset);
                if (!f.isFile() || f.length() < 500 || x.label.isEmpty()) { bad++; System.out.println("  missing text: " + x.asset); }
            }
        }
        check(bad == 0 && texts >= 5, "names are unique, every entry is complete, every license text exists (" + texts + " texts)");
        check(list.get(1).texts.isEmpty() && list.get(1).name.contains("RadioMaster") && list.get(1).description.contains("Thank you"), "the SDK entry credits RadioMaster and does not invent a license text");
        check(list.get(2).texts.size() == 2 && list.get(2).license.contains("LGPL"), "Qt: its own license information and the LGPL");
        check(Credits.entries().get(0).url.contains("eminesrefakbay"), "the project page");

        // ---------- the written notices agree with the list ----------
        String notices = read("THIRD_PARTY_NOTICES.md");
        int missing = 0;
        for (Credits.Entry e : list) if (!e.name.equals("AX-HUD") && !notices.contains(e.name)) { missing++; System.out.println("  not in THIRD_PARTY_NOTICES.md: " + e.name); }
        check(missing == 0, "every library of the About page is named in THIRD_PARTY_NOTICES.md");
        check(notices.contains("not affiliated") && notices.contains("GNU General Public License, version 3 or later") && notices.contains("Thank you, RadioMaster"), "the notices carry the thanks, the MIT statement and the no-affiliation statement");
        check(read("README.md").contains("GNU General Public License, version 3 or later") && !read("README.md").contains("MIT License"), "the README names the license");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
