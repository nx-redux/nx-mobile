# Libretro cores for nx-mobile, built from pinned source with the Android NDK.
# Pattern borrowed from nx-redux workspace/all/cores/Makefile. Run with GNU make >= 3.82 (gmake on macOS).
ifneq (3.82,$(firstword $(sort $(MAKE_VERSION) 3.82)))
$(error GNU make >= 3.82 required; on macOS run gmake (brew install make))
endif

# ---- tools -----------------------------------------------------------------
NDK_VERSION ?= 26.1.10909125
NDK ?= $(or $(ANDROID_NDK_HOME),$(if $(ANDROID_HOME),$(ANDROID_HOME)/ndk/$(NDK_VERSION),$(HOME)/Library/Android/sdk/ndk/$(NDK_VERSION)))
SDK ?= $(or $(ANDROID_HOME),$(HOME)/Library/Android/sdk)
CMAKE ?= $(SDK)/cmake/3.22.1/bin/cmake
NINJA ?= $(SDK)/cmake/3.22.1/bin/ninja
UNAME_S := $(shell uname -s)
HOST_TAG := $(if $(filter Darwin,$(UNAME_S)),darwin-x86_64,linux-x86_64)
LLVM_BIN := $(NDK)/toolchains/llvm/prebuilt/$(HOST_TAG)/bin
NDK_BUILD := $(NDK)/ndk-build
STRIP := $(LLVM_BIN)/llvm-strip
READELF := $(LLVM_BIN)/llvm-readelf
PROCS ?= -j$(shell sysctl -n hw.ncpu 2>/dev/null || nproc)
ABI := arm64-v8a
PLATFORM := android-26
PAGE_LDFLAGS := -Wl,-z,max-page-size=16384

SRC_ROOT ?= src
OUTPUT_ROOT ?= output
BUILD_ROOT ?= build
JNILIBS_DIR ?= ../androidApp/src/main/jniLibs/$(ABI)
ASSETS_DIR ?= ../androidApp/src/main/assets/system
PATCH = git apply

# ---- option tables (gmake options): each core's declared options, probed on a device ----
# nxprobe (probe/) runs on arm64 Android, so options-<core> pushes it with the core to ADB_SERIAL. It probes the shipped
# copy in $(JNILIBS_DIR) (gmake install) and never builds a core, so a table describes the binary in the APK and its
# soSha256 is that file's. Recommended target:
# the nx16k AVD (arm64, 16 KB pages). A core that declares its options in retro_load_game (FBNeo, FCEUmm, melonDS DS)
# needs a game: PROBE_ROM_<core>=<path> passes one to options-<core> (from your own card copy; never committed).
# options-fbneo requires it (an arcade set zip); FBNEO_PROBE_ROM is the older name for PROBE_ROM_fbneo.
# Keep PROBE_ROM_fbneo a Neo Geo set: the table's neogeo section (Neo-Geo mode, memory card) comes from it.
# See docs/superpowers/specs/2026-09-29-emulator-settings-design.md, section 1.
ADB ?= $(SDK)/platform-tools/adb
ADB_SERIAL ?=
FBNEO_PROBE_ROM ?=
PROBE_ROM_fbneo ?= $(FBNEO_PROBE_ROM)
# PPSSPP declares its table in retro_set_environment, and its retro_init probes Vulkan, which crashes a shell process
# (the vendor linker namespace is an app's): its probe skips retro_init. nxprobe ignores the skip when a game is given
# (PROBE_ROM_<core>): a game needs an initialised core.
PROBE_SKIP_INIT_ppsspp = 1
OPTIONS_DIR = ../androidApp/src/main/assets/core-options
ifneq (,$(filter options options-%,$(MAKECMDGOALS)))
ifeq (,$(strip $(ADB_SERIAL)))
$(error set ADB_SERIAL (e.g. the nx16k AVD: emulator-5556))
endif
endif
ifneq (,$(filter options-fbneo,$(MAKECMDGOALS)))
ifeq (,$(strip $(PROBE_ROM_fbneo)))
$(error set PROBE_ROM_fbneo (or FBNEO_PROBE_ROM) to an arcade set zip)
endif
endif

# A bare `gmake` builds the cores (not check-tools, which is the first rule below).
.DEFAULT_GOAL := cores

.PHONY: all cores cores-json verify verify-options check-licenses install clean nuke check-tools nxprobe options test-sio-lockstep

# The link cable state machine's host tests (cores/sio_lockstep/tests).
test-sio-lockstep:
	sh sio_lockstep/tests/run_tests.sh

check-tools:
	@test -x $(NDK_BUILD) || (echo "NDK not found at $(NDK). Set ANDROID_NDK_HOME." && exit 1)
	@test -x $(CMAKE) || (echo "cmake not found at $(CMAKE). Set CMAKE=." && exit 1)
	@test -x $(NINJA) || (echo "ninja not found at $(NINJA). Set NINJA=." && exit 1)
	@test -x $(READELF) || (echo "llvm-readelf not found at $(READELF)." && exit 1)
	@git --version >/dev/null
	@echo "tools ok: NDK=$(NDK)"

# ---- core list and per-core variables ------------------------------------
# <core>_LICENSE lists the licence files in the core's pinned checkout; the build concatenates them into
# output/licenses/<core>.txt and the app ships that as the asset licenses/<core>.txt. A core without one (fbneo, melondsds,
# flycast) has a curated ../LICENSES/<core>.txt instead. check-licenses fails when a core has neither.
CORES = gambatte fceumm snes9x genesis_plus_gx mgba melondsds mupen64plus_next ppsspp \
  race mednafen_pce_fast mednafen_vb handy stella2014 prosystem a5200 mednafen_wswan mednafen_supafaust pokemini fake08 prboom gpsp pcsx_rearmed \
  swanstation picodrive gearcoleco fbneo flycast azahar dolphin armsx2

gambatte_REPO = https://github.com/libretro/gambatte-libretro
gambatte_HASH = d9d6cd06382d1ced30de34d56d3609452323dab1
gambatte_LICENSE = COPYING
gambatte_KIND = ndkbuild
gambatte_BUILD_PATH = libgambatte/libretro
# libgambatte/libretro/jni/Android.mk defines LOCAL_MODULE := retro -> libs/arm64-v8a/libretro.so
gambatte_ARTIFACT = libs/$(ABI)/libretro.so
# patches/gambatte/002: nx-redux's async slave reply for the network link cable (a service thread answers a master's
# transfer at once from the cached SB byte; without it link-heavy games stutter on both sides). The wire is unchanged.

fceumm_REPO = https://github.com/libretro/libretro-fceumm
fceumm_HASH = 236ccdfc911e84c60fea6b9d0699c2d440a8de14
fceumm_LICENSE = Copying src/ntsc/license.txt
fceumm_KIND = ndkbuild
# jni/Android.mk lives at the repo root for this pin (not src/drivers/libretro),
# so the directory containing jni/ is the repo root -> BUILD_PATH = .
fceumm_BUILD_PATH = .
# jni/Android.mk defines LOCAL_MODULE := retro -> libs/arm64-v8a/libretro.so
fceumm_ARTIFACT = libs/$(ABI)/libretro.so

# patches/snes9x/0001 (nx-redux's): retro_cheat_set copies the code to the heap; database codes overflowed its 256-byte
# stack buffer.
snes9x_REPO = https://github.com/libretro/snes9x
snes9x_HASH = fae2fea08f74180759ef540ee94259213f503480
snes9x_LICENSE = LICENSE
snes9x_KIND = ndkbuild
snes9x_BUILD_PATH = libretro
# libretro/jni/Android.mk defines LOCAL_MODULE := retro -> libs/arm64-v8a/libretro.so
snes9x_ARTIFACT = libs/$(ABI)/libretro.so

# patches/genesis_plus_gx/0001: retro_cheat_set heap-copies the code and stops at MAX_CHEATS parts; the 151st part
# wrote past cheatlist[] and codes over 255 chars were cut.
genesis_plus_gx_REPO = https://github.com/libretro/Genesis-Plus-GX
genesis_plus_gx_HASH = c2838c7dc4236fc2fe94e5dbd08b41486067918e
genesis_plus_gx_LICENSE = LICENSE.txt core/ntsc/license.txt
genesis_plus_gx_KIND = ndkbuild
genesis_plus_gx_BUILD_PATH = libretro
# libretro/jni/Android.mk defines LOCAL_MODULE := retro -> libs/arm64-v8a/libretro.so
genesis_plus_gx_ARTIFACT = libs/$(ABI)/libretro.so

