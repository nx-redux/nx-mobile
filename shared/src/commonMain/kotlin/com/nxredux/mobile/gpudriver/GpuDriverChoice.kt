package com.nxredux.mobile.gpudriver

import com.nxredux.mobile.options.GraphicsApiOptions

/**
 * Which custom GPU driver a launch loads and what follows a start failure. A per-console or per-game settings value
 * ([DEFAULT], [SYSTEM] or a driver id) overrides the app-wide pick. Driver ids are opaque strings.
 */
object GpuDriverChoice {
    /** The settings value meaning "follow the app-wide driver". */
    const val DEFAULT = "default"

    /** The settings value meaning "the phone's own driver". */
    const val SYSTEM = "system"

    /**
     * The driver id to load, null for the system driver. [cfgValue] [SYSTEM] is the system driver; an installed id is
     * that driver; anything else (default, unset, an id no longer installed) follows [appWide] when installed. A driver
     * in [crashedForGame] falls back to the system driver.
     */
    fun resolve(cfgValue: String?, appWide: String?, installed: Set<String>, crashedForGame: Set<String>): String? {
        val id = when {
            cfgValue == SYSTEM -> null
            cfgValue != null && cfgValue in installed -> cfgValue
            appWide != null && appWide in installed -> appWide
            else -> null
        }
        return id?.takeIf { it !in crashedForGame }
    }

    /** What a launch loads: the [driverId] (null for the system driver) and the `vulkanFailed` for [GraphicsApiOptions.resolve]. */
    data class Launch(val driverId: String?, val vulkanFailed: Boolean)

    /**
     * The launch for the settings. A driver loads only when the game runs on Vulkan with the feature [available], and not
     * when GLES is forced, the system driver is forced (the retry after a driver failed) or netplay forces GLES. The
     * "Vulkan failed for this core" record ([coreFailedOnSystem]) is about the system driver, so it only applies when no
     * custom driver is loaded. Feeds [GraphicsApiOptions.resolve]'s `vulkanFailed` and the native driver setting.
     */
    fun launch(
        apiValue: String?, cfgValue: String?, appWide: String?, installed: Set<String>, crashedForGame: Set<String>,
        available: Boolean, forceGles: Boolean, forceSystem: Boolean, coreFailedOnSystem: Boolean, netplayForcesGles: Boolean,
    ): Launch {
        val onVulkan = available && !forceGles && !forceSystem && !netplayForcesGles && apiValue == GraphicsApiOptions.VULKAN
        val driverId = if (onVulkan) resolve(cfgValue, appWide, installed, crashedForGame) else null
        return Launch(driverId, forceGles || netplayForcesGles || (driverId == null && coreFailedOnSystem))
    }

    /** What to do after a failed start. */
    enum class Next {
        /** Relaunch on Vulkan with the system driver. */
        SYSTEM_DRIVER,

        /** Relaunch on OpenGL ES without recording the core (the system driver's record already exists). */
        GLES_NO_RECORD,

        /** Relaunch on OpenGL ES and record the core as failed on Vulkan. */
        GLES_RECORD,

        /** Netplay cannot relaunch: end the session with a message. */
        FAIL_MESSAGE,

        /** Show the error. */
        SHOW_ERROR,
    }

    /** After ERROR_GL_NOT_COMPATIBLE. */
    fun afterStartFailure(launchedOnVulkan: Boolean, driverId: String?, coreFailedOnSystem: Boolean, netplaySession: Boolean): Next = when {
        !launchedOnVulkan -> Next.SHOW_ERROR
        netplaySession -> Next.FAIL_MESSAGE
        driverId != null && !coreFailedOnSystem -> Next.SYSTEM_DRIVER
        driverId != null -> Next.GLES_NO_RECORD
        else -> Next.GLES_RECORD
    }
}
