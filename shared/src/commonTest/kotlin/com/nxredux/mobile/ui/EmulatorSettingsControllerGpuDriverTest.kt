package com.nxredux.mobile.ui

import com.nxredux.mobile.gpudriver.GpuDriver
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.options.ConfigFile
import com.nxredux.mobile.options.CoreConfig
import com.nxredux.mobile.options.CoreOptionSource
import com.nxredux.mobile.options.CoreOptionTable
import com.nxredux.mobile.options.GpuDriverOptions
import com.nxredux.mobile.options.GraphicsApiOptions
import com.nxredux.mobile.options.VulkanFallback
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.EmulatorConfigStore
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmulatorSettingsControllerGpuDriverTest {
    private val turnip = GpuDriver("turnip", "Turnip 24.1", "24.1", "a", "d", "libvulkan_freedreno.so", 28)
    private val n64 = CoreRegistry.forTag("N64")!!
    private val vulkan = "nx_graphics_api = vulkan\n"

    private class MemoryConfigs(initial: Map<String, String> = emptyMap()) : EmulatorConfigStore {
        val files = HashMap(initial)
        private fun console(tag: String, core: String) = "$tag-$core/minarch.cfg"
        private fun game(tag: String, core: String, rom: String) = "$tag-$core/${rom.substringBeforeLast('.')}.cfg"
        override suspend fun read(tag: String, coreId: String, romFileName: String?): Pair<ConfigFile?, ConfigFile?> =
            files[console(tag, coreId)]?.let(CoreConfig::parse) to romFileName?.let { files[game(tag, coreId, it)] }?.let(CoreConfig::parse)
        override suspend fun writeConsole(tag: String, coreId: String, text: String) { files[console(tag, coreId)] = text }
        override suspend fun writeGame(tag: String, coreId: String, romFileName: String, text: String) { files[game(tag, coreId, romFileName)] = text }
        override suspend fun deleteGame(tag: String, coreId: String, romFileName: String) { files.remove(game(tag, coreId, romFileName)) }
    }

    private object NoTables : CoreOptionSource { override fun table(coreId: String): CoreOptionTable? = null }
    private object Host : EmulatorSettingsHost {
        override fun showEmulatorsSettings() {}
        override fun restore(screen: Screen, selection: ListSelection) {}
    }

    private val rom = "Zelda.z64"
    private val entry = GameEntry(ContentRef("r/$rom"), rom, "Zelda", folder = ContentRef("r"), sourceId = "home", relativeDir = "Roms/Nintendo 64 (N64)")
    private val listed = Listed(SystemFolder("N64", "N64", n64, listOf(entry)), entry)

    private class Harness(
        val controller: EmulatorSettingsController, val screen: MutableStateFlow<Screen>, val selection: MutableStateFlow<ListSelection>,
        val cleared: MutableList<String>,
    )

    private fun TestScope.harness(
        configs: MemoryConfigs, crashed: Set<String> = emptySet(), vulkanFailed: Boolean = false, appWide: String? = null,
    ): Harness {
        val screen = MutableStateFlow<Screen>(Screen.EmulatorList(emptyList()))
        val selection = MutableStateFlow(ListSelection(0, 0))
        val cleared = mutableListOf<String>()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val controller = EmulatorSettingsController(
            CoroutineScope(dispatcher), screen, selection, MutableStateFlow(null), NoTables, configs, { emptyMap() }, Host, dispatcher,
            vulkanFailed = { vulkanFailed },
            gpuDrivers = { listOf(turnip) }, gpuDriverAppWide = { appWide }, gpuDriverCrashed = { crashed }, clearGpuDriverCrash = { cleared += it },
        )
        return Harness(controller, screen, selection, cleared)
    }

    private fun TestScope.openGame(h: Harness): Screen.EmulatorOptions {
        h.controller.openGame(listed)
        advanceUntilIdle()
        return assertIs<Screen.EmulatorOptions>(h.screen.value)
    }

    private fun TestScope.openConsole(h: Harness): Screen.EmulatorOptions {
        val s = Screen.EmulatorConsoles(n64.id, n64.name, listOf("N64"))
        h.screen.value = s
        h.controller.onButton(s, NxButton.A)
        advanceUntilIdle()
        return assertIs<Screen.EmulatorOptions>(h.screen.value)
    }

    private fun Harness.gpuRow(): EmulatorRow.Console =
        (screen.value as Screen.EmulatorOptions).rows.filterIsInstance<EmulatorRow.Console>().single { it.row.option.key == GpuDriverOptions.KEY }

    private fun TestScope.stepGpu(h: Harness) {
        val rows = (h.screen.value as Screen.EmulatorOptions).rows
        h.selection.value = ListSelection(rows.indexOf(h.gpuRow()), rows.size)
        h.controller.onButton(h.screen.value, NxButton.RIGHT)
        advanceUntilIdle()
    }

    @Test fun aGameWriteClearsTheCrashRecordAndAConsoleWriteDoesNot() = runTest {
        val game = harness(MemoryConfigs(mapOf("N64-${n64.id}/minarch.cfg" to vulkan)))
        openGame(game)
        stepGpu(game)
        assertEquals(listOf(entry.path), game.cleared)
        assertEquals("system", game.gpuRow().row.value)

        val console = harness(MemoryConfigs(mapOf("N64-${n64.id}/minarch.cfg" to vulkan)))
        openConsole(console)
        stepGpu(console)
        assertEquals("system", console.gpuRow().row.value)
        assertTrue(console.cleared.isEmpty())
    }

    @Test fun aCrashedDriverShowsTheNoteAndTheRowStaysChangeable() = runTest {
        val h = harness(MemoryConfigs(mapOf("N64-${n64.id}/minarch.cfg" to vulkan)), crashed = setOf("turnip"), appWide = "turnip")
        openGame(h)
        val note = GpuDriverOptions.CRASH_NOTE.replace("%s", "Turnip 24.1")
        assertEquals(note, h.gpuRow().row.option.brief)
        assertNull(h.gpuRow().row.disabledNote)
        stepGpu(h)
        assertEquals("system", h.gpuRow().row.value)   // changed through the controller's change path
        assertEquals(listOf(entry.path), h.cleared)
    }

    @Test fun noCrashNoteWhenTheDriverDidNotCrash() = runTest {
        val h = harness(MemoryConfigs(mapOf("N64-${n64.id}/minarch.cfg" to vulkan)), appWide = "turnip")
        openGame(h)
        assertEquals(GpuDriverOptions.row(listOf(turnip)).brief, h.gpuRow().row.option.brief)
    }

    private fun graphicsInfo(h: Harness) =
        (h.screen.value as Screen.EmulatorOptions).page.console.single { it.option.key == GraphicsApiOptions.KEY }.option.info

    @Test fun theVulkanFailedNoteIsHiddenOnlyOnVulkanWithAResolvedDriver() = runTest {
        fun configs(text: String) = MemoryConfigs(mapOf("N64-${n64.id}/minarch.cfg" to text))
        val resolved = harness(configs(vulkan), vulkanFailed = true, appWide = "turnip")
        openConsole(resolved)
        assertEquals(GraphicsApiOptions.row.info, graphicsInfo(resolved))

        val noDriver = harness(configs(vulkan), vulkanFailed = true)   // default follows the app-wide pick, which is the system driver
        openConsole(noDriver)
        assertTrue(graphicsInfo(noDriver).startsWith(VulkanFallback.NOTE))

        val gles = harness(configs("nx_graphics_api = gles\n"), vulkanFailed = true, appWide = "turnip")
        openConsole(gles)
        assertTrue(graphicsInfo(gles).startsWith(VulkanFallback.NOTE))
    }
}
