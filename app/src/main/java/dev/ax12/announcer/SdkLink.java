// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

/**
 * How the SDK opens the radio's serial link. Newer SDK versions take the port and the baud rate (RadioMaster's example:
 * initialize(path, "ttyS0")). With a port named the SDK opens it straight away; with no port it scans for one, which is what
 * takes about 12 seconds. Plain Java, tested on a PC.
 */
public final class SdkLink {
    public static final int COUNT = 4;
    public static final int DEFAULT = 0;
    /** The radio's own serial port (the AX12 has only ttyS0). */
    public static final String PORT = "ttyS0";
    /** What the SDK uses when no baud rate is given. */
    public static final int SDK_DEFAULT_BAUD = 921600;
    private static final int[] BAUD = {921600, 1870000, 400000, SDK_DEFAULT_BAUD};

    private SdkLink() {}

    /** The stored choice, kept in range (a damaged or restored value falls back to the default). */
    public static int clamp(int choice) { return choice < 0 || choice >= COUNT ? DEFAULT : choice; }

    /** True when the SDK is told which port to open; false means it scans for the port itself. */
    public static boolean namesPort(int choice) { return clamp(choice) != COUNT - 1; }

    /** The port name given to the SDK; empty means "scan". */
    public static String port(int choice) { return namesPort(choice) ? PORT : ""; }

    /** The baud rate given to the SDK. */
    public static int baud(int choice) { return BAUD[clamp(choice)]; }

    /** The next choice when the row is tapped. */
    public static int next(int choice) { return (clamp(choice) + 1) % COUNT; }

    /** The row's value: the port and the baud rate, or "scan" (the caller supplies the translated word). */
    public static String label(int choice, String scanWord) {
        if (!namesPort(choice)) return scanWord;
        return PORT + " " + baud(choice);
    }
}
