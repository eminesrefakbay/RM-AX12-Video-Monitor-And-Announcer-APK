// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * One thread for starting and stopping the SDK in the background, shared by every screen of the app, so a stop and the next
 * start always happen in the order they were asked for (for example when the screen is rebuilt). It can also hold a stop back for
 * a while: when the app pauses itself (a rebuild, a file picker) and comes back, the radio link is still running and nothing
 * has to be started again (the SDK needs about 12 seconds to open the serial port). Plain Java, tested on a PC.
 */
public final class SdkThread {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
        @Override public Thread newThread(Runnable r) { return new Thread(r, "ax12-sdk"); }
    });
    private static volatile int stopToken = 0;

    private SdkThread() {}

    /** Runs a task on the SDK thread, after everything asked for before it. A task that fails does not stop later ones. */
    public static void run(final Runnable task) {
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                try { task.run(); } catch (Throwable e) { /* the caller reports its own problems */ }
            }
        });
    }

    /** Runs the stop after the delay, unless {@link #cancelScheduledStop} or a newer scheduled stop comes first. */
    public static void scheduleStop(long delayMs, final Runnable stop) {
        final int token = ++stopToken;
        EXECUTOR.schedule(new Runnable() {
            @Override public void run() {
                if (token != stopToken) return;
                try { stop.run(); } catch (Throwable e) { /* nothing to do */ }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** The screen came back: a stop that is still waiting must not run. */
    public static void cancelScheduledStop() { stopToken++; }
}
