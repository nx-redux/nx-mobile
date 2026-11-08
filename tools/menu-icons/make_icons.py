#!/usr/bin/env python3
"""The main menu's tool icons, drawn from scratch on a 48-unit grid.

Writes tools/menu-icons/menu_icon_<name>.svg (the source) and the matching Android vector drawable
shared/src/commonMain/composeResources/drawable/menu_icon_<name>.xml, with identical path data. Every icon is solid
white on transparent (the app tints it), built from a few filled paths, each even-odd so its cut-outs are holes.
Family rules: 4-unit bars and rings, corners rounded at about 4 units (2 on a 24 grid), about 4 units of margin.

    python3 tools/menu-icons/make_icons.py
"""
import math
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SVG_DIR = os.path.join(ROOT, "tools", "menu-icons")
XML_DIR = os.path.join(ROOT, "shared", "src", "commonMain", "composeResources", "drawable")
GRID = 48


def f(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def rounded_poly(points, r):
    """A closed polygon whose every corner is rounded by a quadratic of radius about [r] (shortened on short edges)."""
    n = len(points)
    out = []
    for i in range(n):
        p0, p, p1 = points[i - 1], points[i], points[(i + 1) % n]
        rr = r if isinstance(r, (int, float)) else r[i]

        def toward(q):
            dx, dy = q[0] - p[0], q[1] - p[1]
            d = math.hypot(dx, dy)
            k = min(rr, d / 2) / d
            return (p[0] + dx * k, p[1] + dy * k)

        a, b = toward(p0), toward(p1)
        out.append(("L" if out else "M") + f" {f(a[0])} {f(a[1])} Q {f(p[0])} {f(p[1])} {f(b[0])} {f(b[1])}")
    return " ".join(out) + " Z"


def rrect(x0, y0, x1, y1, r):
    return (f"M {f(x0 + r)} {f(y0)} H {f(x1 - r)} A {f(r)} {f(r)} 0 0 1 {f(x1)} {f(y0 + r)} "
            f"V {f(y1 - r)} A {f(r)} {f(r)} 0 0 1 {f(x1 - r)} {f(y1)} H {f(x0 + r)} "
            f"A {f(r)} {f(r)} 0 0 1 {f(x0)} {f(y1 - r)} V {f(y0 + r)} A {f(r)} {f(r)} 0 0 1 {f(x0 + r)} {f(y0)} Z")


def circle(cx, cy, r):
    return (f"M {f(cx - r)} {f(cy)} A {f(r)} {f(r)} 0 1 1 {f(cx + r)} {f(cy)} "
            f"A {f(r)} {f(r)} 0 1 1 {f(cx - r)} {f(cy)} Z")


def star(cx, cy, ro, ri, r=0.6):
    pts = []
    for i in range(10):
        a = math.radians(-90 + 36 * i)
        rad = ro if i % 2 == 0 else ri
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    return rounded_poly(pts, r)


# --- Trophy: a flat-topped cup with a star cut out, ring handles, a short stem and a plinth.
def trophy():
    cup = ("M 14 5 H 34 A 3 3 0 0 1 37 8 V 15 C 37 23.5 31.5 29.5 24 29.5 "
           "C 16.5 29.5 11 23.5 11 15 V 8 A 3 3 0 0 1 14 5 Z")
    cup += " " + star(24, 15.5, 7.2, 3.1)
    left = rrect(3, 9, 15, 25, 5.5) + " " + rrect(7, 13, 15, 21, 2)
    right = rrect(33, 9, 45, 25, 5.5) + " " + rrect(33, 13, 41, 21, 2)
    stem = rounded_poly([(21.5, 28), (26.5, 28), (27.5, 36), (20.5, 36)], 0.8)
    base = rrect(13, 35, 35, 43, 2.5)
    return [cup, left, right, stem, base]


# --- Game Tracker: a stopwatch, a solid face with its hands cut out as one L, a crown and a side button.
def stopwatch():
    cx, cy, r = 24, 26.5, 17.5
    face = circle(cx, cy, r)
    # Hands as one outline (minute up, hour right), 4 units wide with round ends, so the cut never overlaps itself.
    hands = (f"M 22 {f(cy)} V 16.5 A 2 2 0 0 1 26 16.5 V {f(cy - 2)} H 32 A 2 2 0 0 1 32 {f(cy + 2)} "
             f"H 24 A 2 2 0 0 1 22 {f(cy)} Z")
    crown = rrect(18, 3, 30, 8, 2)
    neck = rrect(21.5, 6, 26.5, 11, 0.8)
    # The side button, a tilted bar at about 1:30.
    a = math.radians(-45)
    ux, uy = math.cos(a), math.sin(a)
    vx, vy = -uy, ux
    c0 = (cx + ux * 15.5, cy + uy * 15.5)
    c1 = (cx + ux * 21.5, cy + uy * 21.5)
    w = 2.6
    button = rounded_poly([(c0[0] + vx * w, c0[1] + vy * w), (c1[0] + vx * w, c1[1] + vy * w),
                           (c1[0] - vx * w, c1[1] - vy * w), (c0[0] - vx * w, c0[1] - vy * w)], [0.5, 1.6, 1.6, 0.5])
    return [face + " " + hands, crown, neck, button]


# --- Artwork: a landscape frame (a 4-unit ring) holding two solid peaks and a solid sun.
def picture():
    frame = rrect(3, 7, 45, 41, 4) + " " + rrect(7, 11, 41, 37, 1.5)
    hills = rounded_poly([(6, 38), (6, 35.3), (17, 21), (26, 31.5), (31, 26), (42, 35.9), (42, 38)],
                         [0, 0, 2.2, 1.6, 2.2, 0, 0])
    sun = circle(33, 18.5, 3.6)
    return [frame, hills, sun]


# --- Cheat Database: a game cartridge, its corner clipped, with a lightning bolt cut out.
def cartridge():
    body = rounded_poly([(7, 4), (34, 4), (41, 11), (41, 44), (33, 44), (33, 40.5), (15, 40.5), (15, 44), (7, 44)],
                        [3, 1.5, 1.5, 3, 1.2, 1.2, 1.2, 1.2, 3])
    # The edge connector is the notch in the bottom edge.
    bolt = rounded_poly([(27.5, 11), (15.5, 27.5), (23, 27.5), (20.5, 39), (32.5, 22), (25, 22), (28.5, 11)],
                        [0.6, 0.8, 0.6, 0.6, 0.8, 0.6, 0.4])
    return [body + " " + bolt]


# --- Settings: an eight-tooth cog with a round hole.
def gear():
    cx = cy = 24
    teeth, ro, rr = 8, 21, 15.5
    pts = []
    for i in range(teeth):
        a = 360 / teeth * i + 22.5
        for da, rad in ((-13.5, rr), (-8, ro), (8, ro), (13.5, rr)):
            t = math.radians(a + da - 90)
            pts.append((cx + rad * math.cos(t), cy + rad * math.sin(t)))
    radii = []
    for i in range(len(pts)):
        radii.append(1.6 if i % 4 in (1, 2) else 1.4)
    return [rounded_poly(pts, radii) + " " + circle(cx, cy, 6.5)]


# --- Android app: a 2 x 2 grid of rounded squares.
def apps():
    s, g = 16, 4
    x0 = (GRID - (2 * s + g)) / 2
    return [rrect(x0 + i * (s + g), x0 + j * (s + g), x0 + i * (s + g) + s, x0 + j * (s + g) + s, 4.5)
            for j in range(2) for i in range(2)]


# --- Device Sync: two 4-unit arcs chasing each other round the centre, each about 150 degrees ending in a filled
# arrowhead; the second is the first turned half a turn.
def sync():
    cx = cy = 24
    r, w, head, reach = 14.5, 2, 6, 28   # the ring's middle radius, half its width, the head's half width, its length in degrees

    def at(rad, deg):
        t = math.radians(deg)
        return f"{f(cx + rad * math.cos(t))} {f(cy + rad * math.sin(t))}"

    def arrow(t0, t1):
        # Clockwise (on screen) from t0 to t1: the outer edge, the head out to its tip on the middle line, the inner
        # edge back, then a round cap at the tail.
        return (f"M {at(r + w, t0)} A {f(r + w)} {f(r + w)} 0 0 1 {at(r + w, t1)} "
                f"L {at(r + head, t1)} L {at(r, t1 + reach)} L {at(r - head, t1)} L {at(r - w, t1)} "
                f"A {f(r - w)} {f(r - w)} 0 0 0 {at(r - w, t0)} A {f(w)} {f(w)} 0 0 1 {at(r + w, t0)} Z")

    return [arrow(200, 330), arrow(20, 150)]


# --- Kids Mode's Lock: a 4-unit shackle arch over a rounded body with a round keyhole.
def lock():
    cx = 24
    r_out, r_in, top, foot = 10, 6, 16, 23   # the arch's outer and inner radii, its centre's height, where its legs end
    shackle = (f"M {f(cx - r_out)} {f(foot)} V {f(top)} A {f(r_out)} {f(r_out)} 0 0 1 {f(cx + r_out)} {f(top)} "
               f"V {f(foot)} H {f(cx + r_in)} V {f(top)} A {f(r_in)} {f(r_in)} 0 0 0 {f(cx - r_in)} {f(top)} V {f(foot)} Z")
    return [shackle, rrect(9, 21, 39, 42, 4) + " " + circle(cx, 31.5, 3.5)]


ICONS = {
    "achievements": trophy,
    "gametime": stopwatch,
    "artwork": picture,
    "cheats": cartridge,
    "sync": sync,
    "settings": gear,
    "app": apps,
    "lock": lock,
}


def svg(paths):
    body = "\n".join(f'  <path fill="#FFFFFF" fill-rule="evenodd" d="{d}"/>' for d in paths)
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {GRID} {GRID}" width="{GRID}" height="{GRID}">\n{body}\n</svg>\n'


def xml(paths):
    body = "\n".join(
        f'    <path\n        android:pathData="{d}"\n        android:fillType="evenOdd"\n        android:fillColor="#FFFFFFFF" />'
        for d in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/menu-icons/make_icons.py; edit there. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{GRID}dp"\n    android:height="{GRID}dp"\n'
            f'    android:viewportWidth="{GRID}"\n    android:viewportHeight="{GRID}">\n{body}\n</vector>\n')


def main():
    for name, make in ICONS.items():
        paths = make()
        with open(os.path.join(SVG_DIR, f"menu_icon_{name}.svg"), "w") as out:
            out.write(svg(paths))
        with open(os.path.join(XML_DIR, f"menu_icon_{name}.xml"), "w") as out:
            out.write(xml(paths))
        print(name)


if __name__ == "__main__":
    main()
