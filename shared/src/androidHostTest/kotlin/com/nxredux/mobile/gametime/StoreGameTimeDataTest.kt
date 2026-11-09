package com.nxredux.mobile.gametime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreGameTimeDataTest {
    @Test fun aFailedCloseOfLeftSessionsStillAnswersTheReads() = runTest {
        var clock = 10_000_000L
        var broken = false
        val (store, _) = testStore({ if (broken) error("clock unavailable") else clock })
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 60_000; store.end(id)
        val data = StoreGameTimeData(store, Dispatchers.Unconfined)
        broken = true   // the close of left sessions (a write) fails from here on; the reads need no clock
        assertEquals(listOf("Tetris"), data.totals().map { it.name })
        assertEquals(setOf("/Roms/GB/Tetris.gb"), data.byPath().keys)
        assertEquals(60_000L, data.window(0, 20_000_000L).totalMs)
    }
}
