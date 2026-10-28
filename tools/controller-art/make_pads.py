#!/usr/bin/env python3
"""Controller art for the main menu: one WebP per console from a folder of 1000 px transparent PNGs.

Cuts a cable leaving any edge (the narrow solid run reaching the border, followed inward until the body),
crops to what is drawn, fits 640 px, writes menu_pad_<id>.webp, and prints each image's brightness factor
(mean luminance of its opaque pixels, PlayStation = 1, clamped 0.5..2.0; OVERRIDES win).
The Android console's controller comes from prep_controller.py first. Needs Pillow and cwebp. Usage: make_pads.py <source dir> <drawable dir> [id ...] (ids: only those; default all)
"""
import os, subprocess, sys, tempfile
from PIL import Image, ImageDraw, ImageStat

SOURCES = {   # console id -> source file name (without .png)
    "nds": "nintendo-ds", "n64": "nintendo-64", "psp": "sony-psp", "ps": "playstation", "gb": "game-boy",
    "gbc": "game-boy-color", "gba": "game-boy-advance", "fc": "nintendo-es", "sfc": "super-nintendo-es",
    "md": "sega-mega-drive", "sms": "sega-master-system", "gg": "sega-game-gear", "sg1000": "sega-sg-1000",
    "fds": "famicom-disk-system", "ngpc": "neo-geo-pocket", "pce": "pc-engine", "vb": "virtual-boy",
    "lynx": "atari-lynx", "a2600": "atari-2600", "a5200": "atari-5200", "a7800": "atari-7800",
    "pkm": "pokemon-mini", "wsc": "wonderswan-color", "coleco": "colecovision", "dc": "dreamcast",
    "fbn": "competition-pro", "vic20": "commodore-vic-20",   # vic20: the image, shared by Doom and Pico-8
    "3ds": "nintendo-3ds", "wii": "nintendo-wii", "gc": "nintendo-gamecube", "ps2": "sony-playstation-2",
    "android": "android-controller",   # the Android games console (not a registry console); from prep_controller.py
}
OVERRIDES = {"gba": 1.15, "fc": 0.8, "psp": 1.5,   # set by eye: saturated purple reads bright; the NES box and PSP screen read busy
             "fbn": 0.8,   # the Competition Pro's red, at full saturation in List, outshone the grey pads at its measured 1.54
             "android": 1.5}   # the controller's light rims and buttons read brighter than the other pads at the clamp's 2.0
CABLE_MAX = 130   # a solid run through the cable column narrower than this is cable (or its strain relief), not body
SOLID = 120       # alpha above this is drawn body; below it, soft shadow (which can bridge a cable to the body)
SWING = 90        # cleared each side of the cable's column above the body (a coil swings off it)
ROTATE = {"top": None, "bottom": Image.Transpose.ROTATE_180, "left": Image.Transpose.ROTATE_270, "right": Image.Transpose.ROTATE_90}
# Images whose body touches the border (it would read as a cable): first CLEAR (polygons in source pixels; a cable
# that ends short of the border), then centre on a transparent 1000 px canvas, scaled to fit 960 px.
CLEAR = {"fbn": [[(0, 875), (247, 895), (250, 900), (350, 985), (500, 1100), (500, 1200), (0, 1200)]]}   # bottom-left cable
MARGIN = {"fbn", "vic20", "3ds", "wii", "ps2"}   # 3ds: an 840 x 859 source whose body reaches the right edge; wii: an 800 px Wiimote touching top and bottom; ps2: a 1000 x 595 DualShock 2 touching left, right and bottom
UNROTATE = {"top": None, "bottom": Image.Transpose.ROTATE_180, "left": Image.Transpose.ROTATE_90, "right": Image.Transpose.ROTATE_270}

def cut_top_cable(im):
    """A cable leaving the top edge: find where the body starts on the cable's column (the first row whose solid run
    through it is body-wide; soft shadow does not count), then clear everything above it in a window around the
    cable's column (a coiled cable swings off it)."""
    px = im.load(); w, h = im.size
    top = [x for x in range(w) if px[x, 3][3] > 128]
    if not top or max(top) - min(top) > CABLE_MAX: return False
    c0, c1 = min(top), max(top)
    cx = (c0 + c1) // 2
    body = h
    for y in range(h):
        if px[cx, y][3] <= SOLID: continue
        l = r = cx
        while l > 0 and px[l - 1, y][3] > SOLID: l -= 1
        while r < w - 1 and px[r + 1, y][3] > SOLID: r += 1
        if r - l > CABLE_MAX: body = y; break
    for y in range(body):
        for x in range(max(0, c0 - SWING), min(w, c1 + SWING + 1)): px[x, y] = (0, 0, 0, 0)
        for x in range(w):   # the cable's faint shadow above the body
            if px[x, y][3] < 90: px[x, y] = (0, 0, 0, 0)
    return True

def prepare(im, cid):
    for poly in CLEAR.get(cid, []):
        mask = Image.new("L", im.size, 0); ImageDraw.Draw(mask).polygon(poly, fill=255)
        im.paste((0, 0, 0, 0), (0, 0), mask)
    if cid in MARGIN:
        im.thumbnail((960, 960), Image.LANCZOS)
        canvas = Image.new("RGBA", (1000, 1000), (0, 0, 0, 0))
        canvas.paste(im, ((1000 - im.width) // 2, (1000 - im.height) // 2)); im = canvas
    return im

def process(path, cid):
    im = prepare(Image.open(path).convert("RGBA"), cid)
    for edge in ("top", "bottom", "left", "right"):
        t = im.transpose(ROTATE[edge]) if ROTATE[edge] is not None else im
        if cut_top_cable(t):
            im = t.transpose(UNROTATE[edge]) if UNROTATE[edge] is not None else t
            print(f"  cable cut from the {edge}", file=sys.stderr)
    bb = im.getchannel("A").point(lambda v: 255 if v > 10 else 0).getbbox()
    im = im.crop(bb); im.thumbnail((640, 640), Image.LANCZOS)
    return im

def luminance(im):
    mask = im.getchannel("A").point(lambda v: 255 if v > 200 else 0)
    return ImageStat.Stat(im.convert("L"), mask).mean[0]

def main(src, out, ids):
    os.makedirs(out, exist_ok=True)
    ids = ids or list(SOURCES)
    ref = luminance(process(os.path.join(src, SOURCES["ps"] + ".png"), "ps"))
    imgs = {cid: process(os.path.join(src, SOURCES[cid] + ".png"), cid) for cid in ids}
    for cid, im in imgs.items():
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            im.save(tmp.name)
        subprocess.run(["cwebp", "-quiet", "-q", "88", "-alpha_q", "100", tmp.name, "-o", os.path.join(out, f"menu_pad_{cid}.webp")], check=True)
        os.unlink(tmp.name)
        k = OVERRIDES.get(cid, min(2.0, max(0.5, ref / luminance(im))))
        print(f'"{cid}" to {k:.2f}f')

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
