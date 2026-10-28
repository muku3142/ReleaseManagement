"""Regenerates the brand files in frontend/public from favicon.svg and src/components/brand/brand.ts.

Needs Python 3 with Pillow, fontTools and brotli, Google Chrome, and `npm ci` already run (for the fonts).
Usage, from the repo root: py -I frontend/scripts/brand-assets.py [path-to-chrome]
"""
import math
import pathlib
import re
import subprocess
import sys
import tempfile

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image

FRONTEND = pathlib.Path(__file__).resolve().parent.parent
PUB = FRONTEND / "public"
FONTS = FRONTEND / "node_modules" / "@fontsource-variable"
CHROME = sys.argv[1] if len(sys.argv) > 1 else r"C:/Program Files/Google/Chrome/Application/chrome.exe"

GLYPH = ('<g fill="#fff" transform="translate(6.08 6.08) scale(.62)">'
         '<path d="M8 9l8 7M24 9l-8 7M8 23l8-7M24 23l-8-7" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity=".55"/>'
         '<circle cx="16" cy="16" r="4"/><circle cx="8" cy="9" r="2.6" opacity=".85"/><circle cx="24" cy="9" r="2.6" opacity=".85"/>'
         '<circle cx="8" cy="23" r="2.6" opacity=".85"/><circle cx="24" cy="23" r="2.6" opacity=".85"/></g>')


def brand_constant(name):
    source = (FRONTEND / "src" / "components" / "brand" / "brand.ts").read_text(encoding="utf-8")
    return re.search(rf"export const {name} = '([^']*)';", source).group(1)


def num(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def load(path, wght):
    font = TTFont(path)
    return instantiateVariableFont(font, {"wght": wght}) if "fvar" in font else font


def outline(font, text, px, x, line_top, line_h, tracking=0.0):
    """Path data for `text` laid out like CSS: content area centred in the line box. No kerning or ligatures."""
    s = px / font["head"].unitsPerEm
    hhea = font["hhea"]
    baseline = line_top + (line_h - (hhea.ascent - hhea.descent) * s) / 2 + hhea.ascent * s
    cmap, glyphs, hmtx = font.getBestCmap(), font.getGlyphSet(), font["hmtx"]
    pen = SVGPathPen(glyphs, ntos=num)
    cursor = x
    for ch in text:
        name = cmap[ord(ch)]
        glyphs[name].draw(TransformPen(pen, (s, 0, 0, -s, cursor, baseline)))
        cursor += hmtx[name][0] * s + tracking
    return pen.getCommands(), cursor - tracking - x


def write_lockups():
    nunito = load(FONTS / "nunito/files/nunito-latin-wght-normal.woff2", 800)
    inter = load(FONTS / "inter/files/inter-latin-wght-normal.woff2", 400)
    pad, mark, gap = 8, 48, 12
    word_px, tag_px, tag_lh, tag_gap = 30, 14, 20, 4
    block_h = word_px + tag_gap + tag_lh
    inner_h = max(mark, block_h)
    tx = pad + mark + gap
    block_top = pad + (inner_h - block_h) / 2
    word_d, word_w = outline(nunito, brand_constant("PRODUCT_NAME"), word_px, tx, block_top, word_px, -0.01 * word_px)
    tag_d, tag_w = outline(inter, brand_constant("PRODUCT_TAGLINE"), tag_px, tx, block_top + word_px + tag_gap, tag_lh)
    w, h = math.ceil(tx + max(word_w, tag_w) + pad), math.ceil(inner_h + 2 * pad)
    lockups = []
    for theme, word_c, tag_c in (("light", "#09090b", "#52525b"), ("dark", "#fafafa", "#a1a1aa")):
        gid = f"brand-{theme}-g"
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">\n'
               f'  <defs><linearGradient id="{gid}" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6d5bf5"/>'
               f'<stop offset="1" stop-color="#a78bfa"/></linearGradient></defs>\n'
               f'  <g transform="translate({pad} {num(pad + (inner_h - mark) / 2)}) scale(1.5)">'
               f'<rect width="32" height="32" rx="9" fill="url(#{gid})"/>{GLYPH}</g>\n'
               f'  <path fill="{word_c}" d="{word_d}"/>\n'
               f'  <path fill="{tag_c}" d="{tag_d}"/>\n'
               f'</svg>\n')
        path = PUB / "brand" / f"productops-lockup-{theme}.svg"
        path.write_text(svg, encoding="utf-8", newline="\n")
        lockups.append((theme, svg, w, h))
    return lockups


def screenshot(work, svg, w, h, name, scale=1):
    html = work / "wrap.html"
    html.write_text('<!doctype html><style>html,body{margin:0;background:transparent;overflow:hidden}'
                    f'svg{{display:block;width:{w}px;height:{h}px}}</style>{svg}', encoding="utf-8")
    png = work / name
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                    f"--user-data-dir={work / 'profile'}", f"--force-device-scale-factor={scale}",
                    f"--window-size={w},{h}", f"--screenshot={png}", html.as_uri()], check=True, capture_output=True)
    img = Image.open(png).convert("RGBA")
    assert img.size == (w * scale, h * scale), (name, img.size)
    return img


def main():
    with tempfile.TemporaryDirectory() as tmp:
        work = pathlib.Path(tmp)
        favicon = (PUB / "favicon.svg").read_text(encoding="utf-8")
        frames = [screenshot(work, favicon, n, n, f"favicon-{n}.png") for n in (16, 32, 48)]
        frames[2].save(PUB / "favicon.ico", format="ICO", sizes=[(16, 16), (32, 32), (48, 48)], append_images=frames[:2])

        # iOS rounds the icon itself, so the tile is full bleed and fully opaque.
        apple = screenshot(work, favicon.replace(' rx="9"', ""), 180, 180, "apple.png")
        opaque = Image.new("RGBA", apple.size, (79, 70, 229, 255))
        opaque.alpha_composite(apple)
        opaque.convert("RGB").save(PUB / "apple-touch-icon.png", optimize=True)

        for theme, svg, w, h in write_lockups():
            screenshot(work, svg, w, h, f"{theme}.png", scale=2).save(
                PUB / "brand" / f"productops-lockup-{theme}@2x.png", optimize=True)


main()
