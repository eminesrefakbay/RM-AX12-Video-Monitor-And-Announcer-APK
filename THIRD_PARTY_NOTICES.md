# Third-party notices

AX-HUD's own code is released under the **GNU General Public License, version 3 or later**, with an **additional permission** for the
RadioMaster AX SDK and the Qt libraries delivered with it (see [LICENSE](LICENSE)). That license covers only the files in this
repository. The APK that is built from it also contains the third-party components below, which keep their own licenses. The same list
is in the app: *Settings > About > About AX-HUD, licenses and thanks*, where every license text can be read.

How the licenses fit together: the Qt libraries (LGPL v3), the Kotlin standard library and the LLVM C++ runtime (Apache License 2.0) may
all be used in a GPL v3 program. The RadioMaster SDK is the exception: it is not a GPL component, so AX-HUD's license contains an additional
permission under section 7 of the GPL that lets anyone combine and convey AX-HUD together with the SDK, on condition that the SDK's own
terms are respected and that all of AX-HUD's own code stays under the GPL.

AX-HUD is an independent project. It is **not affiliated with, endorsed by or supported by RadioMaster**. RadioMaster, AX12 and other
names and trademarks belong to their owners.

## RadioMaster AX SDK

- Holder: RadioMaster. Published for app developers at <https://github.com/Radiomaster-RC/RadiomasterAX_SDK>.
- It reads the radio's channels and telemetry. Its libraries (`librmAxSdk1.so`) are included in the APK.
- The SDK is **not stored in this repository**: the build downloads it from RadioMaster's repository, so the terms RadioMaster publishes
  there apply to it. AX-HUD does not relicense it.
- Thank you, RadioMaster, for publishing the SDK.

## Qt 6 (Core, Network, SerialPort, Concurrent)

- Holder: The Qt Company. <https://www.qt.io/licensing>. Source code: <https://download.qt.io>.
- These libraries (`libQt6Core`, `libQt6Network`, `libQt6SerialPort`, `libQt6Concurrent`) come with the RadioMaster SDK and are included
  in the APK as delivered with it. The SDK states that they follow Qt's open source license; the SDK's copy of the Qt license
  information is in [app/src/main/assets/licenses/Qt-LICENSE.txt](app/src/main/assets/licenses/Qt-LICENSE.txt). Qt's modules used here
  are available under the **GNU LGPL v3**, whose text (with the GPL v3 it refers to) is in
  [app/src/main/assets/licenses/LGPL-3.0.txt](app/src/main/assets/licenses/LGPL-3.0.txt).
- The libraries are linked dynamically and are separate files in the APK. If you build AX-HUD yourself, the Qt libraries are
  copied from the SDK into `app/src/main/jniLibs/arm64-v8a`, and you can put your own build of the same Qt version there instead.

## LLVM C++ runtime (libc++)

- Holder: the LLVM Project. <https://llvm.org/LICENSE.txt>.
- `libc++_shared.so`, the C++ runtime of the Android NDK, which the SDK needs. License: Apache License 2.0 with LLVM Exceptions; the
  text is in [app/src/main/assets/licenses/Apache-2.0.txt](app/src/main/assets/licenses/Apache-2.0.txt).

## Kotlin standard library

- Holder: JetBrains s.r.o. and Kotlin contributors. <https://kotlinlang.org>. License: Apache License 2.0
  ([text](app/src/main/assets/licenses/Apache-2.0.txt)).
- Added by the Kotlin build plugin; the app is written in Kotlin and Java.

## Artwork

- The logo and the launcher icon are the author's own artwork. The in-app icons are simple shapes drawn by the app itself
  (`app/src/main/assets/icons.txt`) and are covered by the GPL like the rest of the code.
