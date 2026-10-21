"""Draws placeholder art for the Runic Keys (I to IV) and Runic Shards (I to III).

Run from the project root: python tools/placeholder_runic_textures.py
Each key is tinted like the metal it's made from (iron, Barrow Iron, Thraggite, Wardsilver), with a glowing rune in
its bow. Each shard is tinted like the tier its boss guards. Original art, not derived from Minecraft's textures.
"""
import struct
import zlib
from pathlib import Path

ITEM = Path("src/main/resources/assets/dungeondelving/textures/item")

KEY_METALS = {1: (190, 190, 196), 2: (200, 205, 214), 3: (79, 199, 90), 4: (79, 168, 224)}
KEY_RUNES = {1: (255, 210, 120), 2: (160, 220, 255), 3: (200, 255, 170), 4: (190, 240, 255)}
SHARD_COLORS = {1: (79, 199, 90), 2: (79, 168, 224), 3: (164, 91, 216)}


def png(path, pixels):
    rows = b""
    for y in range(16):
        row = bytearray([0])
        for x in range(16):
            row += bytes(pixels.get((x, y), (0, 0, 0, 0)))
        rows += bytes(row)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color) + (255,)


def key(tier):
    metal = KEY_METALS[tier]
    image = {}
    # The bow: a ring at the top left.
    ring = [(3, 1), (4, 1), (5, 1), (2, 2), (6, 2), (1, 3), (7, 3), (1, 4), (7, 4), (1, 5), (7, 5), (2, 6), (6, 6), (3, 7), (4, 7), (5, 7)]
    for x, y in ring:
        image[(x, y)] = shade(metal, 20 if y < 4 else -20)
    # A glowing rune in the middle of the bow.
    for x, y in [(4, 3), (3, 4), (4, 4), (5, 4), (4, 5)]:
        image[(x, y)] = KEY_RUNES[tier] + (255,)
    # The shaft runs diagonally down to the bottom right.
    for i in range(8):
        image[(7 + i, 7 + i)] = shade(metal, 0)
        image[(6 + i, 7 + i)] = shade(metal, -35)
    # Teeth, more of them on higher keys.
    for i in range(min(tier + 1, 4)):
        x, y = 11 + i // 2 - i % 2, 13 - i * 2 + i // 2
        image[(x, y)] = shade(metal, -10)
        image[(x - 1, y)] = shade(metal, -40)
    return image


def shard(tier):
    color = SHARD_COLORS[tier]
    image = {}
    outline = [(7, 1), (8, 2), (9, 3), (10, 5), (10, 7), (9, 9), (9, 11), (8, 13), (7, 14), (6, 12), (5, 10), (5, 8), (5, 6), (6, 4), (6, 2)]
    for x, y in outline:
        image[(x, y)] = shade(color, -50)
    for y in range(2, 14):
        for x in range(6, 10):
            if (x, y) not in image and 5 < x < 10 and not (y > 11 and x > 8) and not (y < 4 and x > 8):
                image[(x, y)] = shade(color, 25 if x == 7 else -5)
    for x, y in [(7, 4), (7, 6), (8, 7), (7, 9)]:
        image[(x, y)] = (255, 255, 240, 255)  # a bright rune line down the middle
    return image


for t in KEY_METALS:
    png(ITEM / f"runic_key_{t}.png", key(t))
for t in SHARD_COLORS:
    png(ITEM / f"runic_shard_{t}.png", shard(t))
print("Wrote Runic Keys I-IV and Runic Shards I-III")
