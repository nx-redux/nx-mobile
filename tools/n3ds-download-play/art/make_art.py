"""Builds the 3DS Download Play row's art from the DS Download Play row's (androidApp/src/main/assets/nds-dlp/).

Usage: make_art.py <ds art dir> <client capture.png> <out dir>

Kept from the DS art: the screenshot as is, the case and its paper cover. Replaced: the title (3DS Download Play), the
DS menu's Download Play tile and the DS Lite (our own two-handheld icon and a plain handheld showing our client),
and the spine. The client capture is a phone screenshot of the client; its top screen is found by its black margin.
Fonts are macOS's (Arial Rounded Bold, Avenir Next), as the DS art's.
"""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

DS, CAPTURE, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
ROUNDED = '/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf'
AVENIR = '/System/Library/Fonts/Avenir Next.ttc'
INK = (40, 40, 40)


def font(path, size, index=0):
    return ImageFont.truetype(path, size, index=index)


def centered(d, cx, y, text, f, fill, stroke=0, stroke_fill=None):
    left, top, right, bottom = d.textbbox((0, 0), text, font=f, stroke_width=stroke)
    d.text((cx - (right - left) / 2 - left, y - top), text, font=f, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill)


def handheld(w):
    """A plain clamshell handheld, open, seen from the front: our own shape, not any console's."""
    s = 4  # drawn at 4x, scaled down for smooth edges
    W, H = w * s, int(w * 1.28) * s
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lid_h, gap = int(H * 0.47), int(H * 0.04)
    body = (52, 54, 60, 255)
    d.rounded_rectangle((0, 0, W - 1, lid_h), radius=W // 14, fill=body)
    d.rounded_rectangle((W // 12, lid_h + gap, W - 1 - W // 12, H - 1), radius=W // 14, fill=body)
    d.rectangle((W // 5, lid_h - W // 40, W - 1 - W // 5, lid_h + gap + W // 40), fill=(36, 38, 42, 255))  # hinge
    top = (int(W * 0.08), int(lid_h * 0.12), int(W * 0.92), int(lid_h * 0.88))
    bot = (int(W * 0.2), lid_h + gap + int(lid_h * 0.1), int(W * 0.8), lid_h + gap + int(lid_h * 0.78))
    d.rectangle(top, fill=(0, 0, 0, 255))
    d.rectangle(bot, fill=(0, 0, 0, 255))
    small = img.resize((w, H // s), Image.LANCZOS)
    scale = lambda box: tuple(v // s for v in box)
    return small, scale(top), scale(bot)


def pair_icon(size):
    """Our two-handheld Download Play icon: two small clamshells with signal arcs between them."""
    s = 4
    W = size * s
    img = Image.new('RGBA', (W * 2, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for x0 in (0, int(W * 1.25)):
        w = int(W * 0.75)
        d.rounded_rectangle((x0, int(W * 0.08), x0 + w, int(W * 0.46)), radius=W // 14, fill=(60, 62, 70, 255))
        d.rounded_rectangle((x0 + w // 10, int(W * 0.52), x0 + w - w // 10, int(W * 0.92)), radius=W // 14, fill=(60, 62, 70, 255))
        d.rectangle((x0 + w // 8, int(W * 0.14), x0 + w - w // 8, int(W * 0.40)), fill=(120, 200, 255, 255))
        d.rectangle((x0 + w // 4, int(W * 0.58), x0 + w - w // 4, int(W * 0.84)), fill=(120, 200, 255, 255))
    cx, cy = W, int(W * 0.5)
    for i, r in enumerate((W * 0.12, W * 0.22, W * 0.32)):
        d.arc((cx - r, cy - r, cx + r, cy + r), 300, 60, fill=(60, 62, 70, 255), width=W // 22)
        d.arc((cx - r, cy - r, cx + r, cy + r), 120, 240, fill=(60, 62, 70, 255), width=W // 22)
    return img.resize((size * 2, size), Image.LANCZOS)


def halo(img, px):
    """A white outline under [img], as the DS wheel's text and icon have."""
    out = Image.new('RGBA', (img.width + 2 * px, img.height + 2 * px), (0, 0, 0, 0))
    out.paste((255, 255, 255, 255), (px, px), img.getchannel('A'))
    out = out.filter(ImageFilter.MaxFilter(px * 2 + 1))
    out.alpha_composite(img, (px, px))
    return out


def client_top(capture):
    """The client's top screen from a phone screenshot: the biggest non-black 5:3 area's bounds, cropped."""
    cap = Image.open(capture).convert('RGB')
    # The capture is the game screen: the 3DS top screen is the upper band; take the frame from the layout's margins.
    w, h = cap.size
    top_h = int(h * 2 / 3)
    band = cap.crop((0, 0, w, top_h))
    sw = int(top_h * 5 / 3)
    x0 = (w - sw) // 2
    return band.crop((x0, 0, x0 + sw, top_h))


# --- 2D box: the DS cover's size and grid paper (redrawn as the DS art draws it), a plain white strip, a new title
# and our icon in place of the DS menu tile
ds2d = Image.open(f'{DS}/boxart2d.png').convert('RGB')
strip_w = next(x for x in range(ds2d.width) if ds2d.getpixel((x, ds2d.height // 2))[0] < 250)


def menu_paper(size, cell):
    """Lined paper with a grid and nodes, as the DS art's cover (tools/ds-download-play-art)."""
    w, h = size
    im = Image.new('RGB', size, (238, 238, 238))
    d = ImageDraw.Draw(im)
    for y in range(0, h, 3):
        d.line([(0, y), (w, y)], fill=(246, 246, 246))
    for x in range(cell // 2, w, cell):
        d.line([(x, 0), (x, h)], fill=(205, 205, 205), width=2)
    for y in range(cell // 2, h, cell):
        d.line([(0, y), (w, y)], fill=(205, 205, 205), width=2)
    for x in range(cell // 2, w, cell):
        for y in range(cell // 2, h, cell):
            d.rectangle([x - 4, y - 4, x + 4, y + 4], fill=(185, 185, 185))
    return im


cover = Image.new('RGB', ds2d.size, (255, 255, 255))
cover.paste(menu_paper((ds2d.width - strip_w, ds2d.height), 64), (strip_w, 0))
d = ImageDraw.Draw(cover)
d.line([(strip_w - 1, 0), (strip_w - 1, ds2d.height)], fill=(225, 225, 225), width=2)
cx = strip_w + (ds2d.width - strip_w) / 2
centered(d, cx, 46, '3DS Download Play', font(ROUNDED, 38), INK, 6, (255, 255, 255))
icon = halo(pair_icon(120), 6)
cover.paste(icon, (int(cx - icon.width / 2), 150), icon)
centered(d, cx, 372, 'Play together from one Game Card', font(AVENIR, 21, 2), (70, 70, 70), 4, (255, 255, 255))
cover.save(f'{OUT}/boxart2d.png')


def abstract_shot(size):
    """The screenshot: the cover's paper and our icon, as the 2D box shows them (no client capture, no title)."""
    w, h = size
    shot = menu_paper(size, h // 6)
    icon = halo(pair_icon(h * 4 // 10), max(4, h // 90))
    shot.paste(icon, ((w - icon.width) // 2, (h - icon.height) // 2), icon)
    return shot

# --- screenshot: abstract, as the 2D box; one image for both Download Play rows
abstract_shot((960, 640)).save(f'{OUT}/screenshot.png')


def warp(src, quad, size):
    """[src] stretched onto [quad] (TL, TR, BR, BL), on a transparent canvas of [size]."""
    w, h = src.size
    corners = [(0, 0), (w, 0), (w, h), (0, h)]
    a, b = [], []
    for (x, y), (u, v) in zip(quad, corners):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        for r in range(n):
            if r != c:
                f = m[r][c] / m[c][c]
                m[r] = [m[r][k] - f * m[c][k] for k in range(n + 1)]
    coeffs = [m[i][n] / m[i][i] for i in range(n)]
    out = src.convert('RGBA').transform(size, Image.PERSPECTIVE, coeffs, Image.BICUBIC)
    mask = Image.new('L', size, 0)
    ImageDraw.Draw(mask).polygon(quad, fill=255)
    out.putalpha(Image.composite(out.getchannel('A'), Image.new('L', size, 0), mask))
    return out


# --- 3D box: the DS box with the new cover and a spine of our own
box = Image.open(f'{DS}/boxart.png').convert('RGBA')
spine_q = [(16, 38), (101, 5), (101, 700), (16, 668)]
cover_q = [(101, 5), (536, 81), (536, 624), (101, 700)]
spine = Image.new('RGB', (100, 820), (250, 250, 250))
label = Image.new('RGBA', (780, 100), (0, 0, 0, 0))
centered(ImageDraw.Draw(label), 390, 30, '3DS DOWNLOAD PLAY', font(ROUNDED, 44), INK)
spine.paste(label.rotate(90, expand=True), (0, 20), label.rotate(90, expand=True))
art = cover.crop((strip_w, 0, cover.width, cover.height))
box.alpha_composite(warp(spine, spine_q, box.size))
box.alpha_composite(warp(art, cover_q, box.size))
box.save(f'{OUT}/boxart.png')

# --- wheel: our icon over the title (600x200, transparent)
wheel = Image.new('RGBA', (600, 200), (0, 0, 0, 0))
wi = halo(pair_icon(70), 6)
wheel.alpha_composite(wi, ((600 - wi.width) // 2, 0))
d = ImageDraw.Draw(wheel)
centered(d, 300, 96, '3DS Download Play', font(ROUNDED, 62), (34, 34, 34), 7, (255, 255, 255))
bbox = wheel.getbbox()
wheel = wheel.crop(bbox)
canvas = Image.new('RGBA', (600, 200), (0, 0, 0, 0))
wheel.thumbnail((600, 200), Image.LANCZOS)
canvas.alpha_composite(wheel, ((600 - wheel.width) // 2, (200 - wheel.height) // 2))
canvas.save(f'{OUT}/wheel.png')

# --- mix: a plain handheld showing our client, the box and the wheel in front (745x745)
mix = Image.new('RGBA', (745, 745), (0, 0, 0, 0))
dev, top, bot = handheld(540)
shot = client_top(CAPTURE).resize((top[2] - top[0], top[3] - top[1]), Image.LANCZOS)
dev.paste(shot, (top[0], top[1]))
mix.alpha_composite(dev, (745 - dev.width - 20, 0))
b3 = box.copy(); b3.thumbnail((290, 370), Image.LANCZOS)
mix.alpha_composite(b3, (0, 745 - b3.height))
wl = canvas.copy(); wl.thumbnail((420, 140), Image.LANCZOS)
mix.alpha_composite(wl, (745 - wl.width - 6, 745 - wl.height - 16))
mix.save(f'{OUT}/mix.png')
print('done')
