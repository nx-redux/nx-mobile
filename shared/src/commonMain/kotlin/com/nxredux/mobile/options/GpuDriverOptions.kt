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

    /** Put on the row of a game whose driver crashed in it ([name] replaces `%s`). */
    const val CRASH_NOTE = "%s crashed in this game; using the system driver."

    /**
     * The row shows for a console with a Vulkan path ([com.nxredux.mobile.runner.CoreDescriptor.vulkan]), Vulkan-only ones
     * included, when the feature is [available].
     */
    fun appliesTo(tag: String, available: Boolean): Boolean = CoreRegistry.forTag(tag)?.vulkan != null && available

    /**
     * [autoVulkan]: Graphics API's Auto is Vulkan here ([GraphicsApiOptions.autoIsVulkan]), so the row shows on Auto too.
     * [vulkanOnly]: the core always runs on Vulkan and has no Graphics API row, so the row always shows.
     */
    fun row(installed: List<GpuDriver>, autoVulkan: Boolean = false, vulkanOnly: Boolean = false): FrontendOption = FrontendOption(
        KEY, "GPU driver", listOf(GpuDriverChoice.DEFAULT, GpuDriverChoice.SYSTEM) + installed.map { it.id }, GpuDriverChoice.DEFAULT,
        "The Vulkan driver this console uses. Default follows Settings → Emulation → GPU driver. Takes effect the next time the game starts.",
        brief = "Applies next launch. Vulkan only",
        visual = false, valueLabels = listOf("Default", "System driver") + installed.map { it.name }, atLaunch = true,
        shownWhen = if (vulkanOnly) null else GraphicsApiOptions.KEY to (if (autoVulkan) setOf(GraphicsApiOptions.VULKAN, GraphicsApiOptions.AUTO) else setOf(GraphicsApiOptions.VULKAN)),
    )
}
