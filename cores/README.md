# cores

Builds the libretro cores bundled in the app from pinned upstream sources with the Android NDK.
No binaries are tracked; `src/`, `build/`, `output/` and the app's `jniLibs/` are gitignored.

Prerequisites: Android NDK 29.0.14206865 and cmake 3.22.1 from the Android SDK
(`~/Library/Android/sdk`), GNU make >= 3.82 (`brew install make`, then use `gmake`), git.

    gmake check-tools      # find NDK, cmake, ninja, llvm-readelf
    gmake                  # default goal: build all cores into output/ (same as `gmake cores`)
    gmake cores            # build every core into output/
    gmake <core>           # build one core, e.g. gmake gambatte
    gmake verify           # 16 KB-alignment / AArch64 check over output/*.so (implies cores)
    gmake install          # verify, then copy cores + assets into androidApp (implies cores via verify)
    gmake all              # cores + verify + install
    gmake clone-<core>     # clone/fetch and check out the pinned commit only
    gmake status-<core>    # show the checked-out commit
    gmake clean-<core>     # remove build outputs only (obj/, libs/, build/, output .so); keeps src/<core>
    gmake distclean-<core> # clean-<core> plus removal of the src/<core> checkout
    gmake clean            # run clean-<core> for every core
    gmake nuke             # delete src/, build/, output/

Pins live in `Makefile` as `<core>_HASH` (must be a 40-character lowercase commit SHA; the build
errors otherwise). Bump one per commit: the next `gmake <core>` re-fetches and checks out the new
SHA automatically -- the checkout is tracked by a `src/<core>/.checkout-<hash>` stamp, so changing
the hash re-runs the checkout and re-applies the patch on the fresh tree (no manual re-clone). The
re-checkout is forced (`git checkout -f`), so any local modifications in `src/<core>` -- including a
previously applied patch -- are discarded on a bump; use `distclean-<core>` to remove a checkout.
Patches go in `patches/<core>.patch`, then `patches/<core>/*.patch` in name order, applied once after each checkout.
After a patch failure, or after editing a patch or a NOTICE it adds, run `gmake distclean-<core>` before building again.
Design: `docs/superpowers/specs/2026-09-22-cores-build-pipeline-design.md`.

## Current pins (2026-09-25)

