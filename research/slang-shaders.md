# Slang shader support — exploration (2026-09-24) and what was built (2026-11-14)

Status: **built** on `feat/slang-shaders` (see "What was built" at the end). The exploration below is kept as it was
written, when the in-game Shaders page ran only RetroArch legacy `.glslp`/`.glsl` presets (the `libretrodroid` module's
`presetchain.cpp`, app `GlslPreset`/`ShaderOptions`). Sources: RetroArch `98a05c5` (`gfx/drivers_shader/slang_process.c`,
`glslang_util.c`, `glslang.cpp`, `shader_gl3.c`, `video_shader_parse.c`), libretro `slang-shaders`
`afb1416`, `glsl-shaders` `f8e23ff`, glslang `ca70aa3`, SPIRV-Cross `aa217ae`.

## Why it would matter

- `slang-shaders` is the maintained library: 2658 presets (1164 outside the bezel packs) and 1652
  `.slang` files, versus 620/684 in `glsl-shaders`. About 700 non-bezel presets are slang-only.
- The current-generation shaders people recommend exist only in slang: `crt-guest-advanced` (+ hd /
  ntsc / fast), `newpixie-crt`, `koko-aio`, Mega Bezel, `crt-sony-megatron` (HDR), `ntsc-adaptive`,
  `scalefx`.
- Drop-in compatibility: a `.slangp` folder copied from RetroArch would work, parameters included.
- Most of the value is in renderer features those presets need (frame history, feedback, float/sRGB
  framebuffers, mipmaps, many passes), not in the format itself. The classic set the app already
  bundles (`crt-pi`, `zfast-crt`, `crt-easymode`, `crt-geom`, `lcd3x`, `dot`, `gameboy`,
  `sharp-bilinear`, `xbrz-freescale`, `perfect-retroshaders`) covers the handheld-first use case.

## The format

- **Preset (`.slangp`)**: same parser as `.glslp` (`video_shader_read_preset`). Per pass: `shaderN`,
  `filter_linearN`, `wrap_modeN`, `frame_count_modN`, `scale_type[_x|_y]N`, `scale[_x|_y]N`,
  `srgb_framebufferN`, `float_framebufferN`, `mipmap_inputN`, `aliasN`, `rgb10_framebufferN`
  (slang only). LUTs via `textures = a;b` + `a = path`, `a_linear`, `a_mipmap`, `a_wrap_mode`.
  Parameter overrides `NAME = value`. `#reference "other.slangp"` (nested, "simple presets") is
  used by 695 presets (50 outside `bezel/`). Legacy `feedback_pass` is ignored; feedback comes from
  the `PassFeedbackN` / `<alias>Feedback` semantics.
