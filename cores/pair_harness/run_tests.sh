#!/bin/sh
# nx-mobile - host checks for the mGBA core's pair mode (patches/mgba/004-pair-cable.patch). GPL-3.0-or-later.
# Builds the libretro core for this machine from the pinned source twice, with every patches/mgba patch ("pair") and without 004 ("stock"), then runs
# pair_harness on your own ROMs. Run through `gmake test-pair`, which passes:
#   MGBA_SRC   a checkout holding the pinned commit (src/mgba)    MGBA_HASH  the pin
#   CMAKE, NINJA                                                   BUILD_DIR  where the builds go
#   PAIR_ROM_A, PAIR_ROM_B  two GBA ROMs (the same file twice is fine). A also runs alone, where the core reads the
#              host's clock: pick one without a cartridge clock (not Pokemon Ruby / Sapphire / Emerald) as A.
#   PAIR_BIOS  optional: a gba_bios.bin for pair_bios_forced (skipped without)
#   PAIR_TRANSFERS=1  optional: also build a debug core and count the link transfers in the pair run
set -eu
D=$(cd "$(dirname "$0")/.." && pwd)
: "${MGBA_SRC:?}" "${MGBA_HASH:?}" "${BUILD_DIR:?}" "${PAIR_ROM_A:?}" "${PAIR_ROM_B:?}"
CMAKE=${CMAKE:-cmake}
NINJA=${NINJA:-ninja}
PAIR_BIOS=${PAIR_BIOS:-}
PAIR_TRANSFERS=${PAIR_TRANSFERS:-}
mkdir -p "$BUILD_DIR"
BUILD_DIR=$(cd "$BUILD_DIR" && pwd)
MGBA_SRC=$(cd "$MGBA_SRC" && pwd)
case $(uname -s) in Darwin) EXT=dylib; LDL= ;; *) EXT=so; LDL=-ldl ;; esac

