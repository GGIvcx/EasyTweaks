# EasyTweaks

Paper plugin with small toggleable server tweaks (MOTD with random player name, dragon egg glow, sitting, item frames, player heads, phantoms, Happy Ghast speed, Nether/End lock).

## Build

```bash
JAVA_HOME=/home/jan/.jdks/temurin-25 mvn -q clean package
```

Default system java is 21 and cannot compile against paper-api 26.3, so always set JAVA_HOME. Output: `target/EasyTweaks-1.0.0.jar` (finalName = artifactId-version).

## Structure

- Standard Maven layout, but package is `de.easysystems.easytweaks` (differs from the `de.ivcx.*` packages of the sibling plugins).
- `EasyTweaks` (main class, Listener): `onPing(PaperServerListPingEvent)` builds the MOTD from config (skipped when `motd-enabled: false`); also handles `/easytweaks` and the `msg(...)` helper (prefix from config); `tickDragonEgg()` (repeating task, 10 ticks) implements the Drachenei tweak (glow + PORTAL particles, tracked `glowing` UUID set, vanish detection uses the standard `vanished` metadata, e.g. set by EasyVanish); `%player%` is rewritten to the `<player>` tag and resolved with `Placeholder.unparsed`.
- `TweaksGui` (Listener): admin GUI (36-slot main menu with one item per tweak, MOTD and Happy-Ghast submenus, paginated sayings menu with add/edit/delete + delete confirmation), custom `Menu` InventoryHolder, chat input via `AsyncChatEvent` (`Input` record: config key, or saying add/edit with expected old text for stale-index checks). `onPing` reads `sayings` from config on every ping. New tweak = one `tweak(...)` in `openMain` + an entry in `mainKey`.
- `SmallTweaks` (Listener): event tweaks Sitzen (non-persistent PDC-tagged marker ArmorStand seats, `seats` map + 1s cleanup timer), armor stand arms, invisible item frames, player heads, no phantoms, Happy Ghast speed (`FLYING_SPEED` = 0.05 × factor), Nether-/End-Sperre (`PlayerPortalEvent`, `EntityPortalEvent` incl. Passagiere, Enderperlen, `PortalCreateEvent` FIRE + Enderauge-Platzierung, target world Environment NETHER/THE_END, keys `nether-lock-enabled`/`end-lock-enabled` default false, 3s message rate-limit, bypass `easysystems.tweaks.bypass.dimensions`; persönlicher Schalter als PDC-Flag `bypass-off` via `EasyTweaks#bypass/toggleBypass`, `/tweaks bypass` + GUI-Slot 22; Fahrzeug erlaubt, sobald ein Spieler-Passagier wirksamen Bypass hat).
- `src/main/resources/plugin.yml`, `config.yml` (keys: prefix, `<tweak>-enabled` toggles read live, happy-ghast-speed, line1, fallback, sayings). Command `/easytweaks` (alias `/tweaks`), permission `easysystems.tweaks.gui` (+ `easysystems.tweaks.*`).

## Conventions

- Paper API 26.3 + Adventure/MiniMessage only. No `§`, no ChatColor, no legacy serializers. No exceptions in this plugin.
- Use `Placeholder.unparsed` for any user input / player names in MiniMessage strings.
- All user-facing texts German, du-form.
- Permissions (if ever added): `easysystems.tweaks.<action>` plus `easysystems.tweaks.*` with children, declared in plugin.yml.
- Config keys lowercase kebab-case, German comments; add the `prefix` key as soon as chat feedback exists.
- plugin.yml: 2-space indent, order name, version (`'${project.version}'`), main, api-version '26.3', author, description, prefix, commands, permissions.
- Part of the EasySystems suite (siblings: EasyBans, EasyTeams, EasyHelp, EasyDeathCount, EasyTweaks, EasyVanish) – keep all plugins consistent.

## Design language

Identisch in allen EasySystems-Plugins – bei Änderungen überall anpassen.

Root: /run/media/jan/Massenspeicher A/Jan/Development/workspace spigot/EasySystems
Author: Ivcx. Target: Paper API 26.3 (paper-api 26.3.build.237-stable), Adventure + MiniMessage.

### 1. Text-API
- ALLE Ausgaben über Adventure/MiniMessage (`MiniMessage.miniMessage().deserialize(...)`).
- Kein `§`, kein `ChatColor` (weder org.bukkit noch net.md_5), kein `LegacyComponentSerializer`, kein `translateAlternateColorCodes`.
- Spielernamen/Nutzereingaben NIE roh in MiniMessage-Strings einsetzen: `Placeholder.unparsed("name", value)` bzw. TagResolver verwenden.
- Deprecated Bukkit-String-APIs ersetzen durch Component-Varianten (sendMessage(Component), Bukkit.createInventory(holder, size, Component), meta.displayName/lore, AsyncChatEvent statt AsyncPlayerChatEvent, player.sendActionBar(Component), ...).

### 2. Chat
- Präfix: `<gold>{PluginName}</gold> <dark_gray>┃</dark_gray> ` (PluginName = z.B. EasyBans, EasyTeams, EasyHelp, Vanish).
- Farben (Rollen):
  - Fließtext: `<gray>`
  - Werte, Spielernamen, Zahlen, Befehle: `<yellow>`
  - Erfolg: `<green>` (ganze Nachricht nach Präfix)
  - Fehler: `<red>` (ganze Nachricht nach Präfix)
  - Hervorhebung/Überschrift: `<gold>`
  - Trenner/Klammern: `<dark_gray>`
