package com.nxredux.mobile.kids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardDecisionTest {
    private val own = "com.nxredux.mobile"
    private val allowed = setOf("com.example.drawing", "com.example.books")
    private val exempt = setOf("com.android.systemui", "com.google.android.inputmethod.latin")

    @Test fun allowedAppIsLeftAlone() =
        assertFalse(guardShouldBounce("com.example.drawing", own, allowed, exempt))

    @Test fun otherAppBounces() {
        assertTrue(guardShouldBounce("com.google.android.youtube", own, allowed, exempt))
        assertTrue(guardShouldBounce("com.android.settings", own, allowed, exempt))
    }

    @Test fun ownPackageNeverBounces() {
        assertFalse(guardShouldBounce(own, own, emptySet(), emptySet()))
        assertFalse(guardShouldBounce("com.nxredux.mobile.dev", "com.nxredux.mobile.dev", allowed, exempt))
    }

    @Test fun exemptPackagesNeverBounce() {
        for (p in exempt) assertFalse(guardShouldBounce(p, own, allowed, exempt))
    }

    @Test fun nullPackageDoesNothing() {
        assertFalse(guardShouldBounce(null, own, allowed, exempt))
        assertFalse(guardShouldBounce("", own, allowed, exempt))
    }

    @Test fun allowedPackagesFromAppIds() =
        assertEquals(
            setOf("com.example.drawing", "com.example.books"),
            guardAllowedPackages(listOf("com.example.drawing/.Main", "com.example.books/com.example.books.Home", "")),
        )

    @Test fun exemptHasGrantScreensOnlyWhileGranting() {
        val ime = "com.example.keyboard/.Ime"
        val off = guardExempt(ime, granting = false)
        assertEquals(setOf("com.example.keyboard", "com.android.systemui"), off)
        val on = guardExempt(ime, granting = true)
        assertTrue(on.containsAll(off + setOf("com.android.settings", "com.google.android.permissioncontroller", "com.android.permissioncontroller")))
        assertEquals(setOf("com.android.systemui"), guardExempt(null, granting = false))
    }

    @Test fun runsOnlyWhileLockedWithAccessAndNoMenu() {
        val locked = KidsMode.Locked(null)
        assertTrue(guardRuns(locked, usageAccess = true, menuShown = false))
        assertFalse(guardRuns(locked, usageAccess = false, menuShown = false))
        assertFalse(guardRuns(locked, usageAccess = true, menuShown = true))
        for (m in listOf(KidsMode.Off, KidsMode.Parent, KidsMode.Open(1L))) assertFalse(guardRuns(m, usageAccess = true, menuShown = false))
    }
}
