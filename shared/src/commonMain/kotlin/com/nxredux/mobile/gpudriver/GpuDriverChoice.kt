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

    /** The toast of [Next.SYSTEM_DRIVER]'s relaunch; the driver's name replaces `%s`. */
    const val SYSTEM_DRIVER_NOTICE = "%s couldn't start, so this game is using the system driver."

    /** The toast of [Next.GLES_NO_RECORD]'s relaunch; the driver's name replaces `%s`. */
    const val GLES_NOTICE = "%s couldn't start, so this game is using OpenGL ES."

    /** [Next.FAIL_MESSAGE] when a custom driver was in use; the driver's name replaces `%s`. */
    const val NETPLAY_FAIL = "%s could not start for this game. Set Display → GPU driver to System driver."

    /** [template] with the driver's [name] in place of `%s` (a name may hold a `%`, so no format string). */
    fun named(template: String, name: String): String = template.replace("%s", name)

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

    /**
     * The launch after the native preflight of [launch]'s driver (named [driverName]), run before any core is loaded.
     * [launch] is unchanged when there is no driver or [preflightOk]. Otherwise this same launch goes on without the
     * driver: on the system driver with [SYSTEM_DRIVER_NOTICE], or on OpenGL ES with [GLES_NOTICE] when the core's
     * system-driver Vulkan already failed here ([coreFailedOnSystem]): what [afterStartFailure] would relaunch into, with
     * no relaunch and nothing recorded. [clearMarker]: the launch's crash marker, written before the preflight, goes.
     */
    data class Preflight(val launch: Launch, val notice: String?, val clearMarker: Boolean)

    fun afterPreflight(launch: Launch, preflightOk: Boolean, driverName: String, coreFailedOnSystem: Boolean): Preflight = when {
        launch.driverId == null || preflightOk -> Preflight(launch, null, false)
        coreFailedOnSystem -> Preflight(Launch(null, true), named(GLES_NOTICE, driverName), true)
        else -> Preflight(Launch(null, launch.vulkanFailed), named(SYSTEM_DRIVER_NOTICE, driverName), true)
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
