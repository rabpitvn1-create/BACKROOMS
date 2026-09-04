from __future__ import annotations

import argparse
import binascii
import json
import math
import shutil
import struct
import subprocess
import tempfile
import zlib
from pathlib import Path

SIZE = 128
QUALITY = 82
ROOT = Path(__file__).resolve().parent
MANIFEST = ROOT / "inventory_icon_manifest.json"
OUT_DIR = ROOT / "app/src/main/assets/inventory-icons"
RGBA = tuple[int, int, int, int]


def clamp(v: int) -> int:
    return 0 if v < 0 else 255 if v > 255 else v


class Canvas:
    def __init__(self, width: int = SIZE, height: int = SIZE):
        self.width = width
        self.height = height
        self.pixels = bytearray(width * height * 4)

    def blend(self, x: int, y: int, color: RGBA) -> None:
        if x < 0 or y < 0 or x >= self.width or y >= self.height:
            return
        r, g, b, a = color
        if a <= 0:
            return
        i = (y * self.width + x) * 4
        if a >= 255:
            self.pixels[i:i + 4] = bytes((clamp(r), clamp(g), clamp(b), 255))
            return
        dr, dg, db, da = self.pixels[i:i + 4]
        sa, dda = a / 255.0, da / 255.0
        oa = sa + dda * (1.0 - sa)
        if oa <= 0:
            return
        self.pixels[i] = clamp(round((r * sa + dr * dda * (1.0 - sa)) / oa))
        self.pixels[i + 1] = clamp(round((g * sa + dg * dda * (1.0 - sa)) / oa))
        self.pixels[i + 2] = clamp(round((b * sa + db * dda * (1.0 - sa)) / oa))
        self.pixels[i + 3] = clamp(round(oa * 255))

    def rect(self, box, color: RGBA) -> None:
        x0, y0, x1, y1 = map(int, box)
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                self.blend(x, y, color)

    def rounded_rect(self, box, radius: int, color: RGBA, outline: RGBA | None = None, width: int = 1) -> None:
        x0, y0, x1, y1 = map(int, box)
        r = max(0, min(radius, (x1 - x0) // 2, (y1 - y0) // 2))
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                cx = x0 + r if x < x0 + r else x1 - r - 1 if x >= x1 - r else x
                cy = y0 + r if y < y0 + r else y1 - r - 1 if y >= y1 - r else y
                inside = (x - cx) ** 2 + (y - cy) ** 2 <= r * r if (x != cx or y != cy) else True
                if inside:
                    self.blend(x, y, color)
        if outline and width > 0:
            self.rounded_rect((x0, y0, x1, y0 + width + r), r, outline)
            self.rounded_rect((x0, y1 - width - r, x1, y1), r, outline)
            self.rounded_rect((x0, y0, x0 + width + r, y1), r, outline)
            self.rounded_rect((x1 - width - r, y0, x1, y1), r, outline)
            if r > 0:
                self.rounded_rect((x0 + width, y0 + width, x1 - width, y1 - width), max(0, r - width), color)

    def ellipse(self, box, color: RGBA, outline: RGBA | None = None, width: int = 1) -> None:
        x0, y0, x1, y1 = map(float, box)
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        rx, ry = max((x1 - x0) / 2, 0.5), max((y1 - y0) / 2, 0.5)
        ix0, iy0, ix1, iy1 = int(math.floor(x0)), int(math.floor(y0)), int(math.ceil(x1)), int(math.ceil(y1))
        inner_rx, inner_ry = max(rx - width, 0.1), max(ry - width, 0.1)
        for y in range(max(0, iy0), min(self.height, iy1)):
            py = y + 0.5
            for x in range(max(0, ix0), min(self.width, ix1)):
                px = x + 0.5
                d = ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2
                if d <= 1.0:
                    if outline:
                        inner = ((px - cx) / inner_rx) ** 2 + ((py - cy) / inner_ry) ** 2 <= 1.0
                        self.blend(x, y, color if inner else outline)
                    else:
                        self.blend(x, y, color)

    def polygon(self, points, color: RGBA) -> None:
        pts = [(float(x), float(y)) for x, y in points]
        minx = max(0, int(math.floor(min(x for x, _ in pts))))
        maxx = min(self.width - 1, int(math.ceil(max(x for x, _ in pts))))
        miny = max(0, int(math.floor(min(y for _, y in pts))))
        maxy = min(self.height - 1, int(math.ceil(max(y for _, y in pts))))
        for y in range(miny, maxy + 1):
            py = y + 0.5
            for x in range(minx, maxx + 1):
                px = x + 0.5
                inside = False
                j = len(pts) - 1
                for i, (xi, yi) in enumerate(pts):
                    xj, yj = pts[j]
                    if ((yi > py) != (yj > py)) and (px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi):
                        inside = not inside
                    j = i
                if inside:
                    self.blend(x, y, color)

    def line(self, a, b, width: int, color: RGBA) -> None:
        x0, y0 = a
        x1, y1 = b
        dx, dy = x1 - x0, y1 - y0
        steps = max(abs(dx), abs(dy), 1)
        radius = max(1, width // 2)
        for step in range(steps + 1):
            t = step / steps
            cx, cy = x0 + dx * t, y0 + dy * t
            self.ellipse((cx - radius, cy - radius, cx + radius + 1, cy + radius + 1), color)

    def shadow(self, box=(31, 105, 97, 120)) -> None:
        self.ellipse(box, (0, 0, 0, 44))

    def write_png(self, path: Path) -> None:
        def chunk(kind: bytes, payload: bytes) -> bytes:
            crc = binascii.crc32(kind + payload) & 0xFFFFFFFF
            return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", crc)

        raw = bytearray()
        stride = self.width * 4
        for y in range(self.height):
            raw.append(0)
            start = y * stride
            raw.extend(self.pixels[start:start + stride])
        png = b"\x89PNG\r\n\x1a\n"
        png += chunk(b"IHDR", struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        png += chunk(b"IEND", b"")
        path.write_bytes(png)


DARK = (43, 50, 56, 255)
EDGE = (35, 42, 47, 255)
LIGHT_METAL = (221, 227, 230, 255)
GLASS = (226, 244, 247, 185)
HIGHLIGHT = (255, 255, 255, 95)


def bottle(c: Canvas, liquid: RGBA, cap: RGBA, emblem: RGBA | None = None) -> None:
    c.shadow()
    c.rounded_rect((49, 12, 79, 27), 5, cap, EDGE, 2)
    c.rect((54, 8, 74, 14), LIGHT_METAL)
    c.rounded_rect((37, 24, 91, 110), 15, GLASS, EDGE, 3)
    c.rounded_rect((42, 47, 86, 105), 10, liquid)
    c.rounded_rect((45, 30, 51, 96), 3, HIGHLIGHT)
    c.ellipse((67, 32, 80, 45), (255, 255, 255, 70))
    if emblem:
        c.ellipse((55, 63, 73, 80), emblem, EDGE, 2)
        c.line((58, 78), (71, 65), 2, (115, 82, 49, 255))


def recipe_almond_water(c: Canvas) -> None:
    bottle(c, (218, 204, 162, 220), (211, 220, 225, 255), (184, 132, 78, 255))


def recipe_la_vie(c: Canvas) -> None:
    bottle(c, (169, 220, 240, 185), (61, 132, 198, 255))
    c.polygon([(48, 60), (80, 60), (84, 82), (44, 82)], (240, 245, 247, 170))
    c.line((50, 70), (78, 70), 2, (79, 151, 203, 210))


def recipe_flashlight(c: Canvas) -> None:
    c.shadow((28, 103, 101, 118))
    c.polygon([(29, 50), (52, 37), (91, 48), (92, 79), (52, 91), (29, 77)], (58, 64, 69, 255))
    c.polygon([(26, 50), (47, 39), (47, 89), (26, 77)], (34, 39, 43, 255))
    c.rounded_rect((48, 46, 94, 84), 7, (69, 77, 83, 255), EDGE, 3)
    c.ellipse((80, 48, 108, 82), (170, 203, 214, 255), EDGE, 3)
    c.ellipse((87, 54, 102, 76), (225, 244, 249, 230))
    c.rounded_rect((56, 55, 64, 76), 3, (110, 121, 128, 255))


def recipe_lighter(c: Canvas) -> None:
    c.shadow((37, 104, 91, 117))
    c.rounded_rect((42, 45, 86, 106), 7, (132, 142, 149, 255), EDGE, 3)
    c.rounded_rect((44, 23, 84, 51), 5, (190, 198, 202, 255), EDGE, 3)
    c.rect((54, 18, 72, 26), (96, 104, 110, 255))
    c.ellipse((55, 26, 70, 41), (67, 72, 76, 255), EDGE, 2)
    c.ellipse((60, 29, 66, 35), (208, 214, 218, 255))
    c.line((48, 65), (80, 65), 2, (202, 209, 213, 140))


def recipe_canned_food(c: Canvas) -> None:
    c.shadow((34, 102, 94, 116))
    c.ellipse((39, 26, 89, 48), LIGHT_METAL, EDGE, 3)
    c.rect((39, 37, 89, 96), (144, 151, 155, 255))
    c.ellipse((39, 85, 89, 106), (118, 125, 130, 255), EDGE, 3)
    c.ellipse((44, 29, 84, 43), (200, 205, 208, 255), (93, 101, 106, 255), 2)
    c.line((46, 58), (82, 58), 3, (188, 113, 66, 220))
    c.line((46, 69), (82, 69), 3, (188, 113, 66, 220))


def recipe_battery(c: Canvas) -> None:
    c.shadow((37, 103, 92, 116))
    c.rounded_rect((45, 31, 82, 105), 7, (49, 54, 59, 255), EDGE, 3)
    c.rounded_rect((50, 24, 77, 35), 4, LIGHT_METAL, EDGE, 2)
    c.rect((53, 16, 74, 26), (144, 154, 160, 255))
    c.rounded_rect((50, 44, 77, 88), 4, (197, 159, 52, 255))
    c.line((55, 52), (72, 52), 3, (239, 219, 137, 220))


def recipe_lighter_fuel(c: Canvas) -> None:
    c.shadow((32, 104, 96, 117))
    c.rounded_rect((36, 37, 87, 106), 9, (147, 155, 160, 255), EDGE, 3)
    c.rounded_rect((43, 27, 77, 43), 5, (197, 202, 205, 255), EDGE, 2)
    c.rect((68, 19, 78, 31), (101, 110, 116, 255))
    c.polygon([(77, 19), (91, 13), (94, 18), (78, 26)], (116, 125, 131, 255))
    c.polygon([(48, 55), (75, 55), (80, 80), (43, 80)], (197, 80, 54, 210))
    c.ellipse((55, 60, 68, 73), (246, 168, 64, 235))


def recipe_bandage(c: Canvas) -> None:
    c.shadow((26, 102, 102, 116))
    c.ellipse((29, 39, 82, 93), (237, 234, 220, 255), EDGE, 3)
    c.ellipse((43, 53, 68, 80), (172, 168, 157, 255), EDGE, 2)
    c.rounded_rect((62, 54, 101, 83), 8, (236, 232, 216, 255), EDGE, 2)
    c.line((68, 61), (94, 61), 2, (201, 197, 183, 255))
    c.line((68, 69), (96, 69), 2, (201, 197, 183, 255))
    c.line((68, 77), (93, 77), 2, (201, 197, 183, 255))


def recipe_antiseptic(c: Canvas) -> None:
    c.shadow((39, 105, 90, 117))
    c.rounded_rect((44, 34, 84, 106), 9, (126, 78, 37, 245), EDGE, 3)
    c.rounded_rect((49, 22, 79, 39), 5, (228, 230, 225, 255), EDGE, 2)
    c.rect((54, 15, 74, 24), (191, 196, 191, 255))
    c.rounded_rect((49, 56, 79, 83), 4, (232, 235, 226, 230))
    c.rounded_rect((54, 63, 74, 73), 5, (98, 146, 119, 230))


def recipe_painkiller(c: Canvas) -> None:
    c.shadow((31, 102, 97, 116))
    c.rounded_rect((37, 27, 91, 101), 10, (228, 231, 224, 255), EDGE, 3)
    c.rounded_rect((41, 18, 87, 34), 5, (119, 129, 135, 255), EDGE, 2)
    c.rounded_rect((43, 51, 85, 80), 4, (197, 214, 225, 240))
    c.ellipse((48, 58, 61, 71), (235, 235, 240, 255), EDGE, 1)
    c.ellipse((66, 58, 79, 71), (235, 235, 240, 255), EDGE, 1)


def recipe_sardines(c: Canvas) -> None:
    c.shadow((28, 100, 102, 115))
    c.rounded_rect((28, 39, 100, 94), 18, (196, 201, 202, 255), EDGE, 3)
    c.rounded_rect((33, 44, 95, 89), 15, (199, 67, 51, 240))
    c.polygon([(48, 66), (61, 55), (76, 58), (85, 66), (76, 74), (61, 77)], (231, 210, 132, 240))
    c.polygon([(48, 66), (40, 60), (40, 72)], (231, 210, 132, 240))
    c.ellipse((76, 62, 79, 65), (38, 42, 44, 255))


def recipe_chicken_rice_box(c: Canvas) -> None:
    c.shadow((21, 102, 107, 117))
    c.rounded_rect((24, 41, 104, 100), 10, (72, 78, 81, 255), EDGE, 3)
    c.rounded_rect((29, 46, 99, 95), 8, (216, 219, 210, 255))
    c.ellipse((35, 52, 72, 88), (245, 241, 219, 255))
    c.ellipse((68, 54, 92, 72), (182, 104, 53, 255), EDGE, 1)
    c.ellipse((72, 69, 94, 87), (205, 127, 68, 255), EDGE, 1)
    c.ellipse((84, 48, 95, 58), (87, 144, 85, 255))


def recipe_generic(c: Canvas) -> None:
    c.shadow((31, 103, 97, 117))
    c.rounded_rect((34, 33, 94, 103), 10, (96, 103, 108, 255), EDGE, 3)
    c.polygon([(34, 50), (64, 31), (94, 50), (64, 69)], (145, 153, 158, 255))
    c.line((64, 69), (64, 96), 3, (64, 70, 75, 255))


RECIPES = {
    "almond_water_bottle": recipe_almond_water,
    "spring_water_bottle": recipe_la_vie,
    "flashlight": recipe_flashlight,
    "lighter": recipe_lighter,
    "canned_food": recipe_canned_food,
    "battery": recipe_battery,
    "lighter_fuel": recipe_lighter_fuel,
    "bandage": recipe_bandage,
    "antiseptic": recipe_antiseptic,
    "painkiller": recipe_painkiller,
    "sardines_can": recipe_sardines,
    "chicken_rice_box": recipe_chicken_rice_box,
    "generic": recipe_generic,
}


def encoder() -> str:
    cwebp = shutil.which("cwebp")
    if cwebp:
        return cwebp
    for candidate in ("magick", "convert"):
        path = shutil.which(candidate)
        if path:
            return path
    raise SystemExit("No WebP encoder found. Install cwebp (package: webp) or ImageMagick with WebP support.")


def encode_webp(png: Path, target: Path, executable: str) -> None:
    exe = Path(executable).name
    if exe == "cwebp":
        cmd = [executable, "-quiet", "-q", str(QUALITY), "-alpha_q", "100", "-m", "6", str(png), "-o", str(target)]
    else:
        cmd = [executable, str(png), "-quality", str(QUALITY), "-define", "webp:method=6", str(target)]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    head = target.read_bytes()[:12]
    if len(head) < 12 or head[:4] != b"RIFF" or head[8:12] != b"WEBP":
        raise RuntimeError(f"Invalid WebP output: {target}")


def load_manifest() -> dict:
    payload = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if payload.get("size") != SIZE:
        raise SystemExit(f"Manifest size must be {SIZE}")
    if payload.get("format") != "webp" or payload.get("background") != "transparent" or payload.get("text") is not False:
        raise SystemExit("Manifest violates INVENTORY_ICON_HARD_LOCK_R01")
    items = payload.get("items")
    if not isinstance(items, list) or not items:
        raise SystemExit("Manifest items must be a non-empty list")
    ids = [str(x.get("id", "")).strip() for x in items]
    if not all(ids) or len(ids) != len(set(ids)):
        raise SystemExit("Manifest contains blank or duplicate item IDs")
    for item in items:
        if item.get("recipe") not in RECIPES:
            raise SystemExit(f"Unknown recipe for {item.get('id')}: {item.get('recipe')}")
    return payload


def generate(clean: bool = True) -> None:
    payload = load_manifest()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if clean:
        for old in OUT_DIR.glob("*.webp"):
            old.unlink()
    executable = encoder()
    with tempfile.TemporaryDirectory(prefix="inventory-icons-") as temp:
        temp_dir = Path(temp)
        entries = list(payload["items"]) + [{"id": "generic", "recipe": "generic"}]
        for item in entries:
            item_id = item["id"]
            canvas = Canvas()
            RECIPES[item["recipe"]](canvas)
            png = temp_dir / f"{item_id}.png"
            webp = OUT_DIR / f"{item_id}.webp"
            canvas.write_png(png)
            encode_webp(png, webp, executable)
            size = webp.stat().st_size
            if size <= 0 or size > 65536:
                raise RuntimeError(f"Inventory icon size out of bounds: {webp} ({size} bytes)")
            print(f"ICON|{item_id}|{size} bytes")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate deterministic text-free 128x128 WebP inventory icons.")
    parser.add_argument("--no-clean", action="store_true", help="Do not remove older WebP files first")
    args = parser.parse_args()
    generate(clean=not args.no_clean)


if __name__ == "__main__":
    main()
