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

    /** A lock alarm locks whenever it finds Locked with no menu shown (a cold process, a game, another app), once. */
    @Test fun lockAlarmLocksUnlessAMenuShowsOrItLockedAlready() {
        val locked = KidsMode.Locked(null)
        assertTrue(lockOnAlarm(locked, menuShown = false, lockedByRefresh = false))
        assertFalse(lockOnAlarm(locked, menuShown = true, lockedByRefresh = false))   // the menu shows the tiles itself
        assertFalse(lockOnAlarm(locked, menuShown = false, lockedByRefresh = true))   // the change to Locked did it
        assertFalse(lockOnAlarm(KidsMode.Open(now + min), menuShown = false, lockedByRefresh = false))
        assertFalse(lockOnAlarm(KidsMode.Parent, menuShown = false, lockedByRefresh = false))
    }

    /** The warning is due from end − 10 min (a minute early still counts), once per end, only while Open. */
    @Test fun warningDueOncePerEndInItsLastTenMinutes() {
        val until = now + 9 * min
        assertTrue(warnDue(KidsMode.Open(until), now, warnedFor = 0L))
        assertFalse(warnDue(KidsMode.Open(until), now, warnedFor = until))
        assertFalse(warnDue(KidsMode.Open(now + 20 * min), now, warnedFor = 0L))
        assertTrue(warnDue(KidsMode.Open(now + 11 * min), now, warnedFor = 0L))   // within the minute's slack
        assertFalse(warnDue(KidsMode.Locked(null), now, warnedFor = 0L))
    }

    /** A warning held while the screen is off is set no more (no wake loop); the screen-on path gives it. */
    @Test fun heldWarningPlansOnlyTheLock() {
        val until = now + 5 * min
        assertEquals(listOf(Alarm(Kind.LOCK, until)), KidsAlarms.planHeld(KidsMode.Open(until), now))
    }
}
