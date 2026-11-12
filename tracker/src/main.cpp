// AX-HUD antenna tracker firmware. Copyright (C) 2026 eminesrefakbay. SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file of the AX-HUD app.
// The AX-HUD antenna tracker. The app (AX-HUD on the radio) sends the aircraft's state over Bluetooth LE; this firmware knows its own
// position (GPS) and the direction its base faces (compass), works out where the aircraft is and points two servos at it. All the geometry
// is in include/aim.h and the packets in include/protocol.h, both tested on a PC (tracker/test).
#include <Arduino.h>
#include <Wire.h>
#include <Preferences.h>
#include <TinyGPSPlus.h>
#include <NimBLEDevice.h>
#include "config.h"
#include "aim.h"
#include "protocol.h"

using namespace trk;

// ---------------------------------------------------------------- state
static TinyGPSPlus gps;
static Preferences prefs;

static portMUX_TYPE lockMux = portMUX_INITIALIZER_UNLOCKED;
static Aircraft airPacket;                 // the newest valid aircraft packet (written by the Bluetooth task)
static uint32_t airRxAtMs = 0;
static volatile bool airHave = false;
static volatile uint8_t pendingMode = 255; // a mode command waiting for the main loop
static volatile uint8_t pendingCal = 255;  // 1 start, 0 stop
static volatile bool pendingDecl = false, pendingHeight = false;
static volatile int16_t newDeclDdeg = 0, newHeightDm = 0;

static NimBLEServer* bleServer = nullptr;
static NimBLECharacteristic* statusChar = nullptr;

static uint8_t mode = MODE_AUTO;
static uint32_t modeSince = 0;
static double trackerLat = 0, trackerLon = 0;
static bool trackerFix = false;
static uint8_t trackerSats = 0;

// compass
static bool compassOk = false, compassCalibrated = false, calibrating = false;
static float offX = 0, offY = 0, offZ = 0, scaleX = 1, scaleY = 1, scaleZ = 1;
static float minX, maxX, minY, maxY, minZ, maxZ;
static double heading = 0;                 // degrees true, smoothed
static bool headingStarted = false;
static float declinationDeg = 0;           // degrees east
static float trackerHeightM = 0;           // the antennas above the launch point

// pointing
static Slew panSlew, tiltSlew;
static bool flipped = false;
static double lastAzimuth = 0;
static Aim lastAim;
static bool tracking = false, limitHit = false;

static const int PAN_CH = 0, TILT_CH = 1;

// ---------------------------------------------------------------- servos
static void writeServo(int channel, int pin, double angle, int minUs, int maxUs, double trim, int invert) {
    (void)pin;
    double a = invert ? 180.0 - angle : angle;
    int us = pulseUs(a + trim, minUs, maxUs);
    ledcWrite(channel, (uint32_t)((uint64_t)us * 65535ULL / 20000ULL));
}

static void servosInit() {
    ledcSetup(PAN_CH, 50, 16);
    ledcAttachPin(PIN_PAN_SERVO, PAN_CH);
    ledcSetup(TILT_CH, 50, 16);
    ledcAttachPin(PIN_TILT_SERVO, TILT_CH);
    panSlew.maxRate = SLEW_DEG_PER_S;
    tiltSlew.maxRate = SLEW_DEG_PER_S;
}

static void servosMove(double pan, double tilt, double dt) {
    double p = panSlew.step(pan, dt), t = tiltSlew.step(tilt, dt);
    writeServo(PAN_CH, PIN_PAN_SERVO, p, PAN_MIN_US, PAN_MAX_US, PAN_TRIM_DEG, PAN_INVERT);
    writeServo(TILT_CH, PIN_TILT_SERVO, t, TILT_MIN_US, TILT_MAX_US, TILT_TRIM_DEG, TILT_INVERT);
}

// ---------------------------------------------------------------- compass
static bool i2cWrite(uint8_t addr, uint8_t reg, uint8_t value) {
    Wire.beginTransmission(addr);
    Wire.write(reg);
    Wire.write(value);
    return Wire.endTransmission() == 0;
}

static bool i2cRead(uint8_t addr, uint8_t reg, uint8_t* buf, uint8_t n) {
    Wire.beginTransmission(addr);
    Wire.write(reg);
    if (Wire.endTransmission(false) != 0) return false;
    if (Wire.requestFrom((int)addr, (int)n) != n) return false;
    for (uint8_t i = 0; i < n; i++) buf[i] = Wire.read();
    return true;
}

