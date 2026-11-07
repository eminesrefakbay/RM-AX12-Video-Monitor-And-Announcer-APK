// AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
// Copyright (C) 2026 eminesrefakbay
// SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

// Core of the native bridge: talks to the RadioMaster AX SDK, keeps the newest values,
// and hands them to Kotlin. No JNI and no Android classes here, so it can be tested on a PC.
#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace ax12 {

constexpr int kChannels = 16;

// Layout of the snapshot array given to Kotlin (all values are doubles).
constexpr int kSnapChOut = 0;      // 16 values: rcGetChOutList()[i].chOut
constexpr int kSnapLogic = 16;     // 16 values: rcGetChOutList()[i].logicVal
constexpr int kSnapLq = 32;        // rcGetRadioStatus().rfLQ
constexpr int kSnapDbm = 33;       // rcGetRadioStatus().rfLDBM
constexpr int kSnapLost = 34;      // rcGetRadioStatus().rfLLostCnt
constexpr int kSnapTxVolt = 35;    // rcGetRadioStatus().rcVtg
constexpr int kSnapRxVolt = 36;    // rcGetRadioStatus().rxVtg
constexpr int kSnapTlmOk = 37;     // 1 = telemetry present, 0 = lost
constexpr int kSnapUpdates = 38;   // counts refreshes, so Kotlin can see data is moving
constexpr int kSnapState = 39;     // 0 = not started, 1 = running
constexpr int kSnapCfgReady = 40;  // 1 once the radio's configuration has been loaded (RC_MSGID_GETDEVCFG_END)
constexpr int kSnapMixoutMsgs = 41;     // how many RC_MSGID_MIXOUT_UPDATE messages arrived
constexpr int kSnapChannelListSize = 42;  // length of the list rcGetChOutList() returned last time
constexpr int kSnapFwMismatch = 43;     // 1 if the radio firmware does not match this SDK
constexpr int kSnapSize = 44;

struct Snapshot {
    double v[kSnapSize];
};

// The start-up timeline: when each step of the start happened, in milliseconds since start() began (-1 = not yet).
// Observe-only: nothing here changes what the bridge does. Kotlin shows it next to the app's own steps.
constexpr int kMaxRequests = 8;
constexpr int kTlInitReturned = 0;       // rmaxSdk::initialize() returned
constexpr int kTlCallbackSet = 1;        // the message callback was registered
constexpr int kTlFirstMessage = 2;       // the first message of any kind arrived
constexpr int kTlFirstMixout = 3;        // the first RC_MSGID_MIXOUT_UPDATE (channel data is flowing)
constexpr int kTlFwInfo = 4;             // RC_MSGID_FWINF_UPDATED (the radio answered a device info request)
constexpr int kTlCfgEnd = 5;             // RC_MSGID_GETDEVCFG_END (the radio's configuration is loaded)
constexpr int kTlFirstChannels = 6;      // rcGetChOutList() returned a non-empty list for the first time
constexpr int kTlDeviceInfoRequested = 7;  // rcTaskRequestDeviceInf() was called for the first time
constexpr int kTlRequestCount = 8;       // how many times the config was requested (the value, not a time)
constexpr int kTlRequest0 = 9;           // times of the first kMaxRequests config requests
constexpr int kTlSize = kTlRequest0 + kMaxRequests;

struct Timeline {
    double t[kTlSize];
};

/** How the SDK is started. The default is how the app has always done it. */
struct StartOptions {
    bool requestDeviceInfo = false;   // ask the radio for its device information before the config, and once more when the config is loaded (RadioMaster's demo does)
    int configRetryMs = 3000;         // how long to wait before asking for the config again
};

/** How long to wait before asking the radio for its configuration again (default 3000 ms). Mainly for tests. */
void setConfigRetryMs(int ms);

/** Starts the SDK the standard way (the retry time set by setConfigRetryMs applies). @return the SDK's InitResult as an int (0 Success, 1 AlreadyInitialized, 2 ThreadStartFailed, 3 CommunicationInitFailed). */
int start(const std::string& appDataPath);
/** Starts the SDK with the given options (they also set the retry time). */
int start(const std::string& appDataPath, const StartOptions& options);

/** Where timeline lines go (logcat in the app, nothing in the PC tests). */
using LogHook = void (*)(const char* line);
void setLogHook(LogHook hook);
Timeline timeline();
void stop();

Snapshot snapshot();

/** Pops the oldest raw CRSF frame. @return false when the queue is empty. */
bool popCrsf(std::vector<std::uint8_t>& out);
std::uint64_t droppedCrsf();

}  // namespace ax12
