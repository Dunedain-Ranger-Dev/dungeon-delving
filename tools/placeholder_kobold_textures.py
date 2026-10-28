"""Draws placeholder art for the kobolds: the looter and the scavenger.

Run from the project root: python tools/placeholder_kobold_textures.py
Uses the vanilla humanoid (zombie/player) layout, like the goblins and orcs, so it fits ArmedHumanoidModel until
kobolds get their own model. Original art, not derived from Minecraft's textures.
"""
import random
import struct
import zlib
from pathlib import Path

ENTITY = Path("src/main/resources/assets/dungeondelving/textures/entity")
rng = random.Random(5454)

SCALES = (160, 82, 45)
SCALES_DARK = (118, 58, 32)
BELLY = (196, 150, 96)
EYE = (240, 200, 40)
PUPIL = (30, 20, 10)
HORN = (220, 210, 180)
LEATHER = (92, 64, 40)
LEATHER_DARK = (62, 42, 26)
CLOTH = (110, 104, 84)
BONE = (226, 218, 196)


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
    fill(pixels, u + d, v, u + d + w + w, v + d, color, noise)
    fill(pixels, u, v + d, u + d + w + d + w, v + d + h, color, noise)


def scaled(pixels, u, v, w, h, d):
    """A box of scales, with a darker scale pattern every other pixel."""
    box(pixels, u, v, w, h, d, SCALES, 14)
    for y in range(v, v + d + h):
        for x in range(u, u + 2 * (w + d)):
            if (x + y) % 3 == 0 and (x, y) in pixels:
                pixels[(x, y)] = shade(SCALES_DARK, rng.randint(-6, 6)) + (255,)


def kobold_body():
    body = {}
    # Head: rusty scales, a pale snout, slit yellow eyes and two little horns on top.
    scaled(body, 0, 0, 8, 8, 8)
    fill(body, 9, 12, 15, 16, BELLY, 8)  # snout
    for x in (10, 13):
        body[(x, 13)] = (40, 24, 16, 255)  # nostrils
    for x in (9, 14):
        body[(x, 10)] = EYE + (255,)
    for x in (10, 13):
        body[(x, 10)] = PUPIL + (255,)
    for x, y in [(9, 0), (10, 1), (13, 1), (14, 0)]:
        body[(x, y)] = HORN + (255,)
    # Body: scales with a pale belly.
    scaled(body, 16, 16, 8, 12, 4)
    fill(body, 21, 21, 27, 32, BELLY, 8)
    # Arms and legs: scales, with dark claws at the ends.
    for u, v in [(40, 16), (32, 48), (0, 16), (16, 48)]:
        scaled(body, u, v, 4, 12, 4)
        fill(body, u, v + 4 + 11, u + 16, v + 4 + 12, SCALES_DARK, 4)
    return body


looter = kobold_body()
# A stolen leather vest and belt.
fill(looter, 20, 20, 22, 32, LEATHER, 6)
fill(looter, 26, 20, 28, 32, LEATHER, 6)
fill(looter, 16, 28, 40, 30, LEATHER_DARK, 6)
png(ENTITY / "kobold_looter.png", 64, 64, looter)

scavenger = kobold_body()
# A ragged cloth hood and a bone necklace.
fill(scavenger, 8, 0, 16, 8, CLOTH)
fill(scavenger, 0, 8, 8, 16, CLOTH)
fill(scavenger, 16, 8, 24, 16, CLOTH)
fill(scavenger, 24, 8, 32, 16, shade(CLOTH, -20))
fill(scavenger, 8, 8, 16, 9, CLOTH)
for x in range(20, 28, 2):
    scavenger[(x, 21)] = BONE + (255,)
png(ENTITY / "kobold_scavenger.png", 64, 64, scavenger)

print("Wrote the kobold textures")
