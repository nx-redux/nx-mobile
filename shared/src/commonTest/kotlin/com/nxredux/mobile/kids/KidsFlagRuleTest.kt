package com.nxredux.mobile.kids

import kotlin.test.Test
import kotlin.test.assertEquals

class KidsFlagRuleTest {
    @Test fun disabledKeeps() =
        assertEquals(FlagAction.Keep, flagAction(false, "a", "a", FlagSeen.MISSING))

    @Test fun sameHomePresentKeeps() =
        assertEquals(FlagAction.Keep, flagAction(true, "a", "a", FlagSeen.PRESENT))

    @Test fun sameHomeMissingTurnsOff() =
        assertEquals(FlagAction.TurnOff, flagAction(true, "a", "a", FlagSeen.MISSING))

    @Test fun unreadableKeeps() {
        assertEquals(FlagAction.Keep, flagAction(true, "a", "a", FlagSeen.UNREADABLE))
    }

    @Test fun homeChangedRewrites() {
        for (s in FlagSeen.entries) assertEquals(FlagAction.Rewrite("b"), flagAction(true, "a", "b", s))
        assertEquals(FlagAction.Rewrite("b"), flagAction(true, null, "b", FlagSeen.MISSING))
    }

    @Test fun noCurrentHomeKeeps() =
        assertEquals(FlagAction.Keep, flagAction(true, "a", null, FlagSeen.MISSING))
}
