# Bundled shaders: origins

Every file keeps its own license header; the terms in that header apply. This list only records where each
file came from. Nothing here is modified from its source unless noted.

## libretro glsl-shaders

From https://github.com/libretro/glsl-shaders (master, commit f8e23ff880668f0f0e837a05a316534d82a7f31b),
copied with the upstream relative layout:

- `crt/crt-easymode.glslp`
- `crt/crt-geom.glslp`
- `crt/crt-pi.glslp`
- `crt/shaders/crt-easymode.glsl`
- `crt/shaders/crt-geom.glsl`
- `crt/shaders/crt-pi.glsl`
- `crt/shaders/zfast_crt.glsl`
- `crt/zfast-crt.glslp`
- `handheld/dot.glslp`
- `handheld/gameboy.glslp`
- `handheld/lcd3x.glslp`
- `handheld/shaders/dot.glsl`
- `handheld/shaders/gameboy/resources/background.png`
- `handheld/shaders/gameboy/resources/palette.png`
- `handheld/shaders/gameboy/shader-files/gb-pass0.glsl`
- `handheld/shaders/gameboy/shader-files/gb-pass1.glsl`
- `handheld/shaders/gameboy/shader-files/gb-pass2.glsl`
- `handheld/shaders/gameboy/shader-files/gb-pass3.glsl`
- `handheld/shaders/gameboy/shader-files/gb-pass4.glsl`
- `handheld/shaders/lcd3x.glsl`
- `interpolation/shaders/sharp-bilinear.glsl`
- `interpolation/sharp-bilinear.glslp`
- `xbrz/shaders/xbrz-freescale.glsl`
- `xbrz/xbrz-freescale.glslp`

`handheld/shaders/gameboy/` carries only the five `shader-files/gb-pass*.glsl` passes and the default
`resources/palette.png` and `resources/background.png` (upstream's sample palettes/backgrounds and README are
not bundled).

## nx-redux

From nx-redux `skeleton/BASE/Shaders/glsl/` (commit 0b2cb7ff), unmodified unless noted. Several are themselves
glsl-shaders ports; `crt-perfect`, `dmg-perfect`, `lcd-perfect` and `pixel-perfect` are MIT (sinedied):

- `glsl/aa-shader-4.0.glsl`
- `glsl/barrel-distortion.glsl`
- `glsl/bicubic.glsl`
- `glsl/crt-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/dmg-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/edge1pixel.glsl`
- `glsl/fast-sharpen.glsl`
- `glsl/lcd-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/lcd1x.glsl`
- `glsl/lcd3x.glsl`
- `glsl/pixel-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/pixel_art_AA.glsl`
- `glsl/pixellate.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/res-independent-scanlines.glsl`
- `glsl/retro-v2.glsl`
- `glsl/scale3x.glsl`
- `glsl/scanline.glsl`
- `glsl/sharp-bilinear.glsl`
- `glsl/sharp-shimmerless-grid.glsl`
- `glsl/sharp-shimmerless-subpixel-vrgb.glsl`
- `glsl/sharp-shimmerless.glsl`
- `glsl/stock.glsl`
- `glsl/waterpaint.glsl`

## perfect-retroshaders

From https://github.com/sinedied/perfect-retroshaders (MIT, sinedied; main, commit
4bb6af9d61ee0ce77b23d674f96e896bd1d60392), the GLSL versions from `shaders/`. The nx-redux copies of
`crt-perfect` (v14), `dmg-perfect` (v11), `lcd-perfect` (v10) and `pixel-perfect` (v8) above are identical to this
commit's and are kept as they are.

- `glsl/colour-mini.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/crt-mini.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/dmg-mini.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/lcd-mini.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/unflat-mini.glsl` (modified: vertex-stage COMPAT_PRECISION highp)

## NX Redux

Ports of LibretroDroid's built-in shaders (GPL-3.0, Filippo Scognamiglio; from the `libretrodroid` module's
`shadermanager.cpp`) to RetroArch's single-file GLSL form:

- `nx/line.glsl`, `nx/line.glslp`: the CRT shader ("Line")
- `nx/grid.glsl`, `nx/grid.glslp`: the LCD shader ("Grid")

## libretro slang-shaders

From https://github.com/libretro/slang-shaders (master, commit 2345ee91602a7404d3a1a6a5df6108d7305fdad4),
copied under `slang/` with the upstream relative layout:

