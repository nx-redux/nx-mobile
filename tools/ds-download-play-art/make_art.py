"""Builds DS Download Play art from ScreenScraper's DS system media and a DS menu capture.

Usage: make_art.py <workdir>   (expects img/*.png from ScreenScraper, cap_menu.png; writes out/)
"""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W = sys.argv[1]
IMG = W + '/img/'
OUT = W + '/out/'

# Where the DS screens sit in the nxultra capture (from the runner's relayout log line).
TOP = (339, 54, 1577 / 256)
BOT = (580, 1455, 1096 / 256)


def native(cap, origin):
    """The 256x192 DS screen, one sample per DS pixel at its centre: exact pixels, no blur."""
    x0, y0, s = origin
    px = cap.load()
    out = Image.new('RGB', (256, 192))
    o = out.load()
    for y in range(192):
        for x in range(256):
            o[x, y] = px[int(x0 + (x + 0.5) * s), int(y0 + (y + 0.5) * s)][:3]
    return out


def solve(a, b):
    n = len(b)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        for r in range(n):
            if r != c:
                f = m[r][c] / m[c][c]
                m[r] = [m[r][k] - f * m[c][k] for k in range(n + 1)]
    return [m[i][n] / m[i][i] for i in range(n)]


def warp(src, quad, size):
    """src stretched onto quad (TL, TR, BR, BL in the output), on a transparent canvas of size."""
    w, h = src.size
    corners = [(0, 0), (w, 0), (w, h), (0, h)]
    a, b = [], []
    for (x, y), (u, v) in zip(quad, corners):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); b.append(u)
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y]); b.append(v)
    coeffs = solve(a, b)
    big = src.convert('RGBA').resize((w * 2, h * 2), Image.LANCZOS)
    coeffs2 = [c * 2 for c in coeffs[:6]] + coeffs[6:]
    out = big.transform(size, Image.PERSPECTIVE, coeffs2, Image.BICUBIC)
    mask = Image.new('L', size, 0)
    ImageDraw.Draw(mask).polygon(quad, fill=255)
    out.putalpha(Image.composite(out.getchannel('A'), Image.new('L', size, 0), mask))
    return out


def font(name, size, index=0):
    return ImageFont.truetype(name, size, index=index)


ROUNDED = '/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf'
AVENIR = '/System/Library/Fonts/Avenir Next.ttc'

cap = Image.open(W + '/cap_menu.png').convert('RGB')
top, bot = native(cap, TOP), native(cap, BOT)

# --- screenshot: both screens, as ScreenScraper's DS screenshots are (256x384)
shot = Image.new('RGB', (256, 384))
shot.paste(top, (0, 0)); shot.paste(bot, (0, 192))
shot.save(OUT + 'screenshot.png')

# The DS menu's Download Play tile, and the two-DS icon inside it.
tile = bot.crop((129, 73, 223, 119))
icon = bot.crop((145, 91, 199, 111)).convert('RGBA')
ip = icon.load()
for y in range(icon.height):
    for x in range(icon.width):
        r, g, b, _ = ip[x, y]
        lum = (r + g + b) / 3
        if 120 < lum < 250 and max(r, g, b) - min(r, g, b) < 12:
            ip[x, y] = (0, 0, 0, 0)   # the tile's grey gradient behind the icon
icon = icon.crop(icon.getbbox())


def menu_paper(size, cell):
    """The DS menu's background: lined paper with a grid and nodes."""
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


def centered(d, cx, y, text, f, fill, stroke=0, stroke_fill=None):
    l, t, r, b = d.textbbox((0, 0), text, font=f, stroke_width=stroke)
    d.text((cx - (r - l) / 2 - l, y - t), text, font=f, fill=fill, stroke_width=stroke, stroke_fill=stroke_fill)
    return b - t


# --- 2D box: the DS case's white strip on the left, the menu tile as the cover art (514x458)
spine = Image.open(IMG + 'box-tranche-vierge.png').convert('RGBA')
cover = Image.new('RGBA', (514, 458), (255, 255, 255, 255))
art_w = 514 - spine.width
cover.paste(menu_paper((art_w, 458), 64), (spine.width, 0))
cover.alpha_composite(spine, (0, 0))
d = ImageDraw.Draw(cover)
cx = spine.width + art_w / 2
centered(d, cx, 46, 'DS Download Play', font(ROUNDED, 40), (40, 40, 40), 6, (255, 255, 255))
big_tile = tile.resize((tile.width * 4, tile.height * 4), Image.NEAREST)
shadow = Image.new('RGBA', (big_tile.width + 40, big_tile.height + 40), (0, 0, 0, 0))
ImageDraw.Draw(shadow).rectangle([20, 26, 20 + big_tile.width, 26 + big_tile.height], fill=(0, 0, 0, 90))
shadow = shadow.filter(ImageFilter.GaussianBlur(8))
tx, ty = int(cx - big_tile.width / 2), 150
cover.alpha_composite(shadow, (tx - 20, ty - 20))
cover.paste(big_tile, (tx, ty))
centered(d, cx, 372, 'Play together from one Game Card', font(AVENIR, 21, 2), (70, 70, 70), 4, (255, 255, 255))
cover = cover.convert('RGB')
cover.save(OUT + 'boxart2d.png')

