# Third-party art

Main menu console logos (`shared/src/commonMain/composeResources/drawable/menu_logo_*.xml`, except the Android,
3DS and Doom logos below) are converted from Dan Patrick's professionally redrawn console logos, v2.1 SVG
set ("Light - Just White"):
https://archive.org/details/console-logos-professionally-redrawn-plus-official-versions

His terms: "I don't own the rights to the logos"; they are "NEVER to be sold in any way"; credit is appreciated. These
files are not under the project's GPL-3.0. Changes: converted to Android vector drawables, single white fill,
transforms baked (`tools/console-logos/`, which holds the source SVGs and the converter). Console names and logos are
trademarks of their owners.

The Doom logo (`menu_logo_prboom.xml`) is the project's own drawing, traced from the earlier PNG. Doom is a trademark of its owner.

## Vector logos (`menu_logo_android.xml`, `menu_logo_3ds.xml`)

Converted from SVG to Android vector drawables (paths unchanged, filled white):

- Android logo: Android wordmark and robot head (user-supplied). The Android robot is reproduced or modified from work
  created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.
  Android is a trademark of Google LLC.
- 3DS logo: Nintendo 3DS logo (user-supplied). Nintendo 3DS is a trademark of Nintendo.

## Tab row icons (`nav_icon_*.xml`)

Google's Material Icons "search", "history", "delete", "filter_list", "create_new_folder", "call_merge", "call_split", "sd_card" and
"grid_view", under the Apache License 2.0 (https://github.com/google/material-design-icons). Paths unchanged, filled white (grid_view drawn
at 80% scale).

## Controller art (`menu_pad_*.webp`)

Console controllers and handhelds drawn in the main menu's Consoles tab, from ScreenScraper (https://www.screenscraper.fr),
user-supplied. Changes: cables cut, cropped, fitted to 640 px, converted to WebP (`tools/controller-art/make_pads.py`).
Console names and hardware designs are trademarks of their owners; Commodore and VIC-20 are trademarks of their owners.

The Android console's pad (`menu_pad_android.webp`) is a controller with no brand (`controller.png`, saved as
`android-controller.png`), prepared first by `tools/controller-art/prep_controller.py` (the right stick's cap, holed by
the background removal, replaced with the left stick's).

One image is a manufacturer's product image, same changes:

- `menu_pad_fbn.webp` (Arcade): a Competition Pro joystick (`competition-pro.png`). Source: Speedlink product image.

Competition Pro is a trademark of its owner.

## Overlays (`androidApp/src/main/assets/overlays/`)

Shin's (KrutzOtrem) Trimui Brick overlays, https://github.com/KrutzOtrem/Trimui-Brick-Overlays, MIT licence (text and
notes in `LICENSES/trimui-brick-overlays.txt`, shipped in the APK as `licenses/trimui-brick-overlays.txt`). Brought in
through nx-redux, unchanged.

## Font (`misans_semibold.ttf`, `misans_regular.ttf`)

MiSans Latin Semibold and Regular, © Beijing Xiaomi Mobile Software Co., Ltd., used under Xiaomi's MiSans terms
(https://hyperos.mi.com/font/en/download/): software may embed the font and must note that it does
(https://hyperos.mi.com/font/en/faq/; Settings → System → About shows "Font: MiSans by Xiaomi", and its Licenses group names the font); the font files may not be
distributed on their own. So the TTFs are not in this repository:
the build fetches them from Xiaomi (`shared/build.gradle.kts`, `fetchMiSans`, `fetchMiSansRegular`). Not under the project's GPL-3.0.
