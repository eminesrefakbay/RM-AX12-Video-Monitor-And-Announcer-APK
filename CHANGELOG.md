# Changelog

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