# --- 3D box: the cover and spine under ScreenScraper's DS case shell (554x705)
shell = Image.open(IMG + 'box3D.png').convert('RGBA')
box = Image.new('RGBA', shell.size, (0, 0, 0, 0))
box.alpha_composite(warp(spine, [(16, 38), (101, 5), (101, 700), (16, 668)], shell.size))
box.alpha_composite(warp(cover, [(101, 5), (536, 81), (536, 624), (101, 700)], shell.size))
box.alpha_composite(shell)
box.save(OUT + 'boxart.png')

# --- wheel: the two-DS icon over the title (600x200, transparent)
wheel = Image.new('RGBA', (600, 200), (0, 0, 0, 0))
wi = icon.resize((icon.width * 4, icon.height * 4), Image.NEAREST)
halo = Image.new('RGBA', (wi.width + 24, wi.height + 24), (0, 0, 0, 0))
halo.paste((255, 255, 255, 255), (12, 12), wi.getchannel('A'))
halo = halo.filter(ImageFilter.MaxFilter(13))
wheel.alpha_composite(halo, ((600 - halo.width) // 2, 4))
wheel.alpha_composite(wi, ((600 - wi.width) // 2, 16))
centered(ImageDraw.Draw(wheel), 300, 112, 'DS Download Play', font(ROUNDED, 62), (34, 34, 34), 7, (255, 255, 255))
wheel = wheel.crop(wheel.getbbox())
canvas = Image.new('RGBA', (600, 200), (0, 0, 0, 0))
wheel.thumbnail((600, 200), Image.LANCZOS)
canvas.alpha_composite(wheel, ((600 - wheel.width) // 2, (200 - wheel.height) // 2))
wheel = canvas
wheel.save(OUT + 'wheel.png')

# --- game card: ScreenScraper's blank DS card with a label (for the mix)
card = Image.open(IMG + 'support2D.png').convert('RGBA')
hole = Image.new('L', card.size, 0)
cw, ch = card.size
ca = card.getchannel('A').load()
# The label is the transparent window inside the card's frame: walk out from the middle to the frame.
mx, my = cw // 2, ch // 2
lx0 = next(x for x in range(mx, 0, -1) if ca[x, my] > 128) + 1
lx1 = next(x for x in range(mx, cw) if ca[x, my] > 128) - 1
ly0 = next(y for y in range(my, 0, -1) if ca[mx, y] > 128) + 1
ly1 = next(y for y in range(my, ch) if ca[mx, y] > 128) - 1
label = Image.new('RGBA', card.size, (0, 0, 0, 0))
lw, lh = lx1 - lx0 + 1, ly1 - ly0 + 1
lab = Image.new('RGBA', (lw, lh), (255, 255, 255, 255))
logo = Image.open(IMG + 'logo-monochrome.png').convert('RGBA')
logo.thumbnail((int(lw * 0.78), 80), Image.LANCZOS)
lab.alpha_composite(logo, ((lw - logo.width) // 2, 18))
art = menu_paper((lw, lh - 120), 48)
lab.paste(art, (0, 120))
ct = tile.resize((tile.width * 4, tile.height * 4), Image.NEAREST)
ct.thumbnail((int(lw * 0.86), lh), Image.NEAREST)
lab.paste(ct, ((lw - ct.width) // 2, 120 + (lh - 120 - ct.height) // 2))
label.alpha_composite(lab, (lx0, ly0))
label.alpha_composite(card)
card = label
card.save(OUT + 'card.png')

# --- mix: the DS Lite showing the screenshot, the 3D box, the card and the wheel (745x745)
lite = Image.open(IMG + 'illustration.png').convert('RGBA')
screen_top, screen_bot = (331, 223, 667, 473), (331, 603, 667, 853)
for (x0, y0, x1, y1), scr in ((screen_top, top), (screen_bot, bot)):
    lite.paste(scr.resize((x1 - x0, y1 - y0), Image.NEAREST), (x0, y0))
lite = lite.crop(lite.getchannel('A').getbbox())
mix = Image.new('RGBA', (745, 745), (0, 0, 0, 0))
# The DS Lite fills most of the square; the box, card and wheel sit over its lower half.
lite.thumbnail((700, 700), Image.LANCZOS)
mix.alpha_composite(lite, (745 - lite.width, 0))
b3 = box.copy(); b3.thumbnail((290, 370), Image.LANCZOS)
mix.alpha_composite(b3, (0, 745 - b3.height))
c2 = card.copy(); c2.thumbnail((140, 150), Image.LANCZOS)
mix.alpha_composite(c2, (b3.width - 50, 745 - c2.height - 4))
wl = wheel.copy(); wl.thumbnail((420, 140), Image.LANCZOS)
mix.alpha_composite(wl, (745 - wl.width - 6, 745 - wl.height - 16))
mix.save(OUT + 'mix.png')
print('done')
