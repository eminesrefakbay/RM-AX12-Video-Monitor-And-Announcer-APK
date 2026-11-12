// AX-HUD antenna tracker firmware. Copyright (C) 2026 eminesrefakbay. SPDX-License-Identifier: GPL-3.0-or-later
// With the additional permission for the RadioMaster AX SDK described in the LICENSE file of the AX-HUD app.
// The things to adjust for your build.
#pragma once

// ---- pins (ESP32 DevKit) ----
#define PIN_PAN_SERVO 18
#define PIN_TILT_SERVO 19
#define PIN_GPS_RX 16        // the ESP32 receives here: connect it to the GPS module's TX
#define PIN_GPS_TX 17
#define PIN_SDA 21           // the compass
#define PIN_SCL 22
#define GPS_BAUD 9600        // a u-blox module's default

// ---- compass ----
#define COMPASS_TYPE 0       // 0 = QMC5883L (address 0x0D), 1 = HMC5883L (address 0x1E). Many "HMC5883L" boards carry a QMC chip: try 0 first.
#define HEADING_SIGN 1       // +1 or -1. Turn the tracker's base clockwise: the heading on the serial monitor must increase. If it falls, change this.
#define COMPASS_OFFSET_DEG 0.0   // the mounting rotation of the compass board against the tracker's forward axis, degrees

// ---- servos (the pulse widths of your servos; the 500..2500 us of many 180-degree servos is a start) ----
#define PAN_MIN_US 500
#define PAN_MAX_US 2500
#define TILT_MIN_US 500
#define TILT_MAX_US 2500
#define PAN_TRIM_DEG 0.0     // adjust so that pan 90 looks straight ahead of the base
#define TILT_TRIM_DEG 0.0    // adjust so that tilt 0 looks at the horizon
#define PAN_INVERT 0         // 1 when a larger pan angle turns the antennas to the left
#define TILT_INVERT 0        // 1 when a larger tilt angle lowers the antennas
#define SLEW_DEG_PER_S 120.0 // the fastest the servos may move

// ---- behaviour ----
#define AIRCRAFT_FRESH_MS 6000     // the position of the aircraft counts as fresh for this long
#define HOLD_NEAR_M 3.0            // closer than this to the aircraft the azimuth is not trusted: keep the last one
#define SWEEP_MS 30000             // how long the test sweep runs
