# Changelog

## 1.17.3

The trail button of the replay has a third choice, Off: no ribbon and no colour legend. The FPV camera angle (how far a quad's camera looks up from the nose) is a
setting, 25 degrees by default, and can be changed by dragging up or down in the FPV view.

## 1.17.2

Hills fitted to the flight: the ground is lowered, by up to 60 m and never raised, until the lowest point of the log is a metre above it, so the ribbon does not
dip under the hills when the log height and the elevation data disagree. A switch in Settings > Display turns it off.

## 1.17.1

A fifth camera in the 3D replay: FPV. The view is from the aircraft, along its nose (a quad camera tilted up 25 degrees), with the horizon turning with the roll.
Satellite pictures on the hills work with Hills in 3D left on.

## 1.17.0

Hills under the 3D replay. The map now lies on a rough 3D shape of the land (no contour lines, no labels), made from public elevation tiles (AWS Terrain Tiles)
that are fetched once per flight and kept on the radio. Settings > Display > Hills in 3D: Off, Real size, Twice as high, Three times as high. The aircraft,
drop lines, flags and the HOME ring stand on the ground, the camera stays above it, and the credit line names the elevation data. The heights are for looking
and not exact; the flight ribbon keeps the logged heights. Without elevation data the ground stays flat.

## 1.16.1

Choose the kind of map under the 3D replay: a street map (OpenStreetMap), a terrain map with contour lines (OpenTopoMap), satellite pictures (Esri World
Imagery), or the address of your own tile server. The Map button of the replay opens a menu (Off, Street map, Satellite, Terrain), and Settings has the map
type and the custom address. Terrain and satellite maps stop at zoom 17. When no tile can be had, the corner of the view says why. Each kind has its own cache.

## 1.16.0

A map under the 3D replay. The ground of the replay is now a map: the tiles of OpenStreetMap by default, or the tile address of your own, laid on the ground in
perspective under the ribbon. The tiles that cover a flight are fetched once when you open its replay and kept on the radio, so the map also shows
without a network at the field. A Map button in the replay and a switch in Settings (Display) turn it on and off; without a map the grid stays. The credit of the map
shows in the corner. The settings backup holds the switch and the tile address, and only takes an https address with the tile placeholders.

## 1.15.4

The replay: the buttons on the right no longer reach down onto the player bar (they use two columns when the screen is too short for one), and the red
REPLAY tag at the top fades out together with the bar and the buttons, so it no longer covers the cards. The still logo screen that Android showed before the
animated splash is gone: the app now opens on plain navy and goes straight into the splash.

## 1.15.3

The flight summary page fits on one screen without scrolling. The figures are a little smaller, the voltage and current chart and the track share the height
that is left, and the buttons (replay, export, copy the text, close) sit in one row at the bottom.

## 1.15.0

Flight tools. A return reserve card on the HUD compares what is left in the pack with what the flight home needs and calls out before the margin is gone; its
numbers are kept for each pack. The replay lists the dives, rolls and flips of the flight (marks on the scrub bar, a tap jumps there) and shows a report for a
dive. Every log has a summary page, and can be exported as GPX, KML, CSV or report text, with an option to take the GPS positions out. Backup and restore lets
you choose what goes in, shows the date of the last backup and reminds you after 30 days. The layout can now change with the aircraft, with a switch to turn it
off.

## 1.14.0

A new player for the 3D replay. The scrub bar now runs over the whole width at the bottom, like a video player's, with a mark for every event of the flight
(take-off, mode changes, the highest point, landing). Under it: Stop, the previous and next event, 10 seconds back and forward, Play or Pause, the time, the
speed (0.5x, 1x, 2x, 4x, 8x) and Loop. On the right side a rail holds Close, the camera (Chase, Orbit, Pilot and a new Top view), the aircraft (quad or
plane), the colour of the trail (by speed or by height), a button that hides the cards, and a snapshot. The bar and the rail fade out after four seconds and
come back with a touch.

## 1.13.4

The quad of the 3D replay is drawn with filled shapes like the plane: a long body plate, battery, camera pod, arms with orange front ones, prop discs and
red and green lights. Both models are a little bigger.

## 1.13.3

The aircraft of the 3D replay is easier to read: an arrow along its heading at its own height and a triangle on the ground, a plane made of filled shapes, a
little bigger models and a higher chase camera. In a fast run the aircraft is pitched 30 degrees down, which could look like flying tail first.

## 1.13.2

The 3D replay showed the pitch the wrong way: the log holds pitch positive with the nose down, so the nose rose in a straight run. Fixed.

## 1.13.1

The replay bar along the bottom fades out after four seconds and comes back on any touch, so it does not cover the sticks and the bottom cards. The
3D replay can draw the aircraft as a quad or as a plane (a button on the replay bar; the choice is remembered).

## 1.13.0

The 3D flight replay, first version. A flight log now replays in 3D behind the cards: a speed-coloured ribbon over a flat ground with a horizon, the
aircraft, a home ring and flags at the events. The motion is smooth (a curve through the logged points for position and attitude, a view clock that
runs on every screen frame, a camera that follows with a half-life) and there are three cameras: chase, orbit and pilot.

## 1.12.1

Fixes found in the first real AX-HUD flight log. The signal strength (RSSI) was shown and logged wrongly (-201 for -55 dBm): it is read correctly
now. The log rate no longer offers 25 rows per second (the radio is polled 20 times a second) and the log's last line states the rate the
flight really got. `win_left` goes negative after the approval window has ended instead of going empty. The descriptions of the `dbm`, `lost`
and `tx_power` values say what they really are. The README has a new section, "Reading a flight log".

## 1.12.0

Settings are tabs now, with a search. A rail on the left holds eight tabs (General, Aircraft and flight, Video and overlays, Dashboard, Antenna
tracker, Flight logs, Rules and backup, System); the 14 collapsible groups of earlier versions are folded into them, and the two Diagnostics
groups are one section. A search field in the title bar finds a setting in every tab by its words. The rows themselves are unchanged.

## 1.11.2

The magnetic declination dialog of the antenna tracker no longer names a place: it asks for degrees, east positive. The tracker firmware's
stored default is now 0 (it was a value for one town), and the tracker README no longer names a place either.

## 1.11.1

A notice about the RadioMaster SDK, in the README (all four languages) and in the app's About page: the release APK contains RadioMaster's SDK
libraries, the SDK repository states no licence, RadioMaster has not answered the questions about redistribution, and what will happen if
someone objects. The README title shows the current version again, and a new test keeps the notice and the version in step.

## 1.11.0

**Antenna tracker.** AX-HUD can drive an antenna tracker: an ESP32 with two servos, its own GPS and a compass, reached over Bluetooth LE. The radio
sends the aircraft's position, speed, course and height five times a second; the tracker works out where the aircraft is (with dead reckoning
between the slow telemetry updates) and points the antennas, with a flip so that two 180-degree servos cover every direction. Settings >
Antenna tracker: choose the tracker, follow or hold, centre, test sweep, compass calibration, declination, antenna height. The firmware, the
wiring and the build instructions are in the new `tracker/` folder; its geometry and packets are tested on a PC against the same packets as the
app. Not yet tested on hardware.

