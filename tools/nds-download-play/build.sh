#!/bin/sh
# Builds the DS Download Play client in devkitPro's devkitARM image (the 3DS client's pin) and puts it where the app
# ships it.
set -e
cd "$(dirname "$0")"
docker run --rm -v "$PWD":/src -w /src \
  devkitpro/devkitarm@sha256:116afba8df8453961de2936ffab20dd441edf4d682856c1ec8b0e53d7ed0bbf5 make
mkdir -p ../../androidApp/src/main/assets/nds-dlp
cp download-play.nds ../../androidApp/src/main/assets/nds-dlp/download-play.nds
echo "built ... download-play.nds"
