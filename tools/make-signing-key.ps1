# Makes the AX-HUD signing key (once, on your own computer) and prepares the two values for GitHub secrets.
# Needs a JDK (keytool). Install one with:  winget install EclipseAdoptium.Temurin.17.JDK
# KEEP ax-hud.jks SAFE and NEVER commit it: whoever has it can sign updates for your app.
# Run:  powershell -ExecutionPolicy Bypass -File make-signing-key.ps1
$ErrorActionPreference = "Stop"
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool not found: install a JDK first (winget install EclipseAdoptium.Temurin.17.JDK), open a new window and run again"
}
$out = Join-Path (Get-Location) "ax-hud.jks"
if (Test-Path $out) { throw "$out already exists: not overwriting it" }
$chars = (48..57) + (65..90) + (97..122)
$pass = -join ($chars | Get-Random -Count 24 | ForEach-Object { [char]$_ })
keytool -genkeypair -keystore $out -alias ax-hud -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $pass -keypass $pass -dname "CN=AX-HUD, O=AX-HUD" | Out-Null
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($out))
Write-Host ""
Write-Host "Created $out (keep a backup somewhere safe, outside the repository)."
Write-Host ""
Write-Host "In GitHub: your repository > Settings > Secrets and variables > Actions > New repository secret"
Write-Host ""
Write-Host "Secret 1   Name: SIGNING_PASSWORD"
Write-Host "           Value: $pass"
Write-Host ""
Write-Host "Secret 2   Name: SIGNING_KEYSTORE_BASE64"
Write-Host "           Value: the long text, now on your clipboard (paste it)"
Set-Clipboard -Value $b64
Write-Host ""
Write-Host "(The long text was copied to the clipboard. Create secret 1 first, then come back for secret 2.)"
Write-Host "If the clipboard was replaced, copy it again with:  Get-Content ax-hud.jks -Encoding Byte | ... or run this script's last lines."
