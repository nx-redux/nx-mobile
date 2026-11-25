package com.nxredux.mobile.gpudriver

import com.nxredux.mobile.options.GraphicsApiOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class GpuDriverChoiceTest {
    private val installed = setOf("d1", "d2")

    private fun launch(
        apiValue: String? = GraphicsApiOptions.VULKAN, cfgValue: String? = GpuDriverChoice.DEFAULT, appWide: String? = "d1",
        crashed: Set<String> = emptySet(), available: Boolean = true, forceGles: Boolean = false, forceSystem: Boolean = false,
        coreFailed: Boolean = false, netplay: Boolean = false,
    ) = GpuDriverChoice.launch(apiValue, cfgValue, appWide, installed, crashed, available, forceGles, forceSystem, coreFailed, netplay)

    @Test fun systemAtCfgWinsOverAppWide() =
        assertEquals(null, GpuDriverChoice.resolve(GpuDriverChoice.SYSTEM, "d1", installed, emptySet()))

    @Test fun installedCfgIdWins() = assertEquals("d2", GpuDriverChoice.resolve("d2", "d1", installed, emptySet()))

    @Test fun defaultFollowsAppWide() = assertEquals("d1", GpuDriverChoice.resolve(GpuDriverChoice.DEFAULT, "d1", installed, emptySet()))

    @Test fun unknownCfgIdFollowsAppWide() = assertEquals("d1", GpuDriverChoice.resolve("gone", "d1", installed, emptySet()))

    @Test fun removedAppWideIsSystem() = assertEquals(null, GpuDriverChoice.resolve(null, "gone", installed, emptySet()))

    @Test fun crashedDriverIsSystemForThatGame() =
        assertEquals(null, GpuDriverChoice.resolve("d2", "d1", installed, setOf("d2")))

    @Test fun glesLaunchNeverLoadsADriver() {
        assertEquals(null, launch(apiValue = GraphicsApiOptions.GLES).driverId)
        assertEquals(null, launch(apiValue = GraphicsApiOptions.AUTO).driverId)
        assertEquals(null, launch(available = false).driverId)
        assertEquals(null, launch(forceGles = true).driverId)
    }

    @Test fun customDriverIgnoresCoreFailedRecord() =
        assertEquals(GpuDriverChoice.Launch("d1", false), launch(coreFailed = true))

    @Test fun noDriverKeepsCoreFailedRecord() {
        assertEquals(GpuDriverChoice.Launch(null, true), launch(appWide = null, coreFailed = true))
        assertEquals(GpuDriverChoice.Launch(null, false), launch(appWide = null))
    }

    @Test fun forceSystemDropsDriverAndHonoursRecord() {
        assertEquals(GpuDriverChoice.Launch(null, true), launch(forceSystem = true, coreFailed = true))
        assertEquals(GpuDriverChoice.Launch(null, false), launch(forceSystem = true))
    }

    @Test fun netplayForcesGles() = assertEquals(GpuDriverChoice.Launch(null, true), launch(netplay = true))

    @Test fun forceGlesMarksVulkanFailed() = assertEquals(GpuDriverChoice.Launch(null, true), launch(forceGles = true))

    @Test fun notOnVulkanShowsError() =
        assertEquals(GpuDriverChoice.Next.SHOW_ERROR, GpuDriverChoice.afterStartFailure(false, null, false, false))

    @Test fun netplayFailsWithMessage() =
        assertEquals(GpuDriverChoice.Next.FAIL_MESSAGE, GpuDriverChoice.afterStartFailure(true, "d1", false, true))

    @Test fun driverFailureFallsBackToSystem() =
        assertEquals(GpuDriverChoice.Next.SYSTEM_DRIVER, GpuDriverChoice.afterStartFailure(true, "d1", false, false))

    @Test fun driverFailureWithCoreRecordGoesToGlesWithoutRecording() =
        assertEquals(GpuDriverChoice.Next.GLES_NO_RECORD, GpuDriverChoice.afterStartFailure(true, "d1", true, false))

    @Test fun systemFailureGoesToGlesAndRecords() =
        assertEquals(GpuDriverChoice.Next.GLES_RECORD, GpuDriverChoice.afterStartFailure(true, null, false, false))

    /** The three extras together: custom → system driver → OpenGL ES → the error, never a loop. */
    @Test fun relaunchChainEnds() {
        fun launch(forceGles: Boolean, forceSystem: Boolean, coreFailed: Boolean) = GpuDriverChoice.launch(
            GraphicsApiOptions.VULKAN, "d1", null, setOf("d1"), emptySet(), available = true, forceGles = forceGles,
            forceSystem = forceSystem, coreFailedOnSystem = coreFailed, netplayForcesGles = false)
        val first = launch(forceGles = false, forceSystem = false, coreFailed = false)
        assertEquals("d1", first.driverId)
        assertEquals(GpuDriverChoice.Next.SYSTEM_DRIVER, GpuDriverChoice.afterStartFailure(!first.vulkanFailed, first.driverId, false, false))
        val second = launch(forceGles = false, forceSystem = true, coreFailed = false)
        assertEquals(GpuDriverChoice.Launch(null, false), second)
        assertEquals(GpuDriverChoice.Next.GLES_RECORD, GpuDriverChoice.afterStartFailure(!second.vulkanFailed, second.driverId, false, false))
        val third = launch(forceGles = true, forceSystem = true, coreFailed = true)
        assertEquals(GpuDriverChoice.Launch(null, true), third)
        assertEquals(GpuDriverChoice.Next.SHOW_ERROR, GpuDriverChoice.afterStartFailure(!third.vulkanFailed, third.driverId, true, false))
    }

    @Test fun noticesNameTheDriver() {
        assertEquals("Turnip v25 couldn't start, so this game is using the system driver.", GpuDriverChoice.named(GpuDriverChoice.SYSTEM_DRIVER_NOTICE, "Turnip v25"))
        assertEquals("Turnip 100% couldn't start, so this game is using OpenGL ES.", GpuDriverChoice.named(GpuDriverChoice.GLES_NOTICE, "Turnip 100%"))
        assertEquals("Turnip v25 could not start for this game. Set Display → GPU driver to System driver.", GpuDriverChoice.named(GpuDriverChoice.NETPLAY_FAIL, "Turnip v25"))
    }

    @Test fun passedPreflightKeepsTheDriver() {
        val l = launch()
        assertEquals(GpuDriverChoice.Preflight(l, null, false), GpuDriverChoice.afterPreflight(l, true, "Turnip", false))
    }

    @Test fun failedPreflightGoesOnOnTheSystemDriverWithTheNotice() {
        val p = GpuDriverChoice.afterPreflight(launch(), false, "Turnip", false)
        assertEquals(GpuDriverChoice.Launch(null, false), p.launch)   // Vulkan on the system driver: nothing recorded
        assertEquals("Turnip couldn't start, so this game is using the system driver.", p.notice)
        assertEquals(true, p.clearMarker)
    }

    @Test fun failedPreflightOnACoreThatFailedOnTheSystemDriverGoesToGles() {
        val p = GpuDriverChoice.afterPreflight(launch(coreFailed = true), false, "Turnip", true)
        assertEquals(GpuDriverChoice.Launch(null, true), p.launch)
        assertEquals("Turnip couldn't start, so this game is using OpenGL ES.", p.notice)
        assertEquals(true, p.clearMarker)
    }

    @Test fun noDriverNoPreflightEffect() {
        val l = launch(apiValue = GraphicsApiOptions.GLES)
        assertEquals(GpuDriverChoice.Preflight(l, null, false), GpuDriverChoice.afterPreflight(l, false, "Turnip", false))
    }
}
