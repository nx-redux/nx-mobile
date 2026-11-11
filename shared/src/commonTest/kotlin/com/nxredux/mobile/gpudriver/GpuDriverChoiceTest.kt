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
}
