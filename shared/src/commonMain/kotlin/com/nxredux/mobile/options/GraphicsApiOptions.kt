package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.VulkanSwitch

/** What a launch gives the runner for Graphics API: whether to offer Vulkan, the core options to force, the swapchain bound. */
data class GraphicsApiLaunch(
    val preferVulkan: Boolean,
    val forcedOptions: Map<String, String>,
    val swapchainFrames: Boolean,
    /** Process environment to set before the core loads; a null value unsets (a GLES launch after a Vulkan one). */
    val env: Map<String, String?> = emptyMap(),
)

/**
 * The Console Settings row choosing how a Vulkan-capable core renders: OpenGL ES, or Vulkan with each frame bridged into
 * the GLES presentation (libretrodroid VulkanBridge). Read once when the game starts. Only consoles whose core
 * describes a Vulkan path ([com.nxredux.mobile.runner.CoreDescriptor.vulkan]) get the row.
 */
object GraphicsApiOptions {
    const val KEY = "nx_graphics_api"
    const val AUTO = "auto"
    const val GLES = "gles"
    const val VULKAN = "vulkan"

    val row = FrontendOption(
        KEY, "Graphics API", listOf(AUTO, GLES, VULKAN), AUTO,
        "How the console is drawn. Auto uses OpenGL ES.\nVulkan on N64 switches to the ParaLLEl-RDP renderer\n" +
            "(more accurate, heavier). Phones without Vulkan\nstay on OpenGL ES. Takes effect the next time the game starts.",
        brief = "Applies next launch. Auto is OpenGL ES",
        visual = false, valueLabels = listOf("Auto", "OpenGL ES", "Vulkan"), atLaunch = true,
    )

    /** The row shows for a console whose core can render with Vulkan ([com.nxredux.mobile.runner.CoreDescriptor.vulkan]). */
    fun appliesTo(tag: String): Boolean = CoreRegistry.forTag(tag)?.vulkan != null

    /**
     * The launch for [value] on a core with [switch]: Vulkan (its options, offered) only when picked and the phone can run
     * the bridge ([vulkanSupported]); else the OpenGL ES options, so a core that asks for Vulkan itself (N64's ParaLLEl,
     * Azahar) never asks on a phone that would refuse it. A core without a Vulkan path gets nothing. [vulkanSupported] is
     * the device probe (a Vulkan instance + device enumeration), so it is asked only when Vulkan is picked on such a core.
     * [vulkanFailed]: Vulkan could not start for this core on this phone, or this launch is the relaunch after it did
     * ([VulkanFallback]): OpenGL ES, as on a phone without Vulkan, and the probe is not asked.
     */
    fun resolve(value: String?, switch: VulkanSwitch?, vulkanSupported: () -> Boolean, vulkanFailed: Boolean = false): GraphicsApiLaunch {
        if (switch == null) return GraphicsApiLaunch(false, emptyMap(), false)
        val vulkan = preferVulkan(value) && !vulkanFailed && vulkanSupported()
        val env: Map<String, String?> = if (vulkan) switch.vulkanEnv else switch.vulkanEnv.mapValues { null }
        return GraphicsApiLaunch(vulkan, if (vulkan) switch.vulkan else switch.gles, vulkan && switch.swapchainFrames, env)
    }

    /** Whether the runner offers the core Vulkan: only when picked. Auto stays on GLES, as fast or faster for Dolphin. */
    fun preferVulkan(value: String?): Boolean = value == VULKAN
}
