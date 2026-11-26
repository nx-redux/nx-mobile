"""Converts the console logo SVGs in src/ to the menu's Android vector drawables (menu_logo_<id>.xml).

Sources: Dan Patrick's "Platform Logos (Professionally Redrawn)", v2.1 SVG set, the "Light - Just White" variants
(https://archive.org/details/console-logos-professionally-redrawn-plus-official-versions); mapping.tsv names the pack
member each src/<id>.svg was taken from. His terms: he does not own the rights to the logos, they are never to be sold,
and credit is appreciated (THIRD_PARTY_ART.md). The logos are trademarks of their owners.

What it does: every visible filled shape (all white) becomes one <path> with its transforms baked into the coordinates
(svgelements' abs(Path)), arcs as cubics, 2 decimals; one white fill (#FFFFFFFF), evenodd kept; Pico-8's pixel squares
merged into one path so no seams show. The drawable is sized to fit 512 x 128 dp.
Doom (menu_logo_prboom.xml) is not in the pack: it is the project's own drawing, traced from an earlier PNG.
Saturn (src/saturn.svg) is not in the pack either: user-supplied, recoloured white, its viewBox trimmed to the logo.

    pip install svgelements
    python3 tools/console-logos/convert.py          # writes shared/src/commonMain/composeResources/drawable/
"""
import os
from svgelements import SVG, Shape, Path, Move, Close, Line, CubicBezier, QuadraticBezier, Arc

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'shared', 'src', 'commonMain', 'composeResources', 'drawable')


def n(v):
    s = f'{v:.2f}'.rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def pt(p):
    return f'{n(p.x)} {n(p.y)}'


def d_of(path):
    out = []
    for s in path:
        if isinstance(s, Move): out.append('M ' + pt(s.end))
        elif isinstance(s, Close): out.append('Z')
        elif isinstance(s, Line): out.append('L ' + pt(s.end))
        elif isinstance(s, CubicBezier): out.append(f'C {pt(s.control1)} {pt(s.control2)} {pt(s.end)}')
        elif isinstance(s, QuadraticBezier): out.append(f'Q {pt(s.control)} {pt(s.end)}')
        elif isinstance(s, Arc):
            for cb in s.as_cubic_curves(): out.append(f'C {pt(cb.control1)} {pt(cb.control2)} {pt(cb.end)}')
        else: raise Exception(type(s))
    return ' '.join(out)


# Logos not from the pack: their own header, credited in THIRD_PARTY_ART.md.
USER_SUPPLIED = {'saturn': '<!-- Sega Saturn logo, user-supplied. Sega Saturn is a trademark of Sega. -->'}


def convert(k):
    svg = SVG.parse(os.path.join(HERE, 'src', f'{k}.svg'))
    vb = svg.viewbox
    paths = []
    for e in svg.elements():
        if not isinstance(e, Shape): continue
        if e.values.get('display') == 'none' or e.values.get('visibility') == 'hidden': continue
        if e.fill is None or e.fill.value is None: continue
        assert str(e.fill).lower() in ('#ffffff', 'white'), (k, str(e.fill))
        p = abs(Path(e))
        if len(p) == 0: continue
        paths.append((d_of(p), e.values.get('fill-rule', 'nonzero')))
    if k == 'p8':  # 99 pixel rects: one path, no seams between squares
        paths = [(' '.join(d for d, _ in paths), 'nonzero')]
    s = min(512 / vb.width, 128 / vb.height)
    x = ['<?xml version="1.0" encoding="utf-8"?>',
         USER_SUPPLIED.get(k, '<!-- Logo by Dan Patrick (Platform Logos, Professionally Redrawn). Never to be sold. -->'),
         '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         f'    android:width="{round(vb.width * s)}dp"', f'    android:height="{round(vb.height * s)}dp"',
         f'    android:viewportWidth="{n(vb.width)}"', f'    android:viewportHeight="{n(vb.height)}">']
    for d, fr in paths:
        x.append('    <path')
        x.append(f'        android:pathData="{d}"')
        if fr == 'evenodd': x.append('        android:fillType="evenOdd"')
        x.append('        android:fillColor="#FFFFFFFF" />')
    x.append('</vector>')
    with open(os.path.join(OUT, f'menu_logo_{k}.xml'), 'w') as f: f.write('\n'.join(x) + '\n')


if __name__ == '__main__':
    for name in sorted(os.listdir(os.path.join(HERE, 'src'))):
        if name.endswith('.svg'): convert(name[:-4])
