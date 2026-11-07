// Host test for bridge_core.cpp with a FAKE SDK (no radio, no Android needed).
//   g++ -std=c++20 -pthread -I../../main/cpp -I<sdk>/include/rmAxSdk1 bridge_core_test.cpp ../../main/cpp/bridge_core.cpp -o bridge_test
#include <atomic>
#include <chrono>
#include <cstdio>
#include <functional>
#include <cstdlib>
#include <memory>
#include <sys/stat.h>
#include <mutex>
#include <thread>
#include <vector>

#include "bridge_core.h"
#include "rmAxSdkApi.h"

// ---------------------------------------------------------------- fake SDK
namespace fake {
std::mutex m;
rmaxSdk::MessageCallback callback = nullptr;
std::vector<rmaxSdk::RcChOutCfgUI> channels;
rmaxSdk::RadioStatusUI radio;
std::atomic<int> getterCalls{0};
std::atomic<bool> shutdownCalled{false};
std::atomic<int> cfgReadCalls{0};
std::atomic<int> devInfoCalls{0};
std::atomic<int> seq{0};                 // numbers the calls in the order they happen
std::atomic<int> firstDevInfoSeq{0};
std::atomic<int> firstCfgSeq{0};
std::atomic<int> logLines{0};
std::thread::id lastGetterThread;
rmaxSdk::InitResult initResult = rmaxSdk::InitResult::Success;
}  // namespace fake

namespace rmaxSdk {
std::string_view version() noexcept { return "fake"; }
InitResult initialize(std::string) noexcept { return fake::initResult; }
bool setMessageCallback(MessageCallback cb, void*) noexcept {
    std::lock_guard<std::mutex> l(fake::m);
    fake::callback = cb;
    return true;
}
void shutdown() noexcept { fake::shutdownCalled = true; }
void taskReadConfigFromDevice() noexcept {
    if (fake::cfgReadCalls++ == 0 || fake::firstCfgSeq == 0) fake::firstCfgSeq = ++fake::seq;
}
void rcTaskRequestDeviceInf() noexcept {
    if (fake::devInfoCalls++ == 0) fake::firstDevInfoSeq = ++fake::seq;
}
std::vector<RcChOutCfgUI> rcGetChOutList() noexcept {
    ++fake::getterCalls;
    std::lock_guard<std::mutex> l(fake::m);
    fake::lastGetterThread = std::this_thread::get_id();
    return fake::channels;
}
RadioStatusUI rcGetRadioStatus() noexcept {
    std::lock_guard<std::mutex> l(fake::m);
    return fake::radio;
}
}  // namespace rmaxSdk

static int failures = 0, checks = 0;
#define CHECK(cond, what) do { ++checks; if (!(cond)) { ++failures; std::printf("FAIL: %s\n", what); } } while (0)

static void send(std::uint32_t id, std::shared_ptr<const void> payload = nullptr) {
    rmaxSdk::MessageCallback cb;
    { std::lock_guard<std::mutex> l(fake::m); cb = fake::callback; }
    if (cb) { rmaxSdk::SdkMessage msg{id, payload}; cb(msg, nullptr); }
}

static bool waitFor(std::function<bool()> pred, int ms) {
    auto end = std::chrono::steady_clock::now() + std::chrono::milliseconds(ms);
    while (std::chrono::steady_clock::now() < end) { if (pred()) return true; std::this_thread::sleep_for(std::chrono::milliseconds(5)); }
    return pred();
}

