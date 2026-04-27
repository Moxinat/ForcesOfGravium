# ForcesOfGravium Project Context

Du arbeitest im Hytale-Mod-Projekt `ForcesOfGravium`.

Vor Aenderungen zuerst Kontext sammeln, nichts aendern. Nicht raten: Projektcode, lokale Hytale-JARs und lokale `Assets.zip` pruefen.

## Session-Ziel

Verstehe zuerst:

- Projektstruktur und wichtigste Systeme
- Connectable-Netz
- Gravium Siphon
- Rotation / Inverter / Wooden Button
- Persistenz in `worldsave.json`

Danach eine kurze Zusammenfassung geben. Erst spaeter aendern, wenn explizit angefordert.

## Erst lesen

- `README.md`
- `src/main/java`
- `src/main/resources`
- `src/test/java`

Falls vorhanden auch `AGENTS.md` oder weitere lokale Hinweise lesen.

## Wichtigste Projektbereiche

- `system`
  ECS-Systeme und Event-Systeme
- `logic/network`
  Connectable-Propagation, Recompute, Netz-Scan, Neighbor-Resolver, Siphon-Netzstatus
- `logic/siphon`
  Transferlogik, Container, Bench, World-Item-Handling, State-Refresh
- `logic/source`
  Temporare Aktivierung von Quellen wie Button
- `data`
  Runtime-Stores fuer Kabel, Inverter, Siphon, Rotation, Sources
- `registry`
  IDs, Rollen, Connectable-Sides, State-ID-Erkennung
- `persistence`
  `WorldSaveFileService`, `worldsave.json`

## Besonders wichtige Dateien

- `ForcesOfGraviumPlugin`
- `ConnectablePropagationSystem`
- `ConnectableBlockLifecycleSystem`
- `BlockPlacementRotationSystem`
- `ButtonInteractionSystem`
- `ConnectablePropagationScheduler`
- `ConnectableSignalRecalculator`
- `ConnectableNetworkScanner`
- `ConnectableNeighborResolver`
- `ConnectableNetworkUpdateService`
- `GraviumSiphonLogic`
- `GraviumSiphonBlockRefresher`
- `ConnectableRegistry`
- `ConnectableBlockRoles`
- `WorldSaveFileService`

## Hytale-API: Pflichtpruefungen

Nutze lokale Hytale-JARs aus dem Gradle-Cache und pruefe relevante Klassen mit `javap`.

Wichtig:

- `EntityTickingSystem`
- `DelayedEntitySystem`
- `EntityEventSystem`
- `CommandBuffer`
- `PlaceBlockEvent`, `BreakBlockEvent`
- `World`, World-/Chunk-Zugriffe
- `Rotation`, `RotationTuple`
- `BlockComponentChunk`
- `ItemContainer`
- `ItemContainerBlock`
- `ProcessingBenchBlock`
- `MoveTransaction`, `ItemStackTransaction`, `ListTransaction`
- `FillerBlockUtil`
- `BlockAccessor.placeBlock(...)`

## Assets.zip: Pflichtpruefungen

Nutze die lokale `Assets.zip`, um JSON-Strukturen gegen Vanilla zu bestaetigen.

Suche Beispiele fuer:

- normale und grosse Kisten / Connected Container
- Furnace / Processing Bench
- `Support` / `Supporting`
- `PlacementSettings`
- `State.Definitions`
- `BlockEntity.Components`
- `Interactions.Use`
- `ChangeState`
- Animation-States

JSON-Properties nie frei herleiten, immer bestaetigen.

## Aktueller Mod-Stand

- Siphon-Drop zu Empty-Output existiert.
- Siphon kann Item-Entities von Empty-Input aufnehmen.
- Siphon-Drops laufen ueber `ItemComponent.generateItemDrop(...)` + `CommandBuffer.addEntity(...)`.
- Item-Entity-Entfernung laeuft ueber `CommandBuffer.removeEntity(...)`.
- Inverter gibt Support wie Siphon.
- Wooden Button hat Custom-Hitbox, `Pressed`/`PressedAlt` und wieder `BlockNormal`.
- Der alte Initial-Scan `ConnectableNetworkUpdateService.ensureInitialized(...)` ist im Tick-Pfad deaktiviert, weil er World-Crashes verursacht hat.

