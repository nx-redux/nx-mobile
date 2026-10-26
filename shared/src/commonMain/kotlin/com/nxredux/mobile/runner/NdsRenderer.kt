package com.nxredux.mobile.runner

/**
 * melonDS DS's renderer: GPU by default where the device has GLES 3.2, which the core's GL renderer needs. The default
 * is a load-time one, lowest priority (a stored value wins) and never written.
 */
object NdsRenderer {
    const val CORE_ID_MELONDSDS = "melondsds"
    const val RENDER_MODE = "melonds_render_mode"
    const val OPENGL = "opengl"
    const val SOFTWARE = "software"
    /** `ConfigurationInfo.reqGlEsVersion` encoding: major in the high 16 bits. */
    const val GLES_3_2 = 0x30002

    /**
     * Core options the app keeps from the running core: changed in the menu they are saved and apply at the next launch
     * (melonDS DS would switch renderer live through SET_SYSTEM_AV_INFO, which the runner can't serve for a hardware
     * context). Unlike restart-only options they stay visible, so the renderer keeps its per-game override.
     */
    val APPLY_ON_RESTART: Set<String> = setOf(RENDER_MODE)

    fun defaults(glesVersion: Int): Map<String, String> = mapOf(RENDER_MODE to if (glesVersion >= GLES_3_2) OPENGL else SOFTWARE)

    /** OpenGL was asked for but the core's frames are software ones: melonDS DS fell back (it can't start its GL renderer). */
    fun showFallbackNotice(renderMode: String?, hardwareRendered: Boolean): Boolean = renderMode == OPENGL && !hardwareRendered

    /**
     * The running game draws in software: it started in a mode other than OpenGL, or OpenGL [fellBack] ([showFallbackNotice]).
     * A Render Mode changed since applies at the next launch, so it does not count.
     */
    fun rendersSoftware(renderMode: String?, fellBack: Boolean): Boolean = renderMode != OPENGL || fellBack
}
