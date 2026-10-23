# Builds a test Dungeon Gate (DD-75) in a superflat world, west of the test forge: the gate, a 2x3 Riftstone frame
# north of it, and a Gate Tuner beside it. Gives Runic Keys, Bloodshed Stones and building supplies.
# Run with /function dungeondelving:dev/test_gate.
function dungeondelving:dev/clear_test_gate

fill -40 -61 -2 -30 -61 8 minecraft:polished_deepslate
setblock -35 -60 2 dungeondelving:dungeon_gate
setblock -33 -60 2 dungeondelving:gate_tuner

# The frame: Riftstone under, over and at both sides of a 2-wide, 3-tall opening.
fill -37 -61 6 -34 -61 6 dungeondelving:riftstone
fill -37 -57 6 -34 -57 6 dungeondelving:riftstone
fill -37 -60 6 -37 -58 6 dungeondelving:riftstone
fill -34 -60 6 -34 -58 6 dungeondelving:riftstone

give @p dungeondelving:runic_key_1 2
give @p dungeondelving:runic_key_2 1
give @p dungeondelving:bloodshed_stone 2
give @p dungeondelving:gate_tuner 1
give @p dungeondelving:riftstone 16
