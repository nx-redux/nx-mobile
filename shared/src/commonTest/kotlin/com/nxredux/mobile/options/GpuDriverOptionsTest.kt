package com.nxredux.mobile.options

import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.gpudriver.GpuDriverChoice
import com.nxredux.mobile.runner.CoreRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuDriverOptionsTest {
    private val turnip = GpuDriver("turnip", "Turnip 24.1", "24.1", "a", "d", "libvulkan_freedreno.so", 28)
    private val mesa = GpuDriver("mesa", "Mesa", "1.0", "b", "d", "libvulkan_mesa.so", 28)
    private val n64 = CoreRegistry.forTag("N64")!!
    private fun cfg(text: String) = CoreConfig.parse(text)
    private fun page(console: String? = null, game: String? = null, drivers: List<GpuDriver>? = listOf(turnip, mesa), forGame: Boolean = false,
                     resolved: Boolean = false, note: String? = null, failed: Boolean = false) =
        EmulatorSettings.page("N64", n64, null, n64.defaultOptions, console?.let(::cfg), game?.let(::cfg), forGame = forGame, vulkanFailed = failed,
            gpuDrivers = drivers, gpuDriverResolved = resolved, gpuDriverCrashNote = note)
    private fun ConsolePageRow(p: EmulatorPage) = p.console.singleOrNull { it.option.key == GpuDriverOptions.KEY }

    @Test fun rowListsDefaultSystemThenInstalled() {
        val row = GpuDriverOptions.row(listOf(turnip, mesa))
        assertEquals(listOf("default", "system", "turnip", "mesa"), row.values)
        assertEquals(listOf("Default", "System driver", "Turnip 24.1", "Mesa"), row.valueLabels)
        assertEquals(GpuDriverChoice.DEFAULT, row.default)
        assertTrue(row.atLaunch)
        assertTrue(!row.visual)
        assertEquals(GraphicsApiOptions.KEY to setOf(GraphicsApiOptions.VULKAN), row.shownWhen)
    }

    @Test fun rowHiddenWhileGraphicsApiIsNotVulkan() {
        val rows = ConsoleSettings.tableFor("N64", gpuDrivers = listOf(turnip))
        assertTrue(rows.indexOfFirst { it.key == GpuDriverOptions.KEY } == rows.indexOfFirst { it.key == GraphicsApiOptions.KEY } + 1)
        assertNull(rows.shownFor(mapOf(GraphicsApiOptions.KEY to GraphicsApiOptions.GLES)).firstOrNull { it.key == GpuDriverOptions.KEY })
        assertNull(rows.shownFor(emptyMap()).firstOrNull { it.key == GpuDriverOptions.KEY })
        assertNotNull(rows.shownFor(mapOf(GraphicsApiOptions.KEY to GraphicsApiOptions.VULKAN)).firstOrNull { it.key == GpuDriverOptions.KEY })
        assertNull(ConsolePageRow(page("nx_graphics_api = gles\n")))
        assertNotNull(ConsolePageRow(page("nx_graphics_api = vulkan\n")))
    }

    @Test fun noRowWhenUnavailable() {
        assertNull(ConsolePageRow(page("nx_graphics_api = vulkan\n", drivers = null)))
        assertTrue(ConsoleSettings.tableFor("N64").none { it.key == GpuDriverOptions.KEY })
        assertTrue(!GpuDriverOptions.appliesTo("N64", available = false))
        assertTrue(!GpuDriverOptions.appliesTo("GBA", available = true))
    }

    @Test fun unknownStoredIdShowsDefault() {
        val row = ConsolePageRow(page("nx_graphics_api = vulkan\nnx_gpu_driver = gone\n"))!!
        assertEquals(GpuDriverChoice.DEFAULT, row.value)
        assertEquals("turnip", ConsolePageRow(page("nx_graphics_api = vulkan\nnx_gpu_driver = turnip\n"))!!.value)
    }

    @Test fun vulkanFailedNoteHiddenWhenDriverResolves() {
        fun info(resolved: Boolean) = page(failed = true, resolved = resolved).console.single { it.option.key == GraphicsApiOptions.KEY }.option.info
        assertTrue(info(false).startsWith(VulkanFallback.NOTE))
        assertEquals(GraphicsApiOptions.row.info, info(true))
    }

    @Test fun crashNoteOnGamePage() {
        val note = GpuDriverOptions.CRASH_NOTE.replace("%s", "Turnip 24.1")
        val p = page(console = "nx_graphics_api = vulkan\n", forGame = true, note = note)
        assertEquals(note, ConsolePageRow(p)!!.disabledNote)
        assertNull(page(console = "nx_graphics_api = vulkan\n", forGame = true).console.single { it.option.key == GpuDriverOptions.KEY }.disabledNote)
        assertNull(p.console.single { it.option.key == GraphicsApiOptions.KEY }.disabledNote)
    }

    @Test fun allKeepsKeysUnique() {
        val keys = ConsoleSettings.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(GpuDriverOptions.KEY in keys)
    }
}
