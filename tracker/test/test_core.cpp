// AX-HUD antenna tracker firmware. Copyright (C) 2026 eminesrefakbay. SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file of the AX-HUD app.
// Tests of the tracker's core, on a PC:  g++ -std=c++17 -I tracker/include tracker/test/test_core.cpp -o /tmp/test_core && /tmp/test_core tracker/test/vectors.txt
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include "../include/aim.h"
#include "../include/protocol.h"

static int checks = 0, failures = 0;
#define CHECK(cond, what) do { checks++; if (!(cond)) { failures++; printf("FAIL: %s\n", what); } } while (0)
static bool near(double a, double b, double tol) { return fabs(a - b) <= tol; }

static std::vector<uint8_t> fromHex(const std::string& h) {
    std::vector<uint8_t> v;
    for (size_t i = 0; i + 1 < h.size(); i += 2) v.push_back((uint8_t)strtol(h.substr(i, 2).c_str(), nullptr, 16));
    return v;
}
static long val(const std::string& line, const char* key) {
    size_t at = line.find(std::string(" ") + key + "=");
    if (at == std::string::npos) return -999999999L;
    return atol(line.c_str() + at + strlen(key) + 2);
}

int main(int argc, char** argv) {
    using namespace trk;
    // ---------- the packets shared with the app ----------
    CHECK(crc8((const uint8_t*)"123456789", 9) == 0xF4, "CRC-8 check value of the standard test string");
    std::ifstream f(argc > 1 ? argv[1] : "tracker/test/vectors.txt");
    CHECK(f.good(), "the shared packet file is found");
    std::string line;
    int aircraftLines = 0, statusLines = 0;
    while (std::getline(f, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream in(line);
        std::string kind, hex;
        in >> kind >> hex;
        std::vector<uint8_t> b = fromHex(hex);
        if (kind == "aircraft") {
            aircraftLines++;
            Aircraft a;
            CHECK(decodeAircraft(b.data(), b.size(), a), "an aircraft packet of the app is accepted");
            CHECK(a.flags == val(line, "flags") && a.sats == val(line, "sats") && a.latE7 == val(line, "lat_e7") && a.lonE7 == val(line, "lon_e7"), "aircraft: flags, satellites, position");
            CHECK(a.heightDm == val(line, "height_dm") && a.ageMs == val(line, "age_ms") && a.speedDkmh == val(line, "speed_dkmh") && a.courseCdeg == val(line, "course_cdeg"), "aircraft: height, age, speed, course");
            std::vector<uint8_t> bad = b;
            bad[5] ^= 0x40;
            CHECK(!decodeAircraft(bad.data(), bad.size(), a), "a damaged packet is refused");
            CHECK(!decodeAircraft(b.data(), b.size() - 1, a), "a short packet is refused");
            bad = b; bad[0] = 2; bad[19] = crc8(bad.data(), 19);
            CHECK(!decodeAircraft(bad.data(), bad.size(), a), "another version is refused");
        } else if (kind == "status") {
            statusLines++;
            Status s;
            CHECK(decodeStatus(b.data(), b.size(), s), "a status packet is read");
            CHECK(s.flags == val(line, "flags") && s.sats == val(line, "sats") && s.mode == val(line, "mode") && s.latE7 == val(line, "lat_e7") && s.lonE7 == val(line, "lon_e7"), "status: flags, mode, position");
            CHECK(s.headingDdeg == val(line, "heading_ddeg") && s.azimuthDdeg == val(line, "azimuth_ddeg") && s.elevationDdeg == val(line, "elevation_ddeg") && s.distance10m == val(line, "distance_10m"), "status: heading, azimuth, elevation, distance");
            uint8_t out[PACKET_LEN];
            encodeStatus(s, out);
            CHECK(memcmp(out, b.data(), PACKET_LEN) == 0, "writing the status gives the same bytes as the app");
        }
    }
    CHECK(aircraftLines >= 2 && statusLines >= 2, "the file has aircraft and status packets");

    // ---------- distances and bearings ----------
    double d, brg;
    distanceBearing(36.0, 34.0, 36.0 + 1.0 / 111.195, 34.0, d, brg);          // about 1 km north
    CHECK(near(d, 1000.0, 2.0) && near(brg, 0.0, 0.01), "1 km north: distance and bearing");
    distanceBearing(36.0, 34.0, 36.0, 34.0 + 1.0 / (111.195 * cos(rad(36.0))), d, brg);
    CHECK(near(d, 1000.0, 2.0) && near(brg, 90.0, 0.05), "1 km east: bearing 90");
    distanceBearing(36.0, 34.0, 35.99, 34.0, d, brg);
    CHECK(near(brg, 180.0, 0.01), "south: bearing 180");
    distanceBearing(36.0, 34.0, 36.0, 33.99, d, brg);
    CHECK(near(brg, 270.0, 0.05), "west: bearing 270");
    distanceBearing(36.0, 34.0, 36.0, 34.0, d, brg);
    CHECK(d < 0.001, "the same place: distance 0");

    // ---------- elevation ----------
    Aim a = aim(36.0, 34.0, 36.0 + 1.0 / 111.195, 34.0, 100.0);
    CHECK(near(a.elevationDeg, deg(atan2(100.0, a.distanceM)), 1e-9) && near(a.elevationDeg, 5.71, 0.02), "100 m high at 1 km: 5.7 degrees");
    CHECK(aim(36.0, 34.0, 36.0, 34.0001, -50.0).elevationDeg == 0.0, "below the horizon: the tracker looks at the horizon");
    CHECK(aim(36.0, 34.0, 36.0, 34.0, 80.0).elevationDeg == 90.0, "right above: straight up");

    // ---------- dead reckoning ----------
    double la, lo;
    deadReckon(36.0, 34.0, 100.0, 0.0, 2.0, la, lo);                          // 100 km/h north for 2 s = 55.6 m
    distanceBearing(36.0, 34.0, la, lo, d, brg);
    CHECK(near(d, 55.56, 0.2) && near(brg, 0.0, 0.01), "100 km/h north for 2 s: 55.6 m north");
    deadReckon(36.0, 34.0, 2.0, 90.0, 5.0, la, lo);
    CHECK(la == 36.0 && lo == 34.0, "a very slow aircraft is not moved");
    deadReckon(36.0, 34.0, 100.0, 90.0, 60.0, la, lo);
    distanceBearing(36.0, 34.0, la, lo, d, brg);
    CHECK(near(d, 277.8, 0.5), "a very old position is moved for 10 s at most");
    deadReckon(36.0, 34.0, 100.0, 90.0, -1.0, la, lo);
    CHECK(la == 36.0 && lo == 34.0, "no backwards time");

    // ---------- the servos and the flip ----------
    ServoAngles s = servoAngles(0.0, 20.0, false);
    CHECK(s.pan == 90.0 && s.tilt == 20.0 && !s.flipped, "straight ahead: pan 90, tilt = elevation");
    s = servoAngles(60.0, 30.0, false);
    CHECK(s.pan == 150.0 && s.tilt == 30.0 && !s.flipped, "60 degrees to the right");
    s = servoAngles(-60.0, 30.0, false);
    CHECK(s.pan == 30.0 && !s.flipped, "60 degrees to the left");
    s = servoAngles(180.0, 10.0, false);
    CHECK(s.flipped && s.pan == 90.0 && s.tilt == 170.0, "straight behind: flipped, pan 90, tilt 170");
    s = servoAngles(120.0, 45.0, false);
    CHECK(s.flipped && s.pan == 30.0 && s.tilt == 135.0, "behind to the right: pan to the left, tilt past vertical");
    s = servoAngles(-120.0, 45.0, false);
    CHECK(s.flipped && s.pan == 150.0 && s.tilt == 135.0, "behind to the left");
    CHECK(!servoAngles(92.0, 10.0, false).flipped && servoAngles(92.0, 10.0, true).flipped, "hysteresis: at 92 degrees the state stays as it was");
    CHECK(servoAngles(97.0, 10.0, false).flipped && !servoAngles(83.0, 10.0, true).flipped, "and it changes beyond 95 and below 85");
    CHECK(servoAngles(95.0, 10.0, false).pan == 180.0, "just before the flip the pan stays within 180");
    // every direction is reachable: the antenna direction from the servo angles gives the wanted relative bearing back
    bool allDirections = true;
    for (int rel = -179; rel <= 180; rel += 7) {
        for (int el = 0; el <= 90; el += 15) {
            ServoAngles q = servoAngles(rel, el, false);
            if (q.pan < 0 || q.pan > 180 || q.tilt < 0 || q.tilt > 180) allDirections = false;
            double look = q.flipped ? wrap180((q.pan - 90.0) + 180.0) : (q.pan - 90.0);      // bearing the antenna really looks at
            double elev = q.flipped ? 180.0 - q.tilt : q.tilt;
            if (fabs(fabs(rel) - 95.0) > 5.0 && (!near(wrap180(look - rel), 0.0, 1e-9) || !near(elev, el, 1e-9))) allDirections = false;
        }
    }
    CHECK(allDirections, "from the servo angles the antenna looks exactly where it was told, in every direction");

    // ---------- slew and smoothing ----------
    Slew sl; sl.maxRate = 90.0;
    CHECK(sl.step(120.0, 0.02) == 120.0, "the first value is taken at once");
    CHECK(near(sl.step(30.0, 0.1), 111.0, 1e-9), "then at most 90 degrees a second: 9 degrees in 0.1 s");
    for (int i = 0; i < 20; i++) sl.step(30.0, 0.1);
    CHECK(sl.value == 30.0, "and it arrives");
    CHECK(near(smoothAngle(359.0, 1.0, 0.5), 0.0, 1e-9) && near(smoothAngle(1.0, 359.0, 0.5), 0.0, 1e-9), "a heading smooths across north without a jump");
    CHECK(near(smoothAngle(10.0, 20.0, 1.0), 20.0, 1e-9) && near(smoothAngle(10.0, 20.0, 0.0), 10.0, 1e-9), "factor 1 takes the target, 0 keeps the value");
    CHECK(pulseUs(0, 500, 2500) == 500 && pulseUs(180, 500, 2500) == 2500 && pulseUs(90, 500, 2500) == 1500 && pulseUs(-20, 500, 2500) == 500 && pulseUs(400, 500, 2500) == 2500, "angles become pulses between the end points");

    // ---------- the whole chain, an example ----------
    // the tracker faces east (heading 90); the aircraft is 1 km north of it, 100 m high: it is 90 degrees to the left of the base
    Aim t = aim(36.0, 34.0, 36.0 + 1.0 / 111.195, 34.0, 100.0);
    ServoAngles chain = servoAngles(wrap180(t.azimuthDeg - 90.0), t.elevationDeg, false);
    CHECK(chain.flipped == false && near(chain.pan, 0.0, 0.01) && near(chain.tilt, 5.71, 0.02), "facing east, aircraft to the north: pan to the left stop, tilt 5.7");

    printf("%d checks, %d failures\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
