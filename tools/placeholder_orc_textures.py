"""Draws placeholder art for the orcs: the warband (scrapper, raider, veteran, vagabond, rampager), the casters
(doomsayer, shaman, Lorekeeper) and the bosses (Commander, Chieftain, Warchief).

Run from the project root: python tools/placeholder_orc_textures.py
Uses the vanilla humanoid (zombie/player) layout, like the goblins, so it fits ArmedHumanoidModel and Iron's caster
body until orcs get their own model. Original art, not derived from Minecraft's textures.
"""
import random
import struct
import zlib
from pathlib import Path

ENTITY = Path("src/main/resources/assets/dungeondelving/textures/entity")
rng = random.Random(7474)

SKIN = (104, 122, 88)
SKIN_DARK = (78, 94, 64)
EYE = (210, 60, 30)
MOUTH = (34, 26, 22)
TUSK = (232, 222, 196)
LEATHER = (96, 66, 40)
LEATHER_DARK = (66, 44, 26)
HIDE = (120, 98, 70)
TROUSERS = (70, 62, 52)
IRON = (120, 122, 128)
IRON_DARK = (84, 86, 92)
GOLD = (212, 170, 60)
BLOOD = (128, 24, 24)


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


def orc_body():
    body = {}
    # Head: grey-green skin, a heavy brow, red eyes and two tusks jutting up from a wide jaw.
    box(body, 0, 0, 8, 8, 8, SKIN)
    fill(body, 8, 0, 16, 8, SKIN_DARK)
    fill(body, 8, 8, 16, 11, SKIN_DARK, 6)  # brow ridge
    for x in (9, 10, 13, 14):
        body[(x, 11)] = MOUTH + (255,)
    body[(10, 11)] = EYE + (255,)
    body[(13, 11)] = EYE + (255,)
    body[(11, 12)] = shade(SKIN_DARK, -12) + (255,)
    body[(12, 12)] = shade(SKIN_DARK, -12) + (255,)
    for x in range(9, 15):
        body[(x, 14)] = MOUTH + (255,)
    for x, y in [(9, 13), (9, 12), (14, 13), (14, 12)]:
        body[(x, y)] = TUSK + (255,)
    # Body: bare chest under a hide harness, with a belt.
    box(body, 16, 16, 8, 12, 4, SKIN)
    for i in range(8):
        body[(20 + i, 20 + i)] = LEATHER_DARK + (255,)
        body[(27 - i, 20 + i)] = LEATHER_DARK + (255,)
    fill(body, 16, 28, 40, 30, LEATHER_DARK, 6)
    # Arms: bare, with leather bracers.
    for u, v in [(40, 16), (32, 48)]:
        box(body, u, v, 4, 12, 4, SKIN)
        fill(body, u, v + 4 + 8, u + 16, v + 4 + 12, LEATHER, 6)
    # Legs: dark trousers and hide boots.
    for u, v in [(0, 16), (16, 48)]:
        box(body, u, v, 4, 12, 4, TROUSERS)
        fill(body, u, v + 4 + 8, u + 16, v + 4 + 12, HIDE, 8)
    return body


def copy(body):
    return dict(body)


def hood(body, color):
    fill(body, 8, 0, 16, 8, color)
    fill(body, 0, 8, 8, 16, color)
    fill(body, 16, 8, 24, 16, color)
    fill(body, 24, 8, 32, 16, shade(color, -20))
    fill(body, 8, 8, 16, 10, color)


def robe(body, color, trim):
    box(body, 16, 16, 8, 12, 4, color)
    fill(body, 16, 28, 40, 30, trim, 6)
    for u, v in [(0, 16), (16, 48)]:
        box(body, u, v, 4, 12, 4, color)
        fill(body, u, v + 4 + 10, u + 16, v + 4 + 12, shade(color, -25), 6)


def warpaint(body, color):
    for x in range(8, 16):
        body[(x, 10)] = color + (255,)
    for y in range(12, 16):
        body[(10, y)] = color + (255,)
        body[(13, y)] = color + (255,)


def cape(body, color):
    fill(body, 32, 20, 40, 32, color, 4)


def scars(body):
    for x, y in [(9, 9), (10, 10), (11, 11), (14, 9), (13, 10)]:
        body[(x, y)] = (176, 150, 130, 255)


scrapper = orc_body()
fill(scrapper, 20, 20, 28, 28, HIDE, 8)  # a scrap of hide over the chest
png(ENTITY / "orc_scrapper.png", 64, 64, scrapper)

raider = orc_body()
warpaint(raider, (40, 40, 44))
png(ENTITY / "orc_raider.png", 64, 64, raider)

veteran = orc_body()
scars(veteran)
fill(veteran, 16, 28, 40, 30, IRON_DARK, 4)
png(ENTITY / "orc_veteran.png", 64, 64, veteran)

vagabond = orc_body()
hood(vagabond, (78, 64, 48))
cape(vagabond, (70, 56, 40))
png(ENTITY / "orc_vagabond.png", 64, 64, vagabond)

rampager = orc_body()
warpaint(rampager, BLOOD)
fill(rampager, 22, 20, 26, 26, shade(BLOOD, 20), 12)  # a bloody handprint on the chest
png(ENTITY / "orc_rampager.png", 64, 64, rampager)

doomsayer = orc_body()
robe(doomsayer, (74, 22, 22), (40, 12, 12))
warpaint(doomsayer, (20, 10, 10))
png(ENTITY / "orc_doomsayer.png", 64, 64, doomsayer)

shaman = orc_body()
robe(shaman, (46, 82, 128), (30, 54, 90))
for x, y in [(11, 0), (12, 1), (11, 2), (12, 3)]:
    shaman[(x, y)] = (240, 230, 120, 255)  # a lightning mark painted on the scalp
png(ENTITY / "orc_shaman.png", 64, 64, shaman)

lorekeeper = orc_body()
robe(lorekeeper, (52, 40, 64), GOLD)
hood(lorekeeper, (52, 40, 64))
for x in range(9, 15):
    lorekeeper[(x, 15)] = (200, 200, 190, 255)  # a long pale beard
png(ENTITY / "orc_lorekeeper.png", 64, 64, lorekeeper)

commander = copy(veteran)
fill(commander, 20, 20, 28, 30, (128, 32, 28), 6)  # red tabard
fill(commander, 8, 8, 16, 9, IRON, 4)
png(ENTITY / "orc_commander.png", 64, 64, commander)

chieftain = orc_body()
scars(chieftain)
fill(chieftain, 8, 8, 16, 9, GOLD, 4)  # gold band across the brow
cape(chieftain, HIDE)
png(ENTITY / "orc_chieftain.png", 64, 64, chieftain)

warchief = copy(chieftain)
warpaint(warchief, BLOOD)
cape(warchief, (110, 20, 20))
fill(warchief, 16, 28, 40, 30, GOLD, 4)
png(ENTITY / "orc_warchief.png", 64, 64, warchief)

print("Wrote the orc textures")