mgba_REPO = https://github.com/libretro/mgba
mgba_HASH = 7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6
mgba_LICENSE = LICENSE
# mgba ships no libretro Android.mk (no jni/ anywhere in the tree). Its libretro
# core is a CMake target defined in the top-level CMakeLists.txt, gated on
# -DBUILD_LIBRETRO=ON, target ${BINARY_NAME}_libretro (BINARY_NAME=mgba), built
# as a SHARED lib with empty PREFIX -> mgba_libretro.so in the build root.
# USE_SQLITE3=OFF: the host pkg-config finds the build machine's sqlite3 and mgba adds its
# -L/usr/lib/x86_64-linux-gnu, so -lm picks up glibc's x86 linker script and lld fails. The core doesn't use it.
mgba_KIND = cmake
mgba_BUILD_PATH = .
mgba_CMAKE_TARGET = mgba_libretro
# Link (netplay stage 2c): patches/mgba/001-sio-lockstep.patch is nx-redux main's (2f25643b), the real link cable with a
# CMakeLists.txt hunk that compiles cores/sio_lockstep in (NX_SIOLS_DIR). The core is then GPL-2.0-or-later (NOTICE.txt).
mgba_FLAGS = -DBUILD_LIBRETRO=ON -DUSE_SQLITE3=OFF -DNX_SIOLS_DIR=$(CURDIR)/sio_lockstep
mgba_LICENSE_EXTRA = sio_lockstep/NOTICE.txt
mgba_ARTIFACT = mgba_libretro.so

# patches/mupen64plus_next/0012 (nx-redux's): retro_cheat_set sizes its code arrays to the code; database codes with
# more than 256 parts overflowed them.
mupen64plus_next_REPO = https://github.com/libretro/mupen64plus-libretro-nx
mupen64plus_next_HASH = 6752836de8b224febfd5708444755b77712ac939
mupen64plus_next_LICENSE = LICENSE GLideN64/LICENSE mupen64plus-core/LICENSES mupen64plus-rsp-hle/LICENSES mupen64plus-rsp-cxd4/COPYING \
  mupen64plus-rsp-paraLLEl/LICENSE mupen64plus-video-paraLLEl/parallel-rdp/LICENSE
mupen64plus_next_KIND = ndkbuild
# Output must carry the gles3 flavour; the generic default would be
# mupen64plus_next_libretro_android.so, so set _CORE explicitly.
mupen64plus_next_CORE = mupen64plus_next_gles3_libretro_android.so
# The tree has one gitlink, mupen64plus-rsp-paraLLEl/lightning/gnulib, whose
# .gitmodules carries no URL, so the template's default recursive submodule init
# aborts ("No url found ..."). paraLLEl and lightning themselves are vendored
# directories, not submodules, and gnulib (GNU lightning's autotools portability
# shim) is unused by the libretro build, so we init submodules non-recursively
# while excluding it. With no other submodules present this is a clean no-op.
mupen64plus_next_SUBMODULES = ':(exclude)mupen64plus-rsp-paraLLEl/lightning/gnulib'
# recipe jni path is "libretro/jni", so the directory containing jni/ is libretro/.
mupen64plus_next_BUILD_PATH = libretro
# libretro/jni/Android.mk defines LOCAL_MODULE := retro -> libs/arm64-v8a/libretro.so
mupen64plus_next_ARTIFACT = libs/$(ABI)/libretro.so
# GLES3=1 is the exact flag the libretro-super cores-android recipe passes for the
# mupen64plus_next_gles3 target; Android.mk reads it: ifeq ($(GLES3),1) -> -lGLESv3.
mupen64plus_next_FLAGS = GLES3=1
# Its option table is named after the descriptor id, not the _gles3 artifact.
mupen64plus_next_OPTIONS_ID := mupen64plus_next

ppsspp_REPO = https://github.com/hrydgard/ppsspp
ppsspp_HASH = 2a1700a7ee495310c52c85f9436652cc8f26e45e
ppsspp_LICENSE = LICENSE.TXT ffmpeg/LICENSE.md
# ppsspp is not in the libretro-super cores-android recipe; it builds its libretro
# core straight from its own top-level CMakeLists.txt (add_library(ppsspp_libretro
# SHARED ...) gated on -DLIBRETRO=ON), so KIND=cmake with BUILD_PATH=. and target
# ppsspp_libretro. For the android/libretro build CMake sets OUTPUT_NAME to
# ppsspp_libretro_android and drops the shared lib in the build root (like mgba),
# so ARTIFACT has no lib/ prefix and already carries the _android suffix.
# patches/ppsspp.patch keeps a thread's own sigaltstack in the exception handler: upstream
# restores the boot loader thread's stack on the unload thread, which kills the app later (see the patch).
# patches/ppsspp/*.patch (nx-redux's numbered patches) applies after it: 0008 makes FixPathCase skip the "PSP/" that
# STRIP_PSP removes, so saves work on case-sensitive storage (Saves/PSP is the memory stick). 0011 keeps every cheat
# retro_cheat_set is given: it took the cheat file's header for a slot, so each cheat replaced the one before.
ppsspp_KIND = cmake
ppsspp_BUILD_PATH = .
ppsspp_CMAKE_TARGET = ppsspp_libretro
ppsspp_ARTIFACT = ppsspp_libretro_android.so
# Flags per the PPSSPP libretro Android build: LIBRETRO=ON selects the core target,
# ANDROID/USING_GLES2 pick the mobile GL path, and the USE_SYSTEM_*/USE_DISCORD=OFF
# switches force the vendored ffmpeg/libzip submodules and drop the Discord SDK
# (not available for android/arm64). The many submodules (ffmpeg, glslang,
# SPIRV-Cross, rcheevos, ...) are pulled by the template's default recursive
# --depth 1 init; they are all required, so no _SUBMODULES override is set.
ppsspp_FLAGS = -DLIBRETRO=ON -DANDROID=ON -DUSING_GLES2=ON -DUSE_SYSTEM_FFMPEG=OFF -DUSE_DISCORD=OFF -DUSE_SYSTEM_LIBZIP=OFF

# melonDS DS (JesseTG), replacing libretro/melonDS on 2026-09-27. CMake kind like ppsspp. The libretro target is a
# MODULE library, so the 16 KB page flag goes in CMAKE_MODULE_LINKER_FLAGS (the template only sets the SHARED one).
# OpenGL is on via GLES3: upstream refuses GLES, so patches/melondsds.patch lifts that and ports its compositor, and
# patches/melonds.patch (below, on the melonDS dependency) carries melonDS-android-lib's GLES port of the renderer.
# 3D renders on the GPU when melonds_render_mode = opengl; the software renderer (with the JIT) stays the default.
# Its configure step FetchContent-clones eleven dependencies by short hash or tag. They are pinned below by full SHA,
# cloned once into src/melondsds-deps/<name>-src (FetchContent's layout under FETCHCONTENT_BASE_DIR), and configure
# runs with FETCHCONTENT_FULLY_DISCONNECTED=ON, so a build never fetches by itself.
melondsds_REPO = https://github.com/JesseTG/melonds-ds
melondsds_HASH = bc4e4b67d2d470d7c682810a1e892cafd6f9082b
melondsds_KIND = cmake
melondsds_BUILD_PATH = .
melondsds_CMAKE_TARGET = melondsds_libretro
melondsds_ARTIFACT = src/libretro/melondsds_libretro_android.so
MELONDSDS_DEPS_DIR = $(abspath $(SRC_ROOT))/melondsds-deps
melondsds_FLAGS = -DENABLE_JIT=ON -DENABLE_OPENGL=ON -DENABLE_OGLRENDERER=ON -DOPENGL_PROFILE=OpenGLES3 -DBUILD_TESTING=OFF \
  "-DCMAKE_MODULE_LINKER_FLAGS=$(PAGE_LDFLAGS)" \
  -DFETCHCONTENT_BASE_DIR=$(MELONDSDS_DEPS_DIR) -DFETCHCONTENT_FULLY_DISCONNECTED=ON
