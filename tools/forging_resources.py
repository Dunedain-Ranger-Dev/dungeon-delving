"""Writes the forging blocks' and items' data and assets: blockstates, models, loot tables, recipes, tags and
placeholder item textures.

Run from the project root: python tools/forging_resources.py
Block models reuse vanilla textures. Item textures are simple original placeholders until real art exists.
"""
import json
import struct
import zlib
from pathlib import Path

RES = Path("src/main/resources")
ASSETS = RES / "assets/dungeondelving"
DATA = RES / "data/dungeondelving"
MOD = "dungeondelving"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


# --- Textures ---

def png(path, pixels, size=16):
    rows = b""
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            row += bytes(pixels.get((x, y), (0, 0, 0, 0)))
        rows += bytes(row)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color) + (255,)


def ingot(color, flat=False):
    """A bar, outlined darker, lit on top. Flat bars are thinner and longer, as if hammered out."""
    image = {}
    rows = range(7, 10) if flat else range(6, 11)
    for y in rows:
        for x in range(2, 14):
            # Slanted ends, like the vanilla ingot's silhouette but drawn fresh.
            if (x == 2 and y == rows.start) or (x == 13 and y == rows.stop - 1):
                continue
            edge = y in (rows.start, rows.stop - 1) or x in (2, 13)
            image[(x, y)] = shade(color, -45 if edge else (35 if y == rows.start + 1 else 0))
    return image


def bottle(liquid):
    image = {}
    glass = (200, 220, 230)
    for y in range(3, 5):
        for x in range(7, 9):
            image[(x, y)] = shade((140, 100, 60), 0)  # cork
    for y in range(5, 7):
        for x in range(6, 10):
            image[(x, y)] = shade(glass, -20) if x in (6, 9) else shade(glass, 10)
    for y in range(7, 14):
        for x in range(4, 12):
            edge = x in (4, 11) or y == 13
            image[(x, y)] = shade(glass, -30) if edge else (shade(liquid, 0) if y >= 9 else shade(glass, 20))
    return image


def overlay(points):
    """Pixels to draw over the vanilla iron ingot, which is drawn diagonally with its top face on rows 3 to 7."""
    return {pos: color for pos, color in points}


WHITE = (255, 255, 255, 255)
GLINT = (255, 255, 255, 170)
CRACK = (35, 25, 25, 235)

# Tempered: two little sparkles on the ingot's top face.
TEMPERED_SHINE = overlay([((10, 4), WHITE), ((10, 3), GLINT), ((9, 4), GLINT), ((11, 4), GLINT), ((10, 5), GLINT),
                          ((5, 6), WHITE), ((5, 5), GLINT), ((4, 6), GLINT), ((6, 6), GLINT), ((5, 7), GLINT)])
# Unstable: a jagged crack along the ingot.
UNSTABLE_CRACKS = overlay([((x, y), CRACK) for x, y in [(2, 8), (3, 8), (4, 9), (5, 9), (6, 8), (7, 8), (7, 7), (8, 9), (9, 9),
                                                         (10, 8), (11, 8), (12, 9), (6, 10)]])

# Heated, struck, quenched, unstable and tempered ingots use the vanilla iron ingot, tinted at runtime (see
# ForgingItemColors); these are only the extra layers drawn over it.
ITEM_TEXTURES = {
    "tempered_shine": TEMPERED_SHINE,
    "unstable_cracks": UNSTABLE_CRACKS,
}

# Item models built on the vanilla iron ingot: name -> extra layer (or None).
IRON_INGOT_ITEMS = {"heated_ingot": None, "struck_ingot": None, "quenched_ingot": None, "unstable_ingot": "unstable_cracks", "tempered_iron_ingot": "tempered_shine"}

# --- Blocks ---

HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}


