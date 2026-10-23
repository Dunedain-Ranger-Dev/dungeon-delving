"""Writes the profession skill trees (DD-80) for Pufferfish's Skills: one category per profession, each with its own
experience, levels and points, and a small first tree of nodes.

Run from the project root: python tools/skill_trees.py

Gathering professions (Mining, Woodcutting, Excavation, Farming, Fishing) earn experience from Pufferfish's own
sources. The crafting professions (Forging, Leatherworking, Weaving) have no sources here: our forging code awards
their experience (CraftingSkills, mirrored into these categories), so their curve and level cap must match
CraftingSkills: 20 * (L - 1)^2 total XP for level L, levels 1 to 30 (Pufferfish counts from 0, so 0 to 29).

The nodes are a first pass: small attribute bonuses. Vein mining, tree felling and the forging perks come later as
nodes our own code reacts to.
"""
import json
from pathlib import Path

RESOURCES = Path("src/main/resources")
DATA = RESOURCES / "data/dungeondelving/puffish_skills"
LANG = RESOURCES / "assets/dungeondelving/lang/en_us.json"
NS = "dungeondelving"

GATHERING_CURVE = "min(30 + level * 12 + level ^ 2, 6000)"
GATHERING_LIMIT = 50
# Matches CraftingSkills: going from (Pufferfish) level n to n + 1 takes 20 * (2n + 1) XP.
CRAFTING_CURVE = "20 * (2 * level + 1)"
CRAFTING_LIMIT = 29


def mine(blocks_xp):
    """A mine_block source giving experience for blocks in each tag: {"#tag": xp}."""
    variables, terms = {}, []
    for i, (tag, xp) in enumerate(blocks_xp.items()):
        name = f"block_{i}"
        variables[name] = {"operations": [{"type": "get_mined_block_state"}, {"type": "puffish_skills:test", "data": {"block": tag}}]}
        terms.append(f"{name} * {xp}")
    return {"type": "puffish_skills:mine_block", "data": {"variables": variables, "experience": " + ".join(terms)}}


def attribute(attr, value, operation="multiply_base"):
    return {"type": "puffish_skills:attribute", "data": {"attribute": attr, "operation": operation, "value": value}}


# key -> (title, icon item, sources or None for crafting, [(node key, title, description, rewards)])
PROFESSIONS = {
    "mining": ("Mining", "minecraft:iron_pickaxe",
               [mine({"#c:ores": 10, "#minecraft:base_stone_overworld": 1, "#c:stones": 1})],
               [("quick_pick", "Quick Pick", "+5% mining speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                ("quicker_pick", "Quicker Pick", "+5% mining speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                ("prospector", "Prospector", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])]),
    "woodcutting": ("Woodcutting", "minecraft:iron_axe", [mine({"#minecraft:logs": 4})],
                    [("sure_swing", "Sure Swing", "+5% breaking speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                     ("timber", "Timber", "+5% breaking speed.", [attribute("minecraft:player.block_break_speed", 0.05)])]),
    "excavation": ("Excavation", "minecraft:iron_shovel", [mine({"#minecraft:mineable/shovel": 1})],
                   [("digger", "Digger", "+5% breaking speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                    ("treasure_sense", "Treasure Sense", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])]),
    "farming": ("Farming", "minecraft:iron_hoe", [mine({"#minecraft:crops": 3})],
                [("green_thumb", "Green Thumb", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")]),
                 ("hardy", "Hardy", "+2 max health.", [attribute("minecraft:generic.max_health", 2, "addition")])]),
    "fishing": ("Fishing", "minecraft:fishing_rod", [{"type": "puffish_skills:fish_item", "data": {"experience": "15"}}],
                [("patient", "Patient", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")]),
                 ("angler", "Angler", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])]),
    "forging": ("Forging", "dungeondelving:master_anvil", None,
                [("apprentice_smith", "Apprentice Smith", "Your first step as a smith. Forging perks move into this tree in a later update.", [])]),
    "leatherworking": ("Leatherworking", "minecraft:leather", None,
                       [("apprentice_tanner", "Apprentice Tanner", "Your first step as a tanner. Leatherworking perks move into this tree in a later update.", [])]),
    "weaving": ("Weaving", "minecraft:string", None,
                [("apprentice_weaver", "Apprentice Weaver", "Your first step as a weaver. Weaving perks move into this tree in a later update.", [])]),
}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


lang = json.loads(LANG.read_text(encoding="utf-8"))
write(DATA / "config.json", {"version": 3, "categories": list(PROFESSIONS)})
for key, (title, icon, sources, nodes) in PROFESSIONS.items():
    folder = DATA / "categories" / key
    lang[f"skill_category.{NS}.{key}"] = title
    write(folder / "category.json", {
        "title": {"translate": f"skill_category.{NS}.{key}"},
        "icon": {"type": "item", "data": {"item": icon}},
        "unlocked_by_default": True,
        "exclusive_root": False,
        "background": {"texture": "minecraft:textures/block/deepslate_tiles.png", "width": 16, "height": 16, "position": "tile"},
    })
    crafting = sources is None
    write(folder / "experience.json", {
        "experience_per_level": {"type": "expression", "data": {"expression": CRAFTING_CURVE if crafting else GATHERING_CURVE}},
        "level_limit": CRAFTING_LIMIT if crafting else GATHERING_LIMIT,
        "sources": [] if crafting else sources,
    })
    definitions, skills, links = {}, {}, []
    for i, (node, node_title, description, rewards) in enumerate(nodes):
        definitions[node] = {
            "title": {"translate": f"skill.{NS}.{key}.{node}"},
            "description": {"translate": f"skill.{NS}.{key}.{node}.description"},
            "icon": {"type": "item", "data": {"item": icon}},
            "frame": {"type": "advancement", "data": {"frame": "task" if i < len(nodes) - 1 or len(nodes) == 1 else "goal"}},
            "rewards": rewards,
            "cost": 1,
        }
        lang[f"skill.{NS}.{key}.{node}"] = node_title
        lang[f"skill.{NS}.{key}.{node}.description"] = description
        skills[node] = {"definition": node, "x": i * 40, "y": 0, "root": i == 0}
        if i > 0:
            links.append([nodes[i - 1][0], node])
    write(folder / "definitions.json", definitions)
    write(folder / "skills.json", skills)
    write(folder / "connections.json", {"normal": {"bidirectional": links}})
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
print("Wrote skill trees for", ", ".join(PROFESSIONS))
