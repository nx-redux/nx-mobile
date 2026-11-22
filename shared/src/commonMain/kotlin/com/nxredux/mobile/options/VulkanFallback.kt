package com.nxredux.mobile.options

/**
 * Vulkan that passed the support probe but could not start (the bridge's device setup failed, reported as
 * ERROR_GL_NOT_COMPATIBLE) relaunches the game on OpenGL ES, and the core is remembered on this phone so later launches
 * go straight to OpenGL ES ([GraphicsApiOptions.resolve]'s `vulkanFailed`). The record holds for one app version: an
 * update may have fixed it, so it gets to try Vulkan again.
 *
 * Stored as text (one platform setting, read back as [com.nxredux.mobile.settings.AppSettings.vulkanFailedCores]): the
 * app version on the first line, then one core id per line.
 */
object VulkanFallback {
    /** The toast the relaunch shows. */
    const val NOTICE = "Vulkan couldn't start on this device, so this game is using OpenGL ES."

    /** Put before the Graphics API row's info while the core's failure is recorded. */
    const val NOTE = "Vulkan didn't work on this phone; using OpenGL ES."

    /** The cores recorded in [stored] for [appVersion]; a record from another version counts for nothing. */
    fun failedCores(stored: String?, appVersion: String): Set<String> {
        val lines = stored?.lines().orEmpty()
        if (lines.firstOrNull() != appVersion || appVersion.isEmpty()) return emptySet()
        return lines.drop(1).filter { it.isNotBlank() }.toSet()
    }

    /** [stored] with [coreId] recorded for [appVersion]; another version's cores are dropped. */
    fun record(stored: String?, coreId: String, appVersion: String): String =
        (listOf(appVersion) + (failedCores(stored, appVersion) + coreId).sorted()).joinToString("\n")

    /** [row] with [NOTE] before its info when it is the Graphics API row and [failed]; any other row unchanged. */
    fun noted(row: FrontendOption, failed: Boolean): FrontendOption =
        if (failed && row.key == GraphicsApiOptions.KEY) row.copy(info = NOTE + "\n" + row.info) else row
}
