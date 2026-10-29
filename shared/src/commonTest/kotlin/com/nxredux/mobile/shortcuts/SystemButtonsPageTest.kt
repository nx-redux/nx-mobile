package com.nxredux.mobile.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The SELECT / START / MENU page: press the three, then bind what wasn't found. */
class SystemButtonsPageTest {
    private val model = PadModel(0x4001, 0x0428, "AYANEO Controller")
    private val aya = PhysicalKey.of(0, 0x2f1)
    private fun key(code: Int) = PhysicalKey.of(code, 0)
    private fun page() = SystemButtonsPage(model, SystemButtonMap())

    /** A full press (down, then up); the down's result. */
    private fun SystemButtonsPage.tap(k: PhysicalKey): Boolean = onKey(k, true, false).also { onKey(k, false, false) }

    @Test fun allStandardFinishes() {
        val p = page()
        assertTrue(p.tap(key(KeyCodes.BUTTON_SELECT)))
        assertTrue(p.tap(key(KeyCodes.BUTTON_START)))
        assertTrue(p.tap(key(KeyCodes.BUTTON_MODE)))
        assertEquals(SystemButtonsPage.Phase.DONE, p.phase)
        assertEquals(SystemRole.entries.toSet(), p.found)
    }

    @Test fun otherKeyIsNotOneOfThree() {
        val p = page()
        assertTrue(p.tap(key(KeyCodes.BUTTON_THUMBL)))
        assertTrue(p.lastOther)
        assertEquals(SystemButtonsPage.Phase.PRESS, p.phase)
        p.tap(key(KeyCodes.BUTTON_SELECT))
        assertFalse(p.lastOther)
    }

    @Test fun navigationKeysPassThrough() {
        val p = page()
        assertFalse(p.tap(key(KeyCodes.BUTTON_A)))
        assertFalse(p.tap(key(KeyCodes.BUTTON_B)))
        assertFalse(p.tap(key(KeyCodes.DPAD_DOWN)))
        assertFalse(p.tap(key(KeyCodes.BACK)))
        assertFalse(p.lastOther)
    }

    @Test fun holdingStartDoesNothing() {
        // "I've pressed them all" is the row only: a held START is a press like any other.
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT))
        assertTrue(p.onKey(key(KeyCodes.BUTTON_START), true, false))
        assertTrue(p.onKey(key(KeyCodes.BUTTON_START), true, true))
        assertEquals(SystemButtonsPage.Phase.PRESS, p.phase)
    }

    @Test fun startRepeatBeforeStartIsKnownDoesNothing() {
        val p = page()
        val l3 = key(KeyCodes.BUTTON_THUMBL)
        p.onKey(l3, true, false)
        p.onKey(l3, true, true)
        assertEquals(SystemButtonsPage.Phase.PRESS, p.phase)
    }

    @Test fun aya() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT))
        p.tap(key(KeyCodes.BUTTON_START))
        assertTrue(p.tap(aya))
        assertTrue(p.lastOther)
        p.pressedAll()
        assertEquals(SystemRole.MENU, p.binding)
        assertTrue(p.tap(aya))
        assertEquals(SystemButtonsPage.Phase.DONE, p.phase)
        assertEquals(110, SystemButtons.translate(p.map, model.key, 0, 0x2f1))
    }

    @Test fun bAndBackDontBind() {
        for (cancel in listOf(KeyCodes.BUTTON_B, KeyCodes.BACK)) {
            val p = page()
            p.tap(key(KeyCodes.BUTTON_SELECT)); p.tap(key(KeyCodes.BUTTON_START))
            p.pressedAll()
            assertTrue(p.tap(key(cancel)))
            assertEquals(SystemButtonsPage.Phase.PRESS, p.phase)
            assertNull(p.binding)
            assertEquals(SystemButtonMap(), p.map)
        }
    }

    @Test fun pressHeldBeforeBindIsIgnored() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT))
        val start = key(KeyCodes.BUTTON_START)
        p.onKey(start, true, false)
        p.pressedAll()               // the row chosen by touch while START is still held: into BIND for MENU
        p.onKey(start, true, true)   // still held: neither binds nor skips MENU
        assertEquals(SystemButtonsPage.Phase.BIND, p.phase)
        assertEquals(SystemRole.MENU, p.binding)
        p.onKey(start, false, false)
        assertEquals(SystemButtonMap(), p.map)
    }

    @Test fun aFoundRolesButtonIsNotTakenForAnother() {
        // A pad with no SELECT: its START button must not become SELECT (START would be left on a code it never sends).
        val p = page()
        p.tap(key(KeyCodes.BUTTON_START)); p.tap(key(KeyCodes.BUTTON_MODE))
        p.pressedAll()
        assertEquals(SystemRole.SELECT, p.binding)
        assertTrue(p.tap(key(KeyCodes.BUTTON_START)))
        assertEquals(SystemRole.START, p.lastTaken)
        assertEquals(SystemRole.SELECT, p.binding)
        assertEquals(SystemButtonMap(), p.map)
    }

    @Test fun skipMenu() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT))
        p.tap(key(KeyCodes.BUTTON_MODE))
        p.pressedAll()
        assertEquals(SystemRole.START, p.binding)
        p.skipMenu()
        assertEquals(SystemButtonsPage.Phase.BIND, p.phase)
        p.tap(key(KeyCodes.BUTTON_THUMBR))
        assertEquals(SystemButtonsPage.Phase.DONE, p.phase)

        val q = page()
        q.tap(key(KeyCodes.BUTTON_SELECT)); q.tap(key(KeyCodes.BUTTON_START))
        q.pressedAll()
        q.skipMenu()
        assertEquals(SystemButtonsPage.Phase.DONE, q.phase)
        assertTrue(q.menuSkipped)
    }

    @Test fun selectMissingAsksSelectFirst() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_START)); p.tap(key(KeyCodes.BUTTON_MODE))
        p.pressedAll()
        assertEquals(SystemRole.SELECT, p.binding)
    }

    @Test fun pressedAllWithAllFoundIsDone() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT)); p.tap(key(KeyCodes.BUTTON_START))
        p.pressedAll()
        p.tap(key(KeyCodes.BUTTON_THUMBL))   // MENU bound to L3
        assertEquals(SystemButtonsPage.Phase.DONE, p.phase)
        assertEquals(110, SystemButtons.translate(p.map, model.key, KeyCodes.BUTTON_THUMBL, 0))
    }

    @Test fun holdingStartWhileBindingMenuDoesNotSkip() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT))
        val start = key(KeyCodes.BUTTON_START)
        p.tap(start)
        p.pressedAll()
        assertTrue(p.onKey(start, true, false))   // START is taken: nothing binds
        assertEquals(SystemRole.START, p.lastTaken)
        assertTrue(p.onKey(start, true, true))    // held: still waiting (Skip is the row's)
        assertEquals(SystemButtonsPage.Phase.BIND, p.phase)
        assertEquals(SystemButtonMap(), p.map)
    }

    // Review fix 2: in BIND, A and the d-pad choose the page's rows (Skip / Cancel); they never bind.
    @Test fun aAndDpadDontBindInBind() {
        val p = page()
        p.tap(key(KeyCodes.BUTTON_SELECT)); p.tap(key(KeyCodes.BUTTON_START))
        p.pressedAll()
        for (code in listOf(KeyCodes.BUTTON_A, KeyCodes.DPAD_DOWN, KeyCodes.DPAD_UP)) {
            assertFalse(p.tap(key(code)))
            assertEquals(SystemRole.MENU, p.binding)
        }
        assertEquals(SystemButtonMap(), p.map)
    }
}
