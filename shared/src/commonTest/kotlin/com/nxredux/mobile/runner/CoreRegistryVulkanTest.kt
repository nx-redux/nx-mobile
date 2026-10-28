package com.nxredux.mobile.runner

import com.nxredux.mobile.options.GraphicsApiOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreRegistryVulkanTest {
    @Test fun locked_switches_lock_their_keys_and_default_them_to_gles() {
        for (d in CoreRegistry.all) {
            val s = d.vulkan ?: continue
            if (!s.lock) continue
            for ((k, v) in s.gles) {
                assertTrue(k in d.lockedOptions, "${d.id} $k")
                assertEquals(v, d.defaultOptions[k], "${d.id} $k")
            }
            for (k in s.vulkan.keys) assertTrue(k in d.lockedOptions, "${d.id} $k")
        }
    }

    @Test fun ppsspp_asks_the_runner() {
        val s = CoreRegistry.forTag("PSP")!!.vulkan!!
        assertEquals(mapOf("ppsspp_backend" to "auto"), s.gles)
        assertEquals(mapOf("ppsspp_backend" to "auto"), s.vulkan)
    }

    @Test fun swanstation_keeps_its_software_renderer_choice() {
        val d = CoreRegistry.forTag("PSX")!!
        val s = d.vulkan!!
        assertFalse(s.lock)
        assertTrue(s.gles.isEmpty())
        assertEquals(mapOf("swanstation_GPU_Renderer" to "Auto"), s.vulkan)
        assertEquals(setOf("Vulkan"), d.hiddenValues["swanstation_GPU_Renderer"])
        assertFalse("swanstation_GPU_Renderer" in d.lockedOptions)
    }

    @Test fun azahar_always_sets_its_graphics_api() {
        val d = CoreRegistry.forTag("3DS")!!
        assertEquals(VulkanSwitch(gles = mapOf("citra_graphics_api" to "OpenGL"), vulkan = mapOf("citra_graphics_api" to "Vulkan")), d.vulkan)
        assertTrue("citra_graphics_api" in d.lockedOptions)
        assertEquals("OpenGL", d.defaultOptions["citra_graphics_api"])
    }

    // DBZ BT3 runs on the Fold over the Vulkan bridge (2026-10-28): the Graphics API row picks the core's renderer.
    @Test fun ps2SwitchesRenderer() {
        val d = CoreRegistry.forTag("PS2")!!
        assertEquals(VulkanSwitch(gles = mapOf("armsx2_renderer" to "OpenGL"), vulkan = mapOf("armsx2_renderer" to "Vulkan")), d.vulkan)
        assertTrue("armsx2_renderer" in d.lockedOptions)
        assertTrue(GraphicsApiOptions.appliesTo("PS2"))
    }

    @Test fun n64_follows_graphics_api_and_offers_parallel_rows_out_of_game() {
        val d = CoreRegistry.forTag("N64")!!
        assertNull(d.hiddenValues["mupen64plus-rdp-plugin"])
        assertTrue(d.offlineHiddenOptions.none { it.startsWith("mupen64plus-parallel-rdp") })
        assertTrue("mupen64plus-rdp-plugin" in d.lockedOptions && "mupen64plus-rsp-plugin" in d.lockedOptions)
    }
}
