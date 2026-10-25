"""Writes the data and assets for the rebuilt crafting professions (DD-88): Leatherworking, Weaving and Woodworking.
Blockstates, models, loot tables, recipes, tags, English names, placeholder item textures and their Smith's Handbook
pages. See docs/design/professions.md.

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


def plain_block(name, model_):
    write(ASSETS / f"models/block/{name}.json", model_)
    write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"{MOD}:block/{name}"}}})
    item_model(name, f"{MOD}:block/{name}")
    block_loot(name)


RETIRED = ["blockstates/soaking_cauldron.json", "blockstates/chopping_block.json", "models/block/chopping_block.json",
           "models/item/chopping_block.json", "models/block/soaking_cauldron_level1.json", "models/block/soaking_cauldron_level2.json",
           "models/block/soaking_cauldron_level3.json"] + [f"{kind}/item/{name}{ext}" for name in ("silk_thread", "spellsilk", "bent_limbs")
                                                         for kind, ext in (("textures", ".png"), ("models", ".json"))]


def gold_blocks():
    """Goldsmithing's stations (DD-89)."""
    # Crucibles: a clay pot on a brick hearth, bigger for the Great Crucible.
    for name, (low, high) in (("crucible", (3, 13)), ("great_crucible", (1, 15))):
        plain_block(name, model({"pot": "minecraft:block/terracotta", "base": "minecraft:block/bricks", "gold": "minecraft:block/gold_block"}, [
            box([0, 0, 0], [16, 4, 16], "base"),
            box([low, 4, low], [high, 13, high], "pot"),
            box([low + 1, 12, low + 1], [high - 1, 12.5, high - 1], "gold"),
        ]))
    # Jeweler's Bench: a small table with a pin (the bench peg) and a gold block's glint.
    facing_block("jewelers_bench", model({"top": "minecraft:block/smithing_table_top", "wood": PLANKS, "gold": "minecraft:block/gold_block"}, [
        box([1, 0, 1], [3, 12, 3], "wood"), box([13, 0, 1], [15, 12, 3], "wood"),
        box([1, 0, 13], [3, 12, 15], "wood"), box([13, 0, 13], [15, 12, 15], "wood"),
        box([0, 12, 0], [16, 14, 16], "top"),
        box([6, 14, 6], [8, 15, 8], "gold"),
    ]))
    # Jeweler's Loupe: a glass lens on a little stand.
    facing_block("jewelers_loupe", model({"stand": "minecraft:block/gold_block", "lens": "minecraft:block/glass"}, [
        box([6, 0, 6], [10, 1, 10], "stand"), box([7.5, 1, 7.5], [8.5, 9, 8.5], "stand"),
        box([5, 8, 7], [11, 12, 9], "lens"),
    ]))


