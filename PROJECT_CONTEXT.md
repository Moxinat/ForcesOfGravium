# ForcesOfGravium Project Context

Du arbeitest in meinem Projekt `ForcesOfGravium`, einem Hytale-Mod-Projekt.

Wenn es eine `AGENTS.md`, Projektzusammenfassung oder andere lokale Hinweise gibt, lies sie zuerst. Nutze sie als Ausgangspunkt, aber pruefe relevante Details im Code, in der lokalen Hytale-API und in `Assets.zip` nach, bevor du darauf basierend Aenderungen machst.

Bevor du irgendetwas aenderst, verschaffe dir gruendlich Kontext ueber das Projekt und die lokale Hytale-API. Veraendere dabei keine Dateien. Es geht erstmal nur darum, das Projekt, die Architektur und die vorhandenen Systeme zu verstehen.

Wichtig: Auch wenn du aehnliche Hytale- oder Minecraft-Konzepte kennst, rate keine API- oder JSON-Strukturen. Dieses Projekt muss gegen lokale Hytale-JARs, vorhandene Mod-Dateien und `Assets.zip` geprueft werden.

## Aktueller Arbeitsstand

- Siphon-Drop zu Empty-Output existiert.
- Siphon kann Item-Entities von Empty-Input aufnehmen.
- Siphon-Drops werden ueber `ItemComponent.generateItemDrop(...)` und `CommandBuffer.addEntity(...)` erzeugt.
- Item-Entity-Entfernung laeuft ueber `CommandBuffer.removeEntity(...)`, nicht ueber direkte Store-Writes.
- Inverter gibt Support wie Siphon.
- Wooden Button hat Custom-Hitbox, Press/PressedAlt-State-Animation und steht wieder auf `BlockNormal`.
- Der initiale `ConnectableNetworkUpdateService.ensureInitialized(...)`-Scan im Tick-System ist deaktiviert, weil er World-Crashes ausgeloest hat.

## 1. Projektstruktur

Bitte pruefe insbesondere:

- Welche Java-Packages gibt es?
- Welche Ressourcen/JSONs gibt es unter `src/main/resources`?
- Welche Bloecke, Items, Texturen, Models und Icons sind im Mod definiert?
- Welche Commands, Events, Systems, Stores, Persistence-Logik und Registries existieren?
- Pruefe auch Tests unter `src/test/java`, weil dort wichtige Logik-Annahmen abgesichert sind.

## 2. Hytale-API-Kontext

- Nutze die lokal installierten Hytale Server-JARs aus dem Gradle-Cache.
- Pruefe relevante Klassen mit `javap`, statt zu raten.
- Achte besonders auf:
  - ECS Systems: `EntityTickingSystem`, `DelayedEntitySystem`, `EntityEventSystem`
  - `CommandBuffer`
  - Entity-Spawning und Entity-Removing
  - Block Events: `PlaceBlockEvent`, `BreakBlockEvent`
  - World/Chunk APIs
  - Methoden, die Chunks laden, starten oder Store-Aenderungen ausloesen koennen
  - Block rotations: `Rotation`, `RotationTuple`
  - Block components / `BlockComponentChunk`
  - Containers: `ItemContainer`, `ItemContainerBlock`, `ProcessingBenchBlock`, `BenchBlock`
  - Inventory transactions: `MoveTransaction`, `ItemStackTransaction`, `ListTransaction`
  - Multiblock/Filler APIs: `FillerBlockUtil`, `BlockSection`, Base/Filler block handling
  - Block placement via `BlockAccessor.placeBlock(...)`

## 3. Assets.zip

- Nutze die lokale `Assets.zip`, um Vanilla-JSONs zu pruefen.
- Suche Beispiele fuer:
  - normale Container/Kisten
  - grosse Kisten / Multiblock-Strukturen
  - Furnace / Processing Bench
  - Support / Supporting
  - Block rotation / placement settings
  - Block states / State.Definitions
  - BlockEntity Components
  - Interactions / Use / ChangeState
  - Animation-States
- Leite keine JSON-Properties frei her, sondern bestaetige sie an vorhandenen Assets.

## 4. Aktueller Mod-Kontext

Bitte verstehe besonders diese vorhandenen Konzepte:

- Connectable-System
- Gravity Powder
- Wooden Button
- Inverter
- Gravium Siphon
- Rotation-System
- Propagation-System
- Network-Scanner / Power-Scanner
- WorldSaveFileService / `worldsave.json`
- Siphon-Transferlogik fuer Container, Doppel-Kisten und Processing Benches
- Siphon-Transferlogik fuer Empty-Input und Empty-Output
- `powered`- und `locked`-Status beim Siphon
- Siphon-State-Texturen und JSON-States
- Block-State-IDs wie `*_State_*`, nicht nur Base-Block-IDs

## 5. Bekannte wichtige Stolperfallen

