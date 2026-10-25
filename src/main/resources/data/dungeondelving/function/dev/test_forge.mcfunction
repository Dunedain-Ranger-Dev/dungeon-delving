# Builds a level 3 test forge in a superflat world, west of the test dungeon, with a leatherworking and weaving corner
# (crafting table, loom, Tanning Rack, and a cauldron over a campfire), and gives forging supplies.
# Run with /function dungeondelving:dev/test_forge, or put that command in a command block.
function dungeondelving:dev/clear_test_forge

# Brick floor and a few decorations, for the workshop bonus
fill -22 -61 -2 -8 -61 12 minecraft:bricks
setblock -21 -60 -1 minecraft:lantern
setblock -9 -60 -1 minecraft:lantern
setblock -21 -60 11 minecraft:barrel
setblock -9 -60 11 minecraft:smithing_table
fill -22 -60 -2 -22 -58 -2 minecraft:chain

# The Guild Ledger in the middle, with the stations around it
setblock -15 -60 5 dungeondelving:guild_ledger[facing=south]
setblock -18 -60 3 minecraft:furnace[facing=south]
setblock -17 -60 3 minecraft:blast_furnace[facing=south]
setblock -16 -60 3 dungeondelving:bellows[facing=south]
setblock -12 -60 3 minecraft:anvil[facing=east]
setblock -12 -60 5 dungeondelving:master_anvil[facing=east]
setblock -18 -60 7 minecraft:water_cauldron[level=3]
setblock -17 -60 7 dungeondelving:oil_cauldron[level=3]
setblock -12 -60 7 minecraft:grindstone[face=floor,facing=north]
setblock -11 -60 7 dungeondelving:grinding_wheel[facing=north]

# Leather and cloth: a crafting table, a loom, a Tanning Rack, and a water cauldron over a lit campfire for boiling
setblock -20 -60 10 minecraft:crafting_table
setblock -19 -60 10 minecraft:loom[facing=south]
setblock -17 -60 10 dungeondelving:tanning_rack[facing=south]
setblock -14 -61 10 minecraft:campfire[lit=true]
setblock -14 -60 10 minecraft:water_cauldron[level=3]

give @p minecraft:iron_ingot 16
give @p minecraft:coal 16
give @p dungeondelving:quenching_oil 4
tellraw @a {"text":"Test forge built. Right-click the Guild Ledger to check it.","color":"green"}