| core | repo | commit | kind | build time (M-series Mac, clean) |
|---|---|---|---|---|
| gambatte | libretro/gambatte-libretro | 77f2567d718b8973d02caa2daa8dce4953faf811 | ndk-build (patched: link) | 0:05 |
| fceumm | libretro/libretro-fceumm | e0f3086a8104f8fbbe49a2615b10401504a9533f | ndk-build | 0:06 |
| snes9x | libretro/snes9x | fae2fea08f74180759ef540ee94259213f503480 | ndk-build | 0:15 |
| genesis_plus_gx | libretro/Genesis-Plus-GX | f27655b592ef43f317272d79883bcaa10d3ac8ad | ndk-build | 0:07 |
| mgba | libretro/mgba | 7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6 | cmake (patched: real link cable) | 0:09 |
| melondsds | JesseTG/melonds-ds | f394adbacb5722ee97c1b37c8064da9a25818310 (v1.4.0) | cmake (JIT, GLES 3.2 port of the new GL renderer, patched; 11 FetchContent deps pinned offline; 6.6 MB stripped) | 0:50 |
| mupen64plus_next | libretro/mupen64plus-libretro-nx | 15bada85906593376e8fd697f1b0b3af3a20f512 | ndk-build (GLES3, patched: cheat code parts; hi-res packs read through the VFS) | 0:20 |
| ppsspp | hrydgard/ppsspp | d4e3c7eb2bf0ec9241621b3752cd3e5406178c78 | cmake (patched: cheat slots) | 16:24 |
| race | libretro/race | 171950ea64c96ead503340ed60fa7ba18f56325c | ndk-build | 0:04 |
| mednafen_pce_fast | libretro/beetle-pce-fast-libretro | 52675734da114a19b3705f03906b1455f3d76644 | ndk-build | 0:05 |
| mednafen_vb | libretro/beetle-vb-libretro | d5ae54c8e23b573a3b34e888b2d48a588027e92c | ndk-build | 0:03 |
| handy | libretro/libretro-handy | bc55d462f0b2d6b073ea93dc552ebd73cec60fd1 | ndk-build | 0:03 |
| stella2014 | libretro/stella2014-libretro | 3cc89f0d316d6c924a5e3f4011d17421df58e615 | ndk-build | 0:06 |
| prosystem | libretro/prosystem-libretro | 8a88014287c7a01cd568067e5a557d0a2b2a051f | ndk-build | 0:03 |
| a5200 | libretro/a5200 | 40c6f2f1ad4a3145b328d5baaf010fae6c7e752b | ndk-build | 0:03 |
| mednafen_wswan | libretro/beetle-wswan-libretro | 4b01295838ea89e3f1355bbe4cb5cf98aa6108cd (HEAD on 2026-09-25; no Brick pin) | ndk-build | 0:01 |
| mednafen_supafaust | libretro/supafaust | 642d1d1b6684aa7e306a02a89885f3f5456a5157 | ndk-build (patched: cheats, arm64 timed wait) | 0:07 |
| pokemini | libretro/PokeMini | 132111b76343559860532a1ccc094f93f1ed5650 | ndk-build (patched) | 0:04 |
| fake08 | jtothebell/fake-08 | 814991a2571ad3970e386cef48f3b148aa1c27b9 | ndk-build (`platform/libretro`, `libfake08.so`) | 0:09 |
| prboom | libretro/libretro-prboom | 4849268e7342494193f2bbf2d0128a751cf58da7 | ndk-build (+ `prboom.wad`; 3.4 MB stripped) | 0:05 |
| gpsp | libretro/gpsp | 5819380c2ffb0900219d700a382ee68c464ebb99 | ndk-build (dynarec, patched; link) | 0:12 |
| pcsx_rearmed | libretro/pcsx_rearmed | 7b0e8f8fadfbce3d170d195b9d9eecaabfa08c8e | ndk-build (dynarec, `USE_LIBRETRO_VFS=1`) | 0:10 |
| swanstation | libretro/swanstation | 631f0fb31b809e0f889d7c551fe695788e39c1d3 | cmake (GLES/Vulkan HW renderer, patched) | 0:12 |
| picodrive | irixxxx/picodrive | 9c298c1fe7347a50b07a494cdb8c91d2f9b40c6a | ndk-build (SH2 dynarec, patched; 1.7 MB stripped) | 1:16 |
| gearcoleco | drhelius/Gearcoleco | 2805e70fd56c3bb6a0412a4ddaf210eefc8a5d7c (1.7.6+11) | ndk-build (`platforms/libretro`; 0.9 MB stripped) | 0:10 |
| fbneo | libretro/FBNeo | d15071abf14191eebb4a5d7c7147398edcf237c8 | ndk-build (`src/burner/libretro`, + `system/fbneo/`; 69.9 MB stripped) | 0:28 |
| flycast (DC) | flyinghead/flycast | 7adbf3caef251b153e348df3f469a0f35878339d (master, 93 past v2.7) | cmake (dynarec, GLES3, patched; 6 submodules; 17.2 MB stripped) | 1:15 |
| dolphin | libretro/dolphin | 32c61f9d0e933331bb7087ba57e96883241b4960 | cmake 3.30.3 (LTO, GLES, patched; 34 submodules; + system/dolphin-emu/Sys 14 MB; ~15 MB stripped) | 4:09 |
| armsx2 (PS2) | ARMSX2/ARMSX2 | ba6892742e357085897d0a151bd8e40485dda968 (2.9.2) | cmake 3.30.3 (upstream's dependency script into build/armsx2-deps; GLES/Vulkan; page size read at runtime, so 4 KB and 16 KB kernels; games open through the libretro VFS, no ISO copy; + system/pcsx2/resources 11 MB; 31.0 MB stripped) | 5:12 |

melondsds (melonDS DS) replaced libretro/melonDS on 2026-09-27. Its eleven CMake FetchContent dependencies are pinned
by full SHA in `MELONDSDS_DEPS` and cloned into `src/melondsds-deps/<name>-src` before configure, which runs with
`FETCHCONTENT_FULLY_DISCONNECTED=ON`, so the build never fetches on its own; editing a pin re-runs the checkout. The
libretro target is a CMake MODULE library, so the 16 KB page flag is passed as `CMAKE_MODULE_LINKER_FLAGS` (the
template sets only the SHARED one). Its 0:50 includes the core and dependency clones. The configure step also writes
`build/melondsds/melondsds-LICENSE.txt`; `LICENSES/melondsds.txt` at the repo root is that file plus embed-binaries'
MIT licence and the melonDS-android-lib note under melonDS, and ships in the APK as `licenses/melondsds.txt`;
regenerate it (keeping both additions) when a pin moves.

melondsds builds the melonDS GL renderer on GLES 3.2 (`ENABLE_OPENGL=ON`, `OPENGL_PROFILE=OpenGLES3`); 3D renders on
the GPU when `melonds_render_mode = opengl`. Two patches:
- `patches/melonds.patch`: melonDS-android-lib's GLES 3.2 port of melonDS's GL renderer, compositor and compute
  renderer (GPL-3.0, attributed in `LICENSES/melondsds.txt`), applied to the melonDS dependency
  (`src/melondsds-deps/melonds-src`, stamp `.nx-patched`, re-applied on a reset tree when the patch changes).
- `patches/melondsds.patch`: melonDS DS itself: drops upstream's GLES refusal, sets a GLES 3.2 context request (glsm
  still asks for a plain GLES 3 context in practice; see the main README's known issues), ports its compositor shaders
  to GLSL ES 3.00, and keeps its VAO state away from glsm and the frontend's client-array draw.

pcsx_rearmed is built with `USE_LIBRETRO_VFS=1` (its `jni/Android.mk` defaults to 0, which would bypass the runner's
virtual files) and only the `frontend/libpicofe` submodule. gpsp and pcsx_rearmed are dynarec cores: `verify` checks
only static alignment, so each passed a runtime smoke test on a 16 KB-page arm64 emulator image (android-35
google_apis_ps16k) on 2026-09-25: gpsp with a commercial cartridge dump (60 s of gameplay, save state, relaunch, load,
picture and input live), pcsx_rearmed with the MIT ps1-tests animated-triangle image (same cycle). pcsx_rearmed's 0:05
excludes the clone, which was already cached; the other times include it.

`patches/gpsp.patch` (2026-09-29) fixes a crash on a GBA relaunch in the same process. gpsp's recompiler cache (10.5 MB)
must be mapped within +/-128 MB of the core's code; after the runner dlclosed the core, the next dlopen could land
where no such gap was free, `map_jit_block` returned NULL unchecked and `retro_load_game` crashed writing to it. The
patch maps the block once and keeps it (retro_deinit no longer unmaps it; a destructor does if the library is ever
unloaded), and `retro_init` resets the state a fresh load used to give (cheats, RTC and input edge state,
serial IRQ, converted palette, option globals). The app keeps gpsp loaded between games (`CoreDescriptor.keepLoaded`,
RTLD_NODELETE in the runner), so the block is reused. No emulation source is touched; states are unchanged.
Rebased on `5819380c` (2026-10-28, nx-redux #287's pin): upstream now checks the mapping itself and runs on the
interpreter without one, so our load refusal is gone; the reset also clears `check_variables`' sound-rate flag, or a
later game would apply `gpsp_sound_rate` as a mid-session change during its load. That pin adds `gpsp_rtc_time_source`;
the app defaults it to `system` (`CoreRegistry`), as the Brick's GBA.pak does.
On an AYANEO Pocket Air Mini (Android 11, Mali, 2 GB) no gap was left within +/-128 MB of the core at all, even on a
fresh start (the Mali driver and ART fill it), so every GBA game ran on the interpreter. Since 2026-10-29 the patch's
`memmap.c` part gives Android arm64 a 16 KB-aligned 10.5 MB block in the core's own `.bss`, made RWX with `mprotect`
on first use: always next to the code, no W+E segment in the ELF, untouched pages cost no RAM. The mmap search (then the
interpreter) is the fallback if `mprotect` is refused.
Since 2026-10-30 its `sound.c` part derives the PSG clock (length, envelope and sweep, 256 Hz) from `gpsp_sound_rate`:
upstream fixes the per-sample step at the 65536 Hz value, so at 32768 Hz (the option's default) those units ran at half
speed, and GB-style notes held twice as long (Pokémon FireRed sounded off; 65536 was fine). Emulation-only; states load.
Since 2026-11-03 it also holds each DirectSound FIFO sample until the next (zero-order hold, as the hardware and mGBA
do; upstream's linear interpolation cut 7-22 dB above 8 kHz and sounded dull), and reads the mix out x3/4, on mGBA's
scale (the hardware's 10-bit output x48; upstream was 4/3 of it, 2.5 dB louder). States load.

fceumm needs no patch since 2026-11-07: upstream's `path_vfs_init` call (libretro-fceumm #660) makes the content check
(`path_is_valid`) use the runner's VFS v3 `stat`, so a SAF file-descriptor ROM (`/rom/<name>`) loads without a cache
copy. The app carried that one line as `patches/fceumm.patch` until then.

picodrive, gearcoleco and fbneo were added on 2026-09-26 (Brick pins). picodrive moved to 9c298c1f on 2026-11-07: its
pad type is no longer the `picodrive_input1/2` options but the port device (`RETRO_DEVICE_MD_3BTN` / `_6BTN`, 257 / 513,
as genesis_plus_gx's), so the app sends Controller Type as devices; its submodules are fetched non-recursively
(`picodrive_SUBMODULES`), since dr_libs' test-only miniaudio submodule hangs the clone. The Brick still runs b0be121b
until nx-redux is bumped to match (netplay lockstep and states need the same pin). `patches/picodrive.patch` (build script only)
sets `use_sh2drc := 1` for arm64 in `jni/Android.mk`; upstream enables the 32X SH2 dynarec for 32-bit arm only, and the
arm64 emitter (`cpu/drc/emit_arm64.c`) is in the tree. fbneo stages `system/fbneo/hiscore.dat` (from `metadata/`) and
`system/fbneo/sets.tsv` (name, title, parent, romof, BIOS flag; 8510 sets), generated by `scripts/fbneo-sets.py` from the
pinned arcade-only ClrMame Pro XML DAT. picodrive's and fbneo's times exclude the clone (0:42 for picodrive; FBNeo's is
large and network-bound); gearcoleco's includes it.

gearcoleco moved to 1.7.6+11 (2805e70f) on 2026-11-07. Upstream added the ADAM computer (disks, data packs, its EOS and
SmartWRITER firmware); the app offers cartridges only, so `gearcoleco_cartridge_hardware` and the three `gearcoleco_adam_*`
options are locked at their defaults (CoreRegistry). Only a load test ran (no ColecoVision game on the Nova); 1.6+ also
checks a save state's machine and cartridge identity, so states from the old pin may be refused.

flycast (tag DC) was added on 2026-09-28 from the Dreamcast GL spike. It builds the top-level CMake target
`flycast_libretro` with flycast's own libretro Android CI flags and only the six submodules the Android libretro build
uses; its 1:15 includes the clone. `LICENSES/flycast.txt` (flycast's GPL-2.0-or-later text, the core being GPL-3.0 as
built because it links xBRZ, plus the notices of the dependencies linked into it) ships in the APK as `licenses/flycast.txt`. `patches/flycast.patch` has five hunks (since the
2026-11-07 bump upstream links libandroid into the libretro target itself):
- `core/hw/maple/maple_devs.cpp`: the VMU file is flushed after every write (block write and full save). flycast writes
  it through stdio and flushed only on close, so a process killed without unloading (a crash, the hang watchdog, a
  force-stop) lost the last writes; Soulcalibur's new save then had a directory entry without its FAT chain, and the
  game reported its data as unreadable.
- `shell/libretro/oslib.cpp` `getVmuPath`: per-game VMUs are named after the content file (`<content>.A1.bin`) instead
  of the disc's game ID, so the app's saves mirror can tie a VMU to its game; an existing game-ID VMU from an earlier
  run is read once, then rewritten as `<content>.A1.bin` and deleted by flycast (`maple_devs.cpp` `OnSetup`). The
  shared-VMU branch (`vmu_save_<port>.bin`) is unchanged.
- `core/rend/gles/gles.h` `GlVertexArray::term`: no longer deletes vertex array name 0 (the post-processor's, never
  created on the blit path, on every render-size change). The spec ignores that, but the Android emulator's GLES stack
  drops the default vertex array's client state, and the frontend's presentation quad then draws nothing (black screen
  after any resize: internal resolution 320x240, emulated framebuffer).
- `core/hw/naomi/naomi_roms.cpp` (from nx-redux 959dbfb7): the `awbios` entry also accepts the modern MAME dump
  `bios.ic23_l` (64 KB, CRC `e5693ce3`) beside `bios0.ic23` (128 KB, of which flycast now reads only the first 64 KB;
  the rest is writable). `loadBios` tries each listed blob by CRC, then by name, and maps the first one found, so an
  `awbios.zip` holding only `bios.ic23_l` boots Atomiswave games.
- `core/rend/vulkan/vk_context_lr.cpp` `VkCreateDevice`: also enables the frontend's required device extensions (its
  AHardwareBuffer bridge needs them).

The cores added on 2026-09-25 use nx-redux's pins (`workspace/tg5040/cores/Makefile`) so save states cross with the
Brick where the core matches; a bump is one commit per core and needs that core's device pass again.
Build from a worktree with the main checkout's caches as roots, so the existing cores are not re-cloned:
`gmake -C cores SRC_ROOT=<main>/cores/src OUTPUT_ROOT=<main>/cores/output BUILD_ROOT=<main>/cores/build <core>`.
Deviations (build flags only, no source patch): `mednafen_wswan_FLAGS = -C jni`, because its `jni/Android.mk` includes
`../Makefile.common` relative to the working directory; `mednafen_supafaust_FLAGS = APP_CPPFLAGS=-Wno-register`, because
`OwlResampler_neon.inc` uses the C++17-removed `register` specifier, which the NDK's clang rejects by default.

(2026-09-23, the original eight cores) Clean `gmake all` (re-clone + build all eight + verify + install): 17m49s total on an M-series Mac,
run 2026-09-23. Per-core times are from `output/*.so` mtimes relative to the run start (cores build
sequentially), so each includes that core's shallow clone; ppsspp dominates because of its recursive
submodule clone (ffmpeg, glslang, SPIRV-Cross, ...) and CMake build. `mgba` builds with CMake, not
ndk-build, because upstream ships no libretro `Android.mk`. Patches: `patches/pokemini.patch` (nx-redux's resume-audio patch, its libretro.c part: the first state
load after start is deferred 40 frames so audio resumes; since the 2026-11-08 bump its `source/PokeMini.c` part, the
state loader reading the audio chunk under `"AUD-"` instead of `"LCD-"`, is upstream) (pcsx_rearmed's
build-path patch went with the 2026-11-07 pin, which has upstream's fix 6f230b1; the Brick stays on 6eb679d0, patched there,
until nx-redux is bumped to match). Two staging
deviations: ppsspp's `assets/` as `system/PPSSPP/`, and prboom's `prboom.wad` (its own game data, in the repo) as
`system/prboom.wad`. fake08 builds from `platform/libretro` and its module is `fake08`, so its artifact is `libfake08.so`.

Reproducibility (2026-09-23, the original eight cores): rebuilding from the same sources (`gmake clean && gmake cores`), 7 of 8 cores are
bit-identical. Only `ppsspp` differs, because it embeds a build timestamp: its bundled libpng
(`ext/libpng17/pngerror.c`) compiles `__DATE__`/`__TIME__` into the copyright string
(e.g. `translated Sep 23 2026 00:59:43`). The other seven cores are byte-for-byte reproducible.

## melonDS DS (DS): check save states on every pin bump

melonDS refuses a save state whose format major version differs from its own (`SAVESTATE_MAJOR` in the melonDS
dependency's `src/Savestate.h`), and melonDS DS also rejects one whose size differs from what the running core
expects. v1.4.0 (melonDS major 14) cannot load v1.3.1 states (major 12): the load fails cleanly with a toast, the game
keeps running, and in-game battery saves are unaffected. On every pin bump, compare `SAVESTATE_MAJOR` with the
current pin's; if it changed, say in the release notes that DS save states from earlier versions won't load.

Checked: v1.3.1 → v1.4.0 (2026-11-07, Nova): old states refused cleanly (major 12 → 14).

## Azahar (3DS): check save states on every pin bump

Pin and build notes are in the `Makefile` above `azahar_HASH`. A 3DS save state records the git revision of the core
that wrote it. Upstream's libretro core refuses any state from another revision, so before our patch every pin bump
broke all the states players had. `patches/azahar.patch` (`src/core/savestate.cpp`, `System::LoadStateBuffer`) loads
such a state with a warning instead.

**The risk:** the revision check was upstream's guard against a changed state format. Without it, a state from an older
core is read by the new code. If the format changed, it usually fails cleanly: the reader throws, `retro_unserialize`
returns false and the app shows its load failure. A change the reader does not notice could instead load a broken
game state.

**So on every Azahar pin bump:**

1. Look at the upstream diff between the two pins for serialization changes: `serialize`, `SERIALIZE_EXPORT`,
   `BOOST_CLASS_VERSION`, `template <class Archive>` and new members of serialized classes.
2. On a device, resume a state saved by the old core (the `Mario Kart 7` race state is the usual one) and play on for
   a minute: the race goes on, with no error in the log beyond the revision warning.
3. If the format changed, say so in the release notes so players know older 3DS states will not load.

Checked: 2126.1.2 → 2126.2 (2026-11-05, Nova): old states load and play.

The patch also carries azahar-emu/azahar#2718 (video views: the app lays out the 3DS screens from the core's map). On
every pin bump, re-check it: the PR's commits re-apply, its Frontend Layout and 3D option is still `citra_frontend_layout`
with `auto`, and a 3DS game sends a map (top and bottom views) in single display and Split. Drop the carried commits
once the pin includes them.

## Link cores (netplay stage 2c, 2026-10-10)

GB/GBC and GBA link as on the NX Redux handheld (nx-redux main 2f25643b):
- `sio_lockstep/`: the real link cable's lockstep state machine, copied from nx-redux `workspace/all/cores/sio_lockstep`
  with its host tests (`gmake test-sio-lockstep`). GPL-2.0-or-later; `NOTICE.txt` is appended to the gpSP and mGBA
  licence files.
- gpSP: nx-redux `patches/gpsp/001` (RFU disconnect fix), `002` (RFU queue 4 → 16) and `004` (real link cable:
  `gpsp_serial` `lockstep`, and `auto_cable`, which the app sets for `auto` over the USB cable) after our `gpsp.patch`;
  their `003` is left out (our patch covers the JIT cache). `NX_SIOLS_DIR` is passed on the ndk-build line.
- mGBA: nx-redux `patches/mgba/001` as is (real link cable; a `CMakeLists.txt` hunk compiles `sio_lockstep` in through
  `-DNX_SIOLS_DIR`). The combined core is GPL-2.0-or-later (MPL-2.0 §3.3).
- gambatte: `HAVE_NETWORK` is on upstream (`jni/Android.mk`); nx-redux `patches/gambatte/002` (async slave reply) minus
  its `Makefile.common` hunk (`-lpthread`; bionic has pthreads in libc).
