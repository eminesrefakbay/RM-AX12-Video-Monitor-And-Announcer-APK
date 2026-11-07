// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * Whether the pre-flight card was closed by hand. A closed card stays closed until the next flight starts (it is then back for the
 * one after), or until the aircraft, the pack or the checks change. While the layout is edited it is always shown, so it can be placed.
 */
public final class CheckCardState {
    private boolean closed;

    /** The X was tapped. */
    public void close() { closed = true; }

    /** The aircraft, the pack or the checks changed: the card is wanted again. */
    public void reopen() { closed = false; }

    /** Called on every update: an armed aircraft ends the pre-flight period, so the card is back for the next one. */
    public void update(boolean armed) { if (armed) closed = false; }

    public boolean isClosed() { return closed; }

    /** True when the card may be shown. */
    public boolean allowed(boolean editingLayout) { return editingLayout || !closed; }
}
