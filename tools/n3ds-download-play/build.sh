#!/bin/sh
# Builds the 3DS Download Play client in devkitPro's devkitARM image (pinned) and puts it where the app ships it.
# Its text is the app's MiSans, which the app build fetches (./gradlew :shared:fetchMiSans fetchMiSansRegular).
set -e
cd "$(dirname "$0")"
FONTS=../../shared/src/commonMain/composeResources/font
if [ ! -f "$FONTS/misans_regular.ttf" ] || [ ! -f "$FONTS/misans_semibold.ttf" ]; then
  echo "MiSans is missing from $FONTS: build the app once (it fetches the font), then run this again." >&2
  exit 1
fi
[ -x .venv/bin/python ] || { python3 -m venv .venv && .venv/bin/pip install -q pillow; }
.venv/bin/python -I gen_font.py "$FONTS/misans_regular.ttf" "$FONTS/misans_semibold.ttf" source/font_data.c
docker run --rm -v "$PWD":/src -w /src \
  devkitpro/devkitarm@sha256:116afba8df8453961de2936ffab20dd441edf4d682856c1ec8b0e53d7ed0bbf5 make
mkdir -p ../../androidApp/src/main/assets/n3ds-dlp
cp download-play.3dsx ../../androidApp/src/main/assets/n3ds-dlp/download-play.3dsx
echo "built ... download-play.3dsx"
