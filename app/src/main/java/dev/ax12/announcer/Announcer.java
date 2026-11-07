// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/** Where spoken messages go. Implemented by the Android text-to-speech wrapper. */
public interface Announcer {
    /** @param urgent true = interrupt whatever is being spoken */
    void say(String text, boolean urgent);
}
