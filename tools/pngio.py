"""Minimal PNG reading and writing for the texture tools, with no dependencies.

Reads 8-bit RGBA (colour type 6), RGB (2) and paletted (3) images, and writes RGBA. Pixels are a list of rows of
(r, g, b, a) tuples.
"""
import struct
import zlib


def read(path):
    return decode(open(path, "rb").read())


def decode(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, palette, alpha = 8, b"", [], []
    width = height = kind = 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, kind = struct.unpack(">IIBB", body[:10])
            assert depth == 8 and body[12] == 0, "only 8-bit, non-interlaced images"
        elif tag == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif tag == b"tRNS":
            alpha = list(body)
        elif tag == b"IDAT":
            idat += body
        pos += 12 + length
    size = {6: 4, 2: 3, 3: 1}[kind]
    raw = zlib.decompress(idat)
    stride = width * size
    rows, prev = [], bytearray(stride)
    for y in range(height):
        kind_filter = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - size] if i >= size else 0
            b = prev[i]
            c = prev[i - size] if i >= size else 0
            if kind_filter == 1:
                line[i] = (line[i] + a) & 255
            elif kind_filter == 2:
                line[i] = (line[i] + b) & 255
            elif kind_filter == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind_filter == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        row = []
        for x in range(width):
            if kind == 3:
                index = line[x]
                row.append(palette[index] + ((alpha[index] if index < len(alpha) else 255),))
            elif kind == 2:
                row.append(tuple(line[x * 3:x * 3 + 3]) + (255,))
            else:
                row.append(tuple(line[x * 4:x * 4 + 4]))
        rows.append(row)
    return rows


def write(path, rows):
    height, width = len(rows), len(rows[0])
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in rows)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
