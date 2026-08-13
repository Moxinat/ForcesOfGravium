# Commands

This file documents the user-facing and debug commands available in ForcesOfGravium.

## `/fog`

Main mod command.

If no valid subcommand is provided, the command shows the currently supported syntax.

## `/fog rotation here`

Compares the rotation stored in the FoG `Nodes` runtime data with the rotation currently stored by the Hytale world for the block below the player.

Useful for debugging local-side and placement-rotation problems.

The output includes:

- block id
- stored FoG node rotation
- Hytale world rotation

## `/fog rotation <x> <y> <z>`

Runs the same rotation comparison at an exact block position.

Example:

```text
/fog rotation 10 64 5
```

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
- The rotation and save commands require a player sender because they operate on the player's current world.
- Legacy gravity-powder, inverter, and siphon debug commands were removed together with their old block-specific runtime stores.
