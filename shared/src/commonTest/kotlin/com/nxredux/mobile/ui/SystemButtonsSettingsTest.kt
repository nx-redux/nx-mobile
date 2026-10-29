package com.nxredux.mobile.ui

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.FolderPicker
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.PhysicalKey
import com.nxredux.mobile.shortcuts.SystemButtonMap
import com.nxredux.mobile.shortcuts.SystemButtons
import com.nxredux.mobile.shortcuts.SystemRole
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings → Controls → System buttons: the pad pressed last, its three roles, rebind and reset. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemButtonsSettingsTest {
    private object Picker : FolderPicker { override fun pickHome() {}; override fun pickExtra() {}; override fun forget(ref: ContentRef) {} }
    private class Runner : EmulatorRunner { override suspend fun launch(session: GameSession, recent: com.nxredux.mobile.storage.Recent) {} }

    private val source = FakeRomsSource(dirs = mapOf("Home" to listOf("Roms"), "Home/Roms" to emptyList()))
    private val aya = PadModel(0x4001, 0x0428, "AYANEO Controller")
    private val xbox = PadModel(0x045e, 0x028e, "Xbox 360 Controller")
    private var pads = listOf<PadModel>()

    private fun TestScope.state(settings: SystemButtonsOnboardingTest.Settings = SystemButtonsOnboardingTest.Settings()): AppState {
        // Every model already asked about: the page never opens over Settings here.
        if (settings.systemButtons.isEmpty()) settings.systemButtons = SystemButtons.encode(SystemButtonMap(asked = setOf(aya.key, xbox.key)))
        val state = AppState(
            AppDeps(settings, source, Runner(), Picker, compute = UnconfinedTestDispatcher(testScheduler), connectedPads = { pads }),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); runCurrent()
        return state
    }

    private fun AppState.page() = assertIs<Screen.Settings>(screen.value)
    private fun AppState.row(label: String) = page().rows.first { it.label == label }
    private fun AppState.open() {
        openSettings(SettingsSection.CONTROLS)
        select(page().rows.indexOfFirst { it.kind == SettingsKind.SYSTEM_BUTTONS }); openSelected()
        assertEquals(SettingsSection.SYSTEM_BUTTONS, page().section)
    }
    private fun AppState.bind(role: SystemRole) { select(page().rows.indexOfFirst { it.label == role.label }); onButton(NxButton.A) }
    private fun AppState.tap(pad: PadModel, code: Int, scan: Int = 0) = systemButtonKey(pad, code, scan, true, false).also { systemButtonKey(pad, code, scan, false, false) }

    @Test fun controlsHasSystemButtonsAfterShortcuts() = runTest {
        val s = state()
        s.openSettings(SettingsSection.CONTROLS)
        val labels = s.page().rows.map { it.label }
        assertEquals(labels.indexOf("Shortcuts") + 1, labels.indexOf("System buttons"))
    }

    @Test fun rowsShowStandardThenBound() = runTest {
        pads = listOf(aya)
        val settings = SystemButtonsOnboardingTest.Settings()
        val s = state(settings)
        s.open()
        assertTrue(s.page().rows.any { it.label == "AYANEO Controller" })
        assertEquals("Standard", s.row("MENU").secondary)
        s.bind(SystemRole.MENU)
        assertEquals(SYSTEM_BUTTON_PROMPT, s.row("MENU").secondary)
        assertTrue(s.tap(aya, 0, 0x2f1))
        assertEquals("Button 2F1", s.row("MENU").secondary)
        assertEquals(110, SystemButtons.translate(SystemButtons.decode(settings.systemButtons), aya.key, 0, 0x2f1))
        s.bind(SystemRole.SELECT); s.tap(aya, KeyCodes.BUTTON_THUMBL)
        assertEquals("L3", s.row("SELECT").secondary)
    }

    @Test fun bAndBackCancel() = runTest {
        pads = listOf(aya)
        val settings = SystemButtonsOnboardingTest.Settings()
        val s = state(settings)
        s.open()
        val before = settings.systemButtons
        for (cancel in listOf(KeyCodes.BUTTON_B, KeyCodes.BACK)) {
            s.bind(SystemRole.MENU)
            assertTrue(s.tap(aya, cancel))
            assertEquals("Standard", s.row("MENU").secondary)
        }
        s.bind(SystemRole.MENU)
        s.onButton(NxButton.B)                     // the touch hint's B: cancels, the page stays
        assertEquals(SettingsSection.SYSTEM_BUTTONS, s.page().section)
        assertEquals("Standard", s.row("MENU").secondary)
        assertEquals(before, settings.systemButtons)
    }

    @Test fun resetClearsThisModelOnly() = runTest {
        pads = listOf(aya)
        val settings = SystemButtonsOnboardingTest.Settings()
        var map = SystemButtonMap(asked = setOf(aya.key, xbox.key))
        map = SystemButtons.bind(map, aya.key, SystemRole.MENU, PhysicalKey.of(0, 0x2f1))
        map = SystemButtons.bind(map, xbox.key, SystemRole.MENU, PhysicalKey.of(KeyCodes.BUTTON_THUMBR, 0))
        settings.systemButtons = SystemButtons.encode(map)
        val s = state(settings)
        s.open()
        s.select(s.page().rows.indexOfFirst { it.kind == SettingsKind.RESET_DEFAULTS }); s.openSelected()
        assertTrue(s.page().confirmReset)
        s.onButton(NxButton.A)
        val after = SystemButtons.decode(settings.systemButtons)
        assertNull(after.bound[aya.key])
        assertEquals(map.bound[xbox.key], after.bound[xbox.key])
        assertEquals("Standard", s.row("MENU").secondary)
    }

    @Test fun bindsOnlyTheLastPressedModel() = runTest {
        pads = listOf(xbox, aya)
        val settings = SystemButtonsOnboardingTest.Settings()
        val s = state(settings)
        s.tap(aya, KeyCodes.DPAD_DOWN)            // the AYANEO pressed last
        s.open()
        assertTrue(s.page().rows.any { it.label == "AYANEO Controller" })
        s.bind(SystemRole.MENU)
        s.tap(xbox, KeyCodes.BUTTON_THUMBR)        // another pad: not this page's
        assertEquals(SYSTEM_BUTTON_PROMPT, s.row("MENU").secondary)
        s.tap(aya, 0, 0x2f1)
        val after = SystemButtons.decode(settings.systemButtons)
        assertEquals(PhysicalKey(0, 0x2f1), after.bound[aya.key]?.get(SystemRole.MENU))
        assertNull(after.bound[xbox.key])
    }

    @Test fun noPadShowsConnectNote() = runTest {
        val s = state()
        s.open()
        assertEquals(listOf(SYSTEM_BUTTONS_NO_CONTROLLER), s.page().rows.map { it.label })
    }
}
