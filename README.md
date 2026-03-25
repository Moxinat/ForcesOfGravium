# ForcesOfGravium

Pre-release repository for the Hytale mod `ForcesOfGravium`.

## Author

Moxinat

## Project Structure

The codebase is organized by responsibility.

- `commands`
  Command classes exposed to players or admins.
- `event`
  Real event listeners and handlers only.
- `system`
  ECS systems such as `EntityEventSystem` and `TickingSystem`.
- `logic`
  Gameplay logic that computes behavior without being the registered system itself.
- `logic/gravity`
  Gravity powder state and model refresh logic.
- `logic/network`
  Connectable neighbor lookup and propagation scheduling.
- `data`
  Runtime stores and state containers.
- `registry`
  Static IDs, masks, lookup rules, and gameplay classifications.

## Naming Rules

Class names should reflect their actual role.

- Event listeners use names like `...Events` or `...EventHandler`.
- ECS systems use names ending in `...System`.
- Pure gameplay logic uses names like `...Calculator`, `...Resolver`, `...Refresher`, or `...Scheduler`.
- Runtime storage classes use names ending in `...Store`.
- Registries and gameplay classifications should not be mixed when they have different responsibilities.

Public names should also match the mod context.

- Commands should use meaningful names such as `fog` instead of placeholder names like `test`.
- IDs and asset-facing names should stay close to the Hytale ecosystem naming style.

## Resource Naming

Resources follow the naming style used by the Hytale asset ecosystem:

- Prefer `PascalCase` or `PascalCase_With_Underscores`.
- Do not mix this with custom `snake_case` names in the same feature area.
- Keep folders, models, textures, and icons consistent with the item or block ID they belong to.
- Use clear suffixes like `_Default`, `_Off`, `_Push`, `_Pull`, `_Texture`, `_Front`, `_Top`, `_Side`.

Examples:

- `Gravity_Powder_Default.blockymodel`
- `Gravity_Powder_Off.png`
- `Inverter_Block.blockymodel`
- `Inverter_Front_Off.png`

## Maintenance Rules

- If a refactor replaces older logic, remove the obsolete classes instead of leaving duplicate implementations behind.
- Keep `event`, `system`, and `logic` separated so navigation stays predictable.
- When adding a new block or machine, decide up front where its IDs, runtime state, ECS systems, and gameplay logic belong.
- Prefer updating this README when structural rules change, so future contributors have one place to check.

## Development

Use `.\gradlew.bat devServer` on Windows or `./gradlew devServer` on Linux/macOS to start the local
development server.

## Commands

- `/fog gpdist all`
  Lists all stored gravity powder entries for the current world.
- `/fog gpdist here`
  Shows the stored gravity powder data at the player's current block position.
- `/fog gpdist <x> <y> <z>`
  Shows the stored gravity powder data at a specific block position.
- `/fog saveinfo`
  Shows the current world save file path used by the mod persistence layer.
- `/fog saveworld`
  Triggers an immediate save for the current world's ForcesOfGravium data.
