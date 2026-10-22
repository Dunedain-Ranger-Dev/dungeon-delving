# Gives a kit for testing forged parts and gear without forging every ingot: tempered ingots at several stars, every
# flux, and the leather and cloth makings. Run with /function dungeondelving:dev/forging_kit.
give @p dungeondelving:tempered_iron_ingot[dungeondelving:stars=1] 32
give @p dungeondelving:tempered_iron_ingot[dungeondelving:stars=3] 32
give @p dungeondelving:tempered_iron_ingot[dungeondelving:stars=5] 32
give @p dungeondelving:tempered_iron_ingot[dungeondelving:stars=7] 32
give @p dungeondelving:sand_flux 16
give @p dungeondelving:borax_flux 16
give @p dungeondelving:runic_flux 8
give @p dungeondelving:runeforged_flux 8
give @p minecraft:iron_nugget 16
give @p minecraft:leather 16
give @p minecraft:honeycomb 16
give @p minecraft:string 32
give @p minecraft:white_wool 8
give @p minecraft:stick 8
give @p dungeondelving:quenching_oil 3
tellraw @a {"text":"Forging kit given.","color":"green"}