- **Shader (`.slang`)**: first line must be `#version 450`. `#include` / `#pragma include_optional`
  are expanded textually (recursively, ignoring `#ifdef`). `#pragma stage vertex|fragment` splits
  the stages; text before the first pragma is shared. `#pragma name X` = pass alias. `#pragma
  format FMT` (30 Vulkan-style names; the preset's `srgb/float/rgb10_framebuffer` override it).
  `#pragma parameter ID "desc" init min max [step]` as in legacy GLSL.
- **Resources**: vertex inputs at locations 0 (`Position`) and 1 (`TexCoord`); one fragment output at
  location 0; at most one `std140` UBO per stage (same binding in both stages) and one push-constant
  block (≤ 128 B); everything in `set = 0`, bindings `< 16` and unique. Every block member must be a
  known semantic or a `#pragma parameter` id, or the load fails.
- **Semantics**: textures `Original`, `Source`, `OriginalHistoryN`, `PassOutputN`, `PassFeedbackN`,
  LUTs by id, `<alias>`, `<alias>Feedback`; sizes as `vec4(w, h, 1/w, 1/h)` — `OriginalSize`,
  `SourceSize`, `OriginalHistorySizeN`, `PassOutputSizeN`, `PassFeedbackSizeN`, `<alias>Size`,
  `<lut>Size`; scalars `MVP` (mat4), `OutputSize`, `FinalViewportSize` (vec4), `FrameCount` (uint),
  `FrameDirection` (int); newer: `FrameTimeDelta`, `OriginalFPS`, `Rotation`, `OriginalAspect`,
  `OriginalAspectRotated`, `TotalSubFrames`, `CurrentSubFrame`, HDR (`HDRMode`, `BrightnessNits`, …),
  sensors (`Gyroscope`, `Accelerometer`, `AccelerometerRest`), `SwapCount`.

## How RetroArch runs it on GLES (the `gl3` driver)

1. **glslang** parses each stage with `EShMsgVulkanRules | EShMsgSpvRules` (default version 100,
   includes already expanded) and emits SPIR-V 1.0.
2. **Reflection** (SPIRV-Cross): `get_active_buffer_ranges` on the UBO and the push block; each
   member name → semantic/parameter with a type check; offsets and block sizes recorded; sampler
   bindings become texture units.
3. **SPIRV-Cross → GLSL ES**: `es = true`, target version = `100*major + 10*minor` of `GL_VERSION`
   (**320 on an ES 3.2 phone**, fallback 300), highp default precision, `vulkan_semantics = false`.
   Renames before compile: vertex inputs `RARCH_ATTRIBUTE_<loc>` (+ `glBindAttribLocation`),
   varyings `RARCH_VARYING_<loc>`, UBO `RARCH_UBO_{VERTEX,FRAGMENT}` (+ `_INSTANCE`), push block
   `RARCH_PUSH_{VERTEX,FRAGMENT}`, samplers `RARCH_TEXTURE_<binding>` (then `glUniform1i`).
   Push constants come out as a plain `uniform struct`; the UBO stays a real `layout(std140)`
   block (`flatten = false`; SPIRV-Cross can also flatten it to plain uniforms).
4. **Per frame**: push members via `glGetUniformLocation("RARCH_PUSH_FRAGMENT_INSTANCE.X")` +
   `glUniform*`; UBO members written into a CPU std140 buffer at the reflected offsets, uploaded
   with `glBufferSubData` into a ring of UBOs, bound with `glBindBufferBase`. (RetroArch never
   calls `glUniformBlockBinding`; it works only because blocks default to binding 0 — do it
   explicitly.)
5. `#pragma format` → GL internal format (`RGBA8`, `SRGB8_ALPHA8`, `RGBA16F`, `RGBA32F`,
   `RGB10_A2`, integer variants); `CLAMP_TO_BORDER` → edge on GLES. The last pass has no FBO
   unless scaled (its `#pragma format` is ignored).
6. A disk SPIR-V cache (`slang_cache.c`, keyed by a hash of the sources) avoids recompiling.

## Dependencies (measured)

- glslang 59 MB / SPIRV-Cross 29 MB as depth-1 clones (~135k + ~33k lines of source).
- Probe `.so` linking glslang + `GlslangToSpv` + `spirv-cross-glsl` (NDK 26.1, CMake 3.22.1,
  arm64-v8a, android-26, `ENABLE_OPT=OFF`, `ENABLE_HLSL=OFF`, HLSL/MSL/CPP/reflect/util/C-API off):
  **clean build ~13 s**; `Release` 5.3 MB stripped; `MinSizeRel` + `--gc-sections` +
  `--exclude-libs,ALL` + hidden visibility **3.2 MB stripped ≈ 1.1 MB compressed** (the APK cost).
- glslang needs C++17 and CMake ≥ 3.22.1; SPIRV-Cross C++11. Both work with `add_subdirectory(...
  EXCLUDE_FROM_ALL)`. SPIRV-Tools is not needed with `ENABLE_OPT=OFF`. Include glslang as
  `<SPIRV/GlslangToSpv.h>` with the glslang root on the include path.
- The NDK's bundled `shaderc` (older glslang, no SPIRV-Cross, no prebuilt) is not useful; vendor both.
- Licences: glslang BSD-3, SPIRV-Cross Apache-2.0 (compatible with GPL-3.0). RetroArch's code is not
  taken: other projects are a source of ideas only, so the runner is written from the format's behaviour.
- Compile time on the Mac: `crt-guest-advanced` (12 passes, 24 stages) ≈ 2 s through the CLIs. Not
  measured on the phone; assume a few seconds for big presets → cache.

## Bulk translation of the whole library

Every `.slang` (1652 files; 8 include-only) through: stage split + include expansion → glslang →
spirv-cross → re-validated by glslang as ES.

| Target  | Pass rate    | Failures |
|---------|--------------|----------|
| ES 3.00 | 1578 (96 %)  | `textureGather` (needs 310, 21 files), arrays of arrays (24, incl. `crt-geom`), `packUnorm4x8`/`bitCount` (3) |
| ES 3.20 | 1628 (99 %)  | 14 upstream/RetroArch-specific bugs (e.g. undeclared `iDate`), 1 SPIRV-Cross failure, 1 ES-validate failure |

At 3.00, 157 of 1164 non-bezel presets break; at 3.20 only 3. **Target ES 3.20.**

## Feature use (2652 presets; non-bezel in brackets)

LUTs 1897 [419] · non-RGBA8 `#pragma format` 1711 [272] · > 8 passes 1699 [253] · > 16 passes 1193
[32] · feedback 1611 [139] · float formats/FBOs 1598/1160 [160/293] · `textureLod` 1556 [87] ·
`mipmap_input` 1536 [66] · `FinalViewportSize` 911 [45] · sRGB FBO 839 [177] · history 742 [184] ·
`#reference` 695 [50] · newer semantics 1564 [83]. Bezel packs: Mega Bezel 660, scanline-classic 602,
koko-aio 177, uborder 49 (1–51 passes).

| Preset | Passes | Needs beyond the current GLSL runner |
|---|---|---|
| `crt/crt-guest-advanced` (+hd, ntsc 19 passes) | 12 | feedback, history, float FBO, mipmap_input |
| `crt-guest-advanced-fast` | 9 | float FBO |
| `crt/crt-royale` | 12 | sRGB FBOs/formats, mipmap_input, LUT mipmaps |
| `crt/newpixie-crt` | 4 | feedback |
| `handheld/gameboy` | 5 | history |
| `edge-smoothing/scalefx`, `ntsc-adaptive` | 6 / 4 | float FBO |
| `MBZ__5__POTATO` / `MBZ__3__STD` / `MBZ__0__SMOOTH-ADV` | 10 / 30 / 48 | the full Mega Bezel set |
| `koko-aio-ng` | 17 | feedback, float, mipmaps, newer semantics |

Simple/single-pass, nothing special: `lcd-grid-v2`, `sharp-bilinear-simple`, `crt-easymode`,
`crt-lottes`, `crt-geom` (needs ≥ 3.10), `zfast-crt`, `crt-pi`, `crt-consumer`, `fake-crt-geom`,
`xbrz-freescale`, `sameboy-lcd`, `retro-v2`, `aann`.

## Options

**A — runtime translation in the fork (recommended if/when done).** Vendor glslang + SPIRV-Cross;
a slang preprocessor (includes, stage split, pragmas); compile + rename + reflection (port of
`gl3_cross_compile_program` and `slang_reflect`); a new `SlangChainRenderer` modelled on
`shader_gl3.c` (std140 UBO ring, push-struct uniforms, semantic filling, pass sizing, final
viewport, history ring, feedback double-buffering, per-pass formats, mipmaps, LUTs, parameters, no
pass cap); a compiled-GLSL/SPIR-V disk cache. Drop-in `.slangp`. **≈ 3–5k LOC, 2–3 weeks**, in
three slices: (a) toolchain + preprocessor + compile/reflection + single-pass presets with UBO/push
and LUTs; (b) multi-pass with formats, feedback, history, mipmaps; (c) cache, `#reference`,
Mega-Bezel-scale validation on the phone (thermals/fps unknown).

**B — offline conversion into the existing `.glslp` runner.** Mac-side tool (`glslang -V` +
`spirv-cross --es`), but the semantics differ (vec4 sizes vs `TextureSize`/`InputSize`/`OutputSize`,
UBO vs plain uniforms, `Position` vs `VertexCoord`, `PassOutputN` vs `PassN`), and history /
feedback / float-sRGB FBOs / mipmaps cannot be expressed: ≈ 577 of 1164 non-bezel presets (50 %)
convert, 0 % of bezel ones, no user drop-in. ≈ 1 week. Not worth it alone.

**C — A plus build-time pre-translation of bundled presets.** The slang → SPIR-V → GLSL ES text +
reflection step is device-independent for a fixed target (ES 3.20), so bundled presets can ship
already translated; only user-supplied `.slang` files need the on-device translator. The final GL
program compile must still happen on the device (program binaries are driver-specific and are
invalidated by driver updates), but that is the same cost the GLSL presets pay today and Android
drivers cache it. Caveat: a device with only ES 3.0/3.1 needs the runtime path or a 3.00 variant.

**librashader** (third-party Rust slang runtime with a C API, GL/GLES backends; a PPSSPP fork
reports it working on Android GLES 3.2 / Adreno) would replace most of A's work but adds a Rust +
cargo-ndk toolchain; licence and size not checked.

## Decision (2026-09-24)

Planned production device: Retroid Pocket Nova (Android 13, Snapdragon QCS8550 → Adreno 740-class, OpenGL ES 3.2), so the ES 3.20 target and the runtime path apply there.

Deferred in favour of the remaining in-game menu pages (Cheats, Controls, Shortcuts, Rewind /
fast-forward, Achievements). If picked up: option A with C's build-time pre-translation for the
bundled set; ES 3.20 target; ~1 MB APK growth and a cached first-load compile were accepted.

## What was built (2026-11-14)

Option A, written from the format's behaviour (no RetroArch code), on the existing OpenGL ES presentation. A Vulkan
presentation layer is a separate later project; the translate half below is API-neutral and carries over.

- **Preset parser** (`shared`, `SlangPreset`): `.slangp` with `#reference` chains (a path is relative to the file that
  set it), per-pass keys, LUTs, parameter overrides; `.slang` with `#include` / `#pragma include_optional` expanded, the
  two stages split, `#pragma name / format / parameter` read. 2,656 of the library's 2,662 presets parse (the other 6
  are parameter-only fragments meant to be referenced).
