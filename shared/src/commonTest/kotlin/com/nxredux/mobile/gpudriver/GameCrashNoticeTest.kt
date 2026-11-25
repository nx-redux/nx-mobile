package com.nxredux.mobile.gpudriver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameCrashNoticeTest {
    private val mali = "Mali-G76 MC4"

    @Test fun onlyVulkanOnMalisOwnDriverCounts() {
        assertTrue(GameCrashNotice.onMaliSystemVulkan(vulkan = true, customDriver = false, gpu = mali))
        assertFalse(GameCrashNotice.onMaliSystemVulkan(vulkan = true, customDriver = true, gpu = mali))
        assertFalse(GameCrashNotice.onMaliSystemVulkan(vulkan = false, customDriver = false, gpu = mali))
        assertFalse(GameCrashNotice.onMaliSystemVulkan(vulkan = true, customDriver = false, gpu = "Adreno (TM) 740"))
        assertFalse(GameCrashNotice.onMaliSystemVulkan(vulkan = true, customDriver = false, gpu = null))
    }

    @Test fun noCrashNoNotice() {
        assertNull(GameCrashNotice.notice(crashed = false, onMaliSystemVulkan = false, driversAvailable = true))
        assertNull(GameCrashNotice.notice(crashed = false, onMaliSystemVulkan = true, driversAvailable = true))
    }

    @Test fun aCrashElsewhereGetsTheGenericNotice() {
        assertEquals(GameCrashNotice.GENERIC, GameCrashNotice.notice(crashed = true, onMaliSystemVulkan = false, driversAvailable = true))
        assertEquals(GameCrashNotice.GENERIC, GameCrashNotice.notice(crashed = true, onMaliSystemVulkan = false, driversAvailable = false))
    }

    @Test fun aCrashOnMalisSystemVulkanNamesTheFix() {
        assertEquals(GameCrashNotice.MALI_VULKAN, GameCrashNotice.notice(crashed = true, onMaliSystemVulkan = true, driversAvailable = true))
        assertEquals(GameCrashNotice.MALI_VULKAN_NO_DRIVERS, GameCrashNotice.notice(crashed = true, onMaliSystemVulkan = true, driversAvailable = false))
    }

    @Test fun everyCrashNoticeKeepsTheAutoSaveLine() {
        for (n in listOf(GameCrashNotice.GENERIC, GameCrashNotice.MALI_VULKAN, GameCrashNotice.MALI_VULKAN_NO_DRIVERS)) {
            assertTrue(n.startsWith("The game stopped unexpectedly."))
            assertTrue(n.endsWith("It will continue from its last auto-save."))
        }
        assertFalse("GPU driver" in GameCrashNotice.MALI_VULKAN_NO_DRIVERS)
    }
}
