package com.nxredux.mobile.ui

import com.nxredux.mobile.ui.OnboardingStep.ACCENT
import com.nxredux.mobile.ui.OnboardingStep.ARTWORK
import com.nxredux.mobile.ui.OnboardingStep.BOTTOM_SCREEN
import com.nxredux.mobile.ui.OnboardingStep.DONE
import com.nxredux.mobile.ui.OnboardingStep.HANDHELD
import com.nxredux.mobile.ui.OnboardingStep.IMPORT_SAVES
import com.nxredux.mobile.ui.OnboardingStep.CUSTOMISE
import com.nxredux.mobile.ui.OnboardingStep.GAME_LISTS
import com.nxredux.mobile.ui.OnboardingStep.PAD
import com.nxredux.mobile.ui.OnboardingStep.TAB_COLLECTIONS
import com.nxredux.mobile.ui.OnboardingStep.TEXT_SIZE
import com.nxredux.mobile.ui.OnboardingStep.TAB_CONSOLES
import com.nxredux.mobile.ui.OnboardingStep.TAB_HOME
import com.nxredux.mobile.ui.OnboardingStep.TAB_TOOLS
import com.nxredux.mobile.ui.OnboardingStep.TITLE
import com.nxredux.mobile.ui.OnboardingStep.RA_DOWNLOAD
import com.nxredux.mobile.ui.OnboardingStep.RA_SIGN_IN
import com.nxredux.mobile.ui.OnboardingStep.SAVE_FORMATS
import com.nxredux.mobile.ui.OnboardingStep.SYNC_OFFER
import com.nxredux.mobile.ui.OnboardingStep.SYSTEM_BUTTONS
import com.nxredux.mobile.ui.OnboardingStep.WELCOME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingControllerTest {
    @Test fun withoutAHandheldTheFormatsAndSyncAreLeftOut() {
        assertEquals(
            listOf(HANDHELD, IMPORT_SAVES, ARTWORK, RA_SIGN_IN, CUSTOMISE, ACCENT, DONE),
            onboardingSteps(OnboardingContext(handheld = false)),
        )
    }

    @Test fun aHandheldAddsItsFormatsAndTheSyncOffer() {
        assertEquals(
            listOf(HANDHELD, SAVE_FORMATS, IMPORT_SAVES, SYNC_OFFER, ARTWORK, RA_SIGN_IN, CUSTOMISE, ACCENT, DONE),
            onboardingSteps(OnboardingContext(handheld = true)),
        )
    }

    @Test fun signedInAddsTheDownloadOffer() {
        val steps = onboardingSteps(OnboardingContext(handheld = false, signedIn = true))
        assertEquals(RA_DOWNLOAD, steps[steps.indexOf(RA_SIGN_IN) + 1])
    }

    @Test fun nextAndBackWalkTheListAndBackStopsAtTheHandheld() {
        val c = OnboardingController()
        c.startMain()
        assertEquals(HANDHELD, c.step)
        assertEquals(HANDHELD, c.back())                  // nothing before it in this phase
        c.context = c.context.copy(handheld = true)
        assertEquals(SAVE_FORMATS, c.next())
        assertEquals(IMPORT_SAVES, c.next())
        assertEquals(SYNC_OFFER, c.next())
        assertEquals(IMPORT_SAVES, c.back())
        assertEquals(Pair(3, 9), c.position())
    }

    @Test fun doneIsTheLastAndNextFinishes() {
        val c = OnboardingController()
        c.startMain(resume = DONE)
        assertEquals(DONE, c.step)
        assertNull(c.next())
        assertNull(c.step)
    }

    @Test fun aResumeThatNoLongerAppliesStartsAtTheHandheld() {
        val c = OnboardingController()
        c.context = OnboardingContext(handheld = false)
        c.startMain(resume = SAVE_FORMATS)                // no handheld: formats are not in the list
        assertEquals(HANDHELD, c.step)
    }

    @Test fun welcomeHasNoPosition() {
        val c = OnboardingController()
        c.startWelcome()
        assertEquals(WELCOME, c.step)
        assertEquals(Pair(0, 0), c.position())
        assertEquals(WELCOME, c.back())
    }

    @Test fun textSizeComesAfterTheWelcomeUnnumbered() {
        val c = OnboardingController()
        c.startTextSize()
        assertEquals(TEXT_SIZE, c.step)
        assertEquals(Pair(0, 0), c.position())
        assertEquals(WELCOME, c.back())
        assertTrue(TEXT_SIZE !in onboardingSteps(OnboardingContext(customise = true, padPage = true, buttons = true)))
    }

    @Test fun customisingAddsItsPagesByThePadContext() {
        fun steps(padPage: Boolean, buttons: Boolean) = onboardingSteps(OnboardingContext(handheld = false, customise = true, padPage = padPage, buttons = buttons))
        val tabs = listOf(TAB_HOME, TAB_CONSOLES, TAB_COLLECTIONS, TAB_TOOLS, GAME_LISTS)
        assertEquals(listOf(HANDHELD, IMPORT_SAVES, ARTWORK, RA_SIGN_IN, CUSTOMISE, PAD, TITLE) + tabs + listOf(ACCENT, DONE), steps(padPage = true, buttons = true))
        assertEquals(listOf(HANDHELD, IMPORT_SAVES, ARTWORK, RA_SIGN_IN, CUSTOMISE, PAD) + tabs + listOf(ACCENT, DONE), steps(padPage = true, buttons = false))   // pad off, no controller
        assertEquals(listOf(HANDHELD, IMPORT_SAVES, ARTWORK, RA_SIGN_IN, CUSTOMISE, TITLE) + tabs + listOf(ACCENT, DONE), steps(padPage = false, buttons = true))   // a controller
        assertEquals(listOf(HANDHELD, IMPORT_SAVES, ARTWORK, RA_SIGN_IN, CUSTOMISE, ACCENT, DONE), onboardingSteps(OnboardingContext(handheld = false, customise = false)))
    }

    // the step list follows a changed context; next() and back() go on from where it is, even off the list.
    @Test fun nextFollowsAContextChangedMidWay() {
        val c = OnboardingController()
        c.context = OnboardingContext(handheld = false, customise = true, padPage = true, buttons = true)
        c.startMain(resume = PAD)
        c.context = c.context.copy(buttons = false)   // the pad turned off on PAD
        assertEquals(TAB_HOME, c.next())
        c.context = c.context.copy(padPage = false, buttons = true)   // a controller now: PAD is off the list
        c.startMain(resume = TITLE)
        c.context = c.context.copy(buttons = false)   // and gone again: TITLE is off the list
        assertEquals(TAB_HOME, c.next())
        c.startMain(resume = TAB_HOME)
        assertEquals(CUSTOMISE, c.back())
    }

    // a page the context took off the list still counts where it stands, never "Step 0".
    @Test fun positionCountsAStepOffTheList() {
        val c = OnboardingController()
        c.context = OnboardingContext(handheld = false, customise = true, padPage = false, buttons = true)
        c.startMain(resume = TITLE)
        val (n, m) = c.position()
        c.context = c.context.copy(buttons = false)   // rotated: TITLE no longer applies
        assertEquals(Pair(n, m), c.position())        // TITLE kept in place and in the total
    }

    // The bottom screen question sits right after the text size (before the folder steps), unnumbered; it is not in the
    // post-folder list, which starts at the handheld either way.
    @Test fun theBottomScreenStepFollowsTheTextSizeUnnumbered() {
        assertEquals(
            listOf(HANDHELD, IMPORT_SAVES, ARTWORK, RA_SIGN_IN, CUSTOMISE, ACCENT, DONE),
            onboardingSteps(OnboardingContext(handheld = false)),
        )
        assertTrue(BOTTOM_SCREEN !in onboardingSteps(OnboardingContext(customise = true, padPage = true, buttons = true)))
        val c = OnboardingController()
        c.startTextSize()
        c.startBottomScreen()
        assertEquals(BOTTOM_SCREEN, c.step)
        assertEquals(Pair(0, 0), c.position())
        assertFalse(c.isFirstMain())
        assertEquals(TEXT_SIZE, c.back())
        assertEquals(WELCOME, c.back())
        c.startMain(resume = BOTTOM_SCREEN)               // never saved, never resumed: the first post-folder step
        assertEquals(HANDHELD, c.step)
        assertTrue(c.isFirstMain())
        assertEquals(Pair(1, 7), c.position())
    }

    // The controller's SELECT / START / MENU page sits right after the text size, unnumbered, before the bottom screen.
    @Test fun systemButtonsBackGoesToTextSize() {
        val c = OnboardingController()
        c.startTextSize()
        c.startSystemButtons()
        assertEquals(SYSTEM_BUTTONS, c.step)
        assertEquals(Pair(0, 0), c.position())
        assertFalse(c.isFirstMain())
        assertEquals(TEXT_SIZE, c.back())
        assertTrue(SYSTEM_BUTTONS !in onboardingSteps(OnboardingContext(customise = true, padPage = true, buttons = true)))
    }

    @Test fun bottomScreenBackGoesToSystemButtonsWithController() {
        val c = OnboardingController()
        c.startBottomScreen()
        assertEquals(SYSTEM_BUTTONS, c.back(controller = true))
        c.startBottomScreen()
        assertEquals(TEXT_SIZE, c.back(controller = false))
    }
}