- Jede Chatnachricht an Spieler beginnt mit dem Präfix (Ausnahme: mehrzeilige Blöcke wie Hilfe/Info → nur Header).
- Header/Footer mehrzeiliger Blöcke:
  - Header: `<dark_gray><st>        </st></dark_gray> <gold>Titel</gold> <dark_gray><st>        </st></dark_gray>`
  - Footer: `<dark_gray><st>                              </st></dark_gray>`
  - Zeilen: `<dark_gray>»</dark_gray> <gray>Label: <yellow>Wert`
- Klickbare Buttons in Chat: `<dark_gray>[</dark_gray><green>Annehmen</green><dark_gray>]</dark_gray>` mit `<hover:show_text:...>` / `<click:run_command:...>`.
- Standardtexte (Deutsch, Du-Form, mit Punkt):
  - Nur Spieler: `<red>Dieser Befehl ist nur für Spieler.`
  - Keine Rechte: `<red>Dafür hast du keine Berechtigung.`
  - Verwendung: `<red>Verwendung: <yellow>/befehl <argument>`
  - Spieler nicht gefunden: `<red>Der Spieler <yellow><name></yellow> wurde nicht gefunden.`
  - Chat-Eingabe-Prompt: `<gray>Gib <...> im Chat ein. <dark_gray>(</dark_gray><yellow>abbrechen</yellow><dark_gray>)</dark_gray>`
  - Abbruch: `<gray>Eingabe abgebrochen.`
- Actionbar/Titel: gleiche Farbrollen, kein Präfix.
- Sprache: ausschließlich Deutsch, Du-Form (auch Konsolen-Logs für Nutzer, Command-Descriptions in plugin.yml).

### 3. Sounds (Player#playSound, Volume 1f, Pitch 1f)
- Erfolg: `Sound.ENTITY_EXPERIENCE_ORB_PICKUP`
- Fehler (Rechte, falsche Verwendung, nicht gefunden): `Sound.ENTITY_VILLAGER_NO`
- GUI-Klick auf aktives Item: `Sound.UI_BUTTON_CLICK`
- Nicht bei jeder Info-Zeile, nur bei Ergebnis einer Aktion.

### 4. GUI
- Titel: `<dark_gray>Titel` (z.B. `<dark_gray>Gebannte Spieler <gray>(1/2)`).
- Füller: `GRAY_STAINED_GLASS_PANE`, displayName `Component.empty()`, alle leeren Slots.
- Item-Namen: Rollenfarbe (gold = Hauptobjekt/Spieler, green = bestätigen/positiv, red = destruktiv, yellow = Option/Navigation, aqua = Sonderfunktion), immer `decoration(ITALIC, false)` (bzw. `<!italic>` in MiniMessage).
- Lore: `<gray>`, Werte `<yellow>`, nicht kursiv. Klick-Hinweis als letzte Lore-Zeile: `<dark_gray>» <yellow>Klicken zum …`.
- Navigation (unterste Reihe):
  - Vorherige Seite: `ARROW`, `<yellow>Vorherige Seite`, Slot = erster Slot der letzten Reihe (bei 54: 45)
  - Nächste Seite: `ARROW`, `<yellow>Nächste Seite`, letzter Slot (bei 54: 53)
  - Zurück: `ARROW`, `<yellow>Zurück`, Mitte der letzten Reihe (bei 54: 49)
  - Schließen: `BARRIER`, `<red>Schließen`, Mitte der letzten Reihe (wenn kein Zurück)
- Bestätigungsdialog: `LIME_WOOL` `<green>Bestätigen`, `RED_WOOL` `<red>Abbrechen`.
- Leerzustand: `BARRIER` `<red>Keine Einträge` (passend formuliert) mittig.
- Klicks/Drags in Plugin-GUIs immer canceln; UI_BUTTON_CLICK bei Klick auf aktives Item.

### 5. plugin.yml
- 2 Spaces Einrückung, Reihenfolge: name, version, main, api-version, author, description, prefix, website (falls vorhanden), depend/softdepend, commands, permissions.
- `name` = Ordnername (EasyBans, EasyTeams, EasyHelp, EasyDeathCount, EasyTweaks, Vanish).
- `version: '${project.version}'` mit Maven resource filtering (falls nicht vorhanden: einrichten).
- `api-version: '26.3'`, `author: Ivcx`, `description:` deutscher Satz, `prefix:` = name.
- Commands: deutsche `description`, `usage` in der Form `/<command> <argument>`.
- Alle Permissions in plugin.yml deklarieren, mit deutscher description und default.

### 6. Permissions
- Schema: `easysystems.<plugin>.<aktion>` lowercase, plugin = bans | teams | help | deathcount | tweaks | vanish.
- Pro Plugin zusätzlich `easysystems.<plugin>.*` mit children.

### 7. Konfigurierbarer Präfix
- Jedes Plugin mit Chat-Rückmeldungen hat eine `config.yml` (in src/main/resources bzw. neben plugin.yml bei Non-Standard-Layout, per saveDefaultConfig) mit dem Top-Level-Key:
  `prefix: '<gold>{PluginName}</gold> <dark_gray>┃</dark_gray> '`
  inkl. deutschem Kommentar darüber: `# Präfix für Chat-Rückmeldungen (MiniMessage-Format)`.
- Der Präfix wird beim Enable (und ggf. Reload) gelesen und von der Msg-Hilfsklasse verwendet. Fehlt der Key → Default.
- Config-Keys generell: lowercase kebab-case auf oberster Ebene, deutsche Kommentare.

### 8. Plugin-Liste
EasyBans, EasyTeams, EasyHelp, EasyDeathCount, EasyTweaks, EasyVanish (vorher "Vanish"). Permission-Kürzel: bans, teams, help, deathcount, tweaks, vanish.
