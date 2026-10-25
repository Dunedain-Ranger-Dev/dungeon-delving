# Builds a test crafts hall south of the test forge: a level 3 tannery, weaving room and woodshop around one Guild
# Ledger (DD-88), and gives a kit for every step. Run with /function dungeondelving:dev/test_crafts.
function dungeondelving:dev/clear_test_crafts

# Plank floor
fill -22 -61 16 -8 -61 30 minecraft:spruce_planks

# The Guild Ledger in the middle
setblock -15 -60 23 dungeondelving:guild_ledger[facing=south]

# Tannery (west): Tanning Rack, a cauldron for the vat, Shaping Rack, Tanning Barrel, Fleshing Beam
setblock -19 -60 20 dungeondelving:tanning_rack[facing=east]
setblock -19 -60 22 minecraft:cauldron
setblock -19 -60 24 dungeondelving:tanning_barrel
setblock -19 -60 26 dungeondelving:fleshing_beam[facing=east]
setblock -15 -60 26 dungeondelving:shaping_rack[facing=north]

# Weaving room (north): Spinning Wheel, a cauldron for the vat, loom, Great Loom, Dye Works
setblock -17 -60 19 dungeondelving:spinning_wheel[facing=south]
setblock -15 -60 21 minecraft:cauldron
setblock -15 -60 19 minecraft:loom[facing=south]
setblock -13 -60 19 dungeondelving:great_loom[facing=south]
setblock -11 -60 19 dungeondelving:dye_works

# Woodshop (east): smoker, a cauldron for the vat, Wood Lathe, Treadle Lathe, Bowyer's Bench
setblock -11 -60 22 minecraft:smoker[facing=west]
setblock -11 -60 24 minecraft:cauldron
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
give @p dungeondelving:wheat_sizing 2
give @p dungeondelving:arcane_sizing 2
give @p minecraft:oak_log 8
give @p dungeondelving:linseed_oil 2
give @p dungeondelving:sylvan_sap 2
give @p minecraft:coal 8
tellraw @a {"text":"Test crafts hall built. Right-click the Guild Ledger to check it.","color":"green"}
