<p align="center"><img src="docs/logo.png" width="160" alt="AX-HUD logo"></p>

<p align="center"><b>English</b> · <a href="README.tr.md">Türkçe</a> · <a href="README.de.md">Deutsch</a> · <a href="README.zh.md">中文</a></p>

# AX-HUD: AX12 video monitor, HUD and announcer (v0.9.0)

**AX-HUD** is an Android app for the RadioMaster AX12:

* opens straight into **full-screen video** from the HDMI input;
* shows a plain grey screen with one slim **status bar at the bottom** ("NO VIDEO · camera · size · reason") when no frames arrive, and reconnects by itself;
* **speaks announcements** from rules you type, e.g. `ch5 >= 1500 -> drone armed`;
* announces **battery per cell, link quality, telemetry loss and the flight controller's flight mode**;
* only **reads** from the radio. It never writes a setting.

## Status

Built and tested by its author on one setup: **RadioMaster AX12 (Android 9, October 2026 system update), INAV 9.1, ExpressLRS**.
It works there: video, spoken announcements, warning bar, overlays, updates from GitHub. It has been checked that the
control link stays clean while the app starts, runs and is force-stopped. Other radios, firmware versions and flight
controllers are **untested**. The APK `ax-hud.apk` (with the serial-lock patch, see "Serial lock") is the one that works on the AX12.

| Part | State |
| --- | --- |
| Rules, text templates, warnings, CRSF parser, scaler, overlays, files, updater and translations (plain Java) | 559 automated checks pass |
| Native bridge core (C++, fake SDK, also under thread and address sanitizers) | 39 checks pass |
| Camera, layout, speech, JNI glue, Gradle/NDK build | built by CI, used on the radio |

## Languages

English, Turkish, German and Simplified Chinese: *Settings > Language* (Automatic follows Android). The voice can speak a
different language than the screen. German and Chinese have not been checked by native speakers yet; see
**[TRANSLATING.md](TRANSLATING.md)** to improve a text.

## Flight overlays: statistics, radar and pre-flight checklist

**Settings > Flight overlays** switches each card on or off and sets its size, position (four corners or the
centre) and opacity.

* **Arm detection:** the arm switch (default channel 5, armed at 1750 us or more, disarmed below 1650, held 0.3 s)
  starts and stops a flight. The statistics start over when you arm and stay readable after you land. The cell count
  of the pack is set here too (for the lowest volts per cell).
* **Flight statistics card:** speed, **height above the arming point**, **current with a limit line**
  (the bar turns red above the limit; default 50 A) as *now / maximum*, plus optional rows: distance from home,
  lowest link quality, lowest volts per cell. A timer in the title shows the flight time; after landing it says
  LAST FLIGHT. Height comes from the GPS altitude relative to where you armed, so expect a few metres of error.
* **Radar:** home in the centre, north up, three range rings, the track of the flight and the aircraft with its
  heading. It shows only positions relative to home, no coordinates. The scale grows as you fly farther and does not
  shrink during the flight.
* **Pre-flight checklist:** a card shown while disarmed with one row per check: green tick, red cross, or a grey dash
  when there is no value yet; READY or NOT READY at the bottom. The checks are **rules**, edited like the warnings
  (Settings > Flight overlays > *Edit the checks*): condition, thresholds, `and`/`or`, and a label that can hold live
  values such as `{sats}`. Defaults: battery connected, battery full (4.15 V per cell or more), 10 or more satellites,
  link quality 95 % or more, airmode switch on, arm switch off. The card is hidden when nothing is known yet.

## OSD overlays: sticks, channel bars and the warning status bar

**Settings > OSD overlays** controls what is drawn over the video.

* **Stick view:** two boxes with a dot show roll, pitch, yaw and throttle from the channel values the radio sends.
  Size, position (five places) and opacity are settings. *Stick mode, channels and direction* sets the stick mode
  (1 to 4), which channel is roll/pitch/throttle/yaw (default 1, 2, 3, 4) and lets you reverse any stick axis.
* **Channel bars:** up to eight bars beside the sticks, four on each side (default channels 5 to 8 on the left,
  9 to 12 on the right). Each bar has an on/off switch, a channel number and a **name written along the bar**
  (defaults ARM, MODE, RTH, PREARM, AIRMODE, HOLD, OSD, BEEP, matching the AKB7's INAV modes; names up to 12
  characters). A bar fills with the channel value and its colour shows a switch position: grey-blue = low,
  amber = middle, green = high. A **reverse** box per bar is for a switch that is reversed in the radio: the bar then
  shows the switch the way you flip it (a reversed bar flips its fill and its colour). Only the picture changes;
  sound and warning rules still use the real channel values. Edit the bars in *Channel bars...* or **press and hold the
  stick view** on the video. One switch shows or hides the whole group.
