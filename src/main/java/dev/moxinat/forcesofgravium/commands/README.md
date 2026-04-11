# Commands

This file documents the user-facing commands available in ForcesOfGravium.

## `/fog`

Main mod command.

If no valid subcommand is provided, the command shows the currently supported syntax.

## `/fog gpdist all`

Lists all stored gravity powder data entries in the current world.

Each entry includes:

- block position
- stored `push` signal flag
- stored `pull` signal flag
- effective cable mode derived from those flags
- connections mask

Example output:

```text
(10,64,5) push=true pull=false mode=push connectionsMask=3
```

Meaning:

- The gravity powder block is at `(10,64,5)`.
- It currently receives a push signal and no pull signal.
- Its current effective behavior and visuals are `push`.
- Its stored connections mask is `3`.

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
- whether inversion is enabled
- whether the side toggle input is currently held active

Example output:

```text
(12,64,5) mode=pull invertEnabled=true toggleInputActive=false
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
- `mode` reflects the live cable behavior derived from the stored `push`/`pull` flags.
- If a block has no stored gravity powder debug data, the command reports that no entry exists instead of returning a fallback value.
