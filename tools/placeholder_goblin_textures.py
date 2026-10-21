"""Draws placeholder art for the goblin: its 64x64 body texture and the 16x16 Goblin Spear.

Run from the project root: python tools/placeholder_goblin_textures.py
The body texture uses the vanilla humanoid (zombie/player) layout, so it fits the model GoblinRenderer uses until a
custom goblin model replaces it. Original art, not derived from Minecraft's textures.
"""
import random
import struct
import zlib
from pathlib import Path

ENTITY = Path("src/main/resources/assets/dungeondelving/textures/entity")
ITEM = Path("src/main/resources/assets/dungeondelving/textures/item")
rng = random.Random(1337)

SKIN = (94, 140, 58)
SKIN_DARK = (70, 108, 42)
LEATHER = (107, 74, 43)
LEATHER_DARK = (80, 54, 30)
CLOTH = (128, 96, 60)
EYE = (232, 200, 48)
PUPIL = (120, 20, 20)
MOUTH = (40, 30, 24)
TOOTH = (230, 225, 200)


def png(path, width, height, pixels):
    """Writes RGBA pixels (a dict of (x, y) -> (r, g, b, a)) as a PNG; unset pixels are transparent."""
    rows = b""
    for y in range(height):
        row = bytearray([0])
        for x in range(width):
            row += bytes(pixels.get((x, y), (0, 0, 0, 0)))
        rows += bytes(row)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color)


def fill(pixels, x1, y1, x2, y2, color, noise=10):
    for y in range(y1, y2):
        for x in range(x1, x2):
            pixels[(x, y)] = shade(color, rng.randint(-noise, noise)) + (255,)


def box(pixels, u, v, w, h, d, color, noise=10):
    """Paints every face of a model box whose texture starts at (u, v), sized w x h x d."""
    fill(pixels, u + d, v, u + d + w + w, v + d, color, noise)  # top and bottom
    fill(pixels, u, v + d, u + d + w + d + w, v + d + h, color, noise)  # four sides


body = {}
# Head (8x8x8 at 0,0): green skin, darker on top, with a face on the front (8..16, 8..16).
box(body, 0, 0, 8, 8, 8, SKIN)
fill(body, 8, 0, 16, 8, SKIN_DARK)
for x, y in [(9, 11), (10, 11), (13, 11), (14, 11)]:
    body[(x, y)] = EYE + (255,)
body[(10, 11)] = PUPIL + (255,)
body[(13, 11)] = PUPIL + (255,)
for x in range(9, 15):
    body[(x, 10)] = shade(SKIN_DARK, -15) + (255,)  # heavy brow
for x in range(10, 14):
    body[(x, 14)] = MOUTH + (255,)
body[(10, 13)] = TOOTH + (255,)
body[(13, 13)] = TOOTH + (255,)
body[(11, 12)] = shade(SKIN_DARK, -10) + (255,)  # nose
body[(12, 12)] = shade(SKIN_DARK, -10) + (255,)

# Body (8x12x4 at 16,16): leather vest over green skin, with a belt.
box(body, 16, 16, 8, 12, 4, LEATHER)
fill(body, 22, 20, 26, 26, SKIN, 8)  # bare chest showing through the open vest
fill(body, 16, 28, 40, 30, LEATHER_DARK, 6)  # belt
# Arms (4x12x4 at 40,16 and the left arm at 32,48): bare green arms with leather wraps at the wrist.
for u, v in [(40, 16), (32, 48)]:
    box(body, u, v, 4, 12, 4, SKIN)
    fill(body, u, v + 4 + 9, u + 16, v + 4 + 12, LEATHER_DARK, 6)
# Legs (4x12x4 at 0,16 and the left leg at 16,48): cloth trousers to the knee, bare feet.
for u, v in [(0, 16), (16, 48)]:
    box(body, u, v, 4, 12, 4, CLOTH)
    fill(body, u, v + 4 + 7, u + 16, v + 4 + 12, SKIN_DARK, 8)
png(ENTITY / "goblin.png", 64, 64, body)

# Goblin Spear: a short wooden shaft from the bottom left, with a chipped flint head at the top right.
spear = {}
SHAFT = (122, 86, 50)
SHAFT_DARK = (84, 58, 32)
HEAD = (110, 110, 118)
HEAD_LIGHT = (170, 170, 180)
WRAP = (70, 46, 26)
for i in range(10):
    x, y = 2 + i, 13 - i
    spear[(x, y)] = SHAFT + (255,)
    spear[(x + 1, y)] = SHAFT_DARK + (255,)
for x, y in [(7, 8), (8, 7)]:
    spear[(x, y)] = WRAP + (255,)
    spear[(x + 1, y)] = WRAP + (255,)
for x, y in [(11, 4), (12, 3), (13, 2), (12, 4), (13, 3), (11, 3), (12, 2), (14, 1), (13, 1), (14, 2)]:
    spear[(x, y)] = HEAD + (255,)
for x, y in [(12, 3), (13, 2), (14, 1)]:
    spear[(x, y)] = HEAD_LIGHT + (255,)
png(ITEM / "goblin_spear.png", 16, 16, spear)
print("Wrote goblin.png and goblin_spear.png")
