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
constexpr int kSnapSize = 40;

struct Snapshot {
    double v[kSnapSize];
};

/** Starts the SDK. @return the SDK's InitResult as an int (0 Success, 1 AlreadyInitialized, 2 ThreadStartFailed, 3 CommunicationInitFailed). */
int start(const std::string& appDataPath);
void stop();

Snapshot snapshot();

/** Pops the oldest raw CRSF frame. @return false when the queue is empty. */
bool popCrsf(std::vector<std::uint8_t>& out);
std::uint64_t droppedCrsf();

}  // namespace ax12
