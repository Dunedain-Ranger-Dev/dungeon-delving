"""Makes the dungeon ore and ingot textures by recoloring vanilla ones, so they read like the vanilla metals.

Run from the project root, after a Gradle build or run (it reads vanilla's textures from the Minecraft resources jar
that ModDevGradle downloads): python tools/vanilla_recolor_textures.py

- Ingots: vanilla's iron ingot, recolored to each ore's color, keeping its shading.
- Ores: vanilla's iron or copper ore, with only the ore specks recolored (the tinted pixels; the stone around them
  is neutral grey and stays as it is).

These are recolors of Mojang's art, which mods commonly ship, so they aren't covered by this project's All Rights
Reserved asset license the way original art is. Needs Pillow (pip install pillow).
"""
import io
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/dungeondelving/textures"
VANILLA_JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"

# Ore colors (as in DungeonOre.java, Barrow Iron a touch cooler), and which vanilla ore each one's specks are patterned after.
ORES = {
    "barrow_iron": (0xC9D3DE, "iron_ore"),  # a cool silver, so its specks stand out from the grey stone
    "thraggite": (0x4FC75A, "copper_ore"),
    "wardsilver": (0x4FA8E0, "iron_ore"),
    "emberstone": (0xA45BD8, "copper_ore"),
    "thailite": (0xF2B53A, "iron_ore"),
}

# Other textures recolored whole: (vanilla texture, our texture, color).
RECOLORS = [
    ("block/nether_portal", "block/rift_portal", 0x46C8E0),   # the rift, a cold cyan so it isn't mistaken for the Nether
    ("item/amethyst_shard", "item/bloodshed_stone", 0xB3202A),  # Bloodshed Stone
    ("mob_effect/regeneration", "mob_effect/mending", 0xE8A0C8),  # Mending, the casters' slow heal
]


def vanilla(jar, path):
    return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")


def luminance(pixel):
    r, g, b = pixel[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


# How far a pixel's color channels must spread to count as an ore speck rather than grey stone.
SPECK_CHROMA = 14


def chroma(pixel):
    return max(pixel[:3]) - min(pixel[:3])


def recolor(pixel, color, low, high):
    """Maps a pixel's brightness (low..high in the source) onto shades of the target color, keeping its alpha."""
    target = ((color >> 16) & 255, (color >> 8) & 255, color & 255)
    t = 0.5 if high == low else (luminance(pixel) - low) / (high - low)
    scale = 0.45 + 0.8 * t  # dark shades well below the color, highlights a little above it
    return tuple(max(0, min(255, round(c * scale))) for c in target) + (pixel[3],)


def recolored(image, color, pixels):
    """A copy of the image with the given pixel positions recolored."""
    out = image.copy()
    shades = [luminance(image.getpixel(p)) for p in pixels]
    low, high = min(shades), max(shades)
    for p in pixels:
        out.putpixel(p, recolor(image.getpixel(p), color, low, high))
    return out


def main():
    jar = zipfile.ZipFile(VANILLA_JAR)
    ingot = vanilla(jar, "item/iron_ingot")
    ingot_pixels = [(x, y) for x in range(16) for y in range(16) if ingot.getpixel((x, y))[3] > 0]
    for name, (color, pattern) in ORES.items():
        recolored(ingot, color, ingot_pixels).save(TEXTURES / f"item/{name}_ingot.png")
        ore = vanilla(jar, f"block/{pattern}")
        specks = [(x, y) for x in range(16) for y in range(16) if chroma(ore.getpixel((x, y))) > SPECK_CHROMA]
        recolored(ore, color, specks).save(TEXTURES / f"block/{name}_ore.png")
        print(f"{name}: ingot, ore ({pattern}, {len(specks)} speck pixels)")
    for source, target, color in RECOLORS:
        image = vanilla(jar, source)
        everything = [(x, y) for x in range(image.width) for y in range(image.height) if image.getpixel((x, y))[3] > 0]
        recolored(image, color, everything).save(TEXTURES / f"{target}.png")
        meta = f"assets/minecraft/textures/{source}.png.mcmeta"
        if meta in jar.namelist():  # Keep animations (the rift).
            (TEXTURES / f"{target}.png.mcmeta").write_bytes(jar.read(meta))
        print(f"{target}: from {source}")


if __name__ == "__main__":
    main()
