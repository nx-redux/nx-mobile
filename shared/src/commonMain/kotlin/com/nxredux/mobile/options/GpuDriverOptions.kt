package com.nxredux.mobile.options

import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.gpudriver.GpuDriverChoice
import com.nxredux.mobile.runner.CoreRegistry

/**
 * The Console Settings row choosing which custom Vulkan driver a console (or one game) loads: a sub-row of Graphics API,
 * shown only while that is Vulkan. Read once when the game starts. Default follows the app-wide pick.
 */
object GpuDriverOptions {
    const val KEY = "nx_gpu_driver"

    /** Core ids that never load a custom driver. */
    val EXCLUDED_CORES: Set<String> = emptySet()

    /** Put on the row of a game whose driver crashed in it ([name] replaces `%s`). */
    const val CRASH_NOTE = "%s crashed in this game; using the system driver."

    /** The row shows for a console with a Vulkan path ([GraphicsApiOptions.appliesTo]) when the feature is [available] and its core is not excluded. */
    fun appliesTo(tag: String, available: Boolean): Boolean =
        GraphicsApiOptions.appliesTo(tag) && available && CoreRegistry.forTag(tag)?.id !in EXCLUDED_CORES

    fun row(installed: List<GpuDriver>): FrontendOption = FrontendOption(
        KEY, "GPU driver", listOf(GpuDriverChoice.DEFAULT, GpuDriverChoice.SYSTEM) + installed.map { it.id }, GpuDriverChoice.DEFAULT,
        "The Vulkan driver this console uses. Default follows Settings → System → GPU driver. Takes effect the next time the game starts.",
        brief = "Applies next launch. Vulkan only",
        visual = false, valueLabels = listOf("Default", "System driver") + installed.map { it.name }, atLaunch = true,
        shownWhen = GraphicsApiOptions.KEY to setOf(GraphicsApiOptions.VULKAN),
    )
}
