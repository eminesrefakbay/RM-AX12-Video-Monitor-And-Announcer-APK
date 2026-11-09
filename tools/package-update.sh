#!/usr/bin/env bash
# AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
# Copyright (C) 2026 eminesrefakbay
# SPDX-License-Identifier: GPL-3.0-or-later
# With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

# Writes update.json and SHA256SUMS next to the APK, for the app's updater.
#   usage:  CHANNEL=nightly RUN_NUMBER=42 VERSION_NAME=1.10.1-nightly.42 NOTES="what changed" tools/package-update.sh dist/ax-hud.apk
# The version code is 1000 + the CI run number (the same rule as app/build.gradle.kts).
set -euo pipefail
APK="${1:?usage: package-update.sh path/to/ax-hud.apk}"
DIR="$(dirname "$APK")"
NAME="$(basename "$APK")"
CHANNEL="${CHANNEL:-nightly}"
RUN="${RUN_NUMBER:-0}"
NOTES="${NOTES:-}"
VNAME="${VERSION_NAME:-$(sed -n 's/^val baseVersion = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)}"

python3 - "$APK" "$NAME" "$CHANNEL" "$RUN" "$VNAME" "$NOTES" > "$DIR/update.json" <<'PY'
import hashlib, json, os, sys
apk, name, channel, run, vname, notes = sys.argv[1:7]
digest = hashlib.sha256(open(apk, "rb").read()).hexdigest()
code = 1000 + int(run) if int(run) > 0 else 16
first_line = notes.strip().splitlines()[0][:300] if notes.strip() else ""
print(json.dumps({
    "versionCode": code,
    "versionName": vname,
    "channel": channel,
    "apk": name,
    "sha256": digest,
    "size": os.path.getsize(apk),
    "notes": first_line,
}))
PY
( cd "$DIR" && sha256sum ./*.apk > SHA256SUMS )
echo "wrote $DIR/update.json and $DIR/SHA256SUMS"
