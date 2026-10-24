"""Writes our skill trees for Pufferfish's Skills: one category per skill, each with its own experience, levels and
points. The professions (DD-80), the combat style, armor, magic school and Beastmastery trees (DD-81), and the class
trees (DD-83, from the RPG Series). The design is docs/design/skill-trees.md.

Run from the project root: python tools/skill_trees.py
It also writes build/skill-trees-preview.json, which tools/skill_tree_preview.py turns into a review page.

Gathering professions (Mining, Woodcutting, Excavation, Farming, Fishing) earn experience from Pufferfish's own
sources. The crafting professions (Forging, Leatherworking, Weaving) have no sources here: our forging code awards
their experience (CraftingSkills, mirrored into these categories), so their curve must match CraftingSkills:
20 * (L - 1)^2 total XP for level L (Pufferfish counts levels from 0). Levels are uncapped (DD-70): once a tree is
full, leftover points go into Mastery.

Every tree has the same vertical shape (shape() below): a root, two choices at level 5, a signature perk with ranks
at levels 10, 20 and 35, two refinements at 25, and a capstone at 50 that also needs points spent in the tree. Two
outer columns hold small stat bonuses, five ranks each (STATS). A node needs its parent and the skill level
("required_points": a point comes with each level). Ranks are nodes stacked in a column, since Pufferfish has no
multi-point nodes. Costs rise with depth, so a tree takes about 50 points, about the level its capstone needs; points
after that go into Mastery.

Rewards are attribute bonuses, or player tags our own code checks for (skill/Perk.java uses the same names:
dungeondelving.perk.<key>, or <key>_<rank> for a ranked perk).
"""
import json
import zipfile
from pathlib import Path

RESOURCES = Path("src/main/resources")
DATA = RESOURCES / "data/dungeondelving/puffish_skills"
LANG = RESOURCES / "assets/dungeondelving/lang/en_us.json"
PREVIEW = Path("build/skill-trees-preview.json")
NS = "dungeondelving"

GATHERING_CURVE = "min(30 + level * 12 + level ^ 2, 6000)"
# Matches CraftingSkills: going from (Pufferfish) level n to n + 1 takes 20 * (2n + 1) XP.
CRAFTING_CURVE = "20 * (2 * level + 1)"
# Grid spacing for the vertical layout, in Pufferfish's screen units.
DX, DY = 34, 34
CAPSTONE_SPENT = 30
# Points per node, by its place in the tree (shape()).
ROOT_COST, CHOICE_COST, SIGNATURE_COSTS, REFINEMENT_COST, CAPSTONE_COST, STAT_COST = 1, 2, [3, 5, 8], 4, 10, 1
# The stat columns' ranks: the level each needs.
STAT_LEVELS = [2, 8, 15, 30, 45]
# Perk tags our code acts on already (skill/Perk.java); the preview marks the rest as still to build.
IMPLEMENTED = {"steady_hand", "slow_cooling", "thrifty", "sure_strikes", "steady_scraper", "careful_tanner", "water_saver",
               "steady_needle", "careful_weaver", "vein_miner", "prospector", "tree_feller", "forester", "bountiful", "replanter"}


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


ROMAN = ["I", "II", "III", "IV", "V"]


def n(key, title, description, rewards=(), mods=(), active=False):
    """A node: its key, name, description, rewards and the mods they need (Pufferfish drops the node without them).
    active marks a timed ability (sneak and break to start it, then a cooldown)."""
    return {"key": key, "title": title, "description": description, "rewards": list(rewards), "mods": list(mods), "active": active}


def tag(key):
    return {"type": "puffish_skills:tag", "data": {"tag": f"{NS}.perk.{key}"}}


def perk(key, title, description, active=False):
    """A single-rank perk: its reward is the player tag our code checks."""
    return n(key, title, description, [tag(key)], active=active)


def ranks(key, title, descriptions, active=False, rewards=None, mods=()):
    """A perk with ranks (I, II, III): one node each, rewarding <key>_<rank>, or the given rewards per rank."""
    return [n(f"{key}_{i + 1}", f"{title} {ROMAN[i]}", d, rewards[i] if rewards else [tag(f"{key}_{i + 1}")], mods, active)
            for i, d in enumerate(descriptions)]


def stats(key, title, description, rewards=None, mods=()):
    """A stat column: five ranks of the same small bonus (rewards, for one rank), or of a perk tag (<key>_<rank>)."""
    return [n(f"{key}_{i + 1}", f"{title} {ROMAN[i]}", description, rewards if rewards else [tag(f"{key}_{i + 1}")], mods) for i in range(5)]


