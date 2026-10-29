"""Recolours the Kobolds! mod's kobold skins into dragon colours, for Dungeon Delving's kobolds.

Run from the project root, with the Kobolds! jar (4.0.2, from the Gradle cache or the pack):
    python tools/kobold_dragon_skins.py path/to/kobolds-484967-9257299.jar

Kobolds! (MIT License, Copyright (c) 2024 Salju) draws every kobold with the same dark grey scales; only the outfits
differ. Its scales are pure greys (red = green = blue), while its metal is faintly blue and its leather, horns and eyes
are coloured, so each pure grey in the scales' range is mapped onto a dragon colour's ramp, dark to light, keeping the
shading. Black kobolds keep the original skins. The results go to textures/entity/kobold/<skin>_<colour>.png, with the
licence beside them (see KOBOLDS_LICENSE.txt).
"""
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import pngio  # noqa: E402

OUT = Path("src/main/resources/assets/dungeondelving/textures/entity/kobold")
SKINS = ("base", "engineer", "warrior")
# The scales' greys: darker is outline or goggles, lighter is metal.
SCALE_MIN, SCALE_MAX = 36, 95
# Each dragon colour's ramp, from the darkest scale to the lightest. Keep in sync with KoboldsModRenderer.COLOURS.
COLOURS = {
    "red": ((82, 18, 14), (196, 54, 38)),
    "blue": ((22, 40, 104), (64, 116, 206)),
    "green": ((22, 70, 28), (78, 156, 64)),
    "white": ((142, 152, 164), (226, 234, 240)),
    "brass": ((112, 78, 28), (214, 170, 74)),
    "copper": ((104, 46, 22), (204, 114, 62)),
}


def recolour(rows, dark, light):
    out = []
    for row in rows:
        line = []
        for r, g, b, a in row:
            if a and r == g == b and SCALE_MIN <= r <= SCALE_MAX:
                t = (r - SCALE_MIN) / (SCALE_MAX - SCALE_MIN)
                line.append(tuple(round(d + (l - d) * t) for d, l in zip(dark, light)) + (a,))
            else:
                line.append((r, g, b, a))
        out.append(line)
    return out


def main(jar):
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as kobolds:
        for skin in SKINS:
            rows = pngio.decode(kobolds.read(f"assets/kobolds/textures/entity/kobolds/{skin}.png"))
            for colour, (dark, light) in COLOURS.items():
                pngio.write(OUT / f"{skin}_{colour}.png", recolour(rows, dark, light))
    print(f"Wrote {len(SKINS) * len(COLOURS)} kobold skins to {OUT}")


if __name__ == "__main__":
    main(sys.argv[1])
