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

## Current pins

| core | repo | commit | kind | build time (M-series Mac, clean) |
|---|---|---|---|---|
| gambatte | libretro/gambatte-libretro | 77f2567d718b8973d02caa2daa8dce4953faf811 | ndk-build (patched: link) | 0:05 |
| fceumm | libretro/libretro-fceumm | e0f3086a8104f8fbbe49a2615b10401504a9533f | ndk-build (patched: FDS disk flip) | 0:06 |
| snes9x | libretro/snes9x | fae2fea08f74180759ef540ee94259213f503480 | ndk-build (patched: cheat codes, MSU-1 folder) | 0:15 |
| genesis_plus_gx | libretro/Genesis-Plus-GX | f27655b592ef43f317272d79883bcaa10d3ac8ad | ndk-build (patched: cheat codes) | 0:07 |
| mgba | libretro/mgba | 7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6 | cmake (patched: real link cable, pair mode) | 0:09 |
| melondsds | JesseTG/melonds-ds | f394adbacb5722ee97c1b37c8064da9a25818310 (v1.4.0) | cmake (JIT, GLES 3.2 port of the new GL renderer, patched; 11 FetchContent deps pinned offline; 6.6 MB stripped) | 0:50 |
| mupen64plus_next | libretro/mupen64plus-libretro-nx | 15bada85906593376e8fd697f1b0b3af3a20f512 | ndk-build (GLES3, patched: cheat code parts; hi-res packs read through the VFS; live 3D resolution) | 0:20 |
| ppsspp | hrydgard/ppsspp | d4e3c7eb2bf0ec9241621b3752cd3e5406178c78 | cmake (patched: cheat slots, link, Vulkan resolution change) | 16:24 |
| race | libretro/race | c7810dd7f172827bfa2004813bc000b13786636b | ndk-build (patched: old states) | 0:04 |
| mednafen_pce_fast | libretro/beetle-pce-fast-libretro | 2de1ce1c3f237b161c126c54ba407990bb86d1e9 | ndk-build (patched: cheat codes) | 0:05 |
| mednafen_vb | libretro/beetle-vb-libretro | d5ae54c8e23b573a3b34e888b2d48a588027e92c | ndk-build | 0:03 |
| handy | libretro/libretro-handy | bc55d462f0b2d6b073ea93dc552ebd73cec60fd1 | ndk-build | 0:03 |
| stella2014 | libretro/stella2014-libretro | 7d1361e407e63f29e52892655069e5fb4096e691 | ndk-build (patched: old states) | 0:06 |
| prosystem | libretro/prosystem-libretro | 8a88014287c7a01cd568067e5a557d0a2b2a051f | ndk-build | 0:03 |
| a5200 | libretro/a5200 | 40c6f2f1ad4a3145b328d5baaf010fae6c7e752b | ndk-build | 0:03 |
| mednafen_wswan | libretro/beetle-wswan-libretro | 4b01295838ea89e3f1355bbe4cb5cf98aa6108cd (no Brick pin) | ndk-build | 0:01 |
| mednafen_supafaust | libretro/supafaust | 642d1d1b6684aa7e306a02a89885f3f5456a5157 | ndk-build (patched: cheats, arm64 timed wait) | 0:07 |
| pokemini | libretro/PokeMini | 132111b76343559860532a1ccc094f93f1ed5650 | ndk-build (patched) | 0:04 |
| fake08 | jtothebell/fake-08 | 814991a2571ad3970e386cef48f3b148aa1c27b9 | ndk-build (`platform/libretro`, `libfake08.so`) | 0:09 |
| prboom | libretro/libretro-prboom | 4849268e7342494193f2bbf2d0128a751cf58da7 | ndk-build (+ `prboom.wad`; 3.4 MB stripped) | 0:05 |
| gpsp | libretro/gpsp | 5819380c2ffb0900219d700a382ee68c464ebb99 | ndk-build (dynarec, patched; link) | 0:12 |
| pcsx_rearmed | libretro/pcsx_rearmed | 7b0e8f8fadfbce3d170d195b9d9eecaabfa08c8e | ndk-build (dynarec, `USE_LIBRETRO_VFS=1`, patched: enhanced resolution mid-game) | 0:10 |
| swanstation | libretro/swanstation | 631f0fb31b809e0f889d7c551fe695788e39c1d3 | cmake (GLES/Vulkan HW renderer, patched) | 0:12 |
| picodrive | irixxxx/picodrive | 9c298c1fe7347a50b07a494cdb8c91d2f9b40c6a | ndk-build (SH2 dynarec, patched; 1.7 MB stripped) | 1:16 |
| gearcoleco | drhelius/Gearcoleco | 2805e70fd56c3bb6a0412a4ddaf210eefc8a5d7c (1.7.6+11) | ndk-build (`platforms/libretro`; 0.9 MB stripped) | 0:10 |
| fbneo | libretro/FBNeo | d15071abf14191eebb4a5d7c7147398edcf237c8 | ndk-build (`src/burner/libretro`, patched: state size check; + `system/fbneo/`; 69.9 MB stripped) | 0:28 |
| flycast (DC) | flyinghead/flycast | 7adbf3caef251b153e348df3f469a0f35878339d (master, 93 past v2.7) | cmake (dynarec, GLES3, patched; 6 submodules; 17.2 MB stripped) | 1:15 |
| yabasanshiro (Saturn) | libretro/yabause | c6de72c08dda71b74648e9e055598cec1a974a11 (yabasanshiro branch) | ndk-build (`yabause/src/libretro`, SH-2 dynarec, GLES3, patched: RAM for achievements, disc swap + m3u, per-game 64 KB backup RAM written through atomically, HLE when forced without a BIOS, live resolution, `+`-joined cheat codes split into pairs, the HLE BIOS's 32 KB backup RAM, save states without `tmpfile`; 1.5 MB stripped) | 0:04 |
| azahar (3DS) | azahar-emu/azahar | 219d743a1e698101fe1ab8bef43e6563126c3c27 (2126.2) | cmake 3.30.3 (GLES 3.2/Vulkan, patched; ~35 submodules) | |
| dolphin | libretro/dolphin | 32c61f9d0e933331bb7087ba57e96883241b4960 | cmake 3.30.3 (LTO, GLES, patched; 34 submodules; + system/dolphin-emu/Sys 14 MB; ~15 MB stripped) | 4:09 |
| armsx2 (PS2) | ARMSX2/ARMSX2 | 6d9511e29e3e00d5ab78899f5292b967ab850ddb (nightly-20261114) | cmake 3.30.3 (thin LTO + upstream's profile; upstream's dependency script into build/armsx2-deps; GLES/Vulkan; page size read at runtime, so 4 KB and 16 KB kernels; games open through the libretro VFS, no ISO copy; + system/pcsx2/resources 11 MB; 33.5 MB stripped) | 5:12 |

What each patch does is written above its core in `Makefile`; the notes below add what the Makefile does not say.

Where the Brick has the core, the pin follows nx-redux's (`workspace/tg5040/cores/Makefile`) so save states and netplay
cross with the Brick (a core's Makefile entry says when it does not); a bump is one commit per core and needs that
core's device pass again.
Build from a worktree with the main checkout's caches as roots, so the existing cores are not re-cloned:
`gmake -C cores SRC_ROOT=<main>/cores/src OUTPUT_ROOT=<main>/cores/output BUILD_ROOT=<main>/cores/build <core>`.

Build deviations without a source patch: `mednafen_wswan_FLAGS = -C jni`, because its `jni/Android.mk` includes
`../Makefile.common` relative to the working directory; `mednafen_supafaust_FLAGS = APP_CPPFLAGS=-Wno-register`, because
`OwlResampler_neon.inc` uses the C++17-removed `register` specifier, which the NDK's clang rejects by default. `mgba`
builds with CMake, not ndk-build, because upstream ships no libretro `Android.mk`. fake08 builds from
`platform/libretro` and its module is `fake08`, so its artifact is `libfake08.so`.

Staged beside the cores (`output/system/`, copied to the app's `assets/system/` by `gmake install`): ppsspp's `assets/`
as `PPSSPP/`, prboom's `prboom.wad` (its own game data, in the repo), fbneo's `fbneo/hiscore.dat` and `fbneo/sets.tsv`
(name, title, parent, romof, BIOS flag; `scripts/fbneo-sets.py` over the pinned arcade-only ClrMame Pro XML DAT),
flycast's `flycast/sets.tsv` (`scripts/flycast-sets.py` over the pinned `naomi_roms.cpp`), Dolphin's `Data/Sys` as
`dolphin-emu/Sys/` and ARMSX2's `bin/resources` as `pcsx2/resources/`.

melondsds (melonDS DS): its eleven CMake FetchContent dependencies are pinned by full SHA in `MELONDSDS_DEPS` and cloned
into `src/melondsds-deps/<name>-src` before configure, which runs with `FETCHCONTENT_FULLY_DISCONNECTED=ON`, so the
build never fetches on its own; editing a pin re-runs the checkout. The libretro target is a CMake MODULE library, so
the 16 KB page flag is passed as `CMAKE_MODULE_LINKER_FLAGS` (the template sets only the SHARED one). The configure
step also writes `build/melondsds/melondsds-LICENSE.txt`; `LICENSES/melondsds.txt` at the repo root is that file plus
embed-binaries' MIT licence and the melonDS-android-lib note under melonDS, and ships in the APK as
`licenses/melondsds.txt`; regenerate it (keeping both additions) when a pin moves.

melondsds builds the melonDS GL renderer on GLES 3.2 (`ENABLE_OPENGL=ON`, `OPENGL_PROFILE=OpenGLES3`); 3D renders on
the GPU when `melonds_render_mode = opengl`. Two patches:
- `patches/melonds.patch`: melonDS-android-lib's GLES 3.2 port of melonDS's GL renderer, compositor and compute
  renderer (GPL-3.0, attributed in `LICENSES/melondsds.txt`), applied to the melonDS dependency
  (`src/melondsds-deps/melonds-src`, stamp `.nx-patched`, re-applied on a reset tree when the patch changes).
- `patches/melondsds.patch`: melonDS DS itself: drops upstream's GLES refusal, sets a GLES 3.2 context request, ports
  its compositor shaders to GLSL ES 3.00, and keeps its VAO state away from glsm and the frontend's client-array draw.

For local wireless between phones, `patches/melondsds.patch` also adds the private environment call
`NX_ENVIRONMENT_SET_NDS_RADIO` (`libretrodroid/src/main/cpp/netplay/nx_nds_radio.h`, duplicated in the patch as
`src/libretro/nx_nds_radio.h`): from the emulation thread the core reports OFF, ON (the game powers its Wi-Fi on,
`Platform::MP_Begin`) and HOSTING (the game's first beacon since the last ON), and OFF again on unload and reset. The
firmware flush writes the dump back with its own MAC and nickname, not the in-memory ones the core overrides, and
`MpState::NextPacketBlock` waits its 25 ms by wall clock (`std::clock` counts CPU time).

pcsx_rearmed is built with `USE_LIBRETRO_VFS=1` (its `jni/Android.mk` defaults to 0, which would bypass the runner's
virtual files) and only the `frontend/libpicofe` submodule. gpsp and pcsx_rearmed are dynarec cores: `verify` checks
only static alignment, so after a pin bump smoke-test each on a 16 KB-page arm64 emulator image (load, 60 s of play,
save state, relaunch, load it).

`patches/gpsp.patch` has three parts; none changes the save state format:
- The recompiler cache (10.5 MB) must sit within +/-128 MB of the core's code, and a busy address space can leave no
  such gap to mmap (the Mali driver and ART fill it on an AYANEO Pocket Air Mini even on a fresh start). On Android
  arm64 `memmap.c` gives it a 16 KB-aligned block in the core's own `.bss`, made RWX with `mprotect` on first use:
  always next to the code, no W+E segment in the ELF, untouched pages cost no RAM. The mmap search (then upstream's
  interpreter) is the fallback if `mprotect` is refused. The app keeps gpsp loaded between games
  (`CoreDescriptor.keepLoaded`, RTLD_NODELETE in the runner), so the block is set up once, and `retro_init` resets what
  only a fresh library load would (cheats, RTC and input edge state, serial IRQ, converted palette, option globals, and
  `check_variables`' sound-rate flag, or a later game would apply `gpsp_sound_rate` as a mid-session change during its
  load).
- `sound.c` derives the PSG clock (length, envelope and sweep, 256 Hz) from `gpsp_sound_rate`: upstream fixes the
  per-sample step at the 65536 Hz value, so at 32768 Hz those units run at half speed and GB-style notes hold twice as
  long.
- `sound.c` holds each DirectSound FIFO sample until the next (zero-order hold, as the hardware and mGBA do; upstream's
  linear interpolation cuts 7-22 dB above 8 kHz), and reads the mix out x3/4, on mGBA's scale (the hardware's 10-bit
  output x48; upstream is 4/3 of it, 2.5 dB louder).

The app defaults `gpsp_rtc_time_source` to `system` (`CoreRegistry`), as the Brick's GBA.pak does.

fceumm: upstream's `path_vfs_init` call (libretro-fceumm #660) makes the content check (`path_is_valid`) use the
runner's VFS v3 `stat`, so a SAF file-descriptor ROM (`/rom/<name>`) loads without a cache copy.

picodrive: the pad type is the port device (`RETRO_DEVICE_MD_3BTN` / `_6BTN`, 257 / 513, as genesis_plus_gx's), not a
core option, so the app sends Controller Type as devices. Its submodules are fetched non-recursively
(`picodrive_SUBMODULES`), since dr_libs' test-only miniaudio submodule hangs the clone. `patches/picodrive.patch` (build
script only) sets `use_sh2drc := 1` for arm64 in `jni/Android.mk`; upstream enables the 32X SH2 dynarec for 32-bit arm
only, and the arm64 emitter (`cpu/drc/emit_arm64.c`) is in the tree.

gearcoleco: the core also emulates the ADAM computer (disks, data packs, its EOS and SmartWRITER firmware); the app
offers cartridges only, so `gearcoleco_cartridge_hardware` and the three `gearcoleco_adam_*` options are locked at
their defaults (`CoreRegistry`).

flycast (tag DC) builds the top-level CMake target `flycast_libretro` with flycast's own libretro Android CI flags and
only the six submodules the Android libretro build uses. `LICENSES/flycast.txt` (flycast's GPL-2.0-or-later text, the
core being GPL-3.0 as built because it links xBRZ, plus the notices of the dependencies linked into it) ships in the
APK as `licenses/flycast.txt`.

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

The patch also adds local wireless between devices (`src/citra_libretro/room_link.cpp`): Azahar's own multiplayer room
carries the games' wireless frames, as in the standalone app, but the libretro core never started it. With the
frontend's `nx_3ds_link` set to `auto`, the core makes the room ready at load without opening a socket; when the game
turns its wireless on (`src/core/hle/service/nwm/uds_wireless_hook.h`), it asks the local network for a room on UDP
24873 and joins it, or opens one on UDP 24872 for a game that hosts. `host` and `join` are the netplay sheet's session.
The ROM runs as the inserted game card, which Download Play hosting (Mario Kart 7's local play) reads from. The player's
name (`RETRO_ENVIRONMENT_GET_USERNAME`) becomes the console's profile name and its personal Mii
(`src/citra_libretro/player_mii.cpp`), which games show in multiplayer. On every pin bump, check two devices still
meet in a game's local play, and that the names show.

## Link cores

GB/GBC and GBA link as on the NX Redux handheld (nx-redux main 2f25643b):
- `sio_lockstep/`: the real link cable's lockstep state machine, copied from nx-redux `workspace/all/cores/sio_lockstep`
  with its host tests (`gmake test-sio-lockstep`). GPL-2.0-or-later; `NOTICE.txt` is appended to the gpSP and mGBA
  licence files.
- gpSP: nx-redux `patches/gpsp/001` (RFU disconnect fix), `002` (RFU queue 4 → 16) and `004` (real link cable:
  `gpsp_serial` `lockstep`, and `auto_cable`, which the app sets for `auto` over the USB cable) after our `gpsp.patch`;
  their `003` is left out (our patch covers the JIT cache). `NX_SIOLS_DIR` is passed on the ndk-build line.
- mGBA: nx-redux `patches/mgba/001` as is (real link cable; a `CMakeLists.txt` hunk compiles `sio_lockstep` in through
  `-DNX_SIOLS_DIR`). The combined core is GPL-2.0-or-later (MPL-2.0 §3.3).
- mGBA: nx-redux `patches/mgba/003` (the `mgba_link_mode` option: gpSP's Wireless Adapter and Pokemon / Advance Wars
  cable protocols), built on `001` alone. It compiles `gba_wifi_link/` in through `-DNX_GWL_DIR`, the shared protocol
  library copied from nx-redux `workspace/all/cores/gba_wifi_link` with its host tests (`gmake test-gwl`, which check
  its gpSP files against `gpsp_HASH` plus gpSP `patches/gpsp/001` and `002`). Its `NOTICE.txt` is appended to the mGBA
  licence file. The app asks `gwl_mode.c` (compiled into libretrodroid) which mode a ROM uses.
- mGBA: `patches/mgba/004-pair-cable.patch`, phone only (nx-redux never carries it), for Cable Link over Wi-Fi: pair
  mode runs two GBAs in one core, joined by mGBA's own in-process link cable (`src/gba/sio/lockstep.c`, which the
  patch adds to the libretro build), so two phones compute the same pair from the same two input streams. Off unless
  the frontend sets these variables (none is declared as a core option, so Settings never lists them): `mgba_pair`
  (`ON`), `mgba_pair_local` (`1`/`2`: the GBA the content ROM, video, audio and save RAM belong to),
  `mgba_pair_other_rom` / `mgba_pair_other_save` (paths; that save is read, never written), `mgba_pair_rtc` (Unix
  seconds; both clocks read it plus emulated time, as UTC) and `mgba_pair_bios` (`real`: `gba_bios.bin` from the system
  directory on both, and the load fails without it; anything else: mGBA's HLE BIOS). Port 0 drives GBA 1, port 1 GBA 2.
  The GBAs take turns on the frame thread, the one behind on the link's clock first. Each phone's Settings never reach
  them: skip BIOS is on, idle loop removal, opposite directions, the Game Boy Player, turbo, cheats and the sensors are
  off for both. A
  state holds both GBAs with their saves (one size for the session), and the exported `nx_pair_hash()` hashes their
  CPU, memory, I/O registers and saves for the drift check. Outside pair mode the core is unchanged.
  Host checks: `gmake test-pair PAIR_ROM_A=<gba> PAIR_ROM_B=<gba> [PAIR_BIOS=<gba_bios.bin>] [PAIR_TRANSFERS=1]` builds
  the core for the host with and without `004` (`pair_harness/run_tests.sh`, into `build/pair_harness`) and checks that
  two runs, a state reload, both phones' views (`mgba_pair_local` 1 and 2), each BIOS choice, opposite Settings and a
  reset in another time zone give the same hashes, and that pair mode off matches the core without `004`; without the ROMs it skips with a notice. PAIR_ROM_A also runs
  alone, so pick one without a cartridge clock. `pair_harness` plays a fixed input stream that takes Mario Kart: Super
  Circuit into a 2-player game.
- gambatte: `HAVE_NETWORK` is on upstream (`jni/Android.mk`); nx-redux `patches/gambatte/002` (async slave reply) minus
  its `Makefile.common` hunk (`-lpthread`; bionic has pthreads in libc).