- `slang/blurs/shaders/royale/blur9fast-horizontal.slang`
- `slang/blurs/shaders/royale/blur9fast-vertical.slang`
- `slang/blurs/shaders/royale/vertex-shader-blur-fast-horizontal.h`
- `slang/blurs/shaders/royale/vertex-shader-blur-fast-vertical.h`
- `slang/crt/crt-easymode.slangp`
- `slang/crt/crt-geom.slangp`
- `slang/crt/crt-guest-advanced-fast.slangp`
- `slang/crt/crt-guest-advanced-hd.slangp`
- `slang/crt/crt-guest-advanced-ntsc.slangp`
- `slang/crt/crt-guest-advanced.slangp`
- `slang/crt/crt-lottes.slangp`
- `slang/crt/crt-pi.slangp`
- `slang/crt/crt-royale.slangp`
- `slang/crt/newpixie-crt.slangp`
- `slang/crt/shaders/crt-easymode.slang`
- `slang/crt/shaders/crt-geom.slang`
- `slang/crt/shaders/crt-lottes.slang`
- `slang/crt/shaders/crt-pi.slang`
- `slang/crt/shaders/crt-royale/TileableLinearApertureGrille15Wide8And5d5Spacing.png`
- `slang/crt/shaders/crt-royale/TileableLinearApertureGrille15Wide8And5d5SpacingResizeTo64.png`
- `slang/crt/shaders/crt-royale/TileableLinearShadowMaskEDP.png`
- `slang/crt/shaders/crt-royale/TileableLinearShadowMaskEDPResizeTo64.png`
- `slang/crt/shaders/crt-royale/TileableLinearSlotMaskTall15Wide9And4d5Horizontal9d14VerticalSpacing.png`
- `slang/crt/shaders/crt-royale/TileableLinearSlotMaskTall15Wide9And4d5Horizontal9d14VerticalSpacingResizeTo64.png`
- `slang/crt/shaders/crt-royale/src/bind-shader-params.h`
- `slang/crt/shaders/crt-royale/src/bloom-functions.h`
- `slang/crt/shaders/crt-royale/src/crt-royale-bloom-approx.h`
- `slang/crt/shaders/crt-royale/src/crt-royale-bloom-approx.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-bloom-horizontal-reconstitute.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-bloom-vertical.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-brightpass.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-first-pass-linearize-crt-gamma-bob-fields.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-geometry-aa-last-pass.h`
- `slang/crt/shaders/crt-royale/src/crt-royale-geometry-aa-last-pass.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-mask-resize-horizontal.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-mask-resize-vertical.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-scanlines-horizontal-apply-mask.h`
- `slang/crt/shaders/crt-royale/src/crt-royale-scanlines-horizontal-apply-mask.slang`
- `slang/crt/shaders/crt-royale/src/crt-royale-scanlines-vertical-interlacing.slang`
- `slang/crt/shaders/crt-royale/src/derived-settings-and-constants.h`
- `slang/crt/shaders/crt-royale/src/geometry-functions.h`
- `slang/crt/shaders/crt-royale/src/phosphor-mask-resizing.h`
- `slang/crt/shaders/crt-royale/src/scanline-functions.h`
- `slang/crt/shaders/crt-royale/src/tex2Dantialias.h`
- `slang/crt/shaders/crt-royale/src/user-cgp-constants.h`
- `slang/crt/shaders/crt-royale/user-settings.h`
- `slang/crt/shaders/guest/advanced/afterglow0.slang`
- `slang/crt/shaders/guest/advanced/avg-lum-ntsc.slang`
- `slang/crt/shaders/guest/advanced/avg-lum.slang`
- `slang/crt/shaders/guest/advanced/bloom_horizontal.slang`
- `slang/crt/shaders/guest/advanced/bloom_vertical.slang`
- `slang/crt/shaders/guest/advanced/crt-guest-advanced-ntsc-pass1.slang`
- `slang/crt/shaders/guest/advanced/crt-guest-advanced-ntsc-pass2.slang`
- `slang/crt/shaders/guest/advanced/crt-guest-advanced.slang`
- `slang/crt/shaders/guest/advanced/custom-fast-sharpen.slang`
- `slang/crt/shaders/guest/advanced/deconvergence-ntsc.slang`
- `slang/crt/shaders/guest/advanced/deconvergence.slang`
- `slang/crt/shaders/guest/advanced/gaussian_horizontal.slang`
- `slang/crt/shaders/guest/advanced/gaussian_vertical.slang`
- `slang/crt/shaders/guest/advanced/interlace-ntsc.slang`
- `slang/crt/shaders/guest/advanced/linearize-ntsc-new.slang`
- `slang/crt/shaders/guest/advanced/linearize.slang`
- `slang/crt/shaders/guest/advanced/lut/inv-trinitron-lut.png`
- `slang/crt/shaders/guest/advanced/lut/nec-lut.png`
- `slang/crt/shaders/guest/advanced/lut/ntsc-lut.png`
- `slang/crt/shaders/guest/advanced/lut/trinitron-lut.png`
- `slang/crt/shaders/guest/advanced/ntsc/ntsc-pass1.slang`
- `slang/crt/shaders/guest/advanced/ntsc/ntsc-pass2.slang`
- `slang/crt/shaders/guest/advanced/ntsc/ntsc-pass3.slang`
- `slang/crt/shaders/guest/advanced/pre-shaders-afterglow.slang`
- `slang/crt/shaders/guest/advanced/stock.slang`
- `slang/crt/shaders/guest/fast/bloom_horizontal.slang`
- `slang/crt/shaders/guest/fast/bloom_vertical.slang`
- `slang/crt/shaders/guest/fast/crt-guest-advanced-pass1.slang`
- `slang/crt/shaders/guest/fast/crt-guest-advanced-pass2.slang`
- `slang/crt/shaders/guest/fast/deconvergence-f.slang`
- `slang/crt/shaders/guest/fast/linearize.slang`
- `slang/crt/shaders/guest/fast/pre-shaders.slang`
- `slang/crt/shaders/guest/fast/stock.slang`
- `slang/crt/shaders/guest/hd/afterglow0.slang`
- `slang/crt/shaders/guest/hd/bloom_horizontal.slang`
- `slang/crt/shaders/guest/hd/bloom_vertical.slang`
- `slang/crt/shaders/guest/hd/crt-guest-advanced-hd-pass1.slang`
- `slang/crt/shaders/guest/hd/crt-guest-advanced-hd-pass2.slang`
- `slang/crt/shaders/guest/hd/deconvergence-hd.slang`
- `slang/crt/shaders/guest/hd/gaussian_horizontal.slang`
- `slang/crt/shaders/guest/hd/gaussian_vertical.slang`
- `slang/crt/shaders/guest/hd/linearize-hd.slang`
- `slang/crt/shaders/guest/hd/pre-shaders-afterglow.slang`
- `slang/crt/shaders/guest/hd/stock.slang`
- `slang/crt/shaders/newpixie/accumulate.slang`
- `slang/crt/shaders/newpixie/blur_horiz.slang`
- `slang/crt/shaders/newpixie/blur_vert.slang`
- `slang/crt/shaders/newpixie/crtframe.png`
- `slang/crt/shaders/newpixie/newpixie-crt.slang`
- `slang/crt/shaders/zfast_crt/zfast_crt_finemask.slang`
- `slang/crt/shaders/zfast_crt/zfast_crt_impl.inc`
- `slang/crt/zfast-crt.slangp`
- `slang/edge-smoothing/scalefx/scalefx.slangp`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass0.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass1.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass2.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass3.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass4.slang`
- `slang/handheld/dot.slangp`
- `slang/handheld/gameboy.slangp`
- `slang/handheld/lcd-grid-v2.slangp`
- `slang/handheld/shaders/dot.slang`
- `slang/handheld/shaders/gameboy/resources/background.png`
- `slang/handheld/shaders/gameboy/resources/palette.png`
- `slang/handheld/shaders/gameboy/shader-files/gb-0-prep.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-1-match.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-2-dot.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-3-blend.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-4-shadow-horizontal.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-5-shadow-vertical.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-6-composite.slang`
- `slang/handheld/shaders/gameboy/shader-files/gb-params.inc`
- `slang/handheld/shaders/gameboy/shader-files/gb-shared.inc`
- `slang/handheld/shaders/lcd-cgwg/lcd-grid-v2.slang`
- `slang/include/blur-functions.h`
- `slang/include/compat_macros.inc`
- `slang/include/gamma-management.h`
- `slang/include/quad-pixel-communication.h`
- `slang/include/special-functions.h`
- `slang/ntsc/ntsc-adaptive.slangp`
- `slang/stock.slang`
