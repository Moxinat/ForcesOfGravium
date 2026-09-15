# Forces of Gravium

**Forces of Gravium** is a pre-release automation mod for Hytale built around Gravium-based signal networks.

The mod provides connectable blocks and machines that can transmit, transform, observe, and react to signals. A major design goal is to keep automation understandable through the world itself: connections, states, and machine behavior should be visible instead of hidden behind large configuration interfaces.

> **Status:** Forces of Gravium is under active development. APIs, systems, assets, and world data may change between pre-release versions.

## Development

### Requirements

* A working Hytale development environment
* A compatible Hytale `0.6.x` server version starting with `0.6.1`
* Java and Gradle as required by the current Hytale development environment

### Run the Development Server

Windows:

```powershell
.\gradlew.bat devServer
```

Linux/macOS:

```bash
./gradlew devServer
```

### Build

Windows:

```powershell
.\gradlew.bat build
```

Linux/macOS:

```bash
./gradlew build
```

## Project Structure

The Java source is organized by responsibility:

* `block`
  Block-specific gameplay logic, interaction systems, refreshers, and other feature implementations.

* `commands`
  Development and debugging commands exposed through `/fog`.

* `data`
  Persistent and runtime ECS components and resources such as nodes, networks, sensors, and signal runtime state.

* `dispatcher`
  Coordinates state changes and visual updates between systems without owning the underlying gameplay state.

* `energy`
  Network energy calculation and energy-related behavior.

* `lifecycle`
  Placement, removal, world-tick, and other lifecycle integration.

* `network`
  Creation, merging, splitting, and maintenance of connectable networks.

* `persistence`
  Restoration and synchronization of persistent FoG state when world sections are loaded.

* `registry`
  Shared block IDs, side definitions, masks, and connectable classifications.

* `signal`
  Signal recalculation, propagation, deferred recomputation, and related scheduling.

* `source`
  Signal-source activation and source-specific scheduling.

* `spatial`
  Rotation-aware side resolution and discovery of neighboring connectable nodes.

Resources and asset definitions are located under:

```text
src/main/resources
```

## Architecture

### Nodes

Connectable FoG blocks are represented by `NodeComponent`.

A node describes properties such as:

* signal input sides
* signal output sides
* control input sides
* instant signal state
* effective signal state
* inversion
* whether the node currently passes signals

Block-specific behavior should build on the shared node system instead of implementing separate signal infrastructure.

### Networks

Connected nodes are grouped into networks managed through `NetworkResource` and the network layer.

The network system is responsible for:

* assigning nodes to networks
* adding and removing connections
* merging connected networks
* splitting networks after topology changes
* tracking per-node energy contributions
* maintaining persistent network topology

### Signals

Signal changes are handled separately from network topology.

The signal layer is responsible for recalculation and propagation of `PUSH`, `PULL`, and `OFF` states while respecting node direction, inversion, control behavior, and current connectivity.

Recomputation may be deferred when required world sections are not currently available. Persistent runtime state is used so pending work can continue once the necessary sections are loaded.

### Persistence

Persistent gameplay state is stored using Hytale ECS components and resources.

Runtime state that can be reconstructed should remain separate from state that must survive world unloads or server restarts.

When adding a new persistent component or resource, restoration behavior after section loading and server restart must be considered as part of the feature.

## Development Conventions

### Class Naming

Class names should describe their actual responsibility.

Common suffixes include:

* `...System` for registered ECS systems
* `...Manager` for ownership and coordination of a larger subsystem
* `...Resolver` for deriving information from existing state
* `...Recalculator` for recomputing derived state
* `...Refresher` for synchronizing visual or runtime representations
* `...Scheduler` for deferred or tick-based work
* `...Dispatcher` for routing state changes to the appropriate systems
* `...Component` for ECS component data
* `...Resource` for ECS resource data
* `...Registry` for shared IDs and classifications

Avoid introducing a new abstraction when an existing shared system already represents the same concept.

### Resource Naming

Asset-facing resources should follow the naming style used throughout the project and the Hytale asset ecosystem.

Prefer:

```text
PascalCase
PascalCase_With_Underscores
```

Keep related block IDs, models, textures, icons, states, and animations consistently named.

Examples:

```text
Gravity_Powder_Default
Inverter_Block
Gravium_Siphon_Block
Gravium_Sensor_Block
```

State and texture suffixes should clearly describe their purpose, for example:

```text
_Off
_Push
_Pull
_Default
_Front
_Top
_Side
_Texture
```

### Maintenance

When changing existing systems:

* remove obsolete implementations after a replacement is complete
* avoid maintaining duplicate sources of truth
* keep persistent state separate from reconstructable runtime state
* reuse the shared node, network, signal, and spatial systems where applicable
* consider placement, breaking, chunk unload/load, and server restart behavior
* keep visual state synchronized with gameplay state
* update this README when project-wide architecture or conventions change

## Debug Commands

The `/fog` command currently contains development and debugging utilities. These commands are not intended as player-facing gameplay features.

### Node Debug

```text
/fog node here
/fog node under
/fog node <x> <y> <z>
```

Displays the `NodeComponent` at the selected position, including:

* signal and control sides
* previous and current instant state
* previous and current effective state
* dirty state
* inversion
* passing state
* energy delta
* network ID

### Rotation Debug

```text
/fog rotation here
/fog rotation under
/fog rotation <x> <y> <z>
```

Displays the block and the rotation resolved by the FoG spatial system.

### Sensor Debug

```text
/fog sensor here
/fog sensor under
/fog sensor <x> <y> <z>
```

Displays information about a sensor and its current observation snapshot, including node state, observed position, block state, container item count, and entity count.

### Particle Debug

```text
/fog particle <particleId>
```

Looks up the specified particle system and attempts to spawn it at the player's position.

## Contributing

When contributing a new block or system, prefer extending the existing shared architecture instead of creating feature-specific alternatives for networking, signal propagation, persistence, or spatial resolution.

Before submitting changes, check that:

* the project builds successfully
* the development server starts
* placement and breaking behave correctly
* affected networks update correctly
* relevant state survives unload/reload where required
* obsolete code and assets are removed
* new IDs and resources follow the existing naming conventions

## Credits

### Development

* **Moxinat**

### Art & Assets

* **Gingledoof**

Additional contributors and asset authors should be credited alongside the systems or assets they contributed to.

## Player Information

This repository primarily contains the source code and developer documentation for Forces of Gravium.

Player-facing descriptions, installation instructions, screenshots, releases, and gameplay information are provided through the mod's distribution page.
