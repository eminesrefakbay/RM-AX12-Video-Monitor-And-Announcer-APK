package dev.ax12.announcer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests for the start-up timeline and the log of launches. */
public class StartupTests {
    static int failures = 0, checks = 0;
    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    public static void main(String[] args) throws Exception {
        // ---------- the numbering agrees with the native header ----------
        String header = new String(Files.readAllBytes(new File("app/src/main/cpp/bridge_core.h").toPath()), StandardCharsets.UTF_8);
        Map<String, Integer> k = new HashMap<String, Integer>();
        Matcher m = Pattern.compile("constexpr int (k\\w+) = (\\d+);").matcher(header);
        while (m.find()) k.put(m.group(1), Integer.parseInt(m.group(2)));
        check(k.get("kTlInitReturned") == StartupTimeline.N_INIT && k.get("kTlCallbackSet") == StartupTimeline.N_CALLBACK
            && k.get("kTlFirstMessage") == StartupTimeline.N_FIRST_MSG && k.get("kTlFirstMixout") == StartupTimeline.N_FIRST_MIXOUT
            && k.get("kTlFwInfo") == StartupTimeline.N_FWINFO && k.get("kTlCfgEnd") == StartupTimeline.N_CFG_END
            && k.get("kTlFirstChannels") == StartupTimeline.N_CHANNELS && k.get("kTlDeviceInfoRequested") == StartupTimeline.N_DEVINFO
            && k.get("kTlRequestCount") == StartupTimeline.N_REQ_COUNT && k.get("kTlRequest0") == StartupTimeline.N_REQ0
            && k.get("kMaxRequests") == StartupTimeline.N_MAX_REQ, "the step numbers match bridge_core.h");
        check(header.contains("constexpr int kTlSize = kTlRequest0 + kMaxRequests;") && StartupTimeline.N_SIZE == k.get("kTlRequest0") + k.get("kMaxRequests"), "and the size");
        String jni = new String(Files.readAllBytes(new File("app/src/main/java/dev/ax12/announcer/AxSdkJni.kt").toPath()), StandardCharsets.UTF_8);
        check(jni.contains("nativeStart(appDataPath: String, requestDeviceInfo: Boolean, configRetryMs: Int)") && jni.contains("nativeTimeline(): DoubleArray"), "the Kotlin side declares the same native functions");
        String cpp = new String(Files.readAllBytes(new File("app/src/main/cpp/ax12bridge_jni.cpp").toPath()), StandardCharsets.UTF_8);
        check(cpp.contains("AxSdkJni_nativeStart(JNIEnv* env, jclass, jstring appDataPath, jboolean requestDeviceInfo, jint configRetryMs)") && cpp.contains("AxSdkJni_nativeTimeline"), "and the JNI file implements them with the same parameters");

        // ---------- marking ----------
        StartupTimeline t = new StartupTimeline();
        check(t.lines().isEmpty() && Double.isNaN(t.get(StartupTimeline.SDK_CALLED)), "empty at first");
        t.mark(StartupTimeline.SCREEN_RESUMED, 612);
        t.mark(StartupTimeline.SDK_CALLED, 640);
        t.mark(StartupTimeline.SCREEN_CREATED, 420);
        check(near(t.get(StartupTimeline.SDK_CALLED), 640), "a step is kept");
        t.mark(StartupTimeline.SDK_CALLED, 9999);
        check(near(t.get(StartupTimeline.SDK_CALLED), 640), "the first time wins (a second screen start does not change it)");
        t.mark(StartupTimeline.CFG_LOADED, -1);
        t.mark(StartupTimeline.CFG_LOADED, Double.NaN);
        check(Double.isNaN(t.get(StartupTimeline.CFG_LOADED)), "a time that is not known yet (-1, NaN) is ignored");
        List<String> lines = t.lines();
        check(lines.size() == 3 && lines.get(0).contains("screen created") && lines.get(1).contains("screen resumed") && lines.get(2).contains("SDK start called"), "lines come in the order the steps happened: " + lines);
        check(lines.get(0).startsWith("    420 ms") && lines.get(2).startsWith("    640 ms"), "the times are lined up: " + lines.get(0));
        t.clear();
        check(t.lines().isEmpty(), "clear");

        // ---------- the native steps join the same clock ----------
        double[] tl = new double[StartupTimeline.N_SIZE];
        java.util.Arrays.fill(tl, -1);
        tl[StartupTimeline.N_REQ_COUNT] = 0;
        t.mergeNative(tl, 1000);
        check(t.lines().isEmpty(), "nothing happened natively: nothing is added");
        tl[StartupTimeline.N_INIT] = 1200; tl[StartupTimeline.N_CALLBACK] = 1201; tl[StartupTimeline.N_DEVINFO] = 1202;
        tl[StartupTimeline.N_REQ_COUNT] = 4; tl[StartupTimeline.N_REQ0] = 1203; tl[StartupTimeline.N_REQ0 + 1] = 4210; tl[StartupTimeline.N_REQ0 + 2] = 7215; tl[StartupTimeline.N_REQ0 + 3] = 10220;
        tl[StartupTimeline.N_FIRST_MSG] = 1500; tl[StartupTimeline.N_FIRST_MIXOUT] = 1540; tl[StartupTimeline.N_FWINFO] = 9000; tl[StartupTimeline.N_CFG_END] = 10500; tl[StartupTimeline.N_CHANNELS] = 10540;
        t.mergeNative(tl, 1000);
        check(near(t.get(StartupTimeline.SDK_INIT), 2200) && near(t.get(StartupTimeline.FIRST_MIXOUT), 2540) && near(t.get(StartupTimeline.CFG_LOADED), 11500), "native times are shifted by the time the SDK began");
        check(near(t.get("config requested"), 2203) && near(t.get("config requested again #1"), 5210) && near(t.get("config requested again #3"), 11220), "the config requests are numbered");
        check(near(t.get(StartupTimeline.DEVINFO_REQ), 2202) && near(t.get(StartupTimeline.FWINFO), 10000) && near(t.get(StartupTimeline.CHANNELS), 11540), "device info steps");
        int before = t.lines().size();
        t.mergeNative(tl, 5000);
        check(t.lines().size() == before && near(t.get(StartupTimeline.SDK_INIT), 2200), "merging again (the panel refreshes often) changes nothing");
        tl[StartupTimeline.N_REQ_COUNT] = 20;                    // more requests than slots: only the recorded ones are shown
        StartupTimeline t2 = new StartupTimeline();
        for (int i = 0; i < StartupTimeline.N_MAX_REQ; i++) tl[StartupTimeline.N_REQ0 + i] = 100 + i;
        t2.mergeNative(tl, 0);
        int n = 0; for (String l : t2.lines()) if (l.contains("config requested")) n++;
        check(n == StartupTimeline.N_MAX_REQ, "no more request lines than slots (" + n + ")");
        t2.mergeNative(new double[3], 0); t2.mergeNative(null, 0);
        check(true, "a short or missing array is ignored without a crash");

        // ---------- the summary line ----------
        StartupTimeline s = new StartupTimeline();
        check(s.summary("standard").equals("standard | start - | call took - | init done - | mixout - | config - | requests 0 | graph -"), "an empty summary has dashes: " + s.summary("standard"));
        s.mark(StartupTimeline.SDK_CALLED, 640); s.mark(StartupTimeline.SDK_RETURNED, 1810);
        s.mergeNative(tl, 640);
        s.mark(StartupTimeline.FIRST_UI, 11600);
        String sum = s.summary("demo-like");
        check(sum.startsWith("demo-like | start 640 | call took 1170 | init done 1840 |") && sum.contains("| config ") && sum.endsWith("| graph 11600") && sum.contains("| requests 8 |"), "a full summary: " + sum);

        // ---------- the log of launches ----------
        String log = null;
        for (int i = 1; i <= 12; i++) log = StartupTimeline.appendLine(log, "run " + i, 10);
        String[] kept = log.trim().split("\n");
        check(kept.length == 10 && kept[0].equals("run 3") && kept[9].equals("run 12") && log.endsWith("\n"), "the log keeps the last 10 launches, newest last");
        check(StartupTimeline.appendLine("", "a", 5).equals("a\n") && StartupTimeline.appendLine("old\n\n  \n", "new", 5).equals("old\nnew\n") && StartupTimeline.appendLine("x", "y", 0).equals("y\n"), "empty lines are dropped, a tiny limit still keeps one line");

        // ---------- the start-up method is no longer a setting ----------
        check(!SettingsFile.isKnown("sdkStartup") && SettingsFile.inspect("B\tfill\ttrue\nI\tsdkStartup\t1\n").ignored == 1, "a settings file from the test build, with the old switch, is read: the switch is ignored");
        String act = new String(Files.readAllBytes(new File("app/src/main/java/dev/ax12/announcer/MainActivity.kt").toPath()), StandardCharsets.UTF_8);
        check(act.contains("override fun recreate()") && act.contains("override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?)") && act.contains("SdkThread.cancelScheduledStop()") && act.contains("App.visibility.hold("),
              "the screen asks for the radio link to be kept through its own rebuilds and the screens it opens, and cancels a waiting stop when it comes back");
        check(!act.contains("sdkStartup") && !act.contains("SdkThread.awaitIdle"), "no trace of the old switch");

        System.out.println(checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }
}
