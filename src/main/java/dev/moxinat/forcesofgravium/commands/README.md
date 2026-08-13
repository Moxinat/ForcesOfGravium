# Commands

This file documents the user-facing and debug commands available in ForcesOfGravium.

## `/fog`

Main mod command.

If no valid subcommand is provided, the command shows the currently supported syntax.

## Node debug

### `/fog node here`

Shows all stored FoG `Node` information for the block at the player's current block position.

### `/fog node under`

Shows all stored FoG `Node` information for the block directly below the player.

### `/fog node <x> <y> <z>`

Shows all stored FoG `Node` information at an exact block position.

The output includes:

- block id
- signal input sides
- signal output sides
- control input sides
- invert capability and current invert state
- pass behavior capability and current passing state
- stored rotation
- previous and current instant state
- previous and current effective state
- dirty state
- energy delta
- network id

## Rotation debug

### `/fog rotation here`

Compares the rotation stored in the FoG `Node` with the rotation currently stored by the Hytale world for the block at the player's current block position.

### `/fog rotation under`

Runs the same rotation comparison for the block directly below the player.

### `/fog rotation <x> <y> <z>`

Runs the same rotation comparison at an exact block position.

The output includes:

- block id
- stored FoG node rotation
- Hytale world rotation

## `/fog saveinfo`

Shows the path of the Forces of Gravium save file for the player's current world.

The save file is stored below the world's save directory in:

```text
forcesofgravium/worldsave.json
```

## `/fog saveworld`

Immediately force-saves the player's current world through `WorldSaveFileService`.

This command is mainly useful for persistence testing.

The output includes:

- the affected world
- save file path
- whether the save file exists after the save attempt
- the last save error, if one exists

## Notes

- The current commands are primarily development/debugging tools.
- The node, rotation, and save commands require a player sender because they operate on the player's current world.
