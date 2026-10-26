package com.nxredux.mobile.runner

enum class LoadMode { FILE_DESCRIPTOR, REAL_PATH }

/**
 * How a core switches between OpenGL ES and Vulkan (Console Settings → Graphics API): the core options forced for each,
 * whether they are locked in Emulator Settings, and whether it renders into a swapchain on the bridge's surface (Dolphin).
 */
data class VulkanSwitch(
    val gles: Map<String, String> = emptyMap(),
    val vulkan: Map<String, String> = emptyMap(),
    val lock: Boolean = true,
    val swapchainFrames: Boolean = false,
    /** Process environment the core reads on Vulkan (set before it loads; cleared on any other launch). */
    val vulkanEnv: Map<String, String> = emptyMap(),
)

data class CoreDescriptor(
    /** minarch's core name (the handheld `.so` basename cut at its last `_`); keys the state/config dirs. */
    val id: String,
    /** The core's file in jniLibs: lib-prefixed, since Android 14 and older extract only lib*.so at install. */
    val libraryFileName: String,
    val extensions: Set<String>,
    val loadMode: LoadMode = LoadMode.FILE_DESCRIPTOR,
    val defaultOptions: Map<String, String> = emptyMap(),
    /** System-directory files or folders the core reads, relative to the system dir. Informational; required ones are [CoreRegistry.requiredBios]. */
    val biosFiles: List<String> = emptyList(),
    /** Options the core only applies at game start although its labels don't say so; hidden from the in-game menu. */
    val restartOnlyOptions: Set<String> = emptySet(),
    /**
     * The libretro device set on port 0 after the game loads and before its first frame (a core such as flycast plugs
     * no pad in otherwise); null keeps the core's default.
     */
    val controllerPort0: Int? = null,
    /**
     * How many ports the frontend sets when [controllerPort0] is non-null (ignored otherwise): those past the session's
     * players get `RETRO_DEVICE_NONE` ([controllerPortsFor]). flycast finishes its device setup (input descriptors, the
     * VMU slot options) only once all four of its ports are set, as RetroArch does.
     */
    val controllerPortCount: Int = 1,
    /**
     * The players its native controller ports give (local multiplayer, one physical pad each; slot N = port N). No
     * multitap or adapter: pce_fast's is always emulated, so it counts. 1 = a one-player core. See [CoreRegistry.playersFor].
     */
    val players: Int = 1,
    /**
     * Keys of [defaultOptions] the app fixes: hidden from the in-game Core Options page, and a value for them in a `.cfg`
     * is ignored ([com.nxredux.mobile.options.CoreConfig.resolve]'s `forced`), e.g. melonDS DS's layout keys, so nothing
     * fights the frontend's DS screen layouts.
     */
    val lockedOptions: Set<String> = emptySet(),
    /**
     * Single values the app never offers, per option key: skipped when cycling the in-game Core Options page, and a stored
     * one in a `.cfg` is ignored ([com.nxredux.mobile.options.CoreConfig.resolve]'s `hidden`). Values are the core's exact strings.
     * Every key needs a [defaultOptions] entry with a value that is not hidden, so the core's own default never slips through.
     */
    val hiddenValues: Map<String, Set<String>> = emptyMap(),
    /**
     * False when the core ignores retro_cheat_set (fbneo, flycast, gearcoleco, ...): code cheats do nothing there, and
     * the in-game menu has no Cheats row unless [ramCheats].
     */
    val cheats: Boolean = true,
    /**
     * For a core with no [cheats]: its database files hold RetroArch RAM patches (handler 1), which the frontend applies
     * to the core's system RAM, so its Cheats row lists those and the Cheat Database installs its folder.
     */
    val ramCheats: Boolean = false,
    /** The system-dir subfolder the core reads its BIOS from (flycast's `<system>/dc/`); empty = the system dir itself. */
    val biosSubdir: String = "",
    /**
     * The core names save files after the content file it is given (flycast's `<content>.A1.bin` VMU): a ROM unpacked
     * from an archive is then named after the archive ([com.nxredux.mobile.library.ArchiveEntries.unpackedName]), so its
     * saves carry the game's stem. Other cores keep the inner file's own name.
     */
    val savesNamedAfterContent: Boolean = false,
    /**
     * The runner never unloads the core's library once it has run a game, so the next game on it finds the core at the
     * same address. gpsp maps its 10.5 MB recompiler cache within 128 MB of its own code once and reuses it; reloaded
     * elsewhere in a busy process it may find no room and cannot start a game.
     */
    val keepLoaded: Boolean = false,
    /**
     * Options whose probed values describe the probe machine, not the core (melonDS DS's firmware and NAND paths, its
     * network interface): never shown or written outside a game ([com.nxredux.mobile.options.EmulatorSettings.page]).
     */
    val offlineHiddenOptions: Set<String> = emptySet(),
    /** How the core switches API under Console Settings → Graphics API; null = no Vulkan path, the console has no row. */
    val vulkan: VulkanSwitch? = null,
    /**
     * Whether the core can rewind (libretro docs feature table, checked 2026-10-24). False for azahar, flycast, ppsspp and
     * prboom, which don't support it, and Dolphin, whose states are too large to snapshot every frame on a phone.
     */
    val rewind: Boolean = true,
    /** The core reads the libretro mic interface; the app shows the Mic toggle and offers the interface. */
    val microphone: Boolean = false,
    /**
     * The emulator's name as the UI shows it (the core's retro_get_system_info library_name: "Genesis Plus GX", "gpSP").
     * One source for Emulator Settings and Default emulators; the id when unset.
     */
    val name: String = id,
) {
    /**
     * port -> libretro device, in port order, set after load and before the first frame; empty keeps the core's
     * defaults. Ports below [players] (the session's player count) get [controllerPort0]'s device, so player 2+ is
     * plugged in (flycast starts its ports empty; pcsx_rearmed's are digital); the rest up to [controllerPortCount] get NONE.
     */
    fun controllerPortsFor(players: Int): Map<Int, Int> {
        val pad = controllerPort0 ?: return emptyMap()
        return (0 until maxOf(controllerPortCount, players)).associateWith { if (it < players) pad else NONE }
    }

    companion object {
        /** `RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 1)`: pcsx_rearmed's DualShock (`frontend/libretro.c:869`). */
        const val PS_DUALSHOCK = 517
        /**
         * `RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 0)`: SwanStation's DualShock (`libretro_host_interface.cpp:207`).
         * Its 517 is the Analog Joystick (flight stick), which starts in analog mode and which most games ignore.
         */
        const val SWANSTATION_DUALSHOCK = 261
        /**
         * `RETRO_DEVICE_JOYPAD`: flycast's Dreamcast controller on port 1. flycast's libretro port state (`device_type[]`)
         * starts at -1, so it plugs no pad in until the frontend sets the port.
         */
        const val JOYPAD = 1
        /** Dolphin's `RETRO_DEVICE_WIIMOTE_SW` (`(2 << 8) | RETRO_DEVICE_JOYPAD`, DolphinLibretro/Input.cpp): a sideways Wiimote. */
        const val WIIMOTE_SIDEWAYS = (2 shl 8) or JOYPAD
        /** `RETRO_DEVICE_NONE`: nothing plugged in. */
        const val NONE = 0
    }
}