- **Translator** (`libretrodroid`, `slangtranslate`): glslang 16.6.0 → SPIR-V → SPIRV-Cross (vulkan-sdk-1.4.363.0) →
  GLSL ES at the device's version, plus the list of block members and samplers. Each stage gets its own block and
  push names and one varying name per location. 1,331 of the library's 1,333 distinct passes translate and validate
  at ES 3.1 and 3.2, 1,289 at ES 3.0 (`slangcheck`, host). No GL: runs off the GL thread, the result cached on disk per
  pass (`SlangCache`, keyed by the pass text, the ES version and the app version).
- **Renderer** (`libretrodroid`, `slangchain`): any number of passes; per-pass formats (float, sRGB, 10-bit, integer)
  with a fallback when a format is not renderable; mipmapped inputs; feedback (double-buffered); frame history (a ring of
  copies of the frame, which also normalises its orientation); sampler objects per binding (border clamp where GLES has
  it); one uniform buffer per stage; LUT mipmaps. State (history, feedback, outputs) is kept per screen region, so the
  two DS screens have their own. The presentation is unchanged: Vulkan cores' frames arrive through the bridge as before.
- **Bundled set**: 50 slang presets from libretro `slang-shaders` under `assets/shaders/slang` (`tools/bundle_slang.py`
  holds the list and rebuilds the folder and its origins list). The 21 bundled GLSL shaders that have a slang version
  were removed; a stored value naming one loads its slang preset (`ShaderOptions.REPLACED`). 18 shaders with no slang
  version stay GLSL.