def block_loot(name):
    write(DATA / f"loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MOD}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


def item_model(name, parent=None):
    write(ASSETS / f"models/item/{name}.json", {"parent": parent} if parent else
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MOD}:item/{name}"}})


def blocks():
    # Forge Hearth: a brick furnace that glows once its workshop is complete.
    for lit in (False, True):
        write(ASSETS / f"models/block/forge_hearth{'_on' if lit else ''}.json", {
            "parent": "minecraft:block/orientable",
            "textures": {"front": f"minecraft:block/blast_furnace_front{'_on' if lit else ''}", "side": "minecraft:block/bricks",
                         "top": "minecraft:block/blast_furnace_top"},
        })
    write(ASSETS / "blockstates/forge_hearth.json", {"variants": {
        f"facing={facing},lit={str(lit).lower()}": {"model": f"{MOD}:block/forge_hearth{'_on' if lit else ''}", "y": y}
        for facing, y in HORIZONTAL.items() for lit in (False, True)}})
    item_model("forge_hearth", f"{MOD}:block/forge_hearth")

    # Bellows: two boards with leather between, and an iron nozzle at the front.
    write(ASSETS / "models/block/bellows.json", {
        "parent": "minecraft:block/block",
        "textures": {"wood": "minecraft:block/spruce_planks", "leather": "minecraft:block/brown_wool", "iron": "minecraft:block/iron_block",
                     "particle": "minecraft:block/spruce_planks"},
        "elements": [
            box([1, 0, 2], [15, 2, 15], "wood"),
            box([2, 2, 3], [14, 7, 14], "leather"),
            box([1, 7, 2], [15, 9, 15], "wood"),
            box([6, 2, 0], [10, 6, 3], "iron"),
        ],
    })
    write(ASSETS / "blockstates/bellows.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/bellows", "y": y} for facing, y in HORIZONTAL.items()}})
    item_model("bellows", f"{MOD}:block/bellows")

    # Dwarven Anvil: the anvil shape in dark deepslate.
    write(ASSETS / "models/block/dwarven_anvil.json", {
        "parent": "minecraft:block/template_anvil",
        "textures": {"particle": "minecraft:block/polished_deepslate", "body": "minecraft:block/polished_deepslate",
                     "top": "minecraft:block/chiseled_deepslate"},
    })
    anvil_y = {"south": 0, "west": 90, "north": 180, "east": 270}
    write(ASSETS / "blockstates/dwarven_anvil.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/dwarven_anvil", "y": y} for facing, y in anvil_y.items()}})
    item_model("dwarven_anvil", f"{MOD}:block/dwarven_anvil")

    # Grinding Wheel: the grindstone shape with a finer stone wheel.
    write(ASSETS / "models/block/grinding_wheel.json", {
        "parent": "minecraft:block/grindstone",
        "textures": {"pivot": "minecraft:block/gold_block", "round": "minecraft:block/smooth_stone", "side": "minecraft:block/polished_andesite",
                     "particle": "minecraft:block/polished_andesite", "leg": "minecraft:block/stripped_dark_oak_log"},
    })
    write(ASSETS / "blockstates/grinding_wheel.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/grinding_wheel", "y": y} for facing, y in HORIZONTAL.items()}})
    item_model("grinding_wheel", f"{MOD}:block/grinding_wheel")

    # Oil Cauldron: a vanilla cauldron filled with oil, at three levels like water.
    for level, template in ((1, "template_cauldron_level1"), (2, "template_cauldron_level2"), (3, "template_cauldron_full")):
        write(ASSETS / f"models/block/oil_cauldron_level{level}.json", {
            "parent": f"minecraft:block/{template}",
            "textures": {"bottom": "minecraft:block/cauldron_bottom", "content": f"{MOD}:block/quenching_oil",
                         "inside": "minecraft:block/cauldron_inner", "particle": "minecraft:block/cauldron_side",
                         "side": "minecraft:block/cauldron_side", "top": "minecraft:block/cauldron_top"},
        })
    write(ASSETS / "blockstates/oil_cauldron.json", {"variants": {
        f"level={level}": {"model": f"{MOD}:block/oil_cauldron_level{level}"} for level in (1, 2, 3)}})
    png(ASSETS / "textures/block/quenching_oil.png", oil_surface())
    # Breaking an oil cauldron gives back the cauldron, like a water cauldron.
    write(DATA / "loot_table/blocks/oil_cauldron.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minecraft:cauldron"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })

    for name in ("forge_hearth", "bellows", "dwarven_anvil", "grinding_wheel"):
        block_loot(name)


