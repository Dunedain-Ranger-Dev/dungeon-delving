# Sets up a fresh dev world (e.g. a creative superflat) for testing: no natural mob spawns, patrols, traders or
# phantoms; always day and clear; no fire spread or mob griefing; inventory kept on death. Summoned mobs and dungeon
# spawners still work. Clears any mobs that spawned before. Run once with /function dungeondelving:dev/setup_world.
gamerule doMobSpawning false
gamerule doPatrolSpawning false
gamerule doTraderSpawning false
gamerule doInsomnia false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
gamerule doFireTick false
gamerule mobGriefing false
gamerule keepInventory true
time set day
weather clear
kill @e[type=!player]
tellraw @s {"text":"Dev world set up: no natural spawns, always day and clear, keep inventory.","color":"green"}