int main() {
    using namespace std::chrono_literals;
    CHECK(ax12::snapshot().v[ax12::kSnapState] == 0, "not started at first");
    CHECK(ax12::timeline().t[ax12::kTlCfgEnd] == -1 && ax12::timeline().t[ax12::kTlRequestCount] == 0, "before any start the timeline says nothing happened (-1), not 0 ms");

    // failure path: the SDK cannot open its port
    fake::initResult = rmaxSdk::InitResult::CommunicationInitFailed;
    CHECK(ax12::start("/tmp") == 3, "init failure is reported");
    CHECK(ax12::snapshot().v[ax12::kSnapState] == 0, "state stays 0 after a failed start");
    fake::initResult = rmaxSdk::InitResult::Success;

    ax12::setConfigRetryMs(100);
    char tmpl[] = "/tmp/ax12testXXXXXX";
    const char* dir = mkdtemp(tmpl);
    CHECK(dir != nullptr, "temp dir for the test");
    unsetenv("TMPDIR");
    CHECK(ax12::start(dir) == 0, "start with a real data directory");
    {
        const char* t = std::getenv("TMPDIR");
        struct stat st {};
        CHECK(t != nullptr && std::string(t) == std::string(dir) + "/qt_tmp" && stat(t, &st) == 0 && S_ISDIR(st.st_mode),
              "TMPDIR points at <appData>/qt_tmp, which exists, before the SDK is initialised");
    }
    ax12::stop();
    fake::cfgReadCalls = 0;
    CHECK(ax12::start("/tmp") == 0, "start succeeds");
    CHECK(ax12::snapshot().v[ax12::kSnapState] == 1, "running");
    CHECK(fake::cfgReadCalls >= 1, "start asks the radio for its configuration (taskReadConfigFromDevice)");
    CHECK(ax12::snapshot().v[ax12::kSnapCfgReady] == 0, "config not ready before GETDEVCFG_END");
    // the radio did not answer: the bridge keeps asking
    CHECK(waitFor([] { return fake::cfgReadCalls >= 3; }, 1500), "config request is repeated until the radio answers");
    send(rmaxSdk::RC_MSGID_GETDEVCFG_END);
    CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapCfgReady] == 1; }, 1000), "GETDEVCFG_END marks the config ready");
    {
        int calls = fake::cfgReadCalls;
        std::this_thread::sleep_for(400ms);
        CHECK(fake::cfgReadCalls == calls, "no more config requests once ready");
    }
    CHECK(fake::callback != nullptr, "callback registered");

    // channel values
    {
        std::lock_guard<std::mutex> l(fake::m);
        fake::channels.assign(16, rmaxSdk::RcChOutCfgUI{});
        for (int i = 0; i < 16; ++i) { fake::channels[i].chIdx = i; fake::channels[i].chOut = i * 10 - 50; fake::channels[i].logicVal = i * 1.5f; }
        fake::radio.rfLQ = 97; fake::radio.rfLDBM = -64; fake::radio.rfLLostCnt = 3; fake::radio.rcVtg = 7.9f; fake::radio.rxVtg = 22.4f;
    }
    const auto me = std::this_thread::get_id();
    send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
    CHECK(waitFor([] { auto x = ax12::snapshot(); return x.v[ax12::kSnapChOut + 15] == 100 && x.v[ax12::kSnapLq] == 97; }, 1000),
          "snapshot refreshed after MIXOUT_UPDATE");
    auto s = ax12::snapshot();
    CHECK(s.v[ax12::kSnapChOut + 0] == -50 && s.v[ax12::kSnapChOut + 15] == 100, "channel outputs copied");
    CHECK(s.v[ax12::kSnapLogic + 4] == 6.0, "logic values copied");
    CHECK(s.v[ax12::kSnapLq] == 97 && s.v[ax12::kSnapDbm] == -64 && s.v[ax12::kSnapLost] == 3, "radio status copied");
    CHECK(s.v[ax12::kSnapTxVolt] > 7.89 && s.v[ax12::kSnapRxVolt] > 22.39, "voltages copied");
    CHECK(s.v[ax12::kSnapChannelListSize] == 16, "channel list length reported");
    CHECK(s.v[ax12::kSnapMixoutMsgs] >= 1, "mixout messages counted");
    CHECK(fake::lastGetterThread != me, "getters run on the worker thread, not the SDK callback thread");

    // getters are throttled even when the SDK floods us
    int before = fake::getterCalls;
    for (int i = 0; i < 200; ++i) { send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE); std::this_thread::sleep_for(1ms); }
    int calls = fake::getterCalls - before;
    CHECK(calls <= 12, "getter calls are throttled (about every 40 ms)");
    CHECK(calls >= 3, "values keep refreshing");

    // CRSF frames
    auto f1 = std::make_shared<const std::vector<std::uint8_t>>(std::vector<std::uint8_t>{0xC8, 4, 0x21, 'A', 0, 0x55});
    send(rmaxSdk::RC_MSGID_CRSF_DATA_RECEIVED, f1);
    std::vector<std::uint8_t> out;
    CHECK(ax12::popCrsf(out) && out == *f1, "CRSF frame passed through unchanged");
    CHECK(!ax12::popCrsf(out), "queue empty afterwards");
    for (int i = 0; i < 300; ++i) send(rmaxSdk::RC_MSGID_CRSF_DATA_RECEIVED, f1);
    int n = 0; while (ax12::popCrsf(out)) ++n;
    CHECK(n == 128, "queue is bounded to 128 frames");
    CHECK(ax12::droppedCrsf() == 300 - 128, "dropped frames are counted");
    send(rmaxSdk::RC_MSGID_CRSF_DATA_RECEIVED, nullptr);   // payload missing: must not crash
    CHECK(!ax12::popCrsf(out), "empty payload ignored");

    send(rmaxSdk::RC_MSGID_FWVERSION_NOT_MATCH);
    send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
    CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapFwMismatch] == 1; }, 1000), "firmware mismatch is reported");

    // telemetry lost / recovered
    send(rmaxSdk::RC_MSGID_TELEMETRY_LOST);
    send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
    CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapTlmOk] == 0.0; }, 1000), "telemetry lost shows as 0");
    send(rmaxSdk::RC_MSGID_TELEMETRY_RECOVER);
    send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
    CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapTlmOk] == 1.0; }, 1000), "telemetry recovered shows as 1");

    // stop and restart
    ax12::stop();
    CHECK(fake::callback == nullptr, "callback cleared on stop");
    CHECK(fake::shutdownCalled, "SDK shutdown called");
    CHECK(ax12::snapshot().v[ax12::kSnapState] == 0, "state back to 0");
    fake::shutdownCalled = false;
    int cfgBefore = fake::cfgReadCalls;
    CHECK(ax12::start("/tmp") == 0, "restart works");
    CHECK(fake::cfgReadCalls > cfgBefore, "restart asks for the config again");
    CHECK(ax12::snapshot().v[ax12::kSnapFwMismatch] == 0, "restart clears the old firmware flag");
    send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
    CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapUpdates] >= 1; }, 1000), "values flow again");
    ax12::stop();
    ax12::stop();   // a second stop must be harmless

    // ============================================================ start options and the start-up timeline
    {
        auto reset = [] {
            fake::cfgReadCalls = 0; fake::devInfoCalls = 0; fake::seq = 0; fake::firstDevInfoSeq = 0; fake::firstCfgSeq = 0;
            std::lock_guard<std::mutex> l(fake::m);
            fake::channels.clear();
        };
        // before any start the timeline says "nothing yet", not "0 ms"
        // (checked on a fresh process by the first start below)

        // --- the standard start does not ask for device information, and retries after the time set
        reset();
        ax12::StartOptions std3;                       // the defaults: as the app has always done it
        std3.configRetryMs = 400;
        CHECK(!std3.requestDeviceInfo && ax12::StartOptions{}.configRetryMs == 3000, "defaults: no device info request, 3000 ms retry");
        CHECK(ax12::start("/tmp", std3) == 0, "start with the standard options");
        CHECK(fake::devInfoCalls == 0, "standard: the device information is not requested");
        std::this_thread::sleep_for(250ms);
        CHECK(fake::cfgReadCalls == 1, "standard: one config request, no retry before the retry time");
        CHECK(waitFor([] { return fake::cfgReadCalls >= 2; }, 1500), "standard: retried after the retry time");
        send(rmaxSdk::RC_MSGID_GETDEVCFG_END);
        CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapCfgReady] == 1; }, 1000), "config ready");
        std::this_thread::sleep_for(200ms);
        CHECK(fake::devInfoCalls == 0, "standard: never asks for the device information, not even when the config is loaded");
        ax12::stop();

        // --- the demo-like start: device information first, then the config, and once more when the config is loaded
        reset();
        ax12::StartOptions demo;
        demo.requestDeviceInfo = true;
        demo.configRetryMs = 600;
        CHECK(ax12::start("/tmp", demo) == 0, "start with the demo-like options");
        CHECK(fake::devInfoCalls >= 1 && fake::cfgReadCalls >= 1, "demo-like: both requests are made at start");
        CHECK(fake::firstDevInfoSeq > 0 && fake::firstDevInfoSeq < fake::firstCfgSeq, "demo-like: the device information is requested BEFORE the config");
        int devAtStart = fake::devInfoCalls;
        CHECK(devAtStart == 1, "demo-like: one device info request at start");
        std::this_thread::sleep_for(300ms);
        CHECK(fake::cfgReadCalls == 1, "demo-like: the longer retry time is respected (no retry after 300 ms of 600)");
        send(rmaxSdk::RC_MSGID_GETDEVCFG_END);
        CHECK(waitFor([] { return fake::devInfoCalls == 2; }, 1500), "demo-like: the device information is requested once more when the config is loaded");
        std::this_thread::sleep_for(300ms);
        send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
        std::this_thread::sleep_for(200ms);
        CHECK(fake::devInfoCalls == 2, "demo-like: and only once more, however many messages follow");
        ax12::stop();

        // --- the timeline
        reset();
        ax12::setLogHook([](const char* line) { if (line != nullptr && line[0] != 0) ++fake::logLines; });
        fake::logLines = 0;
        ax12::StartOptions tl;
        tl.requestDeviceInfo = true;
        tl.configRetryMs = 150;
        CHECK(ax12::start("/tmp", tl) == 0, "start for the timeline");
        auto t = ax12::timeline();
        CHECK(t.t[ax12::kTlInitReturned] >= 0 && t.t[ax12::kTlCallbackSet] >= t.t[ax12::kTlInitReturned], "init returned, then the callback was set");
        CHECK(t.t[ax12::kTlDeviceInfoRequested] >= t.t[ax12::kTlCallbackSet], "device info requested after the callback was set");
        CHECK(t.t[ax12::kTlRequest0] >= t.t[ax12::kTlDeviceInfoRequested] && t.t[ax12::kTlRequestCount] == 1, "the first config request comes after the device info request");
        CHECK(t.t[ax12::kTlFirstMessage] == -1 && t.t[ax12::kTlFirstMixout] == -1 && t.t[ax12::kTlCfgEnd] == -1 && t.t[ax12::kTlFirstChannels] == -1 && t.t[ax12::kTlFwInfo] == -1,
              "nothing from the radio yet: those steps are -1");
        CHECK(waitFor([] { return ax12::timeline().t[ax12::kTlRequestCount] >= 3; }, 2000), "each retry is counted");
        t = ax12::timeline();
        CHECK(t.t[ax12::kTlRequest0 + 1] > t.t[ax12::kTlRequest0] && t.t[ax12::kTlRequest0 + 2] > t.t[ax12::kTlRequest0 + 1], "retry times increase");
        CHECK(t.t[ax12::kTlRequest0 + 1] - t.t[ax12::kTlRequest0] >= 140, "retries are about the retry time apart");
        send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
        t = ax12::timeline();
        const double mix = t.t[ax12::kTlFirstMixout];
        CHECK(mix >= t.t[ax12::kTlCallbackSet] && t.t[ax12::kTlFirstMessage] >= 0 && t.t[ax12::kTlFirstMessage] <= mix, "first message, then first mixout");
        std::this_thread::sleep_for(30ms);
        send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
        CHECK(ax12::timeline().t[ax12::kTlFirstMixout] == mix, "a step keeps the time of the FIRST time");
        send(rmaxSdk::RC_MSGID_FWINF_UPDATED);
        CHECK(ax12::timeline().t[ax12::kTlFwInfo] >= mix, "device info answered is recorded");
        send(rmaxSdk::RC_MSGID_GETDEVCFG_END);
        CHECK(ax12::timeline().t[ax12::kTlCfgEnd] >= mix, "config loaded is recorded");
        {
            std::lock_guard<std::mutex> l(fake::m);
            fake::channels.assign(16, rmaxSdk::RcChOutCfgUI{});
        }
        send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
        CHECK(waitFor([] { return ax12::timeline().t[ax12::kTlFirstChannels] >= 0; }, 1000), "the first channel list is recorded");
        t = ax12::timeline();
        CHECK(t.t[ax12::kTlFirstChannels] >= t.t[ax12::kTlCfgEnd], "channel list after the config");
        CHECK(fake::logLines >= 8, "every step also went to the log hook");
        const double requests = t.t[ax12::kTlRequestCount];
        std::this_thread::sleep_for(400ms);
        CHECK(ax12::timeline().t[ax12::kTlRequestCount] == requests, "no more requests once the config is loaded");
        ax12::stop();
        CHECK(ax12::timeline().t[ax12::kTlCfgEnd] >= 0, "the timeline stays readable after stop");
        // a new start begins a new timeline
        reset();
        CHECK(ax12::start("/tmp", std3) == 0, "start again");
        t = ax12::timeline();
        CHECK(t.t[ax12::kTlCfgEnd] == -1 && t.t[ax12::kTlFirstMixout] == -1 && t.t[ax12::kTlDeviceInfoRequested] == -1 && t.t[ax12::kTlRequestCount] == 1, "a new start clears the old timeline");
        CHECK(t.t[ax12::kTlRequest0 + 1] == -1, "unused request slots are -1");
        ax12::setLogHook(nullptr);
        ax12::stop();
    }

    std::printf("%d checks, %d failures\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