## 1.10.3

Screenshots now work on the AX12's Android 9: the app declares and asks for both storage permissions (the radio lists read and write as two
switches, and granting write alone left the save failing), the folder check no longer depends on read access, and any failure is caught and
written to the diagnostics events.

## 1.10.2

A fix release. The first screenshot no longer pauses the radio link (its storage permission dialog is now protected like the camera's, and is
never asked for while the aircraft is armed). The README, the Settings label of the splash and the serial-lock section now match the app
(shapes, the animated splash, the bundled Chinese font, the real APK names). Two small clean-ups: the horizon's expiry timer leaves with its
view, and a stale code comment is corrected. New tests keep the README and these fixes honest.

## 1.10.1

Added a triple-tap gesture along the top edge of the screen to save a full-screen PNG to `Pictures/AX-HUD`. The capture includes the video or dashboard and is available without tapping the MENU button. Screenshot messages and instructions are translated into Turkish, German and Chinese.

## 1.10.0

Dashboard layouts can now be saved, loaded and deleted by name, with Full flight, Minimal flight and Channel bench presets. Saved layouts are included in settings backup and restore, and the new controls are translated into Turkish, German and Chinese. Radio polling and parsing now run off the UI thread, and dashboard instruments avoid redraws when their visible data has not changed.

