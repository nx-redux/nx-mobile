package com.nxredux.mobile.runner

/**
 * Core options a core only reads at game start even though their labels don't say "restart"/"reload".
 * Derived from a source audit; paths are relative to `cores/src/`.
 */
internal object RestartOnlyOptions {
    // mupen64plus_next/libretro/libretro.c update_variables `if (startup)` block (lines 919-1544);
    // GLideN64's config is built once at context creation.
    val mupen64plusNext: Set<String> = listOf(
        "169screensize", "43screensize", "BackgroundMode", "BilinearMode", "CorrectTexrectCoords",
        "CountPerOp", "CountPerOpDenomPot", "DitheringPattern", "DitheringQuantization",
        "EnableCopyAuxToRDRAM", "EnableCopyColorFromRDRAM", "EnableCopyColorToRDRAM", "EnableCopyDepthToRDRAM",
        "EnableEnhancedHighResStorage", "EnableEnhancedTextureStorage", "EnableFBEmulation",
        "EnableFragmentDepthWrite", "EnableHWLighting", "EnableHiResAltCRC", "EnableInaccurateTextureCoordinates",
        "EnableLODEmulation", "EnableLegacyBlending", "EnableN64DepthCompare", "EnableNativeResFactor",
        "EnableNativeResTexrects", "EnableOverscan", "EnableShadersStorage", "EnableTexCoordBounds",
        "EnableTextureCache", "FXAA", "ForceDisableExtraMem", "FrameDuping", "Framerate", "GLideN64IniBehaviour",
        "HybridFilter", "IgnoreTLBExceptions", "MaxHiResTxVramLimit", "MaxTxCacheSize", "MultiSampling",
        "OverscanBottom", "OverscanLeft", "OverscanRight", "OverscanTop", "RDRAMImageDitheringMode",
        "ThreadedRenderer", "aspect", "cpucore", "rdp-plugin", "rsp-plugin", "txCacheCompression",
        "txEnhancementMode", "txFilterIgnoreBG", "txFilterMode", "txHiresEnable", "txHiresFullAlphaChannel",
        "virefresh",
    ).mapTo(mutableSetOf()) { "mupen64plus-$it" }

    // melonds-ds v1.4.0 src/libretro: consumed only when the console is created (config/console.cpp GetNdsArgs and
    // CreateConsole, core/core.cpp LoadGame) and their info texts don't say "restart" (the ones that do, such as
    // console mode, boot mode, firmware and time options, are already hidden by CoreOptionFilter.requiresRestart).
    // Audio bit depth and interpolation stay: UpdateConsole re-applies them live.
    val melondsds: Set<String> = setOf(
        "melonds_jit_block_size", "melonds_jit_branch_optimisations", "melonds_jit_literal_optimisations",
        "melonds_dsi_sdcard", "melonds_dsi_sdcard_sync_sdcard_to_host",
        "melonds_homebrew_sdcard", "melonds_homebrew_sync_sdcard_to_host",
        "melonds_slot2_device", "melonds_show_bios_warnings",
    )

    // mgba/src/platform/libretro/libretro.c: _reloadSettings is only called from retro_load_game.
    val mgba: Set<String> = setOf("mgba_idle_optimization")

    // snes9x/libretro/libretro.cpp: latched at load / in ApplyROMFixes.
    val snes9x: Set<String> = setOf(
        "snes9x_msu1_enhanced_audio", "snes9x_randomize_memory", "snes9x_block_invalid_vram_access",
    )

    // ppsspp/libretro/libretro.cpp: boot-time, or only re-read on a GPU config change the core triggers
    // solely for texture scaling/shader changes; the last three are never read.
    val ppsspp: Set<String> = setOf(
        "ppsspp_cpu_core", "ppsspp_psp_model", "ppsspp_cache_iso", "ppsspp_locked_cpu_speed",
        "ppsspp_gpu_hardware_transform", "ppsspp_texture_replacement", "ppsspp_texture_anisotropic_filtering",
        "ppsspp_skip_buffer_effects", "ppsspp_lower_resolution_for_effects",
        "ppsspp_software_skinning", "ppsspp_hardware_tesselation", "ppsspp_frameskiptype",
    )

    // mednafen_supafaust/libretro.cpp: check_variables (the retro_run update path) re-reads only the four scanline
    // options; everything else is piped into mednafen's settings in retro_load_game (lines 497-740).
    val supafaust: Set<String> = setOf(
        "supafaust_pixel_format", "supafaust_correct_aspect", "supafaust_h_filter", "supafaust_deinterlacer",
        "supafaust_region", "supafaust_frame_begin_vblank", "supafaust_run_ahead", "supafaust_multitap",
        "supafaust_cx4_clock_rate", "supafaust_superfx_clock_rate", "supafaust_superfx_icache", "supafaust_audio_rate",
        "supafaust_thread_affinity_emu", "supafaust_thread_affinity_ppu", "supafaust_renderer",
    )

    // fceumm, gambatte, genesis_plus_gx: none.
}
