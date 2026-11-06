package dev.ax12.announcer;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Rolling timing statistics over the last few seconds: how often something happened and how long
 * the gaps between events were. Used for video frames and for the main-thread loop.
 * Plain Java, one thread only, tested on a PC.
 */
public final class TimingStats {

    public static final class Summary {
        public final int events;
        public final double perSecond;
        public final long p95Ms;
        public final long maxMs;

        Summary(int events, double perSecond, long p95Ms, long maxMs) {
            this.events = events;
            this.perSecond = perSecond;
            this.p95Ms = p95Ms;
            this.maxMs = maxMs;
        }
    }

    private final long windowMs;
    private final ArrayDeque<long[]> samples = new ArrayDeque<long[]>();   // {time, gap}
    private long firstTime = -1;

    public TimingStats(long windowMs) {
        this.windowMs = windowMs;
    }

    /** @param gapMs time since the previous event */
    public void add(long nowMs, long gapMs) {
        if (firstTime < 0) firstTime = nowMs;
        samples.addLast(new long[]{nowMs, gapMs});
        purge(nowMs);
    }

    private void purge(long nowMs) {
        while (!samples.isEmpty() && nowMs - samples.peekFirst()[0] > windowMs) samples.removeFirst();
    }

    public Summary summary(long nowMs) {
        purge(nowMs);
        int n = samples.size();
        if (n == 0) return new Summary(0, 0.0, 0, 0);
        long[] gaps = new long[n];
        int i = 0;
        long max = 0;
        for (long[] s : samples) {
            gaps[i++] = s[1];
            if (s[1] > max) max = s[1];
        }
        Arrays.sort(gaps);
        long p95 = gaps[Math.min(n - 1, (int) Math.ceil(n * 0.95) - 1)];
        long span = Math.min(windowMs, Math.max(1, nowMs - firstTime));
        return new Summary(n, n * 1000.0 / span, p95, max);
    }
}