## 1.8.0

A new animated splash screen (the split design). Chinese can be chosen on every radio: the app carries its own small Chinese font. Buttons
and tappable rows follow the shape style (no more plain squares in Settings and the rules). Toasts use the app's fonts.

## 1.7.0

Shapes: cards, tiles, rows and the warning bar can have two cut corners (the new default), four cut corners, corner brackets, soft corners or
plain rectangles, chosen in Settings > Display > Shape. Warnings keep a solid fill.

## 1.6.0

New fonts: Manrope for text, Teko (a narrow techno face) for titles and the big numbers, with a typography scale (card titles in capitals,
semi-bold headings, medium labels) and digit slots so that numbers never shift sideways. Both fonts are open (SIL OFL) and credited in About.

## 1.5.0

Diagnostics and a bug-report button: a page with the versions, the device, the radio link and its start-up timing, a digest of the settings,
the last events and the record of a crash, as a report to copy, share or save, with no positions and nothing personal in it.

## 1.4.0

Sunlight theme: dark text on light backgrounds for the menu, the settings and the other screens, the dashboard page and the dialogs, with
stronger warnings (saturated colours, a thick outline, bigger text, thicker marks). A Sunlight tile in the MENU is the quick switch, and
there is a setting. The MENU now has nine tiles.

## 1.3.0

Around the flight: the last known position when the link is lost (card, voice, saved note), an approval window with a time-left chip and
spoken warnings, distance, energy and efficiency of every flight (cards, rules, logs and the flight list), a pack book with totals per pack,
and a voice debrief after landing. The build now pins the SDK commit.

## 1.2.4

Editing the layout starts only when you hold one spot of the dashboard or of the video for 5 seconds: a swipe, a drag or a second finger
cancels it, so swiping no longer opens the editor by accident.

## 1.2.3

Editing the layout starts with a long press, on the dashboard and on the video (as well as from Settings), and the tick returns straight to
the page you were on.

## 1.2.2

The layout editor strip can be folded to one small arrow button and unfolded again (remembered), and steps aside while a card is dragged.

## 1.2.1

The layout editor: no explanatory text and no wide bar over the top of the screen, only a small see-through strip of icon buttons
(+ add, - remove, pencil edit, grid, reverse arrow reset, tick done), and a grid with snapping for moving and resizing cards.

## 1.2.0

A dashboard page (cards only, no video): horizon, compass, value cards, statistics, radar, charts and sticks, with its own editable layout
and a Dashboard / Video tile in the MENU, which now has eight tiles.

## 1.1.0

Flight logs and replay: a CSV log of every flight from arming to disarming (off by default; chosen values, rate and number of flights),
a flight list with a summary of each, and a replay on the radio of the radar, sticks, channel bars, charts and statistics.

## 1.0.0

First stable release. Since 0.15: a welcome guide on the first start; an X on the pre-flight card; Stable is the default update channel; GPL v3 or later with an
additional permission for the RadioMaster AX SDK, a contributor agreement, and a license header in every source file; a check that skips
the Chinese screen language on a device without a Chinese font; a test that estimates whether translated texts fit; screenshots.

## 0.15

A plainer look: rectangles everywhere, see-through panels, a tile MENU, a warning bar with shapes and a pulse, a "No video" screen,
a banner that counts the wait for the radio, label-and-value Settings rows, one motion rule.

## 0.10 to 0.14

Aircraft profiles and packs, live charts, drag-and-drop cards, grouped Settings, an About page with every license, start-up diagnostics
and fixes (keyboard in dialogs, the radio link kept alive while a screen of the app is shown).

Earlier versions: see the commit history and the Releases page.
