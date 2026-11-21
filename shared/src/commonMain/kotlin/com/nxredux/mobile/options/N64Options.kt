package com.nxredux.mobile.options

import com.nxredux.mobile.runner.N64Resolution
import com.nxredux.mobile.runner.Res3dConsole
import kotlin.math.ceil

/** Nintendo 64 settings the app owns on Mupen64Plus-Next: the 3D Resolution rows. */
object N64Options {
    const val NATIVE_FACTOR_KEY = "mupen64plus-EnableNativeResFactor"
    const val WIDE_SIZE_KEY = "mupen64plus-169screensize"
    private const val ASPECT_KEY = "mupen64plus-aspect"

    /** The core's own defaults, in every saved config: they say nothing about a player's pick. */
    private const val DEFAULT_SIZE = "640x480"
    private const val DEFAULT_WIDE_SIZE = "960x540"

    fun appliesTo(tag: String): Boolean = Res3dConsole.forTag(tag) == Res3dConsole.N64

    /** The 3D Resolution rows (mode, Max Scale, Scale), as the other consoles' ones. */
    fun resolutionRows(displayMax: Int?): List<FrontendOption> = resolutionRows(Res3dConsole.N64, displayMax)

    /**
     * Max Scale from a game or console config written before the app set the N64's resolution, so a player who raised it
     * keeps that sharpness: a Native Resolution Factor above 1x, else the 4:3 Resolution (the Wide one for a wide aspect)
     * when it isn't the core's default, its height over 240 lines rounded up. Null when there is none to carry or the row
     * is already set.
     */
    fun carriedMaxScale(values: Map<String, String>): String? {
        val n64 = Res3dConsole.N64
        if (values[n64.maxScaleKey] != null) return null
        val n = values[NATIVE_FACTOR_KEY]?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: run {
            val wide = values[ASPECT_KEY]?.trim()?.startsWith("16:9") == true
            val size = (if (wide) values[WIDE_SIZE_KEY] else values[n64.coreKey])?.trim()
                ?.takeIf { it != (if (wide) DEFAULT_WIDE_SIZE else DEFAULT_SIZE) } ?: return null
            val height = size.substringAfter('x', "").trim().toIntOrNull() ?: return null
            ceil(height.toDouble() / N64Resolution.NATIVE_H).toInt()
        }
        return n.takeIf { it > 1 }?.coerceAtMost(n64.coreMax)?.toString()
    }
}
