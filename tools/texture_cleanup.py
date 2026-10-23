"""Turns a generated texture image (e.g. from Nano Banana) into a real Minecraft texture.

Usage:
  python tools/texture_cleanup.py <input image> <output .png> [--grid 16] [--crop PIXELS] [--warm 0.0-1.0]
                                  [--calm 0.0-1.0] [--colors 8] [--darken 0.0-1.0]

- Reads the image as a grid of big square "pixels" (--grid, default 16) and takes the color at the center of each
  cell, so soft edges and compression noise don't leak in. --crop trims a border first (in source pixels), for
  images that came with a frame.
- --warm pulls cool, blue-tinted greys toward neutral (the reference was lit by snow, for example).
- --calm pulls every color toward the average, lowering contrast so large surfaces look quieter.
- --colors reduces it to that many colors (default 8), like hand-made pixel art.
- --darken makes a darker variant of the same texture (e.g. trim from a stone texture).
- Prints whether the result tiles (how different its opposite edges are) and how many colors it uses.

Needs Pillow (pip install pillow).
"""
import argparse
import sys
from pathlib import Path

from PIL import Image


def mix(a, b, t):
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("output")
    parser.add_argument("--grid", type=int, default=16)
    parser.add_argument("--crop", type=int, default=0)
    parser.add_argument("--warm", type=float, default=0.0)
    parser.add_argument("--calm", type=float, default=0.0)
    parser.add_argument("--colors", type=int, default=8)
    parser.add_argument("--darken", type=float, default=0.0)
    args = parser.parse_args()

    source = Image.open(args.input).convert("RGB")
    if args.crop:
        source = source.crop((args.crop, args.crop, source.width - args.crop, source.height - args.crop))
    size = min(source.size)
    cell = size / args.grid
    pixels = [[source.getpixel((int((x + 0.5) * cell), int((y + 0.5) * cell))) for x in range(args.grid)] for y in range(args.grid)]

    flat = [p for row in pixels for p in row]
    average = tuple(sum(c[i] for c in flat) / len(flat) for i in range(3))
    out = Image.new("RGB", (args.grid, args.grid))
    for y, row in enumerate(pixels):
        for x, color in enumerate(row):
            if args.warm:
                grey = sum(color) / 3
                neutral = (grey + 4, grey + 1, grey - 4)  # a touch warm, like pale limestone
                color = mix(color, neutral, args.warm)
            if args.calm:
                color = mix(color, average, args.calm)
            out.putpixel((x, y), tuple(max(0, min(255, int(c))) for c in color))
    if args.colors:
        out = out.quantize(colors=args.colors, method=Image.Quantize.MEDIANCUT).convert("RGB")
    if args.darken:
        for y in range(args.grid):
            for x in range(args.grid):
                out.putpixel((x, y), mix(out.getpixel((x, y)), (0, 0, 0), args.darken))

    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    out.save(args.output)

    def edge_gap(a, b):
        return sum(abs(p - q) for pa, pb in zip(a, b) for p, q in zip(pa, pb)) / (len(a) * 3)

    g = args.grid
    horizontal = edge_gap([out.getpixel((0, y)) for y in range(g)], [out.getpixel((g - 1, y)) for y in range(g)])
    vertical = edge_gap([out.getpixel((x, 0)) for x in range(g)], [out.getpixel((x, g - 1)) for x in range(g)])
    colors = len({out.getpixel((x, y)) for x in range(g) for y in range(g)})
    print(f"wrote {args.output}: {g}x{g}, {colors} colors, edge differences {horizontal:.1f} (left/right) {vertical:.1f} (top/bottom)")
    if max(horizontal, vertical) > 25:
        print("warning: the edges differ a lot; it may show seams when tiled", file=sys.stderr)


if __name__ == "__main__":
    main()
