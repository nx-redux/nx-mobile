package com.nxredux.mobile.options

import com.nxredux.mobile.runner.Res3dConsole

/** GameCube and Wii settings the app owns that both consoles share (Dolphin is one core for both). */
object DolphinOptions {
    fun appliesTo(tag: String): Boolean = Res3dConsole.forTag(tag) == Res3dConsole.DOLPHIN

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the DS, 3DS, PS2 and PSP ones. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.DOLPHIN, displayMax)

    /**
     * Max Scale from a game or console config written before the app set Dolphin's resolution: its Internal Resolution
     * above 1x becomes the most Auto draws at, so a player who raised it keeps that sharpness and phones that cannot keep
     * up still get less. Null when there is none to carry or the row is already set.
     */
    fun carriedMaxScale(values: Map<String, String>): String? {
        if (values[Res3dConsole.DOLPHIN.maxScaleKey] != null) return null
        val n = values[Res3dConsole.DOLPHIN.coreKey]?.toIntOrNull() ?: return null
        return n.takeIf { it > 1 }?.coerceAtMost(Res3dConsole.DOLPHIN.coreMax)?.toString()
    }
}