# <FetchContent name, lowercased>@<repo>@<full SHA>, from cmake/FetchDependencies.cmake at the pin (resolved 2026-09-27;
# fmt 11.2.0 and zlib v1.3.1 are tags there).
MELONDSDS_DEPS = \
  melonds@https://github.com/melonDS-emu/melonDS@7117178c2dd56df32b6534ba6a54ad1f8547e693 \
  libretro-common@https://github.com/libretro/libretro-common@ad9124f1ecc5d0092fd285cbf9c4ffac317b1a65 \
  embed-binaries@https://github.com/andoalon/embed-binaries@078b62beba97e8192c99bfb16d5e17220cfc7598 \
  glm@https://github.com/g-truc/glm@e7970a8b26732f1b0df9690f7180546f8c30e48e \
  zlib@https://github.com/madler/zlib@51b7f2abdade71cd9bb0e7a373ef2610ec6f9daf \
  libslirp@https://github.com/JesseTG/libslirp-mirror@e61dbd459c8c06607b3a84694489427e8ec60f17 \
  pntr@https://github.com/robloach/pntr@922aed03c4c53803466100596211da7549945da7 \
  fmt@https://github.com/fmtlib/fmt@40626af88bd7df9a5fb80be7b25ac85b122d6c21 \
  yamc@https://github.com/yohhoy/yamc@4e015a7e8eb0d61c34e6928676c8c78881a72d73 \
  span-lite@https://github.com/martinmoene/span-lite@00afc281a8c3c7657bb9c5b000d6e7082c1bbc7f \
  date@https://github.com/HowardHinnant/date@1ead6715dec030d340a316c927c877a3c4e5a00c
MELONDSDS_DEPS_STAMP := $(shell printf %s '$(MELONDSDS_DEPS)' | shasum | cut -c1-12)

# ---- cores expansion (2026-09-25): nx-redux pins (workspace/tg5040/cores/Makefile), so states cross with the Brick.
# Most build unpatched from jni/Android.mk at the repo root (LOCAL_MODULE := retro -> libs/arm64-v8a/libretro.so); the
# exceptions (a patch, build flags or a different module/artifact name) are commented on their own entries.
race_REPO = https://github.com/libretro/race
race_HASH = 171950ea64c96ead503340ed60fa7ba18f56325c
race_LICENSE = license.txt
race_KIND = ndkbuild
race_BUILD_PATH = .
race_ARTIFACT = libs/$(ABI)/libretro.so

# patches/mednafen_pce_fast/0001: retro_cheat_set heap-copies the code; 256+ chars overflowed its stack buffer.
mednafen_pce_fast_REPO = https://github.com/libretro/beetle-pce-fast-libretro
mednafen_pce_fast_HASH = 52675734da114a19b3705f03906b1455f3d76644
mednafen_pce_fast_LICENSE = COPYING mednafen/tremor/COPYING
mednafen_pce_fast_KIND = ndkbuild
mednafen_pce_fast_BUILD_PATH = .
mednafen_pce_fast_ARTIFACT = libs/$(ABI)/libretro.so

mednafen_vb_REPO = https://github.com/libretro/beetle-vb-libretro
mednafen_vb_HASH = 65debc7c4c7b85e2fd988d2be53496c2cf0b5f44
mednafen_vb_LICENSE = COPYING
mednafen_vb_KIND = ndkbuild
mednafen_vb_BUILD_PATH = .
mednafen_vb_ARTIFACT = libs/$(ABI)/libretro.so

handy_REPO = https://github.com/libretro/libretro-handy
handy_HASH = fca239207e9c111da3e85d2faf0b1b9d7524e498
handy_LICENSE = lynx/license.txt
handy_KIND = ndkbuild
handy_BUILD_PATH = .
handy_ARTIFACT = libs/$(ABI)/libretro.so

# nx-redux names this make target a2600 (its clone dir); ours is the core id so the output is stella2014_libretro_android.so.
stella2014_REPO = https://github.com/libretro/stella2014-libretro
stella2014_HASH = 3cc89f0d316d6c924a5e3f4011d17421df58e615
stella2014_LICENSE = stella/license.txt
stella2014_KIND = ndkbuild
stella2014_BUILD_PATH = .
stella2014_ARTIFACT = libs/$(ABI)/libretro.so

# nx-redux: a7800.
prosystem_REPO = https://github.com/libretro/prosystem-libretro
prosystem_HASH = acae250da8d98b8b9707cd499e2a0bf6d8500652
prosystem_LICENSE = License.txt bupboop/License.txt
prosystem_KIND = ndkbuild
prosystem_BUILD_PATH = .
prosystem_ARTIFACT = libs/$(ABI)/libretro.so

a5200_REPO = https://github.com/libretro/a5200
a5200_HASH = 526404072821bb2021fab16f8c5dbbca300512c8
a5200_LICENSE = License.txt
a5200_KIND = ndkbuild
a5200_BUILD_PATH = .
a5200_ARTIFACT = libs/$(ABI)/libretro.so

# No Brick pak: pinned at upstream HEAD on the day it was added.
mednafen_wswan_REPO = https://github.com/libretro/beetle-wswan-libretro
mednafen_wswan_HASH = 4b01295838ea89e3f1355bbe4cb5cf98aa6108cd
mednafen_wswan_LICENSE = COPYING
mednafen_wswan_KIND = ndkbuild
mednafen_wswan_BUILD_PATH = .
mednafen_wswan_ARTIFACT = libs/$(ABI)/libretro.so
# Its jni/Android.mk includes ../Makefile.common (cwd-relative), so ndk-build must run inside jni/.
mednafen_wswan_FLAGS = -C jni

# patches/mednafen_supafaust/0001: retro_run tries every cheat format on each '+' part and skips one none accepts;
# the Game Genie decoder threw on anything else, which aborted the app.
mednafen_supafaust_REPO = https://github.com/libretro/supafaust
mednafen_supafaust_HASH = e25f66765938d33f9ad5850e8d6cd597e55b7299
mednafen_supafaust_LICENSE = COPYING
mednafen_supafaust_KIND = ndkbuild
mednafen_supafaust_BUILD_PATH = .
mednafen_supafaust_ARTIFACT = libs/$(ABI)/libretro.so
# OwlResampler_neon.inc uses the C++17-removed `register` specifier; NDK 26's clang makes that an error by default.
mednafen_supafaust_FLAGS = APP_CPPFLAGS=-Wno-register

# patches/pokemini.patch: nx-redux's resume-audio fix (the only nx-redux source patch carried over).
pokemini_REPO = https://github.com/libretro/PokeMini
pokemini_HASH = 78656d4615691c393e65e48672ce5fff5b1f97d3
pokemini_LICENSE = LICENSE
pokemini_KIND = ndkbuild
pokemini_BUILD_PATH = .
pokemini_ARTIFACT = libs/$(ABI)/libretro.so

# fake08's libretro build lives in platform/libretro/jni with LOCAL_MODULE := fake08, so the artifact is
# libfake08.so; the default recursive submodule init pulls libs/z8lua.
fake08_REPO = https://github.com/jtothebell/fake-08
fake08_HASH = f6bab5a7ba521ce440e45d1aeef6122674be6ee9
fake08_LICENSE = LICENSE.MD libs/miniz/LICENSE libs/lodepng/LICENSE
fake08_KIND = ndkbuild
fake08_BUILD_PATH = platform/libretro
fake08_ARTIFACT = libs/$(ABI)/libfake08.so

prboom_REPO = https://github.com/libretro/libretro-prboom
prboom_HASH = 93c8e7a2074e4fd8410398e3d571c6d9afec1d84
prboom_LICENSE = COPYING
prboom_KIND = ndkbuild
prboom_BUILD_PATH = .
prboom_ARTIFACT = libs/$(ABI)/libretro.so

# Dynarec cores: both map RWX memory at runtime, so verify.sh's static check is not enough; see the 16 KB smoke
# test in the cores expansion plan (a 16 KB-page arm64 image: load, 60 s, save state, relaunch, load it, 10 s).
# patches/gpsp.patch: the recompiler cache (10.5 MB, within +/-128 MB of the core's code) is mapped once and kept
# across sessions, since the runner keeps gpsp loaded between games (CoreDescriptor.keepLoaded); a failed mapping makes
# retro_load_game log an error and return false instead of crashing, and retro_init resets the state a reload used to.
gpsp_REPO = https://github.com/libretro/gpsp
gpsp_HASH = 2db57b11a437c4432ab69823bdcd951181de6213
gpsp_LICENSE = COPYING
gpsp_KIND = ndkbuild
gpsp_BUILD_PATH = .
gpsp_ARTIFACT = libs/$(ABI)/libretro.so
# Link (netplay stage 2c): patches/gpsp/ holds nx-redux's 001 (RFU disconnect), 002 (RFU queue) and 004 (real link
# cable, gpsp_serial "lockstep"/"auto_cable"); its 003 (JIT cache) is left out, patches/gpsp.patch covers it. 004 compiles
# in cores/sio_lockstep through NX_SIOLS_DIR (its Makefile.common default is nx-redux's layout).
gpsp_FLAGS = NX_SIOLS_DIR=$(CURDIR)/sio_lockstep
gpsp_LICENSE_EXTRA = sio_lockstep/NOTICE.txt

