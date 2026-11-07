// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

#include "bridge_core.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <memory>
#include <mutex>
#include <thread>

#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <sys/stat.h>

#include "rmAxSdkApi.h"

namespace ax12 {
namespace {

struct State {
    std::mutex m;
    std::condition_variable cv;
    bool running = false;
    bool dirty = false;
    bool tlmOk = true;
    bool cfgReady = false;
    bool fwMismatch = false;
    std::uint64_t mixoutMsgs = 0;
    std::uint64_t updates = 0;
    std::uint64_t droppedFrames = 0;
    std::deque<std::vector<std::uint8_t>> crsf;
    Snapshot snap{};
    std::thread worker;
    // the start-up timeline
    std::chrono::steady_clock::time_point t0 = std::chrono::steady_clock::now();
    Timeline tl{};
    bool deviceInfoAfterCfg = false;     // ask for the device information once more when the config is loaded

    State() {
        for (double& x : tl.t) x = -1;     // nothing has happened yet
        tl.t[kTlRequestCount] = 0;
    }
};

State& st() {
    static State s;
    return s;
}

// Qt's QSerialPort wants to create "LCK..<port>" lock files under QDir::tempPath(), which reads the
// TMPDIR environment variable. Android's default temp location is not writable for an app, so
// point TMPDIR at a folder inside the app's private data (RadioMaster's demo does the same, and
// it must happen BEFORE initialize(), which creates the Qt application object).
void redirectTempDir(const std::string& appDataPath) {
    const std::string tmp = appDataPath + "/qt_tmp";
    if (mkdir(tmp.c_str(), 0700) != 0 && errno != EEXIST) return;
    setenv("TMPDIR", tmp.c_str(), 1);
}

constexpr std::size_t kMaxQueuedFrames = 128;
std::atomic<int> g_cfgRetryMs{3000};
std::atomic<LogHook> g_logHook{nullptr};

double msSince(std::chrono::steady_clock::time_point t0) {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
}

void logLine(double ms, const char* what) {
    const LogHook hook = g_logHook.load();
    if (hook == nullptr) return;
    char buf[160];
    std::snprintf(buf, sizeof(buf), "%.0f ms: %s", ms, what);
    hook(buf);
}

void resetTimeline(State& s) {
    for (double& x : s.tl.t) x = -1;
    s.tl.t[kTlRequestCount] = 0;
}

// Records a step the first time it happens (the caller holds the lock).
void markFirst(State& s, int index, const char* what) {
    if (s.tl.t[index] >= 0) return;
    s.tl.t[index] = msSince(s.t0);
    logLine(s.tl.t[index], what);
}

// Records one more config request (the caller holds the lock).
void markRequest(State& s) {
    const int n = static_cast<int>(s.tl.t[kTlRequestCount]);
    const double ms = msSince(s.t0);
    if (n < kMaxRequests) s.tl.t[kTlRequest0 + n] = ms;
    s.tl.t[kTlRequestCount] = n + 1;
    logLine(ms, n == 0 ? "config requested" : "config requested again");
}

// Runs on the SDK's output thread: keep it short, copy data, never call getters here.
void onMessage(const rmaxSdk::SdkMessage& message, void* /*user*/) {
    State& s = st();
    {
        std::lock_guard<std::mutex> lock(s.m);
        markFirst(s, kTlFirstMessage, "first SDK message");
    }
    switch (message.msgId) {
    case rmaxSdk::RC_MSGID_MIXOUT_UPDATE: {
        std::lock_guard<std::mutex> lock(s.m);
        markFirst(s, kTlFirstMixout, "first channel message (mixout)");
        s.dirty = true;
        ++s.mixoutMsgs;
        s.cv.notify_one();
        break;
    }
    case rmaxSdk::RC_MSGID_GETDEVCFG_END: {
        // The SDK guide: the getters are only meaningful after this message.
        std::lock_guard<std::mutex> lock(s.m);
        markFirst(s, kTlCfgEnd, "config loaded (GETDEVCFG_END)");
        s.cfgReady = true;
        s.dirty = true;
        s.cv.notify_one();
        break;
    }
    case rmaxSdk::RC_MSGID_FWINF_UPDATED: {
        std::lock_guard<std::mutex> lock(s.m);
        markFirst(s, kTlFwInfo, "device info answered");
        break;
    }
    case rmaxSdk::RC_MSGID_FWVERSION_NOT_MATCH: {
        std::lock_guard<std::mutex> lock(s.m);
        s.fwMismatch = true;
        break;
    }
    case rmaxSdk::RC_MSGID_CRSF_DATA_RECEIVED: {
        if (!message.payload) break;
        const auto frame = std::static_pointer_cast<const std::vector<std::uint8_t>>(message.payload);
        std::vector<std::uint8_t> copy(*frame);
        std::lock_guard<std::mutex> lock(s.m);
        s.crsf.push_back(std::move(copy));
        while (s.crsf.size() > kMaxQueuedFrames) {
            s.crsf.pop_front();
            ++s.droppedFrames;
        }
        break;
    }
    case rmaxSdk::RC_MSGID_TELEMETRY_LOST: {
        std::lock_guard<std::mutex> lock(s.m);
        s.tlmOk = false;
        break;
    }
    case rmaxSdk::RC_MSGID_TELEMETRY_RECOVER: {
        std::lock_guard<std::mutex> lock(s.m);
        s.tlmOk = true;
        break;
    }
    default:
        break;
    }
}

// Reads the getters on our own thread, at most every 40 ms, after the SDK says values changed
// (the SDK guide: do not poll the live getters in a tight loop).
void workerLoop() {
    using namespace std::chrono_literals;
    State& s = st();
    auto lastCfgRequest = std::chrono::steady_clock::now();
    std::unique_lock<std::mutex> lock(s.m);
    while (s.running) {
        s.cv.wait_for(lock, 250ms, [&] { return !s.running || s.dirty; });
        if (!s.running) break;
        s.dirty = false;
        const bool needCfg = !s.cfgReady;
        const bool needDeviceInfo = s.cfgReady && s.deviceInfoAfterCfg;
        if (needDeviceInfo) s.deviceInfoAfterCfg = false;           // once
        lock.unlock();

        // The radio may not have been reachable at start-up: keep asking until it answers.
        if (needCfg && std::chrono::steady_clock::now() - lastCfgRequest >= std::chrono::milliseconds(g_cfgRetryMs.load())) {
            lastCfgRequest = std::chrono::steady_clock::now();
            rmaxSdk::taskReadConfigFromDevice();
            std::lock_guard<std::mutex> l(s.m);
            markRequest(s);
        }
        // RadioMaster's demo asks for the device information once the configuration is loaded.
        if (needDeviceInfo) rmaxSdk::rcTaskRequestDeviceInf();

        const auto channels = rmaxSdk::rcGetChOutList();
        const auto radio = rmaxSdk::rcGetRadioStatus();

        lock.lock();
        if (!channels.empty()) markFirst(s, kTlFirstChannels, "channel list available");
        for (int i = 0; i < kChannels; ++i) {
            if (i < static_cast<int>(channels.size())) {
                s.snap.v[kSnapChOut + i] = static_cast<double>(channels[i].chOut);
                s.snap.v[kSnapLogic + i] = static_cast<double>(channels[i].logicVal);
            }
        }
        s.snap.v[kSnapLq] = radio.rfLQ;
        s.snap.v[kSnapDbm] = radio.rfLDBM;
        s.snap.v[kSnapLost] = radio.rfLLostCnt;
        s.snap.v[kSnapTxVolt] = radio.rcVtg;
        s.snap.v[kSnapRxVolt] = radio.rxVtg;
        s.snap.v[kSnapTlmOk] = s.tlmOk ? 1.0 : 0.0;
        s.snap.v[kSnapUpdates] = static_cast<double>(++s.updates);
        s.snap.v[kSnapCfgReady] = s.cfgReady ? 1.0 : 0.0;
        s.snap.v[kSnapMixoutMsgs] = static_cast<double>(s.mixoutMsgs);
        s.snap.v[kSnapChannelListSize] = static_cast<double>(channels.size());
        s.snap.v[kSnapFwMismatch] = s.fwMismatch ? 1.0 : 0.0;

        lock.unlock();
        std::this_thread::sleep_for(40ms);
        lock.lock();
    }
}

}  // namespace

void setConfigRetryMs(int ms) { g_cfgRetryMs = ms; }
void setLogHook(LogHook hook) { g_logHook = hook; }

Timeline timeline() {
    State& s = st();
    std::lock_guard<std::mutex> lock(s.m);
    return s.tl;
}

namespace {
int startWith(const std::string& appDataPath, bool requestDeviceInfo) {
    State& s = st();
    {
        std::lock_guard<std::mutex> lock(s.m);
        if (s.running) return static_cast<int>(rmaxSdk::InitResult::AlreadyInitialized);
    }
    const auto t0 = std::chrono::steady_clock::now();
    redirectTempDir(appDataPath);
    const auto result = rmaxSdk::initialize(appDataPath);
    const double initMs = msSince(t0);
    if (result != rmaxSdk::InitResult::Success && result != rmaxSdk::InitResult::AlreadyInitialized) {
        return static_cast<int>(result);
    }
    {
        std::lock_guard<std::mutex> lock(s.m);
        s.t0 = t0;
        resetTimeline(s);
        s.tl.t[kTlInitReturned] = initMs;
        logLine(initMs, "SDK initialize() returned");
        s.deviceInfoAfterCfg = requestDeviceInfo;
        s.running = true;
        s.dirty = true;
        s.tlmOk = true;
        s.cfgReady = false;
        s.fwMismatch = false;
        s.mixoutMsgs = 0;
        s.snap = Snapshot{};
        s.snap.v[kSnapState] = 1;
        s.crsf.clear();
    }
    rmaxSdk::setMessageCallback(&onMessage, nullptr);
    {
        std::lock_guard<std::mutex> lock(s.m);
        markFirst(s, kTlCallbackSet, "message callback set");
    }
    // RadioMaster's demo asks for the device information first (the standard start does not).
    if (requestDeviceInfo) {
        rmaxSdk::rcTaskRequestDeviceInf();
        std::lock_guard<std::mutex> lock(s.m);
        markFirst(s, kTlDeviceInfoRequested, "device info requested");
    }
    // Required first step after initialize: load the radio's configuration. Until
    // RC_MSGID_GETDEVCFG_END arrives the getters return empty or default values.
    rmaxSdk::taskReadConfigFromDevice();
    {
        std::lock_guard<std::mutex> lock(s.m);
        markRequest(s);
    }
    s.worker = std::thread(workerLoop);
    return static_cast<int>(result);
}
}  // namespace

int start(const std::string& appDataPath) { return startWith(appDataPath, false); }

int start(const std::string& appDataPath, const StartOptions& options) {
    g_cfgRetryMs = options.configRetryMs;
    return startWith(appDataPath, options.requestDeviceInfo);
}

void stop() {
    State& s = st();
    {
        std::lock_guard<std::mutex> lock(s.m);
        if (!s.running) return;
        s.running = false;
        s.cv.notify_all();
    }
    rmaxSdk::setMessageCallback(nullptr, nullptr);
    if (s.worker.joinable()) s.worker.join();
    rmaxSdk::shutdown();
    std::lock_guard<std::mutex> lock(s.m);
    s.snap.v[kSnapState] = 0;
}

Snapshot snapshot() {
    State& s = st();
    std::lock_guard<std::mutex> lock(s.m);
    return s.snap;
}

bool popCrsf(std::vector<std::uint8_t>& out) {
    State& s = st();
    std::lock_guard<std::mutex> lock(s.m);
    if (s.crsf.empty()) return false;
    out = std::move(s.crsf.front());
    s.crsf.pop_front();
    return true;
}

std::uint64_t droppedCrsf() {
    State& s = st();
    std::lock_guard<std::mutex> lock(s.m);
    return s.droppedFrames;
}

}  // namespace ax12