* **Warning status bar:** one line at the top (or bottom) that lists every active warning, coloured by the worst:
  **critical = red and blinking, warning = amber, info = blue**. Warnings are **rules**, edited like the sound
  rules (Settings > OSD overlays > *Edit the warning rules*): pick variables from the list, thresholds, `and` / `or`,
  *Detect switch*, text. A warning is shown **while its condition is true** (a sound rule speaks once when it becomes
  true). Severity is part of the rule text: `!text` critical, `*text` info, `text` warning. A **timeout** (`@5` after the text, or
  the *Hide after* box in the editor) hides a warning N seconds after it turned on even if its condition stays true, for
  example `ch5 >= 1950 -> *ARMED @5`; it shows again the next time the condition turns on. A warning whose input
  disappears (for example the battery value when the aircraft is switched off) is removed instead of staying on
  screen. *Preview the warning bar* shows a sample for four seconds; each rule has a *Test* button.
* **Live values in texts**, for warnings and for spoken rules: `{vbat/6:2}` is volts per cell with two decimals,
  `{up_lq}`, `{sats}`, `{fm}`. A spoken rule such as `vbat / 6 < 3.6 -> battery {vbat/6:2} per cell` says the number.

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

## Battery profiles (Li-ion / LiPo)

A **battery profile** is a kind of pack: its cell count and its volts-per-cell limits (low warning, critical, full). Two are
there from the start (Li-ion 6S: 3.70 / 3.55 / 4.15, LiPo 6S: 3.85 / 3.70 / 4.15); you can edit them and add more
(*Settings > Battery*). **One tap on the MENU's Battery item switches the profile**; the app says its name aloud, shows the
limits, and the pre-flight checklist carries the profile's name in its title, so you can check it before every flight.

Rules, warnings and checks can use:

* **`cell`**: volts per cell (battery volts divided by the active profile's cells). It only exists while a pack is connected
  (at least 2.5 V per cell), so rules about it stay silent when the flight controller runs from USB.
* **`$low`, `$crit`, `$full`**: the active profile's limits, used instead of a number:
  `cell < $low and cell >= $crit -> battery low`. In the rule editor the **$** button next to the value inserts one.

Switching the profile changes the numbers, **not the rules**. Rules with plain numbers (`vbat / 6 < 3.70`) keep working
as before. The starting rules, warnings and checks use the new names; an existing install keeps its own text until you
*Reset the rules / warning rules / checks to the defaults* (Settings).

## Backup of all settings

*Settings > Backup of all settings* writes **one text file with everything**: sound rules, warning rules, pre-flight checks,
overlays and their places, stick and channel-bar settings, arm detection, battery profiles, camera, language and update
channel. *Import* shows what the file contains, replaces the settings in it, and keeps the old ones: *Restore* swaps them
back (press again to swap forward). Only known settings with valid values are read, so a damaged or hand-edited file cannot
break the app. The sound rules also keep their own, smaller export. The file is a complete snapshot, so it is also the way to
move the setup to another radio.

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

## Updates from GitHub

**Settings > Updates > Check for updates now** looks on GitHub for a newer build, downloads it, checks its SHA-256, and
hands it to Android's installer, which asks you to confirm. Two channels: **Nightly** (the newest build, published by CI on every push to `main`, versions like `0.7.1-nightly.45`) and
**Stable** (the latest GitHub release, made with one click from the Actions tab or by pushing a tag such as `v0.7.1`). The app can
also look for news a few seconds after it starts (switchable). Every build is signed with **one permanent key** kept in GitHub
secrets, which is what lets each build install over the last one without uninstalling. The one-time setup, the daily routine and
the error messages are in **[UPDATES.md](UPDATES.md)**. The only network use is fetching two files from github.com; the app has
the internet permission for that and nothing else.

## Start-up splash screen

On a fresh start the app shows the logo with the version (name, version number and build number, read from the app
itself) for **2 seconds**, then fades out. Two seconds is the usual range for a splash screen (1 to 3 s): long enough to
read, short enough not to get in the way. A tap or the Back key skips it. The app starts up behind it (camera, radio
link, settings), so it adds no waiting. Android's own start-up screen already shows the same navy and logo, so there is no
white flash before it. A switch in Settings > Display turns the splash off.

## Logo

`docs/logo.png` (512 px) and `docs/logo-768.png` are the AX-HUD logo: a flat navy rounded square with the radio, the
drone, the gauge and the name. The launcher icons are built from it. Android 8 and newer (the AX12 runs Android 9) use
an adaptive icon: navy background and the artwork as the foreground, kept inside the safe zone so every launcher shape
shows it whole. Older Android versions get the full design. The logo has no third-party logos in it, and every edge is
pure navy (no light fringe).

## Acknowledgments

Thanks to **RadioMaster** for publishing the AX SDK that makes this app possible. AX-HUD is an independent project and is not
affiliated with RadioMaster. The app also includes the Qt libraries that come with the SDK (LGPL).
