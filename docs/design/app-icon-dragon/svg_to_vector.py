#!/usr/bin/env python3
"""Convert option-1 SVG shapes into Android VectorDrawable XML."""

from __future__ import annotations

import re
from pathlib import Path
from xml.etree import ElementTree as ET

SVG = Path(__file__).resolve().parent / "option-1-chubby.svg"
OUT_FG = Path(__file__).resolve().parents[3] / "app/src/main/res/drawable/ic_launcher_foreground.xml"
OUT_MONO = Path(__file__).resolve().parents[3] / "app/src/main/res/drawable/ic_launcher_monochrome.xml"

NS = {"svg": "http://www.w3.org/2000/svg"}

FILL_MAP = {
    "url(#body)": "#FFF4DC",
    "url(#belly)": "#F5D05A",
    "url(#horn)": "#FFB24A",
    "url(#wing)": "#FFB24A",
    "#FFFFFF": "#FFFFFF",
    "#FF8E78": "#FF8E78",
    "#FF7A6E": "#FF7A6E",
    "#FFF8EE": "#FFF8EE",
    "#E85A52": "#E85A52",
    "#FFC4AE": "#FFC4AE",
    "none": None,
}

STROKE_DEFAULT = "#5D2A0C"


def ellipse_path(cx, cy, rx, ry) -> str:
    return (
        f"M{cx - rx:.2f},{cy:.2f}"
        f"a{rx:.2f},{ry:.2f} 0 1,0 {rx * 2:.2f},0"
        f"a{rx:.2f},{ry:.2f} 0 1,0 {-rx * 2:.2f},0z"
    )


def local(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def parse_style_fill_stroke(el) -> tuple[str | None, str | None, float, float, str]:
    fill = el.get("fill")
    stroke = el.get("stroke")
    sw = float(el.get("stroke-width", "0") or 0)
    opacity = float(el.get("opacity", "1") or 1)
    join = el.get("stroke-linejoin", "round")
    cap = el.get("stroke-linecap", "round")
    return fill, stroke, sw, opacity, join if join else "round"


def path_xml(d: str, fill, stroke, sw, opacity, join="round", cap="round") -> str:
    fill_color = FILL_MAP.get(fill, fill) if fill else None
    if fill_color == "none":
        fill_color = None
    attrs = [f'        android:pathData="{d}"']
    if fill_color:
        attrs.append(f'        android:fillColor="{fill_color}"')
        if opacity < 0.99:
            attrs.append(f'        android:fillAlpha="{opacity:.2f}"')
    else:
        attrs.append('        android:fillColor="#00000000"')
    if stroke and sw > 0:
        attrs.append(f'        android:strokeColor="{stroke}"')
        attrs.append(f'        android:strokeWidth="{sw:.1f}"')
        attrs.append(f'        android:strokeLineJoin="{join}"')
        attrs.append(f'        android:strokeLineCap="{cap}"')
        if opacity < 0.99:
            attrs.append(f'        android:strokeAlpha="{opacity:.2f}"')
    return "    <path\n" + "\n".join(attrs) + " />"


def collect_paths(root) -> list[str]:
    out = []
    for el in root.iter():
        name = local(el.tag)
        if name in ("svg", "defs", "radialGradient", "stop", "filter", "feGaussianBlur"):
            continue
        fill, stroke, sw, opacity, join = parse_style_fill_stroke(el)
        cap = el.get("stroke-linecap", "round")
        if name == "rect":
            continue
        if name == "ellipse":
            cx, cy = float(el.get("cx")), float(el.get("cy"))
            rx, ry = float(el.get("rx")), float(el.get("ry"))
            if el.get("filter"):
                continue
            d = ellipse_path(cx, cy, rx, ry)
            out.append(path_xml(d, fill, stroke, sw, opacity, join, cap))
        elif name == "path":
            d = el.get("d", "").strip()
            d = re.sub(r"\s+", " ", d)
            out.append(path_xml(d, fill, stroke, sw, opacity, join, cap))
        elif name == "circle":
            cx, cy, r = float(el.get("cx")), float(el.get("cy")), float(el.get("r"))
            d = ellipse_path(cx, cy, r, r)
            out.append(path_xml(d, fill, stroke, sw, opacity, join, cap))
    return out


def write_vector(paths: list[str], dest: Path, fill_override: str | None = None) -> None:
    # 512 design -> 108 viewport. Face/eyes stay inside the 66dp safe zone
    # (21–87). Horns sit on the inner edge; wings/feet may clip under masks.
    scale = 0.178
    tx = 54 - 256 * scale
    ty = 54 - 210 * scale
    if fill_override:
        rewritten = []
        for p in paths:
            is_stroke_only = 'android:fillColor="#00000000"' in p
            p2 = re.sub(r'\n        android:fillAlpha="[^"]+"', "", p)
            p2 = re.sub(r'\n        android:strokeAlpha="[^"]+"', "", p2)
            if is_stroke_only:
                p2 = re.sub(r'android:strokeColor="[^"]+"', f'android:strokeColor="{fill_override}"', p2)
            else:
                p2 = re.sub(r'android:fillColor="[^"]+"', f'android:fillColor="{fill_override}"', p2)
                p2 = re.sub(r'\n        android:strokeColor="[^"]+"', "", p2)
                p2 = re.sub(r'\n        android:strokeWidth="[^"]+"', "", p2)
                p2 = re.sub(r'\n        android:strokeLineJoin="[^"]+"', "", p2)
                p2 = re.sub(r'\n        android:strokeLineCap="[^"]+"', "", p2)
            rewritten.append(p2)
        paths = rewritten

    body = "\n".join(paths)
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <group
        android:scaleX="{scale}"
        android:scaleY="{scale}"
        android:translateX="{tx:.2f}"
        android:translateY="{ty:.2f}">
{body}
    </group>
</vector>
"""
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(xml)
    print("wrote", dest)


def main() -> None:
    tree = ET.parse(SVG)
    root = tree.getroot()
    paths = collect_paths(root)
    write_vector(paths, OUT_FG)
    write_vector(paths, OUT_MONO, fill_override="#FFFFFFFF")


if __name__ == "__main__":
    main()
