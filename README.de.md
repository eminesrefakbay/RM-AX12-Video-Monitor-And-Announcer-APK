<p align="center"><img src="docs/logo.png" width="160" alt="AX-HUD-Logo"></p>

<p align="center"><a href="README.md">English</a> · <a href="README.tr.md">Türkçe</a> · <b>Deutsch</b> · <a href="README.zh.md">中文</a></p>

# AX-HUD: Videomonitor, HUD und Ansager für die AX12

> **Haftungsausschluss:** AX-HUD ist ein unabhängiges Open-Source-Projekt und steht in keiner offiziellen Verbindung zu RadioMaster; es wird von RadioMaster weder unterstützt noch empfohlen. Die Nutzung erfolgt auf eigenes Risiko. Die App liest Telemetriedaten nur aus und schreibt keine Einstellungen auf die Fernsteuerung. Für deine Ausrüstung, Konfiguration und Flugsicherheit bist allein du verantwortlich.

**AX-HUD** ist eine Android-App für die RadioMaster AX12. Sie **liest** nur von der Fernsteuerung und schreibt nie eine Einstellung.

## Was sie kann

* Zeigt das Video vom HDMI-Eingang im **Vollbild**; ohne Bild erscheint unten eine schmale Statusleiste, und sie verbindet sich von selbst neu.
* **Spricht Ansagen** nach Regeln, die du selbst schreibst, z. B. `ch5 >= 1500 -> Motoren scharf`. Die Regeln bearbeitest du in der App: Schwellen, `and`/`or`-Logik, Schaltererkennung, Live-Werte wie `{vbat/6:2}`.
* Eine **Warn-Statusleiste** mit derselben Logik (kritisch = rot und blinkend, Warnung = gelb, Info = blau; auf Wunsch nach N Sekunden ausgeblendet).
* **Einblendungen:** Stick-Ansicht und Aux-Schalterbalken (Namen änderbar, umkehrbar), Flugstatistik (Höchstgeschwindigkeit, Maximalhöhe, Spitzenstrom), Radar und Checkliste vor dem Flug.
* **Menü:** neun Kacheln: Soundregeln, Sprache, Akku, Fluggerät und Sonnenlicht in der ersten Reihe; Dashboard/Video, Flugprotokolle, Einstellungen und Beenden in der zweiten.
* **Updates von GitHub:** Einstellungen > Updates. Zwei Kanäle: Nightly (jeder Push) und Stable (Releases). Details: [UPDATES.md](UPDATES.md).
* **Sprache:** Englisch, Türkisch, Deutsch, Chinesisch. Einstellungen > Sprache. Die Stimme kann eine andere Sprache sprechen als die Anzeige.

## Voraussetzungen und Installation

* RadioMaster **AX12** (Android 9). Getestet vom Autor: Systemupdate Oktober 2026, INAV 9.1, ExpressLRS. Andere Aufbauten sind **ungetestet**.
* Lade `ax-hud.apk` von der Seite **Releases** herunter (die Version mit dem Patch für die serielle Sperre; sie läuft auf der AX12) und installiere sie.
* **Schließe** die eigene RadioMasterAX-App der AX12: Den seriellen Port kann immer nur eine App nutzen. Beim Schließen von AX-HUD wird er zurückgegeben.

## Umgehung der seriellen Sperre

Das SDK greift über Qt's `QSerialPort` auf die serielle Verbindung zu und erstellt dabei eine Sperrdatei. Da eine Android-App kein beschreibbares Sperrverzeichnis hat, kann die Verbindung möglicherweise nicht geöffnet werden. Dann zeigt „Variablen anzeigen“ `radio config: NOT loaded yet` und `mixout msgs` bleibt auf 0.

* Vor dem SDK-Start setzt die App Qt's temporäres Verzeichnis (`TMPDIR`) auf ihren eigenen Datenordner.
* Um die erforderliche `QSerialPort`-Sperrdatei unter Android zu umgehen, verwendet die App einen Speicher-Patch ähnlich dem der offiziellen Demo. Die CI erstellt zusätzlich `ax12-announcer-debug-lockpatch.apk` mit `-PaxLockPatch=true`. Der Patch wird nur angewendet, wenn beide Maschinenbefehle exakt den erwarteten Werten entsprechen; in der normalen APK ist er deaktiviert.
* Schließe zuerst die RadioMasterAX-App der AX12: Zwei Programme dürfen den seriellen Port nicht gleichzeitig verwenden.
* Suche in `adb logcat` nach `qt`, `serial`, `lock` oder `ax12bridge` sowie nach `Failed to create a lock file`.
* Die dauerhafte Lösung wäre eine offizielle Möglichkeit von RadioMaster, die Verbindung zu öffnen. Bitte RadioMaster darum.

## Sicherheit

Die App ist experimentell und nur als **zweite Informationsquelle** gedacht; sie ersetzt nicht das OSD deines Flugcontrollers. Die Nutzung erfolgt auf eigenes Risiko. Fliege im Rahmen der geltenden Vorschriften. Aktualisiere nie während eines Fluges.

## Datenschutz

Die App verbindet sich für Updates mit github.com und sendet keine Daten an andere Stellen. Wenn ein GPS-Fix vorliegt, speichert sie die letzte bekannte Position auch während des Fluges und bei einem Verbindungsverlust in `last_position.txt` im App-Ordner auf der Fernsteuerung; das ist unabhängig von den Flugprotokollen. Wenn du Flugprotokolle aktivierst (standardmäßig aus), können auch die CSV-Dateien GPS-Positionen enthalten. Drei schnelle Berührungen oben am Bildschirm, nicht auf MENU, speichern einen PNG-Screenshot des ganzen Bildschirms einschließlich sichtbarer Koordinaten in `Pictures/AX-HUD`. Diese Dateien bleiben auf der Fernsteuerung, sofern du sie nicht kopierst. Veröffentliche keine Protokolle oder Screenshots, die zeigen, wo du fliegst.

## Dank

Danke an **RadioMaster** für die Veröffentlichung des AX SDK, das diese App möglich macht. AX-HUD ist ein unabhängiges Projekt und nicht mit RadioMaster verbunden.

Alle Einzelheiten (Bauen, serielle Sperre, Regeln, Tests) stehen im [englischen README](README.md). Korrekturen an den Übersetzungen: [TRANSLATING.md](TRANSLATING.md).

## Lizenz

AX-HUD ist freie Software unter der **GNU General Public License, Version 3 oder später** (GPL-3.0-or-later) mit einer **zusätzlichen Erlaubnis**, die die Verbindung mit dem AX-SDK von RadioMaster gestattet: [LICENSE](LICENSE). Kurz: Du darfst es benutzen, untersuchen, ändern und weitergeben; gibst du eine geänderte Version oder ein darauf aufbauendes Programm weiter, musst du den Quellcode unter derselben Lizenz weitergeben. Der Rechteinhaber kann einer bestimmten Partei eine gesonderte schriftliche Lizenz zu anderen Bedingungen erteilen; sonst niemand. Das RadioMaster-SDK, die Qt-Bibliotheken und die übrigen Teile im APK behalten ihre eigenen Lizenzen; sie sind mit den vollen Texten in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) und in der App unter *Einstellungen > Über* aufgeführt. Zu Beiträgen siehe [CONTRIBUTING.md](CONTRIBUTING.md).
