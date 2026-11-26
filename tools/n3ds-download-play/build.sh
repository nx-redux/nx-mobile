#!/bin/sh
# Builds the 3DS Download Play client in devkitPro's devkitARM image (pinned) and puts it where the app ships it.
set -e
cd "$(dirname "$0")"
docker run --rm -v "$PWD":/src -w /src \
  devkitpro/devkitarm@sha256:116afba8df8453961de2936ffab20dd441edf4d682856c1ec8b0e53d7ed0bbf5 make
mkdir -p ../../androidApp/src/main/assets/n3ds-dlp
cp download-play.3dsx ../../androidApp/src/main/assets/n3ds-dlp/download-play.3dsx
echo "built ... download-play.3dsx"
