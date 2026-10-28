package com.nxredux.mobile.runner

import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names

object CoreRegistry {
    // melonDS DS (JesseTG/melonds-ds v1.3.1). The core draws the plain top-over-bottom 256x384 stack and takes the
    // touchscreen as a pointer; the frontend lays the two screens out (NdsLayout), so those options are locked. Keys and
    // values: src/libretro/config/constants.hpp and config/definitions/screen.hpp at the pin. BIOS and firmware are
    // optional (built-in free ones); Bios/NDS/ is copied into the system dir when present. The renderer's default comes from the device's GLES version at load (NdsRenderer). The mic options are locked too: the
    // app's Mic toggle drives the frontend feed, so the core always reads the microphone interface, always active, no overlay.
    // One plain top-bottom frame: the app sets the internal resolution from the on-screen size (at launch from the
    // window's size, then from each layout; the 4x default is never run).
    // The screen options that frame never reads are locked at the core's own defaults, so a cfg cannot bring them back.
    private val melondsds = CoreDescriptor(
        "melondsds", "libmelondsds_libretro_android.so", setOf("nds", "ids", "dsi"),
        defaultOptions = mapOf(
            "melonds_touch_mode" to "touch",
            "melonds_number_of_screen_layouts" to "1",
            "melonds_screen_layout1" to "top-bottom",
            "melonds_screen_gap" to "0",
            "melonds_show_cursor" to "disabled",
            "melonds_opengl_resolution" to "4",
            "melonds_secondary_screen_scale" to "100",
            "melonds_secondary_screen_filtering" to "nearest",
            "melonds_screen_layout2" to "left-right",
            "melonds_screen_layout3" to "largescreen-top",
            "melonds_screen_layout4" to "largescreen-bottom",
            "melonds_screen_layout5" to "hybrid-top",
            "melonds_screen_layout6" to "hybrid-bottom",
            "melonds_screen_layout7" to "top",
            "melonds_screen_layout8" to "bottom",
            "melonds_hybrid_ratio" to "2",
            "melonds_hybrid_small_screen" to "both",
            "melonds_opengl_filtering" to "nearest",
            "melonds_cursor_timeout" to "3",
            "melonds_show_current_layout" to "enabled",
            "melonds_mic_input" to "microphone",
            "melonds_mic_input_active" to "always",
            "melonds_show_mic_state" to "disabled",
        ),
        lockedOptions = setOf(
            "melonds_touch_mode", "melonds_number_of_screen_layouts", "melonds_screen_layout1",
            "melonds_screen_gap", "melonds_show_cursor", "melonds_mic_input", "melonds_mic_input_active",
            "melonds_show_mic_state", "melonds_opengl_resolution", "melonds_secondary_screen_scale",
            "melonds_secondary_screen_filtering", "melonds_hybrid_ratio", "melonds_hybrid_small_screen",
            "melonds_opengl_filtering", "melonds_cursor_timeout", "melonds_show_current_layout",
        ) + (2..8).map { "melonds_screen_layout$it" },
        biosFiles = listOf("bios7.bin", "bios9.bin", "firmware.bin", "dsi_bios7.bin", "dsi_bios9.bin"),
        restartOnlyOptions = RestartOnlyOptions.melondsds,
        offlineHiddenOptions = setOf(
            "melonds_firmware_nds_path", "melonds_firmware_dsi_path", "melonds_dsi_nand_path",
            "melonds_direct_network_interface",
        ),
        name = "melonDS DS",
        microphone = true,
    )
    private val mupen64plusNext = CoreDescriptor(
        "mupen64plus_next", "libmupen64plus_next_gles3_libretro_android.so", setOf("n64", "z64", "v64"),
        // Independent C-button Controls: every N64 input on its own RetroPad id, so N64Input can map by name (NX Redux).
        // The plugins follow Graphics API: GLideN64 on HLE for OpenGL ES, ParaLLEl-RDP with its ParaLLEl-RSP for Vulkan (the
        // RDP cannot run on HLE). The GLES pair is the default, so a locked key always has a value.
        defaultOptions = mapOf(
            "mupen64plus-alt-map" to "True",
            "mupen64plus-rdp-plugin" to "gliden64", "mupen64plus-rsp-plugin" to "hle",
        ),
        lockedOptions = setOf("mupen64plus-alt-map", "mupen64plus-rdp-plugin", "mupen64plus-rsp-plugin"),
        restartOnlyOptions = RestartOnlyOptions.mupen64plusNext,
        vulkan = VulkanSwitch(
            gles = mapOf("mupen64plus-rdp-plugin" to "gliden64", "mupen64plus-rsp-plugin" to "hle"),
            vulkan = mapOf("mupen64plus-rdp-plugin" to "parallel", "mupen64plus-rsp-plugin" to "parallel"),
            // ParaLLEl-RDP's 8/16-bit shader types fail to compile on the Adreno 840 driver (a black screen); with them off
            // it renders with subgroups still on. Safe on every GPU.
            vulkanEnv = mapOf("PARALLEL_RDP_SMALL_TYPES" to "0"),
        ),
        players = 4,
        name = "Mupen64Plus-Next",
    )
    private val ppsspp = CoreDescriptor(
        "ppsspp", "libppsspp_libretro_android.so", setOf("iso", "cso", "chd", "pbp", "elf", "prx"),
        biosFiles = listOf("PPSSPP/"),
        // The backend is locked to "auto", which asks GET_PREFERRED_HW_RENDER, so Graphics API picks GL or Vulkan; "none"
        // (software context) is hidden. retro_cheat_set only works with ppsspp_cheats enabled (off by default), so it is
        // locked on: the in-game Cheats row is the switch.
        defaultOptions = mapOf("ppsspp_backend" to "auto", "ppsspp_cheats" to "enabled"),
        lockedOptions = setOf("ppsspp_cheats", "ppsspp_backend"),
        hiddenValues = mapOf("ppsspp_backend" to setOf("vulkan", "none")),
        vulkan = VulkanSwitch(gles = mapOf("ppsspp_backend" to "auto"), vulkan = mapOf("ppsspp_backend" to "auto")),
        restartOnlyOptions = RestartOnlyOptions.ppsspp,
        // PPSSPP writes SAVEDATA/ under the save dir.
        name = "PPSSPP",
        rewind = false,
    )
    private val gambatte = CoreDescriptor("gambatte", "libgambatte_libretro_android.so", setOf("gb", "gbc", "dmg"), name = "Gambatte")
    private val mgba = CoreDescriptor(
        "mgba", "libmgba_libretro_android.so", setOf("gba"),
        restartOnlyOptions = RestartOnlyOptions.mgba,
        name = "mGBA",
    )
    // The Brick's SGB pak: mgba on the Super Game Boy model with borders. A tag view of mgba: the same id, so its
    // states and configs are keyed SGB-mgba as on the Brick; `all` and `forId` keep the plain descriptor.
    private val mgbaSgb = mgba.copy(
        extensions = setOf("gb", "gbc"),
        defaultOptions = mapOf("mgba_gb_model" to "Super Game Boy", "mgba_sgb_borders" to "ON"),
    )
    private val fceumm = CoreDescriptor(
        "fceumm", "libfceumm_libretro_android.so", setOf("nes", "fds", "unf", "unif"),
        biosFiles = listOf("disksys.rom"),
        players = 2,
        name = "FCEUmm",
    )
    private val snes9x = CoreDescriptor(
        "snes9x", "libsnes9x_libretro_android.so", setOf("sfc", "smc", "fig", "swc", "bs", "st"),
        restartOnlyOptions = RestartOnlyOptions.snes9x,
        players = 2,
        name = "Snes9x",
    )
    private val genesisPlusGx = CoreDescriptor(
        "genesis_plus_gx", "libgenesis_plus_gx_libretro_android.so",
        setOf("md", "gen", "smd", "bin", "sms", "gg", "sg", "68k", "chd", "cue", "iso", "m3u"),
        // Sega CD backup RAM as <stem>.brm beside the game's .srm, so each game keeps its own (the default
        // scd_<region>.brm is shared by every game).
        defaultOptions = mapOf("genesis_plus_gx_system_bram" to "per game"),
        biosFiles = listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"),
        // Hidden from Emulator Settings (restart-only): "per bios" would move Sega CD saves out of the per-game files.
        offlineHiddenOptions = setOf("genesis_plus_gx_system_bram"),
        players = 2,
        name = "Genesis Plus GX",
    )
    // Extensions below are each core's retro_get_system_info valid_extensions at its pin. No core lists an archive except
    // fbneo, whose zip/7z is the game (loadsArchive); for every other core the app unpacks zip/7z at launch and checks the
    // inner file (Names.ARCHIVE_EXTENSIONS). Defaults are the Brick paks' default.cfg values.
    private val gpsp = CoreDescriptor(
        "gpsp", "libgpsp_libretro_android.so", setOf("gba", "bin", "agb", "gbz", "u1"),
        biosFiles = listOf("gba_bios.bin"),   // optional: an open BIOS is built in
        keepLoaded = true,   // its recompiler cache is mapped once near the core and kept (cores/patches/gpsp.patch)
        name = "gpSP",
    )
    private val pcsxRearmed = CoreDescriptor(
        "pcsx_rearmed", "libpcsx_rearmed_libretro_android.so",
        setOf("bin", "cue", "img", "mdf", "pbp", "toc", "cbn", "m3u", "chd", "iso", "exe"),
        defaultOptions = mapOf("pcsx_rearmed_dithering" to "enabled"),
        biosFiles = listOf("psxonpsp660.bin", "scph101.bin", "scph5501.bin", "scph7001.bin", "scph1001.bin"),   // optional: HLE built in
        controllerPort0 = CoreDescriptor.PS_DUALSHOCK,
        players = 2,
        name = "PCSX-ReARMed",
    )
    // SwanStation (libretro's DuckStation fork), the PSX folder's core as on the Brick (PSX.pak). Content through the
    // runner's VFS (the core's vfs_hybrid falls back to the frontend when a local open fails). It needs a real BIOS:
    // Bios/PSX/scph5500.bin (JP), scph5501.bin (US) or scph5502.bin (EU). Its DualShock is 261
    // (CoreDescriptor.SWANSTATION_DUALSHOCK): the Brick's 517 (minarch_gamepad_type 1) is swanstation's Analog Joystick, a
    // flight stick most games ignore (Marvel Super Heroes took no buttons). Netplay with a handheld on 517 mismatches.
    private val swanstation = CoreDescriptor(
        "swanstation", "libswanstation_libretro_android.so",
        setOf("exe", "psexe", "cue", "bin", "img", "iso", "chd", "pbp", "ecm", "mds", "psf", "m3u"),
        biosFiles = listOf("scph5500.bin", "scph5501.bin", "scph5502.bin"),
        controllerPort0 = CoreDescriptor.SWANSTATION_DUALSHOCK,
        players = 2,
        // "Auto" asks GET_PREFERRED_HW_RENDER, so Graphics API decides; not locked, so OpenGL / Software stay the player's
        // choice on OpenGL ES. Vulkan is reached only through Graphics API, never offered as a value.
        defaultOptions = mapOf("swanstation_GPU_Renderer" to "Auto", "swanstation_MemoryCards_Card1Type" to "Libretro"),
        // Card 1 as the frontend's battery save: one card per game named by the Save format, shared with PCSX ReARMed (Saves/PS).
        lockedOptions = setOf("swanstation_MemoryCards_Card1Type"),
        hiddenValues = mapOf("swanstation_GPU_Renderer" to setOf("Vulkan")),
        vulkan = VulkanSwitch(vulkan = mapOf("swanstation_GPU_Renderer" to "Auto"), lock = false),
        name = "SwanStation",
    )
    // SPIKE: PS2 on ARMSX2 (ARMSX2/ARMSX2 2.9, cores/Makefile). Reads <system>/pcsx2/{bios,resources}. Opens the game
    // through the runner's libretro VFS (/rom/<name>, need_fullpath), like Dolphin: no cache copy, so Play builds work.
    private val armsx2 = CoreDescriptor(
        "armsx2", "libarmsx2_libretro_android.so", setOf("iso", "chd", "cso", "zso", "bin", "elf", "gz", "m3u"),
        biosSubdir = "pcsx2/bios",
        // BIOS: Auto lets the core pick from <system>/pcsx2/bios; Console Settings' BIOS row forces one file at launch.
        // Patches and cheats off until the player turns them on: the core reads the game's .pnach patches from
        // Cheats/PS2/Patches/ (Tools → Cheats & Patches downloads PCSX2's patches.zip there) and its cheats from Cheats/PS2/
        // (cores/patches/armsx2/0005), never shipped with the app. Software renderer threads stay hidden: they matter only
        // to a renderer the app never picks.
        defaultOptions = mapOf(
            "armsx2_renderer" to "OpenGL", "armsx2_bios" to "auto", "armsx2_upscale" to "2x",
            "armsx2_widescreen_patches" to "disabled", "armsx2_no_interlacing_patches" to "disabled", "armsx2_cheats" to "disabled", "armsx2_sw_threads" to "2",
        ),
        lockedOptions = setOf("armsx2_renderer", "armsx2_bios", "armsx2_upscale", "armsx2_sw_threads"),
        vulkan = VulkanSwitch(gles = mapOf("armsx2_renderer" to "OpenGL"), vulkan = mapOf("armsx2_renderer" to "Vulkan")),
        name = "ARMSX2",
        cheats = false,
        rewind = false,
    )
    private val race = CoreDescriptor(
        "race", "librace_libretro_android.so", setOf("ngp", "ngc", "ngpc", "npc"),
        defaultOptions = mapOf("race_dark_filter_level" to "50"),
        cheats = false,   // retro_cheat_set is empty
        name = "RACE",
    )
    private val pceFast = CoreDescriptor(
        "mednafen_pce_fast", "libmednafen_pce_fast_libretro_android.so", setOf("pce", "cue", "ccd", "chd", "toc", "m3u"),
        biosFiles = listOf("syscard3.pce"),   // CD content only
        players = 4,
        name = "Beetle PCE Fast",
    )
    private val vb = CoreDescriptor(
        "mednafen_vb", "libmednafen_vb_libretro_android.so", setOf("vb", "vboy", "bin"),
        defaultOptions = mapOf("vb_3dmode" to "anaglyph"),
        cheats = false,   // retro_cheat_set is empty
        name = "Beetle VB",
    )
    private val handy = CoreDescriptor("handy", "libhandy_libretro_android.so", setOf("lnx", "lyx", "o"), biosFiles = listOf("lynxboot.img"), cheats = false, ramCheats = true, name = "Handy")   // retro_cheat_set is empty; the Lynx files are RAM patches
    private val stella2014 = CoreDescriptor("stella2014", "libstella2014_libretro_android.so", setOf("a26", "bin"), players = 2, cheats = false, name = "Stella 2014")   // retro_cheat_set is empty
    private val prosystem = CoreDescriptor(
        "prosystem", "libprosystem_libretro_android.so", setOf("a78", "bin", "cdf"),
        biosFiles = listOf("7800 BIOS (U).rom", "7800 BIOS (E).rom"),   // optional
        players = 2,
        cheats = false, ramCheats = true,   // retro_cheat_set is empty; the Atari 7800 files are RAM patches
        name = "ProSystem",
    )
    private val a5200 = CoreDescriptor("a5200", "liba5200_libretro_android.so", setOf("a52", "bin"), biosFiles = listOf("5200.rom"), players = 2, cheats = false, name = "a5200")   // optional: Altirra OS built in; retro_cheat_set is empty, and no system RAM for RAM patches
    private val pokemini = CoreDescriptor(
        "pokemini", "libpokemini_libretro_android.so", setOf("min"),
        defaultOptions = mapOf(
            "pokemini_60hz_mode" to "enabled", "pokemini_palette" to "Old",
            "pokemini_piezofilter" to "disabled", "pokemini_lowpass_filter" to "enabled",
        ),
        biosFiles = listOf("bios.min"),   // optional: a free BIOS is built in
        cheats = false,   // retro_cheat_set is empty
        name = "PokeMini",
    )
    private val wswan = CoreDescriptor("mednafen_wswan", "libmednafen_wswan_libretro_android.so", setOf("ws", "wsc", "pc2"), name = "Beetle WonderSwan")
    // The Brick's SUPA.pak pins thread affinity for its four cores; not carried (phones are big.LITTLE). The core's own
    // defaults pin too (emu 0x2, PPU 0x1), and Android has no pthread_setaffinity_np, so mednafen's Thread_SetAffinity
    // throws in retro_load_game and aborts the app: 0x0 turns both off.
    private val supafaust = CoreDescriptor(
        "mednafen_supafaust", "libmednafen_supafaust_libretro_android.so", setOf("smc", "swc", "sfc", "fig"),
        defaultOptions = mapOf(
            "supafaust_pixel_format" to "rgb565",
            "supafaust_thread_affinity_emu" to "0x0", "supafaust_thread_affinity_ppu" to "0x0",
        ),
        // Any other mask aborts the app at load (see above), so a .cfg value (the Brick's pins) is ignored too.
        lockedOptions = setOf("supafaust_thread_affinity_emu", "supafaust_thread_affinity_ppu"),
        restartOnlyOptions = RestartOnlyOptions.supafaust,
        players = 2,
        name = "Supafaust",
    )
    private val fake08 = CoreDescriptor("fake08", "libfake08_libretro_android.so", setOf("p8", "png"), cheats = false, name = "fake-08")   // retro_cheat_set is empty
    private val prboom = CoreDescriptor(
        "prboom", "libprboom_libretro_android.so", setOf("wad", "iwad", "pwad", "lmp"),
        // prboom looks for IWADs and prboom.wad in the WAD's own folder, then <system>/prboom/, then <system>/, so it gets a
        // real path: the cache copy, with IWADs from Bios/PRBOOM/ and the bundled prboom.wad in <system>/.
        loadMode = LoadMode.REAL_PATH,
        // Rumble on (the core's default is off): Controls → Game rumble decides whether and how hard it is felt.
        defaultOptions = mapOf("prboom-mouse_on" to "disabled", "prboom-rumble" to "enabled"),
        biosFiles = listOf("prboom.wad"),   // required, bundled as an asset
        // prboom saves under <saves>/<stem>/.
        name = "PrBoom",
        rewind = false,
    )
    // The Sega consoles' default emulator, as on the Brick (MD, SMS, GG, SG1000, SEGACD and 32X paks). 68k is listed by
    // its valid_extensions (sc, sgd and pco too, which no console here owns).
    private val picodrive = CoreDescriptor(
        "picodrive", "libpicodrive_libretro_android.so", setOf("32x", "bin", "md", "gen", "smd", "68k", "sms", "gg", "sg", "cue", "iso", "chd", "m3u"),
        // Its NDK build has no libretro VFS (plain fopen on the path): give it a real file.
        loadMode = LoadMode.REAL_PATH,
        // The Brick's MD, SEGACD and 32X paks' values (the rest of theirs are the core's defaults); picodrive_input1 and
        // picodrive_input2 are set at launch and on a change from Console Settings → Controller Type (SegaPadType).
        defaultOptions = mapOf(
            "picodrive_drc" to "enabled", "picodrive_sound_rate" to "44100",
            "picodrive_input1" to "6 button pad", "picodrive_input2" to "6 button pad",
        ),
        lockedOptions = setOf("picodrive_input1", "picodrive_input2"),
        players = 2,
        name = "PicoDrive",
    )
    // Tag views of picodrive for the 8-bit consoles (as mgbaSgb): the SMS, GG and SG1000 paks force the 3-button pad
    // (no Controller Type row there) and SMS and GG the Master System Type. Same id: states and configs stay <TAG>-picodrive.
    private val picodrive8bit = picodrive.copy(
        defaultOptions = picodrive.defaultOptions + mapOf("picodrive_input1" to "3 button pad", "picodrive_input2" to "3 button pad"),
    )
    private val picodriveSms = picodrive8bit.copy(defaultOptions = picodrive8bit.defaultOptions + ("picodrive_smstype" to "Master System"))
    private val picodriveGg = picodrive8bit.copy(defaultOptions = picodrive8bit.defaultOptions + ("picodrive_smstype" to "Game Gear"))
    private val gearcoleco = CoreDescriptor(
        "gearcoleco", "libgearcoleco_libretro_android.so", setOf("col", "cv", "bin", "rom"),
        biosFiles = listOf("colecovision.rom", "coleco.rom"),
        cheats = false, ramCheats = true,   // retro_cheat_set is a TODO; the database's ColecoVision files are RAM patches
        players = 2,
        name = "Gearcoleco",
    )
    // Its extensions are archives (flycast also lists them): an arcade set's zip/7z IS the game (loadsArchive). Parents and BIOS
    // come beside it as companions (ArcadeCompanions) or from <system>/ and <system>/fbneo/ (Bios/FBN/).
    private val fbneo = CoreDescriptor(
        "fbneo", "libfbneo_libretro_android.so", setOf("zip", "7z"),
        // Locked, as on the Brick's FBN.pak (its default.cfg writes `-fbneo-vertical-mode`, the cfg lock prefix).
        defaultOptions = mapOf("fbneo-vertical-mode" to "disabled"),
        lockedOptions = setOf("fbneo-vertical-mode"),
        // fbneo writes <saves>/fbneo/<set>.fs, .memcard, .save.
        cheats = false, ramCheats = true,   // retro_cheat_set is empty; the database's FBNeo files are RAM patches
        players = 4,
        name = "FinalBurn Neo",
    )
    // Dreamcast discs, plus NAOMI/Atomiswave sets whose zip/7z flycast loads as is (loadsArchive). BIOS in <system>/dc/.
    private val flycast = CoreDescriptor(
        "flycast", "libflycast_libretro_android.so", setOf("chd", "cdi", "gdi", "cue", "m3u", "zip", "7z"),
        biosFiles = listOf("dc/dc_boot.bin"),
        // Flycast has no renderer option and asks GET_PREFERRED_HW_RENDER; its libretro Vulkan context enables the frontend's
        // required device extensions only with patches/flycast.patch.
        vulkan = VulkanSwitch(),
        // NX Redux's flycast defaults; values are flycast's exact strings (libretro_core_options.h), a misspelt one is ignored.
        // Per-game VMU: <save dir>/<content name>.A1.bin, with the game's saves.
        defaultOptions = mapOf(
            "reicast_per_content_vmus" to "VMU A1",
            "reicast_alpha_sorting" to "per-strip (fast, least accurate)",
            "reicast_threaded_rendering" to "enabled",
            "reicast_delay_frame_swapping" to "enabled",
            "reicast_internal_resolution" to "640x480",
            "reicast_region" to "USA",
            "reicast_language" to "English",
            "reicast_broadcast" to "NTSC",
            "reicast_cable_type" to "TV (RGB)",
            "reicast_widescreen_hack" to "disabled",
            "reicast_hle_bios" to "disabled",
            // flycast's retro_run runs until the game renders, so a 30 fps game (Metal Slug 6) gets two vblanks per
            // call; with detection on, flycast reports the halved rate (SET_SYSTEM_AV_INFO) and the runner paces to it.
            "reicast_detect_vsync_swap_interval" to "enabled",
            // Ports 2–4 are plugged in for multiplayer (controllerPortsFor); their first slot's VMU would make flycast
            // write shared cards used by every game (vmu_save_B1..D1.bin, only ever in Saves/DC: patches/flycast.patch).
            // Games save to A1; slot 2 keeps the vibration pack, so players 2–4 still rumble.
            "reicast_device_port2_slot1" to "None",
            "reicast_device_port3_slot1" to "None",
            "reicast_device_port4_slot1" to "None",
        ),
        // flycast writes <saves>/reicast/<game>.nvmem, .eeprom, .nvmem2 (arcade).
        // Detection off runs 30 fps games 2x (see its default), so a stored "disabled" must not win.
        // Any VMU mode but "VMU A1" moves the VMU out of <content>.A1.bin, where the runner looks for it.
        lockedOptions = setOf("reicast_detect_vsync_swap_interval", "reicast_per_content_vmus"),
        // Per-pixel sorting draws one flat colour on Adreno; a stored per-pixel value falls back to per-strip.
        hiddenValues = mapOf("reicast_alpha_sorting" to setOf("per-pixel (accurate)")),
        // Only read by the per-pixel sorting hidden above: dead rows out of game.
        offlineHiddenOptions = setOf("reicast_oit_abuffer_size", "reicast_oit_layers"),
        // retro_cheat_set is empty: libretro's Dreamcast .cht files are RetroArch RAM writes (handler 1, address + value,
        // no code), which the runner applies to the core's system RAM.
        cheats = false, ramCheats = true,
        biosSubdir = "dc",
        controllerPort0 = CoreDescriptor.JOYPAD,
        controllerPortCount = 4,
        savesNamedAfterContent = true,   // the VMU is <content>.A1.bin: a zipped disc unpacks as <archive stem>.<ext>
        players = 4,
        name = "Flycast",
        rewind = false,
    )