def blocks():
    gold_blocks()
    for path in RETIRED:
        (ASSETS / path).unlink(missing_ok=True)
    for path in ("loot_table/blocks/soaking_cauldron.json", "loot_table/blocks/chopping_block.json", "recipe/chopping_block.json",
                 "tags/item/threads.json", "tags/item/wood_finishes.json"):
        (DATA / path).unlink(missing_ok=True)
    # Shaping Rack: a sloped board on four legs, where a piece is laid out to cut or sew.
    facing_block("shaping_rack", model({"wood": WOOD, "board": "minecraft:block/birch_planks"}, [
        box([1, 0, 1], [3, 12, 3], "wood"), box([13, 0, 1], [15, 12, 3], "wood"),
        box([1, 0, 13], [3, 12, 15], "wood"), box([13, 0, 13], [15, 12, 15], "wood"),
        box([0, 12, 0], [16, 14, 16], "board"),
    ]))
    # The cauldron vats: brown liquor, pale sizing, golden finish.
    png(ASSETS / "textures/block/tanning_liquor.png", liquid((96, 58, 26)))
    vat("tanning_vat", f"{MOD}:block/tanning_liquor")
    png(ASSETS / "textures/block/sizing.png", liquid((214, 208, 182)))
    vat("sizing_vat", f"{MOD}:block/sizing")
    png(ASSETS / "textures/block/wood_finish.png", liquid((196, 150, 60)))
    vat("finishing_vat", f"{MOD}:block/wood_finish")
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
    # Dye Works: three little vats on a bench. A vat, so it doesn't turn to face the player.
    plain_block("dye_works", model({"bench": PLANKS, "vat": "minecraft:block/cauldron_side", "dye": "minecraft:block/blue_wool"}, [
        box([0, 0, 0], [16, 4, 16], "bench"),
        box([1, 4, 4], [5, 12, 12], "vat"), box([6, 4, 4], [10, 12, 12], "vat"), box([11, 4, 4], [15, 12, 12], "vat"),
        box([2, 11, 5], [4, 11.5, 11], "dye"), box([7, 11, 5], [9, 11.5, 11], "dye"), box([12, 11, 5], [14, 11.5, 11], "dye"),
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


def ring(color):
    image = {}
    for y in range(4, 13):
        for x in range(4, 13):
            d = (x - 8) ** 2 + (y - 8) ** 2
            if 9 <= d <= 18:
                image[(x, y)] = shade(color, 25 if x + y < 15 else -20)
    return image


def amulet(color):
    image = {}
    for i in range(9):
        image[(3 + i, 2 + abs(4 - i))] = shade((200, 200, 200), -10)  # the chain
    for y in range(8, 14):
        for x in range(5, 12):
            if abs(x - 8) + abs(y - 10) <= 3:
                image[(x, y)] = shade(color, 25 if y < 10 else -15)
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
    "sized_yarn": thread((236, 226, 190)),
    "wheat_sizing": bottle((220, 210, 170)),
    "silk_sizing": bottle((240, 240, 250)),
    "arcane_sizing": bottle((180, 140, 230)),
    "spider_silk": thread((240, 240, 245)),
    "arcane_dust": powder((180, 140, 230)),
    "cloth_panel": cloth((214, 204, 184), padded=True),
    "bowstring": rod((240, 236, 220), 12, 1),
    "staff_wrap": cloth((150, 120, 200)),
    "seasoned_billet": billet((140, 100, 60)),
    "cured_billet": billet((176, 128, 60)),
    "stave": rod((170, 130, 80), 13, 2),
    "stock": billet((120, 85, 50)),
    "haft": rod((160, 120, 75), 10, 2),
    "staff_shaft": rod((150, 110, 70), 14, 2),
    "wand_core": rod((180, 140, 90), 8, 2),
    "bow_limbs": limbs((165, 125, 75), bent=True),
    "linseed_oil": bottle((200, 170, 70)),
    "bone_ash": powder((226, 222, 210)),
    "assay_salt": powder((236, 224, 160)),
    "sunsalt": powder((250, 170, 60)),
    "fine_gold": billet((240, 196, 60)),
    "gold_fittings": thread((246, 206, 70)),
    "gold_ring": ring((248, 204, 64)),
    "gold_amulet": amulet((248, 204, 64)),
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

# The vat liquids, brewed in a cooking pot: tanning liquor, sizing and wood finish. Each sets its pieces' ★.
LIQUOR = {"bark_liquor": [f"#{MOD}:tree_bark"] * 4, "tannin_liquor": [f"#{MOD}:tree_bark"] * 3 + [f"{MOD}:barrowmoss"],
          "elder_liquor": [f"#{MOD}:tree_bark"] * 3 + [f"{MOD}:emberroot"],
          "wheat_sizing": ["minecraft:wheat"] * 4, "silk_sizing": ["minecraft:wheat"] * 3 + [f"{MOD}:spider_silk"],
          "arcane_sizing": ["minecraft:wheat"] * 3 + [f"{MOD}:arcane_dust"], "linseed_oil": ["minecraft:wheat_seeds"] * 4}
GILDED_SHAPES = {"helmet": ["FFF", "F F"], "chestplate": ["F F", "FFF", "FFF"], "leggings": ["FFF", "F F", "F F"], "boots": ["F F", "F F"]}


def recipes():
    # Stations.
    shaped("shaping_rack", ["PIP", "S S", "S S"], {"P": "#minecraft:planks", "I": "minecraft:iron_ingot", "S": "minecraft:stick"})
    shaped("tanning_barrel", ["W W", " B "], {"W": f"{MOD}:wardsilver_ingot", "B": "minecraft:barrel"})
    shaped("fleshing_beam", ["LEL", "S S"], {"L": "minecraft:stripped_oak_log", "E": f"{MOD}:emberstone_ingot", "S": "minecraft:stick"})
    shaped("spinning_wheel", ["SSS", "SPS", "P P"], {"S": "minecraft:stick", "P": "#minecraft:planks"})
    shaped("great_loom", ["W W", " L "], {"W": f"{MOD}:wardsilver_ingot", "L": "minecraft:loom"})
    shaped("dye_works", ["CEC", "PPP"], {"C": "minecraft:cauldron", "E": f"{MOD}:emberstone_ingot", "P": "#minecraft:planks"})
    shaped("wood_lathe", ["ISI", "PPP", "P P"], {"I": "minecraft:iron_ingot", "S": "minecraft:stick", "P": "#minecraft:planks"})
    shaped("treadle_lathe", ["W W", " L "], {"W": f"{MOD}:wardsilver_ingot", "L": f"{MOD}:wood_lathe"})
    shaped("bowyers_bench", ["E E", " B "], {"E": f"{MOD}:emberstone_ingot", "B": "minecraft:fletching_table"})

    for name, ingredients in LIQUOR.items():
        cooking(name, ingredients, f"{MOD}:{name}", 400)

    # Leatherworking: panels into straps, hardened leather and backings.
    starred_shapeless("straps", [f"{MOD}:leather_panel", "minecraft:string"], count=4)
    starred_shapeless("hardened_leather", [f"{MOD}:leather_panel"] * 3 + ["minecraft:honeycomb"], count=3)
    starred_shapeless("leather_base", [f"{MOD}:leather_panel"] * 2)

    # Weaving: panels into backings and wraps; sized yarn into bowstrings. (Spinning and weaving happen at stations.)
    starred_shapeless("gambeson_base", [f"{MOD}:cloth_panel"] * 2)
    starred_shapeless("staff_wrap", [f"{MOD}:cloth_panel", "minecraft:string"], count=2)
    starred_shapeless("bowstring", [f"{MOD}:sized_yarn"] * 3)
    (DATA / "recipe/padded_cloth.json").unlink(missing_ok=True)

    # Woodworking: seasoning in a smoker; staves (turned on a lathe) into parts, which keep the staves' ★.
    recipe("seasoned_billet", {"type": "minecraft:smoking", "category": "misc", "ingredient": {"tag": "minecraft:logs"}, "cookingtime": 600,
                               "experience": 0.1, "result": {"id": f"{MOD}:seasoned_billet", "count": 1}})
    stave = f"{MOD}:stave"
    starred_shaped_result("bow_limbs", [" S", "S "], {"S": stave}, f"{MOD}:bow_limbs", 2)
    starred_shaped_result("staff_shaft", ["S", "S"], {"S": stave}, f"{MOD}:staff_shaft")
    starred_shaped_result("haft", ["S"], {"S": stave}, f"{MOD}:haft", 2)
    starred_shaped_result("wand_core", ["H"], {"H": f"{MOD}:haft"}, f"{MOD}:wand_core")
    starred_shaped_result("stock", ["SS"], {"S": stave}, f"{MOD}:stock")

    # Goldsmithing (DD-89): refining salts, stations, and gilded gear and jewelry from gold fittings.
    shapeless("bone_ash", ["minecraft:bone_meal"] * 3 + ["minecraft:charcoal"], count=2)
    shapeless("assay_salt", [f"{MOD}:borax", f"{MOD}:bone_ash", f"{MOD}:bone_ash"], count=2)
    shapeless("sunsalt", [f"{MOD}:assay_salt", f"{MOD}:assay_salt", f"{MOD}:arcane_dust"], count=2)
    shaped("crucible", ["T T", "TGT", "BBB"], {"T": "minecraft:terracotta", "G": "minecraft:gold_ingot", "B": "minecraft:bricks"})
    shaped("great_crucible", ["W W", " C "], {"W": f"{MOD}:wardsilver_ingot", "C": f"{MOD}:crucible"})
    shaped("jewelers_bench", ["NGN", "PTP"], {"N": "minecraft:gold_nugget", "G": "minecraft:glass_pane", "P": "#minecraft:planks",
                                              "T": "minecraft:crafting_table"})
    shaped("jewelers_loupe", ["G", "E", "N"], {"G": "minecraft:glass", "E": f"{MOD}:emberstone_ingot", "N": "minecraft:gold_ingot"})
    fittings = f"{MOD}:gold_fittings"
    for slot, pattern in GILDED_SHAPES.items():
        starred_shaped_result(f"gilded_{slot}", pattern, {"F": fittings}, f"{MOD}:gilded_{slot}")
    starred_shaped_result("gilded_sword", ["F", "F", "S"], {"F": fittings, "S": "minecraft:stick"}, f"{MOD}:gilded_sword")
    starred_shaped_result("gold_ring", [" F ", "F F", " F "], {"F": fittings}, f"{MOD}:gold_ring")
    starred_shaped_result("gold_amulet", ["S S", "F F", " F "], {"F": fittings, "S": "minecraft:string"}, f"{MOD}:gold_amulet")

    # Bows and crossbows from parts (cross-crafting): the weakest part sets the ★.
    starred_shaped_result("bow_from_parts", [" L ", "H S", " L "], {"L": f"{MOD}:bow_limbs", "H": f"{MOD}:haft", "S": f"{MOD}:bowstring"},
                          "minecraft:bow")
    starred_shaped_result("crossbow_from_parts", ["LIL", " K ", " S "], {"L": f"{MOD}:bow_limbs", "I": "minecraft:iron_ingot", "K": f"{MOD}:stock",
                                                                          "S": f"{MOD}:bowstring"}, "minecraft:crossbow")


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
    # Jewelry in Curios' ring and necklace slots, which every player gets (Curios loads these only when it's installed).
    write(RES / "data/curios/tags/item/ring.json", {"replace": False, "values": [f"{MOD}:gold_ring"]})
    write(RES / "data/curios/tags/item/necklace.json", {"replace": False, "values": [f"{MOD}:gold_amulet"]})
    write(DATA / "curios/entities/goldsmithing.json", {"entities": ["player"], "slots": ["ring", "necklace"]})
    write(DATA / "tags/item/tree_bark.json", {"replace": False, "values": [{"id": f"{FD}:tree_bark", "required": False}]})
    for kind, names in (("axe", ("shaping_rack", "tanning_barrel", "fleshing_beam", "spinning_wheel", "great_loom", "dye_works", "wood_lathe",
                                 "treadle_lathe", "bowyers_bench", "jewelers_bench")),
                        ("pickaxe", ("tanning_vat", "sizing_vat", "finishing_vat", "crucible", "great_crucible", "jewelers_loupe"))):
        path = RES / f"data/minecraft/tags/block/mineable/{kind}.json"
        tag = json.loads(path.read_text(encoding="utf-8"))
        tag["values"] = [v for v in tag["values"] if v not in (f"{MOD}:chopping_block", f"{MOD}:soaking_cauldron")]
        write(path, tag)
        add_to_tag(path, [f"{MOD}:{name}" for name in names])


# --- Names ---

NAMES = {
    "block.dungeondelving.shaping_rack": "Shaping Rack",
    "block.dungeondelving.tanning_vat": "Tanning Vat",
    "block.dungeondelving.tanning_barrel": "Tanning Barrel",
    "block.dungeondelving.fleshing_beam": "Fleshing Beam",
    "block.dungeondelving.spinning_wheel": "Spinning Wheel",
    "block.dungeondelving.great_loom": "Great Loom",
    "block.dungeondelving.sizing_vat": "Sizing Vat",
    "block.dungeondelving.dye_works": "Dye Works",
    "block.dungeondelving.finishing_vat": "Finishing Vat",
    "block.dungeondelving.wood_lathe": "Wood Lathe",
    "block.dungeondelving.treadle_lathe": "Treadle Lathe",
    "block.dungeondelving.bowyers_bench": "Bowyer's Bench",
    "item.dungeondelving.refined_leather": "Tanned Leather",
    "item.dungeondelving.scraped_hide": "Scraped Hide",
    "item.dungeondelving.leather_panel": "Leather Panel",
    "item.dungeondelving.straps": "Straps",
    "item.dungeondelving.bark_liquor": "Bark Liquor",
    "item.dungeondelving.tannin_liquor": "Tannin Liquor",
    "item.dungeondelving.elder_liquor": "Elder Liquor",
    "item.dungeondelving.barrowmoss": "Barrowmoss",
    "item.dungeondelving.emberroot": "Emberroot",
    "item.dungeondelving.yarn": "Yarn",
    "item.dungeondelving.sized_yarn": "Sized Yarn",
    "item.dungeondelving.wheat_sizing": "Wheat Sizing",
    "item.dungeondelving.silk_sizing": "Silk Sizing",
    "item.dungeondelving.arcane_sizing": "Arcane Sizing",
    "item.dungeondelving.spider_silk": "Spider Silk",
    "item.dungeondelving.arcane_dust": "Arcane Dust",
    "item.dungeondelving.cloth_panel": "Cloth Panel",
    "item.dungeondelving.bowstring": "Bowstring",
    "item.dungeondelving.staff_wrap": "Staff Wrap",
    "item.dungeondelving.seasoned_billet": "Seasoned Billet",
    "item.dungeondelving.cured_billet": "Cured Billet",
    "item.dungeondelving.stave": "Stave",
    "item.dungeondelving.stock": "Crossbow Stock",
    "item.dungeondelving.haft": "Haft",
    "item.dungeondelving.staff_shaft": "Staff Shaft",
    "item.dungeondelving.wand_core": "Wand Core",
    "item.dungeondelving.bow_limbs": "Bow Limbs",
    "item.dungeondelving.linseed_oil": "Linseed Oil",
    "item.dungeondelving.heartwood_resin": "Heartwood Resin",
    "item.dungeondelving.sylvan_sap": "Sylvan Sap",
    "skill.dungeondelving.woodworking": "Woodworking",
    "skill.dungeondelving.weaponsmithing": "Weaponsmithing",
    "block.dungeondelving.crucible": "Crucible",
    "block.dungeondelving.great_crucible": "Great Crucible",
    "block.dungeondelving.jewelers_bench": "Jeweler's Bench",
    "block.dungeondelving.jewelers_loupe": "Jeweler's Loupe",
    "item.dungeondelving.bone_ash": "Bone Ash",
    "item.dungeondelving.assay_salt": "Assay Salt",
    "item.dungeondelving.sunsalt": "Sunsalt",
    "item.dungeondelving.fine_gold": "Fine Gold",
    "item.dungeondelving.gold_fittings": "Gold Fittings",
    "item.dungeondelving.gold_ring": "Gold Ring",
    "item.dungeondelving.gold_amulet": "Gold Amulet",
    "item.dungeondelving.gilded_helmet": "Gilded Helm",
    "item.dungeondelving.gilded_chestplate": "Gilded Cuirass",
    "item.dungeondelving.gilded_leggings": "Gilded Greaves",
    "item.dungeondelving.gilded_boots": "Gilded Boots",
    "item.dungeondelving.gilded_sword": "Gilded Sword",
    "guild.dungeondelving.profession.goldsmithing": "Goldsmithing",
    "craft.dungeondelving.station.furnace": "a furnace (for smelting gold)",
    "craft.dungeondelving.station.crucible": "a Crucible",
    "craft.dungeondelving.station.jewelers_bench": "a Jeweler's Bench",
    "craft.dungeondelving.station.great_crucible": "Great Crucible",
    "craft.dungeondelving.station.jewelers_loupe": "Jeweler's Loupe",
    "message.dungeondelving.chase.done": "Chased into gold fittings %s.",
    "message.dungeondelving.chase.ruined": "The design went wrong. The gold melts back into nuggets.",
    "gui.dungeondelving.timing.chase": "Click to chase the design when the marker's in the green",
    "tooltip.dungeondelving.liquor.goldsmithing": "Refines gold to %s in %s. Put it in a crucible.",
    "tooltip.dungeondelving.jewelry.ring": "+%s max health, worn in a ring slot",
    "tooltip.dungeondelving.jewelry.amulet": "+%s armor, worn in a necklace slot",
    "skill.dungeondelving.armorsmithing": "Armorsmithing",
    "skill.dungeondelving.goldsmithing": "Goldsmithing",
    "item.dungeondelving.blade": "Blade",
    "item.dungeondelving.axe_head": "Axe Head",
    "guild.dungeondelving.profession.leatherworking": "Leatherworking",
    "guild.dungeondelving.profession.weaving": "Weaving",
    "guild.dungeondelving.profession.woodworking": "Woodworking",
    "craft.dungeondelving.report.complete": "complete, workshop level %s (up to %s)",
    "craft.dungeondelving.station.tanning_rack": "a Tanning Rack",
    "craft.dungeondelving.station.vat": "a cauldron (for the vat)",
    "craft.dungeondelving.station.shaping_rack": "a Shaping Rack",
    "craft.dungeondelving.station.tanning_barrel": "Tanning Barrel",
    "craft.dungeondelving.station.fleshing_beam": "Fleshing Beam",
    "craft.dungeondelving.station.spinning_wheel": "a Spinning Wheel",
    "craft.dungeondelving.station.loom": "a loom",
    "craft.dungeondelving.station.great_loom": "Great Loom",
    "craft.dungeondelving.station.dye_works": "Dye Works",
    "craft.dungeondelving.station.smoker": "a smoker (for seasoning)",
    "craft.dungeondelving.station.lathe": "a Wood Lathe",
    "craft.dungeondelving.station.treadle_lathe": "Treadle Lathe",
    "craft.dungeondelving.station.bowyers_bench": "Bowyer's Bench",
    "message.dungeondelving.craft.no_workshop": "This isn't part of a complete %s workshop. Read a Guild Ledger nearby to see what it needs.",
    "message.dungeondelving.craft.workshop_too_low": "This workshop can only work pieces up to %s. Upgrade it first.",
    "message.dungeondelving.craft.skill_too_low": "You need %s level %s to work %s pieces.",
    "message.dungeondelving.rack.needs_blade": "Cut it with shears or a knife.",
    "message.dungeondelving.vat.other_liquor": "That's a different liquid: use up this one first.",
    "message.dungeondelving.vat.busy": "Something's already soaking. Take it out first.",
    "message.dungeondelving.vat.no_liquor": "The vat is empty. Pour in more.",
    "message.dungeondelving.vat.started": "Soaking %s. Ready in %s.",
    "message.dungeondelving.vat.waiting": "Still soaking: %s to go.",
    "message.dungeondelving.scrape.done": "Hide scraped clean. Tan it in a vat of liquor.",
    "message.dungeondelving.spin.done": "Spun into yarn. Size it in a vat before weaving.",
    "message.dungeondelving.weave.done": "Woven into cloth panels %s.",
    "message.dungeondelving.cut.done": "Cut into leather panels %s.",
    "message.dungeondelving.cut.ruined": "The cut went wrong. The leather is ruined.",
    "message.dungeondelving.turn.done": "Turned into staves %s.",
    "message.dungeondelving.wood.split": "The wood split.",
    "message.dungeondelving.weave.not_enough": "You need %s sized yarn to weave.",
    "gui.dungeondelving.timing.cut": "Hold to cut, let go on the mark",
    "gui.dungeondelving.timing.turn": "Hold to cut deeper, let go in the green",
    "tooltip.dungeondelving.liquor.leatherworking": "Tans hides to %s in %s. Pour into a cauldron.",
    "tooltip.dungeondelving.liquor.weaving": "Sizes yarn to %s in %s. Pour into a cauldron.",
    "tooltip.dungeondelving.liquor.woodworking": "Cures billets to %s in %s. Pour into a cauldron.",
}

# Keys from the old steps, gone now.
RETIRED_NAMES = [
    "skill.dungeondelving.forging", "skill_category.dungeondelving.forging",
    "item.dungeondelving.boiled_leather", "message.dungeondelving.boil.done", "block.dungeondelving.soaking_cauldron", "block.dungeondelving.chopping_block",
    "item.dungeondelving.padded_cloth", "item.dungeondelving.woven_cloth", "item.dungeondelving.silk_thread", "item.dungeondelving.spellsilk",
    "item.dungeondelving.bent_limbs", "craft.dungeondelving.station.cauldron", "craft.dungeondelving.station.chopping_block",
    "craft.dungeondelving.station.carving", "message.dungeondelving.rack.needs_string", "message.dungeondelving.vat.no_water",
    "message.dungeondelving.sew.done", "message.dungeondelving.spin.snapped", "message.dungeondelving.carve.done",
    "message.dungeondelving.split.needs_axe", "message.dungeondelving.scrape.torn", "gui.dungeondelving.timing.sew",
    "gui.dungeondelving.timing.spin", "gui.dungeondelving.timing.carve", "gui.dungeondelving.timing.scrape", "tooltip.dungeondelving.liquor",
    "tooltip.dungeondelving.finish", "message.dungeondelving.boil.no_heat",
]


def names():
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    for key in RETIRED_NAMES:
        lang.pop(key, None)
    lang.update(NAMES)
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


# --- The Smith's Handbook: the crafts' pages (tools/smiths_handbook.py writes Forging's) ---

BOOK = ASSETS / "patchouli_books/smiths_handbook/en_us"


def page(text, title=None):
    value = {"type": "patchouli:text", "text": text}
    if title:
        value["title"] = title
    return value


def crafting(recipe, recipe2=None, text=None):
    value = {"type": "patchouli:crafting", "recipe": f"{MOD}:{recipe}"}
    if recipe2:
        value["recipe2"] = f"{MOD}:{recipe2}"
    if text:
        value["text"] = text
    return value


def entry(category, name, title, icon, sortnum, pages):
    write(BOOK / f"entries/{category}/{name}.json", {"name": title, "icon": icon, "category": f"{MOD}:{category}", "sortnum": sortnum, "pages": pages})


VAT = ("Each vat takes four pieces a batch and uses a level of liquid. Better liquids take longer: 2 minutes at ★3, 3 at ★5, 4 at "
       "★7. Right-click with an empty hand to take the batch out, or to see how long it has left.")


def handbook():
    for old in ("weaving/sewing.json", "woodworking/shaping.json", "woodworking/turning.json", "leatherworking/scraping.json",
                "leatherworking/cutting.json", "weaving/spinning.json"):
        (BOOK / "entries" / old).unlink(missing_ok=True)
    for category, (title, description, icon, sortnum) in {
            "workshops": ("Guild Workshops", "The Guild Ledger, and the pattern every craft follows.", f"{MOD}:guild_ledger", 3),
            "leatherworking": ("Leatherworking", "Scraping, tanning and cutting leather.", f"{MOD}:leather_panel", 4),
            "weaving": ("Weaving", "Spinning, sizing and weaving cloth.", f"{MOD}:cloth_panel", 5),
            "woodworking": ("Woodworking", "Seasoning, curing and turning wood.", f"{MOD}:stave", 6)}.items():
        write(BOOK / f"categories/{category}.json", {"name": title, "description": description, "icon": icon, "sortnum": sortnum})

    entry("workshops", "workshops", "A Guild Workshop", f"{MOD}:guild_ledger", 0, [
        page("A $(item)Guild Ledger$() marks a workshop for every craft: the forge, the tannery, the weaving room and the woodshop. "
             "Each craft's stations must be within 7 blocks of it across and 4 up or down. Read the ledger to see what each craft has "
             "and needs.", "One Ledger"),
        page("Every craft works like the forge:$(li)$(bold)Prepare$() the raw material.$(li)$(bold)Soak$() it in a vat. The liquid "
             "sets its ★, as heating sets an ingot's.$(li)$(bold)Work$() it at the bench. The minigame decides whether it survives."
             "$(li)$(bold)Craft$() parts and gear from it.", "The Pattern"),
        page("Each workshop has three levels: the basic stations (up to ★3), the first upgrade (★5), and both (★7). A vat won't take "
             "a liquid better than its workshop can work, and the bench refuses a piece above the workshop or your skill.$(br2)"
             "Decoration that suits the craft widens its sweet spots a little.", "Levels and ★"),
    ])

    entry("leatherworking", "tannery", "The Tannery", f"{MOD}:tanning_rack", 0, [
        page("$(li)a $(item)Tanning Rack$(), to scrape$(li)a cauldron, for the vat$(li)a $(item)Shaping Rack$(), to cut$(br2)"
             "Upgrades: a $(item)Tanning Barrel$() (a vat for eight, a quarter faster) and a $(item)Fleshing Beam$() (a wider cutting mark).",
             "Stations"),
        crafting("shaping_rack", "tanning_barrel"),
        crafting("fleshing_beam", "leather_from_scraps"),
    ])
    entry("leatherworking", "tanning", "Scraping and Tanning", f"{MOD}:scraped_hide", 1, [
        page("Hold leather and right-click the $(item)Tanning Rack$() for a $(item)Scraped Hide$().", "Scraping"),
        page("Brew a liquor in a Farmer's Delight cooking pot and pour it into an empty cauldron to make a $(item)Tanning Vat$()."
             "$(li)$(item)Bark Liquor$(): four tree bark. ★3$(li)$(item)Tannin Liquor$(): bark and Barrowmoss. ★5"
             "$(li)$(item)Elder Liquor$(): bark and Emberroot. ★7$(br2)Hides come out as $(item)Tanned Leather$() of the liquor's ★.",
             "Tanning"),
        page(VAT, "The Vat"),
    ])
    entry("leatherworking", "cutting", "Cutting and Finishing", f"{MOD}:leather_panel", 2, [
        page("Lay tanned leather on the $(item)Shaping Rack$() and use shears or a knife on it. Hold the button to draw the blade along "
             "the pattern, and let go on the end mark. Two cuts make two $(item)Leather Panels$() of the leather's ★. A cut far off the "
             "mark can ruin it into scraps.", "Cutting"),
        crafting("straps", "hardened_leather"),
        crafting("leather_base", text="Panels make straps, hardened leather (waxed with honeycomb) and leather backings."),
    ])

    entry("weaving", "room", "The Weaving Room", f"{MOD}:spinning_wheel", 0, [
        page("$(li)a $(item)Spinning Wheel$(), to spin$(li)a cauldron, for the vat$(li)a loom, to weave$(br2)"
             "Upgrades: a $(item)Great Loom$() (a wider needle eye) and the $(item)Dye Works$() (a vat for eight, a quarter faster).",
             "Stations"),
        crafting("spinning_wheel", "great_loom"),
        crafting("dye_works"),
    ])
    entry("weaving", "sizing", "Spinning and Sizing", f"{MOD}:sized_yarn", 1, [
        page("Hold wool and right-click the $(item)Spinning Wheel$() for three $(item)Yarn$().", "Spinning"),
        page("Brew a sizing in a cooking pot and pour it into an empty cauldron to make a $(item)Sizing Vat$()."
             "$(li)$(item)Wheat Sizing$(): four wheat. ★3$(li)$(item)Silk Sizing$(): wheat and Spider Silk. ★5"
             "$(li)$(item)Arcane Sizing$(): wheat and Arcane Dust. ★7$(br2)Yarn (or string) comes out as $(item)Sized Yarn$() of the "
             "sizing's ★.", "Sizing"),
        page(VAT, "The Vat"),
    ])
    entry("weaving", "weaving", "Weaving", f"{MOD}:cloth_panel", 2, [
        page("Hold three sized yarn and right-click a loom. Click when the thread lines up with the needle's eye. Three stitches make "
             "two $(item)Cloth Panels$() of the yarn's ★. A missed stitch can snap the thread.", "Weaving"),
        crafting("gambeson_base", "staff_wrap"),
        crafting("bowstring", text="Three sized yarn make a bowstring."),
    ])

    write(BOOK / "categories/goldsmithing.json", {"name": "Goldsmithing", "description": "Refining and chasing gold into jewelry and gilded gear.",
                                                  "icon": f"{MOD}:gold_ring", "sortnum": 7})
    entry("goldsmithing", "goldsmithy", "The Goldsmithy", f"{MOD}:jewelers_bench", 0, [
        page("$(li)a furnace, to smelt gold$(li)a $(item)Crucible$(), to refine it$(li)a $(item)Jeweler's Bench$(), to chase it$(br2)"
             "Upgrades: a $(item)Great Crucible$() (eight at once, a quarter faster) and a $(item)Jeweler's Loupe$() (a wider sweet spot "
             "at the bench).", "Stations"),
        crafting("crucible", "jewelers_bench"),
        crafting("great_crucible", "jewelers_loupe"),
    ])
    entry("goldsmithing", "refining", "Refining Gold", f"{MOD}:fine_gold", 1, [
        page("Put a refining salt in a $(item)Crucible$(), then gold ingots. They come out as $(item)Fine Gold$() of the salt's ★."
             "$(li)$(item)Bone Ash$(): bone meal and charcoal. ★3$(li)$(item)Assay Salt$(): borax and bone ash. ★5"
             "$(li)$(item)Sunsalt$(): assay salt and Arcane Dust. ★7", "Refining"),
        page(VAT, "The Crucible"),
        crafting("bone_ash", "assay_salt"),
    ])
    entry("goldsmithing", "chasing", "Chasing and Jewelry", f"{MOD}:gold_fittings", 2, [
        page("Hold fine gold and right-click the $(item)Jeweler's Bench$(). Click when the marker is in the green, three times, to chase "
             "it into two $(item)Gold Fittings$() of its ★. A miss can spoil it back into nuggets.", "Chasing"),
        page("Fittings make $(item)Gold Rings$() (+1 max health a ★) and $(item)Gold Amulets$() (+0.5 armor a ★), worn in Curios' ring "
             "and necklace slots; and $(item)gilded$() armor and swords: lighter than plate, easy to enchant, and piglins take them for "
             "gold.", "Jewelry and Gilded Gear"),
        crafting("gold_ring", "gold_amulet"),
        crafting("gilded_helmet", "gilded_sword"),
    ])

    entry("woodworking", "woodshop", "The Woodshop", f"{MOD}:wood_lathe", 0, [
        page("$(li)a smoker, to season$(li)a cauldron, for the vat$(li)a $(item)Wood Lathe$(), to turn$(br2)"
             "Upgrades: a $(item)Treadle Lathe$() and a $(item)Bowyer's Bench$(). Each widens the lathe's green.", "Stations"),
        crafting("wood_lathe", "treadle_lathe"),
        crafting("bowyers_bench"),
    ])
    entry("woodworking", "curing", "Seasoning and Curing", f"{MOD}:cured_billet", 1, [
        page("Smoke any log in a smoker into a $(item)Seasoned Billet$().", "Seasoning"),
        page("Pour a finish into an empty cauldron to make a $(item)Finishing Vat$()."
             "$(li)$(item)Linseed Oil$(): four wheat seeds, in a cooking pot. ★3$(li)$(item)Heartwood Resin$(): from the dryads. ★5"
             "$(li)$(item)Sylvan Sap$(): from the dryads. ★7$(br2)Billets come out as $(item)Cured Billets$() of the finish's ★.",
             "Curing"),
        page(VAT, "The Vat"),
    ])
    entry("woodworking", "turning", "Turning and Parts", f"{MOD}:stave", 2, [
        page("Hold a cured billet and right-click the $(item)Wood Lathe$(). Hold to press the chisel in, faster the deeper it goes, and "
             "let go in the green. Three passes make two $(item)Staves$() of the billet's ★.", "Turning"),
        crafting("bow_limbs", "haft"),
        crafting("staff_shaft", "wand_core"),
        crafting("bow_from_parts", "crossbow_from_parts", "A bow or crossbow takes the ★ of its weakest part."),
        crafting("stock"),
    ])


if __name__ == "__main__":
    blocks()
    items()
    recipes()
    tags()
    names()
    handbook()
    print("profession resources written")
