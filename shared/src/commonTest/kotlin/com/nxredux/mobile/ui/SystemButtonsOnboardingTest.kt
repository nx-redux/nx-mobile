package com.nxredux.mobile.ui

import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.settings.FakeAppSettings
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.PadModel
import com.nxredux.mobile.shortcuts.SystemButtons
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Setup with a controller in a real [AppState]: no page asks for its SELECT / START / MENU; the last page says where they are set. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemButtonsOnboardingTest {
    class Settings(treeRef: ContentRef? = ContentRef("Home"), override var onboardingDone: Boolean? = true) :
        FakeAppSettings(treeRef, androidGamesAsked = true) {
        override var onboardingStep: String? = null
        override var shortcuts = ""
        override var systemButtons = ""
    }

    private val source = FakeRomsSource(
        dirs = mapOf(
            "Home" to listOf("Roms"),
            "Home/Roms" to listOf("Super Nintendo (SFC)"),
            "Home/Roms/Super Nintendo (SFC)" to listOf("Super Mario Kart (USA).sfc"),
        ),
    )

    private val aya = PadModel(0x4001, 0x0428, "AYANEO Controller")
    private var pads = listOf<PadModel>()

    private fun TestScope.state(settings: Settings = Settings()): AppState {
        val state = AppState(
            AppDeps(settings, source, NoopRunner, NoopFolderPicker, compute = UnconfinedTestDispatcher(testScheduler), connectedPads = { pads }),
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        state.load(); runCurrent()
        return state
    }

    private fun AppState.toTextSizeContinue() {
        onButton(NxButton.A)   // the welcome's Get started
        assertEquals(OnboardingStep.TEXT_SIZE, assertIs<Screen.Onboarding>(screen.value).step)
        select(1); onButton(NxButton.A)
    }

    // A controller adds no step: the text size goes straight on to the folders, as without one.
    @Test fun controllerAddsNoSetupStep() = runTest {
        pads = listOf(aya)
        val fresh = Settings(treeRef = null, onboardingDone = null)
        val s = state(fresh)
        s.toTextSizeContinue()
        assertFalse(s.screen.value is Screen.Onboarding)
        assertEquals("", fresh.systemButtons)
    }

    // A controller connected later opens nothing over the menu.
    @Test fun newControllerOpensNoPage() = runTest {
        val s = state()
        val menu = assertIs<Screen.Systems>(s.screen.value)
        pads = listOf(aya)
        assertFalse(s.systemButtonKey(aya, null, KeyCodes.BUTTON_SELECT, 0, true, false))
        assertEquals(menu, s.screen.value)
    }

    // The entry earlier versions kept for the models their page had asked about is read past and dropped on the next save.
    @Test fun oldAskedEntryIsReadPast() {
        val map = SystemButtons.decode("4001:0428=MENU:0:753;asked=4001:0428,057e:2009")
        assertEquals(setOf("4001:0428"), map.bound.keys)
        assertEquals("4001:0428=MENU:0:753", SystemButtons.encode(map))
        assertTrue(SystemButtons.decode("asked=4001:0428").bound.isEmpty())
    }
}