    // Nintendo 3DS on Azahar (azahar-emu/azahar 2126.1.2, src/citra_libretro, patched; cores/Makefile), GLES or Vulkan
    // (Graphics API). Content through the runner's VFS: the game is /rom/<name>, read from the SAF descriptor in place, not
    // a multi-GB copy into the cache. FileUtil's plain stat misses that file, so the patch asks the VFS when stat finds
    // nothing (cores/patches/azahar.patch). Decrypted games only; the AES keyblob and the open-source shared font are
    // built in, no BIOS. Its user dir (NAND, SD card, shader cache) is <save dir>/Azahar/, kept local (not part of the
    // saves); the libretro save path keeps it under the runner's save dir. Defaults: the GL renderer ("Software" is not
    // built on Android), 3x internal resolution, the disk shader cache, the core's own top-over-bottom layout with the
    // touchscreen taken from the pointer. Its mic input is locked to the frontend: the app's Mic toggle drives the feed.
    // The internal resolution is the app's (3DS 3D Resolution and Scale, N3dsOptions), so it is locked; so are the large
    // screen proportion (the core's default, 4.00) and the stereoscopic 3D mode (off: 2D only). The swap options stay visible.
    private val azahar = CoreDescriptor(
        "azahar", "libazahar_libretro_android.so", setOf("3ds", "3dsx", "z3dsx", "elf", "axf", "cci", "zcci", "cxi", "zcxi", "app"),
        defaultOptions = mapOf(
            "citra_graphics_api" to "OpenGL",
            "citra_resolution_factor" to "3",
            "citra_use_disk_shader_cache" to "enabled",
            "citra_layout_option" to "default",
            "citra_use_libretro_save_path" to "LibRetro Default",
            "citra_enable_touch_touchscreen" to "enabled",
            // The runner has no mouse (the pointer is touch, read by Touch Device Support): locked off, so hidden.
            "citra_enable_mouse_touchscreen" to "disabled",
            // Motion from the phone's sensors (the runner's sensor interface, GameActivity.offerN3dsMotion): locked on,
            // as a 3DS has no switch for it and the core reads the option only when the game loads.
            "citra_enable_motion" to "enabled",
            "citra_motion_sensitivity" to "1.0",
            "citra_input_type" to "frontend",
            "citra_large_screen_proportion" to "4.00",
            "citra_render_3d" to "off",
        ),
        lockedOptions = setOf(
            "citra_resolution_factor", "citra_large_screen_proportion", "citra_render_3d",
            "citra_graphics_api", "citra_use_libretro_save_path", "citra_enable_mouse_touchscreen", "citra_enable_motion",
            "citra_motion_sensitivity", "citra_input_type",
            // The app sets the layout from its per-orientation rows (N3dsOptions.layoutRows), so the core's is locked.
            "citra_layout_option",
        ),
        hiddenValues = mapOf("citra_graphics_api" to setOf("Software")),
        // With Vulkan compiled in, Azahar's Android "Auto" picks Vulkan without asking the frontend
        // (GET_PREFERRED_HW_RENDER), so the API is always set explicitly: OpenGL, or Vulkan through Graphics API.
        vulkan = VulkanSwitch(gles = mapOf("citra_graphics_api" to "OpenGL"), vulkan = mapOf("citra_graphics_api" to "Vulkan")),
        cheats = false,   // retro_cheat_set is empty
        name = "Azahar",
        rewind = false,
        microphone = true,
    )

