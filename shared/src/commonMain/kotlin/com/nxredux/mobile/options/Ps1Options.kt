package com.nxredux.mobile.options

import com.nxredux.mobile.runner.Res3dConsole

/** PlayStation settings the app owns on SwanStation (the PSX tag); PCSX-ReARMed (PS) draws at native size. */
object Ps1Options {
    fun appliesTo(tag: String): Boolean = Res3dConsole.forTag(tag) == Res3dConsole.PS1

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the other consoles' ones. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.PS1, displayMax)

    /**
     * Max Scale from a game or console config written before the app set SwanStation's resolution: its Internal Resolution
     * Scale above 1x becomes the most Auto draws at. Null when there is none to carry or the row is already set.
     */
    fun carriedMaxScale(values: Map<String, String>): String? {
        if (values[Res3dConsole.PS1.maxScaleKey] != null) return null
        val n = values[Res3dConsole.PS1.coreKey]?.toIntOrNull() ?: return null
        return n.takeIf { it > 1 }?.coerceAtMost(Res3dConsole.PS1.coreMax)?.toString()
    }
}
