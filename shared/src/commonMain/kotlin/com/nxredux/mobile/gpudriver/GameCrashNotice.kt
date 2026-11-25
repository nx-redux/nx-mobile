package com.nxredux.mobile.gpudriver

import com.nxredux.mobile.layout.DeviceTraits

/**
 * What the menu says when the game process crashed. A Mali's system Vulkan driver loses the device in some games, so a
 * crash on it points at the fix (a custom driver, or OpenGL ES) rather than only saying it happened. The launch records
 * whether it ran there ([onMaliSystemVulkan]); each launch overwrites it, so it always describes the game that ended.
 */
object GameCrashNotice {
    const val GENERIC = "The game stopped unexpectedly. It will continue from its last auto-save."
    const val MALI_VULKAN = "The game stopped unexpectedly. On a Mali GPU the system Vulkan driver can't run some games: " +
        "add a custom GPU driver (Settings → Emulation → GPU driver) or set Graphics API to OpenGL ES for this game. " +
        "It will continue from its last auto-save."
    const val MALI_VULKAN_NO_DRIVERS = "The game stopped unexpectedly. On a Mali GPU the system Vulkan driver can't run " +
        "some games: set Graphics API to OpenGL ES for this game. It will continue from its last auto-save."

    /** Whether a launch runs on Mali's system Vulkan driver: on [vulkan], without a [customDriver], on a Mali [gpu]. */
    fun onMaliSystemVulkan(vulkan: Boolean, customDriver: Boolean, gpu: String?): Boolean =
        vulkan && !customDriver && DeviceTraits.isMali(gpu)

    /**
     * The notice for a game that [crashed] (null when it did not), launched [onMaliSystemVulkan]. [driversAvailable]: custom
     * GPU drivers can be added on this phone, else only OpenGL ES is offered.
     */
    fun notice(crashed: Boolean, onMaliSystemVulkan: Boolean, driversAvailable: Boolean): String? = when {
        !crashed -> null
        !onMaliSystemVulkan -> GENERIC
        driversAvailable -> MALI_VULKAN
        else -> MALI_VULKAN_NO_DRIVERS
    }
}
