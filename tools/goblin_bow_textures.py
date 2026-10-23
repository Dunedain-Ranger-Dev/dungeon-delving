"""Makes the Goblin Bow textures from vanilla's bow: the same shape, made cruder, like a bent branch.

Run from the project root, after a Gradle build or run (it reads vanilla's textures from the Minecraft resources jar
that ModDevGradle downloads): python tools/goblin_bow_textures.py

- The stave's smooth browns become rough, mottled bark, with knots and a couple of twig stubs off its back (one with
  a leaf still on it).
- The grip wrap becomes tied grass, and the string frayed twine.
- The arrow in the drawing frames shares the stave's colors, so it comes out as a rough stick too.

Like tools/vanilla_recolor_textures.py, these are edits of Mojang's art, so they aren't covered by this project's All
Rights Reserved asset license the way original art is. Needs Pillow (pip install pillow).
"""
import io
import random
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ITEM = ROOT / "src/main/resources/assets/dungeondelving/textures/item"
VANILLA_JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"

# Vanilla's bow colors, and what each becomes.
EDGE = (73, 54, 21)       # the stave's outer edge
LIGHT = (137, 103, 39)    # its lit face
MID = (104, 78, 30)       # its face
SHADOW = (40, 30, 11)     # its inner edge
GRIP = {(107, 107, 107), (150, 150, 150)}
STRING = (68, 68, 68)

BARK = {EDGE: (70, 58, 44), LIGHT: (122, 100, 72), MID: (96, 78, 56), SHADOW: (44, 36, 26)}
KNOT = (52, 40, 28)
TWIG = (84, 68, 48)
LEAF = (88, 120, 52)
WRAP = (150, 140, 84)
WRAP_DARK = (112, 104, 60)
TWINE = (168, 150, 110)
TWINE_DARK = (124, 108, 76)


def crude(image, seed):
    rng = random.Random(seed)
    out = image.copy()
    px = out.load()
    edges = []
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            color = (r, g, b)
            if color in BARK:
                base = BARK[color]
                noise = rng.randint(-12, 10)
                px[x, y] = tuple(max(0, min(255, c + noise)) for c in base) + (255,)
                if color == EDGE:
                    edges.append((x, y))
            elif color in GRIP:
                px[x, y] = (WRAP if (x + y) % 2 else WRAP_DARK) + (255,)
            elif color == STRING:
                # Frayed: mostly pale twine, darker where it's twisted.
                px[x, y] = (TWINE_DARK if rng.random() < 0.3 else TWINE) + (255,)
    # Knots and twig stubs along the stave's back (its outer, upper-left edge).
    edges.sort(key=lambda p: p[0] - p[1])
    if len(edges) >= 6:
        for share in (0.3, 0.7):
            x, y = edges[int(len(edges) * share)]
            px[x, y] = KNOT + (255,)
        for share, leaf in ((0.2, True), (0.8, False)):
            x, y = edges[int(len(edges) * share)]
            for dx, dy in ((-1, -1), (-1, 0), (0, -1)):
                tx, ty = x + dx, y + dy
                if 0 <= tx < 16 and 0 <= ty < 16 and px[tx, ty][3] == 0:
                    px[tx, ty] = TWIG + (255,)
                    if leaf and 0 <= tx - 1 and 0 <= ty - 1 and px[tx - 1, ty - 1][3] == 0:
                        px[tx - 1, ty - 1] = LEAF + (255,)
                    break
    return out


def main():
    with zipfile.ZipFile(VANILLA_JAR) as jar:
        for i, name in enumerate(["bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2"]):
            image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/item/{name}.png"))).convert("RGBA")
            # One seed for every frame, so the bark doesn't flicker as it's drawn.
            crude(image, 7).save(ITEM / f"goblin_{name}.png")
    print("Wrote the Goblin Bow textures")


if __name__ == "__main__":
    main()
