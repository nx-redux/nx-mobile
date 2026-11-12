package com.nxredux.mobile.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RaProfilesTest {
    private fun game(title: String, unlocked: Int, total: Int) = RaGameView(title.padEnd(32, 'a'), title.hashCode(), title, null, unlocked, total, 0)

    @Test fun sumsTheStartedGames() {
        val p = RaProfiles.of(listOf(game("A", 3, 10), game("B", 5, 5), game("C", 0, 20), game("D", 1, 4)), emptyList())
        assertEquals(9, p.achievements)
        assertEquals(3, p.played)       // C was never started
        assertEquals(1, p.mastered)     // B
        assertFalse(p.empty)
    }

    @Test fun inProgressIsNearestToMasteryFirstAndNeverMasteredOrUnstarted() {
        val games = listOf(game("Low", 1, 10), game("High", 9, 10), game("Done", 4, 4), game("None", 0, 3), game("Mid", 5, 10), game("Also mid", 2, 4))
        assertEquals(listOf("High", "Also mid", "Mid"), RaProfiles.of(games, emptyList()).inProgress.map { it.title })
    }

    @Test fun keepsTheNewestUnlocksOnly() {
        val recent = (1..6).map { RaProfileUnlock("a$it", "g", it.toLong()) }
        assertEquals(RaProfiles.RECENT, RaProfiles.of(emptyList(), recent).recent.size)
    }

    @Test fun nothingUnlockedIsEmpty() {
        assertTrue(RaProfiles.of(listOf(game("A", 0, 10)), emptyList()).empty)
        assertTrue(RaProfiles.of(emptyList(), emptyList()).empty)
    }
}
