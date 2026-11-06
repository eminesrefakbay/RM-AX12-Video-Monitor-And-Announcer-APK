# AX12 Video Monitor and Announcer (v0.4.2)

An Android app for the RadioMaster AX12:

* opens straight into **full-screen video** from the HDMI input;
* shows a plain grey screen with one slim **status bar at the bottom** ("NO VIDEO · camera · size · reason") when no frames arrive, and reconnects by itself;
* **speaks announcements** from rules you type, e.g. `ch5 >= 1500 -> drone armed`;
* announces **battery per cell, link quality, telemetry loss and the flight controller's flight mode**;
* only **reads** from the radio. It never writes a setting.

## Status (v0.2)

| Part | State |
| --- | --- |
| Rule engine, CRSF parser, channel scaler (plain Java) | 167 automated checks pass |
| Native bridge core (C++, fake SDK, also under thread and address sanitizers) | 39 checks pass |
| Camera, layout, speech, JNI glue, Gradle/NDK build | written, **not yet built or run on a radio** |
| Opening the radio's serial link from a third-party app | **open question**, see "Serial lock" |

## How the video is drawn, and measuring lag

The HDMI input is read with Camera2 into a `TextureView`: the frames go from the camera straight into a GPU texture
(hardware accelerated, no copies to the CPU, no bitmap or colour conversion). Rotation and stretch are a GPU matrix.
`TextureView` needs the app's main thread to put each new frame on screen and adds about one frame compared with a
`SurfaceView`, so main-thread work shows up as stutter. Most of the delay is in the radio's HDMI capture, before the app.
Settings > Diagnostics > Show variables prints the frame rate, the gaps between frames and how late the app's own
50 ms loop runs, so constant delay and stutter can be told apart.

## Menu and settings

The MENU button opens an in-screen panel (not a popup window: a popup takes the keyboard focus and Android then
shows its navigation buttons). Dialogs are shown full-screen safe in the same way. The MENU button hides itself and comes back for a few seconds whenever you touch the screen (Settings can keep it
always visible), and it hides while a panel is open so it never covers a panel's own buttons. The menu holds only the daily items: **Sound rules**, **Mute speech** and **Settings**.
**Settings** holds the rest: the MENU button option, video (camera, rotate, fit or fill), speech (on/off, test),
the sound rules files, and the diagnostics (variables panel, test channels).

**Export / import of the sound rules:** Settings > Sound rules > *Export sound rules to a file...* opens Android's
file picker and saves a plain text file (`ax12-sound-rules.txt`, rules with one comment line on top). *Import* reads
such a file, shows how many rules it has and any problems, and replaces your rules only after you confirm. The old
rules are kept: *Restore the rules from before the last import* swaps them back (press again to swap forward).
On a device without a file picker the file is written to the app's own folder and its path is shown.

## Rules

**MENU > Sound rules** opens a list you can tap: switch a rule on or off, **Edit**, **Test** (speaks it), **X** (delete),
**+ Add rule**. In the editor press **List** to pick a channel or telemetry value from a grouped list with live values (or type a name), then pick an operator and a value, type what to say, and press
**Detect switch** then move a switch on the radio: the channel and a sensible threshold are filled in for you. The
editor shows the live value of each variable ("now: 1500") and the rule as text. **Settings** holds the hysteresis,
hold time, start-up behaviour and unit. The GUI and the text editor edit the same text, so they always agree;
comments and settings in the text are kept. A switched-off rule is stored as a comment starting with `#off`.
Rules the GUI cannot show (mixed `and`/`or`) appear as "Advanced rule" and open the text editor.

**Settings > Edit the rules as text (advanced)** edits the same rules as plain text, one rule per line:

```
ch5 >= 1500 -> drone armed | drone disarmed          text after | is spoken when it turns false
ch6 >= 1450 and ch6 <= 1550 -> horizon mode          and / or ("and" binds first)
vbat / 6 < 3.55 -> !battery critical @10             divide first (volts per cell); ! urgent; @10 repeat every 10 s
fm ~ "RTH" -> return to home active                  text rules:  ==  !=  ~ (contains), case-insensitive
hysteresis = 25   var_hysteresis = 1   hold = 100   initial = off   scale = auto
```

