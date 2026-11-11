#!/usr/bin/env python3
"""Rasterize option-1 SVG into Android mipmap PNGs and a 512 preview."""

from pathlib import Path

import cairosvg
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
RES = Path(__file__).resolve().parents[3] / "app" / "src" / "main" / "res"
SVG = ROOT / "option-1-chubby.svg"
ORANGE = (255, 109, 0)

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def render_svg(size: int) -> Image.Image:
    png = cairosvg.svg2png(url=str(SVG), output_width=size, output_height=size)
    from io import BytesIO

    return Image.open(BytesIO(png)).convert("RGBA")


def circle_crop(im: Image.Image) -> Image.Image:
    size = im.size[0]
    rgb = Image.new("RGB", (size, size), ORANGE)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    rgb.paste(im.convert("RGB"), mask=mask)
    return rgb


def main() -> None:
    preview = render_svg(512)
    preview.convert("RGB").save(ROOT / "option-1-chubby.png", "PNG", optimize=True)

    for dens, px in DENSITIES.items():
        folder = RES / f"mipmap-{dens}"
        folder.mkdir(parents=True, exist_ok=True)
        im = render_svg(px)
        im.convert("RGB").save(folder / "ic_launcher.png", "PNG", optimize=True)
        circle_crop(im).save(folder / "ic_launcher_round.png", "PNG", optimize=True)
        print("wrote", folder, px)


if __name__ == "__main__":
    main()
