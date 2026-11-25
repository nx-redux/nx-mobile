package com.nxredux.mobile.options

import com.nxredux.mobile.runner.Res3dConsole

/** PlayStation settings the app owns: the 3D Resolution rows on SwanStation (the PSX tag) and PCSX-ReARMed (PS). */
object Ps1Options {
    /** The PlayStation core's rows for a system [tag]; null for any other console. */
    fun consoleFor(tag: String): Res3dConsole? = Res3dConsole.forTag(tag)?.takeIf { it == Res3dConsole.PS1 || it == Res3dConsole.PCSX }

    fun appliesTo(tag: String): Boolean = consoleFor(tag) != null

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the other consoles' ones. */
    fun resolutionRows(tag: String, displayMax: Int?): List<FrontendOption> =
        consoleFor(tag)?.let { resolutionRows(it, displayMax) }.orEmpty()

    /** Every PlayStation core's rows, for the table of all keys. */
    val allResolutionRows: List<FrontendOption>
        get() = resolutionRows(Res3dConsole.PS1, null) + resolutionRows(Res3dConsole.PCSX, null)

    /**
     * Max Scale from a game or console config written before the app set the core's resolution, so a player who raised it
     * keeps that sharpness: SwanStation's Internal Resolution Scale above 1x, or PCSX-ReARMed's Enhanced Resolution on (2x).
     * Null when there is none to carry or the row is already set.
     */
    fun carriedMaxScale(values: Map<String, String>): Map<String, String> = buildMap {
        val ps1 = Res3dConsole.PS1
        if (values[ps1.maxScaleKey] == null) {
            values[ps1.coreKey]?.toIntOrNull()?.takeIf { it > 1 }?.let { put(ps1.maxScaleKey, it.coerceAtMost(ps1.coreMax).toString()) }
        }
        val pcsx = Res3dConsole.PCSX
        if (values[pcsx.maxScaleKey] == null && values[pcsx.coreKey]?.trim() == "enabled") put(pcsx.maxScaleKey, "2")
    }
}