def oil_surface():
    """Dark amber oil with a few lighter swirls. Original placeholder art."""
    image = {}
    for y in range(16):
        for x in range(16):
            swirl = (x * 3 + y * 5) % 11 == 0 or (x * 7 + y * 2) % 13 == 0
            image[(x, y)] = shade((120, 78, 22), 30 if swirl else ((x + y) % 3) * 4)
    return image


def box(start, end, texture):
    return {"from": start, "to": end, "faces": {face: {"texture": f"#{texture}"} for face in ("north", "east", "south", "west", "up", "down")}}


# --- Recipes ---

def shaped(name, pattern, key, result=None, count=1):
    write(DATA / f"recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": result or f"{MOD}:{name}", "count": count},
    })


def shapeless(name, ingredients, result=None, count=1):
    write(DATA / f"recipe/{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": "misc",
        "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients],
        "result": {"id": result or f"{MOD}:{name}", "count": count},
    })


def heating(name, ingredient, result):
    for kind, time in (("smelting", 100), ("blasting", 50)):
        write(DATA / f"recipe/heating/{name}_{kind}.json", {
            "type": f"{MOD}:heating_{kind}", "category": "misc", "ingredient": {"item": ingredient},
            "result": {"id": result, "count": 1}, "experience": 0.0, "cookingtime": time,
        })


def recipes():
    shaped("forge_hearth", ["BBB", "BCB", "BIB"], {"B": "minecraft:bricks", "C": "minecraft:campfire", "I": "minecraft:iron_block"})
    shaped("bellows", ["PPP", "LLL", "PIP"], {"P": "#minecraft:planks", "L": "minecraft:leather", "I": "minecraft:iron_ingot"})
    shaped("dwarven_anvil", ["WWW", " A ", "DDD"],
           {"W": f"{MOD}:wardsilver_ingot", "A": "minecraft:anvil", "D": "minecraft:polished_deepslate"})
    shaped("grinding_wheel", ["SES", "EGE", "LEL"],
           {"S": "minecraft:smooth_stone", "E": f"{MOD}:emberstone_ingot", "G": "minecraft:grindstone", "L": "minecraft:stripped_dark_oak_log"})
    shapeless("quenching_oil", ["minecraft:glass_bottle", "#c:seeds", "#c:seeds", "#c:seeds", "#c:seeds"])
    heating("iron", "minecraft:iron_ingot", f"{MOD}:heated_ingot")
    heating("tempered", f"{MOD}:tempered_iron_ingot", f"{MOD}:heated_ingot")
    heating("reheat", f"{MOD}:heated_ingot", f"{MOD}:heated_ingot")
    heating("reheat_struck", f"{MOD}:struck_ingot", f"{MOD}:struck_ingot")


# --- Tags ---

DECOR = [
    "minecraft:bricks", "minecraft:brick_stairs", "minecraft:brick_slab", "minecraft:brick_wall",
    "minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab", "minecraft:stone_brick_wall",
    "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:mossy_stone_bricks",
    "minecraft:deepslate_bricks", "minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_slab", "minecraft:deepslate_brick_wall",
    "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab",
    "minecraft:polished_deepslate", "minecraft:chiseled_deepslate", "minecraft:polished_blackstone_bricks",
    "minecraft:chain", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:iron_bars", "minecraft:barrel",
    "minecraft:smithing_table", "minecraft:campfire", "minecraft:soul_campfire", "minecraft:stonecutter",
    "minecraft:iron_block", "minecraft:coal_block", "minecraft:magma_block",
    "dungeondelving:hallstone",
]


