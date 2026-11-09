package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastSlotWaitersTest {
    @Test fun aLaterRefreshWithoutACallbackKeepsTheWaitingOne() {
        val got = mutableListOf<Int>()
        val waiters = LastSlotWaiters()
        waiters.add { got += it }   // openLocal's refresh, then cancelled by a plain refresh
        waiters.add(null)
        waiters.takeAll().forEach { it(4) }   // the plain refresh lands
        assertEquals(listOf(4), got)
    }

    @Test fun eachWaiterRunsOnce() {
        val got = mutableListOf<Int>()
        val waiters = LastSlotWaiters()
        waiters.add { got += it }
        waiters.add { got += it * 10 }
        waiters.takeAll().forEach { it(2) }
        assertTrue(waiters.takeAll().isEmpty())
        assertEquals(listOf(2, 20), got)
    }
}
