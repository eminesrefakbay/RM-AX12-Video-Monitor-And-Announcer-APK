// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

package dev.ax12.announcer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The record the app keeps when it stops unexpectedly: when, in which thread, and the stack trace, cut to a sensible size. */
public final class CrashFile {
    private CrashFile() {}

    public static final int MAX_FRAMES = 30;
    public static final int MAX_CAUSE_FRAMES = 10;
    public static final int MAX_CAUSES = 3;
    public static final int MAX_CHARS = 5000;

    public static String format(long wallMs, String thread, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(wallMs))).append('\n');
        sb.append("thread: ").append(DiagLog.clean(thread, 60)).append('\n');
        Throwable cur = t;
        for (int depth = 0; cur != null && depth <= MAX_CAUSES; depth++) {
            sb.append(depth == 0 ? "" : "caused by: ").append(cur.getClass().getName());
            String msg = cur.getMessage();
            if (msg != null && !msg.isEmpty()) sb.append(": ").append(DiagLog.clean(msg, 200));
            sb.append('\n');
            StackTraceElement[] st = cur.getStackTrace();
            int limit = depth == 0 ? MAX_FRAMES : MAX_CAUSE_FRAMES;
            for (int i = 0; i < st.length && i < limit; i++) sb.append("  at ").append(st[i]).append('\n');
            if (st.length > limit) sb.append("  ... ").append(st.length - limit).append(" more\n");
            Throwable next = cur.getCause();
            cur = next == cur ? null : next;
        }
        String out = Privacy.redact(sb.toString());
        return out.length() <= MAX_CHARS ? out : out.substring(0, MAX_CHARS - 20) + "\n... (cut)\n";
    }
}
