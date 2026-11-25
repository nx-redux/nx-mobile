package com.nxredux.mobile.ui

import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.gpudriver.GpuDriverChoice

/** Emulation → GPU driver's row label for the phone's own driver, and its value on the Emulation page while it is in use. */
internal const val GPU_SYSTEM_DRIVER = "System driver"

/** The GPU driver row's full description (behind its info icon) and the GPU driver page's note under its title. */
internal const val GPU_DRIVER_INFO =
    "Replaces the phone's Vulkan driver in games set to Vulkan. Drivers are third-party code: install only ones you trust."

/** The GPU driver page's last row: picks a driver zip. */
internal const val GPU_DRIVER_ADD_LABEL = "Add driver"

/** The marked row's value on the GPU driver page. */
internal const val GPU_DRIVER_IN_USE = "In use"

/** "Remove driver"'s question for the driver [name]. */
internal fun gpuDriverRemoveText(name: String): String =
    "Delete $name from this phone? Consoles and games set to it use the app-wide driver instead."

/** An installed driver's options ([gpuDriverOptions]): use it, or remove it (asks first). */
internal const val GPU_DRIVER_USE = "Use this driver"
internal const val GPU_DRIVER_REMOVE = "Remove"

/** What is in use for [chosen] (the app-wide setting): an installed driver, or null for the system driver (unset or gone). */
internal fun gpuDriverInUse(chosen: String, installed: List<GpuDriver>): GpuDriver? = installed.firstOrNull { it.id == chosen }

/**
 * Emulation's GPU driver row: [unavailable] "" none; a reason, the row dimmed with that reason as its value; otherwise the
 * driver in use ([gpuDriverInUse]) by name, or System driver.
 */
internal fun gpuDriverRow(unavailable: String?, chosen: String, installed: () -> List<GpuDriver>): SettingsRow? = when {
    unavailable == "" -> null
    unavailable != null -> SettingsRow(SettingsKind.GPU_DRIVER, "GPU driver", unavailable, enabled = false)
    else -> SettingsRow(SettingsKind.GPU_DRIVER, "GPU driver", gpuDriverInUse(chosen, installed())?.name ?: GPU_SYSTEM_DRIVER)
}

/**
 * The GPU driver page: System driver, then each installed driver (its name, then `driverVersion · author`), the one in
 * use marked ([SettingsRow.current]); then Add driver.
 */
internal fun gpuDriverPageRows(chosen: String, installed: List<GpuDriver>): List<SettingsRow> {
    val inUse = gpuDriverInUse(chosen, installed)?.id ?: GpuDriverChoice.SYSTEM
    return buildList {
        add(SettingsRow(SettingsKind.GPU_DRIVER_ITEM, GPU_SYSTEM_DRIVER, "The phone's own driver", gpuDriver = GpuDriverChoice.SYSTEM, current = inUse == GpuDriverChoice.SYSTEM))
        installed.forEach { d ->
            val line = listOf(d.driverVersion, d.author).filter { it.isNotBlank() }.joinToString(" · ")
            add(SettingsRow(SettingsKind.GPU_DRIVER_ITEM, d.name, line, gpuDriver = d.id, current = inUse == d.id))
        }
        add(SettingsRow(SettingsKind.GPU_DRIVER_ADD, GPU_DRIVER_ADD_LABEL))
    }
}

/** The options of an installed driver's row, in the picker opened by A: use it, remove it. */
internal val gpuDriverOptions: List<String> = listOf(GPU_DRIVER_USE, GPU_DRIVER_REMOVE)

/** The crash records ([GpuDriverCrash]) without [id]'s, null when none are left. */
internal fun gpuCrashesWithout(stored: String?, id: String): String? =
    com.nxredux.mobile.gpudriver.GpuDriverCrash.dropDriver(stored, id).takeIf { it.isNotEmpty() }
