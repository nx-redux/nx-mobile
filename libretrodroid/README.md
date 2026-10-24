# libretrodroid (nx-mobile's runner)
The native libretro runner for nx-mobile: C++ frontend, JNI and the Kotlin `GLRetroView` API. The app reaches it only
through `EmulatorRunner` in `shared/`.

## Credits
This module is a fork of [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) by Filippo Scognamiglio
([@Swordfish90](https://github.com/Swordfish90)), the C++ libretro frontend library that powers
[Lemuroid](https://github.com/Swordfish90/Lemuroid). The runner core, its renderers, audio, input, save states and
the Kotlin `GLRetroView` API are his work; nx-mobile builds on top of them.

The fork started at upstream commit `8835c30` (after release `0.14.0`) and does not track upstream after that. It
now lives only in this repository. Upstream copyright headers are kept unchanged in the source. Like LibretroDroid, it is licensed under the GPL-3.0.

Third-party code under `src/main/cpp/` (git submodules, plus [LZ4](https://github.com/lz4/lz4) v1.10.0 (BSD-2-Clause) vendored unmodified in `lz4/`): [Oboe](https://github.com/google/oboe) (Apache-2.0),
[libretro-common](https://github.com/libretro/libretro-common) (MIT), [rcheevos](https://github.com/RetroAchievements/rcheevos)
(MIT), [libchdr](https://github.com/rtissera/libchdr) (BSD-3-Clause).

## Inherited from LibretroDroid
* 2D and GL cores
* Audio
* Gamepad events
* Save states and SaveRAM serialization
* Simple shader effects (CRT and LCD)
* Touchscreen
* Multiple disk support
* Core variables

## Added in nx-mobile
* **RetroAchievements (rcheevos 12.2.1, softcore).** `achievementsEnable(true)` before `create()` starts an rc_client.
  The runner reads core memory (`SET_MEMORY_MAPS` or `retro_get_memory_data`), calls `rc_client_do_frame` after each
  `retro_run`, and hands every HTTP request to the host through `GLRetroView.getAchievementServerCalls()`; the host
  answers with `achievementsServerResponse`. Events arrive on `getAchievementEvents()`, with the achievement list,
  summary and signed award request exposed to the app. `LibretroAchievements.hash` identifies a game from fds
  (cue/bin, m3u, CHD via libchdr) without a core loaded.
* **Core options v1/v2** with rich metadata and category info, and option visibility refresh.
* **Display:** scale modes (fit, integer, stretch), a screen offset and the game geometry; nearest or linear sampling
  on the default shader; the last frame is redrawn while paused; display settings set before `create()` are kept.
* **RetroArch GLSL preset chains** (`.glslp`), with shader chain and parameter setters, error reporting and the core's
  texture bindings kept around the chain.
* **Screen regions** for multi-screen layouts such as the NDS: draw chosen regions of the core frame, blended by
  alpha and sampled linearly when downscaled, with touches mapped back through the regions and `sendPointer`.
* **Input:** analog L2/R2 trigger values for cores that read `ANALOG_BUTTON`, and controller port devices set after
  load and before the first frame (`controllerPorts`).
* **Hardware-rendered cores:** hardware contexts the GLES view cannot serve are refused, `isHardwareRendered()`
  reports whether the core renders on the GPU, and a GL core's state is neutralised and restored around every frame
  draw.
* **GL frame sizing and timing** (for Dreamcast and similar GL cores): the framebuffer is sized to the largest frame
  the core delivers, clamped to the GPU limit, and `retro_run` is paced at the frame rate a core reports through
  `SET_SYSTEM_AV_INFO`, handed to the GL thread through atomics.
* **VFS v3 and v4** (stat, mkdir, directory listing, 64-bit stat), honouring the version the core requests; fdopen'd
  descriptors are closed once.
* **GL debug output** (opt-in `glDebugOutput`, debug builds): driver messages to the `NXGL` log tag through
  `KHR_debug`, with a `glGetError` fallback and a frame/audio probe.
* **Robustness:** the whole environment resets between cores, a core whose create or load failed is deinitialised
  before it is closed, the view fails fast instead of waiting on a detached or wedged emulation thread, serialized
  state and SRAM buffers are freed, and a zero core aspect ratio falls back to base width over height.
* **Netplay:** lockstep engine (`cpp/netplay/`, ported from NX Redux; protocol 3: 10-byte input with sticks, no PAUSE sent) hooked into `step()`/input; `LibretroNetplay` JNI.
* **Build:** compile SDK 37 (the app sets `targetSdk`).

## Tests
- Kotlin: `./gradlew :libretrodroid:testDebugUnitTest`
- Native host checks (no device): each `src/test/cpp/*_test.cpp` has its build-and-run command in its header comment;
  run it from the repo root.
