"""Draws placeholder art for the goblins (warrior, archer, shaman, pyro, gladiator), the hobgoblins, and their weapons.

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


def goblin_body():
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
    return body


HOOD = (62, 92, 46)
HOOD_DARK = (44, 66, 32)
STRAP = (70, 46, 26)
ROBE = (92, 52, 120)
ROBE_DARK = (66, 36, 88)
BONE = (226, 218, 190)
BONE_DARK = (180, 170, 140)
FEATHER = (190, 50, 40)

png(ENTITY / "goblin.png", 64, 64, goblin_body())

# Archer: a green hood over the head (top, back and sides), and a quiver strap across the vest.
archer = goblin_body()
fill(archer, 8, 0, 16, 8, HOOD)  # top of the head
fill(archer, 0, 8, 8, 16, HOOD)  # right side
fill(archer, 16, 8, 24, 16, HOOD)  # left side
fill(archer, 24, 8, 32, 16, HOOD_DARK)  # back
fill(archer, 8, 8, 16, 10, HOOD)  # hood brim over the brow
for i in range(8):
    archer[(20 + i, 20 + i if 20 + i < 32 else 31)] = STRAP + (255,)  # strap across the chest
fill(archer, 32, 20, 40, 32, STRAP, 6)  # quiver on the back
png(ENTITY / "goblin_archer.png", 64, 64, archer)

# Shaman: a purple robe over the body and legs, a bone mask over the face, and a red feather.
shaman = goblin_body()
box(shaman, 16, 16, 8, 12, 4, ROBE)
fill(shaman, 16, 28, 40, 30, ROBE_DARK, 6)
for u, v in [(0, 16), (16, 48)]:
    box(shaman, u, v, 4, 12, 4, ROBE)
    fill(shaman, u, v + 4 + 10, u + 16, v + 4 + 12, ROBE_DARK, 6)
fill(shaman, 9, 9, 15, 13, BONE, 6)  # mask
for x, y in [(10, 11), (13, 11)]:
    shaman[(x, y)] = (20, 10, 30, 255)  # eye holes
shaman[(11, 12)] = BONE_DARK + (255,)
shaman[(12, 12)] = BONE_DARK + (255,)
for y in range(0, 4):
    shaman[(14, y)] = FEATHER + (255,)  # feather on top of the head
    shaman[(15, y + 1)] = shade(FEATHER, -30) + (255,)
png(ENTITY / "goblin_shaman.png", 64, 64, shaman)

# Chief: the shaman's mask and robe, dyed red, with a tall gold headdress.
CHIEF_ROBE = (140, 36, 32)
CHIEF_ROBE_DARK = (100, 24, 22)
GOLD_TRIM = (232, 186, 60)
chief = {k: v for k, v in shaman.items()}
box(chief, 16, 16, 8, 12, 4, CHIEF_ROBE)
fill(chief, 16, 28, 40, 30, GOLD_TRIM, 6)
for u, v in [(0, 16), (16, 48)]:
    box(chief, u, v, 4, 12, 4, CHIEF_ROBE)
    fill(chief, u, v + 4 + 10, u + 16, v + 4 + 12, CHIEF_ROBE_DARK, 6)
fill(chief, 8, 0, 16, 8, GOLD_TRIM, 10)  # headdress on top of the head
fill(chief, 8, 8, 16, 9, GOLD_TRIM, 6)  # gold band across the brow
for x in range(9, 15, 2):
    chief[(x, 8)] = (240, 60, 60, 255)  # red gems in the band
png(ENTITY / "goblin_chief.png", 64, 64, chief)

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
# Goblin Bow: a crooked, knotty stave with a frayed string, plus three drawing frames.
WOOD = (110, 76, 44)
WOOD_DARK = (78, 52, 28)
KNOT = (60, 40, 22)
STRING = (200, 200, 190)


def bow(pull):
    image = {}
    stave = [(3, 14), (4, 13), (5, 13), (6, 12), (7, 11), (8, 10), (9, 9), (10, 8), (11, 7), (12, 6), (12, 5), (13, 4), (13, 3), (14, 2), (13, 1)]
    # The stave curves from bottom left to top right; bend it a little more as it's drawn.
    for i, (x, y) in enumerate(stave):
        bend = pull if 4 <= i <= 10 else 0
        image[(x - bend, y - bend)] = (WOOD if i % 3 else WOOD_DARK) + (255,)
    image[(7 - pull, 11 - pull)] = KNOT + (255,)
    image[(10 - pull, 8 - pull)] = KNOT + (255,)
    # The string runs straight between the tips, pulled back toward the bottom left as it's drawn.
    for t in range(12):
        x = 3 + t * 10 // 11
        y = 14 - t * 13 // 11
        image.setdefault((x - pull, y + pull), STRING + (255,))
    return image


png(ITEM / "goblin_bow.png", 16, 16, bow(0))
for frame in range(3):
    png(ITEM / f"goblin_bow_pulling_{frame}.png", 16, 16, bow(frame + 1))


def staff(head, gem, length):
    image = {}
    for i in range(length):
        x, y = 2 + i, 13 - i
        image[(x, y)] = (WOOD if i % 2 else WOOD_DARK) + (255,)
    tip_x, tip_y = 2 + length, 13 - length
    for dx, dy in [(0, 0), (1, 0), (0, -1), (1, -1), (-1, 0), (0, 1)]:
        image[(tip_x + dx, tip_y + dy)] = head + (255,)
    image[(tip_x, tip_y - 1)] = gem + (255,)
    image[(tip_x + 1, tip_y)] = gem + (255,)
    return image


BONE_HEAD = (226, 218, 190)
png(ITEM / "goblin_totem_staff.png", 16, 16, staff(BONE_HEAD, (120, 200, 90), 9))  # a bone fetish with a green glow
png(ITEM / "goblin_chief_staff.png", 16, 16, staff((232, 186, 60), (240, 60, 60), 11))  # gold-capped, with a red gem

# --- DD-73: the pyro, the gladiator and the hobgoblins (appended, so the textures above stay the same) ---

# Pyro: the shaman's robe in fire colors, with a flame on the crown of the head.
PYRO_ROBE = (196, 92, 30)
PYRO_ROBE_DARK = (150, 60, 20)
FLAME = (250, 190, 60)
pyro = {k: v for k, v in shaman.items()}
box(pyro, 16, 16, 8, 12, 4, PYRO_ROBE)
fill(pyro, 16, 28, 40, 30, PYRO_ROBE_DARK, 6)
for u, v in [(0, 16), (16, 48)]:
    box(pyro, u, v, 4, 12, 4, PYRO_ROBE)
    fill(pyro, u, v + 4 + 10, u + 16, v + 4 + 12, PYRO_ROBE_DARK, 6)
for x, y in [(11, 1), (12, 0), (12, 1), (13, 2), (11, 3), (12, 2), (12, 3), (13, 3)]:
    pyro[(x, y)] = (FLAME if y < 2 else FEATHER) + (255,)
png(ENTITY / "goblin_pyro.png", 64, 64, pyro)

# Gladiator: a goblin pit-fighter, bare-chested with crossed leather straps and studded wrist guards.
STUD = (180, 180, 186)
gladiator = goblin_body()
fill(gladiator, 20, 20, 28, 32, SKIN, 8)  # bare chest
for i in range(8):
    gladiator[(20 + i, 20 + i if 20 + i < 32 else 31)] = STRAP + (255,)
    gladiator[(27 - i, 20 + i if 20 + i < 32 else 31)] = STRAP + (255,)
for u, v in [(40, 16), (32, 48)]:
    for x in range(u, u + 16, 3):
        gladiator[(x, v + 4 + 10)] = STUD + (255,)
png(ENTITY / "goblin_gladiator.png", 64, 64, gladiator)


def hobgoblin_body():
    """The goblin body with a hobgoblin's ruddy orange skin."""
    global SKIN, SKIN_DARK
    saved = (SKIN, SKIN_DARK)
    SKIN, SKIN_DARK = (184, 96, 52), (142, 68, 36)
    body = goblin_body()
    SKIN, SKIN_DARK = saved
    return body


