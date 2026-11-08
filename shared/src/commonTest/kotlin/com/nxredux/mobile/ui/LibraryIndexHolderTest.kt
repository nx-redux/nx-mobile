package com.nxredux.mobile.ui

import com.nxredux.mobile.library.ArtRefs
import com.nxredux.mobile.library.IndexedGame
import com.nxredux.mobile.library.IndexedSystem
import com.nxredux.mobile.library.LibraryIndex
import com.nxredux.mobile.library.LibraryIndexCodec
import com.nxredux.mobile.storage.GameRef
import com.nxredux.mobile.storage.MemoryLibraryIndexStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryIndexHolderTest {
    private fun game(file: String) = IndexedGame(source = "home", dir = "Roms/GB", file = file, ref = "r/$file", folder = "f", name = file)

    private fun index(games: Int) = LibraryIndex(
        version = LibraryIndex.VERSION, home = "h", extras = emptyList(), defaultTags = emptyMap(),
        systems = listOf(IndexedSystem("gb", "Game Boy", games = (0 until games).map { game("g$it.gb") })),
    )

    private class Shown { val indexes = ArrayList<LibraryIndex>() }

    private fun holder(scope: CoroutineScope, store: MemoryLibraryIndexStore, shown: Shown) =
        LibraryIndexHolder(scope, store, Dispatchers.Unconfined) { shown.indexes += it }

    @Test
    fun mutateWritesTheNewIndexAndSkipsTheSameInstance() = runTest {
        val store = MemoryLibraryIndexStore()
        val h = holder(backgroundScope, store, Shown())
        val ix = index(2)
        assertSame(ix, h.mutate { ix })
        assertSame(ix, h.snapshot)
        assertEquals(1, store.writes)
        assertNull(h.mutate { it })   // the same instance: no change, no write
        assertNull(h.mutate { null })
        assertEquals(1, store.writes)
        assertEquals(ix, LibraryIndexCodec.decode(store.text!!))
    }

    @Test
    fun aFailedWriteDeletesTheStoredCopy() = runTest {
        val store = MemoryLibraryIndexStore().apply { failWrites = true }
        val h = holder(backgroundScope, store, Shown())
        h.mutate { index(1) }
        assertEquals(1, store.deletes)
    }

    @Test
    fun artResultsWaitForTheQuietPeriodThenOneWriteAndOneShow() = runTest {
        val store = MemoryLibraryIndexStore()
        val shown = Shown()
        val h = holder(backgroundScope, store, shown)
        h.mutate { index(3) }
        val writes = store.writes
        repeat(3) { assertTrue(h.applyArt(GameRef("home", "Roms/GB", "g$it.gb"), ArtRefs(boxart = "b$it"))) }
        assertFalse(h.applyArt(GameRef("home", "Roms/GB", "missing.gb"), ArtRefs(boxart = "x")))
        assertEquals(writes, store.writes)   // in memory only until the debounce
        assertEquals(listOf("b0", "b1", "b2"), h.snapshot!!.systems[0].games.map { it.boxart })
        advanceTimeBy(ART_QUIET_MS * 2 + 1); runCurrent()
        assertEquals(writes + 1, store.writes)
        assertEquals(1, shown.indexes.size)
        assertSame(h.snapshot, shown.indexes.single())
        h.flushArt()   // nothing waiting: no second write or show
        assertEquals(writes + 1, store.writes)
        assertEquals(1, shown.indexes.size)
    }

    @Test
    fun aWholeIndexWriteCarriesTheArtWaiting() = runTest {
        val store = MemoryLibraryIndexStore()
        val shown = Shown()
        val h = holder(backgroundScope, store, shown)
        h.mutate { index(1) }
        h.applyArt(GameRef("home", "Roms/GB", "g0.gb"), ArtRefs(boxart = "b"))
        h.mutate { it!!.copy(scannedAt = 5) }   // written whole, art in it
        val writes = store.writes
        h.flushArt()
        assertEquals(writes, store.writes)   // not written again
        assertEquals(1, shown.indexes.size)   // but still shown once
    }

    /** Art results from many threads at once: none is lost, as every change runs under the one lock. */
    @Test
    fun concurrentArtResultsAreAllKept() = runTest {
        val store = MemoryLibraryIndexStore()
        val h = holder(backgroundScope, store, Shown())
        val n = 300
        h.mutate { index(n) }
        withContext(Dispatchers.Default) {
            repeat(n) { i -> launch { h.applyArt(GameRef("home", "Roms/GB", "g$i.gb"), ArtRefs(boxart = "b$i")) } }
        }
        h.flushArt()
        assertEquals((0 until n).map { "b$it" }, h.snapshot!!.systems[0].games.map { it.boxart })
        assertEquals((0 until n).map { "b$it" }, LibraryIndexCodec.decode(store.text!!)!!.systems[0].games.map { it.boxart })
    }

    @Test
    fun forgetClearsTheIndexAndTheStoredCopy() = runTest {
        val store = MemoryLibraryIndexStore()
        val h = holder(backgroundScope, store, Shown())
        h.mutate { index(1) }
        h.forget()
        assertNull(h.snapshot)
        assertNull(h.current())
        assertEquals(1, store.deletes)
    }

    @Test
    fun fittingFallsBackToTheStoredCopy() = runTest {
        val store = MemoryLibraryIndexStore()
        val stored = index(2)
        store.text = LibraryIndexCodec.encode(stored)
        val h = holder(backgroundScope, store, Shown())
        assertEquals(stored, h.fitting { it.home == "h" })
        assertEquals(stored, h.snapshot)
        assertNull(holder(backgroundScope, MemoryLibraryIndexStore(store.text), Shown()).fitting { false })
    }
}
