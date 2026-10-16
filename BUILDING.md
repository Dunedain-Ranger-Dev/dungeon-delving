# Building Dungeon Pieces

How to set up the mod, build a dungeon piece, check it against the spec, and export it.

## Install

1. Use a launcher that supports NeoForge, such as **Prism Launcher** or the **CurseForge app**.
2. Create a new instance: **Minecraft 1.21.1** with **NeoForge 21.1.259** (or a newer 21.1.x).
   - With the official launcher instead: run the NeoForge 1.21.1 installer from [neoforged.net](https://neoforged.net), then pick the NeoForge profile.
3. Open the instance's `mods` folder and drop in the Dungeon Delving jar (`dungeondelving-<version>.jar`). Nothing else is needed; the pack's other mods are optional for building.
4. Launch, and make a **creative world with cheats on** (superflat is easiest).
5. If you had an older Dungeon Delving jar, delete it first.

## Sizes (W x H x L)

| Piece | Size | Notes |
|---|---|---|
| Room (start / common / objective) | **33 x 13 x 33**, walls included | Full floor and ceiling. A **5 wide x 6 tall doorway hole** in the middle of every side (blocks 14-18, just above the floor), with wall around it. |
| Tunnel | **9 x 9 x 33** | Corridor between two rooms' doorways. Open ends, a floor, and a clear 5 x 6 path all the way through. |
| Cap | **5 x 6 x 1** | Solid plug that fills an unused doorway. |

## Markers and blocks

All of these are in the Dungeon Delving creative tab.

- **Loot marker:** becomes a chest each run (about 60% of the time), otherwise empty.
- **Mob marker:** becomes a monster spawner. Spawners work in lit rooms too, so don't worry about lighting.
- **Resource marker:** becomes an ore, or **Hallstone** if no ore rolls. You can build whole walls or pillars from resource markers and they'll always come out solid.
- **Objective marker:** becomes the reward chest. Put one in each objective room.
- **Hallstone:** the generic dungeon stone, free to use as a building block.

Keep the center of start rooms clear; the exit goes there.

## The corner

Every command takes the piece's lowest, north-west block (smallest X, Y, Z). Press F3 for coordinates, or look at the block and press Tab while typing.

## Steps

1. **Start from a template:** `/dungeondelving room template common <x> <y> <z>`
   Use `start`, `common`, `objective`, `tunnel` or `cap`. Replace the light gray. **Red wool outside the piece marks doorways that must stay open.**
2. **Check:** `/dungeondelving room check common <x> <y> <z>`
   Repeat until it says **meets the spec**.
3. **Export:** `/dungeondelving room export common <name> <x> <y> <z>`
   Lowercase letters, numbers and underscores only.
4. **Try it** (optional): `/dungeondelving dungeon enter`, then `/dungeondelving dungeon leave`
5. **Send the `.nbt` file** from the folder printed in step 3.

Exports can also be copied straight into a shared folder or a clone of this repository: set `exportCopyFolder` in `config/dungeondelving-common.toml`.
