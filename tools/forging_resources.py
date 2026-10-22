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

    # Quench Tub: a wooden tub, empty or full of oil.
    for full in (False, True):
        elements = [
            box([1, 0, 1], [15, 1, 15], "wood"),
            box([1, 1, 1], [15, 10, 2], "wood"),
            box([1, 1, 14], [15, 10, 15], "wood"),
            box([1, 1, 2], [2, 10, 14], "wood"),
            box([14, 1, 2], [15, 10, 14], "wood"),
        ]
        if full:
            elements.append({"from": [2, 1, 2], "to": [14, 8, 14], "faces": {"up": {"texture": "#oil"}}})
        write(ASSETS / f"models/block/quench_tub{'_full' if full else ''}.json", {
            "parent": "minecraft:block/block",
            "textures": {"wood": "minecraft:block/stripped_spruce_log", "oil": "minecraft:block/honey_block_top",
                         "particle": "minecraft:block/stripped_spruce_log"},
            "elements": elements,
        })
    write(ASSETS / "blockstates/quench_tub.json", {"variants": {
        f"level={level}": {"model": f"{MOD}:block/quench_tub{'_full' if level > 0 else ''}"} for level in range(9)}})
    item_model("quench_tub", f"{MOD}:block/quench_tub")

    for name in ("forge_hearth", "bellows", "dwarven_anvil", "grinding_wheel", "quench_tub"):
        block_loot(name)


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
    shaped("quench_tub", ["P P", "PIP", "PPP"], {"P": "minecraft:stripped_spruce_log", "I": "minecraft:iron_ingot"})
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
    for name in ("bellows", "quench_tub"):
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


if __name__ == "__main__":
    blocks()
    recipes()
    tags()
    items()
    print("forging resources written")
