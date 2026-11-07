// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.List;

/**
 * The welcome guide shown on the first start: five short pages about what a new person needs to know (the camera permission, the
 * wait for the radio data, where everything is, and that the app is an aid). The logic is plain Java so that it can be tested.
 */
public final class FirstRun {
    private FirstRun() {}

    /** Raise this when the guide gets new content that people who saw the old guide should see too. */
    public static final int VERSION = 1;

    public static final class Page {
        public final String title, body;
        Page(String title, String body) { this.title = title; this.body = body; }
    }

    /** The pages, in the language of the screen. */
    public static List<Page> pages() {
        List<Page> p = new ArrayList<Page>();
        p.add(new Page(I18n.t("Welcome to AX-HUD"),
            I18n.t("AX-HUD shows the video from the HDMI input of your AX12 full screen, with a HUD on top, and speaks announcements and warnings. It only reads from the radio and never writes to it. This guide takes about a minute.")));
        p.add(new Page(I18n.t("Allow the camera"),
            I18n.t("The HDMI input reaches Android as a camera, so AX-HUD needs the camera permission. If you said no by mistake, allow it in the Android settings, under Apps, AX-HUD, Permissions. Without it you see the gray No video screen.")));
        p.add(new Page(I18n.t("The first data takes about 12 seconds"),
            I18n.t("After you open the app, the radio data (sticks, link quality, telemetry) appears after about 12 seconds. That wait is inside the RadioMaster SDK and is not an error. A banner counts the seconds. The video starts at once.")));
        p.add(new Page(I18n.t("Where everything is"),
            I18n.t("Tap MENU at the top right for six tiles: Sound rules, Speech, Battery, Aircraft, Settings and Exit. Choose your aircraft and battery pack first, because their limits drive the warnings. Settings holds the overlays, the layout, the checks, the flight logs, the languages and the updates.")));
        p.add(new Page(I18n.t("An aid, not a safety system"),
            I18n.t("AX-HUD helps you see and hear what your aircraft is doing. Do not rely on it as your only warning. Always fly within your local rules.")));
        return p;
    }

    public static int pageCount() { return 5; }

    /** True when the person has not seen this version of the guide yet. */
    public static boolean shouldShow(int seenVersion) { return seenVersion < VERSION; }

    public static boolean isFirst(int index) { return index <= 0; }
    public static boolean isLast(int index) { return index >= pageCount() - 1; }
    public static int next(int index) { return Math.min(index + 1, pageCount() - 1); }
    public static int back(int index) { return Math.max(index - 1, 0); }
}
