"""Writes the data and assets for the rebuilt crafting professions (DD-88): Leatherworking, Weaving and Woodworking.
Blockstates, models, loot tables, recipes, tags, English names and placeholder item textures. See
docs/design/professions.md.

Run from the project root: python tools/profession_resources.py
Block models are built from vanilla textures (and vanilla models, for the upgrades that are a better version of a
vanilla block). Item textures are simple original placeholders until real art exists. Uses tools/forging_resources.py's
helpers, and writes after it (run that one first when both change).
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from forging_resources import (ASSETS, DATA, HORIZONTAL, MOD, RES, add_to_tag, backing, block_loot, bottle, box, cloth, hide, item_model,  # noqa: E402
                               png, powder, shade, shaped, shapeless, speckle, starred_shapeless, write)

LANG = ASSETS / "lang/en_us.json"
FD = "farmersdelight"
WOOD = "minecraft:block/stripped_spruce_log"
PLANKS = "minecraft:block/spruce_planks"


def needs(mod, recipe):
    """A recipe that only loads with another mod."""
    return {**recipe, "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": mod}]}


def recipe(name, value):
    write(DATA / f"recipe/{name}.json", value)


def ingredient(v):
    return {"tag": v[1:]} if v.startswith("#") else {"item": v}


def starred_shaped_result(name, pattern, key, result, count=1):
    write(DATA / f"recipe/{name}.json", {
        "type": f"{MOD}:starred_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: ingredient(v) for k, v in key.items()},
        "result": {"id": result, "count": count},
    })


def starred_shapeless_result(name, ingredients, result, count=1):
    write(DATA / f"recipe/{name}.json", {
        "type": f"{MOD}:starred_shapeless", "category": "equipment",
        "ingredients": [ingredient(v) for v in ingredients],
        "result": {"id": result, "count": count},
    })


def cooking(name, ingredients, result, time):
    """A Farmer's Delight cooking-pot recipe (the result's container, a glass bottle, comes from its crafting remainder)."""
    recipe(name, needs(FD, {"type": "farmersdelight:cooking", "experience": 0.2, "cookingtime": time, "recipe_book_tab": "misc",
                            "ingredients": [ingredient(v) for v in ingredients], "result": {"id": result, "count": 1}}))


def facing_block(name, model):
    write(ASSETS / f"models/block/{name}.json", model)
    write(ASSETS / f"blockstates/{name}.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/{name}", "y": y} for facing, y in HORIZONTAL.items()}})
    item_model(name, f"{MOD}:block/{name}")
    block_loot(name)


def model(textures, elements):
    return {"parent": "minecraft:block/block", "textures": {**textures, "particle": next(iter(textures.values()))}, "elements": elements}


def drops_cauldron(name):
    write(DATA / f"loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:cauldron"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


CAULDRON_TEXTURES = {"bottom": "minecraft:block/cauldron_bottom", "inside": "minecraft:block/cauldron_inner", "particle": "minecraft:block/cauldron_side",
                     "side": "minecraft:block/cauldron_side", "top": "minecraft:block/cauldron_top"}


def vat(name, content):
    """A cauldron-shaped vat at liquid levels 0 to 3 (0: drained, with its batch still in)."""
    for level, template in ((1, "template_cauldron_level1"), (2, "template_cauldron_level2"), (3, "template_cauldron_full")):
        write(ASSETS / f"models/block/{name}_level{level}.json", {"parent": f"minecraft:block/{template}",
                                                                 "textures": {**CAULDRON_TEXTURES, "content": content}})
    write(ASSETS / f"blockstates/{name}.json", {"variants": {
        f"level={level},soaking={str(soaking).lower()}": {"model": "minecraft:block/cauldron" if level == 0 else f"{MOD}:block/{name}_level{level}"}
        for level in range(4) for soaking in (False, True)}})
    drops_cauldron(name)


def liquid(color):
    image = {}
    for y in range(16):
        for x in range(16):
            image[(x, y)] = shade(color, 12 if speckle(x, y, 3) > 7 else (-8 if speckle(x, y, 5) < 2 else 0))
    return image


def blocks():
    # Shaping Rack: a sloped board on four legs, where a piece is laid out to cut or sew.
    facing_block("shaping_rack", model({"wood": WOOD, "board": "minecraft:block/birch_planks"}, [
        box([1, 0, 1], [3, 12, 3], "wood"), box([13, 0, 1], [15, 12, 3], "wood"),
        box([1, 0, 13], [3, 12, 15], "wood"), box([13, 0, 13], [15, 12, 15], "wood"),
        box([0, 12, 0], [16, 14, 16], "board"),
    ]))
    # Tanning Vat: a cauldron of brown liquor. Soaking Cauldron: a cauldron of (biome-tinted) water.
    png(ASSETS / "textures/block/tanning_liquor.png", liquid((96, 58, 26)))
    vat("tanning_vat", f"{MOD}:block/tanning_liquor")
    vat("soaking_cauldron", "minecraft:block/water_still")
    # Tanning Barrel: an open barrel.
    write(ASSETS / "models/block/tanning_barrel.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "minecraft:block/barrel_top_open", "bottom": "minecraft:block/barrel_bottom", "side": "minecraft:block/barrel_side"}})
    write(ASSETS / "blockstates/tanning_barrel.json", {"variants": {"": {"model": f"{MOD}:block/tanning_barrel"}}})
    item_model("tanning_barrel", f"{MOD}:block/tanning_barrel")
    block_loot("tanning_barrel")
    # Fleshing Beam: a log beam on two legs, sloping.
    facing_block("fleshing_beam", model({"beam": "minecraft:block/stripped_oak_log", "legs": WOOD}, [
        box([2, 0, 6], [4, 8, 10], "legs"), box([12, 0, 6], [14, 8, 10], "legs"),
        box([0, 8, 5], [16, 12, 11], "beam"),
    ]))
    # Spinning Wheel: a base, two uprights and the wheel.
    facing_block("spinning_wheel", model({"wood": PLANKS, "post": WOOD, "wheel": "minecraft:block/grindstone_side"}, [
        box([1, 0, 4], [15, 3, 12], "wood"),
        box([3, 3, 7], [5, 13, 9], "post"), box([11, 3, 7], [13, 13, 9], "post"),
        box([7, 2, 2], [9, 16, 14], "wheel"),
    ]))
    # Great Loom and Bowyer's Bench: better versions of the loom and fletching table, drawn as them for now.
    for name, parent in (("great_loom", "minecraft:block/loom"), ("bowyers_bench", "minecraft:block/fletching_table")):
        facing_block(name, {"parent": parent})
    # Dye Works: three little vats on a bench.
    facing_block("dye_works", model({"bench": PLANKS, "vat": "minecraft:block/cauldron_side", "dye": "minecraft:block/blue_wool"}, [
        box([0, 0, 0], [16, 4, 16], "bench"),
        box([1, 4, 4], [5, 12, 12], "vat"), box([6, 4, 4], [10, 12, 12], "vat"), box([11, 4, 4], [15, 12, 12], "vat"),
        box([2, 11, 5], [4, 11.5, 11], "dye"), box([7, 11, 5], [9, 11.5, 11], "dye"), box([12, 11, 5], [14, 11.5, 11], "dye"),
    ]))
    # Chopping Block: a stump with an axe stuck in it.
    facing_block("chopping_block", model({"bark": "minecraft:block/oak_log", "top": "minecraft:block/oak_log_top",
                                          "handle": "minecraft:block/stripped_oak_log", "head": "minecraft:block/iron_block"}, [
        {**box([1, 0, 1], [15, 10, 15], "bark"), "faces": {**box([1, 0, 1], [15, 10, 15], "bark")["faces"], "up": {"texture": "#top"}}},
        box([7.5, 10, 4], [8.5, 15, 5], "handle"),
        box([7, 9, 5], [9, 12, 9], "head"),
    ]))
    # Wood Lathe: a bed, a headstock and tailstock, and the spindle between them. The treadle lathe adds a treadle.
    lathe = [box([0, 0, 4], [16, 8, 12], "bed"), box([0, 8, 5], [4, 14, 11], "iron"), box([12, 8, 6], [15, 12, 10], "iron"),
             box([4, 10, 7], [12, 12, 9], "spindle")]
    textures = {"bed": PLANKS, "iron": "minecraft:block/iron_block", "spindle": "minecraft:block/stripped_birch_log"}
    facing_block("wood_lathe", model(textures, lathe))
    facing_block("treadle_lathe", model({**textures, "treadle": WOOD}, lathe + [box([3, 0, 0], [13, 2, 4], "treadle")]))


# --- Items ---

def rod(color, length=12, thick=2):
    """A straight wooden piece, corner to corner."""
    image = {}
    for i in range(length):
        for t in range(thick):
            x, y = 2 + i, 13 - i - t
            if 0 <= x < 16 and 0 <= y < 16:
                image[(x, y)] = shade(color, -30 if t == thick - 1 else (15 if i % 3 == 0 else 0))
    return image


def billet(color):
    image = {}
    for y in range(5, 12):
        for x in range(3, 13):
            image[(x, y)] = shade(color, -35 if y in (5, 11) or x in (3, 12) else ((10 if (x + y) % 4 == 0 else 0)))
    for y in range(6, 11):
        image[(12, y)] = shade((205, 170, 120), -10 if y in (6, 10) else 0)  # the cut end
    return image


def limbs(color, bent=False):
    image = {}
    for i in range(12):
        x = 2 + i
        y = 12 - i if not bent else 12 - i + (2 if 3 < i < 8 else (1 if i in (3, 8) else 0))
        for t in range(2):
            if 0 <= y - t < 16:
                image[(x, y - t)] = shade(color, -30 if t else 10)
    return image


def thread(color):
    """A little skein."""
    image = {}
    for y in range(4, 13):
        for x in range(4, 12):
            if (x - 7.5) ** 2 / 16 + (y - 8) ** 2 / 20 <= 1:
                image[(x, y)] = shade(color, 15 if (x + y) % 3 == 0 else (-20 if y % 2 else 0))
    return image


def leaf(color):
    image = {}
    for y in range(3, 14):
        half = max(0, 4 - abs(y - 8))
        for x in range(8 - half, 8 + half + 1):
            image[(x, y)] = shade(color, -25 if x in (8 - half, 8 + half) else (20 if x == 8 else 0))
    return image


ITEMS = {
    "scraped_hide": hide((190, 150, 110)),
    "leather_panel": hide((150, 92, 52), stitched=True),
    "straps": rod((120, 72, 40), 12, 2),
    "bark_liquor": bottle((110, 70, 30)),
    "tannin_liquor": bottle((90, 50, 20)),
    "elder_liquor": bottle((70, 30, 50)),
    "barrowmoss": leaf((90, 120, 80)),
    "emberroot": leaf((200, 90, 40)),
    "yarn": thread((230, 224, 210)),
    "spider_silk": thread((240, 240, 245)),
    "silk_thread": thread((250, 250, 255)),
    "spellsilk": thread((190, 150, 240)),
    "arcane_dust": powder((180, 140, 230)),
    "cloth_panel": cloth((214, 204, 184), padded=True),
    "bowstring": rod((240, 236, 220), 12, 1),
    "staff_wrap": cloth((150, 120, 200)),
    "seasoned_billet": billet((140, 100, 60)),
    "stave": rod((170, 130, 80), 13, 2),
    "stock": billet((120, 85, 50)),
    "haft": rod((160, 120, 75), 10, 2),
    "staff_shaft": rod((150, 110, 70), 14, 2),
    "wand_core": rod((180, 140, 90), 8, 2),
    "bow_limbs": limbs((165, 125, 75)),
    "bent_limbs": limbs((145, 105, 60), bent=True),
    "linseed_oil": bottle((200, 170, 70)),
    "heartwood_resin": bottle((180, 90, 30)),
    "sylvan_sap": bottle((120, 200, 110)),
}


def items():
    for name, image in ITEMS.items():
        png(ASSETS / f"textures/item/{name}.png", image)
        item_model(name)
    # Boiled leather is gone (tanning replaced it).
    for path in (ASSETS / "textures/item/boiled_leather.png", ASSETS / "models/item/boiled_leather.json"):
        path.unlink(missing_ok=True)


# --- Recipes ---

LIQUOR = {"bark_liquor": [f"#{MOD}:tree_bark"] * 4, "tannin_liquor": [f"#{MOD}:tree_bark"] * 3 + [f"{MOD}:barrowmoss"],
          "elder_liquor": [f"#{MOD}:tree_bark"] * 3 + [f"{MOD}:emberroot"]}


def recipes():
    # Stations.
    shaped("shaping_rack", ["PIP", "S S", "S S"], {"P": "#minecraft:planks", "I": "minecraft:iron_ingot", "S": "minecraft:stick"})
    shaped("tanning_barrel", ["W W", " B "], {"W": f"{MOD}:wardsilver_ingot", "B": "minecraft:barrel"})
    shaped("fleshing_beam", ["LEL", "S S"], {"L": "minecraft:stripped_oak_log", "E": f"{MOD}:emberstone_ingot", "S": "minecraft:stick"})
    shaped("spinning_wheel", ["SSS", "SPS", "P P"], {"S": "minecraft:stick", "P": "#minecraft:planks"})
    shaped("great_loom", ["W W", " L "], {"W": f"{MOD}:wardsilver_ingot", "L": "minecraft:loom"})
    shaped("dye_works", ["CEC", "PPP"], {"C": "minecraft:cauldron", "E": f"{MOD}:emberstone_ingot", "P": "#minecraft:planks"})
    shaped("chopping_block", ["A", "L"], {"A": "minecraft:iron_axe", "L": "#minecraft:logs"})
    shaped("wood_lathe", ["ISI", "PPP", "P P"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick", "P": "#minecraft:planks"})
    shaped("treadle_lathe", ["W W", " L "], {"W": f"{MOD}:wardsilver_ingot", "L": f"{MOD}:wood_lathe"})
    shaped("bowyers_bench", ["E E", " B "], {"E": f"{MOD}:emberstone_ingot", "B": "minecraft:fletching_table"})

    # Leatherworking: liquor in a cooking pot; panels into straps, hardened leather and backings.
    for name, ingredients in LIQUOR.items():
        cooking(name, ingredients, f"{MOD}:{name}", 400)
    starred_shapeless("straps", [f"{MOD}:leather_panel", "minecraft:string"], count=4)
    starred_shapeless("hardened_leather", [f"{MOD}:leather_panel"] * 3 + ["minecraft:honeycomb"], count=3)
    starred_shapeless("leather_base", [f"{MOD}:leather_panel"] * 2)

    # Weaving: panels into backings, wraps; thread into bowstrings. (Spinning, weaving and fulling happen at stations.)
    starred_shapeless("gambeson_base", [f"{MOD}:cloth_panel"] * 2)
    starred_shapeless("staff_wrap", [f"{MOD}:cloth_panel", "minecraft:string"], count=2)
    starred_shapeless("bowstring", [f"#{MOD}:threads"] * 3)
    (DATA / "recipe/padded_cloth.json").unlink(missing_ok=True)  # Fulled now, not crafted.

    # Woodworking: seasoning in a smoker, linseed oil in a cooking pot, stocks from staves.
    recipe("seasoned_billet", {"type": "minecraft:smoking", "category": "misc", "ingredient": {"tag": "minecraft:logs"}, "cookingtime": 600,
                               "experience": 0.1, "result": {"id": f"{MOD}:seasoned_billet", "count": 1}})
    cooking("linseed_oil", ["minecraft:wheat_seeds"] * 4, f"{MOD}:linseed_oil", 300)
    shapeless("stock", [f"{MOD}:stave", f"{MOD}:stave"])

    # Bows and crossbows from parts (cross-crafting): the finish caps the ★, the limbs and string set it.
    finish = f"#{MOD}:wood_finishes"
    starred_shaped_result("bow_from_parts", [" LS", "F S", " LS"], {"L": f"{MOD}:bent_limbs", "S": f"{MOD}:bowstring", "F": finish}, "minecraft:bow")
    starred_shaped_result("crossbow_from_parts", ["LIL", "SKS", " F "], {"L": f"{MOD}:bent_limbs", "I": "minecraft:iron_ingot", "S": f"{MOD}:bowstring",
                                                                          "K": f"{MOD}:stock", "F": finish}, "minecraft:crossbow")


# --- Tags ---

DECOR = {
    "leatherworking": ["minecraft:barrel", "minecraft:hay_block", "#minecraft:logs", "minecraft:item_frame", "minecraft:brown_wool",
                       "minecraft:brown_carpet", "minecraft:composter", "minecraft:lantern"],
    "weaving": ["#minecraft:wool", "#minecraft:wool_carpets", "#minecraft:banners", "minecraft:loom", "minecraft:flower_pot",
                "#minecraft:flower_pots", "minecraft:lantern"],
    "woodworking": ["#minecraft:logs", "#minecraft:planks", "minecraft:scaffolding", "minecraft:bookshelf", "minecraft:chiseled_bookshelf",
                    "minecraft:lantern", "#minecraft:wooden_slabs"],
}


def tags():
    for profession, values in DECOR.items():
        write(DATA / f"tags/block/{profession}_decor.json", {"replace": False, "values": values})
    write(DATA / "tags/item/threads.json", {"replace": False, "values": ["minecraft:string", f"{MOD}:yarn", f"{MOD}:silk_thread", f"{MOD}:spellsilk"]})
    write(DATA / "tags/item/wood_finishes.json", {"replace": False, "values": [f"{MOD}:linseed_oil", f"{MOD}:heartwood_resin", f"{MOD}:sylvan_sap"]})
    write(DATA / "tags/item/tree_bark.json", {"replace": False, "values": [{"id": f"{FD}:tree_bark", "required": False}]})
    add_to_tag(RES / "data/minecraft/tags/block/mineable/axe.json", [f"{MOD}:{name}" for name in (
        "shaping_rack", "tanning_barrel", "fleshing_beam", "spinning_wheel", "great_loom", "dye_works", "chopping_block", "wood_lathe",
        "treadle_lathe", "bowyers_bench")])
    add_to_tag(RES / "data/minecraft/tags/block/mineable/pickaxe.json", [f"{MOD}:tanning_vat", f"{MOD}:soaking_cauldron"])


# --- Names ---

NAMES = {
    "block.dungeondelving.shaping_rack": "Shaping Rack",
    "block.dungeondelving.tanning_vat": "Tanning Vat",
    "block.dungeondelving.tanning_barrel": "Tanning Barrel",
    "block.dungeondelving.fleshing_beam": "Fleshing Beam",
    "block.dungeondelving.spinning_wheel": "Spinning Wheel",
    "block.dungeondelving.great_loom": "Great Loom",
    "block.dungeondelving.dye_works": "Dye Works",
    "block.dungeondelving.soaking_cauldron": "Soaking Cauldron",
    "block.dungeondelving.chopping_block": "Chopping Block",
    "block.dungeondelving.wood_lathe": "Wood Lathe",
    "block.dungeondelving.treadle_lathe": "Treadle Lathe",
    "block.dungeondelving.bowyers_bench": "Bowyer's Bench",
    "item.dungeondelving.refined_leather": "Tanned Leather",
    "item.dungeondelving.padded_cloth": "Fulled Cloth",
    "item.dungeondelving.scraped_hide": "Scraped Hide",
    "item.dungeondelving.leather_panel": "Leather Panel",
    "item.dungeondelving.straps": "Straps",
    "item.dungeondelving.bark_liquor": "Bark Liquor",
    "item.dungeondelving.tannin_liquor": "Tannin Liquor",
    "item.dungeondelving.elder_liquor": "Elder Liquor",
    "item.dungeondelving.barrowmoss": "Barrowmoss",
    "item.dungeondelving.emberroot": "Emberroot",
    "item.dungeondelving.yarn": "Yarn",
    "item.dungeondelving.spider_silk": "Spider Silk",
    "item.dungeondelving.silk_thread": "Silk Thread",
    "item.dungeondelving.spellsilk": "Spellsilk",
    "item.dungeondelving.arcane_dust": "Arcane Dust",
    "item.dungeondelving.cloth_panel": "Cloth Panel",
    "item.dungeondelving.bowstring": "Bowstring",
    "item.dungeondelving.staff_wrap": "Staff Wrap",
    "item.dungeondelving.seasoned_billet": "Seasoned Billet",
    "item.dungeondelving.stave": "Stave",
    "item.dungeondelving.stock": "Crossbow Stock",
    "item.dungeondelving.haft": "Haft",
    "item.dungeondelving.staff_shaft": "Staff Shaft",
    "item.dungeondelving.wand_core": "Wand Core",
    "item.dungeondelving.bow_limbs": "Bow Limbs",
    "item.dungeondelving.bent_limbs": "Bent Limbs",
    "item.dungeondelving.linseed_oil": "Linseed Oil",
    "item.dungeondelving.heartwood_resin": "Heartwood Resin",
    "item.dungeondelving.sylvan_sap": "Sylvan Sap",
    "skill.dungeondelving.woodworking": "Woodworking",
    "guild.dungeondelving.profession.leatherworking": "Leatherworking",
    "guild.dungeondelving.profession.weaving": "Weaving",
    "guild.dungeondelving.profession.woodworking": "Woodworking",
    "craft.dungeondelving.report.complete": "complete, workshop level %s (up to %s)",
    "craft.dungeondelving.station.tanning_rack": "a Tanning Rack",
    "craft.dungeondelving.station.vat": "a cauldron or Tanning Barrel (for the liquor)",
    "craft.dungeondelving.station.shaping_rack": "a Shaping Rack",
    "craft.dungeondelving.station.tanning_barrel": "Tanning Barrel",
    "craft.dungeondelving.station.fleshing_beam": "Fleshing Beam",
    "craft.dungeondelving.station.spinning_wheel": "a Spinning Wheel",
    "craft.dungeondelving.station.loom": "a loom",
    "craft.dungeondelving.station.cauldron": "a cauldron (for soaking, over heat)",
    "craft.dungeondelving.station.great_loom": "Great Loom",
    "craft.dungeondelving.station.dye_works": "Dye Works",
    "craft.dungeondelving.station.smoker": "a smoker (for seasoning)",
    "craft.dungeondelving.station.chopping_block": "a Chopping Block",
    "craft.dungeondelving.station.carving": "a fletching table",
    "craft.dungeondelving.station.lathe": "a Wood Lathe",
    "craft.dungeondelving.station.treadle_lathe": "Treadle Lathe",
    "craft.dungeondelving.station.bowyers_bench": "Bowyer's Bench",
    "message.dungeondelving.craft.no_workshop": "This isn't part of a complete %s workshop. Read a Guild Ledger nearby to see what it needs.",
    "message.dungeondelving.rack.needs_blade": "Cut it with shears or a knife.",
    "message.dungeondelving.rack.needs_string": "Sew it with string.",
    "message.dungeondelving.vat.other_liquor": "That's a different liquor: use up this one first.",
    "message.dungeondelving.vat.busy": "Something's already soaking. Take it out first.",
    "message.dungeondelving.vat.no_liquor": "The vat is out of liquor. Pour in more.",
    "message.dungeondelving.vat.no_water": "It's out of water.",
    "message.dungeondelving.vat.started": "Soaking %s. Ready in %s.",
    "message.dungeondelving.vat.waiting": "Still soaking: %s to go.",
    "message.dungeondelving.scrape.done": "Hide scraped clean %s. Tan it in a vat of liquor.",
    "message.dungeondelving.weave.done": "Woven %s.",
    "message.dungeondelving.cut.done": "Cut %s.",
    "message.dungeondelving.cut.ruined": "The cut went wrong. The leather is ruined.",
    "message.dungeondelving.sew.done": "Sewn %s.",
    "message.dungeondelving.spin.done": "Spun %s.",
    "message.dungeondelving.spin.snapped": "The yarn snapped. The fibre is wasted.",
    "message.dungeondelving.carve.done": "Carved %s. Bend them in hot water.",
    "message.dungeondelving.turn.done": "Turned %s.",
    "message.dungeondelving.wood.split": "The wood split.",
    "message.dungeondelving.split.needs_axe": "Hold an axe in your off hand to split it.",
    "gui.dungeondelving.timing.cut": "Hold to cut, let go on the mark",
    "gui.dungeondelving.timing.sew": "Stitch on the beat: R right-click, L left-click",
    "gui.dungeondelving.timing.spin": "Tap to keep the wheel in the green",
    "gui.dungeondelving.timing.carve": "Tap to keep the knife on the grain",
    "gui.dungeondelving.timing.turn": "Hold to cut deeper, let go in the green",
    "tooltip.dungeondelving.liquor": "Tans leather up to %s. Pour into a cauldron.",
    "tooltip.dungeondelving.finish": "Finishes wooden gear up to %s.",
}


def names():
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    for key in ("item.dungeondelving.boiled_leather", "message.dungeondelving.boil.done"):
        lang.pop(key, None)
    lang.update(NAMES)
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    blocks()
    items()
    recipes()
    tags()
    names()
    print("profession resources written")
