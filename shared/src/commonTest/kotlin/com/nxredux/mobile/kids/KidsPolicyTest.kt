package com.nxredux.mobile.kids

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KidsPolicyTest {

    // Monday 2026-11-02 00:00 UTC.
    private val base = 1_793_577_600_000L
    private val dayMs = 86_400_000L

    private fun ms(day: Int, hhmm: String): Long {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return (day - 1) * dayMs + (h * 60L + m) * 60_000L
    }

    private fun at(day: Int, hhmm: String): LocalNow {
        val inDay = ms(1, hhmm)
        return LocalNow(base + ms(day, hhmm), day, inDay)
    }

    private fun end(day: Int, hhmm: String) = base + ms(day, hhmm)

    private fun mode(now: LocalNow, unlock: Long = 0L, schedule: KidsSchedule = KidsSchedule.DEFAULT) =
        kidsMode(enabled = true, parent = false, now = now, schedule = schedule, unlockUntilMs = unlock)

    @Test fun defaultLocksMonToThuAndOpensFriToSun() {
        assertEquals(KidsMode.Locked(NextOpen(5, 540, 3)), mode(at(2, "15:00")))
        assertEquals(KidsMode.Open(end(5, "20:00")), mode(at(5, "10:00")))
        assertEquals(KidsMode.Locked(NextOpen(6, 540, 1)), mode(at(5, "20:00")))
    }

    @Test fun unlockInsideEarlierEndingWindowExtends() {
        val now = at(5, "19:00")
        assertEquals(KidsMode.Open(end(5, "21:00")), mode(now, unlockUntil(now, UnlockChip.HOUR2)))
    }

    @Test fun windowOutlastsShortUnlock() {
        val now = at(5, "10:00")
        assertEquals(KidsMode.Open(end(5, "20:00")), mode(now, unlockUntil(now, UnlockChip.MIN30)))
    }

    @Test fun unlockCrossesMidnight() {
        val now = at(7, "23:00")
        val until = unlockUntil(now, UnlockChip.HOUR2)
        assertEquals(KidsMode.Open(base + 7 * dayMs + 3_600_000L), mode(now, until))
        val later = LocalNow(base + 7 * dayMs + 30 * 60_000L, 1, 30 * 60_000L)
        assertEquals(KidsMode.Open(until), mode(later, until))
    }

    @Test fun restOfTodayEndsAtMidnight() {
        val now = at(2, "15:00")
        assertEquals(end(3, "00:00"), unlockUntil(now, UnlockChip.TODAY))
    }

    @Test fun stateFollowsTheClockNotHistory() {
        val now = at(2, "15:00")
        val until = unlockUntil(now, UnlockChip.MIN30)
        assertEquals(KidsMode.Open(until), mode(now, until))
        assertEquals(KidsMode.Locked(NextOpen(5, 540, 3)), mode(at(2, "15:30"), until))
    }

    @Test fun parentAndOffWin() {
        val now = at(2, "15:00")
        assertEquals(KidsMode.Off, kidsMode(false, true, now, KidsSchedule.DEFAULT, 0L))
        assertEquals(KidsMode.Parent, kidsMode(true, true, now, KidsSchedule.DEFAULT, 0L))
    }

    @Test fun warnIsTenMinutesBefore() {
        assertEquals(1_000_000L - 600_000L, warnAtMs(1_000_000L))
    }

    @Test fun scheduleRoundTrips() {
        assertEquals("1=-;2=-;3=-;4=-;5=540-1200;6=540-1200;7=540-1200", KidsSchedule.DEFAULT.encode())
        val s = KidsSchedule.DEFAULT.withDay(2, PlayWindow(600, 660)).withDay(5, null)
        assertEquals(s, KidsSchedule.decode(s.encode()))
        assertEquals(PlayWindow(600, 660), s.days[2])
        assertNull(s.days[5])
    }

    @Test fun decodeGarbageIsDefault() {
        assertEquals(KidsSchedule.DEFAULT, KidsSchedule.decode(null))
        assertEquals(KidsSchedule.DEFAULT, KidsSchedule.decode(""))
        assertEquals(KidsSchedule.DEFAULT, KidsSchedule.decode("nonsense"))
        assertEquals(KidsSchedule.DEFAULT, KidsSchedule.decode("1=-;2=-;3=-;4=-;5=1200-540;6=-;7=-"))
        assertEquals(KidsSchedule.DEFAULT, KidsSchedule.decode("1=-;2=-"))
    }

    @Test fun nextOpenLabels() {
        assertEquals("Games open Today 9:00 AM", nextOpenLabel(NextOpen(2, 540, 0)))
        assertEquals("Games open Tomorrow 12:30 PM", nextOpenLabel(NextOpen(3, 750, 1)))
        assertEquals("Games open Friday 9:00 AM", nextOpenLabel(NextOpen(5, 540, 3)))
        assertEquals("Games open Sunday 12:05 AM", nextOpenLabel(NextOpen(7, 5, 5)))
        // Only today's window, already past: the same weekday next week.
        assertEquals("Games open next Monday 9:00 AM", nextOpenLabel(NextOpen(1, 540, 7)))
    }

    /** The game process's own check at its start: Locked by the stored state, unless the main process holds Parent. */
    @Test fun gameStartBlockedOnlyWhileLockedAndNoParent() {
        var asked = 0
        val parentNo = { asked++; false }
        assertEquals(true, kidsGameBlocked(true, at(2, "15:00"), KidsSchedule.DEFAULT, 0L, parentNo))
        assertEquals(1, asked)
        assertEquals(false, kidsGameBlocked(true, at(2, "15:00"), KidsSchedule.DEFAULT, 0L) { true })   // a parent's game
        assertEquals(false, kidsGameBlocked(true, at(5, "10:00"), KidsSchedule.DEFAULT, 0L, parentNo))  // a play window
        assertEquals(false, kidsGameBlocked(true, at(2, "15:00"), KidsSchedule.DEFAULT, end(2, "16:00"), parentNo))  // a timed unlock
        assertEquals(false, kidsGameBlocked(false, at(2, "15:00"), KidsSchedule.DEFAULT, 0L, parentNo))  // Kids Mode off
        assertEquals(1, asked)   // the main process is asked only when the stored state says Locked
    }

    /** The game's input reaches the main process (Parent mode's idle time) at most every 30 s. */
    @Test fun gameInputReportedAtMostEvery30s() {
        assertEquals(true, kidsInputDue(lastMs = -1L, nowMs = 0L))
        assertEquals(false, kidsInputDue(lastMs = 1_000L, nowMs = 30_999L))
        assertEquals(true, kidsInputDue(lastMs = 1_000L, nowMs = 31_000L))
    }

    @Test fun todaysLaterWindowComesFirstAndAllLockedIsNull() {
        assertEquals(KidsMode.Locked(NextOpen(5, 540, 0)), mode(at(5, "08:00")))
        val none = KidsSchedule((1..7).associateWith { null })
        assertEquals(KidsMode.Locked(null), mode(at(2, "15:00"), schedule = none))
        // Only today has a window and it has passed: same weekday next week.
        val onlyFri = none.withDay(5, PlayWindow(540, 1200))
        assertEquals(KidsMode.Locked(NextOpen(5, 540, 7)), mode(at(5, "21:00"), schedule = onlyFri))
    }
}
