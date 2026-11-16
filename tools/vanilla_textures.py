"""Makes every texture this mod ships from vanilla Minecraft textures, recolored, so none is original art.

Run from the project root, after a Gradle build or run (it reads vanilla's textures from the Minecraft resources jar
that ModDevGradle downloads): python tools/vanilla_textures.py [path/to/minecraft-resources.jar]

Each texture is a vanilla one recolored to a target color, keeping vanilla's shading (each pixel's brightness maps onto
shades of the color). Some keep only part of the vanilla texture (an ore's specks, a bow's wood), and a few are put
together from two (a stick with an arrow's head for a spear). These stand in until real art exists.

- Mob skins (goblins, hobgoblins, orcs, and the kobolds drawn without the Kobolds! mod): vanilla's zombie, whose layout
  matches the humanoid model they're drawn with. Its skin, shirt and trousers are told apart by hue and each recolored
  on its own. The left arm and leg are filled in as mirrors of the right, as the game does for old 64x32 skins, since
  Iron's Spells' caster body reads them from there.
- Ores, ingots and raw ores: vanilla's iron or copper ore with only the specks recolored, its iron ingot, and its raw
  iron, copper or gold lumps (some mirrored, so no two dungeon ores share a silhouette).

The kobold skins under entity/kobold/ aren't made here: they're recolors of the Kobolds! mod's own (MIT), for its
model, whose layout no vanilla skin fits (tools/kobold_dragon_skins.py).

Prints any shipped texture this script doesn't make. Needs Pillow (pip install pillow).
"""
import colorsys
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/dungeondelving/textures"
VANILLA_JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"
# Made elsewhere, from another mod's art (see the module doc).
NOT_FROM_VANILLA = "entity/kobold/"

JAR = None


