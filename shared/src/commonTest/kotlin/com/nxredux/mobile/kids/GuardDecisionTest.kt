package com.nxredux.mobile.kids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardDecisionTest {
    private val own = "com.nxredux.mobile"
    private val allowed = setOf("com.example.drawing", "com.example.books")
    private val exempt = setOf("com.android.systemui", "com.google.android.inputmethod.latin")
    private val game = "com.nxredux.mobile.runner.GameActivity"

    private fun bounce(pkg: String?, own: String = this.own, allowed: Set<String> = this.allowed, exempt: Set<String> = this.exempt, cls: String? = null) =
        guardShouldBounce(pkg, cls, own, game, allowed, exempt)

    @Test fun allowedAppIsLeftAlone() =
        assertFalse(bounce("com.example.drawing"))

    @Test fun otherAppBounces() {
        assertTrue(bounce("com.google.android.youtube"))
        assertTrue(bounce("com.android.settings"))
    }

    @Test fun ownPackageNeverBounces() {
        assertFalse(bounce(own, allowed = emptySet(), exempt = emptySet()))
        assertFalse(bounce("com.nxredux.mobile.dev", own = "com.nxredux.mobile.dev"))
        assertFalse(bounce(own, cls = "com.nxredux.mobile.MainActivity"))
        assertFalse(bounce(own, cls = "com.nxredux.mobile.LaunchActivity"))
    }

    /** Our own game screen resumed while Locked (Recents after Android killed :game) is sent back like a foreign app. */
    @Test fun ownGameScreenBounces() {
        assertTrue(bounce(own, cls = game))
        assertTrue(bounce("com.nxredux.mobile.dev", own = "com.nxredux.mobile.dev", cls = game))
        assertFalse(bounce("com.example.drawing", cls = game))   // another app's class of that name: its package decides
    }

    @Test fun exemptPackagesNeverBounce() {
        for (p in exempt) assertFalse(bounce(p))
    }

    @Test fun nullPackageDoesNothing() {
        assertFalse(bounce(null))
        assertFalse(bounce(""))
    }

    @Test fun allowedPackagesFromAppIds() =
        assertEquals(
            setOf("com.example.drawing", "com.example.books"),
            guardAllowedPackages(listOf("com.example.drawing/.Main", "com.example.books/com.example.books.Home", "")),
        )

    @Test fun exemptHasSettingsOnlyWhileGranting() {
        val ime = "com.example.keyboard/.Ime"
        val always = setOf(
            "com.android.systemui", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "android", "com.android.intentresolver",
        )
        val off = guardExempt(ime, granting = false)
        assertEquals(always + "com.example.keyboard", off)
        assertFalse("com.android.settings" in off)
        assertEquals(off + "com.android.settings", guardExempt(ime, granting = true))
        assertEquals(always, guardExempt(null, granting = false))
    }

    /** An allowed app's own permission dialog and the share sheet stay up while Locked. */
    @Test fun permissionDialogAndShareSheetNeverBounce() {
        val ex = guardExempt(null, granting = false)
        for (p in listOf("com.google.android.permissioncontroller", "com.android.permissioncontroller", "android", "com.android.intentresolver")) {
            assertFalse(bounce(p, exempt = ex), p)
        }
        assertTrue(bounce("com.android.settings", exempt = ex))
    }

    private fun ev(type: GuardEventType, pkg: String, cls: String, at: Long) = GuardEvent(type, pkg, cls, at)

    @Test fun frontIsTheLastResumeNotPausedSince() {
        val r = GuardEventType.RESUMED; val p = GuardEventType.PAUSED; val s = GuardEventType.STOPPED
        // A game resumed hours ago and still in front: found (the long first look).
        assertEquals("g", guardFront(listOf(ev(r, "a", "A", 1), ev(p, "a", "A", 2), ev(r, "g", "G", 3)))?.pkg)
        // The last resume was paused later and nothing came after: nothing in front to judge.
        assertEquals(null, guardFront(listOf(ev(r, "g", "G", 3), ev(p, "g", "G", 4))))
        // A pause of another activity does not cancel the front one; its own stop does.
        assertEquals("G", guardFront(listOf(ev(r, "g", "G", 3), ev(p, "a", "A", 4)))?.cls)
        assertEquals(null, guardFront(listOf(ev(r, "g", "G", 3), ev(s, "g", "G", 5))))
        // The newest unpaused resume wins, with its time.
        val f = guardFront(listOf(ev(r, "a", "A", 1), ev(r, "b", "B", 7), ev(p, "a", "A", 8)))
        assertEquals(GuardEvent(r, "b", "B", 7), f)
        assertEquals(null, guardFront(emptyList()))
    }

    @Test fun runsOnlyWhileLockedWithAccessAndNoMenu() {
        val locked = KidsMode.Locked(null)
        assertTrue(guardRuns(locked, usageAccess = true, menuShown = false))
        assertFalse(guardRuns(locked, usageAccess = false, menuShown = false))
        assertFalse(guardRuns(locked, usageAccess = true, menuShown = true))
        for (m in listOf(KidsMode.Off, KidsMode.Parent, KidsMode.Open(1L))) assertFalse(guardRuns(m, usageAccess = true, menuShown = false))
    }
}
