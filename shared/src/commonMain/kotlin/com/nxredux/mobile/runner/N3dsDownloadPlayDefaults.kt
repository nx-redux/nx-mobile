package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.options.GpuDriverOptions
import com.nxredux.mobile.options.GraphicsApiOptions

/**
 * The 3DS Download Play row's defaults, under any value the player set: a guest slower than full speed pulls the race out
 * of sync with the host. On a Mali GPU with a custom Vulkan driver, Vulkan with that driver; on Mali without one, the 3D
 * at its native 1x (OpenGL ES at 2x ran ~52 fps there); elsewhere the console's own settings.
 */
fun n3dsDownloadPlayDefaults(gpu: String?, installedDrivers: List<String>): Map<String, String> = when {
    !DeviceTraits.isMali(gpu) -> emptyMap()
    installedDrivers.isNotEmpty() -> mapOf(GraphicsApiOptions.KEY to GraphicsApiOptions.VULKAN, GpuDriverOptions.KEY to installedDrivers.first())
    else -> mapOf(Res3dConsole.N3DS.modeKey to Nds3dMode.PERFORMANCE.value, Res3dConsole.N3DS.scaleKey to "1")
}

/** Why the row can't start: its room is the automatic local wireless one, so Local Wireless has to be on. */
fun n3dsDownloadPlayRefusal(localWireless: Boolean): String? =
    if (localWireless) null else "3DS Download Play needs Local Wireless on (Game Settings → Multiplayer)"
