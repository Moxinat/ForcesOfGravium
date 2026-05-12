# ForcesOfGravium Developer Guide

This document describes the current working-tree architecture of `ForcesOfGravium`.
It is intended for developers changing gameplay logic, persistence, or Hytale asset
definitions.

## Start Here

Read this document in this order when onboarding:

1. Start with "Architecture Overview" and "Major Systems" to learn where code lives.
2. Read "Connectable Network Propagation" before changing gravity powder, sources,
   inverters, buttons, or siphon power/lock behavior.
3. Read "Siphon Logic" before changing item movement, containers, item entities, or
   processing bench interaction.
4. Read "Persistence and World Save" before changing any store shape or adding new
   runtime state.
5. Read "Hytale API Pitfalls" before replacing world, chunk, block, inventory, or
   ECS calls.

The shortest mental model is: Hytale systems update stores and schedule work;
scheduled propagation recomputes cable/inverter state; block refreshers turn stored
state into Hytale block states; siphons read nearby propagated networks to decide
whether they are powered or locked; siphon item transfer runs later on its own tick
cadence.

## Architecture Overview

The mod is a Hytale Java plugin registered by
`dev.moxinat.forcesofgravium.ForcesOfGraviumPlugin`.

At startup the plugin registers:

- `/fog`, implemented by `commands/ForcesOfGraviumCommand`.
- global player/shutdown events in `event/ForcesOfGraviumEvents`.
- ECS systems in `system/`:
  - `BlockPlacementRotationSystem`
  - `ButtonInteractionSystem`
  - `ConnectableBlockLifecycleSystem.PlaceSystem`
  - `ConnectableBlockLifecycleSystem.BreakSystem`
  - `ConnectablePropagationSystem`

The main design split is:

- `system/`: Hytale ECS entry points. These adapt events/ticks into mod logic.
- `logic/`: gameplay calculations and world/block refreshers.
- `data/`: in-memory stores keyed by world and block position.
- `registry/`: static block IDs, state-ID recognition, side masks, and roles.
- `persistence/`: JSON save/load for the runtime stores.
- `src/main/resources`: Hytale item, block, model, texture, hitbox, recipe, and language assets.

ECS systems should stay thin. They can read event context, find the world/position,
update the appropriate store, and schedule propagation. The actual propagation,
block-state refresh, siphon transfer, and persistence work lives outside the ECS
system classes.

## Major Systems

### Registries and Roles

`ConnectableRegistry` owns asset-facing IDs and connectable side masks.

Important IDs:

- `Gravity_Powder_Default`
- `Inverter_Block`
- `WindGenerator_Block`
- `Gravium_Siphon_Block`
- `Wooden_Button_Block`

The registry must recognize both base IDs and Hytale state IDs such as
`*Gravity_Powder_Default_State_StraightPush`. Call the `is...Id` helpers instead
of comparing only base IDs.

Connectable sides are local block sides, not world directions. Rotation is resolved
by `ConnectableNeighborResolver.worldSideForLocalSide(...)`.

`ConnectableBlockRoles` classifies sources and consumers:

- sources: wind generator, wooden button, and wooden button state IDs
- consumers: gravium siphon and siphon state IDs

### Runtime Stores

The stores in `data/` are the authoritative runtime state:

- `GravityPowderBlockDataStore`: cable connection mask and signal timeline.
- `InverterDataStore`: inverter current output mode, next mode, enabled/toggled state, and timeline.
- `GraviumSiphonStore`: siphon `powered` and `locked`.
- `ConnectableRotationStore`: placement rotation for blocks whose local sides matter.
- `SourceBlockDataStore`: source active state.

All store writes call `WorldSaveFileService.ensureLoaded(...)` and mark the world
dirty, except while the world is actively being loaded. Store keys currently use
`world.getName()` internally, while persistence and tick de-duplication use the
normalized save path.

Needs verification: whether `world.getName()` is always unique enough for all
multiworld/server layouts this mod will support. Treat save path as the safer world
identity when adding new persistence-facing code.

