package com.nxredux.mobile.kids

import com.nxredux.mobile.kids.KidsAlarms.Alarm
import com.nxredux.mobile.kids.KidsAlarms.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The boundary alarms for a mode: the warning at end − 10 min (once per end), then the lock at the end. */
class KidsAlarmsTest {
    private val min = 60_000L
    private val now = 1_000_000_000L

    @Test fun openPlansWarnThenLock() {
        val until = now + 60 * min
        assertEquals(
            listOf(Alarm(Kind.WARN, until - 10 * min), Alarm(Kind.LOCK, until)),
            KidsAlarms.plan(KidsMode.Open(until), now, warnedFor = 0L),
        )
    }

    @Test fun underTenMinutesWarnsNow() {
        val until = now + 5 * min
        assertEquals(
            listOf(Alarm(Kind.WARN, now), Alarm(Kind.LOCK, until)),
            KidsAlarms.plan(KidsMode.Open(until), now, warnedFor = 0L),
        )
    }

    @Test fun alreadyWarnedSkipsWarn() {
        val until = now + 5 * min
        assertEquals(listOf(Alarm(Kind.LOCK, until)), KidsAlarms.plan(KidsMode.Open(until), now, warnedFor = until))
        // A warning given for an earlier end does not count for a new, later one.
        val later = now + 90 * min
        assertEquals(
            listOf(Alarm(Kind.WARN, later - 10 * min), Alarm(Kind.LOCK, later)),
            KidsAlarms.plan(KidsMode.Open(later), now, warnedFor = until),
        )
    }

    @Test fun lockedPlansNothing() {
        assertEquals(emptyList<Alarm>(), KidsAlarms.plan(KidsMode.Locked(null), now, 0L))
        assertEquals(emptyList<Alarm>(), KidsAlarms.plan(KidsMode.Locked(NextOpen(5, 540, 1)), now, 0L))
        assertEquals(emptyList<Alarm>(), KidsAlarms.plan(KidsMode.Off, now, 0L))
        assertEquals(emptyList<Alarm>(), KidsAlarms.plan(KidsMode.Parent, now, 0L))
    }

    @Test fun reschedulesFromNow() {
        val until = now + 60 * min
        val mode = KidsMode.Open(until)
        for (later in listOf(now + 30 * min, now + 52 * min, now + 59 * min)) {
            val alarms = KidsAlarms.plan(mode, later, warnedFor = 0L)
            assertTrue("no alarm in the past at $later: $alarms", alarms.all { it.atMs >= later })
            assertEquals(Kind.LOCK, alarms.last().kind)
            assertEquals(until, alarms.last().atMs)
        }
        assertEquals(Alarm(Kind.WARN, now + 52 * min), KidsAlarms.plan(mode, now + 52 * min, 0L).first())
    }

    @Test fun warningGoesToExactlyOnePlace() {
        assertEquals(WarnRoute.MENU, warnRoute(menuResumed = true, gameInFront = false))
        assertEquals(WarnRoute.MENU, warnRoute(menuResumed = true, gameInFront = true))
        assertEquals(WarnRoute.GAME, warnRoute(menuResumed = false, gameInFront = true))
        // A game suspended in its own task (live, not in front) or another app in front: the notification.
        assertEquals(WarnRoute.NOTIFICATION, warnRoute(menuResumed = false, gameInFront = false))
    }

    @Test fun bornLockedWithALiveGameLocks() {
        assertTrue(lockAtBirth(KidsMode.Locked(null), gameLive = true))
        assertFalse(lockAtBirth(KidsMode.Locked(null), gameLive = false))
        assertFalse(lockAtBirth(KidsMode.Open(now + min), gameLive = true))
        assertFalse(lockAtBirth(KidsMode.Parent, gameLive = true))
        assertFalse(lockAtBirth(KidsMode.Off, gameLive = true))
    }
}