pcsx_rearmed_REPO = https://github.com/libretro/pcsx_rearmed
pcsx_rearmed_HASH = 6eb679d064a1efe92af79fe9970c0591c8da02d4
pcsx_rearmed_LICENSE = COPYING
pcsx_rearmed_KIND = ndkbuild
pcsx_rearmed_BUILD_PATH = .
pcsx_rearmed_ARTIFACT = libs/$(ABI)/libretro.so
# Only libpicofe is needed (nx-redux does the same); the recursive default would pull unused nested submodules.
pcsx_rearmed_SUBMODULES = frontend/libpicofe
# jni/Android.mk defaults USE_LIBRETRO_VFS to 0, which makes the core fopen() paths itself and bypass the runner's
# VFS (content arrives as /rom/<name> virtual files, never a real path).
pcsx_rearmed_FLAGS = USE_LIBRETRO_VFS=1
# patches/pcsx_rearmed.patch fixes the pin's Android.mk path to compat_strl.c, which the USE_LIBRETRO_VFS=1 build needs.

# SwanStation (PSX folder), libretro's DuckStation fork: the Brick's PSX.pak pin (nx-redux workspace/tg5040/cores/Makefile),
# so serialize sizes match a handheld's for netplay. CMake kind like mgba: src/libretro/CMakeLists.txt adds the SHARED
# target swanstation_libretro (empty prefix, build root, OUTPUT_NAME swanstation_libretro_android on Android). No
# submodules. GLES hardware renderer through the libretro HW context. patches/swanstation.patch: a PBP whose TOC is all
# zero (Tekken 3 SLPS-01300) opens as one data track (PCSX-ReARMed's fallback), sized from its block table; save states
# are unchanged, so netplay with the handheld's unpatched pin still matches.
swanstation_REPO = https://github.com/libretro/swanstation
swanstation_HASH = dd4805619bbbfc6e0d1776372cb16377088b448e
swanstation_LICENSE = LICENSE
swanstation_KIND = cmake
swanstation_BUILD_PATH = .
swanstation_CMAKE_TARGET = swanstation_libretro
swanstation_ARTIFACT = swanstation_libretro_android.so

# 32X only (GPGX runs MD/SMS/GG/SG/SEGACD). jni/Android.mk at the repo root, LOCAL_MODULE := retro. The default
# recursive submodule init pulls libpicofe, cyclone, emu2413, libchdr and dr_libs, all compiled.
# patches/picodrive.patch turns the SH2 dynarec on for arm64 (upstream enables it for 32-bit arm only).
# patches/picodrive/0001 (nx-redux's), after it: retro_cheat_set copies the code to the heap and each part into its
# 12-byte field with a bound; long database codes and parts overflowed both.
picodrive_REPO = https://github.com/irixxxx/picodrive
picodrive_HASH = b0be121b7d58d6ee1ee2809974e62893c80a8264
picodrive_LICENSE = COPYING
picodrive_KIND = ndkbuild
picodrive_BUILD_PATH = .
picodrive_ARTIFACT = libs/$(ABI)/libretro.so

gearcoleco_REPO = https://github.com/drhelius/Gearcoleco
gearcoleco_HASH = ed5095b3ce158ff0cf82395a61c2476505634894
gearcoleco_LICENSE = LICENSE
gearcoleco_KIND = ndkbuild
gearcoleco_BUILD_PATH = platforms/libretro
gearcoleco_ARTIFACT = libs/$(ABI)/libretro.so

# FB Neo licence: non-commercial, and no donation asks for any project using its source (README Constraints).
# 7z support on, Neo Geo CD off (upstream Android.mk defaults). No rotation patch: the runner honours SET_ROTATION.
fbneo_REPO = https://github.com/libretro/FBNeo
fbneo_HASH = 6a5cc250c2db8d874a06ee86e302a2a78918b4c1
fbneo_KIND = ndkbuild
fbneo_BUILD_PATH = src/burner/libretro
fbneo_ARTIFACT = libs/$(ABI)/libretro.so

# flycast (Dreamcast). v2.7 is a lightweight tag on 5aa091fde632fb332c8d8c34e280d62dc951954c. CMake kind like
# mgba/ppsspp: the top-level CMakeLists.txt builds the SHARED target flycast_libretro (empty prefix, build root) when
# LIBRETRO=ON. Flags follow flycast's own libretro Android CI recipe (.gitlab-ci.yml .core-defs-android: LIBRETRO=ON,
# USE_OPENMP=OFF, Vulkan left at its default ON). USE_GLES=ON is a no-op next to ANDROID (the LIBRETRO+ANDROID branch
# already defines GLES GLES3 HAVE_OPENGLES3 HAVE_OIT and compiles glsym_es3); it states the intent.
# CMAKE_OSX_ARCHITECTURES=arm64: on a macOS host its CMakeLists caches "x86_64;arm64" before project(), and
# DetectArchitecture then adds the x86 recompiler (xbyak). No FetchContent: the dependencies are git submodules pinned
# by the tag's gitlinks. Only the six the Android libretro build uses are initialised, recursively for tinygettext's
# external/tinycmmc; SDL, breakpad, oboe, freetype, libjuice, gamesdk and the rest are standalone-app only. Licence
# notices: LICENSES/flycast.txt (ships in the APK as licenses/flycast.txt). patches/flycast.patch has five hunks:
# - CMakeLists.txt links libandroid into the libretro target (upstream links it for the standalone app only), so vmem's
#   weak ASharedMemory_create resolves; else it falls back to /dev/ashmem (EACCES at targetSdk 29+), runs without
#   fast-mem, and the dynarec's write-protect fault handler misses the heap-tagged RAM pointer and crashes.
# - shell/libretro/oslib.cpp getVmuPath names per-game VMUs after the content file (<content>.A1.bin) instead of the
#   disc's game ID, so the app's saves mirror can tie a VMU to its game; an existing game-ID file is read once, then
#   rewritten as <content>.A1.bin and deleted by flycast (maple_devs.cpp OnSetup). The shared VMUs (every port with
#   per_content_vmus Disabled, the ports but A1 with "VMU A1") move from the system folder (<system>/dc/) to the save
#   directory too, still vmu_save_<port>.bin, so Device Sync carries every memory card as a save; dc_nvmem/flash and
#   arcade flash stay in <system>/dc/. No migration: a card left in <system>/dc/ is not read (the app is
#   unreleased, and its defaults plug no shared card in).
# - core/rend/gles/gles.h GlVertexArray::term no longer deletes vertex array name 0 (the post-processor's, never created
#   on the blit path, on every render-size change). The spec ignores it, but the Android emulator's GLES stack then drops
#   the default vertex array's client state and the frontend's presentation quad draws nothing (black screen).
# - core/hw/naomi/naomi_roms.cpp + naomi_cart.cpp (from nx-redux 959dbfb7): awbios also accepts the modern MAME dump
#   bios.ic23_l (64 KB, CRC e5693ce3) as an alternative to bios0.ic23 (128 KB); loadBios takes the first blob it finds
#   by CRC or name, and mirrors a 64 KB awbios image into the upper half of the 128 KB window, as the hardware does.
# - core/rend/vulkan/vk_context_lr.cpp VkCreateDevice also enables the frontend's required device extensions (its
#   AHardwareBuffer bridge needs them; see CoreRegistry's flycast entry).
flycast_REPO = https://github.com/flyinghead/flycast
flycast_HASH = 5aa091fde632fb332c8d8c34e280d62dc951954c
flycast_KIND = cmake
flycast_BUILD_PATH = .
flycast_CMAKE_TARGET = flycast_libretro
flycast_ARTIFACT = flycast_libretro.so
flycast_SUBMODULES = --recursive core/deps/libchdr core/deps/tinygettext core/deps/asio \
  core/deps/glslang core/deps/Vulkan-Headers core/deps/VulkanMemoryAllocator
flycast_FLAGS = -DLIBRETRO=ON -DUSE_GLES=ON -DUSE_OPENMP=OFF -DCMAKE_OSX_ARCHITECTURES=arm64