    // Wii and GameCube on Dolphin (libretro/dolphin 3aac3f05, patched; cores/Makefile), GLES or Vulkan (Graphics API). It
    // opens the game by path (need_fullpath) through the runner's VFS (its Libretro::VFile): /rom/<name>, read from the SAF
    // descriptor in place, not a multi-GB copy into the cache. Its user dir is <save dir>/User/, so each console has its
    // own (Saves/WII/User, Saves/GC/User) and one's NAND (User/Wii) and memory cards (User/GC) are not seen by the other; a
    // GC disc played under WII (a loose GC image resolves to WII) uses WII's.
    // Defaults: hardware renderer; dual core with the GPU thread synced (unsynced, NSMB Wii desynced the FIFO after the
    // airship); async with skip-rendering (shaders compile in the background on the runner's shared contexts and an effect
    // whose shader is not ready is skipped for a moment, not drawn with GPU-heavy ubershaders, which made SMG2 lag on the
    // Fold); on-screen messages off. The orientation hotkeys are off because the app sets the controller type itself
    // (nx_wii_controller); for the same reason Wiimote Device stays "Set per port" (it would override every port's). Audio is pushed per frame (mode 0):
    // the runner has no audio or frame-time callback for the other modes. No VR mode (no VR frontend), no popups (RetroArch
    // notifications). Four players: every port gets the game's device. Port 0 must be set or the Wiimote mapping is
    // half built: the sideways Wiimote for Wii, the GameCube pad for GameCube; GameActivity overrides the Wii ports from
    // the resolved controller.
    private val dolphinOptions = mapOf(
        "dolphin_renderer" to "Hardware",
        "dolphin_main_cpu_thread" to "enabled",
        "dolphin_sync_gpu" to "enabled",
        "dolphin_osd_enabled" to "disabled",
        "dolphin_shader_compilation_mode" to "3",
        "dolphin_hotkey_sideways_toggle" to "Disabled",
        "dolphin_hotkey_upright_toggle" to "Disabled",
        "dolphin_cheats_enabled" to "enabled",   // when off, retro_cheat_set ignores every code
        "dolphin_call_back_audio_method" to "0",
        "dolphin_vr_mode" to "disabled",
        "dolphin_wiimote_default_device" to "frontend",
        "dolphin_log_popups_enabled" to "disabled",
    )
    private val dolphinLocked = setOf(
        "dolphin_renderer", "dolphin_osd_enabled", "dolphin_debug_mode_enabled", "dolphin_bluetooth_passthrough",
        "dolphin_ir_mode", "dolphin_log_level", "dolphin_log_boot_enabled", "dolphin_log_core_enabled",
        "dolphin_log_video_enabled", "dolphin_log_common_enabled", "dolphin_log_host_gpu_enabled",
        "dolphin_log_memmap_enabled", "dolphin_log_dspinterface_enabled", "dolphin_log_dsphle_enabled",
        "dolphin_log_dsplle_enabled", "dolphin_log_dsp_mail_enabled",
        "dolphin_hotkey_sideways_toggle", "dolphin_hotkey_upright_toggle", "dolphin_cheats_enabled",
        "dolphin_vr_mode", "dolphin_wiimote_default_device", "dolphin_log_popups_enabled",
        // Console Settings → Wii Screen sets these at launch (WiiScreen): the shape, and the pointer geometry for it.
        "dolphin_widescreen", "dolphin_ir_yaw", "dolphin_ir_pitch", "dolphin_ir_offset",
    )
    private val dolphinWii = CoreDescriptor(
        "dolphin", "libdolphin_libretro_android.so", setOf("elf", "dol", "gcm", "iso", "tgc", "wbfs", "ciso", "gcz", "wad", "wia", "rvz", "m3u"),
        // The touch pointer (Mouse controls pointer = RETRO_DEVICE_POINTER), its geometry calibrated on the Fold in Super
        // Mario Galaxy 2 so a tap lands under the finger (the libretro defaults, yaw 25, pitch 25, offset 0, overshot
        // sideways and sat below it). These are the 16:9 values; GameActivity sets the ones for the shape the game boots in
        // (WiiScreen.pointerOptions).
        defaultOptions = dolphinOptions + mapOf(
            "dolphin_ir_mode" to "2", "dolphin_ir_yaw" to "18", "dolphin_ir_pitch" to "20", "dolphin_ir_offset" to "16",
        ),
        lockedOptions = dolphinLocked,
        hiddenValues = mapOf("dolphin_renderer" to setOf("Software", "Null")),
        vulkan = VulkanSwitch(swapchainFrames = true),
        // Dolphin writes its NAND and memory cards under User/Wii and User/GC in its console's save dir (Saves/<console tag>/User).
        controllerPort0 = CoreDescriptor.WIIMOTE_SIDEWAYS,
        players = 4,
        cheats = true,   // Dolphin's built-in codes plus the player's own .cht (DolphinCheats)
        name = "Dolphin",
        rewind = false,   // dolphinGc copies it
    )
    private val dolphinGc = dolphinWii.copy(
        defaultOptions = dolphinOptions,
        controllerPort0 = CoreDescriptor.JOYPAD,
    )

