# cores

Builds the libretro cores bundled in the app from pinned upstream sources with the Android NDK.
No binaries are tracked; `src/`, `build/`, `output/` and the app's `jniLibs/` are gitignored.

Prerequisites: Android NDK 26.1.10909125 and cmake 3.22.1 from the Android SDK
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
| gambatte | libretro/gambatte-libretro | d9d6cd06382d1ced30de34d56d3609452323dab1 | ndk-build (patched: link) | 0:05 |
| fceumm | libretro/libretro-fceumm | 236ccdfc911e84c60fea6b9d0699c2d440a8de14 | ndk-build (patched) | 0:06 |
| snes9x | libretro/snes9x | fae2fea08f74180759ef540ee94259213f503480 | ndk-build | 0:15 |
| genesis_plus_gx | libretro/Genesis-Plus-GX | c2838c7dc4236fc2fe94e5dbd08b41486067918e | ndk-build | 0:07 |
| mgba | libretro/mgba | 7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6 | cmake (patched: real link cable) | 0:09 |
| melondsds | JesseTG/melonds-ds | bc4e4b67d2d470d7c682810a1e892cafd6f9082b (v1.3.1) | cmake (JIT, GLES3 GL renderer, patched; 11 FetchContent deps pinned offline; 7.1 MB stripped) | 0:50 |
| mupen64plus_next | libretro/mupen64plus-libretro-nx | 6752836de8b224febfd5708444755b77712ac939 | ndk-build (GLES3, patched: cheat code parts; hi-res packs read through the VFS) | 0:20 |
| ppsspp | hrydgard/ppsspp | 2a1700a7ee495310c52c85f9436652cc8f26e45e | cmake | 16:24 |
| race | libretro/race | 171950ea64c96ead503340ed60fa7ba18f56325c | ndk-build | 0:04 |
| mednafen_pce_fast | libretro/beetle-pce-fast-libretro | 52675734da114a19b3705f03906b1455f3d76644 | ndk-build | 0:05 |
| mednafen_vb | libretro/beetle-vb-libretro | 65debc7c4c7b85e2fd988d2be53496c2cf0b5f44 | ndk-build | 0:03 |
| handy | libretro/libretro-handy | fca239207e9c111da3e85d2faf0b1b9d7524e498 | ndk-build | 0:03 |
| stella2014 | libretro/stella2014-libretro | 3cc89f0d316d6c924a5e3f4011d17421df58e615 | ndk-build | 0:06 |
| prosystem | libretro/prosystem-libretro | acae250da8d98b8b9707cd499e2a0bf6d8500652 | ndk-build | 0:03 |
| a5200 | libretro/a5200 | 526404072821bb2021fab16f8c5dbbca300512c8 | ndk-build | 0:03 |
| mednafen_wswan | libretro/beetle-wswan-libretro | 4b01295838ea89e3f1355bbe4cb5cf98aa6108cd (HEAD on 2026-09-25; no Brick pin) | ndk-build | 0:01 |
| mednafen_supafaust | libretro/supafaust | e25f66765938d33f9ad5850e8d6cd597e55b7299 | ndk-build | 0:07 |
| pokemini | libretro/PokeMini | 78656d4615691c393e65e48672ce5fff5b1f97d3 | ndk-build (patched) | 0:04 |
| fake08 | jtothebell/fake-08 | f6bab5a7ba521ce440e45d1aeef6122674be6ee9 | ndk-build (`platform/libretro`, `libfake08.so`) | 0:09 |
| prboom | libretro/libretro-prboom | 93c8e7a2074e4fd8410398e3d571c6d9afec1d84 | ndk-build (+ `prboom.wad`) | 0:05 |
| gpsp | libretro/gpsp | 5819380c2ffb0900219d700a382ee68c464ebb99 | ndk-build (dynarec, patched; link) | 0:12 |
| pcsx_rearmed | libretro/pcsx_rearmed | 6eb679d064a1efe92af79fe9970c0591c8da02d4 | ndk-build (dynarec, `USE_LIBRETRO_VFS=1`, patched) | 0:05 |
| swanstation | libretro/swanstation | dd4805619bbbfc6e0d1776372cb16377088b448e (the Brick's PSX.pak pin) | cmake (GLES HW renderer) | 0:12 |
| picodrive | irixxxx/picodrive | b0be121b7d58d6ee1ee2809974e62893c80a8264 | ndk-build (SH2 dynarec, patched; 1.6 MB stripped) | 0:24 |
| gearcoleco | drhelius/Gearcoleco | ed5095b3ce158ff0cf82395a61c2476505634894 | ndk-build (`platforms/libretro`; 0.7 MB stripped) | 0:10 |
| fbneo | libretro/FBNeo | 6a5cc250c2db8d874a06ee86e302a2a78918b4c1 | ndk-build (`src/burner/libretro`, + `system/fbneo/`; 63.2 MB stripped) | 0:28 |
| flycast (DC) | flyinghead/flycast | 5aa091fde632fb332c8d8c34e280d62dc951954c (v2.7) | cmake (dynarec, GLES3, patched; 6 submodules; 23.1 MB stripped) | 1:15 |
| dolphin | libretro/dolphin | 3aac3f052f733f8df2f1616843a4d2e14002ed11 | cmake 3.30.3 (LTO, GLES, patched; 34 submodules; + system/dolphin-emu/Sys 14 MB; ~15 MB stripped) | 4:09 |
| armsx2 (PS2) | ARMSX2/ARMSX2 | 023224576867a3846faedc7640911772c7783529 (2.9) | cmake 3.30.3, NDK r29 (own pin; upstream's dependency script into build/armsx2-deps; GLES/Vulkan; page size read at runtime, so 4 KB and 16 KB kernels; games open through the libretro VFS, no ISO copy; + system/pcsx2/resources 11 MB; 33.0 MB stripped) | 5:12 |

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

`patches/fceumm.patch` adds one `path_vfs_init` call next to its `filestream_vfs_init`, so the content check
(`path_is_valid`) uses the runner's VFS v3 `stat` and a SAF file-descriptor ROM (`/rom/<name>`) loads without a cache
copy; upstream fceumm only wires the file-stream callbacks. No emulation source is touched, so states still cross with
the Brick.

picodrive, gearcoleco and fbneo were added on 2026-09-26 (Brick pins). `patches/picodrive.patch` (build script only)
sets `use_sh2drc := 1` for arm64 in `jni/Android.mk`; upstream enables the 32X SH2 dynarec for 32-bit arm only, and the
arm64 emitter (`cpu/drc/emit_arm64.c`) is in the tree. fbneo stages `system/fbneo/hiscore.dat` (from `metadata/`) and
`system/fbneo/sets.tsv` (name, title, parent, romof, BIOS flag; 8047 sets), generated by `scripts/fbneo-sets.py` from the
pinned arcade-only ClrMame Pro XML DAT. picodrive's and fbneo's times exclude the clone (0:42 for picodrive; FBNeo's is
large and network-bound); gearcoleco's includes it.

flycast (tag DC) was added on 2026-09-28 from the Dreamcast GL spike. It builds the top-level CMake target
`flycast_libretro` with flycast's own libretro Android CI flags and only the six submodules the Android libretro build
uses; its 1:15 includes the clone. `LICENSES/flycast.txt` (flycast's GPL-2.0-or-later text, the core being GPL-3.0 as
built because it links xBRZ, plus the notices of the dependencies linked into it) ships in the APK as `licenses/flycast.txt`. `patches/flycast.patch` has four hunks:
- `CMakeLists.txt`: links libandroid into the libretro target (upstream links it for the standalone app only), so
  vmem's weak `ASharedMemory_create` resolves. Without it vmem falls back to `/dev/ashmem`, which targetSdk 29+ apps
  cannot open, runs without fast-mem, and the dynarec's write-protect fault handler crashes.
- `shell/libretro/oslib.cpp` `getVmuPath`: per-game VMUs are named after the content file (`<content>.A1.bin`) instead
  of the disc's game ID, so the app's saves mirror can tie a VMU to its game; an existing game-ID VMU from an earlier
  run is read once, then rewritten as `<content>.A1.bin` and deleted by flycast (`maple_devs.cpp` `OnSetup`). The
  shared-VMU branch (`vmu_save_<port>.bin`) is unchanged.
- `core/rend/gles/gles.h` `GlVertexArray::term`: no longer deletes vertex array name 0 (the post-processor's, never
  created on the blit path, on every render-size change). The spec ignores that, but the Android emulator's GLES stack
  drops the default vertex array's client state, and the frontend's presentation quad then draws nothing (black screen
  after any resize: internal resolution 320x240, emulated framebuffer).
- `core/hw/naomi/naomi_roms.cpp` and `naomi_cart.cpp` `loadBios` (from nx-redux 959dbfb7): the `awbios` entry also
  accepts the modern MAME dump `bios.ic23_l` (64 KB, CRC `e5693ce3`) as an alternative to `bios0.ic23` (128 KB).
  `loadBios` tries each listed blob by CRC, then by name, and maps the first one found; a 64 KB `awbios` image is
  mirrored into the upper half of the 128 KB BIOS window, as the hardware's address decoding does. So an `awbios.zip`
  holding only `bios.ic23_l` boots Atomiswave games.

The cores added on 2026-09-25 use nx-redux's pins (`workspace/tg5040/cores/Makefile`) so save states cross with the
Brick where the core matches; a bump is one commit per core and needs that core's device pass again.
Build from a worktree with the main checkout's caches as roots, so the existing cores are not re-cloned:
`gmake -C cores SRC_ROOT=<main>/cores/src OUTPUT_ROOT=<main>/cores/output BUILD_ROOT=<main>/cores/build <core>`.
Deviations (build flags only, no source patch): `mednafen_wswan_FLAGS = -C jni`, because its `jni/Android.mk` includes
`../Makefile.common` relative to the working directory; `mednafen_supafaust_FLAGS = APP_CPPFLAGS=-Wno-register`, because
`OwlResampler_neon.inc` uses the C++17-removed `register` specifier, which NDK 26's clang rejects by default.

(2026-09-23, the original eight cores) Clean `gmake all` (re-clone + build all eight + verify + install): 17m49s total on an M-series Mac,
run 2026-09-23. Per-core times are from `output/*.so` mtimes relative to the run start (cores build
sequentially), so each includes that core's shallow clone; ppsspp dominates because of its recursive
submodule clone (ffmpeg, glslang, SPIRV-Cross, ...) and CMake build. `mgba` builds with CMake, not
ndk-build, because upstream ships no libretro `Android.mk`. Patches: `patches/pokemini.patch` (nx-redux's resume-audio patch, verbatim so states cross with the Brick: the first state
load after start is deferred 40 frames so audio resumes, and `source/PokeMini.c`'s state loader reads the audio chunk
under `"AUD-"` instead of `"LCD-"`, an emulation-source fix; the only nx-redux source patch carried over) and
`patches/pcsx_rearmed.patch` (build script only: the pin's `jni/Android.mk` names `encodings/compat_strl.c` instead of
`compat/compat_strl.c` under `USE_LIBRETRO_VFS=1`; fixed upstream in 6f230b1, patched here to keep nx-redux's pin). Two staging
deviations: ppsspp's `assets/` as `system/PPSSPP/`, and prboom's `prboom.wad` (its own game data, in the repo) as
`system/prboom.wad`. fake08 builds from `platform/libretro` and its module is `fake08`, so its artifact is `libfake08.so`.

Reproducibility (2026-09-23, the original eight cores): rebuilding from the same sources (`gmake clean && gmake cores`), 7 of 8 cores are
bit-identical. Only `ppsspp` differs, because it embeds a build timestamp: its bundled libpng
(`ext/libpng17/pngerror.c`) compiles `__DATE__`/`__TIME__` into the copyright string
(e.g. `translated Sep 23 2026 00:59:43`). The other seven cores are byte-for-byte reproducible.

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