# Azahar (Nintendo 3DS), 2126.1.2 (a lightweight tag on 9e6f523a57fac9564ac0bf8286db3c3702d301ec). The libretro core is
# src/citra_libretro, the SHARED target citra_libretro (OUTPUT_NAME azahar_libretro, empty prefix) gated on
# ENABLE_LIBRETRO=ON, which forces the Qt/SDL2/web/room/cubeb/OpenAL/libusb frontends off by itself. Its
# CMAKE_LIBRARY_OUTPUT_DIRECTORY is bin/<config>, so the artifact is bin/Release/. Flags follow upstream's Android
# libretro CI (.github/workflows/libretro.yml), Vulkan compiled in. On Android its "Auto" renderer returns Vulkan
# without asking GET_PREFERRED_HW_RENDER, so the app always sets citra_graphics_api (OpenGL, or Vulkan per Graphics API);
# the GL path requests a GLES 3.2 context. Android builds define HAVE_LIBRETRO_VFS and USING_GLES. patches/azahar.patch:
# RenderToWindow waits for the render worker to submit a frame before presenting it, so the frontend's Vulkan bridge
# never copies an unfinished frame; the Large screen layout puts its small screen top-right (upstream: bottom-right).
# The top-level CMakeLists needs CMake >= 3.25, above the SDK's 3.22.1 every other core uses: sdkmanager "cmake;3.30.3"
# (upstream CI's version) and azahar_CMAKE / azahar_NINJA below. No FetchContent: ~35 git submodules, all initialised
# recursively by the template's default. Built-in AES keyblob and open-source shared font: no BIOS. Decrypted games only.
azahar_REPO = https://github.com/azahar-emu/azahar
azahar_HASH = 9e6f523a57fac9564ac0bf8286db3c3702d301ec
azahar_KIND = cmake
azahar_BUILD_PATH = .
azahar_CMAKE_TARGET = citra_libretro
azahar_ARTIFACT = bin/Release/azahar_libretro.so
azahar_CMAKE = $(SDK)/cmake/3.30.3/bin/cmake
azahar_NINJA = $(SDK)/cmake/3.30.3/bin/ninja
# android-21 as upstream CI: at API >= 23 bionic declares the GNU strerror_r under _GNU_SOURCE, and common/error.cpp
# takes the XSI (int) form whenever HAVE_LIBRETRO is set, so android-26 fails to compile. The app's minSdk (26) is above.
azahar_PLATFORM = android-21
azahar_FLAGS = -DENABLE_LIBRETRO=ON -DENABLE_VULKAN=ON -DENABLE_OPENGL=ON -DENABLE_TESTS=OFF -DANDROID_STL=c++_static

# Dolphin (Wii, GameCube): libretro/dolphin at the 2026-10-23 head (its d3ca3028 fixed a GPU-loop stop the CPU thread sent
# before retro_run entered RunGpuLoop: lost, it hung retro_run with the CPU paused, a Wii game frozen ~10 s in). CMake like azahar, needing >= 3.25 (3.30.3 below). The
# libretro target is Source/Core/DolphinLibretro's SHARED dolphin_libretro, suffix "_android.so", built in the build root.
# Flags follow upstream's Android CI (.gitlab-ci.yml .core-defs: -DLIBRETRO=ON -DENABLE_LTO=ON, API 24). The core asks
# GET_PREFERRED_HW_RENDER: the runner answers GLES3, or Vulkan when Console Settings → Graphics API picks it. 34 submodules, recursive (template default). Licences
# are REUSE-style under LICENSES/. patches/dolphin.patch: SET_SYSTEM_AV_INFO on every refresh-rate change (the runner
# paces by it; Dolphin's boot-time 119.88 Hz was never taken back), a Synchronize GPU Thread option (unsynced dual core
# desynced NSMB Wii's FIFO), shared GL contexts through the runner's private call (nxsharedgl.h; upstream's own EGL ones after it), and a failed load
# when no hardware context was granted, sensors re-asked when a port's device is resent (live motion toggle), with the
# IMU unbound on the stick-tilt path, a null-source guard in the emulated Wiimote's HID output (states restored with
# more remotes linked than connected), raw-disc reader exports for RetroAchievements hashing (NxDisc.cpp;
# nx_disc_open_fd reads the frontend's SAF descriptor through DirectIOFile::FromFd, past the VFS, and a CreateBlobReader
# overload taking an open file); retro_cheat_set adds codes not in the game INIs. Sys/ is staged to system/dolphin-emu/Sys, where the core looks, minus Themes/ (Qt GUI icons, useless to
# libretro). dolphin_LICENSE ships the notice of every statically linked external (and the GC font licences) in licenses/dolphin.txt.
# libiconv/libcharset (LGPL-2.1+) vendor no licence text; libusb's COPYING is the same FSF LGPL-2.1 text, from the pinned
# checkout. KissFFT's BSD notice lives only in its source header, so notices/dolphin-kissfft.txt carries it.
dolphin_REPO = https://github.com/libretro/dolphin
dolphin_HASH = 3aac3f052f733f8df2f1616843a4d2e14002ed11
dolphin_LICENSE = COPYING LICENSES/GPL-2.0-or-later.txt LICENSES/MIT.txt LICENSES/BSD-3-Clause.txt LICENSES/CC0-1.0.txt Data/Sys/GC/font-licenses.txt \
  Externals/FatFs/LICENSE.txt Externals/LZO/COPYING Externals/SFML/SFML/license.md Externals/bzip2/bzip2/LICENSE \
  Externals/cpp-optparse/cpp-optparse/LICENSE Externals/curl/curl/COPYING Externals/ed25519/license.txt \
  Externals/enet/enet/LICENSE Externals/expr/LICENSE Externals/fmt/fmt/LICENSE Externals/glslang/glslang/LICENSE.txt \
  Externals/imgui/imgui/LICENSE.txt Externals/implot/implot/LICENSE Externals/libadrenotools/LICENSE \
  Externals/libadrenotools/lib/linkernsbypass/LICENSE Externals/liblzma/COPYING Externals/libspng/libspng/LICENSE \
  Externals/lz4/lz4/lib/LICENSE Externals/mGBA/mgba/LICENSE Externals/mbedtls/LICENSE \
  Externals/minizip-ng/minizip-ng/LICENSE Externals/picojson/LICENSE Externals/pugixml/pugixml/LICENSE.md \
  Externals/tinygltf/tinygltf/LICENSE Externals/watcher/watcher/license Externals/xxhash/xxHash/LICENSE \
  Externals/zlib-ng/zlib-ng/LICENSE.md Externals/zstd/zstd/LICENSE Externals/VulkanMemoryAllocator/LICENSE.txt \
  Externals/libusb/libusb/COPYING Externals/licenses.md
dolphin_LICENSE_EXTRA = notices/dolphin-kissfft.txt
dolphin_KIND = cmake
dolphin_BUILD_PATH = .
dolphin_CMAKE_TARGET = dolphin_libretro
dolphin_ARTIFACT = dolphin_libretro_android.so
dolphin_CMAKE = $(SDK)/cmake/3.30.3/bin/cmake
dolphin_NINJA = $(SDK)/cmake/3.30.3/bin/ninja
dolphin_PLATFORM = android-24
dolphin_FLAGS = -DLIBRETRO=ON -DENABLE_LTO=ON -DANDROID_STL=c++_static

