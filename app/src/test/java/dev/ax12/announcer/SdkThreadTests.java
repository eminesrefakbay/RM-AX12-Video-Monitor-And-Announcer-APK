// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Tests for the SDK thread: stops that wait, are cancelled or replaced, tasks that keep their order, one thread, failures. */
public class SdkThreadTests {
    static int checks = 0, failures = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { } }

    public static void main(String[] args) {
        // 1. a scheduled stop runs after its delay, not before
        final AtomicLong ran = new AtomicLong(0);
        final long t0 = System.nanoTime();
        SdkThread.scheduleStop(300, new Runnable() { public void run() { ran.set((System.nanoTime() - t0) / 1_000_000); } });
        sleep(150);
        check(ran.get() == 0, "a scheduled stop does not run before its delay");
        sleep(400);
        check(ran.get() >= 290 && ran.get() < 600, "it runs after about its delay: " + ran.get() + " ms");

        // 2. coming back cancels it
        final AtomicInteger n = new AtomicInteger();
        SdkThread.scheduleStop(200, new Runnable() { public void run() { n.incrementAndGet(); } });
        sleep(80);
        SdkThread.cancelScheduledStop();
        sleep(400);
        check(n.get() == 0, "the screen came back: the stop never runs");

        // 3. a newer scheduled stop replaces the older one
        final AtomicReference<String> who = new AtomicReference<String>("");
        SdkThread.scheduleStop(200, new Runnable() { public void run() { who.set(who.get() + "A"); } });
        sleep(50);
        SdkThread.scheduleStop(200, new Runnable() { public void run() { who.set(who.get() + "B"); } });
        sleep(500);
        check(who.get().equals("B"), "only the newest scheduled stop runs: " + who.get());

        // 4. a start asked for while a stop is waiting is not held up by it, and tasks keep their order
        final List<String> order = Collections.synchronizedList(new ArrayList<String>());
        SdkThread.scheduleStop(600, new Runnable() { public void run() { order.add("late stop"); } });
        SdkThread.run(new Runnable() { public void run() { order.add("start 1"); } });
        SdkThread.run(new Runnable() { public void run() { order.add("start 2"); } });
        sleep(100);
        check(order.equals(Arrays.asList("start 1", "start 2")), "tasks run at once and in order while a stop is waiting: " + order);
        SdkThread.cancelScheduledStop();                      // the start coming back cancels the waiting stop
        sleep(700);
        check(!order.contains("late stop"), "and the waiting stop never ran");

        // 5. everything runs on one thread, so a stop and a start never overlap
        final Set<String> threads = Collections.synchronizedSet(new HashSet<String>());
        for (int i = 0; i < 5; i++) SdkThread.run(new Runnable() { public void run() { threads.add(Thread.currentThread().getName()); } });
        sleep(100);
        check(threads.size() == 1 && threads.contains("ax12-sdk"), "one named thread: " + threads);

        // 6. the order of a stop and the start that follows it
        final List<String> seq = Collections.synchronizedList(new ArrayList<String>());
        SdkThread.run(new Runnable() { public void run() { sleep(80); seq.add("stop"); } });
        SdkThread.run(new Runnable() { public void run() { seq.add("start"); } });
        sleep(250);
        check(seq.equals(Arrays.asList("stop", "start")), "a slow stop finishes before the next start begins: " + seq);

        // 7. failing tasks do not stop the thread
        SdkThread.run(new Runnable() { public void run() { throw new RuntimeException("boom"); } });
        final AtomicBoolean after = new AtomicBoolean();
        SdkThread.run(new Runnable() { public void run() { after.set(true); } });
        sleep(100);
        check(after.get(), "a task that fails does not stop later tasks");
        SdkThread.scheduleStop(100, new Runnable() { public void run() { throw new RuntimeException("boom in stop"); } });
        sleep(250);
        final AtomicBoolean after2 = new AtomicBoolean();
        SdkThread.run(new Runnable() { public void run() { after2.set(true); } });
        sleep(100);
        check(after2.get(), "nor does a stop that fails");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
