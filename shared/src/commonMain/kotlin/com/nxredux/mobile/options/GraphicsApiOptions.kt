package com.nxredux.mobile.options

import com.nxredux.mobile.layout.DeviceTraits

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
        "How the console is drawn. Auto uses OpenGL ES.\nPhones without Vulkan stay on OpenGL ES. Takes effect\nthe next time the game starts.",
        brief = "Applies next launch. Auto is OpenGL ES",
        visual = false, valueLabels = listOf("Auto", "OpenGL ES", "Vulkan"), atLaunch = true,
    )

    /** [row] where Auto is Vulkan ([autoIsVulkan]). */
    val rowAutoVulkan = row.copy(
        info = "How the console is drawn. Auto uses Vulkan on this\nphone's GPU, where it runs better than OpenGL ES.\n" +
            "Phones without Vulkan stay on OpenGL ES. Takes effect\nthe next time the game starts.",
        brief = "Applies next launch. Auto is Vulkan",
    )

    /** On N64 only, after the row's info: Vulkan there is another renderer, not the same picture faster. */
    const val N64_NOTE = "Vulkan on N64 switches to the ParaLLEl-RDP renderer\n(more accurate, heavier)."

    /** On a Mali GPU, after the row's info: the system driver is what loses the device, so the fix is another driver. */
    const val MALI_NOTE = "On a Mali GPU the system Vulkan driver can crash\nsome games: a custom GPU driver (the GPU driver\nrow) usually fixes it."

    /** Where custom drivers are unavailable, so the way out is OpenGL ES. */
    const val MALI_NOTE_NO_DRIVERS = "On a Mali GPU the system Vulkan driver can crash\nsome games: set OpenGL ES for them."

    /**
     * [row], or [rowAutoVulkan] where [autoVulkan]; on [n64] with [N64_NOTE] after its info; on a [mali] GPU with
     * [MALI_NOTE] last ([MALI_NOTE_NO_DRIVERS] without [drivers], custom GPU drivers being unavailable).
     */
    fun rowFor(autoVulkan: Boolean, mali: Boolean, drivers: Boolean, n64: Boolean = false): FrontendOption {
        val base = if (autoVulkan) rowAutoVulkan else row
        val notes = listOfNotNull(N64_NOTE.takeIf { n64 }, (if (drivers) MALI_NOTE else MALI_NOTE_NO_DRIVERS).takeIf { mali })
        return if (notes.isEmpty()) base else base.copy(info = (listOf(base.info) + notes).joinToString("\n"))
    }

    /**
     * The row shows for a console whose core can render with Vulkan ([com.nxredux.mobile.runner.CoreDescriptor.vulkan]),
     * unless Vulkan is all it has ([VulkanSwitch.vulkanOnly]).
     */
    fun appliesTo(tag: String): Boolean = CoreRegistry.forTag(tag)?.vulkan?.let { !it.vulkanOnly } == true

    /**
     * The launch for [value] on a core with [switch]: Vulkan (its options, offered) only when picked and the phone can run
     * the bridge ([vulkanSupported]); else the OpenGL ES options, so a core that asks for Vulkan itself (N64's ParaLLEl,
     * Azahar) never asks on a phone that would refuse it. A core without a Vulkan path gets nothing. [vulkanSupported] is
     * the device probe (a Vulkan instance + device enumeration), so it is asked only when Vulkan is picked on such a core.
     * [vulkanFailed]: Vulkan could not start for this core on this phone, or this launch is the relaunch after it did
     * ([VulkanFallback]): OpenGL ES, as on a phone without Vulkan, and the probe is not asked. A
     * [VulkanSwitch.vulkanOnly] core is offered Vulkan whatever [value] and [vulkanFailed] say, wherever the phone has it
     * ([vulkanRefusal] refuses the rest).
     */
    fun resolve(value: String?, switch: VulkanSwitch?, vulkanSupported: () -> Boolean, vulkanFailed: Boolean = false): GraphicsApiLaunch {
        if (switch == null) return GraphicsApiLaunch(false, emptyMap(), false)
        val vulkan = (switch.vulkanOnly || preferVulkan(value) && !vulkanFailed) && vulkanSupported()
        val env: Map<String, String?> = if (vulkan) switch.vulkanEnv else switch.vulkanEnv.mapValues { null }
        return GraphicsApiLaunch(vulkan, if (vulkan) switch.vulkan else switch.gles, vulkan && switch.swapchainFrames, env)
    }

    /** Why a launch on a [VulkanSwitch.vulkanOnly] core is refused before it loads: the phone has no Vulkan ([supported]). */
    fun vulkanRefusal(switch: VulkanSwitch?, supported: () -> Boolean): String? =
        VULKAN_REFUSAL.takeIf { switch?.vulkanOnly == true && !supported() }

    const val VULKAN_REFUSAL = "Wii U needs Vulkan, which this phone's GPU driver doesn't offer."

    /**
     * Whether the core's [recorded] Vulkan failure ([VulkanFallback]) counts: never on a [VulkanSwitch.vulkanOnly] core,
     * which has no OpenGL ES to go to.
     */
    fun recordedFailure(switch: VulkanSwitch?, recorded: Boolean): Boolean = recorded && switch?.vulkanOnly != true

    /**
     * [launch]'s process environment with the switch's [VulkanSwitch.adrenoSystemDriverEnv]: set on a Vulkan launch on an
     * Adreno [gpu] without a [customDriver], else unset (a launch in the same process after one that set it).
     */
    fun envFor(launch: GraphicsApiLaunch, switch: VulkanSwitch?, customDriver: Boolean, gpu: String?): Map<String, String?> {
        val extra = switch?.adrenoSystemDriverEnv.orEmpty()
        if (extra.isEmpty()) return launch.env
        val on = launch.preferVulkan && !customDriver && DeviceTraits.isAdreno(gpu)
        return launch.env + (if (on) extra else extra.mapValues { null })
    }

    /**
     * Whether Auto is Vulkan for a core with [switch] on the GPU named [gpu] (GL_RENDERER, null: unknown).
     * [customDriver]: a custom GPU driver is in use for the game, which a [VulkanSwitch.maliNeedsCustomDriver] core needs.
     */
    fun autoIsVulkan(switch: VulkanSwitch?, gpu: String?, customDriver: Boolean = false): Boolean = switch != null &&
        (switch.autoOnXclipse && DeviceTraits.isXclipse(gpu) ||
            switch.autoOnMali && DeviceTraits.isMali(gpu) && (!switch.maliNeedsCustomDriver || customDriver))

    /**
     * The value a launch reads for the stored [value]: Vulkan in place of Auto (or no value) where [autoIsVulkan], else
     * [value] itself. [gpu] is asked only for Auto on a core that follows the GPU. Always Vulkan on a
     * [VulkanSwitch.vulkanOnly] core, so its GPU driver loads as on any Vulkan launch.
     */
    inline fun effective(value: String?, switch: VulkanSwitch?, customDriver: Boolean = false, gpu: () -> String?): String? =
        if (switch?.vulkanOnly == true) VULKAN
        else if ((value == null || value == AUTO) && switch?.followsGpu == true && autoIsVulkan(switch, gpu(), customDriver)) VULKAN else value

    /** Whether the runner offers the core Vulkan: only for the Vulkan value ([effective] turns Auto into it where it should be). */
    fun preferVulkan(value: String?): Boolean = value == VULKAN
}