def tags():
    write(DATA / "tags/block/forge_decor.json", {"replace": False, "values": DECOR})
    pickaxe = RES / "data/minecraft/tags/block/mineable/pickaxe.json"
    tag = json.loads(pickaxe.read_text(encoding="utf-8"))
    for name in ("forge_hearth", "dwarven_anvil", "grinding_wheel"):
        if f"{MOD}:{name}" not in tag["values"]:
            tag["values"].append(f"{MOD}:{name}")
    write(pickaxe, tag)
    axe = RES / "data/minecraft/tags/block/mineable/axe.json"
    tag = json.loads(axe.read_text(encoding="utf-8")) if axe.exists() else {"replace": False, "values": []}
    for name in ("bellows",):
        if f"{MOD}:{name}" not in tag["values"]:
            tag["values"].append(f"{MOD}:{name}")
    write(axe, tag)


def items():
    for name, image in ITEM_TEXTURES.items():
        png(ASSETS / f"textures/item/{name}.png", image)
    item_model("quenching_oil")
    png(ASSETS / "textures/item/quenching_oil.png", bottle((170, 120, 30)))
    for name, extra in IRON_INGOT_ITEMS.items():
        layers = {"layer0": "minecraft:item/iron_ingot"}
        if extra:
            layers["layer1"] = f"{MOD}:item/{extra}"
        write(ASSETS / f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": layers})
    for stale in ("heated_ingot", "struck_ingot", "quenched_ingot", "tempered_iron_ingot"):
        (ASSETS / f"textures/item/{stale}.png").unlink(missing_ok=True)


# --- Flux, parts, leather, cloth and gear ---

def speckle(x, y, seed=0):
    """A cheap repeatable noise value from 0 to 9 for pixel (x, y)."""
    return (x * 7 + y * 13 + seed * 31 + (x * y) % 5) % 10


def powder(color):
    """A little mound of powder."""
    image = {}
    for y in range(8, 14):
        half = (y - 7) + 1
        for x in range(8 - half, 8 + half):
            edge = y == 13 or x in (8 - half, 7 + half)
            image[(x, y)] = shade(color, -40 if edge else (25 if speckle(x, y) > 6 else 0))
    return image


def crystals(color):
    """A small cluster of pale crystals."""
    image = {}
    for cx, top, bottom in ((5, 6, 13), (8, 3, 13), (11, 7, 13)):
        for y in range(top, bottom + 1):
            for x in (cx - 1, cx, cx + 1):
                if y == top and x != cx:
                    continue
                image[(x, y)] = shade(color, 40 if x == cx - 1 else (-35 if x == cx + 1 else 0))
    return image


STEEL = (176, 182, 196)


def plate():
    """A square steel plate with a rivet in each corner."""
    image = {}
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            image[(x, y)] = shade(STEEL, -45 if edge else (30 if x + y < 12 else 0))
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        image[(x, y)] = shade(STEEL, 60)
    return image


def rivets():
    """A handful of rivets."""
    image = {}
    for cx, cy in ((4, 5), (10, 4), (7, 9), (12, 10), (4, 12)):
        for x, y in ((cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + 1, cy + 1)):
            image[(x, y)] = shade(STEEL, 40 if (x, y) == (cx, cy) else -10)
        image[(cx, cy + 2)] = shade(STEEL, -50)
    return image


def links():
    """A short length of chain."""
    image = {}
    for i, (cx, cy) in enumerate(((3, 3), (6, 6), (9, 9), (12, 12))):
        for x, y in ((cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1), (cx - 1, cy - 1), (cx + 1, cy + 1)):
            if 0 <= x < 16 and 0 <= y < 16:
                image[(x, y)] = shade(STEEL, 30 if i % 2 else -20)
    return image


def hide(color, stitched=False):
    """A square of leather, with a stitched border if it's been worked."""
    image = {}
    for y in range(3, 14):
        for x in range(2, 14):
            if (x, y) in ((2, 3), (13, 3), (2, 13), (13, 13)):
                continue
            edge = x in (2, 13) or y in (3, 13)
            image[(x, y)] = shade(color, -35 if edge else (12 if speckle(x, y, 1) > 7 else 0))
    if stitched:
        for x in range(4, 12, 2):
            image[(x, 5)] = image[(x, 11)] = shade(color, 70)
    return image


def cloth(color, padded=False):
    """Woven cloth: a checked weave, quilted if padded."""
    image = {}
    for y in range(3, 13):
        for x in range(3, 13):
            weave = (x + y) % 2 == 0
            quilt = padded and (x in (5, 10) or y in (5, 10))
            image[(x, y)] = shade(color, -30 if quilt else (15 if weave else -5))
    return image


def scraps(color):
    """A few torn strips of leather."""
    image = {}
    for x0, y0, length in ((3, 4, 6), (8, 7, 6), (4, 10, 5), (10, 3, 3)):
        for i in range(length):
            for dy in (0, 1):
                image[(x0 + i, y0 + dy + (i // 3))] = shade(color, -30 if dy else (10 if i % 2 else 0))
    return image


def backing(color):
    """A vest-shaped backing that armor is built on."""
    image = {}
    for y in range(2, 15):
        for x in range(3, 13):
            if y < 5 and 6 <= x <= 9:
                continue  # neck
            if y > 6 and x in (3, 12):
                continue  # arms
            edge = x in (3, 4, 11, 12) and y < 5 or y == 14 or x in (4, 11)
            image[(x, y)] = shade(color, -30 if edge else 0)
    for y in range(6, 14, 2):
        image[(8, y)] = shade(color, 60)
    return image


# A forged piece's maker's mark: a little gold star in the corner of the vanilla icon.
FORGED_MARK = {(13, 12): (255, 210, 60, 255), (12, 13): (255, 210, 60, 255), (13, 13): (255, 240, 150, 255), (14, 13): (255, 210, 60, 255),
               (13, 14): (255, 210, 60, 255)}

GEAR_TEXTURES = {
    "sand_flux": powder((214, 196, 140)),
    "borax": crystals((228, 236, 240)),
    "borax_flux": powder((236, 240, 244)),
    "runic_flux": powder((164, 91, 216)),
    "runeforged_flux": powder((242, 181, 58)),
    "armor_plate": plate(),
    "rivets": rivets(),
    "chain_links": links(),
    "refined_leather": hide((160, 100, 60)),
    "leather_scraps": scraps((130, 82, 48)),
    "boiled_leather": hide((120, 72, 40)),
    "hardened_leather": hide((95, 58, 34), stitched=True),
    "leather_base": backing((140, 88, 52)),
    "woven_cloth": cloth((222, 214, 196)),
    "padded_cloth": cloth((206, 196, 172), padded=True),
    "gambeson_base": backing((210, 198, 170)),
    "forged_mark": FORGED_MARK,
}

ARMOR_SLOTS = ("helmet", "chestplate", "leggings", "boots")
WEIGHTS = {"brigandine": "leather", "hauberk": "chainmail", "plate": "iron"}


def borax_deposit():
    """Grey stone flecked with pale borax crystals."""
    image = {}
    for y in range(16):
        for x in range(16):
            n = speckle(x, y, 2)
            image[(x, y)] = shade((120, 120, 124), -12 if n < 3 else (10 if n > 7 else 0))
    for x, y in ((3, 3), (4, 3), (3, 4), (11, 2), (12, 3), (7, 7), (8, 7), (8, 8), (2, 11), (3, 12), (12, 10), (13, 11), (12, 11), (9, 13)):
        image[(x, y)] = shade((232, 240, 246), 0 if (x + y) % 2 else -20)
    return image


def gear():
    for name, image in GEAR_TEXTURES.items():
        png(ASSETS / f"textures/item/{name}.png", image)
        if name != "forged_mark":
            item_model(name)

    # Gear icons are vanilla icons with the maker's mark on top (brigandine is dyeable, like leather).
    for weight, vanilla in WEIGHTS.items():
        for slot in ARMOR_SLOTS:
            layers = {"layer0": f"minecraft:item/{vanilla}_{slot}"}
            if vanilla == "leather":
                layers["layer1"] = f"minecraft:item/leather_{slot}_overlay"
            layers[f"layer{len(layers)}"] = f"{MOD}:item/forged_mark"
            write(ASSETS / f"models/item/{weight}_{slot}.json", {"parent": "minecraft:item/generated", "textures": layers})
    for weapon, vanilla in (("forged_sword", "iron_sword"), ("forged_axe", "iron_axe")):
        write(ASSETS / f"models/item/{weapon}.json", {"parent": "minecraft:item/handheld",
                                                      "textures": {"layer0": f"minecraft:item/{vanilla}", "layer1": f"{MOD}:item/forged_mark"}})

    # Tanning Rack: two posts and crossbars with a hide stretched between them.
    write(ASSETS / "models/block/tanning_rack.json", {
        "parent": "minecraft:block/block",
        "textures": {"wood": "minecraft:block/stripped_spruce_log", "hide": "minecraft:block/brown_wool", "particle": "minecraft:block/stripped_spruce_log"},
        "elements": [
            box([0, 0, 7], [2, 16, 9], "wood"),
            box([14, 0, 7], [16, 16, 9], "wood"),
            box([2, 13, 7], [14, 15, 9], "wood"),
            box([2, 2, 7], [14, 4, 9], "wood"),
            box([3, 4, 7.5], [13, 13, 8.5], "hide"),
        ],
    })
    write(ASSETS / "blockstates/tanning_rack.json", {"variants": {
        f"facing={facing}": {"model": f"{MOD}:block/tanning_rack", "y": y} for facing, y in HORIZONTAL.items()}})
    item_model("tanning_rack", f"{MOD}:block/tanning_rack")
    block_loot("tanning_rack")

    # Borax deposits: found in tier 2+ dungeons, through resource markers.
    png(ASSETS / "textures/block/borax_deposit.png", borax_deposit())
    write(ASSETS / "models/block/borax_deposit.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{MOD}:block/borax_deposit"}})
    write(ASSETS / "blockstates/borax_deposit.json", {"variants": {"": {"model": f"{MOD}:block/borax_deposit"}}})
    item_model("borax_deposit", f"{MOD}:block/borax_deposit")
    write(DATA / "loot_table/blocks/borax_deposit.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": f"{MOD}:borax_deposit", "conditions": [{"condition": "minecraft:match_tool", "predicate": {
                "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
            {"type": "minecraft:item", "name": f"{MOD}:borax", "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}],
    })


def starred_shapeless(name, ingredients, count=1):
    write(DATA / f"recipe/{name}.json", {
        "type": f"{MOD}:starred_shapeless", "category": "equipment",
        "ingredients": [({"tag": v[1:]} if v.startswith("#") else {"item": v}) for v in ingredients],
        "result": {"id": f"{MOD}:{name}", "count": count},
    })


def starred_shaped(name, pattern, key):
    write(DATA / f"recipe/{name}.json", {
        "type": f"{MOD}:starred_shaped", "category": "equipment", "pattern": pattern,
        "key": {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v}) for k, v in key.items()},
        "result": {"id": f"{MOD}:{name}", "count": 1},
    })


def gear_recipes():
    tempered, flux = f"{MOD}:tempered_iron_ingot", f"#{MOD}:flux"
    shapeless("sand_flux", ["minecraft:sand", "minecraft:bone_meal"], count=2)
    shapeless("borax_flux", [f"{MOD}:borax", "minecraft:sand"], count=2)
    shapeless("runic_flux", [f"{MOD}:borax_flux", f"{MOD}:borax_flux", f"{MOD}:runic_shard_3"], count=2)
    shapeless("runeforged_flux", [f"{MOD}:runic_flux", f"{MOD}:runic_flux", f"{MOD}:thailite_ingot"], count=2)
    starred_shapeless("armor_plate", [tempered, flux])
    starred_shapeless("rivets", [tempered, flux, "minecraft:iron_nugget"], count=4)
    starred_shapeless("chain_links", [tempered, tempered, flux], count=8)

    # Refined leather is scraped on a Tanning Rack, not crafted. Torn hides leave scraps; four make a leather again.
    (DATA / "recipe/refined_leather.json").unlink(missing_ok=True)
    shapeless("leather_from_scraps", [f"{MOD}:leather_scraps"] * 4, result="minecraft:leather")
    shaped("tanning_rack", ["S S", "SLS", "P P"], {"S": "minecraft:stick", "L": "minecraft:leather", "P": "#minecraft:planks"})
    shapeless("hardened_leather", [f"{MOD}:boiled_leather"] * 3 + ["minecraft:string", "minecraft:honeycomb"], count=3)
    shapeless("leather_base", [f"{MOD}:refined_leather"] * 3)
    shapeless("padded_cloth", [f"{MOD}:woven_cloth", f"{MOD}:woven_cloth", "#minecraft:wool"])
    shapeless("gambeson_base", [f"{MOD}:padded_cloth", f"{MOD}:padded_cloth"])

    base, gambeson, hardened = f"{MOD}:leather_base", f"{MOD}:gambeson_base", f"{MOD}:hardened_leather"
    plate_, rivet, link = f"{MOD}:armor_plate", f"{MOD}:rivets", f"{MOD}:chain_links"
    # Vanilla armor shapes: the main material (M) where the ingots go, the backing (B) in the gap.
    shapes = {
        "helmet": ["MMM", "MBM"],
        "chestplate": ["MBM", "MMM", "MMM"],
        "leggings": ["MMM", "MBM", "M M"],
        "boots": ["MBM", "M M"],
    }
    # Brigandine is hardened leather (H) studded with rivets (R): the same shapes, with rivets in some of the leather's places.
    brigandine = {
        "helmet": ["HRH", "HBH"],
        "chestplate": ["HBH", "RHR", "HHH"],
        "leggings": ["HRH", "HBH", "R R"],
        "boots": ["RBR", "H H"],
    }
    for slot in ARMOR_SLOTS:
        starred_shaped(f"plate_{slot}", shapes[slot], {"M": plate_, "B": base})
        starred_shaped(f"hauberk_{slot}", shapes[slot], {"M": link, "B": base})
        starred_shaped(f"brigandine_{slot}", brigandine[slot], {"H": hardened, "R": rivet, "B": gambeson})
    starred_shaped("forged_sword", ["P", "P", "S"], {"P": plate_, "S": "minecraft:stick"})
    starred_shaped("forged_axe", ["PP", "PS", " S"], {"P": plate_, "S": "minecraft:stick"})


def add_to_tag(path, values):
    """Adds values to a tag file, keeping whatever's already in it."""
    tag = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    for value in values:
        if value not in tag["values"]:
            tag["values"].append(value)
    write(path, tag)


def gear_tags():
    write(DATA / "tags/item/flux.json", {"replace": False, "values": [f"{MOD}:{name}" for name in
                                                                      ("sand_flux", "borax_flux", "runic_flux", "runeforged_flux")]})
    vanilla_tags = RES / "data/minecraft/tags/item"
    for slot, tag in (("helmet", "head_armor"), ("chestplate", "chest_armor"), ("leggings", "leg_armor"), ("boots", "foot_armor")):
        add_to_tag(vanilla_tags / f"{tag}.json", [f"{MOD}:{weight}_{slot}" for weight in WEIGHTS])
    add_to_tag(vanilla_tags / "dyeable.json", [f"{MOD}:brigandine_{slot}" for slot in ARMOR_SLOTS])
    add_to_tag(vanilla_tags / "swords.json", [f"{MOD}:forged_sword"])
    add_to_tag(vanilla_tags / "axes.json", [f"{MOD}:forged_axe"])
    for tier in (2, 3, 4):
        add_to_tag(DATA / f"tags/block/dungeon_resources/tier_{tier}.json", [f"{MOD}:borax_deposit"])
    add_to_tag(RES / "data/minecraft/tags/block/mineable/pickaxe.json", [f"{MOD}:borax_deposit"])
    add_to_tag(RES / "data/minecraft/tags/block/mineable/axe.json", [f"{MOD}:tanning_rack"])


if __name__ == "__main__":
    blocks()
    recipes()
    tags()
    items()
    gear()
    gear_recipes()
    gear_tags()
    print("forging resources written")
