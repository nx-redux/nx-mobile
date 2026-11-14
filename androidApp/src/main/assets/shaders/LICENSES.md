# Bundled shaders: origins

Every file keeps its own license header; the terms in that header apply. This list only records where each
file came from. Nothing here is modified from its source unless noted.

## nx-redux

From nx-redux `skeleton/BASE/Shaders/glsl/` (commit 0b2cb7ff), unmodified unless noted. Several are themselves
glsl-shaders ports; `crt-perfect`, `dmg-perfect`, `lcd-perfect` and `pixel-perfect` are MIT (sinedied):

- `glsl/barrel-distortion.glsl`
- `glsl/crt-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/dmg-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/fast-sharpen.glsl`
- `glsl/lcd-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/pixel-perfect.glsl` (modified: vertex-stage COMPAT_PRECISION highp)
- `glsl/pixel_art_AA.glsl`
- `glsl/sharp-shimmerless-grid.glsl`
- `glsl/sharp-shimmerless-subpixel-vrgb.glsl`
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

- `slang/anti-aliasing/aa-shader-4.0.slangp`
- `slang/anti-aliasing/shaders/aa-shader-4.0.slang`
- `slang/blurs/shaders/royale/blur5fast-horizontal.slang`
- `slang/blurs/shaders/royale/blur5fast-vertical.slang`
- `slang/blurs/shaders/royale/blur9fast-horizontal.slang`
- `slang/blurs/shaders/royale/blur9fast-vertical.slang`
- `slang/blurs/shaders/royale/vertex-shader-blur-fast-horizontal.h`
- `slang/blurs/shaders/royale/vertex-shader-blur-fast-vertical.h`
- `slang/crt/crt-aperture.slangp`
- `slang/crt/crt-consumer.slangp`
- `slang/crt/crt-easymode.slangp`
- `slang/crt/crt-gdv-mini.slangp`
- `slang/crt/crt-geom.slangp`
- `slang/crt/crt-guest-advanced-fast.slangp`
- `slang/crt/crt-guest-advanced-hd.slangp`
- `slang/crt/crt-guest-advanced-ntsc.slangp`
- `slang/crt/crt-guest-advanced.slangp`
- `slang/crt/crt-hyllian.slangp`
- `slang/crt/crt-lottes.slangp`
- `slang/crt/crt-mattias.slangp`
- `slang/crt/crt-pi.slangp`
- `slang/crt/crt-royale.slangp`
- `slang/crt/newpixie-crt.slangp`
- `slang/crt/shaders/crt-aperture.slang`
- `slang/crt/shaders/crt-consumer.slang`
- `slang/crt/shaders/crt-easymode.slang`
- `slang/crt/shaders/crt-gdv-mini.slang`
- `slang/crt/shaders/crt-geom.slang`
- `slang/crt/shaders/crt-lottes.slang`
- `slang/crt/shaders/crt-mattias.slang`
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
- `slang/crt/shaders/hyllian/crt-hyllian-base.slang`
- `slang/crt/shaders/hyllian/support/LUT/Sony_Wega_29FA310_no_gamma-v2.png`
- `slang/crt/shaders/hyllian/support/LUT/Sony_Wega_29FA310_no_gamma.png`
- `slang/crt/shaders/hyllian/support/glow/blur-glow-mask-geom.slang`
- `slang/crt/shaders/hyllian/support/glow/blur_horiz.slang`
- `slang/crt/shaders/hyllian/support/glow/threshold.slang`
- `slang/crt/shaders/hyllian/support/multiLUT-linear-fast.slang`
- `slang/crt/shaders/newpixie/accumulate.slang`
- `slang/crt/shaders/newpixie/blur_horiz.slang`
- `slang/crt/shaders/newpixie/blur_vert.slang`
- `slang/crt/shaders/newpixie/crtframe.png`
- `slang/crt/shaders/newpixie/newpixie-crt.slang`
- `slang/crt/shaders/zfast_crt/zfast_crt_finemask.slang`
- `slang/crt/shaders/zfast_crt/zfast_crt_impl.inc`
- `slang/crt/zfast-crt.slangp`
- `slang/dithering/gdapt.slangp`
- `slang/dithering/mdapt.slangp`
- `slang/dithering/shaders/gdapt/gdapt-pass0.slang`
- `slang/dithering/shaders/gdapt/gdapt-pass1.slang`
- `slang/dithering/shaders/mdapt/passes/mdapt-pass0.slang`
- `slang/dithering/shaders/mdapt/passes/mdapt-pass1.slang`
- `slang/dithering/shaders/mdapt/passes/mdapt-pass2.slang`
- `slang/dithering/shaders/mdapt/passes/mdapt-pass3.slang`
- `slang/dithering/shaders/mdapt/passes/mdapt-pass4.slang`
- `slang/edge-smoothing/scalefx/scalefx.slangp`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass0.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass1.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass2.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass3.slang`
- `slang/edge-smoothing/scalefx/shaders/scalefx-pass4.slang`
- `slang/edge-smoothing/scalenx/scale3x.slangp`
- `slang/edge-smoothing/scalenx/shaders/scale3x.slang`
- `slang/edge-smoothing/xbrz/shaders/xbrz-freescale.slang`
- `slang/edge-smoothing/xbrz/xbrz-freescale.slangp`
- `slang/handheld/agb001.slangp`
- `slang/handheld/authentic_gbc.slangp`
- `slang/handheld/dot.slangp`
- `slang/handheld/gameboy-advance-dot-matrix.slangp`
- `slang/handheld/gameboy.slangp`
- `slang/handheld/lcd-grid-v2.slangp`
- `slang/handheld/lcd1x.slangp`
- `slang/handheld/lcd3x.slangp`
- `slang/handheld/retro-v2.slangp`
- `slang/handheld/sameboy-lcd.slangp`
- `slang/handheld/shaders/authentic_gbc/authentic_gbc.slang`
- `slang/handheld/shaders/authentic_gbc/parameters.inc`
- `slang/handheld/shaders/authentic_gbc/shared.inc`
- `slang/handheld/shaders/authentic_gbc/to_lin.slang`
- `slang/handheld/shaders/dot.slang`
- `slang/handheld/shaders/gameboy/resources/background.png`
- `slang/handheld/shaders/gameboy/resources/palette.png`
- `slang/handheld/shaders/gameboy/resources/sample-bgs/paper-bg.png`
- `slang/handheld/shaders/gameboy/resources/sample-palettes/b-w-palette.png`
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
- `slang/handheld/shaders/lcd1x.slang`
- `slang/handheld/shaders/lcd3x.slang`
- `slang/handheld/shaders/mgba/agb001.slang`
- `slang/handheld/shaders/retro-v2.slang`
- `slang/handheld/shaders/sameboy-lcd.slang`
- `slang/handheld/shaders/zfast_lcd.slang`
- `slang/handheld/zfast-lcd.slangp`
- `slang/include/blur-functions.h`
- `slang/include/colorspace-tools.h`
- `slang/include/compat_macros.inc`
- `slang/include/gamma-management.h`
- `slang/include/quad-pixel-communication.h`
- `slang/include/special-functions.h`
- `slang/include/subpixel_masks.h`
- `slang/interpolation/bicubic.slangp`
- `slang/interpolation/jinc2.slangp`
- `slang/interpolation/shaders/bicubic.slang`
- `slang/interpolation/shaders/jinc2.slang`
- `slang/misc/image-adjustment.slangp`
- `slang/misc/night-mode.slangp`
- `slang/misc/shaders/coverage/coverage.inc`
- `slang/misc/shaders/grade.slang`
- `slang/misc/shaders/image-adjustment.slang`
- `slang/misc/shaders/night_mode.slang`
- `slang/motionblur/mix_frames.slangp`
- `slang/motionblur/response-time.slangp`
- `slang/motionblur/shaders/mix_frames.slang`
- `slang/motionblur/shaders/response-time.slang`
- `slang/ntsc/ntsc-256px-svideo.slangp`
- `slang/ntsc/ntsc-adaptive.slangp`
- `slang/ntsc/shaders/maister/ntsc-decode-filter-3phase.inc`
- `slang/ntsc/shaders/maister/ntsc-param.inc`
- `slang/ntsc/shaders/maister/ntsc-pass1-composite-3phase.slang`
- `slang/ntsc/shaders/maister/ntsc-pass1-encode-demodulate.inc`
- `slang/ntsc/shaders/maister/ntsc-pass1-svideo-3phase.slang`
- `slang/ntsc/shaders/maister/ntsc-pass1-vertex.inc`
- `slang/ntsc/shaders/maister/ntsc-pass2-3phase-gamma.slang`
- `slang/ntsc/shaders/maister/ntsc-pass2-3phase.slang`
- `slang/ntsc/shaders/maister/ntsc-pass2-decode.inc`
- `slang/ntsc/shaders/maister/ntsc-pass2-vertex.inc`
- `slang/ntsc/shaders/maister/ntsc-rgbyuv.inc`
- `slang/ntsc/shaders/maister/ntsc-stock.slang`
- `slang/pixel-art-scaling/edge1pixel.slangp`
- `slang/pixel-art-scaling/pixellate.slangp`
- `slang/pixel-art-scaling/shaders/edge1pixel.slang`
- `slang/pixel-art-scaling/shaders/pixellate.slang`
- `slang/pixel-art-scaling/shaders/sharp-bilinear.slang`
- `slang/pixel-art-scaling/shaders/sharp-shimmerless.slang`
- `slang/pixel-art-scaling/sharp-bilinear.slangp`
- `slang/pixel-art-scaling/sharp-shimmerless.slangp`
- `slang/presets/crt-royale-kurozumi.slangp`
- `slang/scanlines/res-independent-scanlines.slangp`
- `slang/scanlines/scanline.slangp`
- `slang/scanlines/shaders/res-independent-scanlines.slang`
- `slang/scanlines/shaders/scanline.slang`
- `slang/sharpen/shaders/adaptive-sharpen.slang`
- `slang/stock.slang`
- `slang/vhs/ntsc-vcr.slangp`
- `slang/vhs/resources/play.png`
- `slang/vhs/resources/rew.png`
- `slang/vhs/shaders/rewind.slang`
- `slang/vhs/shaders/static.slang`
- `slang/vhs/shaders/vhs.slang`
- `slang/vhs/vhs.slangp`
