# Builds a test crafts hall south of the test forge: a level 3 tannery, weaving room and woodshop around one Guild
# Ledger (DD-88), and gives a kit for every step. Run with /function dungeondelving:dev/test_crafts.
function dungeondelving:dev/clear_test_crafts

# Plank floor
fill -22 -61 16 -8 -61 30 minecraft:spruce_planks

# The Guild Ledger in the middle
setblock -15 -60 23 dungeondelving:guild_ledger[facing=south]

# Tannery (west): Tanning Rack, a cauldron for the liquor, a Shaping Rack (shared with weaving), Tanning Barrel, Fleshing Beam
setblock -19 -60 20 dungeondelving:tanning_rack[facing=east]
setblock -19 -60 22 minecraft:cauldron
setblock -19 -60 24 dungeondelving:tanning_barrel
setblock -19 -60 26 dungeondelving:fleshing_beam[facing=east]
setblock -15 -60 26 dungeondelving:shaping_rack[facing=north]

# Weaving room (north): Spinning Wheel, loom, Great Loom, Dye Works, and a water cauldron over a campfire (shared with woodworking)
setblock -17 -60 19 dungeondelving:spinning_wheel[facing=south]
setblock -15 -60 19 minecraft:loom[facing=south]
setblock -13 -60 19 dungeondelving:great_loom[facing=south]
setblock -11 -60 19 dungeondelving:dye_works[facing=south]
setblock -15 -61 21 minecraft:campfire[lit=true]
setblock -15 -60 21 minecraft:water_cauldron[level=3]

# Woodshop (east): smoker, Chopping Block, fletching table, Wood Lathe, Treadle Lathe, Bowyer's Bench
setblock -11 -60 22 minecraft:smoker[facing=west]
setblock -11 -60 24 dungeondelving:chopping_block[facing=west]
setblock -11 -60 26 minecraft:fletching_table
setblock -13 -60 27 dungeondelving:wood_lathe[facing=north]
setblock -11 -60 28 dungeondelving:treadle_lathe[facing=west]
setblock -9 -60 26 dungeondelving:bowyers_bench[facing=west]

# Skill levels high enough for ★7, and a kit for each craft
skills set leatherworking 30
skills set weaving 30
skills set woodworking 30
give @p minecraft:leather 8
give @p dungeondelving:bark_liquor 3
give @p dungeondelving:elder_liquor 3
give @p minecraft:shears
give @p minecraft:white_wool 8
give @p minecraft:string 16
give @p minecraft:oak_log 8
give @p minecraft:iron_axe
give @p dungeondelving:linseed_oil 2
give @p minecraft:coal 8
tellraw @a {"text":"Test crafts hall built. Right-click the Guild Ledger to check it.","color":"green"}
