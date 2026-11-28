#!/bin/sh
# Builds the DS Download Play client in devkitPro's devkitARM image (the 3DS client's pin) and puts it where the app
# ships it.
set -e
cd "$(dirname "$0")"
# Its text is the app's MiSans, which the app build fetches (./gradlew :shared:fetchMiSans fetchMiSansRegular).
FONTS=../../shared/src/commonMain/composeResources/font
if [ ! -f "$FONTS/misans_regular.ttf" ] || [ ! -f "$FONTS/misans_semibold.ttf" ]; then
  echo "MiSans is missing from $FONTS: build the app once (it fetches the font), then run this again." >&2
  exit 1
fi
[ -x .venv/bin/python ] || { python3 -m venv .venv && .venv/bin/pip install -q pillow; }
.venv/bin/python -I gen_font.py "$FONTS/misans_regular.ttf" "$FONTS/misans_semibold.ttf" source/font_data.c
# Built from a copy inside the container: make there misses a source edited on the host just before, and the shared
# folder can refuse a build directory deleted a moment earlier.
docker run --rm -v "$PWD":/src \
  devkitpro/devkitarm@sha256:116afba8df8453961de2936ffab20dd441edf4d682856c1ec8b0e53d7ed0bbf5 \
  sh -c 'mkdir /work && cp -r /src/Makefile /src/source /work/ && cd /work && make && cp download-play.nds /src/'
mkdir -p ../../androidApp/src/main/assets/nds-dlp
cp download-play.nds ../../androidApp/src/main/assets/nds-dlp/download-play.nds
echo "built ... download-play.nds"