def vanilla(path):
    return Image.open(io.BytesIO(JAR.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def vanilla_meta(path):
    name = f"assets/minecraft/textures/{path}.png.mcmeta"
    return JAR.read(name) if name in JAR.namelist() else None


def luminance(pixel):
    r, g, b = pixel[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def chroma(pixel):
    return max(pixel[:3]) - min(pixel[:3])


def hue(pixel):
    return colorsys.rgb_to_hsv(*(c / 255 for c in pixel[:3]))[0] * 360


def opaque(image):
    return [(x, y) for y in range(image.height) for x in range(image.width) if image.getpixel((x, y))[3] > 0]


def rgb(color):
    return (color >> 16) & 255, (color >> 8) & 255, color & 255


def shade(out, source, pixels, color):
    """Recolors the given pixels of out to shades of a color, by their brightness in source: the darkest well below
    the color, the brightest a little above."""
    if not pixels:
        return
    shades = [luminance(source.getpixel(p)) for p in pixels]
    low, high = min(shades), max(shades)
    target = rgb(color)
    for p in pixels:
        pixel = source.getpixel(p)
        t = 0.5 if high == low else (luminance(pixel) - low) / (high - low)
        scale = 0.45 + 0.8 * t
        out.putpixel(p, tuple(max(0, min(255, round(c * scale))) for c in target) + (pixel[3],))


def recolor(image, color, keep=None):
    """The image recolored to a color, keeping its shading. With keep, only the pixels it accepts (given the pixel's
    color and position) stay; the rest turn clear."""
    out = image.copy()
    pixels = opaque(image)
    if keep:
        for p in pixels:
            if not keep(image.getpixel(p), p):
                out.putpixel(p, (0, 0, 0, 0))
        pixels = [p for p in pixels if keep(image.getpixel(p), p)]
    shade(out, image, pixels, color)
    return out


def recolor_parts(image, parts):
    """The image with each part recolored on its own: parts is a list of (test, color), test taking a pixel's color
    and position; the first that matches wins, and a pixel none match is left as it is."""
    out = image.copy()
    groups = [[] for _ in parts]
    for p in opaque(image):
        for i, (test, _) in enumerate(parts):
            if test(image.getpixel(p), p):
                groups[i].append(p)
                break
    for (_, color), pixels in zip(parts, groups):
        shade(out, image, pixels, color)
    return out


def mirror(image, axis):
    """Mirrored across the top-left to bottom-right diagonal (axis "diagonal", which keeps the light coming from the
    top left) or left to right."""
    if axis == "diagonal":
        return image.transpose(Image.Transpose.TRANSPOSE)
    return image.transpose(Image.Transpose.FLIP_LEFT_RIGHT)


# --- Mob skins -------------------------------------------------------------------------------------------------------

# Vanilla's zombie: green skin, a cyan shirt, blue trousers, and near-black eyes and mouth (left as they are).
def zombie_skin(pixel, _):
    return chroma(pixel) >= 12 and 60 <= hue(pixel) < 150


def zombie_shirt(pixel, _):
    return chroma(pixel) >= 12 and 150 <= hue(pixel) < 215


def zombie_trousers(pixel, _):
    return chroma(pixel) >= 12 and 215 <= hue(pixel) < 320


# Skin colors.
GOBLIN = 0x6E9A3A
HOBGOBLIN = 0xC0642E
ORC = 0x7C8C62
KOBOLD = 0xA4502C
# Cloth and leather colors.
LEATHER = 0x6A4A2C
DARK_LEATHER = 0x4A3A2E
HIDE = 0x86603A
MOSS = 0x3E5A2A
RED = 0x9A2222
DARK_RED = 0x5A1414
ORANGE = 0xD06A1E
PURPLE = 0x5E2E86
DARK_PURPLE = 0x30203E
BLUE = 0x2E5A9E
STEEL = 0x5E626C
TAN = 0xC09A6A
GOLD = 0xD8B040

# Mob -> (skin, shirt, trousers).
MOBS = {
    "goblin": (GOBLIN, HIDE, LEATHER),
    "goblin_archer": (GOBLIN, MOSS, LEATHER),
    "goblin_gladiator": (GOBLIN, DARK_LEATHER, HIDE),
    "goblin_shaman": (GOBLIN, PURPLE, PURPLE),
    "goblin_pyro": (GOBLIN, ORANGE, ORANGE),
    "goblin_chief": (GOBLIN, RED, DARK_LEATHER),
    "hobgoblin_soldier": (HOBGOBLIN, RED, LEATHER),
    "hobgoblin_hunter": (HOBGOBLIN, LEATHER, HIDE),
    "hobgoblin_vanguard": (HOBGOBLIN, STEEL, LEATHER),
    "hobgoblin_spellbreaker": (HOBGOBLIN, DARK_PURPLE, LEATHER),
    "hobgoblin_veteran": (HOBGOBLIN, STEEL, DARK_LEATHER),
    "hobgoblin_general": (HOBGOBLIN, RED, STEEL),
    "orc_scrapper": (ORC, HIDE, DARK_LEATHER),
    "orc_raider": (ORC, DARK_LEATHER, LEATHER),
    "orc_veteran": (ORC, STEEL, DARK_LEATHER),
    "orc_vagabond": (ORC, LEATHER, HIDE),
    "orc_rampager": (ORC, RED, DARK_LEATHER),
    "orc_doomsayer": (ORC, DARK_RED, DARK_RED),
    "orc_shaman": (ORC, BLUE, BLUE),
    "orc_lorekeeper": (ORC, DARK_PURPLE, DARK_PURPLE),
    "orc_commander": (ORC, RED, STEEL),
    "orc_chieftain": (ORC, DARK_LEATHER, STEEL),
    "orc_warchief": (ORC, DARK_RED, STEEL),
    "kobold_looter": (KOBOLD, LEATHER, LEATHER),
    "kobold_scavenger": (KOBOLD, TAN, LEATHER),
    "kobold_packmaster": (KOBOLD, DARK_RED, DARK_LEATHER),
}


def mirrored_limb(image, u, v):
    """A 4x4x12 limb's 16x16 block of a skin, mirrored the way the game mirrors an old skin's right limb into its
    left: each face flipped, and the outer and inner sides swapped."""
    out = Image.new("RGBA", (16, 16))
    faces = [  # (from x, from y, width, height, to x)
        (4, 0, 4, 4, 4), (8, 0, 4, 4, 8),  # top, bottom: flipped in place
        (0, 4, 4, 12, 8), (8, 4, 4, 12, 0),  # outer and inner sides: swapped
        (4, 4, 4, 12, 4), (12, 4, 4, 12, 12),  # front, back: flipped in place
    ]
    for x, y, w, h, to in faces:
        face = image.crop((u + x, v + y, u + x + w, v + y + h)).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        out.paste(face, (to, y))
    return out


def mob_skin(skin, shirt, trousers):
    zombie = vanilla("entity/zombie/zombie")
    out = recolor_parts(zombie, [(zombie_skin, skin), (zombie_shirt, shirt), (zombie_trousers, trousers)])
    out.paste(mirrored_limb(out, 40, 16), (32, 48))  # left arm
    out.paste(mirrored_limb(out, 0, 16), (16, 48))  # left leg
    return out


# --- Ores and metals -------------------------------------------------------------------------------------------------

# Ore colors (as in DungeonOre.java, Barrow Iron a touch cooler), which vanilla ore each one's specks are patterned after,
# and which vanilla raw ore its raw lump is shaped after (optionally mirrored).
ORES = {
    "barrow_iron": (0xC9D3DE, "iron_ore", "copper"),  # a cool silver, so its specks stand out from the grey stone
    "thraggite": (0x4FC75A, "copper_ore", "iron/diagonal"),
    "wardsilver": (0x4FA8E0, "iron_ore", "gold"),
    "emberstone": (0xA45BD8, "copper_ore", "copper/diagonal"),
    "thailite": (0xF2B53A, "iron_ore", "gold/diagonal"),
}
# How far a pixel's color channels must spread to count as an ore speck rather than grey stone.
SPECK_CHROMA = 14


def ore(pattern, color):
    image = vanilla(f"block/{pattern}")
    out = image.copy()
    shade(out, image, [p for p in opaque(image) if chroma(image.getpixel(p)) > SPECK_CHROMA], color)
    return out


def raw_ore(shape, color):
    base, _, axis = shape.partition("/")
    image = vanilla(f"item/raw_{base}")
    return recolor(mirror(image, axis) if axis else image, color)


# --- Items -----------------------------------------------------------------------------------------------------------

def wood(pixel, _):
    return chroma(pixel) >= 24


def string(pixel, _):
    return chroma(pixel) < 24


# The Goblin Bow: vanilla's bow, its wood a dark mossy brown and its string twine. The arrow in the drawing frames is
# wood too, so it comes out the same brown.
GOBLIN_BOW_WOOD = 0x5E4C2C
GOBLIN_BOW_STRING = 0xA89A6A


def goblin_bow(frame):
    return recolor_parts(vanilla(f"item/{frame}"), [(wood, GOBLIN_BOW_WOOD), (string, GOBLIN_BOW_STRING)])


def tipped_stick(shaft, tip_source, tip_test, tip_color):
    """Vanilla's stick, recolored, with another item's top-right tip laid over its top end."""
    out = recolor(vanilla("item/stick"), shaft)
    tip = vanilla(tip_source)
    out.alpha_composite(recolor(tip, tip_color, tip_test))
    return out


def arrow_head(pixel, p):
    return p[0] >= 10 and p[1] <= 5 and chroma(pixel) < 24


def bone_end(pixel, p):
    return p[0] >= 10 and p[1] <= 5


def gold_tip(pixel, p):
    return p[0] >= 10 and p[1] <= 5


def goblin_chief_staff():
    """A dark red stick with the spectral arrow's head in gold."""
    return tipped_stick(0x6A2E1A, "item/spectral_arrow", gold_tip, GOLD)


# Runic Keys: vanilla's ominous trial key in each key's metal, its red eyes the rune's glow.
KEYS = {
    1: (0xBEBEC4, 0xFFD278),  # iron, amber rune
    2: (0xC8CDD6, 0xA0DCFF),  # Barrow Iron, pale blue
    3: (0x4FC75A, 0xC8FFAA),  # Thraggite, pale green
    4: (0x4FA8E0, 0xBEF0FF),  # Wardsilver, ice
}


def rune(pixel, _):
    r, g, b = pixel[:3]
    return r > g + 40 and r > b + 40


def runic_key(metal, glow):
    return recolor_parts(vanilla("item/ominous_trial_key"), [(rune, glow), (lambda pixel, p: True, metal)])


# Whole recolors: our texture -> (vanilla texture, color).
RECOLORS = {
    "block/rift_portal": ("block/nether_portal", 0x46C8E0),  # the rift, a cold cyan so it isn't mistaken for the Nether
    "block/riftstone": ("block/deepslate_bricks", 0x4A5662),  # a cold, faintly blue-grey brick
    "item/bloodshed_stone": ("item/amethyst_shard", 0xB3202A),
    "mob_effect/mending": ("mob_effect/regeneration", 0xE8A0C8),  # Mending, the casters' slow heal
    # Coins: vanilla's snowball in each metal.
    "item/copper_coin": ("item/snowball", 0xC06A3A),
    "item/silver_coin": ("item/snowball", 0xC8CCD2),
    "item/gold_coin": ("item/snowball", 0xE8BC2C),
    "item/platinum_coin": ("item/snowball", 0xA8E0E4),
    # Runic Shards: vanilla's echo shard, tinted like the tier its boss guards.
    "item/runic_shard_1": ("item/echo_shard", 0x4FC75A),
    "item/runic_shard_2": ("item/echo_shard", 0x4FA8E0),
    "item/runic_shard_3": ("item/echo_shard", 0xA45BD8),
    "item/testers_charm": ("item/heart_of_the_sea", 0xC8323C),
}


def save(name, image, meta=None):
    path = TEXTURES / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    meta_path = path.with_name(path.name + ".mcmeta")
    if meta:
        meta_path.write_bytes(meta)
    elif meta_path.exists():
        meta_path.unlink()


def main():
    global JAR
    JAR = zipfile.ZipFile(sys.argv[1] if len(sys.argv) > 1 else VANILLA_JAR)
    made = {}
    metas = {}
    for name, colors in MOBS.items():
        made[f"entity/{name}"] = mob_skin(*colors)
    for name, (color, pattern, shape) in ORES.items():
        made[f"item/{name}_ingot"] = recolor(vanilla("item/iron_ingot"), color)
        made[f"item/raw_{name}"] = raw_ore(shape, color)
        made[f"block/{name}_ore"] = ore(pattern, color)
        made[f"block/{name}_block"] = recolor(vanilla("block/iron_block"), color)
    for name, (source, color) in RECOLORS.items():
        made[name] = recolor(vanilla(source), color)
        metas[name] = vanilla_meta(source)  # keeps animations (the rift)
    made["item/goblin_bow"] = goblin_bow("bow")
    for i in range(3):
        made[f"item/goblin_bow_pulling_{i}"] = goblin_bow(f"bow_pulling_{i}")
    made["item/goblin_spear"] = tipped_stick(0x5A3E22, "item/arrow", arrow_head, 0x9A9A9E)
    made["item/goblin_totem_staff"] = tipped_stick(0x4A3420, "item/bone", bone_end, 0xC8D4A0)
    made["item/goblin_chief_staff"] = goblin_chief_staff()
    for tier, (metal, glow) in KEYS.items():
        made[f"item/runic_key_{tier}"] = runic_key(metal, glow)
    for name, image in made.items():
        save(name, image, metas.get(name))
    shipped = {str(p.relative_to(TEXTURES).with_suffix("")).replace("\\", "/") for p in TEXTURES.rglob("*.png")}
    left = sorted(name for name in shipped - set(made) if not name.startswith(NOT_FROM_VANILLA))
    print(f"{len(made)} textures written from vanilla")
    if left:
        print("Not made from vanilla (still original):", ", ".join(left))


if __name__ == "__main__":
    main()
