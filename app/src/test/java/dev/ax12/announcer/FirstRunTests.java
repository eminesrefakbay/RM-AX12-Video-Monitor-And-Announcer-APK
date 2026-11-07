// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.List;

/** Tests of the welcome guide: its pages, its page logic and when it is shown. */
public class FirstRunTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }

    public static void main(String[] args) throws Exception {
        for (String lang : new String[]{"en", "tr", "de", "zh"}) {
            I18n.load(I18nTests.FILES, lang);
            List<FirstRun.Page> pages = FirstRun.pages();
            check(pages.size() == FirstRun.pageCount() && pages.size() == 5, lang + ": five pages");
            for (int i = 0; i < pages.size(); i++) {
                FirstRun.Page p = pages.get(i);
                check(p.title.length() > 2 && p.body.length() > 20, lang + ": page " + (i + 1) + " has a title and a text");
                check(!p.title.endsWith(".") && p.title.length() <= 48, lang + ": page " + (i + 1) + " has a short title without a full stop: " + p.title);
                check(p.body.length() <= (lang.equals("zh") ? 160 : 420), lang + ": page " + (i + 1) + " is short enough to read at a glance (" + p.body.length() + " characters)");
            }
        }
        I18n.load(I18nTests.FILES, "en");
        List<FirstRun.Page> en = FirstRun.pages();
        check(en.get(0).title.equals("Welcome to AX-HUD") && en.get(0).body.contains("never writes"), "page 1 welcomes and says the app only reads");
        check(en.get(1).body.contains("camera permission") && en.get(1).body.contains("Permissions"), "page 2 explains the camera permission and where to allow it");
        check(en.get(2).title.contains("12 seconds") && en.get(2).body.contains("RadioMaster SDK") && en.get(2).body.contains("not an error"), "page 3 explains the 12 second wait");
        check(en.get(3).body.contains("MENU") && en.get(3).body.contains("Sound rules") && en.get(3).body.contains("Exit"), "page 4 says where the MENU is and what is in it");
        check(en.get(4).title.contains("not a safety system") && en.get(4).body.contains("local rules"), "page 5 says the app is an aid");
        // the other languages really are translated (not the English text again)
        for (String lang : new String[]{"tr", "de", "zh"}) {
            I18n.load(I18nTests.FILES, lang);
            List<FirstRun.Page> t = FirstRun.pages();
            int same = 0;
            for (int i = 0; i < t.size(); i++) if (t.get(i).body.equals(en.get(i).body) || t.get(i).title.equals(en.get(i).title)) same++;
            check(same == 0, lang + ": every title and text is translated (" + same + " are not)");
        }
        I18n.load(I18nTests.FILES, "en");

        // ---------- the page logic ----------
        check(FirstRun.isFirst(0) && !FirstRun.isFirst(1) && FirstRun.isLast(4) && !FirstRun.isLast(3), "first and last page");
        check(FirstRun.next(0) == 1 && FirstRun.next(4) == 4 && FirstRun.back(4) == 3 && FirstRun.back(0) == 0, "next and back stay inside the guide");
        int i = 0, steps = 0;
        while (!FirstRun.isLast(i)) { i = FirstRun.next(i); steps++; }
        check(steps == 4, "the last page is reached after four steps");

        // ---------- when it is shown ----------
        check(FirstRun.shouldShow(0), "a new person sees the guide");
        check(!FirstRun.shouldShow(FirstRun.VERSION), "a person who finished it does not see it again");
        check(FirstRun.shouldShow(FirstRun.VERSION - 1), "a person who saw an older guide sees the new one");
        check(!FirstRun.shouldShow(FirstRun.VERSION + 1), "a guide version from the future is not shown");
        check(FirstRun.VERSION >= 1, "the version starts at one");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
