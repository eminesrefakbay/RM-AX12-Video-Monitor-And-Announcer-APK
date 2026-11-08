<p align="center"><img src="docs/logo.png" width="160" alt="AX-HUD logo"></p>

<p align="center"><b>English</b> · <a href="README.tr.md">Türkçe</a> · <a href="README.de.md">Deutsch</a> · <a href="README.zh.md">中文</a></p>

# AX-HUD: AX12 video monitor, HUD and announcer (v1.2.2)

**AX-HUD** is an Android app for the RadioMaster AX12:

* opens straight into **full-screen video** from the HDMI input;
* shows a gray **No video** screen (a camera icon, "No video" and one line of advice, with a small detail line along the bottom) when no frames arrive, and reconnects by itself;
* **speaks announcements** from rules you type, e.g. `ch5 >= 1500 -> drone armed`;
* announces **battery per cell, link quality, telemetry loss and the flight controller's flight mode**;
* has a second page, the **dashboard**: cards only and no video, with an artificial horizon, a compass, value cards, radar, charts and sticks, for goggles or a second pair of eyes;
* can **record every flight** to a CSV file (off by default) and **replay** it on the radio: radar, sticks, channel bars and charts;
* only **reads** from the radio. It never writes a setting.

## Screenshots

![AX-HUD in flight on the RadioMaster AX12: live charts, radar map, flight statistics and stick view over the video](docs/screenshots/hud-flight-annotated.jpg)

