package com.nxredux.mobile.runner

/**
 * Whether portrait keeps its bottom virtual-pad band. A connected controller drops it so the game gets the full height,
 * except for a clip-on controller (it sits over the band's place on the phone).
 */
object PadBandRule {
    /** Consoles with their own controller portrait layout (`nx_nds_layout_portrait_controller`) for the freed height. */
    val DualScreenTags: Set<String> = setOf("NDS", "3DS")

    /** Clip-on controllers (GameSir Pocket Taco, 8BitDo FlipPad): name fragments [isClipOn] looks for. */
    val ClipOnNames: List<String> = listOf(
        "GameSir-Pocket 1", // GameSir Pocket Taco: InputDevice name observed on hardware
        "pocket taco", // GameSir Pocket Taco: assumed marketing name, not seen on hardware
        "flippad", // 8BitDo FlipPad: assumed name, not seen on hardware
    )

    private fun squash(s: String): String = s.lowercase().filterNot { it == ' ' || it == '-' || it == '_' }

    /** True if the device [name] contains a [ClipOnNames] entry, ignoring case, spaces, hyphens and underscores. */
    fun isClipOn(name: String): Boolean = squash(name).let { n -> ClipOnNames.any { squash(it) in n } }

    /** False only in [portrait] with a controller connected and none of them clip-on. */
    fun keepPadBand(portrait: Boolean, controllerNames: List<String>): Boolean =
        !portrait || controllerNames.isEmpty() || controllerNames.any(::isClipOn)

    /**
     * True when a dual-screen [tag]'s band drops ([keepPadBand] false while in [portrait]): the DS screens then use the
     * controller portrait layout (`nx_nds_layout_portrait_controller`) instead of the normal one.
     */
    fun controllerLayout(tag: String, portrait: Boolean, controllerNames: List<String>): Boolean =
        portrait && tag.trim().uppercase() in DualScreenTags && !keepPadBand(portrait, controllerNames)
}