def shape(root, choices, signature, refinements, capstone, extra=()):
    """The shared vertical tree. Rows top to bottom: root (level 0); choices (level 5, left and right); signature
    rank I (10) and II (20); refinements (25, left and right, under rank II); signature rank III (35); capstone (50,
    with CAPSTONE_SPENT points spent). extra places more nodes: (node, column, row, level, parent key, cost). Each
    placed node is (node, column, row, level, parent key or None, points spent needed, cost). add() puts the stat
    columns either side."""
    placed = [(root, 0, 0, 0, None, 0, ROOT_COST)]
    for node, column in zip(choices, (-1, 1)):
        placed.append((node, column, 1, 5, root["key"], 0, CHOICE_COST))
    rows, levels = [2, 3, 5], [10, 20, 35]
    parent = root["key"]
    for i, node in enumerate(signature):
        placed.append((node, 0, rows[i], levels[i], parent, 0, SIGNATURE_COSTS[i]))
        parent = node["key"]
    above = signature[min(1, len(signature) - 1)]["key"]
    for node, column in zip(refinements, (-1, 1)):
        placed.append((node, column, 4, 25, above, 0, REFINEMENT_COST))
    last_row = max(row for _, _, row, _, _, _, _ in placed)
    for node, column, row, level, under, cost in extra:
        placed.append((node, column, row, level, under, 0, cost))
        last_row = max(last_row, row)
    placed.append((capstone, 0, last_row + 1, 50, parent if not extra else extra[-1][4] if extra[-1][1] != 0 else extra[-1][0]["key"],
                   CAPSTONE_SPENT, CAPSTONE_COST))
    return placed


# --- Gathering ---

TREES = {}
GROUPS = {}
MASTERY = {}
# Each tree's stat columns (left, right): small bonuses to spend points on as you level.
SPEED = "minecraft:player.block_break_speed"
LUCK, HEALTH, ARMOR = "minecraft:generic.luck", "minecraft:generic.max_health", "minecraft:generic.armor"
DAMAGE, ATTACK_SPEED, MOVE = "minecraft:generic.attack_damage", "minecraft:generic.attack_speed", "minecraft:generic.movement_speed"
RANGED_DAMAGE = "ranged_weapon:damage"


def melee_stats(key):
    return (stats(f"{key}_power", "Power", "+1% melee damage.", [attribute(DAMAGE, 0.01)]),
            stats(f"{key}_quickness", "Quickness", "+1% attack speed.", [attribute(ATTACK_SPEED, 0.01)]))


def ranged_stats(key):
    return (stats(f"{key}_power", "Power", "+1% ranged damage.", [attribute(RANGED_DAMAGE, 0.01, "multiply_total")], ["ranged_weapon_api"]),
            stats(f"{key}_mobility", "Mobility", "+1% movement speed.", [attribute(MOVE, 0.01)]))


STATS = {
    "mining": (stats("swift_pick", "Swift Pick", "+2% mining speed.", [attribute(SPEED, 0.02)]),
               stats("miners_constitution", "Miner's Constitution", "+1 max health.", [attribute(HEALTH, 1, "addition")])),
    "woodcutting": (stats("swift_axe", "Swift Axe", "+2% breaking speed.", [attribute(SPEED, 0.02)]),
                    stats("woodland_luck", "Woodland Luck", "+0.1 luck.", [attribute(LUCK, 0.1, "addition")])),
    "excavation": (stats("quick_dig", "Quick Dig", "+2% breaking speed.", [attribute(SPEED, 0.02)]),
                   stats("keen_eye", "Keen Eye", "+0.1 luck.", [attribute(LUCK, 0.1, "addition")])),
    "farming": (stats("green_fingers", "Green Fingers", "+0.1 luck.", [attribute(LUCK, 0.1, "addition")]),
                stats("farmhand", "Farmhand", "+1 max health.", [attribute(HEALTH, 1, "addition")])),
    "fishing": (stats("patience", "Patience", "+0.1 luck.", [attribute(LUCK, 0.1, "addition")]),
                stats("deep_breath", "Deep Breath", "Stay underwater a little longer.", [attribute("minecraft:generic.oxygen_bonus", 0.2, "addition")])),
    "forging": (stats("strong_arm", "Strong Arm", "+0.2 attack damage.", [attribute(DAMAGE, 0.2, "addition")]),
                stats("forge_studies", "Forge Studies", "+5% Forging XP.")),
    "leatherworking": (stats("tough_hide", "Tough Hide", "+0.2 armor.", [attribute(ARMOR, 0.2, "addition")]),
                       stats("tanning_studies", "Tanning Studies", "+5% Leatherworking XP.")),
    "weaving": (stats("nimble", "Nimble", "+1% movement speed.", [attribute(MOVE, 0.01)]),
                stats("weaving_studies", "Weaving Studies", "+5% Weaving XP.")),
    **{key: melee_stats(key) for key in ("one_handed", "two_handed", "polearms", "daggers", "unarmed")},
    **{key: ranged_stats(key) for key in ("archery", "crossbows")},
    "dual_wielding": tuple(reversed(melee_stats("dual_wielding"))),
    "blocking": (stats("blocking_stance", "Stance", "+1% knockback resistance.", [attribute("minecraft:generic.knockback_resistance", 0.01, "addition")]),
                 stats("blocking_guard", "Guard", "+0.2 armor.", [attribute(ARMOR, 0.2, "addition")])),
    "light_armor": (stats("light_fleetness", "Fleetness", "+1% movement speed.", [attribute(MOVE, 0.01)]),
                    stats("light_padding", "Padding", "+0.2 armor.", [attribute(ARMOR, 0.2, "addition")])),
    "medium_armor": (stats("medium_guard", "Guard", "+0.2 armor.", [attribute(ARMOR, 0.2, "addition")]),
                     stats("medium_fleetness", "Fleetness", "+1% movement speed.", [attribute(MOVE, 0.01)])),
    "heavy_armor": (stats("heavy_hardening", "Hardening", "+0.2 armor toughness.", [attribute("minecraft:generic.armor_toughness", 0.2, "addition")]),
                    stats("heavy_vigor", "Vigor", "+1 max health.", [attribute(HEALTH, 1, "addition")])),
    "mage_armor": (stats("mage_attunement", "Attunement", "+0.1 spell power.", [attribute("spell_power:generic", 0.1, "addition")], ["spell_power"]),
                   stats("mage_wellspring", "Wellspring", "+5 max mana for Iron's Spells.", [attribute("irons_spellbooks:max_mana", 5, "addition")], ["irons_spellbooks"])),
}


