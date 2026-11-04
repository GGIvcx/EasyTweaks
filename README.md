# EasyTweaks

Zeigt in der Serverliste eine MOTD mit dem Namen eines zufälligen Online-Spielers an.

Teil der **EasySystems**-Pluginreihe von Ivcx.

## Features

- Optionale feste erste MOTD-Zeile
- Zweite Zeile: zufälliger Spruch mit zufälligem Online-Spielernamen (`%player%`)
- Fallback-Text, wenn niemand online ist
- Spielernamen werden nie als MiniMessage interpretiert
- Tweak „Drachenei“: Spieler mit einem Drachenei im Inventar leuchten und sind von Ender-Partikeln umgeben (Spectator und unsichtbare/vanished Spieler ausgenommen)

## Voraussetzungen

- Paper 26.3
- Java 25
- Keine weiteren Plugins nötig

## Installation

1. `EasyTweaks-1.0.0.jar` in den Ordner `plugins/` legen.
2. Server neu starten.

Keine Datenordner-Migration nötig.

## Befehle

- `/easytweaks` (Alias `/tweaks`) – öffnet das Admin-Menü: Tweaks per Linksklick ein-/ausschalten (Drachenei: Klick), per Rechtsklick Einstellungen öffnen (MOTD: erste Zeile und Fallback per Chat ändern, Sprüche im Untermenü hinzufügen, bearbeiten und löschen).

## Permissions

- `easysystems.tweaks.gui` – Menü öffnen (Standard: op)
- `easysystems.tweaks.*` – alle Rechte

## Konfiguration

`plugins/EasyTweaks/config.yml`:

```yaml
# Präfix für Chat-Rückmeldungen (MiniMessage-Format)
prefix: '<gold>EasyTweaks</gold> <dark_gray>┃</dark_gray> '
# MOTD-Tweak ein- oder ausschalten (auch per /easytweaks)
motd-enabled: true
# Drachenei-Tweak ein- oder ausschalten: Spieler mit Drachenei im Inventar leuchten und haben Ender-Partikel
dragon-egg-enabled: true
# Optionale erste MOTD-Zeile (MiniMessage). Leer lassen, um sie zu deaktivieren.
line1: "<gradient:gold:yellow><bold>EasySystems</bold></gradient>"
# Wird angezeigt, wenn niemand online ist.
fallback: "<gray>Der Server schläft... weck ihn auf!"
# %player% = zufälliger Online-Spieler
sayings:
  - "<green>Bei <yellow>%player%</yellow> beliebt"
  - "<green><yellow>%player%</yellow> hat den Server gebaut <gray>(behauptet er)"
  - "<green>Empfohlen von <yellow>%player%"
  - "<yellow>%player%</yellow> <green>ist schon wieder online..."
  - "<green>4 von 5 Zahnärzten empfehlen <yellow>%player%"
  - "<green>Dank <yellow>%player%</yellow> noch nicht explodiert"
  - "<yellow>%player%</yellow> <green>hat nur kurz Diamanten geholt"
  - "<green>Von <yellow>%player%</yellow> <green>persönlich getestet, nie für gut befunden"
  - "<yellow>%player%</yellow> <green>sagt: Ich lag nicht in der Lava!"
  - "<green>Creeper fürchten <yellow>%player%"
```

- `motd-enabled`: schaltet den MOTD-Tweak ein/aus.
- `dragon-egg-enabled`: schaltet den Drachenei-Tweak ein/aus (wirkt sofort).
- `line1`: erste MOTD-Zeile; leer lassen, um sie zu deaktivieren.
- `fallback`: zweite Zeile, wenn niemand online ist (oder `sayings` leer ist).
- `sayings`: Liste möglicher Sprüche; `%player%` wird durch einen zufälligen Online-Spieler ersetzt.

Alle Texte nutzen das [MiniMessage-Format](https://docs.advntr.dev/minimessage/format.html). Die Config wird beim Start geladen. Manuelle Änderungen an der Datei wirken erst nach einem Neustart; Änderungen über das Menü gelten sofort (die MOTD liest die Config bei jeder Abfrage).

## Bauen

```bash
JAVA_HOME=/pfad/zu/jdk-25 mvn clean package
```

Das Ergebnis liegt in `target/EasyTweaks-1.0.0.jar`.