def tabard(body, color, trim):
    fill(body, 20, 20, 28, 32, color, 6)  # front of the body
    fill(body, 32, 20, 40, 32, color, 6)  # back
    fill(body, 20, 30, 28, 32, trim, 4)


TABARD_RED = (150, 38, 34)
TABARD_GREY = (82, 84, 92)
GOLD = (220, 176, 60)
WARPAINT = (90, 40, 140)

soldier = hobgoblin_body()
tabard(soldier, TABARD_RED, LEATHER_DARK)
png(ENTITY / "hobgoblin_soldier.png", 64, 64, soldier)

hunter = hobgoblin_body()
fill(hunter, 8, 0, 16, 8, LEATHER)
fill(hunter, 0, 8, 8, 16, LEATHER)
fill(hunter, 16, 8, 24, 16, LEATHER)
fill(hunter, 24, 8, 32, 16, LEATHER_DARK)
fill(hunter, 8, 8, 16, 10, LEATHER)
png(ENTITY / "hobgoblin_hunter.png", 64, 64, hunter)

vanguard = hobgoblin_body()
tabard(vanguard, TABARD_GREY, (50, 52, 58))
png(ENTITY / "hobgoblin_vanguard.png", 64, 64, vanguard)

spellbreaker = hobgoblin_body()
for x in range(8, 16):
    spellbreaker[(x, 12)] = WARPAINT + (255,)  # a stripe of warpaint across the eyes
for y in range(9, 16):
    spellbreaker[(11, y)] = WARPAINT + (255,)
fill(spellbreaker, 20, 20, 28, 32, (60, 44, 70), 6)
png(ENTITY / "hobgoblin_spellbreaker.png", 64, 64, spellbreaker)

veteran = {k: v for k, v in vanguard.items()}
for x, y in [(9, 9), (10, 10), (11, 11), (13, 13), (14, 14)]:
    veteran[(x, y)] = (220, 170, 140, 255)  # old scars across the face
fill(veteran, 20, 30, 28, 32, GOLD, 4)
png(ENTITY / "hobgoblin_veteran.png", 64, 64, veteran)

general = {k: v for k, v in soldier.items()}
fill(general, 32, 20, 40, 32, (170, 30, 30), 4)  # a red cape down the back
fill(general, 8, 8, 16, 9, GOLD, 4)  # a gold band across the brow
png(ENTITY / "hobgoblin_general.png", 64, 64, general)

print("Wrote the goblin, archer, shaman, pyro, chief, gladiator and hobgoblin textures, the Goblin Spear, the Goblin Bow, "
      "and the goblin staves")
