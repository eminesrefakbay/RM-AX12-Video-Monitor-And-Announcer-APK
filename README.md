# AX12 Announcer (starter)

A small Android app for the RadioMaster AX12:

* opens straight into **full-screen video** (the HDMI-in camera);
* when no frames arrive it shows a **grey "NO VIDEO" panel** with big readable text and a hint;
* speaks **announcements from simple rules** you type, for example `ch5 >= 1500 -> drone armed`;
* only **reads** the radio. It never writes a setting.

> Status: the rule engine is plain Java and has 33 passing tests (run below). The Android parts
> (camera, layout, speech) are written but **not compiled or run yet**: expect to fix small
> compile errors the first time you build. Reuse your tested Camera2 code if it differs.

## Build

1. Push to GitHub: `.github/workflows/build.yml` runs the rule-engine tests and builds a debug APK
   (download it from the run's "Artifacts"). Locally: JDK 17 and `gradle :app:assembleDebug`
   (or copy a Gradle wrapper from your Camera2 test app).
2. Install the APK on the AX12.
3. First start: allow the camera permission. Tap **MENU > Choose camera** and pick the HDMI input.
   **MENU > Rotate video** turns the picture in 90-degree steps and remembers it.

## Sound rules

One rule per line, in **MENU > Edit sound rules**:

```
ch5 >= 1500 -> drone armed | drone disarmed       text after | is spoken when it turns false
ch6 >= 1450 and ch6 <= 1550 -> horizon mode       and / or (and binds first)
ch9 < 1300 -> !battery critical @10               ! = urgent (interrupts), @10 = repeat every 10 s
hysteresis = 25      hold = 100      initial = off
```

* A rule speaks when its condition **becomes true**, not while it stays true.
* `hysteresis` (microseconds) stops chatter near a threshold. `hold` (ms) ignores short blips.
* The first reading after start-up is silent unless `initial = on`.
* Operators: `>=  <=  >  <  ==  !=`. Channel numbers are RC channel numbers (AUX1 = ch5).
* Starting rules for your INAV modes: see `DefaultRules.kt`.

## Try it tonight without the radio

**MENU > Test channels** shows 16 sliders (1000..2000 us). Move ch5 above 1500 and listen.
The layout lives in `res/layout/activity_main.xml`.

## Connecting the real channels (not done yet)

`AxSdkChannelSource` in `ChannelSource.kt` is the placeholder. The SDK is C++, so add a small JNI
bridge that, on `RC_MSGID_MIXOUT_UPDATE`, calls `rcGetChOutList()` and passes 16 values in
microseconds to `AxSdkChannelSource.publish()`. Then set `available = true`.
Getters only: never `rcSet*`, never the save or task functions. First dump the real value range
(percent or microseconds) to the screen and fix the conversion. The SDK headers were not
available to me, so check the field names in `rmAxSdkTypes.h`.

## Tests for the rule engine

```
mkdir -p out
javac -d out app/src/main/java/dev/ax12/announcer/*.java app/src/test/java/dev/ax12/announcer/*.java
java -cp out dev.ax12.announcer.RuleEngineTest
```

## Licence note

Choose a licence for your own code (MIT or Apache-2.0 are common). Do **not** commit RadioMaster's SDK
headers or libraries: the SDK repository shows no licence of its own. `.gitignore` already excludes `sdk/` and `*.so`.

## Next ideas

Telemetry variables in rules (`vbat < 3.5`, `lq < 60`) once you have dumped the SDK telemetry list;
switch-versus-flight-mode mismatch warning from the CRSF flight-mode frame; a speech language setting.
