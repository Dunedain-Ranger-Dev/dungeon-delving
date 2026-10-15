# Builds a small test dungeon in a superflat world: start room, tunnel, pillars room, and caps on the start room.
# Run with /function dungeondelving:dev/test_dungeon, or put that command in a command block.
function dungeondelving:dev/clear_test_dungeon

# Rooms
place template dungeondelving:dungeon/start_placeholder 0 -60 0
place template dungeondelving:dungeon/common_pillars_placeholder 0 -60 68

# Tunnel from the start room's south side to the pillars room, doorway walls included
place template dungeondelving:dungeon/tunnel_placeholder 0 -60 33

# Caps on the start room's north, west and east sides
place template dungeondelving:dungeon/cap_placeholder 0 -60 -1
place template dungeondelving:dungeon/cap_placeholder -1 -60 0 clockwise_90
place template dungeondelving:dungeon/cap_placeholder 33 -60 0 clockwise_90

tellraw @a {"text":"Test dungeon built.","color":"green"}