- **Picker** (`ShaderBrowse`, the in-game menu's `ShaderBrowse` pages): the Shader row opens None, the bundled presets
  in groups by effect (CRT, Handheld, Smoothing, NTSC, Effects, each with a line saying what it does) under readable
  names, and Personal, the home's `Shaders/` browsed a folder at a time. A on a preset uses it and shows its preview
  strip, LEFT / RIGHT stepping through that level. The second screen's Shader row opens the same picker.
- **Not done**: rotation / HDR / sensor / sub-frame semantics (zero or neutral values), `FrameDirection` under rewind
  (always 1), a program-binary cache (the driver's own cache is relied on).
- **Cost measured**: the stripped arm64 runner grows from 4.2 MB to 6.4 MB (2.3 MB → 3.1 MB compressed) with both
  libraries built for size and unreached code dropped.
- **Checked**: on the emulator (GLES 3.1) and the Nova (GLES 3.2) with `tools/device-tests/slang_shaders.py`: a set of
  presets across SNES, Game Boy, a rotated arcade game, N64, GameCube, PSP, PS2, DS and 3DS; every bundled preset and
  every replaced GLSL name on SNES. Mega Bezel STD and SMOOTH-ADV draw at about 36 fps on the Nova. What is still owed
  is listed in `TODO_LIST.md`.