# ARMSX2 (PS2): ARMSX2/ARMSX2 at its 2.9 release tag, a PCSX2 fork with arm64 recompilers. Its libretro port is
# pcsx2-libretro/ (SHARED pcsx2-libretro, OUTPUT_NAME armsx2_libretro, empty prefix), added only with ENABLE_LIBRETRO=ON;
# the build tree's bin/ holds it. Flags follow upstream's Android libretro CI (.gitlab-ci.yml .core-defs-android, API 28:
# bionic's posix_spawn). NDK r29 like upstream's own Android app (armsx2.ndkVersion); the
# other cores stay on $(NDK_VERSION). Its static dependencies (png, jpeg-turbo, zstd, lz4, webp, SDL3, freetype, plutovg,
# plutosvg, curl without TLS, libpcap, shaderc) come from upstream's own script, run once into $(ARMSX2_DEPS) with the
# same NDK; the stamp names the pin, so a bump rebuilds them. CMake >= 3.25 policy range: 3.30.3, as azahar/dolphin.
# Renders on GLES 3.2 or Vulkan (armsx2_renderer, set by the app per Graphics API). bin/resources (shaders, GameIndex,
# fonts) is staged to system/pcsx2/resources, where the core looks; the BIOS goes in system/pcsx2/bios (Bios/PS2/).
# patches/armsx2/0001: the host page size is read at runtime on Android (4 KB or 16 KB kernels); upstream fixes it at
# build time and refuses a mismatched kernel. vtlb fastmem then maps at host-page granularity, as on 16 KB Apple hosts.
# ARMSX2_EMBED_PATCHES=OFF: upstream fetches a patches.zip at configure time and compiles it in; that collection has no
# licence, so it stays out of the .so. The app's Cheats & Patches tool downloads PCSX2's patches.zip at runtime and
# extracts it into Cheats/PS2/Patches/, which patches/armsx2/0005 points the core at (cheats from Cheats/PS2/).
armsx2_REPO = https://github.com/ARMSX2/ARMSX2
armsx2_HASH = 023224576867a3846faedc7640911772c7783529
armsx2_LICENSE = COPYING.GPLv3 3rdparty/cpuinfo/LICENSE 3rdparty/cubeb/LICENSE 3rdparty/discord-rpc/LICENSE \
  3rdparty/fast_float/LICENSE-MIT 3rdparty/fmt/LICENSE 3rdparty/imgui/LICENSE.txt 3rdparty/libchdr/LICENSE.txt \
  3rdparty/libzip/LICENSE 3rdparty/lzma/LICENSE.txt 3rdparty/plutosvg/LICENSE 3rdparty/plutovg/LICENSE \
  3rdparty/promptfont/LICENSE.txt 3rdparty/rainterface/LICENSE 3rdparty/rapidjson/license.txt 3rdparty/rcheevos/LICENSE \
  3rdparty/simpleini/LICENCE.txt 3rdparty/soundtouch/COPYING.TXT 3rdparty/vixl/LICENCE 3rdparty/vulkan/LICENSE.md \
  3rdparty/demangler/DemanglerLicenseGPL.txt 3rdparty/demangler/DemanglerLicenseLGPL.txt 3rdparty/freesurround/GPL.txt
# The static dependencies' own licences, from their checkouts under $(ARMSX2_DEPS) (built before the .so, so present).
# ccc, demangler, freesurround, glad, rapidyaml and the libretro headers keep their notices in source headers (or ship
# none), so notices/armsx2-*.txt carries them like dolphin's KissFFT; the demangler and FreeSurround licence texts are
# in the tree.
ARMSX2_DEPS_SRC = $(BUILD_ROOT)/armsx2-deps/deps-build
armsx2_LICENSE_EXTRA = $(addprefix $(ARMSX2_DEPS_SRC)/,libpng/LICENSE libjpeg-turbo/LICENSE.md libjpeg-turbo/README.ijg \
  zstd/LICENSE lz4/LICENSE libwebp/COPYING sdl3/LICENSE.txt freetype/LICENSE.TXT plutovg/LICENSE plutosvg/LICENSE \
  curl/COPYING libpcap/LICENSE shaderc/LICENSE shaderc/third_party/glslang/LICENSE.txt \
  shaderc/third_party/spirv-tools/LICENSE shaderc/third_party/spirv-headers/LICENSE) \
  $(addprefix notices/armsx2-,ccc.txt demangler.txt freesurround.txt glad.txt rapidyaml.txt libretro.txt)
armsx2_KIND = cmake
armsx2_BUILD_PATH = .
armsx2_CMAKE_TARGET = pcsx2-libretro
armsx2_ARTIFACT = bin/armsx2_libretro.so
armsx2_CMAKE = $(SDK)/cmake/3.30.3/bin/cmake
armsx2_NINJA = $(SDK)/cmake/3.30.3/bin/ninja
armsx2_NDK_VERSION = 29.0.14206865
armsx2_NDK = $(SDK)/ndk/$(armsx2_NDK_VERSION)
armsx2_PLATFORM = android-28
ARMSX2_DEPS = $(abspath $(BUILD_ROOT))/armsx2-deps
armsx2_FLAGS = -DCMAKE_PREFIX_PATH=$(ARMSX2_DEPS)/prefix -DCMAKE_FIND_ROOT_PATH=$(ARMSX2_DEPS)/prefix -DANDROID_STL=c++_static \
  -DENABLE_LIBRETRO=ON -DENABLE_QT_UI=OFF -DENABLE_SDL_FRONTEND=OFF -DENABLE_GSRUNNER=OFF -DENABLE_TESTS=OFF \
  -DENABLE_RECOMPILER_TEST_HOOKS=OFF -DENABLE_SETCAP=OFF -DUSE_BACKTRACE=OFF -DX11_API=OFF -DWAYLAND_API=OFF \
  -DDISABLE_ADVANCE_SIMD=TRUE -DCMAKE_DISABLE_PRECOMPILE_HEADERS=ON -DARMSX2_EMBED_PATCHES=OFF

# Deviation from the generic pattern: ppsspp reads its data files (flash0, fonts,
# shaders, ...) from <system>/PPSSPP/ at runtime, so stage the repo's assets/ dir
# next to the core. This extra prerequisite on the ppsspp target runs after the
# .so is built (the .staged marker depends on it).
$(OUTPUT_ROOT)/system/PPSSPP/.staged: $(OUTPUT_ROOT)/ppsspp_libretro_android.so
	rm -rf $(OUTPUT_ROOT)/system/PPSSPP
	mkdir -p $(OUTPUT_ROOT)/system/PPSSPP
	cp -R $(SRC_ROOT)/ppsspp/assets/. $(OUTPUT_ROOT)/system/PPSSPP/
	touch $(OUTPUT_ROOT)/system/PPSSPP/.staged
ppsspp: $(OUTPUT_ROOT)/system/PPSSPP/.staged

# Deviation, like ppsspp's: prboom needs prboom.wad (its own game data, 143 KB, shipped in the repo) in the system
# dir, so stage it next to the core; install copies output/system/ to the app's assets/system/.
$(OUTPUT_ROOT)/system/prboom.wad: $(OUTPUT_ROOT)/prboom_libretro_android.so
	mkdir -p $(OUTPUT_ROOT)/system
	cp $(SRC_ROOT)/prboom/prboom.wad $@
prboom: $(OUTPUT_ROOT)/system/prboom.wad

# Deviation, like prboom's: fbneo reads hiscore.dat from <system>/fbneo/, and the app reads the set table
# (title, parent, BIOS) generated from the pinned arcade DAT.
FBNEO_DAT = $(SRC_ROOT)/fbneo/dats/FinalBurn Neo (ClrMame Pro XML, Arcade only).dat
$(OUTPUT_ROOT)/system/fbneo/hiscore.dat: $(OUTPUT_ROOT)/fbneo_libretro_android.so
	mkdir -p $(dir $@)
	cp $(SRC_ROOT)/fbneo/metadata/hiscore.dat $@
$(OUTPUT_ROOT)/system/fbneo/sets.tsv: $(OUTPUT_ROOT)/fbneo_libretro_android.so scripts/fbneo-sets.py
	mkdir -p $(dir $@)
	python3 scripts/fbneo-sets.py "$(FBNEO_DAT)" $@
fbneo: $(OUTPUT_ROOT)/system/fbneo/hiscore.dat $(OUTPUT_ROOT)/system/fbneo/sets.tsv

# Deviation, like fbneo's: the app reads flycast's NAOMI/Atomiswave set table (title, parent, BIOS archive) generated from
# the pinned ROM list, core/hw/naomi/naomi_roms.cpp. The .so prerequisite orders it after the checkout.
$(OUTPUT_ROOT)/system/flycast/sets.tsv: $(OUTPUT_ROOT)/flycast_libretro_android.so scripts/flycast-sets.py
	mkdir -p $(dir $@)
	python3 scripts/flycast-sets.py $(SRC_ROOT)/flycast/core/hw/naomi/naomi_roms.cpp $@
flycast: $(OUTPUT_ROOT)/system/flycast/sets.tsv

# Deviation, like ppsspp's: Dolphin reads its data files (game INIs, shaders, fonts) from <system>/dolphin-emu/Sys/.
$(OUTPUT_ROOT)/system/dolphin-emu/Sys/.staged: $(OUTPUT_ROOT)/dolphin_libretro_android.so
	rm -rf $(OUTPUT_ROOT)/system/dolphin-emu/Sys
	mkdir -p $(OUTPUT_ROOT)/system/dolphin-emu/Sys
	cp -R $(SRC_ROOT)/dolphin/Data/Sys/. $(OUTPUT_ROOT)/system/dolphin-emu/Sys/
	rm -rf $(OUTPUT_ROOT)/system/dolphin-emu/Sys/Themes
	touch $(OUTPUT_ROOT)/system/dolphin-emu/Sys/.staged
