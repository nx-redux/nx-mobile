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

    @Test fun frontsAreTheResumesNotPausedSince() {
        val r = GuardEventType.RESUMED; val p = GuardEventType.PAUSED; val s = GuardEventType.STOPPED
        fun pkgs(vararg e: GuardEvent) = guardFronts(e.toList()).map { it.pkg }
        // A game resumed hours ago and still in front: found (the long first look).
        assertEquals(listOf("g"), pkgs(ev(r, "a", "A", 1), ev(p, "a", "A", 2), ev(r, "g", "G", 3)))
        // The last resume was paused later and nothing came after: nothing in front to judge.
        assertEquals(emptyList(), pkgs(ev(r, "g", "G", 3), ev(p, "g", "G", 4)))
        // A pause of another activity does not cancel the front one; its own stop does.
        assertEquals(listOf("g"), pkgs(ev(r, "g", "G", 3), ev(p, "a", "A", 4)))
        assertEquals(emptyList(), pkgs(ev(r, "g", "G", 3), ev(s, "g", "G", 5)))
        assertEquals(emptyList(), guardFronts(emptyList()))
    }

    @Test fun twoDisplaysKeepBothResumedApps() {
        val r = GuardEventType.RESUMED; val p = GuardEventType.PAUSED
        // A blocked app stays resumed on the top screen while an allowed one resumes on the other display after it.
        val f = guardFronts(listOf(ev(r, "store", "S", 1), ev(r, "chrome", "C", 2)))
        assertEquals(listOf(GuardEvent(r, "store", "S", 1), GuardEvent(r, "chrome", "C", 2)), f)
        // A second resume of the same activity replaces the first, newest last.
        assertEquals(listOf("chrome", "store"), guardFronts(listOf(ev(r, "store", "S", 1), ev(r, "chrome", "C", 2), ev(r, "store", "S", 3))).map { it.pkg })
        // Once paused, it is gone even with the other still resumed.
        assertEquals(listOf("chrome"), guardFronts(listOf(ev(r, "store", "S", 1), ev(r, "chrome", "C", 2), ev(p, "store", "S", 3))).map { it.pkg })
    }

    @Test fun runsOnlyWhileLockedWithAccessAndNoMenu() {
        val locked = KidsMode.Locked(null)
        assertTrue(guardRuns(locked, usageAccess = true, menuShown = false))
        assertFalse(guardRuns(locked, usageAccess = false, menuShown = false))
        assertFalse(guardRuns(locked, usageAccess = true, menuShown = true))
        for (m in listOf(KidsMode.Off, KidsMode.Parent, KidsMode.Open(1L))) assertFalse(guardRuns(m, usageAccess = true, menuShown = false))
    }

    /** Dual mode: an app on the top screen keeps the menu shown below it, and the guard still watches what opens there. */
    @Test fun runsWithTheMenuShownWhileAnAppIsOnTheOtherDisplay() {
        val locked = KidsMode.Locked(null)
        assertTrue(guardRuns(locked, usageAccess = true, menuShown = true, appOnOtherDisplay = true))
        assertFalse(guardRuns(locked, usageAccess = false, menuShown = true, appOnOtherDisplay = true))
        assertFalse(guardRuns(KidsMode.Open(1L), usageAccess = true, menuShown = true, appOnOtherDisplay = true))
    }
}