    // Canonical descriptors before tag views, so `all` (distinct by id) keeps plain mgba.
    private val byTag: Map<String, CoreDescriptor> = mapOf(
        "NDS" to melondsds,
        "N64" to mupen64plusNext,
        "PSP" to ppsspp,
        "GB" to gambatte, "GBC" to gambatte,
        "GBA" to gpsp, "MGBA" to mgba, "SGB" to mgbaSgb,
        "FC" to fceumm, "FDS" to fceumm,
        "SFC" to snes9x, "SUPA" to supafaust,
        "MD" to picodrive, "SMS" to picodriveSms, "GG" to picodriveGg,
        "SG1000" to picodrive8bit, "SEGACD" to picodrive,
        "GPGX" to genesisPlusGx,
        // Genesis Plus GX on the other Sega consoles: the handheld's one GPGX pak (handheldTag).
        "GPGX_SMS" to genesisPlusGx, "GPGX_GG" to genesisPlusGx, "GPGX_SEGACD" to genesisPlusGx, "GPGX_SG1000" to genesisPlusGx,
        "PS" to pcsxRearmed, "PSX" to swanstation,
        "NGP" to race, "NGPC" to race,
        "PCE" to pceFast,
        "VB" to vb,
        "LYNX" to handy,
        "A2600" to stella2014,
        "A5200" to a5200,
        "A7800" to prosystem,
        "PKM" to pokemini,
        "WSC" to wswan,
        "P8" to fake08,
        "PRBOOM" to prboom,
        "32X" to picodrive, "COLECO" to gearcoleco, "FBN" to fbneo,
        "DC" to flycast,
        "3DS" to azahar,
        "WII" to dolphinWii, "GC" to dolphinGc,
        "PS2" to armsx2,
    )