dolphin: $(OUTPUT_ROOT)/system/dolphin-emu/Sys/.staged

# Deviation: ARMSX2's dependencies, built by upstream's .github/workflows/scripts/android/build-dependencies.sh from the
# pinned checkout (it clones each library at a fixed tag into deps-build/ under its working directory) with the core's
# NDK and the 3.30.3 cmake/ninja on PATH. Done once per pin; clean-armsx2 keeps them, distclean-armsx2 drops them.
$(ARMSX2_DEPS)/.built-$(armsx2_HASH): $(SRC_ROOT)/armsx2/.patched
	rm -rf $(ARMSX2_DEPS)/prefix $(ARMSX2_DEPS)/.built-*
	mkdir -p $(ARMSX2_DEPS)/prefix
	cd $(ARMSX2_DEPS) && PATH="$(dir $(armsx2_CMAKE)):$$PATH" ANDROID_NDK=$(armsx2_NDK) ANDROID_ABI=$(ABI) ANDROID_API=28 \
	  bash $(CURDIR)/$(SRC_ROOT)/armsx2/.github/workflows/scripts/android/build-dependencies.sh $(ARMSX2_DEPS)/prefix
	touch $@
$(OUTPUT_ROOT)/armsx2_libretro_android.so: $(ARMSX2_DEPS)/.built-$(armsx2_HASH)
distclean-armsx2: distclean-armsx2-deps
distclean-armsx2-deps:
	rm -rf $(ARMSX2_DEPS)
.PHONY: distclean-armsx2-deps

$(OUTPUT_ROOT)/system/pcsx2/resources/.staged: $(OUTPUT_ROOT)/armsx2_libretro_android.so
	rm -rf $(OUTPUT_ROOT)/system/pcsx2/resources
	mkdir -p $(OUTPUT_ROOT)/system/pcsx2/resources
	cp -R $(SRC_ROOT)/armsx2/bin/resources/. $(OUTPUT_ROOT)/system/pcsx2/resources/
	touch $(OUTPUT_ROOT)/system/pcsx2/resources/.staged
armsx2: $(OUTPUT_ROOT)/system/pcsx2/resources/.staged

