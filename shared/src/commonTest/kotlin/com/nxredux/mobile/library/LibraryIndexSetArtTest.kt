package com.nxredux.mobile.library

import com.nxredux.mobile.storage.Recents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LibraryIndexSetArtTest {
    private val dir = "Roms/Game Boy (GB)"
    private val g1 = IndexedGame(Recents.HOME, dir, "Tetris.gb", "Home/$dir/Tetris.gb", "Home/$dir", "Tetris", subtitle = null)
    private val g2 = IndexedGame(Recents.HOME, dir, "Zelda.gb", "Home/$dir/Zelda.gb", "Home/$dir", "Zelda", boxart = "old.png", screenshot = "h.jpg", heroShot = true)
    private val index = LibraryIndex(
        version = LibraryIndex.VERSION, home = "Home", extras = emptyList(), defaultTags = emptyMap(),
        systems = listOf(IndexedSystem("GB", "Game Boy", "Game Boy (GB)", games = listOf(g1, g2))),
    )

    @Test fun setArtUpdatesOneGame() {
        val next = index.setArt(g1, ArtRefs(boxart = "b", box2d = "c", screenshot = "s", heroShot = true))
        val games = next.systems.single().games
        assertEquals(g1.copy(boxart = "b", box2d = "c", screenshot = "s", heroShot = true), games[0])
        assertEquals(g2, games[1])
        assertEquals(index.copy(systems = emptyList()), next.copy(systems = emptyList()))
    }

    @Test fun setArtClearsWithNull() {
        assertEquals(g2.copy(boxart = null, screenshot = null, heroShot = false), index.setArt(g2, ArtRefs()).systems.single().games[1])   // every slot replaced
    }

    @Test fun setArtUnknownGameReturnsSameInstance() {
        assertSame(index, index.setArt(g1.copy(file = "Ghost.gb"), ArtRefs(boxart = "b")))
    }

    /** Every art kind round-trips through [ArtRefs]: a kind added to one side but not the other fails here. */
    @Test fun gameWithArtRoundTripsEveryKind() {
        val all = ArtRefs(boxart = "b", box2d = "c", screenshot = "s", wheel = "w", mix = "m", heroShot = true)
        val g = g1.withArt(all)
        assertEquals(all, g.artRefs)
        assertEquals(g1, g.withArt(ArtRefs()))   // other fields kept, every kind cleared (g1 has none)
    }
}