## Kritische Projektregeln

- Nicht auf bekannte Minecraft-/Hytale-Muster vertrauen, sondern lokal pruefen.
- State-IDs beachten: oft `*_State_*`, nicht nur Base-IDs.
- Fuer Siphon-Erkennung `ConnectableRegistry.isGraviumSiphonId(...)` nutzen.
- In ECS-Systemen keine direkten Store-Writes wie `Store.addEntity(...)` oder `Store.removeEntity(...)`.
- Fuer Entity-Aenderungen im Tick-System `CommandBuffer` benutzen.
- Aenderungen klein und gezielt halten. Keine unrelated Refactors.

## Bekannte Stolperfallen

- `DelayedEntitySystem` fuehrt zu sichtbarem Delay; schnelle Propagation laeuft aktuell ueber `EntityTickingSystem`.
- Ein Tick-System darf trotz Player-Query pro World/Tick effektiv nur einmal arbeiten.
- Siphon-Transfer und Kabel-Propagation sind zeitlich getrennt.
- `GraviumSiphonBlockRefresher.refreshAt(...)` nicht dauerhaft pro Tick auf jeden Siphon anwenden.
- Inverter-Scans muessen bidirektional funktionieren:
  - Push-Seite -> Pull-Seite
  - Pull-Seite -> Push-Seite
- Fuer `locked` startet der Scan oft auf der Pull-Seite und muss trotzdem die Source auf der Push-Seite finden.
- Doppel-Kisten / Multiblocks brauchen Base-/Filler-Aufloesung, sonst wird die falsche BlockEntity verwendet.
- Bei Processing Benches Input/Fuel/Output respektieren.
- Bei Inventory-Transfers `succeeded()` und Remainder pruefen.
- Beim Item-Entity-Verbrauch keine Duplikation durch falsche Stack-Reduktion oder falsches Remove.
- `World.execute(...)` ist nicht automatisch sicher fuer Scans.
- Full-World- oder Initial-Scans koennen ueber `world.getBlockType(...)` Chunks starten/laden und in `Store is currently processing` laufen.
- Wenn ein Initial-Sync spaeter wiederkommt, dann nur mit chunk- und store-sicherem Ansatz.
- Button-States nutzen `default`, `Pressed`, `PressedAlt`, damit die Press-Animation erneut triggerbar bleibt.
- Button-Rotation bleibt aktuell `BlockNormal`.

## Ressourcen-Check

Pruefe unter `src/main/resources` besonders:

- Block-/Item-JSONs
- State-Definitionen
- Texturen / Icons / Models
- Icons in `Common/Icons/ItemsGenerated`
- Icons in `Common/Icons/ItemCategories`
- Wooden Button
- Inverter
- Gravity Powder
- Gravium Siphon

## Block-JSON Stolperfalle

- Platzierbare Mod-Bloecke brauchen fuer normales Abbauen in der Regel eine explizite `BlockType.Gathering.Breaking`-Definition.
- Den `GatherType` nicht raten, sondern in der lokalen `Assets.zip` an Vanilla-Beispielen bestaetigen.
- `GatherType` muss zum Block passen, z. B. `Woods` fuer Holz und `Rocks` fuer Stein-/Rock-Bloecke.

## Tests

Tests unter `src/test/java` lesen. Sie sichern wichtige Annahmen fuer:

- `ConnectableRegistry`
- Rollen von Connectables
- Network Scanner
- Signal Recalculator
- Propagation Scheduler
- Inverter-State
- Stores

## Arbeitsweise

- Zum Suchen bevorzugt `rg`
- Fuer API `javap`
- Fuer `Assets.zip` `tar -tf` / `tar -xOf`
- Vermutungen klar als Vermutung markieren
- Bei bestaetigten Aussagen genau Klasse oder Datei nennen
- Nach Java-Aenderungen `.\gradlew.bat test --console=plain`
- Nach reinen Ressourcen-Aenderungen mindestens `.\gradlew.bat processResources --console=plain`

## Erwartete Kurz-Zusammenfassung

Am Ende kurz beantworten:

- Wichtigste Systeme?
- Wichtigste Hytale-API-Stellen?
- Projekt-Konventionen?
- Bekannte Stolperfallen?
- Offene Risiken?