# Deviation: melonDS DS's dependencies (see its entry) are cloned at their pinned SHAs before its configure step. The
# stamp names the whole list, so editing a pin re-runs the checkout.
$(MELONDSDS_DEPS_DIR)/.checkout-$(MELONDSDS_DEPS_STAMP):
	mkdir -p $(MELONDSDS_DEPS_DIR)
	set -e; for dep in $(MELONDSDS_DEPS); do \
	  name=$${dep%%@*}; rest=$${dep#*@}; url=$${rest%@*}; sha=$${rest##*@}; dir=$(MELONDSDS_DEPS_DIR)/$$name-src; \
	  test -d $$dir/.git || git init -q $$dir; \
	  git -C $$dir fetch -q --depth 1 $$url $$sha; \
	  git -C $$dir checkout -q -f $$sha; \
	done
	rm -f $(MELONDSDS_DEPS_DIR)/.checkout-*
	touch $@
$(OUTPUT_ROOT)/melondsds_libretro_android.so: $(MELONDSDS_DEPS_DIR)/.checkout-$(MELONDSDS_DEPS_STAMP)
# patches/melonds.patch (melonDS-android-lib's GLES port) goes on the melonDS dependency, not on melonDS DS. The stamp
# depends on the patch file, and the tree is reset before applying, so an edited patch re-applies cleanly.
MELONDS_DEP_PATCHED = $(MELONDSDS_DEPS_DIR)/melonds-src/.nx-patched
$(MELONDS_DEP_PATCHED): $(MELONDSDS_DEPS_DIR)/.checkout-$(MELONDSDS_DEPS_STAMP) patches/melonds.patch
	cd $(MELONDSDS_DEPS_DIR)/melonds-src && git checkout -q -- . && $(PATCH) -p1 < $(CURDIR)/patches/melonds.patch
	touch $@
$(OUTPUT_ROOT)/melondsds_libretro_android.so: $(MELONDS_DEP_PATCHED)
# The dependencies' build trees live beside their sources (FETCHCONTENT_BASE_DIR); clean drops them, distclean all.
clean-melondsds: clean-melondsds-deps
clean-melondsds-deps:
	rm -rf $(MELONDSDS_DEPS_DIR)/*-build $(MELONDSDS_DEPS_DIR)/*-subbuild
distclean-melondsds: distclean-melondsds-deps
distclean-melondsds-deps:
	rm -rf $(MELONDSDS_DEPS_DIR)
.PHONY: clean-melondsds-deps distclean-melondsds-deps

# ---- generic rules -----------------------------------------------------------
define TEMPLATE=
$1_REPO ?= https://github.com/libretro/$(1)
$1_CORE ?= $(1)_libretro_android.so
$1_KIND ?= ndkbuild
$1_BUILD_PATH ?= .
$1_ARTIFACT ?= libs/$(ABI)/libretro.so
$1_CMAKE_TARGET ?= $(1)_libretro
$1_OPTIONS_ID ?= $(1)
$1_CMAKE ?= $(CMAKE)
$1_NINJA ?= $(NINJA)
$1_PLATFORM ?= $(PLATFORM)
$1_NDK ?= $(NDK)

ifneq (40,$$(strip $$(shell printf %s '$$($1_HASH)' | grep -cE '^[0-9a-f]{40}$$$$' >/dev/null && echo 40)))
$(1):
	$$(error $(1)_HASH must be a 40-character lowercase commit SHA)
else

# Checkout state is a hash stamp: bumping $(1)_HASH changes the stamp name, so its
# recipe re-runs, re-fetches/checks out the new SHA, drops any old stamp and .patched
# (so the patch re-applies on the new tree). Clone if the dir is missing, else fetch.
$(SRC_ROOT)/$(1)/.checkout-$$($1_HASH):
	@test -d $(SRC_ROOT)/$(1) || (mkdir -p $(SRC_ROOT) && cd $(SRC_ROOT) && git clone --depth 1 $$($1_REPO) $(1))
# -f discards a previously applied patch; .patched re-applies it below
	cd $(SRC_ROOT)/$(1) && git fetch --depth=1 origin $$($1_HASH) && git checkout --quiet -f $$($1_HASH)
	$(if $($1_SUBMODULES),cd $(SRC_ROOT)/$(1) && git submodule update --init --depth 1 $($1_SUBMODULES),cd $(SRC_ROOT)/$(1) && git submodule update --init --recursive --depth 1)
	rm -f $(SRC_ROOT)/$(1)/.checkout-* $(SRC_ROOT)/$(1)/.patched
	touch $(SRC_ROOT)/$(1)/.checkout-$$($1_HASH)

# patches/<core>.patch first, then patches/<core>/*.patch in name order (nx-redux's numbered core patches, kept as they
# are so both projects carry the same files). All of them once per checkout, like the single patch.
$(SRC_ROOT)/$(1)/.patched: $(SRC_ROOT)/$(1)/.checkout-$$($1_HASH)
	@(test ! -f patches/$(1).patch) || (test -f $(SRC_ROOT)/$(1)/.patched) || (cd $(SRC_ROOT)/$(1) && $(PATCH) -p1 < $(CURDIR)/patches/$(1).patch)
	@test -f $(SRC_ROOT)/$(1)/.patched || for p in $$(sort $$(wildcard patches/$(1)/*.patch)); do (cd $(SRC_ROOT)/$(1) && $(PATCH) -p1 < $(CURDIR)/$$$$p) || exit 1; done
	@test -f $(SRC_ROOT)/$(1)/.patched || touch $(SRC_ROOT)/$(1)/.patched

ifeq (ndkbuild,$$($1_KIND))
# Absolute NDK_PROJECT_PATH="$$PWD"/APP_BUILD_SCRIPT: libretro jni/Android.mk files derive LOCAL_PATH relative to jni/ and fail when given a relative project path.
$(OUTPUT_ROOT)/$$($1_CORE): $(SRC_ROOT)/$(1)/.patched
	mkdir -p $(OUTPUT_ROOT)
	cd $(SRC_ROOT)/$(1)/$$($1_BUILD_PATH) && $(NDK_BUILD) $(PROCS) NDK_PROJECT_PATH="$$$$PWD" APP_BUILD_SCRIPT="$$$$PWD/jni/Android.mk" \
	  APP_ABI=$(ABI) APP_PLATFORM=$(PLATFORM) NDK_TOOLCHAIN_VERSION=clang APP_STL=c++_static \
	  APP_LDFLAGS="$(PAGE_LDFLAGS)" APP_ALLOW_MISSING_DEPS=true $$($1_FLAGS)
	$(STRIP) --strip-unneeded -o $(OUTPUT_ROOT)/$$($1_CORE) $(SRC_ROOT)/$(1)/$$($1_BUILD_PATH)/$$($1_ARTIFACT)
else
$(OUTPUT_ROOT)/$$($1_CORE): $(SRC_ROOT)/$(1)/.patched
	mkdir -p $(OUTPUT_ROOT) $(BUILD_ROOT)/$(1)
	$$($1_CMAKE) -S $(SRC_ROOT)/$(1)/$$($1_BUILD_PATH) -B $(BUILD_ROOT)/$(1) -G Ninja -DCMAKE_MAKE_PROGRAM=$$($1_NINJA) \
	  -DCMAKE_TOOLCHAIN_FILE=$$($1_NDK)/build/cmake/android.toolchain.cmake -DANDROID_ABI=$(ABI) -DANDROID_PLATFORM=$$($1_PLATFORM) \
	  -DCMAKE_BUILD_TYPE=Release "-DCMAKE_SHARED_LINKER_FLAGS=$(PAGE_LDFLAGS)" $$($1_FLAGS)
	$$($1_CMAKE) --build $(BUILD_ROOT)/$(1) --target $$($1_CMAKE_TARGET) $(PROCS)
	$(STRIP) --strip-unneeded -o $(OUTPUT_ROOT)/$$($1_CORE) $(BUILD_ROOT)/$(1)/$$($1_ARTIFACT)
endif

clone-$(1): $(SRC_ROOT)/$(1)/.checkout-$$($1_HASH)
status-$(1): $(SRC_ROOT)/$(1)/.checkout-$$($1_HASH)
	cd $(SRC_ROOT)/$(1) && git show --oneline -s
# clean-$(1): build outputs only (leaves the src/$(1) checkout intact).
# distclean-$(1): clean plus removal of the src/$(1) checkout.
clean-$(1):
	rm -rf $(BUILD_ROOT)/$(1) $(SRC_ROOT)/$(1)/$$($1_BUILD_PATH)/obj $(SRC_ROOT)/$(1)/$$($1_BUILD_PATH)/libs
	rm -f $(OUTPUT_ROOT)/$$($1_CORE) $(OUTPUT_ROOT)/licenses/$(1).txt
distclean-$(1): clean-$(1)
	rm -rf $(SRC_ROOT)/$(1)
$(1): $(OUTPUT_ROOT)/$$($1_CORE)
# The licence texts, verbatim, under a line naming the repo and commit. The .so prerequisite orders it after the checkout.
ifneq (,$$(strip $$($1_LICENSE)))
$(OUTPUT_ROOT)/licenses/$(1).txt: $(OUTPUT_ROOT)/$$($1_CORE)
	mkdir -p $(OUTPUT_ROOT)/licenses
	{ echo "$(1) ($$($1_REPO), commit $$($1_HASH))"; for f in $$($1_LICENSE); do printf '\n# %s\n\n' "$$$$f"; \
	  cat $(SRC_ROOT)/$(1)/$$$$f; done; for f in $$($1_LICENSE_EXTRA); do printf '\n# %s\n\n' "$$$$f"; cat $$$$f; done; } > $$@.tmp
	mv $$@.tmp $$@
$(1): $(OUTPUT_ROOT)/licenses/$(1).txt
endif
# options-$(1): probe the shipped $(JNILIBS_DIR) copy on ADB_SERIAL into $(OPTIONS_DIR)/<OPTIONS_ID>.json, with
# PROBE_ROM_$(1) as its game when set. Deliberately no core prerequisite: a probe never rebuilds or re-patches a core.
options-$(1): nxprobe
	SYSTEM_DIR=$$(ASSETS_DIR) NXPROBE=$$(OUTPUT_ROOT)/nxprobe PROBE_SKIP_INIT=$$(PROBE_SKIP_INIT_$(1)) sh probe/pull-options.sh "$$(ADB)" "$$(ADB_SERIAL)" $$(JNILIBS_DIR)/lib$$($1_CORE) $$($1_OPTIONS_ID) $$(OPTIONS_DIR)$$(if $$(strip $$(PROBE_ROM_$(1))), "$$(PROBE_ROM_$(1))")
.PHONY: $(1) clone-$(1) status-$(1) clean-$(1) distclean-$(1) options-$(1)
endif
endef

$(foreach CORE,$(CORES),$(eval $(call TEMPLATE,$(CORE))))

# <artifact name>=<coreId> per core, for verify.sh's option-table check (the one place mupen's _gles3 maps to its id).
OPTIONS_IDS = $(foreach CORE,$(CORES),$($(CORE)_CORE)=$($(CORE)_OPTIONS_ID))

# verify runs before install, so its option-table check sees the previous jniLibs; the check after install warns
# when a core bump left a table stale.
all: cores verify install
	@$(MAKE) --no-print-directory verify-options
cores: $(foreach CORE,$(CORES),$(CORE))
verify: cores
	@OPTIONS_DIR=$(OPTIONS_DIR) JNILIBS_DIR=$(JNILIBS_DIR) OPTIONS_IDS="$(OPTIONS_IDS)" \
	  sh verify.sh $(READELF) $(wildcard $(OUTPUT_ROOT)/*.so)
# Only the option-table check of verify (a missing table, or one probed from another build than the shipped core);
# no cores prerequisite, so it never builds. It warns and never fails.
verify-options:
	@OPTIONS_DIR=$(OPTIONS_DIR) JNILIBS_DIR=$(JNILIBS_DIR) OPTIONS_IDS="$(OPTIONS_IDS)" sh verify.sh --options-only
# Every core ships its licence: output/licenses/<core>.txt or a curated ../LICENSES/<core>.txt. No prerequisites, so it
# never builds; the release workflow runs it on the downloaded cores.
check-licenses:
	@missing=; for c in $(CORES); do \
	  test -s $(OUTPUT_ROOT)/licenses/$$c.txt || test -s ../LICENSES/$$c.txt || missing="$$missing $$c"; \
	done; \
	if [ -n "$$missing" ]; then echo "check-licenses: no licence for$$missing" >&2; exit 1; fi
# Each core goes into jniLibs as lib<artifact>: Android 14 and older extract only lib*.so at install, so an unprefixed
# core is missing from nativeLibraryDir and fails to dlopen there.
install: verify check-licenses
	mkdir -p $(JNILIBS_DIR) $(ASSETS_DIR)
	for f in $(OUTPUT_ROOT)/*.so; do cp "$$f" $(JNILIBS_DIR)/lib$$(basename "$$f"); done
	test ! -d $(OUTPUT_ROOT)/system || cp -R $(OUTPUT_ROOT)/system/. $(ASSETS_DIR)/

# nxprobe for the device (arm64 PIE, 16 KB pages); the host build and tests are make -C probe test.
$(OUTPUT_ROOT)/nxprobe: probe/nxprobe.c probe/opts_json.c probe/opts_json.h probe/libretro.h
	mkdir -p $(OUTPUT_ROOT)
	$(LLVM_BIN)/aarch64-linux-android26-clang -std=c11 -O2 -fPIE -pie $(PAGE_LDFLAGS) -o $@ probe/nxprobe.c probe/opts_json.c -ldl -llog
nxprobe: $(OUTPUT_ROOT)/nxprobe

# Not part of all: needs a device. Probes every core in turn, one make per core so a failure (or fbneo without
# FBNEO_PROBE_ROM) moves on to the next; exits non-zero at the end naming the cores that failed.
options:
	@failed=; for core in $(CORES); do \
	  $(MAKE) -k --no-print-directory options-$$core || failed="$$failed $$core"; \
	done; \
	if [ -n "$$failed" ]; then echo "options: failed for$$failed"; exit 1; fi

# CORES as a one-line JSON array, for the release workflow's build matrix (make -s -C cores cores-json).
COMMA := ,
cores-json:
	@echo '[$(subst " ","$(COMMA)",$(patsubst %,"%",$(strip $(CORES))))]'
# armsx2's NDK pin, for the release workflow's sdkmanager step (make -s -C cores print-armsx2-ndk).
print-armsx2-ndk:
	@echo '$(armsx2_NDK_VERSION)'
.PHONY: print-armsx2-ndk

clean: $(foreach CORE,$(CORES),clean-$(CORE))
nuke:
	rm -rf ./$(OUTPUT_ROOT) ./$(SRC_ROOT) ./$(BUILD_ROOT)
