# DS Download Play art

Builds the art the DS Download Play row ships with (`androidApp/src/main/assets/nds-dlp/`): 3D box, 2D box,
wheel, screenshot and mix.

Inputs, in a work folder:

- `img/*.png`: ScreenScraper's Nintendo DS **system** media (`systemesListe.php`, system 15): `box3D`,
  `box-tranche-vierge`, `support2D`, `illustration`, `logo-monochrome`. Fetch them with your own dev credentials.
- `cap_menu.png`: a capture of the DS menu from the `nxultra` emulator (2256x2504; the DS screens' positions are at
  the top of `make_art.py`). The one used is kept here.

```
python3 -m venv venv && venv/bin/pip install pillow
venv/bin/python -I make_art.py <work folder>   # writes <work folder>/out/
```

Fonts are macOS's (Arial Rounded Bold, Avenir Next).