### Placement and Breaking

`ConnectableBlockLifecycleSystem` handles connectable placement and breaking.

On placement it:

- initializes gravity powder, inverter, siphon, or source state as needed
- records rotation in `ConnectableRotationStore`
- schedules propagation around the placed block

On break it:

- removes rotation and per-block runtime state
- schedules propagation around the broken block

`BlockPlacementRotationSystem` changes pitch only for inverter and siphon placement
when the player looks strongly up or down. Yaw and roll are preserved from Hytale's
event rotation.

### Tick Loop

`ConnectablePropagationSystem` is an `EntityTickingSystem` queried on players. Since
that can run once per matching player, it guards with `LAST_PROCESSED_WORLD_TICKS`
so each world is processed only once per tick.

Per processed world tick it:

- expires temporary source activations
- runs pending connectable propagation
- runs siphon transfer every 5 ticks
- autosaves dirty loaded worlds every 100 ticks

## Connectable Network Propagation

Propagation is easier to understand as four phases: scheduling, recompute, visual
refresh, and siphon-state refresh.

### Phase 1: Scheduling

Propagation starts when a system calls:

- `ConnectablePropagationScheduler.onConnectablePlaced(...)`
- `ConnectablePropagationScheduler.onConnectableBroken(...)`

Both enqueue positions around the target. On the next tick,
`ConnectablePropagationScheduler.tickPropagation()` drains the pending world queues
and processes each affected world.

`affectedConnectablePositions(...)` intentionally expands through all stored cables
and inverters in the touched component. This keeps an isolated network from being
recomputed when another network changes, while still updating both sides of a broken
bridge.

### Phase 2: Recompute

`ConnectableSignalRecalculator` is the signal engine. It does not walk arbitrary
world blocks; it works from the stored cable and inverter positions selected by the
scheduler.

Signal rules:

- It initializes all affected cables and inverters to `OFF`.
- It seeds `PUSH` from active sources adjacent to cables.
- It also seeds `PUSH` into an inverter when an active source faces the inverter back.
- Cable output travels to adjacent cables and into an inverter only through its back.
- Inverter output exits only through its front.
- Enabled inverters invert `PUSH` to `PULL` and `PULL` to `PUSH`.
- Disabled inverters pass the signal through unchanged.
- Side inputs toggle `invertEnabled` only on a rising edge, tracked by `toggleInputActive`.
- If `PUSH` and `PULL` collide on a cable, `PUSH` wins.

The important direction rule is: cable can feed an inverter only at local back, and
an inverter can feed the next carrier only from local front. Side neighbors are
reserved for toggle input, not normal signal throughput.

### Phase 3: Wave State and Visual Refresh

Gravity powder stores both an instant state and a wave state through `StateTimeline`.
The visible/effective state remains the previous wave state while an instant/wave
mismatch exists. Wave adoption is scheduled through `PENDING_WAVE_ADOPTION`, and
adoption can cascade to neighboring mismatched cables.

`ConnectablePropagationScheduler.onCableInstantStateChanged(...)` is currently a
no-op.

Needs verification: this appears intentional in the current propagation design,
because wave scheduling happens through placement, breaking, and explicit adoption
processing. Confirm that before wiring new behavior into this hook.

After recompute the scheduler refreshes block visuals:

- gravity powder via `GravityPowderBlockRefresher.refreshAt(...)`
- inverters via `InverterBlockRefresher.refreshAt(...)`

Refreshing is separate from recomputing. Recompute updates store data; refreshers
convert store data into concrete Hytale block state IDs and preserve rotation where
needed.

### Phase 4: Siphon-State Refresh

After cable/inverter refresh, the scheduler updates nearby siphon network state via
`ConnectableNetworkUpdateService.updateSiphonsNear(...)`. This is only the siphon's
`powered`/`locked` state. Actual item movement is handled later by
`GraviumSiphonLogic.tickWorld(...)`.

