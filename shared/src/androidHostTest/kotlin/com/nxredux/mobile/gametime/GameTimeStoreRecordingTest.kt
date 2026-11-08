package com.nxredux.mobile.gametime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class GameTimeStoreRecordingTest {
    private var clock = 1_000_000L
    private val pair = testStore({ clock })
    private val store = pair.first
    private val q = pair.second.gameTimeQueries

    @Test fun startThenEndStoresDuration() {
        val id = store.start("/Roms/Game Boy (GB)/Tetris.gb", "GB", "Tetris")
        clock += 90_000
        store.end(id)
        val s = q.sessionByUuid(id).executeAsOne()
        assertEquals(1_090_000L, s.ended_at)
        assertEquals(90_000L, s.duration)
    }

    @Test fun samePathReusesTheGameNewPathCreatesOne() {
        val a = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 5_000; store.end(a)
        val b = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 5_000; store.end(b)
        val c = store.start("/Roms/GB/Kirby.gb", "GB", "Kirby"); clock += 5_000; store.end(c)
        val ga = q.sessionByUuid(a).executeAsOne().game_id
        assertEquals(ga, q.sessionByUuid(b).executeAsOne().game_id)
        assertNotEquals(ga, q.sessionByUuid(c).executeAsOne().game_id)
    }

    @Test fun underOneSecondIsDropped() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 999; store.end(id)
        assertNull(q.sessionByUuid(id).executeAsOneOrNull())
    }

    @Test fun overTwentyFourHoursIsDropped() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 86_400_001; store.end(id)
        assertNull(q.sessionByUuid(id).executeAsOneOrNull())
    }

    @Test fun backwardsClockIsDropped() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock -= 60_000; store.end(id)
        assertNull(q.sessionByUuid(id).executeAsOneOrNull())
    }

    @Test fun staleSessionClosesAtLastTouch() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris")
        clock += 60_000; store.touch(id)
        clock += 3_600_000   // killed in the background; next app start
        store.closeStale()
        val s = q.sessionByUuid(id).executeAsOne()
        assertEquals(60_000L, s.duration)
    }

    // The menu process opens the database while a game plays in the game process: the live session stays open.
    @Test fun anotherProcessPreparingKeepsALiveSession() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris")
        clock += GameTimeStore.TOUCH_MS; store.touch(id)
        clock += GameTimeStore.TOUCH_MS - 1_000
        GameTimeStore(pair.second, "dev-1", "Fold", { clock }) { "menu" }.prepare()
        clock += 1_000; store.touch(id)
        clock += 30_000; store.end(id)
        assertEquals(2 * GameTimeStore.TOUCH_MS + 30_000, q.sessionByUuid(id).executeAsOne().duration)
    }

    @Test fun prepareClosesASessionPastTheStaleBound() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris")
        clock += GameTimeStore.TOUCH_MS; store.touch(id)
        clock += GameTimeStore.STALE_MS + 1
        GameTimeStore(pair.second, "dev-1", "Fold", { clock }) { "menu" }.prepare()
        assertEquals(GameTimeStore.TOUCH_MS, q.sessionByUuid(id).executeAsOne().duration)
    }

    @Test fun startClosesAnOpenSessionFirst() {
        val a = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 30_000; store.touch(a)
        clock += 10_000
        store.start("/Roms/GB/Kirby.gb", "GB", "Kirby")
        assertEquals(30_000L, q.sessionByUuid(a).executeAsOne().duration)
    }

    @Test fun endTwiceKeepsTheFirstEnd() {
        val id = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 10_000; store.end(id)
        clock += 10_000; store.end(id)
        assertEquals(10_000L, q.sessionByUuid(id).executeAsOne().duration)
    }

    @Test fun renamePathsKeepsSessions() {
        val a = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 5_000; store.end(a)
        val b = store.start("/Roms/GB/Tetris.gb", "GB", "Tetris"); clock += 5_000; store.end(b)
        store.renamePaths(mapOf("/Roms/GB/Tetris.gb" to "/Extra/x/GB/Tetris.gb"))
        val g = q.gameByPath("/Extra/x/GB/Tetris.gb").executeAsOne()
        assertEquals(g.id, q.sessionByUuid(a).executeAsOne().game_id)
        assertEquals(g.id, q.sessionByUuid(b).executeAsOne().game_id)
        assertNull(q.gameByPath("/Roms/GB/Tetris.gb").executeAsOneOrNull())
    }

    // Every recorded game's path, played or not: the storage upgrade renames them all.
    @Test fun romPathsListsEveryGame() {
        store.start("/Roms/GB/Tetris.gb", "GB", "Tetris")
        val b = store.start("/Extra/x/GBA/Zelda.gba", "GBA", "Zelda"); clock += 5_000; store.end(b)
        assertEquals(setOf("/Roms/GB/Tetris.gb", "/Extra/x/GBA/Zelda.gba"), store.romPaths().toSet())
    }
}
