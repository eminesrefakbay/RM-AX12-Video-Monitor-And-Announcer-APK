<p align="center"><img src="docs/logo.png" width="160" alt="AX-HUD-Logo"></p>

<p align="center"><a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a> · <b>Deutsch</b> · <a href="README.zh.md">中文</a></p>

# AX-HUD: Videomonitor, HUD und Ansager für die AX12

**AX-HUD** ist eine Android-App für die RadioMaster AX12. Sie **liest** nur von der Fernsteuerung und schreibt nie eine Einstellung.

## Was sie kann

* Zeigt das Video vom HDMI-Eingang im **Vollbild**; ohne Bild erscheint unten eine schmale Statusleiste, und sie verbindet sich von selbst neu.
* **Spricht Ansagen** nach Regeln, die du selbst schreibst, z. B. `ch5 >= 1500 -> Motoren scharf`. Die Regeln bearbeitest du in der App: Schwellen, `and`/`or`-Logik, Schaltererkennung, Live-Werte wie `{vbat/6:2}`.
* Eine **Warn-Statusleiste** mit derselben Logik (kritisch = rot und blinkend, Warnung = gelb, Info = blau; auf Wunsch nach N Sekunden ausgeblendet).
* **Einblendungen:** Stick-Ansicht und Aux-Schalterbalken (Namen änderbar, umkehrbar), Flugstatistik (Höchstgeschwindigkeit, Maximalhöhe, Spitzenstrom), Radar und Checkliste vor dem Flug.
* **Updates von GitHub:** Einstellungen > Updates. Zwei Kanäle: Nightly (jeder Push) und Stable (Releases). Details: [UPDATES.md](UPDATES.md).
* **Sprache:** Englisch, Türkisch, Deutsch, Chinesisch. Einstellungen > Sprache. Die Stimme kann eine andere Sprache sprechen als die Anzeige.

## Voraussetzungen und Installation

* RadioMaster **AX12** (Android 9). Getestet vom Autor: Systemupdate Oktober 2026, INAV 9.1, ExpressLRS. Andere Aufbauten sind **ungetestet**.
* Lade `ax-hud.apk` von der Seite **Releases** herunter (die Version mit dem Patch für die serielle Sperre; sie läuft auf der AX12) und installiere sie.
* **Schließe** die eigene RadioMasterAX-App der AX12: Den seriellen Port kann immer nur eine App nutzen. Beim Schließen von AX-HUD wird er zurückgegeben.

## Sicherheit

Die App ist experimentell und nur als **zweite Informationsquelle** gedacht; sie ersetzt nicht das OSD deines Flugcontrollers. Die Nutzung erfolgt auf eigenes Risiko. Fliege im Rahmen der geltenden Vorschriften. Aktualisiere nie während eines Fluges.

## Datenschutz

Die Internet-Berechtigung dient nur dazu, die Update-Information und die APK von github.com zu holen. Es werden keine Daten gesendet.

## Dank

Danke an **RadioMaster** für die Veröffentlichung des AX SDK, das diese App möglich macht. AX-HUD ist ein unabhängiges Projekt und nicht mit RadioMaster verbunden.

Alle Einzelheiten (Bauen, serielle Sperre, Regeln, Tests) stehen im [englischen README](README.md). Korrekturen an den Übersetzungen: [TRANSLATING.md](TRANSLATING.md).