## Network Scans for Siphons

`ConnectableNetworkUpdateService` determines siphon network state after nearby
connectable changes.

For each affected siphon it checks the local right, left, top, and bottom sides.
Front and back are transfer endpoints, not control sides. Each control neighbor must
be gravity powder.

For each control cable:

- scanning `PUSH` and finding any source makes the siphon `powered`
- scanning `PULL` and finding any source makes the siphon `locked`

`ConnectableNetworkScanner` walks only carriers whose effective state matches the
requested `SignalState`. It can traverse across inverters in both directions by
flipping the requested state when the inverter is enabled. This is important for
locked detection: a scan may start on a pull-side cable and still need to find the
source on the push side behind an inverter.

The scanner returns carriers, inverters, sources, and consumers. `NetworkScanResult`
currently uses only `hasAnySource()` for siphon state.

Needs verification: consumers are collected by the scanner but are not currently
used by siphon-state resolution. The likely intent is future network behavior, but
that is not proven by code.

## Siphon Logic

There are two siphon flows that happen at different times:

- Network control flow: propagation updates the siphon's `powered` and `locked`
  state by scanning nearby control cables.
- Item transfer flow: `GraviumSiphonLogic.tickWorld(...)` moves at most one item
  when the siphon is eligible.

`GraviumSiphonLogic.tickWorld(...)` runs every 5 world ticks from the propagation
system. It first repairs missing siphon-store entries for any stored rotation that
currently belongs to a siphon block, then iterates stored siphons.

Needs verification: the repair pass is inferred from the code path that scans stored
rotations and adds missing siphons. Confirm whether this is meant as long-term
self-healing or only a transitional safeguard for older saves.

Siphons are skipped when:

- the block at the stored position is missing or no longer a siphon
- the siphon is locked
- transfer cooldown has not elapsed
- the siphon is unpowered and is not pitched `Rotation.Ninety`

Transfer direction is local:

- source endpoint: local back
- target endpoint: local front

Rotation is resolved through `ConnectableNeighborResolver.adjacentPositionForLocalSide(...)`.

Transfer endpoint resolution is:

1. Resolve source and target positions from the siphon's stored rotation.
2. Resolve each endpoint to either a processing bench, normal item container, empty
   block, or no usable endpoint.
3. Try to move exactly one item.
4. Return a `SiphonMoveResult` describing the outcome.

Supported endpoint behavior:

- Normal containers use `ItemContainerBlock`.
- Processing benches extract from output and insert into input/fuel.
- If the source is an empty block and a command buffer is available, a world item
  entity at that position can be consumed.
- If the target is an empty block and a command buffer is available, one item can be
  spawned as a dropped item.

Container transfer code must check `succeeded()` and empty remainder. Item entity
consumption must either remove the entity when quantity reaches one or decrement the
stack by exactly one. Dropped items are spawned with
`ItemComponent.generateItemDrop(...)` and added through `CommandBuffer.addEntity(...)`.

Multiblock/filler blocks are resolved in `baseBlockPosition(...)` before reading a
block entity reference. This prevents transfers from addressing a filler block when
the real container or processing bench entity belongs to its base block.

Needs verification: the current world-item pickup check compares the entity block
position directly to the source block position. Confirm expected behavior for items
whose transform is slightly offset inside the block.

## Inverter Behavior

Inverters have three distinct concepts:

- input mode from the back side
- output mode after optional inversion
- toggle state from side inputs

`InverterStateCalculator.computeInputMode(...)` reads the back neighbor:

- active source facing the inverter back gives `push`
- gravity powder gives its effective state
- upstream inverter gives its current mode only if its front points into this inverter
- anything else gives `off`

`ConnectableSignalRecalculator` computes propagation output and stores inverter
state. Enabled means "invert"; disabled means "pass through". The texture state name
uses `On` when `invertEnabled` is false and `Off` when `invertEnabled` is true, so
the visual suffix is not a direct boolean name.

