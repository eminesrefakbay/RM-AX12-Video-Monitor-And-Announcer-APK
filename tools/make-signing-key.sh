#!/usr/bin/env bash
# AX-HUD: video monitor, HUD and voice announcer for the RadioMaster AX12.
# Copyright (C) 2026 eminesrefakbay
# SPDX-License-Identifier: GPL-3.0-or-later
# With the additional permission for the RadioMaster AX SDK described in the LICENSE file.

# Makes the AX-HUD signing key (once, on your own computer) and prints the two values for GitHub secrets.
# Needs a JDK (keytool). KEEP ax-hud.jks SAFE and NEVER commit it: whoever has it can sign updates for your app.
set -euo pipefail
command -v keytool >/dev/null || { echo "keytool not found: install a JDK (for example Temurin 17) and run again"; exit 1; }
OUT="${1:-ax-hud.jks}"
[ -e "$OUT" ] && { echo "$OUT already exists: not overwriting it"; exit 1; }
PASS="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
keytool -genkeypair -keystore "$OUT" -alias ax-hud -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$PASS" -keypass "$PASS" -dname "CN=AX-HUD, O=AX-HUD" >/dev/null
echo
echo "Created $OUT (keep a backup somewhere safe, outside this repository)."
echo
echo "Add these two secrets in GitHub: your repository > Settings > Secrets and variables > Actions > New repository secret"
echo
echo "  Name:  SIGNING_PASSWORD"
echo "  Value: $PASS"
echo
echo "  Name:  SIGNING_KEYSTORE_BASE64"
echo "  Value: (the long text below, all on one line)"
echo
base64 -w0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'
echo