    /** Extensions too generic to name a console on their own (disc images and sheets shared by many systems). */
    val GENERIC_EXTENSIONS: Set<String> = setOf("bin", "iso", "chd", "cue", "img", "m3u", "toc", "ccd")

    /**
     * Display names are the NX Redux skeleton's folder names; aliases add `scraper_systems.c`'s names. [Console.extensions]
     * are explicit: what the console's games use, which may be a subset of its cores' (pbp stays PSP's; png, o, lmp and exe
     * are run by their cores but only reach them by folder or sidecar).
     */
    val consoles: List<Console> = listOf(
        Console("nds", "Nintendo DS", listOf("NDS"), setOf("nds", "ds", "nintendo ds"), setOf("nds"), rawSram = false),
        Console("n64", "Nintendo 64", listOf("N64"), setOf("n64", "nintendo 64"), setOf("n64", "z64", "v64"), rawSram = false),
        Console("psp", "Sony Playstation Portable", listOf("PSP"), setOf("psp", "ppsspp", "playstation portable", "sony playstation portable"), setOf("iso", "cso", "chd", "pbp", "elf", "prx"), rawSram = false),
        Console("ps2", "Sony PlayStation 2", listOf("PS2"), setOf("ps2", "playstation 2", "sony playstation 2"), setOf("iso", "chd", "zso"), rawSram = false),
        Console("ps", "Sony PlayStation", listOf("PS", "PSX"), setOf("ps", "ps1", "psx", "playstation", "sony playstation"), setOf("bin", "cue", "img", "mdf", "toc", "cbn", "m3u", "chd", "iso"), rawSram = false),
        Console("gb", "Game Boy", listOf("GB"), setOf("gb", "game boy"), setOf("gb", "dmg"), rawSram = true),
        Console("gbc", "Game Boy Color", listOf("GBC"), setOf("gbc", "game boy color"), setOf("gbc"), rawSram = true),
        Console("gba", "Game Boy Advance", listOf("GBA", "MGBA"), setOf("gba", "mgba", "game boy advance"), setOf("gba", "bin", "agb", "gbz", "u1"), rawSram = true),
        Console("fc", "Nintendo ES", listOf("FC"), setOf("fc", "nes", "famicom", "nintendo entertainment system", "nintendo es"), setOf("nes", "unf", "unif"), rawSram = true),
        Console("sfc", "Super Nintendo ES", listOf("SFC", "SUPA"), setOf("sfc", "snes", "supa", "super famicom", "super nintendo", "super nintendo es"), setOf("sfc", "smc", "fig", "swc", "bs", "st"), rawSram = true),
        // PicoDrive first, as the Brick's MD pak; GPGX is its GPGX pak (Genesis Plus GX), states in GPGX-genesis_plus_gx.
        Console("md", "Sega Genesis", listOf("MD", "GPGX"), setOf("md", "gen", "genesis", "smd", "gpgx", "mega drive", "megadrive", "sega mega drive", "sega genesis"), setOf("md", "gen", "smd", "bin", "68k"), rawSram = true),
        // Their Genesis Plus GX tags are GPGX_<console tag> here, GPGX on the handheld (handheldTag).
        Console("sms", "Sega Master System", listOf("SMS", "GPGX_SMS"), setOf("sms", "master system", "sega master system"), setOf("sms"), rawSram = false),
        Console("gg", "Sega Game Gear", listOf("GG", "GPGX_GG"), setOf("gg", "game gear", "sega game gear"), setOf("gg"), rawSram = false),
        Console("sg1000", "Sega SG-1000", listOf("SG1000", "GPGX_SG1000"), setOf("sg", "sg1000", "sg-1000", "sega sg-1000"), setOf("sg"), rawSram = false),
        Console("segacd", "Sega CD", listOf("SEGACD", "GPGX_SEGACD"), setOf("segacd", "scd", "sega cd", "mega cd", "megacd", "mdcd"), setOf("chd", "cue", "iso", "m3u"), rawSram = false),
        Console("fds", "Famicom Disk System", listOf("FDS"), setOf("fds", "famicom disk system"), setOf("fds"), rawSram = false),
        Console("sgb", "Super Game Boy", listOf("SGB"), setOf("sgb", "super game boy"), emptySet(), rawSram = false),
        Console("ngp", "Neo Geo Pocket", listOf("NGP"), setOf("ngp", "neo geo pocket"), setOf("ngp"), rawSram = false),
        Console("ngpc", "Neo Geo Pocket Color", listOf("NGPC"), setOf("ngpc", "neo geo pocket color"), setOf("ngc", "ngpc", "npc"), rawSram = false),
        Console("pce", "TurboGrafx-16", listOf("PCE"), setOf("pce", "pcengine", "pc engine", "tg16", "tgfx16", "turbografx-16", "turbografx 16", "pc engine cd", "turbografx-cd"), setOf("pce", "cue", "ccd", "chd", "toc", "m3u"), rawSram = false),
        Console("vb", "Virtual Boy", listOf("VB"), setOf("vb", "virtual boy"), setOf("vb", "vboy", "bin"), rawSram = false),
        Console("lynx", "Atari Lynx", listOf("LYNX"), setOf("lynx", "lnx", "atari lynx"), setOf("lnx", "lyx"), rawSram = false),
        Console("a2600", "Atari 2600", listOf("A2600"), setOf("a2600", "atari", "atari2600", "atari 2600"), setOf("a26", "bin"), rawSram = false),
        Console("a5200", "Atari 5200", listOf("A5200"), setOf("a5200", "atari 5200"), setOf("a52", "bin"), rawSram = false),
        Console("a7800", "Atari 7800", listOf("A7800"), setOf("a7800", "atari 7800"), setOf("a78", "bin", "cdf"), rawSram = false),
        Console("pkm", "Pok\u00E9mon mini", listOf("PKM"), setOf("pkm", "poke", "pokemini", "pokemon mini", "pok\u00E9mon mini"), setOf("min"), rawSram = false),
        Console("wsc", "Wonderswan Color", listOf("WSC"), setOf("wsc", "ws", "wswan", "wswanc", "wonderswan", "wonderswan color"), setOf("ws", "wsc", "pc2"), rawSram = false),
        Console("p8", "Pico-8", listOf("P8"), setOf("p8", "pico8", "pico-8"), setOf("p8"), rawSram = false),
        Console("prboom", "Doom", listOf("PRBOOM"), setOf("prboom", "doom"), setOf("wad", "iwad", "pwad"), rawSram = false),
        Console("32x", "Sega 32X", listOf("32X"), setOf("32x", "sega 32x", "sega32x"), setOf("32x"), rawSram = false),
        Console("coleco", "Colecovision", listOf("COLECO"), setOf("coleco", "colecovision", "coleco vision", "cv"), setOf("col", "cv"), rawSram = false),
        // Owns no extension (like SGB): a loose zip is never claimed as arcade. No "mame" or "neogeo" alias.
        Console("fbn", "Arcade", listOf("FBN"), setOf("arcade", "fbn", "fbneo", "fba", "finalburn neo", "final burn neo"), emptySet(), rawSram = false),
        // Discs only: an arcade zip in a Dreamcast folder still reaches flycast by folder, never by extension.
        Console("dc", "Dreamcast", listOf("DC"), setOf("dc", "dreamcast", "sega dreamcast"), setOf("chd", "cdi", "gdi", "cue", "m3u"), rawSram = false),
        // elf, axf and app are left to the folder: too generic to name a 3DS game (PSP runs elf too).
        Console("3ds", "Nintendo 3DS", listOf("3DS"), setOf("3ds", "n3ds", "nintendo 3ds", "azahar", "citra"), setOf("3ds", "3dsx", "z3dsx", "cci", "zcci", "cxi", "zcxi"), rawSram = false),
        // iso, elf, dol, wad (Doom's) and m3u go by folder; a GameCube rvz or wia reaches GC by its folder too.
        Console("wii", "Nintendo Wii", listOf("WII"), setOf("wii", "nintendo wii"), setOf("wbfs", "wia", "rvz"), rawSram = false),
        Console("gc", "Nintendo GameCube", listOf("GC"), setOf("gc", "gamecube", "nintendo gamecube"), setOf("gcm", "gcz", "tgc", "ciso"), rawSram = false),
    )

