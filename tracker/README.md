# AX-HUD antenna tracker

An **ESP32** that points your antennas at the aircraft. The AX-HUD app on the radio sends the aircraft's state over **Bluetooth LE**; the
tracker has its **own GPS** and a **compass**, so it knows where it is and which way its base faces, works out where the aircraft is, and
drives two servos (pan and tilt).

> **Experimental.** Try it away from people, with the antennas off first, and tell me what you see. Servos can move suddenly: keep fingers and
> cables clear. The Bluetooth link has no pairing or encryption, so anyone in range could connect and move it (see [../SECURITY.md](../SECURITY.md)).

**Status: written and tested on a PC, not yet tested on hardware.** The geometry and the packets (`include/aim.h`, `include/protocol.h`) are
plain C++ with 52 checks, read against the same packets as the app's tests. The Bluetooth, servo, GPS and compass code in `src/main.cpp` passed a
syntax check on a PC but has not run on an ESP32. Expect to adjust things (the compass axis, the servo directions); the settings for that are in
`include/config.h`. Please report what you find.

## How it works

```
 radio (AX-HUD)                                     tracker (ESP32)
 telemetry: GPS, height, speed, course  --BLE-->    aircraft packet, 5 times a second
 Settings > Antenna tracker             <--BLE--    status: its GPS, compass, where it points
                                                    GPS + compass  ->  bearing and elevation to the aircraft
                                                    dead reckoning between the slow telemetry updates
                                                    servos: pan and tilt, slew limited
```

* The packets are 20 bytes each (they fit any Bluetooth LE connection without negotiating). The layout is in `app/.../TrackerProtocol.java` and in
  `include/protocol.h`; `test/vectors.txt` holds packets that both sides must read and write in the same way.
* **Dead reckoning.** The telemetry of ExpressLRS can be seconds old. The app sends how old the position is; the tracker moves it forward with the
  speed and course (at most 10 s) before it aims. At 100 km/h a 2-second-old position at 500 m is about 6 degrees off; this removes most of it.
* **Height.** The app sends the height above the launch point (the GPS altitude on the ground is the zero). The tracker assumes it stands at the
  launch altitude; if its antennas are higher or lower, set the antenna height in the app (*Set the antenna height above the launch point*).
* **The flip.** Two 180-degree servos cannot look behind the base directly. When the aircraft is behind, the pan servo turns to the opposite side and
  the tilt servo goes past vertical (tilt 180 = the horizon behind). With a little hysteresis (95 / 85 degrees) so that it does not flip back and
  forth when the aircraft crosses the line.

## Parts

* **ESP32 DevKit** (the 30-pin ESP32-WROOM board, any clone).
* **Two servos.** A metal-gear 180-degree servo for the pan (it carries everything) and one for the tilt. Digital servos of 15 to 35 kg.cm are
  common for this; they must run on **5 to 6 V from a separate supply** (a BEC or a 2S pack with a regulator, 3 A or more), never from the ESP32's
  pin. **Join the grounds.** A 470 to 1000 uF capacitor across the servo supply helps.
* **GPS with a compass.** A u-blox NEO-M8N (or M10) module with a QMC5883L (or HMC5883L) on the same board, or two modules. The GPS antenna needs a
  clear view of the sky.
* **A pan and tilt mount** for your servos, and your **patch antennas** (wide ones are forgiving: a 60 to 90 degree beam does not care about a few
  degrees of error).
* A level mast or tripod head.

## Wiring (the pins are in `include/config.h`)

