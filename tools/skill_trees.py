"""Writes our skill trees for Pufferfish's Skills: one category per skill, each with its own experience, levels and
points, and a small first tree of nodes. The professions (DD-80), and the combat style, armor, magic school and
Beastmastery trees (DD-81), whose experience our own code awards (ActivitySkills, MagicSchool).

Run from the project root: python tools/skill_trees.py

Gathering professions (Mining, Woodcutting, Excavation, Farming, Fishing) earn experience from Pufferfish's own
sources. The crafting professions (Forging, Leatherworking, Weaving) have no sources here: our forging code awards
their experience (CraftingSkills, mirrored into these categories), so their curve and level cap must match
CraftingSkills: 20 * (L - 1)^2 total XP for level L, levels 1 to 30 (Pufferfish counts from 0, so 0 to 29).

The nodes are a first pass: small attribute bonuses. Vein mining, tree felling and the forging perks come later as
nodes our own code reacts to.
"""
import json
import zipfile
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


def best_of(terms, floor="0"):
    """An expression for the largest of some terms (nested two-argument max), at least floor."""
    expression = floor
    for term in terms:
        expression = f"max({expression}, {term})"
    return expression


# Mining with Silk Touch: the tool has the enchantment.
SILK_TOUCH = {"operations": [{"type": "get_tool_item_stack"}, {"type": "puffish_skills:test", "data": {
    "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}


def mine(blocks_xp, no_silk_touch=()):
    """A mine_block source giving experience for blocks in each tag: {"#tag": xp}. A block in several of the tags
    gets the best one's experience, not their sum (stone is in two stone tags). Tags in no_silk_touch give nothing
    when mined with Silk Touch: the block comes back whole, so it could be placed and mined again for free XP."""
    variables, terms = {}, []
    for i, (tag, xp) in enumerate(blocks_xp.items()):
        name = f"block_{i}"
        variables[name] = {"operations": [{"type": "get_mined_block_state"}, {"type": "puffish_skills:test", "data": {"block": tag}}]}
        terms.append(f"{name} * {xp} * (1 - silk_touch)" if tag in no_silk_touch else f"{name} * {xp}")
    if no_silk_touch:
        variables["silk_touch"] = SILK_TOUCH
    return {"type": "puffish_skills:mine_block", "data": {"variables": variables, "experience": best_of(terms)}}


def fish(fish_xp, other):
    """A fish_item source: experience by what was caught ({"item id": xp}), and `other` for anything else (cod,
    junk, treasure)."""
    variables, terms = {}, []
    for i, (item, xp) in enumerate(fish_xp.items()):
        name = f"fish_{i}"
        variables[name] = {"operations": [{"type": "get_fished_item_stack"}, {"type": "puffish_skills:test", "data": {"item": item}}]}
        terms.append(f"{name} * {xp}")
    return {"type": "puffish_skills:fish_item", "data": {"variables": variables, "experience": best_of(terms, str(other))}}


def attribute(attr, value, operation="multiply_base"):
    return {"type": "puffish_skills:attribute", "data": {"attribute": attr, "operation": operation, "value": value}}


def node(key, title, description, rewards, mods=()):
    """A tree node; mods are the mods its rewards need (Pufferfish drops the node if one is missing)."""
    return (key, title, description, rewards, list(mods))


def melee(key, name):
    return [node(f"{key}_training", f"{name} Training", "+3% melee damage.", [attribute("minecraft:generic.attack_damage", 0.03)]),
            node(f"{key}_mastery", f"{name} Mastery", "+3% melee damage.", [attribute("minecraft:generic.attack_damage", 0.03)])]


def ranged(key, name):
    return [node(f"{key}_training", f"{name} Training", "+3% ranged damage.",
                 [attribute("ranged_weapon:damage", 0.03, "multiply_total")], ["ranged_weapon_api"]),
            node(f"{key}_mastery", f"{name} Mastery", "+3% ranged damage.",
                 [attribute("ranged_weapon:damage", 0.03, "multiply_total")], ["ranged_weapon_api"])]


def school(key, name, spell_power, irons=()):
    """A magic school tree. spell_power is its Spell Power school (None if it has none); irons are its Iron's Spells
    schools, each with a node raising that school's Iron's spell power."""
    nodes = [] if spell_power is None else [
        node(f"{key}_adept", f"{name} Adept", f"+0.5 {name.lower()} spell power.",
             [attribute(f"spell_power:{spell_power}", 0.5, "addition")], ["spell_power"]),
        node(f"{key}_expert", f"{name} Expert", f"+0.5 {name.lower()} spell power.",
             [attribute(f"spell_power:{spell_power}", 0.5, "addition")], ["spell_power"])]
    for irons_school in ([irons] if isinstance(irons, str) else irons):
        lore = irons_school if len(irons) > 1 and not isinstance(irons, str) else key
        title = irons_school.capitalize() if lore != key else name
        nodes.append(node(f"{lore}_lore", f"{title} Lore", f"+3% {irons_school} spell power for Iron's Spells.",
                          [attribute(f"irons_spellbooks:{irons_school}_spell_power", 0.03)], ["irons_spellbooks"]))
    return nodes


# key -> (title, icon item, sources or None for crafting, [(node key, title, description, rewards)])
PROFESSIONS = {
    "mining": ("Mining", "minecraft:iron_pickaxe",
               [mine({"#c:ores": 10, "#minecraft:base_stone_overworld": 1, "#c:stones": 1}, no_silk_touch=("#c:ores",))],
               [node("quick_pick", "Quick Pick", "+5% mining speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                node("quicker_pick", "Quicker Pick", "+5% mining speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                node("prospector", "Prospector", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])]),
    "woodcutting": ("Woodcutting", "minecraft:iron_axe", [mine({"#minecraft:logs": 4})],
                    [node("sure_swing", "Sure Swing", "+5% breaking speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                     node("timber", "Timber", "+5% breaking speed.", [attribute("minecraft:player.block_break_speed", 0.05)])]),
    "excavation": ("Excavation", "minecraft:iron_shovel", [mine({"#minecraft:mineable/shovel": 1})],
                   [node("digger", "Digger", "+5% breaking speed.", [attribute("minecraft:player.block_break_speed", 0.05)]),
                    node("treasure_sense", "Treasure Sense", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])]),
    "farming": ("Farming", "minecraft:iron_hoe", [mine({"#minecraft:crops": 3})],
                [node("green_thumb", "Green Thumb", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")]),
                 node("hardy", "Hardy", "+2 max health.", [attribute("minecraft:generic.max_health", 2, "addition")])]),
    "fishing": ("Fishing", "minecraft:fishing_rod",
                [fish({"minecraft:salmon": 5, "minecraft:pufferfish": 8, "minecraft:tropical_fish": 15}, 2)],
                [node("patient", "Patient", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")]),
                 node("angler", "Angler", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])]),
    "forging": ("Forging", "dungeondelving:master_anvil", None,
                [node("apprentice_smith", "Apprentice Smith", "Your first step as a smith. Forging perks move into this tree in a later update.", [])]),
    "leatherworking": ("Leatherworking", "minecraft:leather", None,
                       [node("apprentice_tanner", "Apprentice Tanner", "Your first step as a tanner. Leatherworking perks move into this tree in a later update.", [])]),
    "weaving": ("Weaving", "minecraft:string", None,
                [node("apprentice_weaver", "Apprentice Weaver", "Your first step as a weaver. Weaving perks move into this tree in a later update.", [])]),
}


# The activity skills (DD-81): our code awards their experience (ActivitySkills, MagicSchool), so they have no sources here.
ACTIVITIES = {
    "one_handed": ("One-Handed", "minecraft:iron_sword", melee("one_handed", "One-Handed")),
    "two_handed": ("Two-Handed", "minecraft:netherite_axe", melee("two_handed", "Two-Handed")),
    "polearms": ("Polearms", "minecraft:trident", melee("polearms", "Polearm")),
    "daggers": ("Daggers", "rogues:iron_dagger", melee("daggers", "Dagger")),
    "unarmed": ("Unarmed", "minecraft:leather", melee("unarmed", "Unarmed")),
    "archery": ("Archery", "minecraft:bow", ranged("archery", "Archery")),
    "crossbows": ("Crossbows", "minecraft:crossbow", ranged("crossbows", "Crossbow")),
    "dual_wielding": ("Dual Wielding", "texture:dungeondelving:textures/gui/skill_icons/dual_wielding.png",
                      [node("ambidextrous", "Ambidextrous", "+2% attack speed.", [attribute("minecraft:generic.attack_speed", 0.02)]),
                       node("flurry", "Flurry", "+2% attack speed.", [attribute("minecraft:generic.attack_speed", 0.02)])]),
    "blocking": ("Blocking", "minecraft:shield",
                 [node("braced", "Braced", "+5% knockback resistance.", [attribute("minecraft:generic.knockback_resistance", 0.05, "addition")]),
                  node("bulwark", "Bulwark", "+1 armor.", [attribute("minecraft:generic.armor", 1, "addition")])]),
    "light_armor": ("Light Armor", "minecraft:leather_chestplate",
                    [node("light_step", "Light Step", "+2% movement speed.", [attribute("minecraft:generic.movement_speed", 0.02)]),
                     node("evasive", "Evasive", "+2% movement speed.", [attribute("minecraft:generic.movement_speed", 0.02)])]),
    "medium_armor": ("Medium Armor", "minecraft:chainmail_chestplate",
                     [node("fitted_mail", "Fitted Mail", "+1 armor.", [attribute("minecraft:generic.armor", 1, "addition")]),
                      node("balanced", "Balanced", "+1% movement speed.", [attribute("minecraft:generic.movement_speed", 0.01)])]),
    "heavy_armor": ("Heavy Armor", "minecraft:iron_chestplate",
                    [node("iron_skin", "Iron Skin", "+1 armor toughness.", [attribute("minecraft:generic.armor_toughness", 1, "addition")]),
                     node("immovable", "Immovable", "+5% knockback resistance.", [attribute("minecraft:generic.knockback_resistance", 0.05, "addition")])]),
    "mage_armor": ("Mage Armor", "minecraft:purple_wool",
                   [node("woven_wards", "Woven Wards", "+0.5 spell power.", [attribute("spell_power:generic", 0.5, "addition")], ["spell_power"]),
                    node("mana_weave", "Mana Weave", "+10 max mana for Iron's Spells.", [attribute("irons_spellbooks:max_mana", 10, "addition")], ["irons_spellbooks"])]),
    "fire_magic": ("Fire Magic", "minecraft:blaze_powder", school("fire", "Fire", "fire", "fire")),
    "frost_magic": ("Frost Magic", "minecraft:blue_ice", school("frost", "Frost", "frost", "ice")),
    "lightning_magic": ("Lightning Magic", "minecraft:lightning_rod", school("lightning", "Lightning", "lightning", "lightning")),
    "arcane_magic": ("Arcane Magic", "minecraft:amethyst_shard", school("arcane", "Arcane", "arcane", "ender")),
    "holy_magic": ("Holy Magic", "minecraft:golden_apple", school("holy", "Holy", "healing", "holy")),
    "earth_magic": ("Earth Magic", "minecraft:moss_block", school("earth", "Earth", "earth", "nature")),
    "water_magic": ("Water Magic", "minecraft:heart_of_the_sea", school("water", "Water", "water")),
    "air_magic": ("Air Magic", "minecraft:feather", school("air", "Air", "air")),
    "necromancy": ("Necromancy", "minecraft:wither_skeleton_skull", school("necromancy", "Necromancy", "soul", ("eldritch", "blood"))),
    "beastmastery": ("Beastmastery", "minecraft:lead",
                     [node("animal_friend", "Animal Friend", "Your first step as a beastmaster. Companions and their perks come in a later update.", [])]),
}


def icon_json(icon):
    """A Pufferfish icon: an item id, or "texture:<path>" for one of our textures (like the crossed swords)."""
    if icon.startswith("texture:"):
        return {"type": "texture", "data": {"texture": icon[len("texture:"):]}}
    return {"type": "item", "data": {"item": icon}}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


lang = json.loads(LANG.read_text(encoding="utf-8"))
TREES = {**PROFESSIONS, **{key: (title, icon, [], nodes) for key, (title, icon, nodes) in ACTIVITIES.items()}}
# The class trees (DD-83): the RPG Series' radial class tree (Skill Tree), split into one tree per class. Each keeps
# its branches' nodes, layout, spells and experience (from kills), and starts locked: picking the class unlocks it.
# Read from the Skill Tree jar in Gradle's cache, so run this after a Gradle build or run.
CLASSES = {
    "fighter": ("Fighter", "rogues:berserker_armor_head", ["warrior", "paladin"]),
    "archer": ("Archer", "archers:small_quiver", ["archer"]),
    "rogue": ("Rogue", "rogues:assassin_armor_head", ["rogue"]),
    "mage": ("Mage", "elemental_wizards_rpg:elemental_head", ["arcane", "fire", "frost", "priest"]),
}
GRADLE_CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1/maven.modrinth/skill-tree"


def skill_tree_jar():
    jars = sorted(GRADLE_CACHE.rglob("*.jar"))
    if not jars:
        raise SystemExit("Skill Tree jar not found in the Gradle cache: run a Gradle build or runClient first")
    return zipfile.ZipFile(jars[-1])


def write_classes(lang):
    jar = skill_tree_jar()
    base = "data/skill_tree_rpgs/puffish_skills/categories/class_skills/"
    load = lambda name: json.loads(jar.read(base + name).decode("utf-8"))
    category, definitions, skills, connections, experience = (load(n) for n in
        ("category.json", "definitions.json", "skills.json", "connections.json", "experience.json"))
    for key, (title, icon, branches) in CLASSES.items():
        kept = {sid: s for sid, s in skills.items() if any(s["definition"].startswith(b + "_") for b in branches)}
        used = {s["definition"] for s in kept.values()}
        folder = DATA / "categories" / key
        lang[f"skill_category.{NS}.{key}"] = title
        # Locked until the class is picked. One class can take several of its branches (a mage can mix fire and frost),
        # and points come from the class's own levels, so there's no cap on how many are spent.
        cat = {**category, "title": {"translate": f"skill_category.{NS}.{key}"}, "icon": icon_json(icon),
               "unlocked_by_default": False, "exclusive_root": False}
        cat.pop("spent_points_limit", None)
        write(folder / "category.json", cat)
        write(folder / "definitions.json", {d: v for d, v in definitions.items() if d in used})
        write(folder / "skills.json", kept)
        write(folder / "connections.json", {kind: {direction: [pair for pair in pairs if all(s in kept for s in pair)]
                                                    for direction, pairs in groups.items()} for kind, groups in connections.items()})
        write(folder / "experience.json", experience)


write_classes(lang)
write(DATA / "config.json", {"version": 3, "categories": list(TREES) + list(CLASSES)})
for key, (title, icon, sources, nodes) in TREES.items():
    folder = DATA / "categories" / key
    lang[f"skill_category.{NS}.{key}"] = title
    write(folder / "category.json", {
        "title": {"translate": f"skill_category.{NS}.{key}"},
        "icon": icon_json(icon),
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
    for i, (node_key, node_title, description, rewards, mods) in enumerate(nodes):
        definitions[node_key] = {
            "title": {"translate": f"skill.{NS}.{key}.{node_key}"},
            "description": {"translate": f"skill.{NS}.{key}.{node_key}.description"},
            "icon": icon_json(icon),
            "frame": {"type": "advancement", "data": {"frame": "task" if i < len(nodes) - 1 or len(nodes) == 1 else "goal"}},
            "rewards": rewards,
            "cost": 1,
            **({"required_mods": mods} if mods else {}),
        }
        lang[f"skill.{NS}.{key}.{node_key}"] = node_title
        lang[f"skill.{NS}.{key}.{node_key}.description"] = description
        skills[node_key] = {"definition": node_key, "x": i * 40, "y": 0, "root": i == 0}
        if i > 0:
            links.append([nodes[i - 1][0], node_key])
    write(folder / "definitions.json", definitions)
    write(folder / "skills.json", skills)
    write(folder / "connections.json", {"normal": {"bidirectional": links}})
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
print("Wrote skill trees for", ", ".join(TREES), "and classes", ", ".join(CLASSES))
