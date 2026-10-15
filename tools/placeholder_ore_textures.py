"""Draws simple 16x16 placeholder textures for the dungeon ores, one color per tier.

Run from the project root: python tools/placeholder_ore_textures.py
Uses only the standard library. Replace the output with real art when it exists.
"""
import random
import struct
import zlib
from pathlib import Path

ASSETS = Path("src/main/resources/assets/dungeondelving/textures")

# Must match DungeonOre.java.
ORES = {
    "common": 0xB8B8B8,
    "tier_1": 0x4FC75A,
    "tier_2": 0x4FA8E0,
    "tier_3": 0xA45BD8,
    "tier_4": 0xF2B53A,
}


def png(path, pixels):
    """Writes 16x16 RGBA pixels (a list of 16 rows of (r, g, b, a)) as a PNG."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def rgb(color, shade=1.0):
    r, g, b = (color >> 16) & 255, (color >> 8) & 255, color & 255
    return (min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)), 255)


def ore(color, seed):
    rng = random.Random(seed)
    pixels = [[rgb(0x7F7F7F, rng.uniform(0.85, 1.1)) for _ in range(16)] for _ in range(16)]
    for _ in range(5):  # Clusters of colored specks.
        cx, cy = rng.randint(2, 13), rng.randint(2, 13)
        for dx, dy in [(0, 0), (1, 0), (0, 1), (-1, 0), (1, 1)]:
            if rng.random() < 0.85:
                pixels[cy + dy][cx + dx] = rgb(color, rng.uniform(0.8, 1.15))
    return pixels


def storage_block(color):
    pixels = [[rgb(color, 0.95) for _ in range(16)] for _ in range(16)]
    for i in range(16):
        for edge in (0, 15):
            pixels[edge][i] = rgb(color, 0.65)
            pixels[i][edge] = rgb(color, 0.65)
    for i in range(2, 14):
        pixels[2][i] = rgb(color, 1.2)
    return pixels


def raw_chunk(color, seed):
    rng = random.Random(seed)
    clear = (0, 0, 0, 0)
    pixels = [[clear] * 16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 / 30 + (y - 8.5) ** 2 / 22 <= 1 + rng.uniform(-0.15, 0.15):
                pixels[y][x] = rgb(color, rng.uniform(0.7, 1.05))
    return pixels


def ingot(color):
    clear = (0, 0, 0, 0)
    pixels = [[clear] * 16 for _ in range(16)]
    for y in range(5, 11):
        for x in range(2 + (10 - y) // 2, 14 - (y - 5) // 2):
            pixels[y][x] = rgb(color, 1.15 if y == 5 else (0.7 if y == 10 else 0.95))
    return pixels


for key, color in ORES.items():
    png(ASSETS / "block" / f"{key}_dungeon_ore.png", ore(color, key))
    png(ASSETS / "block" / f"{key}_dungeon_block.png", storage_block(color))
    png(ASSETS / "item" / f"raw_{key}_dungeon_ore.png", raw_chunk(color, key))
    png(ASSETS / "item" / f"{key}_dungeon_ingot.png", ingot(color))
print("Wrote placeholder textures for", ", ".join(ORES))
