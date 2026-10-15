# Builds a small test dungeon in a superflat world: start room, tunnel, pillars room, and caps on the start room.
# Run with /function dungeondelving:dev/test_dungeon, or put that command in a command block.
function dungeondelving:dev/clear_test_dungeon

# Rooms (walls included), one grid step apart along z
place template dungeondelving:dungeon/start_placeholder 0 -60 0
place template dungeondelving:dungeon/common_pillars_placeholder 0 -60 66

# Tunnel from the start room's south doorway to the pillars room's north doorway
place template dungeondelving:dungeon/tunnel_placeholder 12 -60 33

# Caps plugging the start room's north, west and east doorways
place template dungeondelving:dungeon/cap_placeholder 14 -59 0
place template dungeondelving:dungeon/cap_placeholder 0 -59 14 clockwise_90
place template dungeondelving:dungeon/cap_placeholder 32 -59 14 clockwise_90

tellraw @a {"text":"Test dungeon built.","color":"green"}
