# Commands

This file documents the user-facing commands available in ForcesOfGravium.

## `/fog`

Main mod command.

If no valid subcommand is provided, the command shows the currently supported syntax.

## `/fog gpdist all`

Lists all stored gravity powder data entries in the current world.

Each entry includes:

- block position
- current `mode`
- current `stable` state
- all stored `distance` entries

Example output:

```text
(10,64,5) mode=push stable=true distances=[(8,64,5 -> 2), (20,64,5 -> 7)]
```

Meaning:

- The gravity powder block is at `(10,64,5)`.
- Its current mode is `push`.
- It is currently marked as `stable`.
- It knows about two source targets:
  - source at `(8,64,5)` with distance `2`
  - source at `(20,64,5)` with distance `7`

## `/fog gpdist here`

Reads the gravity powder data entry at the player's current block position.

Usage notes:

- useful for quick in-game debugging
- if no entry exists at that position, the command reports that no data was found

## `/fog gpdist <x> <y> <z>`

Reads the gravity powder data entry at an exact coordinate.

Example:

```text
/fog gpdist 10 64 5
```

## Notes

- These commands are currently focused on debugging gravity powder logic.
- `gpdist here` requires a player sender.
- The data is read from the internal `GravityPowderBlockDataStore`.
- If a block has no stored gravity powder debug data, the command reports that no entry exists instead of returning a fallback value.
