# Security policy

AX-HUD is a hobby project by one person. Security reports are welcome and are handled on a best-effort basis.

## Supported versions

The latest stable release. Older versions are not patched; update from *Settings > System > Updates*.

## How to report a vulnerability

Please report it **privately**, not in a public issue:

* with GitHub's *Security > Report a vulnerability* on this repository, if it is offered, or
* by e-mail to **eminesrefakbay@gmail.com**, with "AX-HUD security" in the subject.

Say what you found, which version, and how to repeat it. I will try to answer within about a week, say whether I can reproduce it, and tell you
when a fix is released. If you want to be named in the release notes, say so.

## What AX-HUD does, so that you know the surface

* It reads the radio's channels and telemetry through RadioMaster's AX SDK; it never writes to the radio.
* It has no account and no server of its own. Its only network use is the optional update check against this repository's GitHub releases,
  over HTTPS. Installing an update needs the *install packages* permission, and Android asks you to confirm every update and checks that it
  is signed with the same key.
* Flight logs, the last known position and diagnostics reports are files on the radio. They can hold GPS positions: protect the radio, and read
  a report before you share it (the diagnostics report itself has no GPS positions).
* Bluetooth and the location permission are used only to find and talk to your antenna tracker (Android requires the location permission for
  Bluetooth searches; the app does not read the location).

## Known limitations

* **The antenna tracker's Bluetooth link has no pairing and no encryption.** Anyone within range who knows the protocol can connect to a
  tracker and move its servos or change its calibration. The tracker is experimental; use it away from strangers.
* **`ax-hud.apk` applies a small in-memory patch** to the RadioMaster SDK, to get around its serial lock file on Android (documented in the
  README; `ax-hud-plain.apk` does not).

## Not in scope

Vulnerabilities in RadioMaster's SDK, in the AX12's firmware or in other apps belong with their authors (RadioMaster: https://github.com/Radiomaster-RC/AX12).
