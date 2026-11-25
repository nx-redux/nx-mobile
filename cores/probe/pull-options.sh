#!/bin/sh
# nx-mobile - probe one core's option table on a device over adb. GPL-3.0-or-later.
#
#   pull-options.sh <adb> <serial> <so> <coreId> <outdir> [rom]
#
# Pushes $NXPROBE (default output/nxprobe: the Makefile's $(OUTPUT_ROOT)/nxprobe), the core (the shipped jniLibs copy,
# from the Makefile) and the shipped system files ($SYSTEM_DIR, default ../androidApp/src/main/assets/system: the Makefile's ASSETS_DIR) to /data/local/tmp/nxprobe/,
# runs the probe (with the rom as its game when given: FBNeo declares its options in retro_load_game), pulls the JSON, adds soSha256 and probedAt,
# and writes <outdir>/<coreId>.json. PROBE_SKIP_INIT=1 runs nxprobe with NXPROBE_SKIP_INIT=1 (no retro_init). On any failure an existing <outdir>/<coreId>.json is left untouched and the
# script exits 1. Run from cores/ (gmake -C cores options-<core>).
set -u

[ "$#" -ge 5 ] && [ "$#" -le 6 ] || { echo "usage: $0 <adb> <serial> <so> <coreId> <outdir> [rom]" >&2; exit 1; }
ADB=$1 SERIAL=$2 SO=$3 CORE_ID=$4 OUTDIR=$5 ROM=${6:-}
REMOTE=/data/local/tmp/nxprobe
SYSTEM_DIR=${SYSTEM_DIR:-../androidApp/src/main/assets/system}
NXPROBE=${NXPROBE:-output/nxprobe}
envset=
[ -n "${PROBE_SKIP_INIT:-}" ] && envset="NXPROBE_SKIP_INIT=1 "
# The core goes up as <coreId>_libretro_android.so, so the table's "core" field is the coreId
# (mupen64plus_next's artifact carries _gles3).
REMOTE_SO=${CORE_ID}_libretro_android.so

tmp=
fail() {
	echo "options: $CORE_ID failed ($1)" >&2
	"$ADB" -s "$SERIAL" shell rm -rf "$REMOTE" > /dev/null 2>&1
	[ -n "$tmp" ] && rm -f "$tmp"
	exit 1
}

adb() { "$ADB" -s "$SERIAL" "$@"; }

[ -f "$SO" ] || fail "no $SO — gmake $CORE_ID install first"
[ -f "$NXPROBE" ] || fail "no $NXPROBE (gmake nxprobe)"
[ -z "$ROM" ] || [ -f "$ROM" ] || fail "no rom $ROM"

adb shell rm -rf "$REMOTE" > /dev/null || fail "adb shell on $SERIAL"
adb shell mkdir -p "$REMOTE/system" > /dev/null || fail "mkdir $REMOTE"
adb push "$NXPROBE" "$REMOTE/nxprobe" > /dev/null || fail "push nxprobe"
adb shell chmod 755 "$REMOTE/nxprobe" || fail "chmod nxprobe"
adb push "$SO" "$REMOTE/$REMOTE_SO" > /dev/null || fail "push $SO"
if [ -d "$SYSTEM_DIR" ]; then
	adb push "$SYSTEM_DIR/." "$REMOTE/system/" > /dev/null || fail "push $SYSTEM_DIR"
fi

game=
if [ -n "$ROM" ]; then
	# Keep the file name: FBNeo takes the driver (set) name from it.
	name=$(basename "$ROM")
	adb shell mkdir -p "$REMOTE/game" > /dev/null || fail "mkdir $REMOTE/game"
	adb push "$ROM" "$REMOTE/game/$name" > /dev/null || fail "push $ROM"
	# Single-quoted for the device shell, with any ' in the name written as '\''.
	quoted=$(printf '%s' "$name" | sed "s/'/'\\\\''/g")
	game=" 'game/$quoted'"
fi

# adb shell's own exit status is unreliable on older devices, so the exit code travels in the output.
out=$(adb shell "cd $REMOTE && ${envset}./nxprobe ./$REMOTE_SO ./$CORE_ID.json ./system$game; echo EXIT:\$?") \
	|| fail "adb shell run"
printf '%s\n' "$out" | grep -v '^EXIT:' >&2
code=$(printf '%s\n' "$out" | tr -d '\r' | sed -n 's/^EXIT:\([0-9]*\)$/\1/p' | tail -n 1)
[ -n "$code" ] || fail "no exit code from nxprobe"
case "$code" in
	0) ;;
	2) fail "$(basename "$SO") declares no options" ;;
	*) fail "nxprobe exit $code" ;;
esac

tmp=$(mktemp "${TMPDIR:-/tmp}/nxprobe-$CORE_ID.XXXXXX") || fail "mktemp"
adb pull "$REMOTE/$CORE_ID.json" "$tmp" > /dev/null || fail "pull $CORE_ID.json"

sha=$(shasum -a 256 "$SO" | cut -d' ' -f1)
[ -n "$sha" ] || fail "shasum $SO"
python3 -c '
import json, sys
path, sha, day = sys.argv[1:4]
with open(path, encoding="utf-8") as f:
    table = json.load(f)
table["soSha256"] = sha
table["probedAt"] = day
with open(path, "w", encoding="utf-8") as f:
    json.dump(table, f, indent=1, sort_keys=False, ensure_ascii=False)
    f.write("\n")
' "$tmp" "$sha" "$(date +%F)" || fail "stamp $CORE_ID.json"

mkdir -p "$OUTDIR" || fail "mkdir $OUTDIR"
mv "$tmp" "$OUTDIR/$CORE_ID.json" || fail "write $OUTDIR/$CORE_ID.json"
tmp=
chmod 644 "$OUTDIR/$CORE_ID.json"

adb shell rm -rf "$REMOTE" > /dev/null
echo "options: $CORE_ID -> $OUTDIR/$CORE_ID.json"