def add(group, key, title, icon, sources, placed, mastery):
    """A tree, with its stat columns (STATS) added either side: rank I hangs off the root, the rest below it."""
    for side, column in zip(STATS.get(key, ()), (-2, 2)):
        parent = placed[0][0]["key"]
        for i, node in enumerate(side):
            placed.append((node, column, i + 1, STAT_LEVELS[i], parent, 0, STAT_COST))
            parent = node["key"]
    TREES[key] = (title, icon, sources, placed)
    GROUPS[key] = group
    MASTERY[key] = mastery


add("Gathering", "mining", "Mining", "minecraft:iron_pickaxe",
    [mine({"#c:ores": 10, "#minecraft:base_stone_overworld": 1, "#c:stones": 1}, no_silk_touch=("#c:ores",))],
    shape(n("quick_pick", "Quick Pick", "+5% mining speed.", [attribute(SPEED, 0.05)]),
          [perk("prospector", "Prospector", "A 10% chance of a second drop from ore (not with Silk Touch)."),
           perk("stonemason", "Stonemason", "+15% mining speed on stone and deepslate.")],
          ranks("vein_miner", "Vein Miner", [f"Sneak and mine ore to start: for {s} seconds, every ore you mine takes its whole vein. "
                                             f"Then a {c} minute cooldown." for s, c in ((10, 4), (15, 3), (20, 2))], active=True),
          [perk("sturdy_pick", "Sturdy Pick", "A 15% chance mining costs your pickaxe no durability."),
           perk("deep_sight", "Deep Sight", "When Vein Miner starts, ores within 6 blocks glow for a few seconds.")],
          perk("motherlode", "Motherlode", "Prospector's chance doubles while Vein Miner is active.")),
    "Mining speed, up to +50%")

add("Gathering", "woodcutting", "Woodcutting", "minecraft:iron_axe", [mine({"#minecraft:logs": 4})],
    shape(n("sure_swing", "Sure Swing", "+5% breaking speed.", [attribute(SPEED, 0.05)]),
          [perk("forester", "Forester", "Chopping a tree's bottom log plants its sapling half the time (four for a 2x2 trunk)."),
           perk("timber", "Timber", "+15% breaking speed on logs.")],
          ranks("tree_feller", "Tree Feller", [f"Sneak and chop a log to start: for {s} seconds, every log you chop fells its whole tree. "
                                               f"Then a {c} minute cooldown. Only natural trees." for s, c in ((10, 4), (15, 3), (20, 2))], active=True),
          [perk("sturdy_axe", "Sturdy Axe", "A 15% chance chopping costs your axe no durability."),
           perk("bark_collector", "Bark Collector", "A 10% chance of a second log.")],
          perk("heartwood", "Heartwood", "Tree Feller uses half the durability.")),
    "Tree Feller's duration, up to +100%")

add("Gathering", "excavation", "Excavation", "minecraft:iron_shovel", [mine({"#minecraft:mineable/shovel": 1})],
    shape(n("digger", "Digger", "+5% breaking speed.", [attribute(SPEED, 0.05)]),
          [perk("treasure_sense", "Treasure Sense", "A 1% chance of a find from dirt, sand and gravel: flint, clay, bones, now and then a gem."),
           perk("swift_shovel", "Swift Shovel", "+15% speed on shovel blocks.")],
          ranks("earthmover", "Earthmover", [f"Sneak and dig to start: for {s} seconds, every shovel block you dig clears a 3x3 face. "
                                             f"Then a {c} minute cooldown." for s, c in ((10, 4), (15, 3), (20, 2))], active=True),
          [perk("sturdy_shovel", "Sturdy Shovel", "A 15% chance digging costs your shovel no durability."),
           perk("sifter", "Sifter", "Gravel always drops flint.")],
          perk("buried_riches", "Buried Riches", "Treasure Sense's chance triples while Earthmover is active.")),
    "Treasure Sense's chance, up to +5%")