| ESP32 | Goes to |
| --- | --- |
| GPIO 18 | pan servo signal |
| GPIO 19 | tilt servo signal |
| GPIO 16 (RX2) | GPS module TX |
| GPIO 17 (TX2) | GPS module RX |
| GPIO 21 (SDA), GPIO 22 (SCL) | compass SDA, SCL |
| 3V3, GND | GPS and compass power (check the board's voltage), ground |
| servo supply + / - | both servos; the supply's ground joins the ESP32's ground |

## Mechanics (write this on your build)

* The **pan servo at 90 degrees** looks **straight ahead of the base**: the way the compass board is mounted. The compass must be fixed to the
  **base** (not to the moving part), **level**, and **at least 10 to 15 cm from the servos and any magnet or steel**. A mast between the servos and
  the compass is a good place.
* The **tilt servo at 0 degrees** looks at the horizon ahead, at **90** straight up, at **180** at the horizon behind.
* Adjust the trims in `config.h` (`PAN_TRIM_DEG`, `TILT_TRIM_DEG`) until pan 90 and tilt 0 really look ahead and at the horizon, and set
  `PAN_INVERT` / `TILT_INVERT` if a servo turns the wrong way (use the centre command in the app, or the test sweep).
* The tracker base must be **level**: there is no tilt compensation in the compass.

## First set-up

1. Install PlatformIO, open this folder, `pio run -t upload`, then `pio device monitor` (115200). The tracker prints its GPS, heading, and what
   it is doing once a second.
2. **Compass.** The monitor says if it is found and calibrated. If it is not found, try `COMPASS_TYPE` 0 or 1 (many "HMC5883L" boards carry a QMC chip).
   Turn the base clockwise: **the heading must increase**. If it falls, set `HEADING_SIGN` to -1. Set `COMPASS_OFFSET_DEG` for the way the board is mounted
   (point the tracker's forward at a known direction and compare).
3. **Pair with the radio.** On the radio: *Settings > Antenna tracker*, switch it on, tap *Tracker* to search (Android asks for the location permission
   once; it is only used to search), choose `AX-TRACK`. The status line shows the connection.
4. **Calibrate the compass** (once, and again if you move it to a new mount): *Compass calibration: start*, turn the whole tracker base slowly through a
   full circle and tilt it a little each way, then *Compass calibration: save*. It is stored in the ESP32.
5. **Declination.** Set the magnetic declination of your place (degrees, east is positive; look it up with a declination calculator for your location). The tracker stores it. A wrong value turns
   every pointing by the same angle.
6. **Test.** *Centre the antennas* (they should look straight ahead and level), then *Test sweep* (30 seconds of pan and tilt through their range; watch for
   cables and stops).
7. **In the field.** Put the tracker where you stand, level, with a clear sky for the GPS; wait for the GPS (the status shows the satellites and "Compass ready");
   *Follow the aircraft*. With the aircraft powered on the ground, the tracker should look at it. Walk it out 100 m and check that it follows.

## The status line in the app

`Connected. Tracker GPS: 18 satellites - Compass ready - Following the aircraft`. If it says it is **waiting for the aircraft's GPS**, the aircraft has no fix or
sends no GPS telemetry yet; **waiting for the tracker's GPS or compass**: no fix on the tracker, or the compass is not calibrated.

## Limits and honest notes

* GPS of both ends is good to a few metres; the compass and the mounting usually decide the accuracy (a few degrees). This is fine for wide patch
  antennas and not for narrow dishes.
* The elevation uses the aircraft's height above the launch point and the distance on the ground; it is zero below the horizon.
* Near the tracker (closer than 3 m) the azimuth is not trusted and the last one is kept.
* If the telemetry stops, the tracker keeps aiming where the aircraft was for up to a minute, then holds. If the Bluetooth link drops it holds its last position.
* Not for anything where a wrong pointing is dangerous. It is an aid for better reception, nothing more.

## Tests

```
g++ -std=c++17 -Wall -Wextra -I tracker/include tracker/test/test_core.cpp -o /tmp/test_core && /tmp/test_core tracker/test/vectors.txt
```

The CI runs this, and the app's Java tests read the same `vectors.txt`. If you change the packet layout, change both sides and the file.

## Licence

GPL-3.0-or-later, like the app (see the LICENSE file of the repository).

## Known limitations

* **Experimental:** the Bluetooth, servo, GPS and compass code has had little testing on real hardware.
* **No pairing and no encryption** on the Bluetooth link: any Bluetooth LE device in range can connect and send commands (move the servos,
  change the declination, start a calibration). Keep the tracker away from strangers.
* **One tracker** is remembered by the app at a time.
* The servos have **no position feedback**: the tracker points where it commands, and a stalled or slipping servo is not noticed.
