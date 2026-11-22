package com.nxredux.mobile.options

import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VulkanFallbackTest {
    @Test fun a_recorded_core_counts_only_for_the_app_version_it_failed_on() {
        val stored = VulkanFallback.record(null, "dolphin", "0.1.1-abc")
        assertEquals(setOf("dolphin"), VulkanFallback.failedCores(stored, "0.1.1-abc"))
        assertEquals(emptySet(), VulkanFallback.failedCores(stored, "0.1.2-def"))
    }

    @Test fun recording_keeps_this_versions_cores_and_drops_an_older_versions() {
        val v1 = VulkanFallback.record(VulkanFallback.record(null, "dolphin", "a"), "ppsspp", "a")
        assertEquals(setOf("dolphin", "ppsspp"), VulkanFallback.failedCores(v1, "a"))
        val v2 = VulkanFallback.record(v1, "flycast", "b")
        assertEquals(setOf("flycast"), VulkanFallback.failedCores(v2, "b"))
    }

    @Test fun nothing_or_garbage_stored_means_no_failures() {
        assertEquals(emptySet(), VulkanFallback.failedCores(null, "a"))
        assertEquals(emptySet(), VulkanFallback.failedCores("", "a"))
        assertEquals(emptySet(), VulkanFallback.failedCores("\n\n", ""))
    }

    @Test fun the_graphics_api_row_carries_the_note_only_when_vulkan_failed() {
        val row = GraphicsApiOptions.row
        assertEquals(row, VulkanFallback.noted(row, failed = false))
        val noted = VulkanFallback.noted(row, failed = true)
        assertTrue(noted.info.startsWith(VulkanFallback.NOTE), noted.info)
        assertTrue(noted.info.endsWith(row.info))
        assertEquals(row.copy(info = noted.info), noted)
        // Any other row is untouched.
        val other = FrontendOptions.table.first()
        assertEquals(other, VulkanFallback.noted(other, failed = true))
    }

    private val n64 = CoreRegistry.forTag("N64")!!.vulkan

    @Test fun a_core_vulkan_failed_for_resolves_to_gles_without_probing() {
        var probed = false
        val l = GraphicsApiOptions.resolve(GraphicsApiOptions.VULKAN, n64, { probed = true; true }, vulkanFailed = true)
        assertFalse(l.preferVulkan)
        assertFalse(probed)
        assertEquals("gliden64", l.forcedOptions["mupen64plus-rdp-plugin"])
        assertEquals(mapOf("PARALLEL_RDP_SMALL_TYPES" to null), l.env)
    }
}
