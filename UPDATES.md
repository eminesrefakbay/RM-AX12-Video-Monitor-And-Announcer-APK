# Updating AX-HUD from GitHub

Every push to `main` builds the app and publishes it as the **Nightly** release, and the app on the radio can fetch it: **Settings > Updates > Check for updates now**,
then *Download and install*, then confirm in Android's dialog. No cable, no copying files.

## Why installing was painful before

Android only installs an APK over an installed app when both are signed with **the same key**. GitHub's build machine makes
a brand-new throw-away debug key for every run, so every build looked like a stranger to Android and could only be
installed after uninstalling the old one (which also wipes the app's settings). The fix is one permanent key of your own,
kept in GitHub's secrets, used for every build.

## One-time setup (about 10 minutes)

1. **Make the key, on your own computer** (not inside the repository folder).
   - You need a JDK. On Windows: `winget install EclipseAdoptium.Temurin.17.JDK`, then open a new window.
   - Windows: `powershell -ExecutionPolicy Bypass -File tools\make-signing-key.ps1`
     Linux / macOS: `bash tools/make-signing-key.sh`
   - It creates `ax-hud.jks` and shows a **password** and a very long **text** (Windows also puts the text on the clipboard).
   - **Keep `ax-hud.jks` somewhere safe and never put it in the repository.** Whoever has it can sign updates for your app,
     and if you lose it you have to uninstall and reinstall once more.
2. **Give GitHub the two values.** On the repository page: *Settings > Secrets and variables > Actions > New repository secret*.
   - Name `SIGNING_PASSWORD`, value: the password.
   - Name `SIGNING_KEYSTORE_BASE64`, value: the long text.
3. **Push this version** (the update script does it). The build now signs with your key and publishes a **Nightly build**
   under *Releases* (a few minutes; watch the *Actions* tab).
4. **Install once the hard way.** Open *Releases > Nightly build*, download `ax-hud.apk` (the build with the serial-lock patch
   that works on the AX12). Export your rules first (Settings > Sound rules > Export), uninstall the old AX-HUD, install this one.
   Android asks you to allow installs from your file manager or browser: allow it.
5. **From now on** every new build installs over the old one, with your settings kept.

## Every day

1. Push the change to GitHub (the update script).
2. Wait for the green check on the *Actions* tab.
3. On the radio, with the phone hotspot on: **Settings > Updates > Check for updates now > Download and install > confirm.**
   The first time, Android asks you to allow installs from AX-HUD: open the page it shows, switch it on, come back and run
   the update again (the download is kept).
4. On Android 9 the app reopens by itself. On newer versions, open it.

The app can also look for news by itself a few seconds after it starts (a short notice, no interruption). Turn that off
under *Settings > Updates > Check at start-up*.

## The two channels

**Stable is the default channel** (since version 1.0.0): a new installation follows the tested releases. Choose Nightly in Settings > Updates if you want every build.

| | **Nightly** | **Stable** |
| --- | --- | --- |
| What it is | the newest build: every push to `main` replaces it | a release you made on purpose |
| Version name | `0.7.1-nightly.45` (the number counts builds) | `0.7.1` |
| When | within minutes of a push | when you decide |
| Use it for | trying the latest changes | the version you fly with |

Choose the channel under *Settings > Updates*. Each channel only offers builds **newer than the one installed** (compared by
the build number), so switching from Nightly to a Stable release that was built earlier says "you have the latest": it will not
step backwards. (Going back to an older build means uninstalling once.)

**Making a stable release.** First set `baseVersion` in `app/build.gradle.kts` to the number you want (for example `0.7.1`)
and push it. Then either:

- **On GitHub (easiest):** *Actions* tab > *build* > **Run workflow** > type the tag in the box (`v0.7.1`) > *Run workflow*.
  It builds, signs, creates the tag and publishes the release (the notes are written from your commits).
- **Or from a terminal:** `git tag v0.7.1 && git push origin v0.7.1`.
- **Or** create a release by hand on GitHub with a new tag that starts with `v`: the build attaches the APK to it.

A tag that already exists is never moved: use a new tag for each release.

## What protects you

- Only `https://github.com/...` addresses are used, and only for two files: `update.json` and the APK.
- The APK's SHA-256 is checked against `update.json` before Android is asked to install it.
- Android itself refuses an APK that is not signed with your key, so nobody else can push a "fake update".
- Nothing is sent to anyone: the app only asks for those two files. It has the internet permission for that and nothing else.
- Do not update during a flight: the app restarts. The control link does not depend on the app, but your video and warnings do.

## When something goes wrong

| Message | Meaning and fix |
| --- | --- |
| No build has been published on this channel yet | The first build is not finished, or (Stable) there is no release yet, or (Nightly) a new build is being published right now: try again in a minute. Look at the *Actions* and *Releases* pages. |
| Could not check: ... Is the phone hotspot on? | No internet. Turn the hotspot on and connect the radio to it. |
| The installed copy was signed with a different key | The installed app came from an older build with a throw-away key. Uninstall it once, install the new build (step 4). |
| Installation stopped: ... DOWNGRADE | The build on GitHub is older than the one installed (version code). |
| The checksum does not match | The download was damaged or is not the published file: nothing was installed. Try again. |
| Warning in the Actions log: *No signing secrets found* | Step 2 was not done (or the names are misspelled). The APK is built but cannot update an installed app. |

