# Commands

This file documents the user-facing commands available in ForcesOfGravium.

## `/fog`

Main mod command.

If no valid subcommand is provided, the command shows the currently supported syntax.

## `/fog gpdist all`

Lists all stored gravity powder data entries in the current world.

Each entry includes:

- block position
- centralized cable `state`
- effective cable mode derived from that state
- remaining `stateTicks`

Example output:

```text
(10,64,5) state=push_wave mode=push stateTicks=2
```

Meaning:

- The gravity powder block is at `(10,64,5)`.
- Its centralized current state is `push_wave`.
- Its current effective behavior and visuals are `push`.
- It has `2` ticks remaining before that wave settles into normal `push`.

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

## `/fog invdist all`

Lists all stored inverter data entries in the current world.

Each entry includes:

- block position
- current `mode`

Example output:

```text
(12,64,5) mode=pull
```

## `/fog invdist here`

Reads the inverter data entry at the block below the player's current position.

## `/fog invdist <x> <y> <z>`

Reads the inverter data entry at an exact coordinate.

## Notes

- These commands are currently focused on debugging gravity powder logic.
- `gpdist here` requires a player sender.
- `invdist here` also requires a player sender.
- `gpdist` reads from the internal `GravityPowderBlockDataStore`.
- `invdist` reads from the internal `InverterDataStore`.
- `mode` reflects the live cable behavior derived from the centralized `state`.
- If a block has no stored gravity powder debug data, the command reports that no entry exists instead of returning a fallback value.

## `/fog reconnectdebug on|off`

Toggles in-game chat debug messages for the reconnect path that runs after placing gravity powder.

When enabled, the placing player receives chat lines for:

- wave detected in the placed cable component
- source path found from the placed cable
- wave cleared for the component
