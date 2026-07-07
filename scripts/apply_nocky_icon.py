#!/usr/bin/env python3
"""Generate Android launcher mipmaps from a source Nocky icon PNG.

Usage:
    python3 scripts/apply_nocky_icon.py /path/to/nocky-icon-1024.png

The project stores legacy launcher mipmaps as WebP. Android 12+ launchers prefer
adaptive icons from mipmap-anydpi-v31, so this helper also writes a separate
Nocky bitmap background resource used by the adaptive icon XMLs.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pragma: no cover - user-facing helper
    print("Pillow is required. Install it with: python3 -m pip install --user pillow", file=sys.stderr)
    raise SystemExit(1)

ROOT = Path(__file__).resolve().parents[1]
RES_DIR = ROOT / "app" / "src" / "main" / "res"

DENSITIES: tuple[tuple[str, int], ...] = (
    ("mdpi", 48),
    ("hdpi", 72),
    ("xhdpi", 96),
    ("xxhdpi", 144),
    ("xxxhdpi", 192),
)

LAUNCHER_NAMES: tuple[str, ...] = (
    "ic_launcher",
    "ic_launcher_round",
    "ic_launcher_static",
    "ic_launcher_static_round",
    "nocky_launcher_background",
)


def square_crop(image: Image.Image) -> Image.Image:
    width, height = image.size
    side = min(width, height)
    left = (width - side) // 2
    top = (height - side) // 2
    return image.crop((left, top, left + side, top + side))


def save_icon(source: Image.Image, density: str, size: int, name: str) -> Path:
    directory = RES_DIR / f"mipmap-{density}"
    directory.mkdir(parents=True, exist_ok=True)
    png_duplicate = directory / f"{name}.png"
    if png_duplicate.exists():
        png_duplicate.unlink()
    output = directory / f"{name}.webp"
    resized = source.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(output, "WEBP", quality=95, method=6)
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate Nocky launcher icons.")
    parser.add_argument("source_icon", type=Path, help="Path to the 1024x1024 Nocky PNG icon")
    args = parser.parse_args()

    if not args.source_icon.exists():
        print(f"Icon not found: {args.source_icon}", file=sys.stderr)
        return 2

    source = Image.open(args.source_icon).convert("RGBA")
    source = square_crop(source)

    generated: list[Path] = []
    for density, size in DENSITIES:
        for name in LAUNCHER_NAMES:
            generated.append(save_icon(source, density, size, name))

    print("Generated launcher icons:")
    for path in generated:
        print(f"  {path.relative_to(ROOT)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