static bool compassInit() {
#if COMPASS_TYPE == 0
    bool ok = i2cWrite(0x0D, 0x0B, 0x01);            // set/reset period
    ok = ok && i2cWrite(0x0D, 0x09, 0x1D);           // continuous, 200 Hz, 8 gauss, oversampling 512
    return ok;
#else
    bool ok = i2cWrite(0x1E, 0x00, 0x70);            // 8 samples averaged, 15 Hz
    ok = ok && i2cWrite(0x1E, 0x01, 0x20);           // gain 1.3 gauss
    ok = ok && i2cWrite(0x1E, 0x02, 0x00);           // continuous
    return ok;
#endif
}

static bool compassRead(float& x, float& y, float& z) {
    uint8_t b[6];
#if COMPASS_TYPE == 0
    if (!i2cRead(0x0D, 0x00, b, 6)) return false;
    x = (int16_t)(b[0] | (b[1] << 8));
    y = (int16_t)(b[2] | (b[3] << 8));
    z = (int16_t)(b[4] | (b[5] << 8));
#else
    if (!i2cRead(0x1E, 0x03, b, 6)) return false;
    x = (int16_t)((b[0] << 8) | b[1]);
    z = (int16_t)((b[2] << 8) | b[3]);
    y = (int16_t)((b[4] << 8) | b[5]);
#endif
    return true;
}

static void loadSettings() {
    prefs.begin("trk", false);
    compassCalibrated = prefs.getBool("cal", false);
    offX = prefs.getFloat("ox", 0); offY = prefs.getFloat("oy", 0); offZ = prefs.getFloat("oz", 0);
    scaleX = prefs.getFloat("sx", 1); scaleY = prefs.getFloat("sy", 1); scaleZ = prefs.getFloat("sz", 1);
    declinationDeg = prefs.getShort("decl", 55) / 10.0f;       // Mersin is about +5.5 degrees east
    trackerHeightM = prefs.getShort("hgt", 15) / 10.0f;
}

static void startCalibration() {
    calibrating = true;
    minX = minY = minZ = 1e9f;
    maxX = maxY = maxZ = -1e9f;
    Serial.println("compass calibration: turn the whole tracker base slowly through a full circle, then tilt it each way a little");
}

static void stopCalibration() {
    calibrating = false;
    float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
    if (dx < 50 || dy < 50) { Serial.println("compass calibration: not enough movement, nothing saved"); return; }
    offX = (maxX + minX) / 2; offY = (maxY + minY) / 2; offZ = (maxZ + minZ) / 2;
    float avg = (dx + dy + (dz > 50 ? dz : (dx + dy) / 2)) / 3;
    scaleX = avg / dx; scaleY = avg / dy; scaleZ = dz > 50 ? avg / dz : 1;
    compassCalibrated = true;
    prefs.putBool("cal", true);
    prefs.putFloat("ox", offX); prefs.putFloat("oy", offY); prefs.putFloat("oz", offZ);
    prefs.putFloat("sx", scaleX); prefs.putFloat("sy", scaleY); prefs.putFloat("sz", scaleZ);
    Serial.println("compass calibration: saved");
}

static void compassStep() {
    float x, y, z;
    if (!compassRead(x, y, z)) { compassOk = false; return; }
    compassOk = true;
    if (calibrating) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        if (z < minZ) minZ = z;
        if (z > maxZ) maxZ = z;
    }
    double cx = (x - offX) * scaleX, cy = (y - offY) * scaleY;
    double h = wrap360(HEADING_SIGN * deg(atan2(cy, cx)) + COMPASS_OFFSET_DEG + declinationDeg);
    if (!headingStarted) { heading = h; headingStarted = true; }
    else heading = smoothAngle(heading, h, 0.15);
}

// ---------------------------------------------------------------- Bluetooth
class AircraftCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c) override {
        std::string v = c->getValue();
        Aircraft a;
        if (!decodeAircraft((const uint8_t*)v.data(), v.size(), a)) return;
        portENTER_CRITICAL(&lockMux);
        airPacket = a;
        airRxAtMs = millis();
        airHave = true;
        portEXIT_CRITICAL(&lockMux);
    }
};

