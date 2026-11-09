package com.nxredux.mobile.netplay

import com.nxredux.mobile.runner.CoreDescriptor
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.SegaPad
import com.nxredux.mobile.runner.SegaPadType

/**
 * The controller a netplay session gives the core on ports 0-1, exactly what the handheld runs for the same tag and core,
 * so a game that probes the pad takes the same path on both devices (a 6-button probe against a 3-button pad desyncs).
 * Single player never reads this: it keeps Controls → Controller Type ([SegaPadType]).
 *
 * Sources, nx-redux origin/main aa85f72c, `skeleton/SYSTEM/tg5040/paks/Emus/<TAG>.pak/default.cfg` and `launch.sh`:
 * - SMS.pak, GG.pak, SG1000.pak: `-picodrive_input1 = 3 button pad`, `-picodrive_input2 = 3 button pad`.
 * - MD.pak, SEGACD.pak, 32X.pak: no `picodrive_input*`, so picodrive's own default, `3 button pad`.
 * picodrive since 2026 takes its pad as the port device instead of those options: its 3-button pad on ports 0-1 here.
 * - GPGX.pak: no `minarch_gamepad_type`, so minarch's `set_controller_port_device(0/1, RETRO_DEVICE_JOYPAD)` after load
 *   (`workspace/all/minarch/ma_core.c:298-299`) stands: genesis_plus_gx's auto pad (2B/3B/6B from the cart header).
 *   A phone-only genesis_plus_gx session on MD/SMS/GG/SG1000/SEGACD gets the same (no pak runs it there; both phones agree).
 * - A picodrive session on GPGX (the joiner's alternative core) gets picodrive's default, `3 button pad`.
 * - SFC, SUPA, FC, FDS, FBN: no pad-type option in the paks or the cores' option lists (snes9x, supafaust, fceumm, fbneo):
 *   null, the launch is unchanged. PS: `minarch_gamepad_type = 1` (DualShock, 517 on both ports), already the
 *   descriptor's `controllerPortsFor(2)`: null here.
 * - N64: minarch forces its own option set for a session (`Config_forceNetplayN64`): [N64_LOCKSTEP].
 */
object NetplayPads {
    /** [options]: core options forced for the session; [ports]: port -> libretro device; [pad]: what the on-screen pad draws (null = the header's). */
    data class Pad(val options: Map<String, String>, val ports: Map<Int, Int>, val pad: SegaPad?)

    private val PICODRIVE_3B = Pad(emptyMap(), mapOf(0 to SegaPadType.MDPAD_3B, 1 to SegaPadType.MDPAD_3B), SegaPad.THREE)
    private val GPGX_AUTO = Pad(emptyMap(), mapOf(0 to CoreDescriptor.JOYPAD, 1 to CoreDescriptor.JOYPAD), null)

    /**
     * The handheld's np_n64_options (minarch ma_config.c), value for value: everything that changes the emulation, or how a
     * raw stick or button reaches the game, is the same on both devices. GLideN64's copies to RDRAM put a different image
     * where the game reads it, and its threaded renderer makes that timing-dependent: both off.
     */
    private val N64_LOCKSTEP = Pad(mapOf(
        "mupen64plus-cpucore" to "dynamic_recompiler", "mupen64plus-rsp-plugin" to "hle",
        "mupen64plus-Framerate" to "Original", "mupen64plus-virefresh" to "Auto",
        "mupen64plus-CountPerOp" to "0", "mupen64plus-CountPerOpDenomPot" to "0",
        "mupen64plus-ForceDisableExtraMem" to "False", "mupen64plus-IgnoreTLBExceptions" to "False",
        "mupen64plus-alt-map" to "True", "mupen64plus-astick-deadzone" to "15", "mupen64plus-astick-sensitivity" to "100",
        "mupen64plus-r-cbutton" to "C1", "mupen64plus-l-cbutton" to "C2", "mupen64plus-d-cbutton" to "C3", "mupen64plus-u-cbutton" to "C4",
        "mupen64plus-pak1" to "memory", "mupen64plus-pak2" to "none", "mupen64plus-pak3" to "none", "mupen64plus-pak4" to "none",
        "mupen64plus-EnableCopyColorToRDRAM" to "Off", "mupen64plus-EnableCopyDepthToRDRAM" to "Off",
        "mupen64plus-ThreadedRenderer" to "False",
    ), mapOf(0 to CoreDescriptor.JOYPAD, 1 to CoreDescriptor.JOYPAD), null)

    /** tag -> core -> pad. */
    private val table: Map<String, Map<String, Pad>> = mapOf(
        "MD" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),        // core default
        "SEGACD" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),    // core default
        "32X" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B),                                       // core default
        "SMS" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),       // pak: 3 button pad
        "GG" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),        // pak: 3 button pad
        "SG1000" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),    // pak: 3 button pad
        "GPGX" to mapOf(SegaPadType.GPGX to GPGX_AUTO, SegaPadType.PICODRIVE to PICODRIVE_3B),      // minarch JOYPAD
        "N64" to mapOf("mupen64plus_next" to N64_LOCKSTEP),
    )

    /**
     * The session's pad for [tag] run on [core]; null = nothing to force (the launch's own ports and options stand). A
     * GPGX_<X> tag reads <X>'s row ([CoreRegistry.baseTag]), whose genesis_plus_gx entry is the GPGX pak's.
     */
    fun forSession(tag: String, core: String?): Pad? = core?.let { table[CoreRegistry.baseTag(tag)]?.get(it) }
}