add("Gathering", "farming", "Farming", "minecraft:iron_hoe", [mine({"#minecraft:crops": 3})],
    shape(n("green_thumb", "Green Thumb", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")]),
          [perk("replanter", "Replanter", "Ripe crops you harvest plant themselves again, using one of their seeds."),
           perk("bountiful", "Bountiful", "A 15% chance of an extra crop from a ripe harvest.")],
          ranks("harvest_moon", "Harvest Moon", [f"Sneak and harvest a ripe crop to start: for {s} seconds, harvesting a ripe crop "
                                                 f"also harvests the ripe crops around it ({a}). Then a {c} minute cooldown."
                                                 for s, a, c in ((10, "3x3", 4), (15, "5x5", 3), (20, "7x7", 2))], active=True),
          [perk("fertile_ground", "Fertile Ground", "Crops near you grow faster."),
           n("hardy", "Hardy", "+2 max health.", [attribute("minecraft:generic.max_health", 2, "addition")])],
          perk("golden_harvest", "Golden Harvest", "Ripe harvests now and then give a golden carrot or golden apple.")),
    "Bountiful's chance, up to +30%")

add("Gathering", "fishing", "Fishing", "minecraft:fishing_rod",
    [fish({"minecraft:salmon": 5, "minecraft:pufferfish": 8, "minecraft:tropical_fish": 15}, 2)],
    shape(n("patient", "Patient", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")]),
          [perk("quick_bite", "Quick Bite", "Fish bite sooner."),
           n("angler", "Angler", "+0.5 luck.", [attribute("minecraft:generic.luck", 0.5, "addition")])],
          ranks("treasure_hunter", "Treasure Hunter", ["More treasure from fishing.", "Even more treasure, and better books.",
                                                       "Treasure can include dungeon ore."]),
          [perk("double_catch", "Double Catch", "A 10% chance of catching two fish."),
           perk("shake", "Shake", "Hooking a mob with your rod pulls one of its drops off it.")],
          perk("master_angler", "Master Angler", "Rare dungeon keys and relic shards from fishing.")),
    "Luck of the sea, up to +3")

# --- Crafting ---
# The level still sets the best star you can forge; perks are bought here.
add("Crafting", "forging", "Forging", "dungeondelving:master_anvil", None,
    shape(n("apprentice_smith", "Apprentice Smith", "Your first step as a smith. Your level sets the best ★ you can forge."),
          [perk("thrifty_1", "Thrifty I", "Cracks give back 6 nuggets instead of 5, and salvage returns 55% of the metal instead of 40%."),
           perk("slow_cooling_1", "Slow Cooling I", "Hot metal stays workable 25% longer.")],
          ranks("steady_hand", "Steady Hand", ["+2% sweet spot on the anvil and grindstone."] * 3),
          [perk("thrifty_2", "Thrifty II", "Cracks give back 8 nuggets, and salvage returns 70% of the metal."),
           perk("slow_cooling_2", "Slow Cooling II", "Hot metal stays workable 50% longer.")],
          perk("masterwork", "Masterwork", "A forging with no misses has a 10% chance to come out one ★ higher, up to what the forge allows."),
          extra=[(perk("sure_strikes_1", "Sure Strikes I", "25% less chance a strike cracks the piece."), 0, 6, 30, "steady_hand_3", 5),
                 (perk("sure_strikes_2", "Sure Strikes II", "50% less chance a strike cracks the piece."), 0, 7, 40, "sure_strikes_1", 7)]),
    "Sweet spot, up to +5%")

add("Crafting", "leatherworking", "Leatherworking", "minecraft:leather", None,
    shape(n("apprentice_tanner", "Apprentice Tanner", "Your first step as a tanner."),
          [perk("water_saver_1", "Water Saver I", "Boiling leather has a 25% chance not to use water."),
           perk("careful_tanner_1", "Careful Tanner I", "25% less chance a miss tears the hide.")],
          ranks("steady_scraper", "Steady Scraper", ["+2% room on each patch at the tanning rack."] * 3),
          [perk("water_saver_2", "Water Saver II", "Boiling leather has a 50% chance not to use water."),
           perk("careful_tanner_2", "Careful Tanner II", "50% less chance a miss tears the hide.")],
          perk("supple_hides", "Supple Hides", "A 25% chance of a second refined leather.")),
    "Patch width, up to +5%")

add("Crafting", "weaving", "Weaving", "minecraft:string", None,
    shape(n("apprentice_weaver", "Apprentice Weaver", "Your first step as a weaver."),
          [perk("careful_weaver_1", "Careful Weaver I", "25% less chance a miss snaps the thread."),
           perk("quick_fingers", "Quick Fingers", "A perfect first stitch finishes the cloth in two stitches instead of three.")],
          ranks("steady_needle", "Steady Needle", ["+2% room for each stitch at the loom."] * 3),
          [perk("careful_weaver_2", "Careful Weaver II", "50% less chance a miss snaps the thread."),
           perk("padding", "Padding", "Padded cloth takes one less woven cloth.")],
          perk("fine_weave", "Fine Weave", "A 25% chance of a second cloth.")),
    "Loom room, up to +5%")

# --- Combat styles ---
MELEE = [attribute("minecraft:generic.attack_damage", 0.03)]
RANGED = ([attribute("ranged_weapon:damage", 0.03, "multiply_total")], ["ranged_weapon_api"])


def style(key, title, icon, root, choices, signature, refinements, capstone):
    """A combat style tree. root is (title, description, rewards, mods); the rest are (key, title, description)."""
    r_title, r_desc, r_rewards, r_mods = root
    add("Combat", key, title, icon, [],
        shape(n(f"{key}_training", r_title, r_desc, r_rewards, r_mods),
              [perk(*c) for c in choices],
              ranks(signature[0], signature[1], [f"{signature[2]} For {s} seconds, then a {c} minute cooldown." for s, c in ((10, 4), (15, 3), (20, 2))],
                    active=True),
              [perk(*c) for c in refinements], perk(*capstone)),
        f"{title} damage, up to +20%")


style("one_handed", "One-Handed", "minecraft:iron_sword", ("One-Handed Training", "+3% melee damage.", MELEE, []),
      [("riposte", "Riposte", "Striking right after a block deals +30% damage."), ("footwork", "Footwork", "+3% movement speed with a one-handed weapon.")],
      ("bladestorm", "Bladestorm", "Sneak and strike to start: your hits sweep everything around you."),
      [("bleed", "Bleed", "Hits have a 15% chance to make the target bleed."), ("disarm", "Disarm", "A 5% chance to knock a mob's weapon out of its hands.")],
      ("duelist", "Duelist", "+25% damage when no other enemy is within 8 blocks."))
style("two_handed", "Two-Handed", "minecraft:netherite_axe", ("Two-Handed Training", "+3% melee damage.", MELEE, []),
      [("cleave", "Cleave", "Wider sweeps."), ("crushing_blow", "Crushing Blow", "Hits ignore 10% of the target's armor.")],
      ("skull_splitter", "Skull Splitter", "Sneak and strike to start: every hit splashes onto nearby enemies."),
      [("stagger", "Stagger", "Hits slow the target briefly."), ("momentum", "Momentum", "Each hit in a row deals a little more, up to +15%.")],
      ("executioner", "Executioner", "+50% damage to enemies under 25% health."))
style("polearms", "Polearms", "minecraft:trident", ("Polearm Training", "+3% melee damage.", MELEE, []),
      [("reach", "Reach", "+0.5 attack reach with a polearm."), ("brace", "Brace", "+10% knockback resistance with a polearm.")],
      ("impale", "Impale", "Sneak and strike to start: your hits pierce through to the enemy behind."),
      [("sweep", "Sweep", "Hits push enemies back."), ("keep_away", "Keep Away", "+20% damage to enemies at the edge of your reach.")],
      ("phalanx", "Phalanx", "+2 armor while holding a polearm."))
style("daggers", "Daggers", "rogues:iron_dagger", ("Dagger Training", "+3% melee damage.", MELEE, []),
      [("backstab", "Backstab", "+30% damage from behind."), ("quick_hands", "Quick Hands", "+5% attack speed with a dagger.")],
      ("shadow_flurry", "Shadow Flurry", "Sneak and strike to start: much faster attacks."),
      [("poison_edge", "Poison Edge", "Hits have a 15% chance to poison."), ("vanish", "Vanish", "A kill turns you invisible for 3 seconds.")],
      ("assassinate", "Assassinate", "Your first hit on an enemy that hasn't seen you is a critical hit."))
style("unarmed", "Unarmed", "minecraft:leather", ("Unarmed Training", "+3% melee damage.", MELEE, []),
      [("iron_fist", "Iron Fist", "+1 unarmed damage."), ("deflect", "Deflect", "A 10% chance to deflect arrows.")],
      ("berserk", "Berserk", "Sneak and punch to start: +50% unarmed damage, and you break dirt and sand by hand instantly."),
      [("disarm_unarmed", "Wrestle", "A 10% chance to knock a mob's weapon away."), ("iron_grip", "Iron Grip", "Mobs can't knock your weapon away.")],
      ("martial_master", "Martial Master", "Unarmed hits ignore armor."))
style("archery", "Archery", "minecraft:bow", ("Archery Training", "+3% ranged damage.", *RANGED),
      [("steady_aim", "Steady Aim", "Less arrow spread."), ("retrieval", "Retrieval", "A 30% chance to get your arrow back from a kill.")],
      ("volley", "Volley", "Sneak and loose to start: every shot fires two extra arrows."),
      [("daze", "Daze", "Hits have a 10% chance to daze the target."), ("piercing", "Piercing", "Arrows pass through one enemy.")],
      ("deadeye", "Deadeye", "Fully drawn shots have a 20% chance to crit."))
style("crossbows", "Crossbows", "minecraft:crossbow", ("Crossbow Training", "+3% ranged damage.", *RANGED),
      [("quick_reload", "Quick Reload", "Faster loading."), ("bolt_pierce", "Bolt Pierce", "Bolts pass through one enemy.")],
      ("rapid_fire", "Rapid Fire", "Sneak and fire to start: crossbows load instantly."),
      [("explosive_bolts", "Explosive Bolts", "A 5% chance a bolt bursts on hit."), ("pin", "Pin", "Hits root the target briefly.")],
      ("siegebreaker", "Siegebreaker", "+50% damage to shielded or armored enemies."))
style("dual_wielding", "Dual Wielding", "texture:dungeondelving:textures/gui/skill_icons/dual_wielding.png",
      ("Ambidextrous", "+2% attack speed.", [attribute("minecraft:generic.attack_speed", 0.02)], []),
      [("flurry", "Flurry", "+3% attack speed while dual wielding."), ("off_hand_guard", "Off-Hand Guard", "+1 armor while dual wielding.")],
      ("twin_fury", "Twin Fury", "Sneak and strike to start: off-hand hits deal double."),
      [("dual_bleed", "Rend", "Off-hand hits have a 15% chance to make the target bleed."), ("parry", "Parry", "A 10% chance to parry a melee hit.")],
      ("whirlwind", "Whirlwind", "Every fifth hit strikes all enemies around you."))
style("blocking", "Blocking", "minecraft:shield",
      ("Braced", "+5% knockback resistance.", [attribute("minecraft:generic.knockback_resistance", 0.05, "addition")], []),
      [("bulwark", "Bulwark", "+1 armor while holding a shield."), ("quick_guard", "Quick Guard", "Your shield comes up faster.")],
      ("shield_wall", "Shield Wall", "Sneak and block to start: your shield stops all damage from the front, explosions too."),
      [("shield_bash", "Shield Bash", "Blocking a hit knocks the attacker back."), ("reflect", "Reflect", "Blocked arrows fly back at the shooter.")],
      ("unbreakable", "Unbreakable", "Your shield takes half the durability damage."))

# --- Armor ---


def armor(key, title, icon, root, choices, signature, refinements, capstone, mastery):
    add("Armor", key, title, icon, [], shape(root, choices, signature, refinements, capstone), mastery)


armor("light_armor", "Light Armor", "minecraft:leather_chestplate",
      n("light_step", "Light Step", "+2% movement speed.", [attribute("minecraft:generic.movement_speed", 0.02)]),
      [perk("evasive", "Evasive", "A 5% chance to dodge an attack in light armor."), perk("silent_step", "Silent Step", "Mobs notice you from less far away.")],
      ranks("agility", "Agility", ["+3% dodge chance in light armor."] * 3),
      [perk("light_footed", "Light Footed", "No fall damage from under 6 blocks."), perk("second_wind", "Second Wind", "Dropping under 30% health gives a burst of speed.")],
      perk("phantom", "Phantom", "Dodging an attack gives a burst of speed."), "Movement speed, up to +15%")
armor("medium_armor", "Medium Armor", "minecraft:chainmail_chestplate",
      n("fitted_mail", "Fitted Mail", "+1 armor.", [attribute("minecraft:generic.armor", 1, "addition")]),
      [n("balanced", "Balanced", "+1% movement speed.", [attribute("minecraft:generic.movement_speed", 0.01)]),
       perk("steady_footing", "Steady Footing", "+10% knockback resistance in medium armor.")],
      ranks("adaptable", "Adaptable", ["+1 armor and +1% movement speed."] * 3,
            rewards=[[attribute("minecraft:generic.armor", 1, "addition"), attribute("minecraft:generic.movement_speed", 0.01)]] * 3),
      [perk("rally", "Rally", "Killing an enemy heals you a little."), perk("brace_mail", "Mail Brace", "Arrows deal 15% less damage to you.")],
      perk("vanguard", "Vanguard", "+10% damage for a few seconds after taking a hit."), "Armor and speed, up to +3 and +5%")
armor("heavy_armor", "Heavy Armor", "minecraft:iron_chestplate",
      n("iron_skin", "Iron Skin", "+1 armor toughness.", [attribute("minecraft:generic.armor_toughness", 1, "addition")]),
      [n("immovable", "Immovable", "+5% knockback resistance.", [attribute("minecraft:generic.knockback_resistance", 0.05, "addition")]),
       perk("plated", "Plated", "+1 armor per heavy piece worn.")],
      ranks("juggernaut", "Juggernaut", ["3% less damage taken in heavy armor."] * 3),
      [perk("unshaken", "Unshaken", "Immune to slowness in heavy armor."), perk("heavy_bulwark", "Bulwark", "Blocking with a shield in heavy armor takes no shield durability.")],
      perk("fortress", "Fortress", "Heavy armor no longer slows you."), "Armor, up to +4")
armor("mage_armor", "Mage Armor", "minecraft:purple_wool",
      n("woven_wards", "Woven Wards", "+0.5 spell power.", [attribute("spell_power:generic", 0.5, "addition")], ["spell_power"]),
      [n("mana_weave", "Mana Weave", "+10 max mana for Iron's Spells.", [attribute("irons_spellbooks:max_mana", 10, "addition")], ["irons_spellbooks"]),
       perk("focus", "Focus", "Getting hit doesn't interrupt your casting.")],
      ranks("arcane_weave", "Arcane Weave", ["+0.5 spell power in mage armor."] * 3,
            rewards=[[attribute("spell_power:generic", 0.5, "addition")]] * 3, mods=["spell_power"]),
      [perk("mana_shield", "Mana Shield", "Some damage is taken from your mana instead of your health."),
       n("deep_reserves", "Deep Reserves", "+5% mana regeneration for Iron's Spells.", [attribute("irons_spellbooks:mana_regen", 0.05)], ["irons_spellbooks"])],
      perk("archmages_robe", "Archmage's Robe", "+10% spell power while wearing a full set of mage armor."), "Spell power, up to +2")

# --- Magic schools ---
# (key, title, icon, Spell Power school, Iron's Spells school or None, school effect, capstone)
SCHOOLS = [
    ("fire_magic", "Fire", "minecraft:blaze_powder", "fire", "fire", ("Kindling", "Fire spells burn longer."),
     ("Wildfire", "Burning enemies spread their flames to those around them.")),
    ("frost_magic", "Frost", "minecraft:blue_ice", "frost", "ice", ("Deep Chill", "Frost spells slow for longer."),
     ("Shatter", "Frozen enemies take double damage from your next hit.")),
    ("lightning_magic", "Lightning", "minecraft:lightning_rod", "lightning", "lightning", ("Arc", "Lightning spells jump to one more enemy."),
     ("Storm Caller", "Hits now and then call down lightning.")),
    ("arcane_magic", "Arcane", "minecraft:amethyst_shard", "arcane", "ender", ("Flux", "Arcane spells cost 10% less mana."),
     ("Overload", "Every tenth arcane spell is cast twice.")),
    ("holy_magic", "Holy", "minecraft:golden_apple", "healing", "holy", ("Blessing", "Your heals also cure one bad effect."),
     ("Aegis", "Healing beyond full health becomes a shield.")),
    ("earth_magic", "Earth", "minecraft:moss_block", "earth", "nature", ("Rooted", "Earth spells root enemies briefly."),
     ("Stoneskin", "Casting an earth spell gives you +4 armor for 5 seconds.")),
    ("water_magic", "Water", "minecraft:heart_of_the_sea", "water", None, ("Tidal", "Water spells push enemies back."),
     ("Riptide", "Water spells heal you a little for each enemy hit.")),
    ("air_magic", "Air", "minecraft:feather", "air", None, ("Tailwind", "Casting an air spell gives you a burst of speed."),
     ("Cyclone", "Air spells lift enemies off the ground.")),
    ("necromancy", "Necromancy", "minecraft:wither_skeleton_skull", "soul", "eldritch", ("Siphon", "Necromancy spells heal you for some of their damage."),
     ("Lich Form", "Below 20% health, your necromancy spells deal double damage.")),
]
for key, school, icon, spell_power, irons, effect, capstone in SCHOOLS:
    title = "Necromancy" if key == "necromancy" else f"{school} Magic"
    s = school.lower() if key != "necromancy" else "necromancy"
    STATS[key] = (stats(f"{s}_attunement", "Attunement", f"+0.1 {school.lower() if key != 'necromancy' else 'soul'} spell power.",
                        [attribute(f"spell_power:{spell_power}", 0.1, "addition")], ["spell_power"]),
                  stats(f"{s}_wellspring", "Wellspring", "+5 max mana for Iron's Spells.", [attribute("irons_spellbooks:max_mana", 5, "addition")],
                        ["irons_spellbooks"]))
    lore = (n(f"{s}_lore", f"{school} Lore" if key != "necromancy" else "Eldritch Lore", f"+3% {irons} spell power for Iron's Spells.",
              [attribute(f"irons_spellbooks:{irons}_spell_power", 0.03)], ["irons_spellbooks"]) if irons
            else perk(f"{s}_efficiency", "Efficient Casting", f"{school} spells cost 10% less mana."))
    add("Magic", key, title, icon, [],
        shape(n(f"{s}_initiate", f"{school} Initiate", f"+0.5 {s if key != 'necromancy' else 'soul'} spell power.",
                [attribute(f"spell_power:{spell_power}", 0.5, "addition")], ["spell_power"]),
              [perk(f"{s}_quick_casting", "Quick Casting", f"{school} spells cool down 10% faster."), lore],
              ranks(f"{s}_adept", f"{school} Adept", [f"+0.5 {s if key != 'necromancy' else 'soul'} spell power."] * 3,
                    rewards=[[attribute(f"spell_power:{spell_power}", 0.5, "addition")]] * 3, mods=["spell_power"]),
              [perk(f"{s}_effect", effect[0], effect[1]),
               n(f"{s}_reserves", "Deep Reserves", "+10 max mana for Iron's Spells.", [attribute("irons_spellbooks:max_mana", 10, "addition")], ["irons_spellbooks"])],
              perk(f"{s}_capstone", capstone[0], capstone[1])),
        f"{school} spell power, up to +25%")

add("Other", "beastmastery", "Beastmastery", "minecraft:lead", [],
    [(n("animal_friend", "Animal Friend", "Your first step as a beastmaster. Companions and their perks come in a later update."), 0, 0, 0, None, 0, ROOT_COST)],
    "Companion strength (with companions, DD-61)")


def icon_json(icon):
    """A Pufferfish icon: an item id, or "texture:<path>" for one of our textures (like the crossed swords)."""
    if icon.startswith("texture:"):
        return {"type": "texture", "data": {"texture": icon[len("texture:"):]}}
    return {"type": "item", "data": {"item": icon}}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def perk_key(node):
    """The Perk.java key a node's tag belongs to (its rank suffix dropped), or None if it has no tag."""
    for reward in node["rewards"]:
        if reward["type"] == "puffish_skills:tag":
            key = reward["data"]["tag"][len(f"{NS}.perk."):]
            return key.rsplit("_", 1)[0] if key[-2:] in ("_1", "_2", "_3") else key
    return None


lang = json.loads(LANG.read_text(encoding="utf-8"))
# Names of nodes these trees no longer have (written again below for the ones they do).
lang = {k: v for k, v in lang.items() if not (k.startswith(f"skill.{NS}.") and k.split(".")[2] in TREES)}
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
preview = {"groups": {}, "mastery": MASTERY, "capstone_spent": CAPSTONE_SPENT}
for key, (title, icon, sources, placed) in TREES.items():
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
    # No level_limit: skills level forever (DD-70).
    write(folder / "experience.json", {
        "experience_per_level": {"type": "expression", "data": {"expression": CRAFTING_CURVE if crafting else GATHERING_CURVE}},
        "sources": [] if crafting else sources,
    })
    definitions, skills, links, nodes = {}, {}, [], []
    for node, column, row, level, parent, spent, cost in placed:
        k = node["key"]
        frame = "challenge" if spent else "goal" if node["active"] else "task"
        definitions[k] = {
            "title": {"translate": f"skill.{NS}.{key}.{k}"},
            "description": {"translate": f"skill.{NS}.{key}.{k}.description"},
            "icon": icon_json(icon),
            "frame": {"type": "advancement", "data": {"frame": frame}},
            "rewards": node["rewards"],
            "cost": cost,
            # A point comes with each level from level 2 (Pufferfish's level 1), so level L needs L - 1 points.
            **({"required_points": level - 1} if level > 1 else {}),
            **({"required_spent_points": spent} if spent else {}),
            **({"required_mods": node["mods"]} if node["mods"] else {}),
        }
        lang[f"skill.{NS}.{key}.{k}"] = node["title"]
        lang[f"skill.{NS}.{key}.{k}.description"] = node["description"]
        skills[k] = {"definition": k, "x": column * DX, "y": row * DY, "root": parent is None}
        if parent:
            links.append([parent, k])
        tag_key = perk_key(node)
        nodes.append({"key": k, "title": node["title"], "description": node["description"], "column": column, "row": row, "level": level,
                      "parent": parent, "spent": spent, "cost": cost, "active": node["active"], "mods": node["mods"],
                      "kind": "attribute" if tag_key is None and node["rewards"] else "none" if not node["rewards"] else "perk",
                      "built": tag_key is None or tag_key in IMPLEMENTED})
    write(folder / "definitions.json", definitions)
    write(folder / "skills.json", skills)
    write(folder / "connections.json", {"normal": {"bidirectional": links}})
    preview["groups"].setdefault(GROUPS[key], []).append({"key": key, "title": title, "icon": icon, "nodes": nodes})
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
write(PREVIEW, preview)
print("Wrote skill trees for", ", ".join(TREES), "and classes", ", ".join(CLASSES))