*In flight on the AX12 (the video and the OSD are the flight controller's; the cards on top are AX-HUD):*
**1** live charts of link quality, cell volts and current, **2** radar map with home, your track and range rings, **3** flight statistics,
**4** stick view with the aux channel bars, **5** the MENU button.

![The AX-HUD menu: large tiles over the live video, and the amber warning bar](docs/screenshots/hud-menu.jpg)

*The MENU (a photo of an earlier version, with six of today's eight tiles): large tiles with their state: speech on, the pack in use and the aircraft in use. The video
stays visible behind it. The amber bar along the top is the warning bar; each level has its own shape, here the diamond of a warning.*

![The pre-flight check card, all items ticked and READY](docs/screenshots/hud-preflight.jpg)

*The pre-flight check (a photo of the radio's screen): the aircraft and the pack are named under the title, and every item must be ticked
before it says READY.*

![AX-HUD on the ground, waiting for a GPS fix](docs/screenshots/hud-ground.jpg)

*On the ground: the same screen before take-off, waiting for a GPS fix.*

## Install and first start

1. Download **`ax-hud.apk`** from the [latest release](../../releases/latest). (`ax-hud-plain.apk` is the same app without the serial-lock
   patch; it is for tests only. `SHA256SUMS` lets you check the download.)
2. Install it on the AX12 (allow installs from unknown sources when Android asks) and open it.
3. Allow the **camera** permission: the HDMI input reaches Android as a camera.
4. A short **welcome guide** explains the first steps. It can be shown again under *Settings > About > Show the welcome guide*.
5. The video starts at once. The radio data (sticks, link quality, telemetry) appears after about **12 seconds**; that wait is inside the
   RadioMaster SDK (see "Start-up time of the radio link"), and a banner counts the seconds.
6. Choose your aircraft and battery pack first (MENU > Aircraft, Battery): their limits drive the warnings.

The app looks for updates on the **Stable** channel by default (the tested releases). The Nightly channel is for people who want every build.

## Status

Built and tested by its author on one setup: **RadioMaster AX12 (Android 9, October 2026 system update), INAV 9.1, ExpressLRS**.
It works there: video, spoken announcements, warning bar, overlays, updates from GitHub. It has been checked that the
control link stays clean while the app starts, runs and is force-stopped. Other radios, firmware versions and flight
controllers are **untested**. The APK `ax-hud.apk` (with the serial-lock patch, see "Serial lock") is the one that works on the AX12.

| Part | State |
| --- | --- |
| Rules, text templates, warnings, CRSF parser, scaler, overlays, files, updater and translations (plain Java) | 1,433 automated checks pass |
| Native bridge core (C++, fake SDK, also under thread and address sanitizers) | 74 checks pass |
| Camera, layout, speech, JNI glue, Gradle/NDK build | built by CI, used on the radio |

## Languages

English, Turkish, German and Simplified Chinese: *Settings > Language* (Automatic follows Android). The voice can speak a
different language than the screen. German and Chinese have not been checked by native speakers yet; see
**[TRANSLATING.md](TRANSLATING.md)** to improve a text. A test estimates whether the translated texts fit their places (menu tiles,
the welcome guide, buttons); it is a safety net, not a measurement on the radio. If a device has no Chinese font, the Chinese screen
language is skipped, because every text would show as empty boxes; if there is no voice for a speech language, Settings says so.

## Flight overlays: statistics, radar and pre-flight checklist

**Settings > Overlays on the video > Flight overlays** switches each card on or off and sets its size, position (four corners or the
centre) and opacity.

* **Arm detection:** the arm switch (default channel 5, armed at 1750 us or more, disarmed below 1650, held 0.3 s)
  starts and stops a flight. The statistics start over when you arm and stay readable after you land. The cell count
  of the pack is set here too (for the lowest volts per cell).
* **Flight statistics card:** speed, **height above the arming point**, **current with a limit line**
  (the bar turns red above the limit; default 50 A) as *now / maximum*, plus optional rows: distance from home,
  lowest link quality, lowest volts per cell. A timer in the title shows the flight time; after landing it says
  LAST FLIGHT. Height comes from the GPS altitude relative to where you armed, so expect a few meters of error.
* **Radar:** home in the center, north up, three range rings, the track of the flight and the aircraft with its
  heading. It shows only positions relative to home, no coordinates. The scale grows as you fly farther and does not
  shrink during the flight.
* **Pre-flight checklist:** a card shown while disarmed with one row per check: green tick, red cross, or a gray dash
  when there is no value yet; READY or NOT READY at the bottom. The checks are **rules**, edited like the warnings
  (Settings > Overlays on the video > Flight overlays > *Edit the checks*): condition, thresholds, `and`/`or`, and a label that can hold live
  values such as `{sats}`. Defaults: battery connected, battery full (4.15 V per cell or more), 10 or more satellites,
  link quality 95 % or more, airmode switch on, arm switch off. The card is hidden when nothing is known yet.
  A small **X** in its top right corner closes it without arming; it comes back for the next flight (after you arm), or when you change
  the aircraft, the pack or the checks.

## OSD overlays: sticks, channel bars and the warning status bar

**Settings > Overlays on the video > OSD overlays** controls what is drawn over the video.

* **Stick view:** two boxes with a dot show roll, pitch, yaw and throttle from the channel values the radio sends.
  Size, position (five places) and opacity are settings. *Stick mode, channels and direction* sets the stick mode
  (1 to 4), which channel is roll/pitch/throttle/yaw (default 1, 2, 3, 4) and lets you reverse any stick axis.
* **Channel bars:** up to eight bars beside the sticks, four on each side (default channels 5 to 8 on the left,
  9 to 12 on the right). Each bar has an on/off switch, a channel number and a **name written along the bar**
  (defaults ARM, MODE, RTH, PREARM, AIRMODE, HOLD, OSD, BEEP, matching the AKB7's INAV modes; names up to 12
  characters). A bar fills with the channel value and its colour shows a switch position: gray-blue = low,
  amber = middle, green = high. A **reverse** box per bar is for a switch that is reversed in the radio: the bar then
  shows the switch the way you flip it (a reversed bar flips its fill and its colour). Only the picture changes;
  sound and warning rules still use the real channel values. Edit the bars in *Channel bars...* or **press and hold the
  stick view** on the video. One switch shows or hides the whole group.
* **Warning status bar:** one line at the top (or bottom) that lists every active warning, coloured by the worst:
  **critical = red and blinking, warning = amber, info = blue**. Warnings are **rules**, edited like the sound
  rules (Settings > Overlays on the video > OSD overlays > *Edit the warning rules*): pick variables from the list, thresholds, `and` / `or`,
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

**Export / import of the sound rules:** Settings > Rules and backup > Sound rules > *Export sound rules to a file...* opens Android's
file picker and saves a plain text file (`ax12-sound-rules.txt`, rules with one comment line on top). *Import* reads
such a file, shows how many rules it has and any problems, and replaces your rules only after you confirm. The old
rules are kept: *Restore the rules from before the last import* swaps them back (press again to swap forward).
On a device without a file picker the file is written to the app's own folder and its path is shown.

## Battery profiles (Li-ion / LiPo)

A **battery profile** is a kind of pack: its cell count and its volts-per-cell limits (low warning, critical, full). Two are
there from the start (Li-ion 6S: 3.70 / 3.55 / 4.15, LiPo 6S: 3.85 / 3.70 / 4.15); you can edit them and add more
(*Settings > Aircraft and battery > Battery*). **One tap on the MENU's Battery item switches the profile**; the app says its name aloud, shows the
limits, and the pre-flight checklist carries the profile's name in its title, so you can check it before every flight.

Rules, warnings and checks can use:

* **`cell`**: volts per cell (battery volts divided by the active profile's cells). It only exists while a pack is connected
  (at least 2.5 V per cell), so rules about it stay silent when the flight controller runs from USB.
* **`$low`, `$crit`, `$full`**: the active profile's limits, used instead of a number:
  `cell < $low and cell >= $crit -> battery low`. In the rule editor the **$** button next to the value inserts one.

Switching the profile changes the numbers, **not the rules**. Rules with plain numbers (`vbat / 6 < 3.70`) keep working
as before. The starting rules, warnings and checks use the new names; an existing install keeps its own text until you
*Reset the rules / warning rules / checks to the defaults* (Settings).

## Moving and resizing the cards

*Settings > Overlays on the video > Layout of the cards > Move and resize the cards with your finger* opens an edit mode over the video. **Drag a card to
move it; drag the square handle at its bottom right corner to resize it.** The cards you can edit are the stick view, the flight
statistics, the radar, the pre-flight checklist and the live charts (switched-on ones; cards that are only shown at certain moments,
like the radar before a flight or the checklist while armed, are shown while you edit). A card snaps lightly to the screen edges and
can never leave the screen or grow bigger than it. A small see-through strip of icon buttons at the top centre (it hides as little as possible)
holds the **grid** button (a grid is drawn and a card's corner and size snap to 16 dp cells; on by default, the screen edges still win when a
card is close), the **reverse arrow** (*Reset the layout*: every card back in its corner) and the **tick** (done). On the dashboard the strip also has
**+** (add a card), **-** (remove a card) and a **pencil** (edit a value card). The **arrow at the right end** folds the strip to that one small
button and unfolds it again (the choice is remembered), and the strip steps aside by itself while you drag or resize a card. The back key also
finishes editing.

A card's place is stored as a fraction of the free space on the screen, so it stays where you meant it on any screen size; its size is
a factor on top of its *Size* setting (50 % to 250 %, as far as it fits the screen). A card you never touch stays in the corner chosen in
Settings. The layout is shared by all aircraft and is part of the full settings backup. The warning bar keeps its top/bottom setting.

## Live charts

*Settings > Overlays on the video > Flight overlays > Live charts* shows **up to four small graphs**, one per value, of the **last 30, 60 or 120 seconds**.
Each graph has its own scale, its name and unit, the value now, and the ends of its axis. Pick the values from the same list the rule
editor uses (*Charts: choose the values and the time window*); the default is link quality, volts per cell and current. Values with a
natural range keep a fixed scale (link quality is always 0 to 100, a channel 1000 to 2000), so a small wobble stays small; current, speed
and distance start at zero so the height of a punch means something; volts and the rest get a scale that never zooms in closer than a
sensible span. A missing value shows as a gap. Size, place and opacity work like the other cards. The charts start off (switch them on in
Settings) and fill as soon as the app runs.

Two new values come with them, usable in charts **and in rules**: **`height`** (meters above where you armed) and **`dist`** (meters from
home). They exist only while armed, so a rule like `height > 120 -> !above your approved height` (a ceiling warning) is silent on the ground.

## Dashboard: a second page with cards only

The **Dashboard** tile in the MENU switches to a second page with no video (and the camera rests); the same tile, now named **Video**,
switches back. The page you left is the page the app starts on. It is meant for pilots in goggles, who do not use the HDMI input, and for a
second pair of eyes beside the pilot. The warnings, the connecting banner and the MENU button stay on top of it, and a replay of a flight log
runs on it too.

* **Cards.** Horizon, compass, flight statistics, radar map, live charts, stick view and any number of **value cards**. The statistics,
  radar, charts and sticks are the same cards as on the video, and take their rows, channels and chart values from the same settings.
* **Horizon.** An artificial horizon from the pitch and roll angles of the flight controller (smoothed a little: telemetry arrives a few times
  a second). INAV counts a nose down pitch as positive, and the horizon follows that. Settings > Dashboard has *Horizon: invert pitch* and
  *invert roll* for a flight controller that does it the other way: tilt the aircraft nose down on the bench; the horizon should show more
  ground. The raw gyro rates are not sent in telemetry (only the blackbox has them), so the horizon shows angles, not rates.
* **Compass.** A rose that turns with the heading (the flight controller's yaw, or the GPS course when there is no yaw), the heading in
  degrees, and a green marker that points the way home, with the distance.
* **Value cards.** Pick any value (the same list as the rule editor), give it a label and a unit, choose the decimals, and set an amber
  and a red limit with the side that is bad (a battery gets worse downwards, a current upwards). A text value such as the flight mode is
  shown as text. The first dashboard has the cell volts, the current, the link quality and the flight mode.
* **Layout.** Settings > Dashboard > *Edit the dashboard layout* opens the same editor as the video cards: drag a card, drag its square
  handle to resize it, and use *Add a card*, *Remove a card* and *Edit a value card* in the bar. *Reset the dashboard layout* brings back the
  first dashboard. The layout is part of the settings backup.

## Flight logs and replay

**Settings > Flight logs** (off by default) writes a log of every flight, from arming to disarming (the arm switch you set under
*Arm detection*). One CSV file per flight, named like `AX-HUD_2026-11-07_14-55-33_Akb7custom001.csv`, in the app's own folder
`Android/data/dev.ax12.announcer/files/flights/` on the radio (no storage permission needed; copy it out over USB, or open it in a
spreadsheet). The top of the file lists the app, the aircraft, the pack, the cells and the arm switch; the last line is a summary.

* **Values.** *Values in the log* lists every channel and telemetry value the radio publishes (the same list as the rule editor), each with a
  tick box. Presets: *Replay set* (every standard value, the starting choice), *Everything* (adds the advanced ones) and *None*. The list
  warns when a replay would miss something (the arm channel, the sticks, position, speed, current, link, volts or a heading).
* **Rate.** 1, 2, 5, 10 (default) or 25 rows per second. About 10 MB an hour at 10 rows a second with the replay set.
* **Safe by design.** The log is written on its own thread. If the storage is slow the rows are dropped and counted (the last line says
  how many); if it fails, or less than 20 MB is free, logging stops and Settings says why. Nothing a log meets can reach the radio link or
  the screen. The file is flushed every second and closed when you disarm or leave the screen, and a file that was cut off is still
  readable. Only the newest 20, 50, 100 or 200 flights are kept (*Flights to keep*).
* **Flight list.** *Flight logs: list, replay and delete* shows each flight: date, aircraft, length, pack, highest point, farthest
  distance, top speed, peak current, lowest cell, mAh used. Each can be replayed or deleted; the flight being recorded is marked.
* **Replay.** The statistics, radar (with the track), stick view, channel bars and charts are drawn from the log by the same code that
  draws the live flight, over a dark backdrop with a red REPLAY tag (the video is not recorded). A bar at the bottom has Close, Play or
  Pause, the speed (1x, 2x, 4x, 8x), a scrub bar and the time. Seeking rebuilds the statistics and the chart from the start of the log, so
  they are right at any position. There is no voice and no warning bar in a replay, and arming the aircraft ends it at once.
  A card shows only what the log holds: with fewer values chosen, some cards stay empty (a toast names what is missing).
* **Privacy.** A log contains GPS positions. It stays on the radio unless you copy it. Do not publish a log that shows where you fly.

## Start-up time of the radio link

**Why the sticks take about 12 seconds to move after the app starts.** Measured on an AX12 with the logcat of both apps: the SDK's own
serial-port start-up takes a fixed ~12 s (11 of 12 recorded launches took 11.9 to 12.2 s from the SDK's first port scan to the port
opening), and the first channel data arrives 24 ms after the port opens. AX-HUD's own part is about 1 s (the SDK call takes 22 ms).
RadioMaster's stock app is told its serial ports up front (`ttyS0` and `ttyS4` at 921600 baud) and opens the port 4.4 s after it starts.
The public SDK has no way to pass a port: `initialize()` only takes a data folder, it exports no other function for it, and
`postMessage()` is not meant for it. So this time cannot be shortened from the app; it needs an SDK change by RadioMaster. (The
"denied" lines in logcat are harmless: this radio runs SELinux in permissive mode.)

**What the app does about it**

* The radio link is started on a **background thread**, as RadioMaster's demo does (the device information is requested first and again
  when the configuration is loaded; the configuration is asked for again only after 6 s), so the screen and the camera are not held up.
  While it starts no data and no pretend data is shown.
* **The link stays up as long as any screen of the app is on show.** That includes the text editor for the sound, warning and check
  rules, which is a separate screen: it no longer stops the link, however long you edit, and nothing has to be started again
  (about 12 s without stick data) when you come back. The link is released only when the app has left the display (Home, another
  app, screen off, or the app closed), and then at once, so the radio's serial port is free for the stock app.
* **Rebuilds and file pickers.** A rebuild (changing language or aircraft, importing settings) and the screens other apps provide
  (the file pickers, the browser link) take the whole app off the display for a moment. The link is then kept for 10 s after a
  rebuild and 40 s after a picker or the browser, and released only if the app does not come back in that time.

**Timings.** *Settings > Diagnostics > Show the variables panel* starts with a timeline, in milliseconds since the app's process started:
the screen created, the SDK start called, `initialize()` returned, the message callback set, each time the configuration was requested,
the first channel message (mixout), the device information answered, the configuration loaded, the channel list available, and the first
sample in the app that can move the sticks. The same lines go to logcat under the tag `ax12timing` (`adb logcat -s ax12timing`).
*Show the start-up timings of the last launches* keeps one line for each of the last 10 launches
(`start | call took | init done | mixout | config | requests | graph`); `graph` is the time until the first stick value reached the
screen. If you capture logcat with PowerShell's `>`, the file is UTF-16; use `| Out-File -Encoding utf8` to get UTF-8.

## Looks: one style, plain rectangles, see-through panels, icons

* **Plain rectangles.** Nothing in the app has rounded corners: the cards, the MENU, the panels, the buttons, the dialogs, the switches
  (a square thumb in a square track), the layout editor's frames and handles, the icons' rectangles. The radar is a square card; only
  its range rings stay round.
* **Settings rows: label and value.** A setting that cycles through values is one row: the label on the left, the value on the right
  in blue ("Size ... Medium"), instead of "Size: Medium (tap to change)". Rows are at least 48 dp tall, and long labels wrap, so German
  and Turkish fit.
* **The MENU is a grid of eight large square tiles** (two rows of four: Sound rules, Speech, Battery, Aircraft, Dashboard or Video, Flight logs, Settings, Exit), each
  with an icon, a name and, where there is something to show, its state: Speech shows On or Off, Battery the pack in use, Aircraft the
  aircraft in use, in words and in a coloured bar along the bottom (green On, red Off, blue for the pack and aircraft). Speech and
  Battery keep the menu open when tapped, so the new state can be seen at once.
* **Connecting banner.** While the radio link comes up (the SDK needs about 12 s) a card at the top says "Connecting to the radio...
  7 s" and shows a strip that fills towards 12 s; after 20 s it says what to check (close the RadioMaster app, check that the radio is
  on). It goes when the radio answers.
* **HUD numbers** use a condensed bold font with digits of equal width, so values do not jitter and take less room.
* **Warning bar.** A thicker bar (48 dp) with a shape in front of the text, so it does not depend on colour alone: a triangle for
  critical, a diamond for warning, a circle for info. A critical warning breathes slowly (70 % to full) instead of blinking.
* **No-video screen.** A big camera-off icon, "No video", and one line saying what is wrong or what to check; the technical line stays
  small along the bottom.
* **Touch targets and spacing.** Everything you tap is at least 48 dp; spaces and sizes follow a 4/8 dp grid (a test checks the new
  screens). **Long text:** buttons wrap to two lines and then shrink (down to 11 sp) instead of being cut off; tiles, group headers
  and the warning bar do the same (Android 8 and newer, which is what the AX12 runs).
* **One motion rule.** Every fade and slide takes 150 to 250 ms (220 in, 160 out). Panels slide in from the right edge, the menu from
  the top, cards fade; a test checks that no animation uses its own time.
* **See-through menu and panels.** The MENU box and the backdrop of Settings, About and the rules let the video show through, so you
  can see that it is there while you change a setting. Rows, notes and text sit on darker blocks, so they stay readable over moving video.
  Dialogs stay nearly solid, because you type in them.
* **Exit.** The MENU ends with an **Exit** item (in a warning colour, below a divider). It asks first, then closes the app, removes it
  from the recent apps and releases the radio's serial port.
* **One look everywhere.** Every card (sticks, statistics, radar, checklist, charts) shares the same background, edge and colours, and
  so do the menu and the panels. Buttons are flat and dark, dialog buttons are written like every other button (not in capitals), and
  every dialog is dark with light blue buttons.
* **Capitals and spelling.** Every text starts with a capital letter (option values, column headings, error lines, hints, captions);
  no text is half in capitals and half not; one spelling (American) everywhere; one product name and one tagline on the splash and the
  About page. The all-capitals HUD labels (SPEED, READY, ...) are on purpose. A test checks all of this, in English, German and Turkish.
* **Settings are grouped.** *Settings* shows eight collapsible groups with icons (Language and display, Aircraft and battery,
  Overlays on the video, Video and speech, Rules and backup, Updates, Diagnostics, About); tap a group to open it, one at a time. The
  first group is titled in four languages on purpose, so it can always be found.
* **Icons** in the MENU and in the Settings groups. They are described in a small text file (`app/src/main/assets/icons.txt`), so they
  can be changed or extended without touching the code.
* **Soft fades.** Cards fade in and out instead of popping; the MENU fades in.
* **About page** (*Settings > About > About AX-HUD, licenses and thanks*): the license of the app, a link to the project, and every
  library and SDK inside the app with its license text, readable offline.

## Aircraft profiles and packs

Each aircraft can have its own setup. *Settings > Aircraft and battery > Aircraft* (and the MENU's **Aircraft** item) switch between them; *Manage the
aircraft* adds one (a copy of the current setup, or the starting settings), renames and deletes.

* **Per aircraft:** sound rules, warning rules, pre-flight checks, battery profiles and packs, aux channel bars (names),
  stick mapping, arm switch and current limit.
* **Shared by all:** overlay looks and places, language, speech language, camera and video settings, updates.
* Switching saves what you changed on the aircraft you leave and loads the other one; nothing is lost on the way. The screens are
  rebuilt for a moment. Your existing setup becomes the first aircraft, "My aircraft".
* The pre-flight checklist shows the aircraft and the pack under its title, so you see before arming that the right setup is loaded.
* The **full settings backup includes every aircraft**.

**Packs** are the named packs of an aircraft (*Settings > Aircraft and battery > Battery > Edit the packs*): a name, the battery profile it uses and its
capacity (for your records). With packs, the MENU's Battery item chooses the **pack** and the pack chooses the profile (Li-ion or
LiPo limits); the voice says the pack's name. With no packs it switches the profile directly, as before.

## Backup of all settings

*Settings > Rules and backup > Backup of all settings* writes **one text file with everything**: sound rules, warning rules, pre-flight checks,
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

**Settings > Rules and backup > Edit the rules as text (advanced)** edits the same rules as plain text, one rule per line:

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

GPS coordinates are shown on screen. They are also written to a file **only if you switch on flight logs** (Settings > Flight logs, off by default), and that file stays on the radio. Do not commit flight logs or screenshots that show where you fly.

## Updates from GitHub

**Settings > Updates > Check for updates now** looks on GitHub for a newer build, downloads it, checks its SHA-256, and
hands it to Android's installer, which asks you to confirm. Two channels: **Nightly** (the newest build, published by CI on every push to `main`, versions like `0.7.1-nightly.45`) and
**Stable**, the default (the latest GitHub release, made with one click from the Actions tab or by pushing a tag such as `v1.0.0`). The app can
also look for news a few seconds after it starts (switchable). Every build is signed with **one permanent key** kept in GitHub
secrets, which is what lets each build install over the last one without uninstalling. The one-time setup, the daily routine and
the error messages are in **[UPDATES.md](UPDATES.md)**. The only network use is fetching two files from github.com; the app has
the internet permission for that and nothing else.

## Start-up splash screen

On a fresh start the app shows the logo with the version (name, version number and build number, read from the app
itself) for **2 seconds**, then fades out. Two seconds is the usual range for a splash screen (1 to 3 s): long enough to
read, short enough not to get in the way. A tap or the Back key skips it. The app starts up behind it (camera, radio
link, settings), so it adds no waiting. Android's own start-up screen already shows the same navy and logo, so there is no
white flash before it. A switch in Settings > Language / Dil / Sprache > Display turns the splash off.

## Logo

`docs/logo.png` (512 px) and `docs/logo-768.png` are the AX-HUD logo: a flat navy rounded square with the radio, the
drone, the gauge and the name. The launcher icons are built from it. Android 8 and newer (the AX12 runs Android 9) use
an adaptive icon: navy background and the artwork as the foreground, kept inside the safe zone so every launcher shape
shows it whole. Older Android versions get the full design. The logo has no third-party logos in it, and every edge is
pure navy (no light fringe).

## Acknowledgments

Thanks to **RadioMaster** for publishing the AX SDK that makes this app possible. AX-HUD is an independent project and is not
affiliated with RadioMaster. The app also includes the Qt libraries that come with the SDK (LGPL).

## License

AX-HUD is free software under the **GNU General Public License, version 3 or later** (GPL-3.0-or-later), with an **additional permission**
that allows it to be combined with RadioMaster's AX SDK (see [LICENSE](LICENSE)). In short: you may use, study, change and share it;
if you share a changed version, or a program built on it, you must share the source code under the same license.

* **Other terms.** The copyright holder can grant a separate written license on other terms to a particular party. Nobody else can: without
  such an agreement, the GPL applies to everyone.
* **Components.** The RadioMaster SDK, the Qt libraries and the other components that are inside the APK keep their own licenses; they are
  listed, with links and the full texts, in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and in the app under *Settings > About > About
  AX-HUD, licenses and thanks*.
* **Contributions.** See [CONTRIBUTING.md](CONTRIBUTING.md): a contribution needs a short contributor agreement, so that the project can
  keep licensing consistent.
* **Name.** "AX-HUD" and the logo are the maintainer's; a changed version should use another name.
