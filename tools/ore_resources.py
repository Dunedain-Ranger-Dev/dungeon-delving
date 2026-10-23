"""Writes the dungeon ores' models, names, drops, recipes and tags.

Run from the project root: python tools/ore_resources.py
Must match DungeonOre.java. Textures come from tools/placeholder_ore_textures.py until real art exists.
"""
import json
from pathlib import Path

RESOURCES = Path("src/main/resources")
ASSETS = RESOURCES / "assets/dungeondelving"
DATA = RESOURCES / "data/dungeondelving"
NS = "dungeondelving"

# id key -> (display name, tier position for smelting XP, pickaxe needed)
ORES = {
    "barrow_iron": ("Barrow Iron", 0, "stone"),
    "thraggite": ("Thraggite", 1, "stone"),
    "wardsilver": ("Wardsilver", 2, "iron"),
    "emberstone": ("Emberstone", 3, "diamond"),
    "thailite": ("Thailite", 4, "diamond"),
}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8", newline="\n")


lang_path = ASSETS / "lang/en_us.json"
lang = json.loads(lang_path.read_text(encoding="utf-8"))
mineable, needs = [], {"stone": [], "iron": [], "diamond": []}
item_tags = {"dungeon_ores": [], "raw_dungeon_ores": [], "dungeon_ingots": [], "dungeon_storage_blocks": []}

for key, (name, tier, tool) in ORES.items():
    ore, block, raw, ingot = f"{key}_ore", f"{key}_block", f"raw_{key}", f"{key}_ingot"
    for b in (ore, block):
        write(ASSETS / f"blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
        write(ASSETS / f"models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{b}"}})
        write(ASSETS / f"models/item/{b}.json", {"parent": f"{NS}:block/{b}"})
    for i in (raw, ingot):
        write(ASSETS / f"models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{i}"}})
    lang[f"block.{NS}.{ore}"] = f"{name} Ore"
    lang[f"block.{NS}.{block}"] = f"Block of {name}"
    lang[f"item.{NS}.{raw}"] = f"Raw {name}"
    lang[f"item.{NS}.{ingot}"] = f"{name} Ingot"

    # Ore: silk touch drops the ore itself, otherwise raw ore with Fortune.
    silk_touch = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
    write(DATA / f"loot_table/blocks/{ore}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
        "type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{NS}:{ore}", "conditions": [silk_touch]},
            {"type": "minecraft:item", "name": f"{NS}:{raw}", "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})
    write(DATA / f"loot_table/blocks/{block}.json", {"type": "minecraft:block", "pools": [{"rolls": 1,
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    for kind, time in (("smelting", 200), ("blasting", 100)):
        write(DATA / f"recipe/{ingot}_from_{kind}.json", {"type": f"minecraft:{kind}", "category": "misc",
            "ingredient": {"item": f"{NS}:{raw}"}, "result": {"id": f"{NS}:{ingot}"}, "experience": round(0.7 + 0.2 * tier, 1), "cookingtime": time})
    write(DATA / f"recipe/{block}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["###", "###", "###"],
        "key": {"#": {"item": f"{NS}:{ingot}"}}, "result": {"id": f"{NS}:{block}", "count": 1}})
    write(DATA / f"recipe/{ingot}_from_block.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [{"item": f"{NS}:{block}"}], "result": {"id": f"{NS}:{ingot}", "count": 9}})

    mineable += [f"{NS}:{ore}", f"{NS}:{block}"]
    needs[tool] += [f"{NS}:{ore}", f"{NS}:{block}"]
    item_tags["dungeon_ores"].append(f"{NS}:{ore}")
    item_tags["raw_dungeon_ores"].append(f"{NS}:{raw}")
    item_tags["dungeon_ingots"].append(f"{NS}:{ingot}")
    item_tags["dungeon_storage_blocks"].append(f"{NS}:{block}")

lang_path.write_text(json.dumps(lang, indent=2) + "\n", encoding="utf-8", newline="\n")
vanilla_tags = RESOURCES / "data/minecraft/tags/block"
# Riftstone, the generic dungeon stone, mines with any pickaxe.
write(vanilla_tags / "mineable/pickaxe.json", {"replace": False, "values": mineable + [f"{NS}:riftstone"]})
for tool, values in needs.items():
    write(vanilla_tags / f"needs_{tool}_tool.json", {"replace": False, "values": values})
for tag, values in item_tags.items():
    write(DATA / f"tags/item/{tag}.json", {"replace": False, "values": values})
print("Wrote ore resources for", ", ".join(ORES))
