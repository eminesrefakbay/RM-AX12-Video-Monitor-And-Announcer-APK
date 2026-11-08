// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the app is made of and under which licenses: the app itself and every library that ships inside the APK.
 * The About page shows this list; THIRD_PARTY_NOTICES.md says the same in words. A test keeps them together.
 * Plain Java, tested on a PC.
 */
public final class Credits {

    public static final String PROJECT_NAME = "AX-HUD";

    /** One license text that can be read in the app (a file in assets/licenses). */
    public static final class Text {
        public final String label;
        public final String asset;

        Text(String label, String asset) {
            this.label = label;
            this.asset = asset;
        }
    }

    public static final class Entry {
        public final String name;
        public final String holder;
        public final String license;
        public final String url;
        public final String description;       // English; translated when shown
        public final List<Text> texts;

        Entry(String name, String holder, String license, String url, String description, Text... texts) {
            this.name = name;
            this.holder = holder;
            this.license = license;
            this.url = url;
            this.description = description;
            this.texts = Collections.unmodifiableList(java.util.Arrays.asList(texts));
        }
    }

    private static final List<Entry> ENTRIES;

    static {
        List<Entry> e = new ArrayList<Entry>();
        e.add(new Entry("AX-HUD", "eminesrefakbay", "GNU GPL v3 or later, with an exception for the RadioMaster AX SDK",
            "https://github.com/eminesrefakbay/RM-AX12-Video-Monitor-And-Announcer-APK",
            I18n.n("This app: the HUD, the voice announcer, the rules and the settings."),
            new Text("AX-HUD license (GNU GPL v3 or later, with the SDK exception)", "licenses/AX-HUD-license.txt")));
        e.add(new Entry("RadioMaster AX SDK", "RadioMaster", "Published by RadioMaster for app developers",
            "https://github.com/Radiomaster-RC/RadiomasterAX_SDK",
            I18n.n("Reads the radio's channels and telemetry. Its libraries are included in the app. Thank you, RadioMaster, for publishing it.")));
        e.add(new Entry("Qt 6 (Core, Network, SerialPort, Concurrent)", "The Qt Company", "GNU LGPL v3 (Qt open source license)",
            "https://www.qt.io/licensing",
            I18n.n("The Qt libraries that come with the RadioMaster SDK, as delivered with it. Source code: download.qt.io."),
            new Text("Qt license information (from the SDK)", "licenses/Qt-LICENSE.txt"),
            new Text("GNU LGPL v3 (with the GPL v3 it refers to)", "licenses/LGPL-3.0.txt")));
        e.add(new Entry("LLVM C++ runtime (libc++)", "LLVM Project", "Apache License 2.0 with LLVM Exceptions",
            "https://llvm.org/LICENSE.txt",
            I18n.n("The C++ runtime of the Android NDK, needed by the SDK."),
            new Text("Apache License 2.0", "licenses/Apache-2.0.txt")));
        e.add(new Entry("Kotlin standard library", "JetBrains s.r.o. and Kotlin contributors", "Apache License 2.0",
            "https://kotlinlang.org",
            I18n.n("The standard library of the Kotlin language, which the app is written in."),
            new Text("Apache License 2.0", "licenses/Apache-2.0.txt")));
        e.add(new Entry("Manrope (the font of the text)", "The Manrope Project Authors (Mikhail Sharanda)", "SIL Open Font License 1.1",
            "https://github.com/googlefonts/manrope",
            I18n.n("The typeface of the menus, settings and all running text, in four weights (trimmed to the letters the app needs)."),
            new Text("SIL Open Font License 1.1 (Manrope)", "licenses/Manrope-OFL.txt")));
        e.add(new Entry("Teko (the font of titles and big numbers)", "The Teko Project Authors (Indian Type Foundry)", "SIL Open Font License 1.1",
            "https://github.com/googlefonts/teko",
            I18n.n("The narrow typeface of the titles and the big numbers, in two weights (trimmed to the letters the app needs)."),
            new Text("SIL Open Font License 1.1 (Teko)", "licenses/Teko-OFL.txt")));
        ENTRIES = Collections.unmodifiableList(e);
    }

    private Credits() {}

    public static List<Entry> entries() { return ENTRIES; }
}