Needs verification: the semantic meaning of the visual `On`/`Off` suffix is inferred
from `InverterBlockRefresher.stateName(...)` and texture names. Confirm with the
intended art/UX language before renaming states or textures.

`InverterBlockRefresher` chooses a state prefix from computed input mode:

- `Off`
- `Push`
- `Pull`

Then it appends `Off` or `On` according to `invertEnabled`. The final Hytale states
are `OffOn`, `OffOff`, `PushOn`, `PushOff`, `PullOn`, and `PullOff`.

## Persistence and World Save

`WorldSaveFileService` stores mod runtime state at:

`<world save path>/forcesofgravium/worldsave.json`

It writes one BSON/JSON document with arrays:

- `gravityPowder`
- `inverters`
- `graviumSiphons`
- `rotations`
- `sources`

Worlds are loaded lazily by `ensureLoaded(...)`, primarily from player ready events
and store access. Loading clears the current in-memory state for that world, reads
the save file if present, and repopulates stores.

Important persistence behavior:

- `LOADING_WORLDS` suppresses dirty marking while loading, because load code writes
  through normal store APIs.
- `markDirty(...)` throttles immediate saves to once per second per world.
- `ConnectablePropagationSystem` also calls `saveDirtyWorlds()` every 100 ticks.
- shutdown calls `saveLoadedWorlds()`.
- `/fog saveinfo` reports the active path.
- `/fog saveworld` forces a save and reports existence/errors.

Save loading supports legacy gravity powder shapes:

- current `instantState`/`waveState`/`previousState`
- older `state`
- older `currentMode`/`decayMark`
- older `push`/`pull` booleans

`effectiveState` is serialized as debug/readability data. It is derived from the
timeline on load rather than treated as authoritative input.

## Hytale API Pitfalls

- `EntityTickingSystem` can execute once per matching player entity. Guard world
  work by world/tick if the logic must run once per world.
- ECS entity mutations in ticking code should use `CommandBuffer`; direct store
  entity mutation is risky in tick/event processing.
- `world.execute(...)` moves work to the world thread for debug commands, but it is
  not a general solution for broad scans or unsafe chunk access.
- `world.getBlockType(...)` and chunk access can touch unloaded/changing world
  state. Avoid full-world or initial scans in tick paths.
- `BlockAccessor.placeBlock(...)` should be called only after resolving the chunk
  and preserving rotation where the local side semantics matter.
- Hytale state block IDs commonly look like `*Base_Block_State_StateName`; use
  registry prefix helpers.
- Rotation-sensitive logic must distinguish local sides from world directions.
- Needs verification: `RotationYawPlacementOffset` appears to affect how model-facing
  direction maps to stored rotation and local front/back behavior. Verify against
  Hytale asset/API behavior before changing front/back assumptions.
- Multiblock or connected-container targets can require filler-to-base resolution
  before reading `BlockComponentChunk` entity references.
- Processing benches are not normal containers: extract output, insert input/fuel.
- Item transactions are not successful unless the transaction succeeds and the
  remainder is empty.
- Wooden button state changes use `default`, `Pressed`, and `PressedAlt` to retrigger
  the animation on repeated use.
- Needs verification: Hytale JSON property names and values should be copied from
  verified local assets, not guessed from similar engines. Recheck local vanilla
  examples when adding new asset features.

## Known Edge Cases and Invariants

- Siphon control sides exclude local front and back.
- Siphon front/back are reserved for item transfer.
- Siphon `locked` wins over `powered`; locked siphons do not transfer.
- Unpowered siphons transfer only when pitched `Rotation.Ninety`.
- Powered transfer interval is 30 ticks; unpowered interval is 60 ticks.
- Siphon logic and cable propagation are intentionally separated in time.
- Gravity powder visual state is driven by `effectiveState`, not always the newest
  instant state.