    /** Every extension some tag's core runs, console-owned or not (png, o, lmp, exe only by folder or sidecar). */
    val coreExtensions: Set<String> = byTag.values.flatMap { it.extensions }.toSet()

    private val consoleByTag: Map<String, Console> = consoles.flatMap { c -> c.tags.map { it to c } }.toMap()
    private val consoleByAlias: Map<String, Console> = consoles.flatMap { c -> c.aliases.map { it to c } }.toMap()

    fun consoleOf(tag: String): Console? = consoleByTag[tag.trim().uppercase()]

    /** The console tag [tag]'s folders are named by on the phone: its console's first tag, else [tag] itself (uppercase). */
    fun consoleTag(tag: String): String = tag.trim().uppercase().let { t -> consoleOf(t)?.defaultTag ?: t }

    /**
     * Tags the handheld gives folders of several consoles: its GPGX pak runs `Sega Genesis (GPGX)`, `Sega Master System
     * (GPGX)`, `Sega Game Gear (GPGX)` and `Sega CD (GPGX)`, all saving to `Saves/GPGX`. Such a tag names no console on its
     * own ([consoleOfFolder]).
     */
    val SHARED_HANDHELD_TAGS: Set<String> = setOf("GPGX")

    /**
     * The console a `[display] ([tag])` folder (either device's) holds; the library and Device Sync (SyncMap.consoleOf)
     * both use this one rule: the console with the same display name (ignoring case) first, then the one the display name
     * is an alias of (`Mega CD (GPGX)` is Sega CD, not GPGX's Genesis), then the same ignoring spaces and punctuation too
     * (`Gameboy`, `Mastersystem`), then the console of its tag unless the tag is one several consoles share
     * ([SHARED_HANDHELD_TAGS]), then the console the display name mentions ([consoleForFolderName]: `Sega Mega-CD`, `My
     * Game Gear`). Null when none of them: a shared tag with a name of no console is never guessed.
     */
    fun consoleOfFolder(display: String, tag: String): Console? =
        consoles.firstOrNull { it.displayName.equals(display, ignoreCase = true) }
            ?: consoleByAlias(display)
            ?: bySquashedName[squash(display)]
            ?: consoleOf(tag)?.takeIf { tag.trim().uppercase() !in SHARED_HANDHELD_TAGS }
            ?: consoleForFolderName(display)

