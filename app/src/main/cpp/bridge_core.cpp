#include "bridge_core.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <deque>
#include <memory>
#include <mutex>
#include <thread>

#include <cerrno>
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

// Runs on the SDK's output thread: keep it short, copy data, never call getters here.
void onMessage(const rmaxSdk::SdkMessage& message, void* /*user*/) {
    State& s = st();
    switch (message.msgId) {
    case rmaxSdk::RC_MSGID_MIXOUT_UPDATE: {
        std::lock_guard<std::mutex> lock(s.m);
        s.dirty = true;
        ++s.mixoutMsgs;
        s.cv.notify_one();
        break;
    }
    case rmaxSdk::RC_MSGID_GETDEVCFG_END: {
        // The SDK guide: the getters are only meaningful after this message.
        std::lock_guard<std::mutex> lock(s.m);
        s.cfgReady = true;
        s.dirty = true;
        s.cv.notify_one();
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
        lock.unlock();

        // The radio may not have been reachable at start-up: keep asking until it answers.
        if (needCfg && std::chrono::steady_clock::now() - lastCfgRequest >= std::chrono::milliseconds(g_cfgRetryMs.load())) {
            lastCfgRequest = std::chrono::steady_clock::now();
            rmaxSdk::taskReadConfigFromDevice();
        }

        const auto channels = rmaxSdk::rcGetChOutList();
        const auto radio = rmaxSdk::rcGetRadioStatus();

        lock.lock();
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

int start(const std::string& appDataPath) {
    State& s = st();
    {
        std::lock_guard<std::mutex> lock(s.m);
        if (s.running) return static_cast<int>(rmaxSdk::InitResult::AlreadyInitialized);
    }
    redirectTempDir(appDataPath);
    const auto result = rmaxSdk::initialize(appDataPath);
    if (result != rmaxSdk::InitResult::Success && result != rmaxSdk::InitResult::AlreadyInitialized) {
        return static_cast<int>(result);
    }
    {
        std::lock_guard<std::mutex> lock(s.m);
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
    // Required first step after initialize: load the radio's configuration. Until
    // RC_MSGID_GETDEVCFG_END arrives the getters return empty or default values.
    rmaxSdk::taskReadConfigFromDevice();
    s.worker = std::thread(workerLoop);
    return static_cast<int>(result);
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