- `DelayedEntitySystem` erzeugt sichtbaren Delay. Fuer schnelle Kabel-Propagation wird aktuell `EntityTickingSystem` genutzt.
- Pro World darf ein Tick-System trotz Player-Query nur einmal pro World/Tick arbeiten.
- Siphon-Transfer ist zeitlich separat von Kabel-Propagation. Nicht alles kuenstlich auf denselben Delay legen.
- `GraviumSiphonBlockRefresher.refreshAt(...)` darf nicht dauerhaft pro Tick fuer jeden Siphon laufen, sondern nur bei echtem State-Wechsel oder Initial-Sync.
- Siphon-State-Erkennung muss `ConnectableRegistry.isGraviumSiphonId(...)` nutzen, weil JSON-States eigene Block-IDs erzeugen koennen.
- Inverter-Scans muessen bidirektional funktionieren:
  - Push-Seite zu Pull-Seite
  - Pull-Seite zurueck zur Push-Seite
- Fuer `locked` startet der Scan oft auf der Pull-Seite eines Inverters und muss trotzdem die Source auf der Push-Seite finden.
- Multiblock-Container wie Doppel-Kisten brauchen Base/Filler-Block-Aufloesung, sonst landet man auf dem falschen BlockEntity-Ref.
- Bei Processing Benches/Furnaces muessen Input/Fuel/Output-Slots respektiert werden. Nicht einfach irgendeinen Container nehmen.
- Bei Inventory-Transfers muss geprueft werden, ob die Transaction wirklich erfolgreich war und kein Remainder uebrig bleibt.
- Bei Item-Entity-Verbrauch muss Stack-Reduktion oder Entity-Entfernung sauber passieren und darf keine Items duplizieren.
- In ECS-Systemen niemals direkt `Store.addEntity(...)`, `Store.removeEntity(...)` oder aehnliche direkte Store-Writes benutzen.
- Fuer Entity-Aenderungen im Tick-System `CommandBuffer` verwenden.
- `World.execute(...)` ist nicht automatisch sicher fuer Block-/Chunk-Scans. Tasks koennen immer noch in einem Zeitpunkt laufen, in dem Stores verarbeitet werden.
- Full-World- oder Initial-Scans beim Join sind gefaehrlich, wenn sie `world.getBlockType(...)` aufrufen und dadurch Chunks starten/laden koennen.
- Der alte `ConnectableNetworkUpdateService.ensureInitialized(...)`-Initialscan wurde aus dem Tick-System entfernt, weil er World-Crashes durch `Store is currently processing` ausgeloest hat.
- Wenn ein Initial-Sync wieder eingefuehrt wird, muss er mit einer API gebaut werden, die keine Chunks startet und keine Store-Writes waehrend Store-Processing ausloest.
- Button-States nutzen aktuell `default`, `Pressed` und `PressedAlt`, damit die Press-Animation durch State-Wechsel mehrfach triggerbar ist.
- Button-Rotation steht aktuell wieder auf `BlockNormal`, weil `FacingPlayer`/State-Rotation vorher problematisch war.
- JSON-Properties fuer Blocks/Items/States/Supporting/Placement/Interactions nicht raten, sondern aus `Assets.zip` oder vorhandenen Mod-Dateien bestaetigen.
- Aenderungen klein und gezielt machen. Keine unrelated Refactors.

## 6. Vorgehensweise

- Verwende bevorzugt `rg` zum Suchen.
- Verwende `javap` fuer API-Klassen.
- Verwende `tar -xOf`, `tar -tf` oder passende Zip-Reads fuer `Assets.zip`.
- Aendere keine Dateien, bis ich explizit sage, dass du etwas umsetzen sollst.
- Wenn du eine Vermutung hast, kennzeichne sie klar als Vermutung.
- Wenn API/Assets etwas bestaetigen, sag genau welche Klasse oder Datei das bestaetigt.
- Wenn du spaeter Code aenderst, arbeite klein und gezielt, passend zu vorhandenen Patterns.
- Nach Aenderungen immer `.\gradlew.bat test --console=plain` laufen lassen, falls die Aenderung Java betrifft.
- Wenn nur Ressourcen/JSONs geaendert wurden, fuehre mindestens `.\gradlew.bat processResources --console=plain` aus.

## Erwartete Zusammenfassung nach Kontextpruefung

Am Ende gib bitte eine kompakte Uebersicht:

- Was sind die wichtigsten Systeme im Projekt?
- Welche Hytale-API-Stellen sind fuer dieses Projekt besonders wichtig?
- Welche Projekt-Konventionen sollte Codex beachten?
- Welche bekannten Stolperfallen gibt es?
- Welche offenen Fragen oder Risiken sollten wir im Kopf behalten?

Wichtig: Noch nichts aendern. Erst lesen, pruefen, verstehen und zusammenfassen.