* A rule speaks when its condition **becomes true**, not while it stays true (unless `@N`).
* `hysteresis` (channel units) and `var_hysteresis` (percent, for volts and link quality) stop chatter.
* `hold` ignores changes shorter than that many ms. The first reading is silent unless `initial = on`.
* A rule whose variable has no value is skipped (for example battery rules before telemetry arrives).
* `scale` is the unit of the radio's channel numbers. The SDK does not name it, so `auto` guesses from the
  numbers seen (percent, then permille, then microseconds). Look at Settings > Diagnostics > Show variables.

### Variables (Settings > Diagnostics > Show variables lists the live ones)

| Name | Meaning |
| --- | --- |
| `ch1`..`ch16` | channel output in microseconds (1000..2000) |
| `raw1`..`raw16`, `lv1`..`lv16` | the radio's own numbers: `chOut` and `logicVal` |
| `lq`, `dbm`, `lost`, `txv`, `rxv`, `tlm` | radio status: link quality, dBm, lost counter, radio voltage, receiver voltage, telemetry present (1/0) |
| `vbat`, `curr`, `capa`, `batpct` | battery from the flight controller (CRSF 0x08) |
| `up_lq`, `up_rssi1`, `up_rssi2`, `up_snr`, `dn_lq`, `dn_rssi`, `dn_snr`, `rf_mode`, `tx_power` | link statistics (CRSF 0x14) |
| `fm` (text) | flight mode name (CRSF 0x21) |
| `gps_lat`, `gps_lon`, `gps_speed`, `gps_heading`, `gps_alt`, `sats` | GPS (CRSF 0x02) |
| `pitch`, `roll`, `yaw`, `vspeed` | attitude and vertical speed |

CRSF values older than 5 s are dropped, so a rule never acts on a stale reading.

## Build

The RadioMaster SDK files are **not stored in this repository** (the SDK repository states no licence of its own).
The CI workflow clones <https://github.com/Radiomaster-RC/RadiomasterAX_SDK> and copies the five `.so` files into
`app/src/main/jniLibs/arm64-v8a/` and the headers into `app/src/main/cpp/sdk-include/rmAxSdk1/` before building.
Without those files the app still builds and runs on the test sliders.

Locally: JDK 17, Android SDK + NDK, `gradle :app:assembleDebug` (copy the SDK files in first, same folders).

## Serial lock (open question)

The SDK talks to the radio over a serial link that Qt's `QSerialPort` opens only after creating a lock file.
An Android app has no writable lock directory, so the link may never open: then the Show variables panel says
`radio config: NOT loaded yet` and `mixout msgs` stays 0.

* The app always points Qt's temp folder (`TMPDIR`) at its own data folder before the SDK starts (as RadioMaster's demo does).
* RadioMaster's demo also patches two instructions of the bundled Qt serial library at run time. That is the
  **second APK** the CI builds, `ax12-announcer-debug-lockpatch.apk` (Gradle flag `-PaxLockPatch=true`).
  It checks both instructions are exactly the expected ones before changing anything. Off in the plain APK.
* Close RadioMaster's own AX app first: two programs must not read the same serial port.
* Look for the cause in `adb logcat`: lines with `qt`, `serial`, `lock` or `ax12bridge` ("Failed to create a lock file").
* The proper fix is an official way from RadioMaster to open the link; please ask them.

## Try it without the radio

Settings > Diagnostics > Test channels shows 16 sliders (1000..2000 us). Move ch5 above 1500 and listen.

## Tests

```
mkdir -p out
javac -d out app/src/main/java/dev/ax12/announcer/*.java app/src/test/java/dev/ax12/announcer/*.java
java -cp out dev.ax12.announcer.RuleEngineTest && java -cp out dev.ax12.announcer.ExtraTests
# native core (needs the SDK headers in sdk-src/include/rmAxSdk1):
g++ -std=c++20 -pthread -Iapp/src/main/cpp -Isdk-src/include/rmAxSdk1 app/src/test/cpp/bridge_core_test.cpp app/src/main/cpp/bridge_core.cpp -o bridge_test && ./bridge_test
```

## Privacy

GPS coordinates are shown on screen only. Do not commit flight logs or screenshots that show where you fly.
