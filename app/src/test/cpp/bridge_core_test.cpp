// Host test for bridge_core.cpp with a FAKE SDK (no radio, no Android needed).
//   g++ -std=c++20 -pthread -I../../main/cpp -I<sdk>/include/rmAxSdk1 bridge_core_test.cpp ../../main/cpp/bridge_core.cpp -o bridge_test
#include <atomic>
#include <chrono>
#include <cstdio>
#include <functional>
#include <memory>
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

    // failure path: the SDK cannot open its port
    fake::initResult = rmaxSdk::InitResult::CommunicationInitFailed;
    CHECK(ax12::start("/tmp") == 3, "init failure is reported");
    CHECK(ax12::snapshot().v[ax12::kSnapState] == 0, "state stays 0 after a failed start");
    fake::initResult = rmaxSdk::InitResult::Success;

    CHECK(ax12::start("/tmp") == 0, "start succeeds");
    CHECK(ax12::snapshot().v[ax12::kSnapState] == 1, "running");
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
    CHECK(ax12::start("/tmp") == 0, "restart works");
    send(rmaxSdk::RC_MSGID_MIXOUT_UPDATE);
    CHECK(waitFor([] { return ax12::snapshot().v[ax12::kSnapUpdates] >= 1; }, 1000), "values flow again");
    ax12::stop();
    ax12::stop();   // a second stop must be harmless

    std::printf("%d checks, %d failures\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
