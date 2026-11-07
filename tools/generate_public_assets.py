#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Generate original geometric artwork. Pillow is only needed to regenerate PNGs."""
from pathlib import Path
import colorsys
import hashlib
import json
import math

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
BITMAPS = RES / "drawable-nodpi"
VECTORS = RES / "drawable"
BITMAPS.mkdir(parents=True, exist_ok=True)
VECTORS.mkdir(parents=True, exist_ok=True)


def stick(size, inset=0):
    scale = 4
    im = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(im)
    k = size * scale / 512
    def box(coords):
        return tuple(round(x * k) for x in coords)
    draw.rounded_rectangle(box((211, 48 + inset, 301, 376)), radius=round(45 * k), fill="#58D7E8")
    draw.rounded_rectangle(box((229, 66 + inset, 283, 342)), radius=round(27 * k), fill="#EBFAFF")
    draw.rounded_rectangle(box((213, 331, 299, 448 - inset)), radius=round(20 * k), fill="#5555AD")
    draw.ellipse(box((242, 356, 270, 384)), fill="#8CE9F5")
    draw.rounded_rectangle(box((236, 412, 276, 419)), radius=round(3 * k), fill="#9A9AE0")
    return im.resize((size, size), Image.Resampling.LANCZOS)


stick(512).save(BITMAPS / "yingyuanbang.png")
foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
foreground.alpha_composite(stick(272), (80, 80))
foreground.save(BITMAPS / "ic_launcher_foreground.png")
icon = Image.new("RGBA", (512, 512), "#09162B")
icon.alpha_composite(stick(432), (40, 40))
icon.save(BITMAPS / "iag_app_icon_source.png")

side = 512
wheel = Image.new("RGBA", (side, side), (0, 0, 0, 0))
pixels = wheel.load()
for y in range(side):
    for x in range(side):
        dx = (x + .5 - side / 2) / (side / 2)
        dy = (y + .5 - side / 2) / (side / 2)
        radius = math.hypot(dx, dy)
        if radius <= 1:
            hue = (math.atan2(dy, dx) / (2 * math.pi)) % 1
            rgb = colorsys.hsv_to_rgb(hue, min(1, radius), 1)
            pixels[x, y] = (*[round(channel * 255) for channel in rgb], round(min(1, (1 - radius) * side / 2) * 255))
wheel.save(BITMAPS / "iag_color_wheel.png")

play = "M7,4 L21,12 L7,20 Z"
pause = "M5,4 H10 V20 H5 Z M14,4 H19 V20 H14 Z"
forward = "M3,5 L12,12 L3,19 Z M12,5 L21,12 L12,19 Z"
backward = "M21,5 L12,12 L21,19 Z M12,5 L3,12 L12,19 Z"
more = "M3,12 A2,2 0,1 0,7,12 A2,2 0,1 0,3,12 Z M10,12 A2,2 0,1 0,14,12 A2,2 0,1 0,10,12 Z M17,12 A2,2 0,1 0,21,12 A2,2 0,1 0,17,12 Z"
paths = {
    "am_player_play": play, "am_player_pause": pause,
    "am_mini_play": play, "am_mini_pause": pause,
    "am_player_next": forward, "am_mini_next": forward,
    "am_player_previous": backward, "am_player_more": more,
}
for name, path in paths.items():
    (VECTORS / f"{name}.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- SPDX-License-Identifier: GPL-3.0-only; original geometric symbol. -->\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp"\n'
        '    android:viewportWidth="24" android:viewportHeight="24">\n'
        f'    <path android:fillColor="#FFFFFFFF" android:pathData="{path}" />\n'
        '</vector>\n', encoding="utf-8")

assets = [*BITMAPS.glob("*.png"), *(VECTORS / f"{name}.xml" for name in paths)]
(ROOT / "docs/public-assets.json").write_text(json.dumps([
    {"path": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
     "source": "tools/generate_public_assets.py", "license": "GPL-3.0-only"}
    for path in sorted(assets)
], indent=2), encoding="utf-8")
print(f"Generated {len(assets)} original geometric assets")