class CommandCallbacks : public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic* c) override {
        std::string v = c->getValue();
        if (v.size() < 2) return;
        const uint8_t* b = (const uint8_t*)v.data();
        switch (b[0]) {
            case CMD_MODE: if (b[1] <= MODE_SWEEP) pendingMode = b[1]; break;
            case CMD_COMPASS_CAL: pendingCal = b[1] ? 1 : 0; break;
            case CMD_DECLINATION: if (v.size() >= 3) { newDeclDdeg = (int16_t)rd16(b + 1); pendingDecl = true; } break;
            case CMD_HEIGHT_OFFSET: if (v.size() >= 3) { newHeightDm = (int16_t)rd16(b + 1); pendingHeight = true; } break;
            default: break;
        }
    }
};

static AircraftCallbacks aircraftCallbacks;
static CommandCallbacks commandCallbacks;

static void bleInit() {
    NimBLEDevice::init(DEVICE_NAME);
    bleServer = NimBLEDevice::createServer();
    NimBLEService* svc = bleServer->createService(SERVICE_UUID);
    NimBLECharacteristic* air = svc->createCharacteristic(AIRCRAFT_UUID, NIMBLE_PROPERTY::WRITE_NR);
    air->setCallbacks(&aircraftCallbacks);
    statusChar = svc->createCharacteristic(STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
    NimBLECharacteristic* cmd = svc->createCharacteristic(COMMAND_UUID, NIMBLE_PROPERTY::WRITE);
    cmd->setCallbacks(&commandCallbacks);
    svc->start();
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    adv->addServiceUUID(SERVICE_UUID);
    adv->setScanResponse(true);
    adv->start();
}

static void bleKeepAdvertising() {
    NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
    if (bleServer->getConnectedCount() == 0 && !adv->isAdvertising()) adv->start();
}

static void sendStatus(uint32_t now, bool fresh) {
    Status s;
    s.flags = (trackerFix ? S_GPS : 0) | ((compassOk && compassCalibrated) ? S_COMPASS : 0) | (fresh ? S_FRESH : 0) | (tracking ? S_TRACKING : 0)
        | (flipped ? S_FLIPPED : 0) | (calibrating ? S_CALIBRATING : 0) | (limitHit ? S_LIMIT : 0);
    s.sats = trackerSats;
    s.mode = mode;
    s.latE7 = (int32_t)llround(trackerLat * 1e7);
    s.lonE7 = (int32_t)llround(trackerLon * 1e7);
    s.headingDdeg = (uint16_t)clampd(llround(heading * 10.0), 0, 3599);
    s.azimuthDdeg = (uint16_t)clampd(llround(wrap360(lastAim.azimuthDeg) * 10.0), 0, 3599);
    s.elevationDdeg = (int16_t)llround(lastAim.elevationDeg * 10.0);
    s.distance10m = (uint16_t)clampd(llround(lastAim.distanceM / 10.0), 0, 65535);
    uint8_t buf[PACKET_LEN];
    encodeStatus(s, buf);
    statusChar->setValue(buf, PACKET_LEN);
    if (bleServer->getConnectedCount() > 0) statusChar->notify();
    (void)now;
}

// ---------------------------------------------------------------- the main steps
static void applyCommands() {
    if (pendingMode != 255) {
        mode = pendingMode;
        modeSince = millis();
        pendingMode = 255;
        Serial.printf("mode %u\n", mode);
    }
    if (pendingCal != 255) {
        if (pendingCal == 1) startCalibration(); else if (calibrating) stopCalibration();
        pendingCal = 255;
    }
    if (pendingDecl) { declinationDeg = newDeclDdeg / 10.0f; prefs.putShort("decl", newDeclDdeg); pendingDecl = false; }
    if (pendingHeight) { trackerHeightM = newHeightDm / 10.0f; prefs.putShort("hgt", newHeightDm); pendingHeight = false; }
}

static void gpsStep() {
    while (Serial2.available()) gps.encode((char)Serial2.read());
    trackerFix = gps.location.isValid() && gps.location.age() < 3000 && gps.satellites.value() >= 5;
    trackerSats = (uint8_t)clampd(gps.satellites.isValid() ? gps.satellites.value() : 0, 0, 255);
    if (trackerFix) { trackerLat = gps.location.lat(); trackerLon = gps.location.lng(); }
}

static void pointingStep(uint32_t now, double dt, bool& fresh) {
    Aircraft a;
    uint32_t rxAt;
    bool have;
    portENTER_CRITICAL(&lockMux);
    a = airPacket; rxAt = airRxAtMs; have = airHave;
    portEXIT_CRITICAL(&lockMux);
    uint32_t sinceRx = now - rxAt;
    fresh = have && ((uint32_t)a.ageMs + sinceRx) < AIRCRAFT_FRESH_MS;
    tracking = false;
    limitHit = false;

    if (mode == MODE_CENTRE) { servosMove(90.0, 0.0, dt); return; }
    if (mode == MODE_SWEEP) {
        uint32_t t = now - modeSince;
        if (t > SWEEP_MS) { mode = MODE_HOLD; return; }
        servosMove(90.0 + 90.0 * sin(2 * PI_ * t / 10000.0), 45.0 + 45.0 * sin(2 * PI_ * t / 6000.0), dt);
        return;
    }
    if (mode != MODE_AUTO) return;                                             // hold: the servos stay where they are
    if (!(a.flags & A_FIX) || !have || !trackerFix || !compassOk || !compassCalibrated || calibrating) return;
    if (((uint32_t)a.ageMs + sinceRx) > 60000UL) return;                       // a minute old: nothing to follow

    double lat = a.latE7 / 1e7, lon = a.lonE7 / 1e7, la, lo;
    double ageS = ((double)a.ageMs + (double)sinceRx) / 1000.0;
    deadReckon(lat, lon, a.speedDkmh / 10.0, a.courseCdeg / 100.0, ageS, la, lo);
    double h = ((a.flags & A_HEIGHT) ? a.heightDm / 10.0 : 0.0) - trackerHeightM;
    Aim m = aim(trackerLat, trackerLon, la, lo, h);
    if (m.distanceM < HOLD_NEAR_M) m.azimuthDeg = lastAzimuth;
    lastAzimuth = m.azimuthDeg;
    lastAim = m;
    ServoAngles s = servoAngles(wrap180(m.azimuthDeg - heading), m.elevationDeg, flipped);
    flipped = s.flipped;
    limitHit = (s.pan <= 0.0 || s.pan >= 180.0);
    servosMove(s.pan, s.tilt, dt);
    tracking = true;
}

void setup() {
    Serial.begin(115200);
    Serial.println("AX-HUD antenna tracker");
    loadSettings();
    Wire.begin(PIN_SDA, PIN_SCL);
    compassOk = compassInit();
    Serial2.begin(GPS_BAUD, SERIAL_8N1, PIN_GPS_RX, PIN_GPS_TX);
    servosInit();
    servosMove(90.0, 0.0, 1.0);
    bleInit();
    modeSince = millis();
    Serial.printf("compass %s, %s; declination %.1f deg, antennas %.1f m above the launch point\n", compassOk ? "found" : "NOT found", compassCalibrated ? "calibrated" : "NOT calibrated: use the app to calibrate it", declinationDeg, trackerHeightM);
}

void loop() {
    static uint32_t lastServo = 0, lastCompass = 0, lastStatus = 0, lastPrint = 0;
    static bool fresh = false;
    uint32_t now = millis();
    applyCommands();
    gpsStep();
    if (now - lastCompass >= 40) { lastCompass = now; compassStep(); }
    if (now - lastServo >= 20) {
        double dt = (now - lastServo) / 1000.0;
        lastServo = now;
        pointingStep(now, dt, fresh);
    }
    if (now - lastStatus >= 500) { lastStatus = now; sendStatus(now, fresh); bleKeepAdvertising(); }
    if (now - lastPrint >= 1000) {
        lastPrint = now;
        Serial.printf("gps %s (%u sats) heading %.1f az %.1f el %.1f dist %.0f m  %s%s%s\n", trackerFix ? "fix" : "no fix", trackerSats, heading, lastAim.azimuthDeg, lastAim.elevationDeg, lastAim.distanceM,
                      tracking ? "TRACKING " : "", flipped ? "flipped " : "", calibrating ? "CALIBRATING" : "");
    }
}
