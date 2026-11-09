package com.nxredux.mobile.gametime

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LazyGameTimeDataTest {
    /** Answers [grandTotal] with [total]; nothing else is called. */
    private class Fixed(private val total: Long) : GameTimeData {
        override suspend fun grandTotal() = total
        override suspend fun totals() = error("unused")
        override suspend fun members(target: Long) = error("unused")
        override suspend fun sessions(target: Long) = error("unused")
        override suspend fun mergeCandidates(target: Long) = error("unused")
        override suspend fun merge(from: Long, into: Long, name: String) = error("unused")
        override suspend fun unmerge(gameId: Long) = error("unused")
        override suspend fun delete(target: Long) = error("unused")
        override suspend fun byPath() = error("unused")
        override suspend fun window(sinceMs: Long, nowMs: Long) = error("unused")
        override suspend fun playedPaths(sinceMs: Long, nowMs: Long) = error("unused")
        override suspend fun renamePaths(map: Map<String, String>) = error("unused")
    }

    @Test
    fun followsAStoreReopenedAtAnotherDataRoot() = runTest {
        var current: GameTimeData = Fixed(5)
        val data = LazyGameTimeData { current }
        assertEquals(5, data.grandTotal())
        current = Fixed(9)   // All files access back on: the opener now answers the home folder's database
        assertEquals(9, data.grandTotal())
    }

    @Test
    fun aStoreThatCannotOpenFailsTheCallAndTheNextOneTriesAgain() = runTest {
        var current: GameTimeData? = null
        val data = LazyGameTimeData { current }
        assertFailsWith<IllegalStateException> { data.grandTotal() }
        current = Fixed(3)
        assertEquals(3, data.grandTotal())
    }
}