    private fun squash(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** Every console's display name and aliases without case, spaces or punctuation; the first console wins a clash. */
    private val bySquashedName: Map<String, Console> = buildMap {
        consoles.forEach { c -> (c.aliases + c.displayName).forEach { putIfAbsent(squash(it), c) } }
    }

    private const val GPGX_PREFIX = "GPGX_"

    /**
     * The handheld's tag for this phone's [tag] (uppercase): a game's tag also names its console here, so Genesis Plus GX
     * on Master System, Game Gear, Sega CD and SG-1000 is GPGX_SMS, GPGX_GG, GPGX_SEGACD and GPGX_SG1000, all the
     * handheld's one GPGX pak; every other tag is the handheld's own. Used wherever a tag meets the handheld: state folders
     * (`.userdata/shared/GPGX-genesis_plus_gx`), Device Sync's folder tags, netplay's wire, and the labels the user reads.
     */
    fun handheldTag(tag: String): String = tag.trim().uppercase().let { if (it.startsWith(GPGX_PREFIX)) "GPGX" else it }

    /** [console]'s tag that the handheld calls [handheld] (GPGX on Master System: GPGX_SMS); null when none of its tags is. */
    fun phoneTag(console: Console, handheld: String): String? =
        handheld.trim().uppercase().let { h -> console.tags.firstOrNull { handheldTag(it) == h } }

    /**
     * A `systems.txt` [tag] read in a folder of [console]: itself when it is one of the console's tags, else the console's
     * tag the handheld calls [tag] ([phoneTag]: GPGX in a Master System folder is GPGX_SMS); null for another console's.
     */
    fun tagOn(console: Console, tag: String): String? =
        tag.trim().uppercase().let { t -> t.takeIf { it in console.tags } ?: phoneTag(console, t) }

    /**
     * The tag whose console-specific behaviour [tag] shares: a GPGX_<X> tag is <X>'s (Master System's pad, Sega CD's
     * BIOS, Game Gear's one player), every other tag itself (uppercase). For tag-keyed tables with no GPGX_<X> row.
     */
    fun baseTag(tag: String): String = tag.trim().uppercase().let { if (it.startsWith(GPGX_PREFIX)) it.removePrefix(GPGX_PREFIX) else it }
    fun consoleByAlias(name: String): Console? = consoleByAlias[name.trim().lowercase()]

    /**
     * The console a folder name mentions: an alias of two or more words or at least four characters counts when it
     * appears as whole contiguous words ("My Sega Genesis" and "My SNES Games" match); a shorter alias ("gb", "ds",
     * "gen", "gba", ...) counts only when it is the whole normalised name once the words roms, rom, games, game, isos
     * and iso are dropped ("GB" and "GBA Roms" match; "GBA Manuals", "My GBA" and "Next Gen Backups" are nothing). The
     * longest alias wins (most words, then most characters), so "Sony Playstation Portable" is psp rather than ps; a
     * tie goes to the earlier console. Null when none matches.
     */
    fun consoleForFolderName(name: String): Console? {
        val words = words(name)
        if (words.isEmpty()) return null
        val coreWords = words.filter { it !in stopWords }
        var best: Console? = null
        var bestWords = 0
        var bestChars = 0
        for ((c, alias) in aliasWords) {
            if (alias.size < bestWords || (alias.size == bestWords && alias.sumOf { it.length } <= bestChars)) continue
            val hit = if (isShort(alias)) coreWords == alias else (0..words.size - alias.size).any { i -> alias.indices.all { words[i + it] == alias[it] } }
            if (hit) {
                best = c; bestWords = alias.size; bestChars = alias.sumOf { it.length }
            }
        }
        return best
    }

    /** Words a short alias may stand beside in a folder name ("GBA Roms", "PSP ISOs"). */
    private val stopWords = setOf("roms", "rom", "games", "game", "isos", "iso")

    /** One word under four characters: too likely to occur by chance inside a longer folder name. */
    private fun isShort(alias: List<String>): Boolean = alias.size == 1 && alias[0].length < 4

    private val wordSplit = Regex("[^a-z0-9]+")
    private fun words(s: String): List<String> = s.lowercase().split(wordSplit).filter { it.isNotEmpty() }
    private val aliasWords: List<Pair<Console, List<String>>> =
        consoles.flatMap { c -> c.aliases.map { c to words(it) } }.filter { it.second.isNotEmpty() }

    /** Consoles that own [ext]; empty for archives, generic extensions and unknown ones. */
    fun consolesForExtension(ext: String): List<Console> {
        val e = ext.lowercase()
        if (e in Names.ARCHIVE_EXTENSIONS || e in GENERIC_EXTENSIONS) return emptyList()
        return consoles.filter { e in it.extensions }
    }

    /** One descriptor per core id, canonical first (a tag view such as SGB's mgba is not listed). */
    val all: List<CoreDescriptor> = byTag.values.distinctBy { it.id }

    /** Every core's BIOS file names ([CoreDescriptor.biosFiles], folders left out), lowercase: never a game in a ROM folder. */
    val biosFileNames: Set<String> =
        all.flatMap { it.biosFiles }.filter { !it.endsWith("/") }.mapTo(HashSet()) { it.substringAfterLast('/').lowercase() }

    /** Every console tag that has a core (the keys of the tag map). */
    val tags: Set<String> = byTag.keys

    fun forTag(tag: String): CoreDescriptor? = byTag[tag.trim().uppercase()]

    /**
     * The players [tag]'s game gets on [core] (the launched one, which may not be the tag's default): the core's
     * [CoreDescriptor.players], except Game Gear, which picodrive and genesis_plus_gx run but has no second pad. 1 without a core.
     */
    fun playersFor(tag: String, core: CoreDescriptor?): Int =
        if (baseTag(tag) == "GG") 1 else core?.players ?: 1

    /** The [tags] whose core takes cheats ([CoreDescriptor.cheats]): Cheats & Patches installs folders for these only. */
    val cheatTags: Set<String> = tags.filterTo(LinkedHashSet()) { forTag(it)?.let { d -> d.cheats || d.ramCheats } != false }

    /** [fileName] is an archive the launched [tag]'s core loads as is (fbneo's and flycast's arcade sets), not one to unpack (minarch's rule). */
    fun loadsArchive(tag: String, fileName: String): Boolean =
        Names.isArchive(fileName) && Names.extensionOf(fileName) in (forTag(tag)?.extensions ?: emptySet())

    /**
     * [loadsArchive], with flycast's set table: flycast loads only an archive named after one of its NAOMI/Atomiswave sets
     * ([NaomiSets.loadsAsIs]); another zip/7z on DC (a zipped disc) is unpacked like any console's archive.
     */
    fun loadsArchive(tag: String, fileName: String, naomiSets: NaomiSets): Boolean =
        loadsArchive(tag, fileName) && (forTag(tag)?.id != "flycast" || naomiSets.loadsAsIs(fileName))

    /**
     * The name the core sees the content under (`/rom/<name>` or the cache copy): [fileName] in lowercase when the core
     * loads the archive as is ([loadsArchive]), since fbneo takes the driver name from the base name and compares it
     * case-sensitively; [fileName] otherwise. Saves, states and config stay keyed by [fileName].
     */
    fun contentName(tag: String, fileName: String): String =
        if (loadsArchive(tag, fileName)) fileName.lowercase() else fileName

    fun forId(id: String): CoreDescriptor? = all.firstOrNull { it.id == id }

    /** "<emulator> (<TAG>)": the name of the emulator behind [tag] with the tag ("mGBA (MGBA)"); the tag alone when no core runs it. */
    fun emulatorLabel(tag: String): String = forTag(tag)?.let { "${it.name} (${handheldTag(tag)})" } ?: tag

    /** The tags whose core id equals [tag]'s, in registry order (FDS -> [FC, FDS]); empty for a tag with no core. */
    fun tagsSharingCore(tag: String): List<String> {
        val id = forTag(tag)?.id ?: return emptyList()
        return byTag.filterValues { it.id == id }.keys.toList()
    }

    private val segaCdBios = listOf("bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin")
    private val segaCdDiscs = BiosNeed(segaCdBios, setOf("cue", "chd", "iso", "m3u"))
    private val requiredBiosByTag: Map<String, List<BiosNeed>> = mapOf(
        "FDS" to listOf(BiosNeed(listOf("disksys.rom"))),
        "FC" to listOf(BiosNeed(listOf("disksys.rom"), setOf("fds"))),   // a .fds file kept in a Famicom folder runs on the same core
        "SEGACD" to listOf(BiosNeed(segaCdBios)),
        "GPGX" to listOf(segaCdDiscs),
        "MD" to listOf(segaCdDiscs),
        "PCE" to listOf(BiosNeed(listOf("syscard3.pce"), setOf("cue", "ccd", "chd", "toc", "m3u"))),
        "COLECO" to listOf(BiosNeed(listOf("colecovision.rom", "coleco.rom"))),
        "DC" to listOf(BiosNeed(listOf("dc_boot.bin"), setOf("chd", "cdi", "gdi", "cue", "m3u"))),   // arcade sets bring their own BIOS
        "PS2" to listOf(BiosNeed(emptyList(), anySuffix = ".bin")),   // the core's scan takes ".bin" only: .nvm / .mec do not count
    )

    /** The BIOS a launch under [tag] cannot do without (checked before the core starts); empty for most tags. */
    fun requiredBios(tag: String): List<BiosNeed> = requiredBiosByTag[baseTag(tag)] ?: emptyList()
}
