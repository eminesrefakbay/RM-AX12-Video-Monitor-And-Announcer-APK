// AX-HUD antenna tracker firmware. Copyright (C) 2026 eminesrefakbay. SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file of the AX-HUD app.
// The Bluetooth LE packets between the AX-HUD app and the tracker. The same layout is in the app (TrackerProtocol.java); the file
// tracker/test/vectors.txt holds packets that both sides must read and write in the same way. Plain C++, no Arduino headers.
#pragma once
#include <stdint.h>
#include <stddef.h>

namespace trk {

static const char* const DEVICE_NAME = "AX-TRACK";
static const char* const SERVICE_UUID = "5a1d0000-7c3e-4b5a-9e1f-a0b1c2d3e4f5";
static const char* const AIRCRAFT_UUID = "5a1d0001-7c3e-4b5a-9e1f-a0b1c2d3e4f5";   // write without response, 20 bytes
static const char* const STATUS_UUID = "5a1d0002-7c3e-4b5a-9e1f-a0b1c2d3e4f5";     // notify, 20 bytes
static const char* const COMMAND_UUID = "5a1d0003-7c3e-4b5a-9e1f-a0b1c2d3e4f5";    // write

static const uint8_t VERSION = 1;
static const size_t PACKET_LEN = 20;

enum AircraftFlags { A_FIX = 1, A_ARMED = 2, A_LINK = 4, A_HEIGHT = 8 };
enum StatusFlags { S_GPS = 1, S_COMPASS = 2, S_FRESH = 4, S_TRACKING = 8, S_FLIPPED = 16, S_CALIBRATING = 32, S_LIMIT = 64 };
enum Mode { MODE_HOLD = 0, MODE_AUTO = 1, MODE_CENTRE = 2, MODE_SWEEP = 3 };
enum Command { CMD_MODE = 1, CMD_COMPASS_CAL = 2, CMD_DECLINATION = 3, CMD_HEIGHT_OFFSET = 4 };

struct Aircraft {
    uint8_t flags = 0;
    uint8_t sats = 0;
    int32_t latE7 = 0, lonE7 = 0;
    int16_t heightDm = 0;       // above the launch point, decimetres
    uint16_t ageMs = 0;         // how old the position was when the app sent it
    uint16_t speedDkmh = 0;     // 0.1 km/h
    uint16_t courseCdeg = 0;    // 0.01 degrees
};

struct Status {
    uint8_t flags = 0;
    uint8_t sats = 0;
    uint8_t mode = 0;
    int32_t latE7 = 0, lonE7 = 0;
    uint16_t headingDdeg = 0;   // 0.1 degrees
    uint16_t azimuthDdeg = 0;   // 0.1 degrees
    int16_t elevationDdeg = 0;  // 0.1 degrees
    uint16_t distance10m = 0;
};

inline uint8_t crc8(const uint8_t* b, size_t len) {
    uint8_t crc = 0;
    for (size_t i = 0; i < len; i++) {
        crc ^= b[i];
        for (int k = 0; k < 8; k++) crc = (crc & 0x80) ? (uint8_t)((crc << 1) ^ 0x07) : (uint8_t)(crc << 1);
    }
    return crc;
}

inline uint16_t rd16(const uint8_t* b) { return (uint16_t)(b[0] | (b[1] << 8)); }
inline int32_t rd32(const uint8_t* b) { return (int32_t)((uint32_t)rd16(b) | ((uint32_t)rd16(b + 2) << 16)); }
inline void wr16(uint8_t* b, uint16_t v) { b[0] = (uint8_t)v; b[1] = (uint8_t)(v >> 8); }
inline void wr32(uint8_t* b, int32_t v) { wr16(b, (uint16_t)((uint32_t)v & 0xFFFF)); wr16(b + 2, (uint16_t)((uint32_t)v >> 16)); }

// Returns false when the length, the version or the CRC is wrong.
inline bool decodeAircraft(const uint8_t* b, size_t n, Aircraft& a) {
    if (n != PACKET_LEN || b[0] != VERSION || b[19] != crc8(b, 19)) return false;
    a.flags = b[1];
    a.sats = b[2];
    a.latE7 = rd32(b + 3);
    a.lonE7 = rd32(b + 7);
    a.heightDm = (int16_t)rd16(b + 11);
    a.ageMs = rd16(b + 13);
    a.speedDkmh = rd16(b + 15);
    a.courseCdeg = rd16(b + 17);
    return true;
}

inline void encodeStatus(const Status& s, uint8_t* b) {
    b[0] = VERSION;
    b[1] = s.flags;
    b[2] = s.sats;
    b[3] = s.mode;
    wr32(b + 4, s.latE7);
    wr32(b + 8, s.lonE7);
    wr16(b + 12, s.headingDdeg);
    wr16(b + 14, s.azimuthDdeg);
    wr16(b + 16, (uint16_t)s.elevationDdeg);
    wr16(b + 18, s.distance10m);
}

inline bool decodeStatus(const uint8_t* b, size_t n, Status& s) {
    if (n != PACKET_LEN || b[0] != VERSION) return false;
    s.flags = b[1];
    s.sats = b[2];
    s.mode = b[3];
    s.latE7 = rd32(b + 4);
    s.lonE7 = rd32(b + 8);
    s.headingDdeg = rd16(b + 12);
    s.azimuthDdeg = rd16(b + 14);
    s.elevationDdeg = (int16_t)rd16(b + 16);
    s.distance10m = rd16(b + 18);
    return true;
}

}  // namespace trk