# build_core <name> <build type> <patch>...: a fresh checkout of the pin with those patches (kept while they and the
# NXRedux sources they compile in are unchanged), built as mgba_libretro.$EXT in $BUILD_DIR/<name>.
build_core() {
	name=$1 type=$2
	shift 2
	src=$BUILD_DIR/src-$name
	stamp=$(cat "$@" "$D"/sio_lockstep/*.[ch] "$D"/gba_wifi_link/*.[ch] | { cat; echo "$MGBA_HASH $type"; } | shasum | cut -d' ' -f1)
	if [ "$(cat "$src/.pair-stamp" 2>/dev/null)" != "$stamp" ]; then
		rm -rf "${src:?}" "${BUILD_DIR:?}/$name"
		git clone --quiet --shared --no-checkout "$MGBA_SRC" "$src"
		git -C "$src" checkout --quiet -f "$MGBA_HASH"
		for p in "$@"; do
			git -C "$src" apply -p1 "$p"
		done
		echo "$stamp" > "$src/.pair-stamp"
	fi
	"$CMAKE" -S "$src" -B "$BUILD_DIR/$name" -G Ninja -DCMAKE_MAKE_PROGRAM="$NINJA" -DCMAKE_BUILD_TYPE="$type" \
		-DBUILD_LIBRETRO=ON -DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_GL=OFF -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF \
		-DENABLE_SCRIPTING=OFF -DUSE_SQLITE3=OFF -DUSE_FFMPEG=OFF -DUSE_LUA=OFF -DUSE_EDITLINE=OFF \
		-DUSE_DISCORD_RPC=OFF -DUSE_JSON_C=OFF -DUSE_EPOXY=OFF -DUSE_LIBZIP=OFF -DUSE_MINIZIP=OFF -DUSE_PNG=OFF \
		-DUSE_ZLIB=OFF -DUSE_ELF=OFF -DUSE_LZMA=OFF -DUSE_FREETYPE=OFF \
		-DNX_SIOLS_DIR="$D/sio_lockstep" -DNX_GWL_DIR="$D/gba_wifi_link" > "$BUILD_DIR/$name.configure.log"
	"$CMAKE" --build "$BUILD_DIR/$name" --target mgba_libretro > "$BUILD_DIR/$name.build.log" ||
		{ tail -20 "$BUILD_DIR/$name.build.log"; exit 1; }
}

# The glob sorts by name: the order the core build applies them in
ALL_PATCHES=
STOCK_PATCHES=
for p in "$D"/patches/mgba/*.patch; do
	ALL_PATCHES="$ALL_PATCHES $p"
	[ "${p##*/}" = 004-pair-cable.patch ] || STOCK_PATCHES="$STOCK_PATCHES $p"
done
echo "test-pair: building the host cores in $BUILD_DIR"
# shellcheck disable=SC2086 # patch paths have no spaces (cores/patches/mgba)
build_core pair Release $ALL_PATCHES
# shellcheck disable=SC2086
build_core stock Release $STOCK_PATCHES
cc -std=gnu99 -O2 -Wall -Wextra -Werror -I"$D/probe" "$D/pair_harness/pair_harness.c" -o "$BUILD_DIR/pair_harness" $LDL

H=$BUILD_DIR/pair_harness
PAIR=$BUILD_DIR/pair/mgba_libretro.$EXT
STOCK=$BUILD_DIR/stock/mgba_libretro.$EXT
T=$BUILD_DIR/run
rm -rf "$T"
mkdir -p "$T"
failed=0
pass() { echo "PASS $1"; }
fail() { echo "FAIL $1: $2"; failed=1; }
same() { cmp -s "$1" "$2"; }
# pair <out> <content ROM> <other ROM> [harness options]: a pair-mode run (hash: nx_pair_hash). A harness that
# fails leaves its output short, which the check reports; its errors stay in <out>.log.
pair() {
	out=$1 rom=$2 other=$3
	shift 3
	"$H" --core "$PAIR" --rom "$rom" --hash pair --opt mgba_pair=ON --opt "mgba_pair_other_rom=$other" \
		--opt mgba_pair_rtc=1762646400 "$@" > "$out" 2> "$out.log" || true
}

# Step 2's baseline: no pair mode, A alone, the serialize hash every 60 frames.
"$H" --core "$PAIR" --rom "$PAIR_ROM_A" --frames 600 --every 60 > "$T/solo1" 2> "$T/solo1.log" || true
"$H" --core "$PAIR" --rom "$PAIR_ROM_A" --frames 600 --every 60 > "$T/solo2" 2> "$T/solo2.log" || true
if same "$T/solo1" "$T/solo2" && [ -s "$T/solo1" ]; then pass single_runs_identical; else fail single_runs_identical "two runs differ (a game with a clock as PAIR_ROM_A?)"; fi

pair "$T/pair1" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 3600 --every 60
pair "$T/pair2" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 3600 --every 60
if same "$T/pair1" "$T/pair2" && [ "$(wc -l < "$T/pair1")" -eq 60 ]; then
	pass pair_runs_identical
else
	fail pair_runs_identical "two runs differ (or did not finish)"
fi

pair "$T/ref" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 1600 --every 20 --state-out "1000:$T/state1000"
pair "$T/loaded" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 20 --first-frame 1001 --state-in "$T/state1000"
awk '$2 >= 1000' "$T/ref" > "$T/ref.after"
if same "$T/ref.after" "$T/loaded" && [ "$(wc -l < "$T/loaded")" -eq 31 ]; then
	pass pair_serialize_roundtrip
else
	fail pair_serialize_roundtrip "hashes after the load differ from the run that saved"
fi

# Each phone's view of the same pair: phone 1 owns GBA 1 (A), phone 2 owns GBA 2 (B). Marked saves show whose
# save RAM each one exposes; its contents and size must be what B (or A) alone exposes.
mark_save() { { printf 'NX-PAIR-SAVE-%s' "$2"; head -c 131058 /dev/zero | tr '\0' '\377'; } > "$1"; }
mark_save "$T/a.sav" A
mark_save "$T/b.sav" B
pair "$T/local1" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --save "$T/a.sav" \
	--opt "mgba_pair_other_save=$T/b.sav" --save-ram-at 1 --save-ram-at 600
pair "$T/local2" "$PAIR_ROM_B" "$PAIR_ROM_A" --frames 600 --every 60 --save "$T/b.sav" --opt mgba_pair_local=2 \
	--opt "mgba_pair_other_save=$T/a.sav" --save-ram-at 1 --save-ram-at 600
"$H" --core "$PAIR" --rom "$PAIR_ROM_B" --frames 600 --every 600 --save "$T/b.sav" --save-ram-at 1 --save-ram-at 600 \
	> "$T/soloB" 2> "$T/soloB.log" || true
"$H" --core "$PAIR" --rom "$PAIR_ROM_A" --frames 600 --every 600 --save "$T/a.sav" --save-ram-at 1 --save-ram-at 600 \
	> "$T/soloA" 2> "$T/soloA.log" || true
for f in local1 local2 soloA soloB; do
	grep '^frame' "$T/$f" > "$T/$f.frames" || true
	grep '^save_ram' "$T/$f" > "$T/$f.save" || true
done
if ! same "$T/local1.frames" "$T/local2.frames" || [ ! -s "$T/local1.frames" ]; then
	fail pair_local_switch "the two phones' views compute different pairs"
elif ! same "$T/local2.save" "$T/soloB.save" || ! same "$T/local1.save" "$T/soloA.save"; then
	fail pair_local_switch "save RAM is not the local GBA's: $(paste "$T/local2.save" "$T/soloB.save" | tr '\n' ' ')"
else
	pass pair_local_switch
fi

mkdir -p "$T/system"
[ -z "$PAIR_BIOS" ] || cp "$PAIR_BIOS" "$T/system/gba_bios.bin"
if [ -n "$PAIR_BIOS" ]; then
	pair "$T/real1" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --system "$T/system" --opt mgba_pair_bios=real
	pair "$T/real2" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --system "$T/system" --opt mgba_pair_bios=real
	pair "$T/hle1" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --system "$T/system" --opt mgba_pair_bios=hle \
		--opt mgba_use_bios=ON
	pair "$T/hle2" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --system "$T/system" --opt mgba_pair_bios=hle
	if ! same "$T/real1" "$T/real2" || ! same "$T/hle1" "$T/hle2"; then
		fail pair_bios_forced "the same BIOS choice gave different hashes"
	elif same "$T/real1" "$T/hle1"; then
		fail pair_bios_forced "real and HLE BIOS gave the same hashes"
	else
		pass pair_bios_forced
	fi
else
	echo "SKIP pair_bios_forced: set PAIR_BIOS to a gba_bios.bin"
fi

# Each phone's own Settings must not reach the pair: opposite values of every option that changes emulation, with
# opposite directions pressed together, and a BIOS in the system directory that HLE must not pick up.
pair "$T/setA" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --opposing 1 --system "$T/system" \
	--opt mgba_skip_bios=OFF --opt mgba_use_bios=ON --opt mgba_allow_opposing_directions=yes --opt mgba_force_gbp=ON \
	--opt "mgba_idle_optimization=Detect and Remove" --opt mgba_frameskip=fixed_interval --opt mgba_frameskip_interval=2
pair "$T/setB" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --opposing 1 --system "$T/system" \
	--opt mgba_skip_bios=ON --opt mgba_use_bios=OFF --opt mgba_allow_opposing_directions=no --opt mgba_force_gbp=OFF \
	--opt "mgba_idle_optimization=Remove Known"
settings_ok=1
same "$T/setA" "$T/setB" && [ -s "$T/setA" ] || settings_ok=0
if [ -n "$PAIR_BIOS" ]; then
	pair "$T/setRealA" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --system "$T/system" \
		--opt mgba_pair_bios=real --opt mgba_skip_bios=OFF
	pair "$T/setRealB" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 600 --every 60 --system "$T/system" \
		--opt mgba_pair_bios=real --opt mgba_skip_bios=ON
	same "$T/setRealA" "$T/setRealB" && [ -s "$T/setRealA" ] || settings_ok=0
fi
if [ "$settings_ok" = 1 ]; then pass pair_settings_pinned; else fail pair_settings_pinned "options changed the hashes"; fi

# A reset re-reads a clock cart's saved RTC in the host's time zone; the phones may be in different ones.
(TZ=UTC; export TZ; pair "$T/resetUtc" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 1100 --every 60 --reset-at 500)
(TZ=Asia/Kuala_Lumpur; export TZ; pair "$T/resetKl" "$PAIR_ROM_A" "$PAIR_ROM_B" --frames 1100 --every 60 --reset-at 500)
if same "$T/resetUtc" "$T/resetKl" && [ "$(wc -l < "$T/resetUtc")" -eq 19 ]; then
	pass pair_reset_tz_independent
else
	fail pair_reset_tz_independent "a reset in another time zone changed the hashes"
fi

"$H" --core "$STOCK" --rom "$PAIR_ROM_A" --frames 600 --every 60 > "$T/stock" 2> "$T/stock.log" || true
"$H" --core "$PAIR" --rom "$PAIR_ROM_A" --frames 600 --every 60 --opt mgba_pair=OFF > "$T/off" 2> "$T/off.log" || true
if same "$T/stock" "$T/solo1" && same "$T/off" "$T/solo1"; then
	pass pair_off_is_stock
else
	fail pair_off_is_stock "pair=OFF or no option differs from the core without 004 (a game with a clock as PAIR_ROM_A?)"
fi

if [ "$PAIR_TRANSFERS" = 1 ]; then
	# shellcheck disable=SC2086
	build_core pair-debug Debug $ALL_PATCHES
	"$H" --core "$BUILD_DIR/pair-debug/mgba_libretro.$EXT" --rom "$PAIR_ROM_A" --hash pair --opt mgba_pair=ON \
		--opt "mgba_pair_other_rom=$PAIR_ROM_B" --opt mgba_pair_rtc=1762646400 --frames 3600 --every 3600 \
		--count-log "Transfer starting" > "$T/transfers" 2> "$T/transfers.log" || true
	echo "link transfers in the 3600-frame pair run: $(sed -n 's/^log_count //p' "$T/transfers")"
fi

exit $failed