- `PUSH` wins over `PULL` when both reach the same cable.
- Inverter back accepts network input; inverter front emits output.
- Side input toggles an inverter only on rising edge.
- Pull scans must be able to cross enabled inverters back to a push-side source.
- Wind generators default active; wooden buttons default inactive.
- Button activation lasts 30 ticks and schedules propagation on activation and expiry.
- Connectable state IDs must remain recognized after visual block replacement.
- Runtime stores may contain stale positions; tick/update code validates live block
  type before using or refreshes/removes stale entries.
- Avoid reintroducing full initial network scans unless chunk/store safety is solved.

## Resource Layout

Key server assets:

- `Server/Item/Items/Gravity_Powder_Default.json`
- `Server/Item/Items/Inverter_Block.json`
- `Server/Item/Items/Gravium_Siphon_Block.json`
- `Server/Item/Items/WindGenerator_Block.json`
- `Server/Item/Items/Wooden_Button_Block.json`
- `Server/Item/Block/Hitboxes/Block/*.json`
- `Server/Item/Recipes/Gravity_Powder_Salvage_Recipe.json`
- `Server/Languages/en-US/items.lang`

Key common assets:

- `Common/Blocks/Gravity_Powder/*.blockymodel`
- `Common/Blocks/Gravity_Powder/*.blockyanim`
- `Common/Blocks/Inverter/Inverter_Block.blockymodel`
- `Common/Blocks/Gravium_Siphon/Gravium_Siphon_Block.blockymodel`
- `Common/Blocks/Wooden_Button/Button.blockymodel`
- `Common/BlockTextures/*.png`
- `Common/Icons/ItemsGenerated/*.png`

Gravity powder has state definitions for shape plus optional `Push`/`Pull` suffixes.
The Java refresher must stay in sync with those state names.

## Suggested Code Comments

These are places where short comments would help future readers understand intent.
They are not obvious line-by-line comments and should be added only when editing the
relevant code.

- `ConnectablePropagationSystem.markWorldTickProcessed(...)`: note that the player
  query can tick once per player, so this prevents duplicate per-world processing.
- `StateTimeline.effectiveState()`: explain that visuals/network consumers keep the
  previous wave state until delayed wave adoption catches up to the instant state.
- `ConnectablePropagationScheduler.onCableInstantStateChanged(...)`: either document
  why it is intentionally empty or remove/implement it during the next propagation
  cleanup.
- `ConnectablePropagationScheduler.affectedConnectablePositions(...)`: note that the
  component expansion limits recompute to the touched network and handles bridge
  breaks.
- `ConnectableNetworkScanner.addInverterConnections(...)`: note that scans can cross
  an inverter in either direction and flip the requested signal state when enabled.
- `ConnectableNetworkUpdateService.controlNeighbors(...)`: note that siphon
  front/back are transfer endpoints, so only right/left/top/bottom are control
  inputs.
- `ConnectableSignalRecalculator.hasSideInput(...)`: note that side inputs are
  toggle controls and exclude inverter front/back.
- `InverterBlockRefresher.stateName(...)`: note that the visual `On` suffix means
  invert disabled/pass-through, while `Off` means invert enabled.
- `WorldSaveFileService.LOADING_WORLDS`: note that normal store writes during load
  must not mark the world dirty or trigger immediate saves.
- `GraviumSiphonLogic.baseBlockPosition(...)`: note that filler blocks must resolve
  to the base block before looking up block entity components.
- `GraviumSiphonLogic.SiphonEndpoint`: note why processing bench output is the
  extraction side and input/fuel are insertion targets.
- `Wooden_Button_Block.json` state changes: document in nearby asset notes that
  `Pressed` and `PressedAlt` exist to make repeated use trigger animation again.

## Test Coverage Notes

Tests currently cover:

- registry recognition of state IDs and side masks
- source defaults
- gravity/inverter timeline initialization
- component-limited propagation scheduling
- signal recompute through cables and inverters
- inverter side-toggle rising edge
- network scanning across inverters and requested states

When changing propagation, add tests through the adapter-based network tests before
touching Hytale world APIs. They are the cheapest way to protect the core signal
rules.
