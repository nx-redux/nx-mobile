/*
 *     Copyright (C) 2022  Filippo Scognamiglio
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.swordfish.libretrodroid

import android.content.Context

class GLRetroViewData(context: Context) {
    var coreFilePath: String? = null
    var gameFilePath: String? = null
    var gameFileBytes: ByteArray? = null
    var gameVirtualFiles: List<VirtualFile> = listOf()
    var systemDirectory: String = context.filesDir.absolutePath
    var savesDirectory: String = context.filesDir.absolutePath
    var variables: Array<Variable> = arrayOf()
    var saveRAMState: ByteArray? = null

    /** The clock file's bytes, applied after the battery save. */
    var rtcState: ByteArray? = null
    var shader: ShaderConfig = ShaderConfig.Default()
    var viewportAlignment = ViewportAlignment.CENTER
    var rumbleEventsEnabled: Boolean = true
    var preferLowLatencyAudio: Boolean = true
    var skipDuplicateFrames: Boolean = false
    var enableMicrophone: Boolean = false
    var immersiveMode: ImmersiveMode? = null
    /** With Settings → System → Debug log on at launch: log GL driver messages to tag NXGL (GL_KHR_debug callback, else glGetError polling). */
    var glDebugOutput: Boolean = false
    /**
     * Offer the core Vulkan, each frame bridged into the GLES presentation through AHardwareBuffers. Ignored where the
     * device cannot run the bridge: the core then gets GLES.
     */
    var preferVulkan: Boolean = false
    /** With [preferVulkan]: the core renders into a swapchain on the bridge's surface (Dolphin), so frames are bounded by it. */
    var vulkanSwapchainFrames: Boolean = false
    /**
     * Never unload the core's library after this session (RTLD_NODELETE): the next game on the same core reuses it at the
     * same address. For a core that maps memory near its own code once (gpsp's recompiler cache).
     */
    var keepCoreLoaded: Boolean = false
    /**
     * libretro device type per port (e.g. 1 = RETRO_DEVICE_JOYPAD), applied with retro_set_controller_port_device
     * after the game loads and before the first retro_run, so a core that reads its pads at boot sees them.
     */
    var controllerPorts: Map<Int, Int> = emptyMap()
}

enum class ViewportAlignment(val value: Int) {
    CENTER(0),
    TOP(1),
    BOTTOM(2)
}

/** How the picture fills the viewport: FIT keeps the core aspect, INTEGER uses whole multiples of the base size, STRETCH fills it. */
enum class ScaleMode(val value: Int) {
    FIT(LibretroDroid.SCALE_MODE_FIT),
    INTEGER(LibretroDroid.SCALE_MODE_INTEGER),
    STRETCH(LibretroDroid.SCALE_MODE_STRETCH),
}
