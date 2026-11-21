package com.nxredux.mobile.options

import com.nxredux.mobile.runner.DcResolution
import com.nxredux.mobile.runner.Res3dConsole
import kotlin.math.ceil

/** Dreamcast settings the app owns on Flycast: the 3D Resolution rows. */
object DcOptions {
    fun appliesTo(tag: String): Boolean = Res3dConsole.forTag(tag) == Res3dConsole.DC

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the other consoles' ones. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.DC, displayMax)

    /**
     * Max Scale from a game or console config written before the app set Flycast's resolution: its Internal Resolution
     * above native ("1280x960", or a part-way "960x720" rounded up) becomes the most Auto draws at, so a player who raised
     * it keeps that sharpness. Null when there is none to carry or the row is already set.
     */
    fun carriedMaxScale(values: Map<String, String>): String? {
        val dc = Res3dConsole.DC
        if (values[dc.maxScaleKey] != null) return null
        val height = values[dc.coreKey]?.substringAfter('x', "")?.trim()?.toIntOrNull() ?: return null
        val n = ceil(height.toDouble() / DcResolution.NATIVE_H).toInt()
        return n.takeIf { it > 1 }?.coerceAtMost(dc.coreMax)?.toString()
    }
}
