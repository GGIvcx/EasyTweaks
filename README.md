# EasyTweaks

Zeigt in der Serverliste eine MOTD mit dem Namen eines zufälligen Online-Spielers an.

Teil der **EasySystems**-Pluginreihe von Ivcx.

## Features

- Optionale feste erste MOTD-Zeile
- Zweite Zeile: zufälliger Spruch mit zufälligem Online-Spielernamen (`%player%`)
- Fallback-Text, wenn niemand online ist
- Spielernamen werden nie als MiniMessage interpretiert

## Voraussetzungen

- Paper 26.3
- Java 25
- Keine weiteren Plugins nötig

## Installation

1. `EasyTweaks-1.0.0.jar` in den Ordner `plugins/` legen.
2. Server neu starten.

Keine Datenordner-Migration nötig.

## Befehle

Keine.

## Permissions

Keine.

## Konfiguration

`plugins/EasyTweaks/config.yml`:

```yaml
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

- `line1`: erste MOTD-Zeile; leer lassen, um sie zu deaktivieren.
- `fallback`: zweite Zeile, wenn niemand online ist (oder `sayings` leer ist).
- `sayings`: Liste möglicher Sprüche; `%player%` wird durch einen zufälligen Online-Spieler ersetzt.

Alle Texte nutzen das [MiniMessage-Format](https://docs.advntr.dev/minimessage/format.html). Die Config wird beim Start geladen; Änderungen an der Datei wirken erst nach einem Neustart des Servers (es gibt keinen Reload-Befehl).

## Bauen

```bash
JAVA_HOME=/pfad/zu/jdk-25 mvn clean package
```

Das Ergebnis liegt in `target/EasyTweaks-1.0.0.jar`.
