# Builds a small test vault in a superflat world: start room, tunnel, pillars room, and caps on the start room.
# Run with /function dungeondelving:dev/test_vault, or put that command in a command block.
function dungeondelving:dev/clear_test_vault

# Rooms
place template dungeondelving:vault/start_placeholder 0 -60 0
place template dungeondelving:vault/common_pillars_placeholder 0 -60 68

# Tunnel from the start room's south side to the pillars room, doorway walls included
place template dungeondelving:vault/tunnel_placeholder 0 -60 33

# Caps on the start room's north, west and east sides
place template dungeondelving:vault/wall_cap_placeholder 0 -60 -1
place template dungeondelving:vault/wall_cap_placeholder -1 -60 0 clockwise_90
place template dungeondelving:vault/wall_cap_placeholder 33 -60 0 clockwise_90

tellraw @a {"text":"Test vault built.","color":"green"}
