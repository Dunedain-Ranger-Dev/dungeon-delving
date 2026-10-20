"""Writes the dungeon loot tables: per dungeon tier, a room chest table, an objective chest table, and each boss's drops.

Run from the project root: python tools/loot_tables.py
Output: src/main/resources/data/dungeondelving/loot_table/chests/tier_<n>/{room,objective}.json
        src/main/resources/data/dungeondelving/loot_table/bosses/<boss>/tier_<n>.json

Vanilla items plus the dungeon ores, scaling with tier. Dungeon ores found in a dungeon are lost if the run fails
(see DungeonLoot); everything else is the player's to keep. Magic-mod loot is added by the pack, not here.
"""
import json
from pathlib import Path

LOOT = Path("src/main/resources/data/dungeondelving/loot_table/chests")
NS = "dungeondelving"
# Must match DungeonOre.java: the ore for each tier.
TIER_ORES = {1: "thraggite", 2: "wardsilver", 3: "emberstone", 4: "thailite"}
COMMON_ORE = "barrow_iron"


def item(name, weight, low=1, high=1, extra=()):
    entry = {"type": "minecraft:item", "name": name, "weight": weight}
    functions = []
    if (low, high) != (1, 1):
        functions.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": low, "max": high}})
    functions.extend(extra)
    if functions:
        entry["functions"] = functions
    return entry


def nothing(weight):
    return {"type": "minecraft:empty", "weight": weight}


def pool(low, high, entries):
    rolls = low if low == high else {"type": "minecraft:uniform", "min": low, "max": high}
    return {"rolls": rolls, "entries": entries}


def enchanted_book(weight, levels_low, levels_high):
    return item("minecraft:book", weight, extra=[{
        "function": "minecraft:enchant_with_levels",
        "levels": {"type": "minecraft:uniform", "min": levels_low, "max": levels_high},
    }])


def room(tier):
    """A loot-marker chest: supplies, a chance of dungeon ore, and a small chance of something valuable."""
    t = tier
    supplies = [
        item("minecraft:bread", 10, 1, 2 + t),
        item("minecraft:torch", 8, 4, 8 + 2 * t),
        item("minecraft:arrow", 8, 4, 8 + 4 * t),
        item("minecraft:iron_ingot", 8, 1, 2 + t),
        item("minecraft:gold_nugget", 8, 2, 6 + 2 * t),
        item("minecraft:coal", 6, 2, 4 + t),
        item("minecraft:bone", 5, 1, 3),
        item("minecraft:string", 5, 1, 3),
    ]
    if t >= 2:
        supplies.append(item("minecraft:cooked_beef", 8, 1, 2 + t))
        supplies.append(item("minecraft:gold_ingot", 4, 1, t))
    ore = [
        item(f"{NS}:raw_{COMMON_ORE}", 8, 2, 3 + t),
        item(f"{NS}:raw_{TIER_ORES[t]}", 4, 1, 1 + t),
        nothing(6),
    ]
    valuables = [
        item("minecraft:emerald", 6, 1, t),
        item("minecraft:ender_pearl", 3, 1, 1 + t // 2),
        enchanted_book(3, 5 * t, 10 * t),
        nothing(30 - 5 * t),
    ]
    if t >= 2:
        valuables.append(item("minecraft:diamond", t, 1, 1 + t // 3))
    if t >= 3:
        valuables.append(item("minecraft:golden_apple", 2))
    return {"type": "minecraft:chest", "pools": [pool(2, 3 + t // 2, supplies), pool(1, 1, ore), pool(1, 1, valuables)]}


def objective(tier):
    """The objective reward chest: guaranteed tier ingots, plus several valuable rolls."""
    t = tier
    ingots = [
        item(f"{NS}:{TIER_ORES[t]}_ingot", 1, 2, 2 + t),
    ]
    common = [
        item(f"{NS}:{COMMON_ORE}_ingot", 1, 3, 4 + 2 * t),
    ]
    valuables = [
        item("minecraft:gold_ingot", 10, 2, 3 + 2 * t),
        item("minecraft:iron_ingot", 10, 3, 4 + 3 * t),
        item("minecraft:emerald", 8, 1, 2 + t),
        item("minecraft:diamond", 2 + 2 * t, 1, 1 + t // 2),
        enchanted_book(4 + t, 10 * t, 10 + 10 * t),
        item("minecraft:golden_apple", 2 + t),
        item("minecraft:experience_bottle", 6, 2, 4 + 2 * t),
    ]
    if t >= 3:
        valuables.append(item("minecraft:netherite_scrap", t - 2))
    if t >= 4:
        valuables.append(item("minecraft:enchanted_golden_apple", 1))
    return {"type": "minecraft:chest", "pools": [pool(1, 1, ingots), pool(1, 1, common), pool(3, 4 + t // 2, valuables)]}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


def named(name, color, lore):
    """Functions that give an item a custom name and lore line, for boss-specific rewards."""
    return [
        {"function": "minecraft:set_name", "target": "item_name", "name": {"text": name, "color": color, "italic": False}},
        {"function": "minecraft:set_lore", "mode": "replace_all", "lore": [{"text": lore, "color": "gray", "italic": True}]},
    ]


def barrow_king(tier):
    """The Barrow King: the boss pays out about half a run's rewards, plus a chance at its signature crown."""
    t = tier
    crown = item("minecraft:golden_helmet", 1, extra=named("Barrow King's Crown", "gold", "Still cold from the barrow.") + [{
        "function": "minecraft:enchant_with_levels",
        "levels": {"type": "minecraft:uniform", "min": 10 * t, "max": 10 + 10 * t},
    }])
    signature = [crown, nothing(3)]
    ingots = [item(f"{NS}:{TIER_ORES[t]}_ingot", 1, 3, 3 + 2 * t)]
    common = [item(f"{NS}:{COMMON_ORE}_ingot", 1, 4, 6 + 2 * t)]
    valuables = [
        item("minecraft:gold_ingot", 10, 3, 4 + 2 * t),
        item("minecraft:emerald", 8, 2, 3 + t),
        item("minecraft:diamond", 3 + 2 * t, 1, 1 + t),
        enchanted_book(5 + t, 10 + 10 * t, 15 + 10 * t),
        item("minecraft:golden_apple", 3 + t),
        item("minecraft:experience_bottle", 6, 4, 6 + 3 * t),
        item("minecraft:wither_skeleton_skull", 2),
    ]
    if t >= 2:
        valuables.append(item("minecraft:netherite_scrap", t - 1))
    if t >= 4:
        valuables.append(item("minecraft:enchanted_golden_apple", 2))
    return {"type": "minecraft:entity", "pools": [pool(1, 1, signature), pool(1, 1, ingots), pool(1, 1, common), pool(4, 5 + t // 2, valuables)]}


BOSSES = {"barrow_king": barrow_king}
BOSS_LOOT = Path("src/main/resources/data/dungeondelving/loot_table/bosses")

for tier in TIER_ORES:
    write(LOOT / f"tier_{tier}/room.json", room(tier))
    write(LOOT / f"tier_{tier}/objective.json", objective(tier))
    for boss, table in BOSSES.items():
        write(BOSS_LOOT / f"{boss}/tier_{tier}.json", table(tier))
print("Wrote chest and boss loot tables for tiers", ", ".join(map(str, TIER_ORES)))
