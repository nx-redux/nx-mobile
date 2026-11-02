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
 * - MD.pak, SEGACD.pak, 32X.pak: no `picodrive_input*`, so picodrive's own default, `3 button pad`
 *   (`assets/core-options/picodrive.json`, both inputs).
 * - GPGX.pak: no `minarch_gamepad_type`, so minarch's `set_controller_port_device(0/1, RETRO_DEVICE_JOYPAD)` after load
 *   (`workspace/all/minarch/ma_core.c:298-299`) stands: genesis_plus_gx's auto pad (2B/3B/6B from the cart header).
 *   A phone-only genesis_plus_gx session on MD/SMS/GG/SG1000/SEGACD gets the same (no pak runs it there; both phones agree).
 * - A picodrive session on GPGX (the joiner's alternative core) gets picodrive's default, `3 button pad`.
 * - SFC, SUPA, FC, FDS, FBN: no pad-type option in the paks or the cores' option lists (snes9x, supafaust, fceumm, fbneo):
 *   null, the launch is unchanged. PS: `minarch_gamepad_type = 1` (DualShock, 517 on both ports), already the
 *   descriptor's `controllerPortsFor(2)`: null here.
 */
object NetplayPads {
    /** [options]: core options forced for the session; [ports]: port -> libretro device; [pad]: what the on-screen pad draws (null = the header's). */
    data class Pad(val options: Map<String, String>, val ports: Map<Int, Int>, val pad: SegaPad?)

    private const val THREE = "3 button pad"
    private val PICODRIVE_3B = Pad(
        mapOf(SegaPadType.PICODRIVE_INPUT1 to THREE, SegaPadType.PICODRIVE_INPUT2 to THREE), emptyMap(), SegaPad.THREE,
    )
    private val GPGX_AUTO = Pad(emptyMap(), mapOf(0 to CoreDescriptor.JOYPAD, 1 to CoreDescriptor.JOYPAD), null)

    /** tag -> core -> pad. */
    private val table: Map<String, Map<String, Pad>> = mapOf(
        "MD" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),        // core default
        "SEGACD" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),    // core default
        "32X" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B),                                       // core default
        "SMS" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),       // pak: 3 button pad
        "GG" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),        // pak: 3 button pad
        "SG1000" to mapOf(SegaPadType.PICODRIVE to PICODRIVE_3B, SegaPadType.GPGX to GPGX_AUTO),    // pak: 3 button pad
        "GPGX" to mapOf(SegaPadType.GPGX to GPGX_AUTO, SegaPadType.PICODRIVE to PICODRIVE_3B),      // minarch JOYPAD
    )

    /**
     * The session's pad for [tag] run on [core]; null = nothing to force (the launch's own ports and options stand). A
     * GPGX_<X> tag reads <X>'s row ([CoreRegistry.baseTag]), whose genesis_plus_gx entry is the GPGX pak's.
     */
    fun forSession(tag: String, core: String?): Pad? = core?.let { table[CoreRegistry.baseTag(tag)]?.get(it) }
}
