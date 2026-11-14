#!/usr/bin/env python3
"""Copies the bundled slang presets out of a libretro slang-shaders checkout into the app's assets.

    python3 tools/bundle_slang.py <slang-shaders checkout>

Each preset in PRESETS is copied to androidApp/src/main/assets/shaders/slang/ with every file it needs (its passes,
their #includes, its lookup textures, the presets it #references), unmodified and in the upstream layout; the folder
is rebuilt whole. The "libretro slang-shaders" section of the shaders' LICENSES.md is rewritten to list the files and
the checkout's commit. BundledSlangTest then checks every preset is complete.
"""

import os
import re
import shutil
import subprocess
import sys

PRESETS = [
    # CRT
    "crt/crt-easymode", "crt/crt-geom", "crt/crt-lottes", "crt/crt-pi", "crt/zfast-crt", "crt/newpixie-crt",
    "crt/crt-guest-advanced", "crt/crt-guest-advanced-fast", "crt/crt-guest-advanced-ntsc", "crt/crt-guest-advanced-hd",
    "crt/crt-royale", "presets/crt-royale-kurozumi", "crt/crt-hyllian", "crt/crt-aperture", "crt/crt-consumer",
    "crt/crt-mattias", "crt/crt-gdv-mini", "scanlines/scanline", "scanlines/res-independent-scanlines",
    # Handheld
    "handheld/dot", "handheld/gameboy", "handheld/lcd-grid-v2", "handheld/lcd1x", "handheld/lcd3x", "handheld/retro-v2",
    "handheld/sameboy-lcd", "handheld/zfast-lcd", "handheld/authentic_gbc", "handheld/agb001",
    "handheld/gameboy-advance-dot-matrix",
    # Smoothing
    "edge-smoothing/scalefx/scalefx", "edge-smoothing/scalenx/scale3x", "edge-smoothing/xbrz/xbrz-freescale",
    "anti-aliasing/aa-shader-4.0", "interpolation/bicubic", "interpolation/jinc2", "pixel-art-scaling/edge1pixel",
    "pixel-art-scaling/pixellate", "pixel-art-scaling/sharp-bilinear", "pixel-art-scaling/sharp-shimmerless",
    "dithering/mdapt", "dithering/gdapt",
    # Signal
    "ntsc/ntsc-adaptive", "ntsc/ntsc-256px-svideo", "vhs/vhs", "vhs/ntsc-vcr",
    # Effects
    "motionblur/response-time", "motionblur/mix_frames", "misc/image-adjustment", "misc/night-mode",
]

REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SHADERS = os.path.join(REPO, "androidApp", "src", "main", "assets", "shaders")
SECTION = "## libretro slang-shaders"


def closure(library, path, seen):
    """Adds [path] (relative to the library) and everything it names to [seen]."""
    if path in seen:
        return
    full = os.path.join(library, path)
    if not os.path.isfile(full):
        raise SystemExit("missing in the library: %s" % path)
    seen.add(path)
    if path.lower().endswith((".png", ".jpg", ".jpeg", ".tga")):
        return
    folder = os.path.dirname(path)
    text = open(full, errors="replace").read()
    if path.endswith(".slangp"):
        for m in re.finditer(r'^\s*#reference\s+"?([^"\n]+)"?', text, re.M):
            closure(library, os.path.normpath(os.path.join(folder, m.group(1).strip())), seen)
        for m in re.finditer(r'^\s*\w+\s*=\s*"?([^"\n#]+?\.(?:slang|png|jpe?g|tga))"?\s*$', text, re.M | re.I):
            closure(library, os.path.normpath(os.path.join(folder, m.group(1).strip())), seen)
    else:
        for m in re.finditer(r'^\s*#(include|pragma\s+include_optional)\s+"([^"]+)"', text, re.M):
            target = os.path.normpath(os.path.join(folder, m.group(2)))
            # An optional include that the library does not have is left out, as the app does.
            if m.group(1) == "include" or os.path.isfile(os.path.join(library, target)):
                closure(library, target, seen)


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    library = sys.argv[1]
    commit = subprocess.run(["git", "-C", library, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    if not commit:
        raise SystemExit("%s is not a git checkout" % library)
    seen = set()
    for preset in PRESETS:
        closure(library, preset + ".slangp", seen)
    out = os.path.join(SHADERS, "slang")
    if os.path.isdir(out):
        shutil.rmtree(out)
    for path in sorted(seen):
        os.makedirs(os.path.dirname(os.path.join(out, path)), exist_ok=True)
        shutil.copyfile(os.path.join(library, path), os.path.join(out, path))

    licenses = os.path.join(SHADERS, "LICENSES.md")
    text = open(licenses).read()
    head = text[:text.index(SECTION)] if SECTION in text else text.rstrip("\n") + "\n\n"
    section = "%s\n\nFrom https://github.com/libretro/slang-shaders (master, commit %s),\ncopied under `slang/` with " \
              "the upstream relative layout:\n\n%s" % (SECTION, commit, "".join("- `slang/%s`\n" % p for p in sorted(seen)))
    open(licenses, "w").write(head + section)
    size = sum(os.path.getsize(os.path.join(out, p)) for p in seen)
    print("%d presets, %d files, %d KB, slang-shaders %s" % (len(PRESETS), len(seen), size // 1024, commit[:8]))


if __name__ == "__main__":
    main()
