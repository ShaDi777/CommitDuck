#!/usr/bin/env python3
"""Convert Telegram animated stickers (.tgs) into sprite sheets for the plugin.

The state name is taken from the file name: assets/stickers/idle.tgs -> idle.
For every sticker and size it writes:
  <out>/<size-name>/<state>.png        1x sprite sheet (frames left-to-right, top-to-bottom)
  <out>/<size-name>/<state>@2x.png     2x sprite sheet for HiDPI
  <out>/<size-name>/<state>.properties frame metadata read by DuckAnimation.kt

Requirements: pip install -r tools/requirements.txt

Example (regenerate everything):
  python tools/sticker_to_sprites.py assets/stickers/*.tgs --preview build/duck-preview

See docs/assets.md for the full workflow.
"""
import argparse
import math
from pathlib import Path
from typing import Optional

from PIL import Image
from rlottie_python import LottieAnimation

DEFAULT_OUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "duck"
RENDER_SIZE = 512
# name -> frame height in logical (1x) pixels.
# Add e.g. "large": 128 when the tool window needs a big duck (costs ~3 MB per state).
SIZES = {
    "status": 18,
    "popup": 96,
}
# keep sheets well below common image size limits
MAX_SHEET_WIDTH = 4096
LIGHT_BG = (255, 255, 255, 255)
DARK_BG = (43, 43, 43, 255)


def render_frames(src: Path, fps: int) -> list:
    anim = LottieAnimation.from_tgs(str(src))
    source_fps = anim.lottie_animation_get_framerate()
    # rlottie reports op - ip + 1 frames; the last one equals the first in a looping sticker
    total = anim.lottie_animation_get_totalframe() - 1
    step = source_fps / fps
    return [
        anim.render_pillow_frame(frame_num=int(i * step), width=RENDER_SIZE, height=RENDER_SIZE)
        for i in range(round(total / step))
    ]


def union_bbox(frames: list) -> tuple:
    boxes = [b for b in (f.getchannel("A").getbbox() for f in frames) if b]
    return (
        min(b[0] for b in boxes),
        min(b[1] for b in boxes),
        max(b[2] for b in boxes),
        max(b[3] for b in boxes),
    )


def resize(frame: Image.Image, size: tuple) -> Image.Image:
    # resize in premultiplied alpha so edges don't get dark fringes
    return frame.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def build_sheet(frames: list, frame_w: int, frame_h: int, columns: int) -> Image.Image:
    rows = math.ceil(len(frames) / columns)
    sheet = Image.new("RGBA", (frame_w * columns, frame_h * rows), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        sheet.paste(resize(frame, (frame_w, frame_h)), ((i % columns) * frame_w, (i // columns) * frame_h))
    return sheet


def write_preview(frames: list, path: Path, fps: int) -> None:
    """GIF with the animation on light and dark backgrounds side by side. Review only, not used by the plugin."""
    previews = []
    for frame in frames:
        small = resize(frame, (frame.width // 2, frame.height // 2))
        row = Image.new("RGBA", (small.width * 2, small.height), LIGHT_BG)
        row.paste(DARK_BG, (small.width, 0, small.width * 2, small.height))
        row.alpha_composite(small, (0, 0))
        row.alpha_composite(small, (small.width, 0))
        previews.append(row.convert("RGB"))
    previews[0].save(path, save_all=True, append_images=previews[1:], duration=round(1000 / fps), loop=0)


def convert(sticker: Path, out: Path, fps: int, preview: Optional[Path]) -> None:
    state = sticker.stem
    frames = render_frames(sticker, fps)
    left, top, right, bottom = union_bbox(frames)
    frames = [f.crop((left, top, right, bottom)) for f in frames]
    aspect = (right - left) / (bottom - top)

    for name, height in SIZES.items():
        frame_w, frame_h = round(height * aspect), height
        columns = min(len(frames), MAX_SHEET_WIDTH // (frame_w * 2))
        target = out / name / state
        target.mkdir(parents=True, exist_ok=True)
        for scale, suffix in ((1, ""), (2, "@2x")):
            sheet = build_sheet(frames, frame_w * scale, frame_h * scale, columns)
            sheet.save(target / f"animation{suffix}.png", optimize=True)
        (target / "animation.properties").write_text(
            f"frameCount={len(frames)}\n"
            f"fps={fps}\n"
            f"frameWidth={frame_w}\n"
            f"frameHeight={frame_h}\n"
            f"columns={columns}\n"
        )
        print(f"{state}/{name}: {len(frames)} frames of {frame_w}x{frame_h} @ {fps} fps, {columns} columns -> {target}")

    if preview:
        preview.mkdir(parents=True, exist_ok=True)
        write_preview(frames, preview / f"{state}.gif", fps)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("stickers", type=Path, nargs="+", help="input .tgs files named after states, e.g. idle.tgs")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT, help="output directory (default: src/main/resources/duck)")
    parser.add_argument("--fps", type=int, default=30, help="output frame rate (default: 30)")
    parser.add_argument("--preview", type=Path, help="optional directory for a light/dark GIF preview")
    args = parser.parse_args()

    for sticker in args.stickers:
        convert(sticker, args.out, args.fps, args.preview)


if __name__ == "__main__":
    main()
