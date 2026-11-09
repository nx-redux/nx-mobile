package com.nxredux.mobile.ui

import com.nxredux.mobile.gametime.GameSession
import com.nxredux.mobile.gametime.GameSessions
import com.nxredux.mobile.gametime.GameTimeData
import com.nxredux.mobile.gametime.WindowTotals
import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.ui.nx.ListSelection
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameTimeScreenControllerTest {
    private class FakeData(var rows: MutableList<GameTotal>) : GameTimeData {
        val calls = mutableListOf<String>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun totals() = if (fail) error("db") else { gate?.await(); rows.toList() }
        override suspend fun grandTotal() = rows.sumOf { it.totalMs }
        val sessionCalls = mutableListOf<Long>()
        override suspend fun sessions(target: Long): GameSessions {
            sessionCalls += target; gate?.await()
            return GameSessions(List(target.toInt()) { i -> GameSession("s$target-$i", target, "dev", "Fold", 1_000L * (10 - i), 1_000L * (10 - i) + 500, 500) })
        }
        override suspend fun members(target: Long) = listOf(rows.first { it.gameId == target }, GameTotal(99, "Tetris (Europe)", "GB", "/eu", 5_000, 1, 0))
        override suspend fun mergeCandidates(target: Long) = rows.filter { it.gameId != target }
        override suspend fun merge(from: Long, into: Long, name: String) { calls += "merge $from $into $name"; rows.removeAll { it.gameId == from } }
        override suspend fun unmerge(gameId: Long) { calls += "unmerge $gameId" }
        var actGate: CompletableDeferred<Unit>? = null
        override suspend fun delete(target: Long) { calls += "delete $target"; actGate?.await(); rows.removeAll { it.gameId == target } }
        override suspend fun byPath() = rows.associateBy { it.romPath }
        override suspend fun window(sinceMs: Long, nowMs: Long) = WindowTotals(rows.sumOf { it.totalMs }, rows.maxByOrNull { it.totalMs })
        override suspend fun playedPaths(sinceMs: Long, nowMs: Long) = rows.map { it.romPath }.toSet()
        override suspend fun renamePaths(map: Map<String, String>) { calls += "rename $map" }
    }

    private fun total(id: Long, name: String, ms: Long, members: Int = 0) = GameTotal(id, name, "GB", "/Roms/GB/$name.gb", ms, 1, 0, members)

    private fun setup(data: FakeData, scope: kotlinx.coroutines.CoroutineScope): Triple<GameTimeScreenController, MutableStateFlow<Screen>, MutableStateFlow<String?>> {
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val error = MutableStateFlow<String?>(null)
        val c = GameTimeScreenController(scope, screen, MutableStateFlow(ListSelection(0, 0)), error, data) { { null } }
        return Triple(c, screen, error)
    }

    @Test fun theFirstLoadRunsUnderTheLoadingOverlayAndBLeaves() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000))).apply { gate = CompletableDeferred() }
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val error = MutableStateFlow<String?>(null)
        val loader = ScreenLoader(this)
        val c = GameTimeScreenController(this, screen, MutableStateFlow(ListSelection(0, 0)), error, data, loader) { { null } }
        var left = false
        c.open(onCancel = { left = true })
        advanceTimeBy(200); runCurrent()
        assertTrue(loader.visible.value); assertTrue(assertIs<Screen.GameTime>(screen.value).loading)
        assertTrue(loader.cancel())
        advanceUntilIdle()
        assertTrue(left); assertFalse(loader.visible.value); assertNull(error.value)   // a cancel is not a failure
        c.open(); advanceTimeBy(200); runCurrent(); assertTrue(loader.visible.value)
        data.gate!!.complete(Unit); advanceUntilIdle()
        assertFalse(loader.visible.value); assertEquals(1, assertIs<Screen.GameTime>(screen.value).rows.size)
    }

    @Test fun openLoadsRowsAndTotal() = runTest {
        val (c, screen, _) = setup(FakeData(mutableListOf(total(1, "Kirby", 60_000), total(2, "Tetris", 30_000))), this)
        c.open(); advanceUntilIdle()
        val s = assertIs<Screen.GameTime>(screen.value)
        assertEquals(listOf("Kirby", "Tetris"), s.rows.map { it.total.name }); assertEquals(90_000L, s.grandTotalMs)
    }

    @Test fun optionsHideMergeForASingleRowAndUnmergeWithoutMembers() = runTest {
        val (c, screen, _) = setup(FakeData(mutableListOf(total(1, "Kirby", 60_000))), this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); advanceUntilIdle()
        val m = assertIs<GameTimeModal.Options>((screen.value as Screen.GameTime).modal)
        assertEquals(listOf(GameTimeAction.DELETE), m.actions)
    }

    @Test fun mergeAsksForTheNameThenMerges() = runTest {
        val data = FakeData(mutableListOf(total(1, "Tetris", 60_000), total(2, "Tetris (Europe)", 30_000)))
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); advanceUntilIdle()         // options on Tetris
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()            // Merge into…
        assertIs<GameTimeModal.PickMerge>((screen.value as Screen.GameTime).modal)
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()            // into Tetris (Europe)
        val pick = assertIs<GameTimeModal.PickName>((screen.value as Screen.GameTime).modal)
        assertEquals(listOf("Tetris", "Tetris (Europe)"), pick.names)
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()            // keep "Tetris"
        assertEquals(listOf("merge 1 2 Tetris"), data.calls)
        assertNull((screen.value as Screen.GameTime).modal)
        assertEquals(1, (screen.value as Screen.GameTime).rows.size)
    }

    @Test fun sameNameMergeSkipsNamePick() = runTest {
        val data = FakeData(mutableListOf(total(1, "Tetris", 60_000), total(2, "Tetris", 30_000)))
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); advanceUntilIdle()              // options
        repeat(2) { c.onButton(screen.value, NxButton.A); advanceUntilIdle() }   // merge, target
        assertEquals(listOf("merge 1 2 Tetris"), data.calls)
    }

    @Test fun unmergeListsMembersWithoutTheTarget() = runTest {
        val data = FakeData(mutableListOf(total(1, "Tetris", 65_000, members = 1)))
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); advanceUntilIdle()              // options: UNMERGE, DELETE
        val o = assertIs<GameTimeModal.Options>((screen.value as Screen.GameTime).modal)
        assertEquals(listOf(GameTimeAction.UNMERGE, GameTimeAction.DELETE), o.actions)
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        val u = assertIs<GameTimeModal.PickUnmerge>((screen.value as Screen.GameTime).modal)
        assertEquals(listOf("Tetris (Europe)"), u.members.map { it.name })
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertEquals(listOf("unmerge 99"), data.calls)
    }

    @Test fun deleteNeedsConfirmAndBCancels() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000)))
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertIs<GameTimeModal.ConfirmDelete>((screen.value as Screen.GameTime).modal)
        c.onButton(screen.value, NxButton.B); advanceUntilIdle()
        assertNull((screen.value as Screen.GameTime).modal); assertEquals(emptyList(), data.calls)
        c.onButton(screen.value, NxButton.MENU); c.onButton(screen.value, NxButton.A); c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertEquals(listOf("delete 1"), data.calls)
        assertEquals(0, (screen.value as Screen.GameTime).rows.size)
    }

    @Test fun aSecondAWhileAnActionRunsDoesNothing() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000), total(2, "Tetris", 30_000))).apply { actGate = CompletableDeferred() }
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); c.onButton(screen.value, NxButton.DOWN); c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertIs<GameTimeModal.ConfirmDelete>((screen.value as Screen.GameTime).modal)
        c.onButton(screen.value, NxButton.A); runCurrent()
        c.onButton(screen.value, NxButton.A); c.onButton(screen.value, NxButton.B); runCurrent()   // still deleting: ignored
        assertIs<GameTimeModal.ConfirmDelete>((screen.value as Screen.GameTime).modal)
        data.actGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("delete 1"), data.calls)
        assertEquals(listOf("Tetris"), (screen.value as Screen.GameTime).rows.map { it.total.name })
    }

    @Test fun aCancelledActionIsNotAFailure() = runTest {
        var deletes = 0
        val data = object : GameTimeData by FakeData(mutableListOf(total(1, "Kirby", 60_000))) {
            override suspend fun delete(target: Long) { deletes++; throw CancellationException("left") }
        }
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val error = MutableStateFlow<String?>(null)
        val c = GameTimeScreenController(this, screen, MutableStateFlow(ListSelection(0, 0)), error, data) { { null } }
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); c.onButton(screen.value, NxButton.A); c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertNull(error.value)
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()   // the busy guard was released: A acts again
        assertEquals(2, deletes); assertNull(error.value)
    }

    @Test fun aOpensTheRowsSessionsAndMenuItsOptions() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000), total(2, "Tetris", 30_000)))
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.MENU); advanceUntilIdle()
        assertIs<GameTimeModal.Options>((screen.value as Screen.GameTime).modal)
        c.onButton(screen.value, NxButton.B); advanceUntilIdle()
        c.onButton(screen.value, NxButton.DOWN)
        assertTrue(c.onButton(screen.value, NxButton.A)); advanceUntilIdle()
        val s = assertIs<Screen.GameTimeSessions>(screen.value)
        assertEquals("Tetris", s.game.name); assertEquals(listOf(2L), data.sessionCalls); assertEquals(2, s.sessions.sessions.size)
    }

    @Test fun sessionsAreReadOnlyAndBReturnsToTheRow() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000), total(3, "Tetris", 30_000)))
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val selection = MutableStateFlow(ListSelection(0, 0))
        val c = GameTimeScreenController(this, screen, selection, MutableStateFlow(null), data) { { null } }
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.DOWN); c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        val s = assertIs<Screen.GameTimeSessions>(screen.value)
        assertEquals(1, c.parentOf(s)!!.row)
        c.onButton(s, NxButton.DOWN); assertEquals(1, selection.value.index)
        assertTrue(c.onButton(s, NxButton.A)); assertTrue(c.onButton(s, NxButton.MENU)); advanceUntilIdle()
        assertIs<Screen.GameTimeSessions>(screen.value); assertEquals(listOf(3L), data.sessionCalls)
        assertTrue(c.onButton(s, NxButton.B))
        assertIs<Screen.GameTime>(screen.value); assertEquals(1, selection.value.index)
        assertNull(c.parentOf(screen.value))
    }

    @Test fun deleteFromTheSessionsAsksOnTheListAndBReturnsToTheSessions() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000), total(2, "Tetris", 30_000)))
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val selection = MutableStateFlow(ListSelection(0, 0))
        val c = GameTimeScreenController(this, screen, selection, MutableStateFlow(null), data) { { null } }
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.DOWN); c.onButton(screen.value, NxButton.A); advanceUntilIdle()   // Tetris's sessions
        c.onButton(screen.value, NxButton.Y); advanceUntilIdle()
        assertIs<GameTimeModal.ConfirmDelete>(assertIs<Screen.GameTime>(screen.value).modal); assertEquals(1, selection.value.index)
        c.onButton(screen.value, NxButton.B); advanceUntilIdle()                                           // cancel: Tetris's sessions again
        assertEquals("Tetris", assertIs<Screen.GameTimeSessions>(screen.value).game.name); assertEquals(emptyList(), data.calls)
        c.onButton(screen.value, NxButton.Y); c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertEquals(listOf("delete 2"), data.calls)
        assertEquals(listOf("Kirby"), assertIs<Screen.GameTime>(screen.value).rows.map { it.total.name })   // done: the list
    }

    @Test fun mergeAndUnmergeFromTheSessionsOnlyWhenTheyApply() = runTest {
        val data = FakeData(mutableListOf(total(1, "Tetris", 60_000, members = 1), total(2, "Tetris (Europe)", 30_000)))
        val (c, screen, _) = setup(data, this)
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertTrue(assertIs<Screen.GameTimeSessions>(screen.value).canMerge)
        c.onButton(screen.value, NxButton.X); advanceUntilIdle()
        assertEquals(listOf("Tetris (Europe)"), assertIs<GameTimeModal.PickMerge>((screen.value as Screen.GameTime).modal).candidates.map { it.name })
        c.onButton(screen.value, NxButton.B); advanceUntilIdle()
        assertIs<Screen.GameTimeSessions>(screen.value)
        c.onButton(screen.value, NxButton.SELECT); advanceUntilIdle()
        assertIs<GameTimeModal.PickUnmerge>((screen.value as Screen.GameTime).modal)
        c.onButton(screen.value, NxButton.B); advanceUntilIdle()
        c.onButton(screen.value, NxButton.B)                                         // the list
        c.onButton(screen.value, NxButton.MENU); c.onButton(screen.value, NxButton.A); advanceUntilIdle()   // MENU → Merge into…
        c.onButton(screen.value, NxButton.B); advanceUntilIdle()
        assertIs<GameTimeModal.Options>((screen.value as Screen.GameTime).modal)     // from the list, B still goes to the options
        val single = FakeData(mutableListOf(total(1, "Kirby", 60_000)))
        val (c2, screen2, _) = setup(single, this)
        c2.open(); advanceUntilIdle()
        c2.onButton(screen2.value, NxButton.A); advanceUntilIdle()
        val s = assertIs<Screen.GameTimeSessions>(screen2.value); assertFalse(s.canMerge)
        c2.onButton(s, NxButton.X); c2.onButton(s, NxButton.SELECT); advanceUntilIdle()   // nothing to merge into, nothing merged
        assertTrue(screen2.value === s)
        assertEquals(listOf("Back", "Delete"), gameSessionsHints(s).map { it.label })
        assertEquals(listOf("Back", "Merge", "Unmerge", "Delete"), gameSessionsHints(s.copy(canMerge = true, game = s.game.copy(members = 1))).map { it.label })
    }

    @Test fun aTapOnAnotherGameBesideTheSessionsOpensItsOwn() = runTest {
        val data = FakeData(mutableListOf(total(1, "Kirby", 60_000), total(2, "Tetris", 30_000), total(3, "Mario", 10_000)))
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val selection = MutableStateFlow(ListSelection(0, 0))
        val c = GameTimeScreenController(this, screen, selection, MutableStateFlow(null), data) { { null } }
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        val kirby = assertIs<Screen.GameTimeSessions>(screen.value)
        c.parentTap(0); advanceUntilIdle()                           // the game already open: nothing
        assertEquals(listOf(1L), data.sessionCalls)
        data.gate = CompletableDeferred()
        c.parentTap(2); c.parentTap(2); runCurrent()                 // one tap = select + open: a single load
        assertTrue(screen.value === kirby)                           // the old sessions stay until the new ones land
        data.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals("Mario", assertIs<Screen.GameTimeSessions>(screen.value).game.name)
        assertEquals(listOf(1L, 3L), data.sessionCalls)
        assertEquals(2, c.parentOf(screen.value)!!.row)
        c.onButton(screen.value, NxButton.B)
        assertIs<Screen.GameTime>(screen.value); assertEquals(2, selection.value.index)
    }

    @Test fun aSessionsReadFailureStaysOnTheList() = runTest {
        val data = object : GameTimeData by FakeData(mutableListOf(total(1, "Kirby", 60_000))) {
            override suspend fun sessions(target: Long): GameSessions = error("db")
        }
        val screen = MutableStateFlow<Screen>(Screen.Systems(emptyList()))
        val error = MutableStateFlow<String?>(null)
        val c = GameTimeScreenController(this, screen, MutableStateFlow(ListSelection(0, 0)), error, data) { { null } }
        c.open(); advanceUntilIdle()
        c.onButton(screen.value, NxButton.A); advanceUntilIdle()
        assertIs<Screen.GameTime>(screen.value); assertEquals("Couldn't read game time", error.value)
    }

    @Test fun aSessionRowShowsItsStartAndLengthAndItsDeviceOnlyWhenSeveral() {
        val fold = GameSession("a", 1, "d1", "Fold", 3_600_000L, 3_600_000L + 4_980_000L, 4_980_000L)
        val flip = GameSession("b", 1, "d2", "Flip", 1_000L, 46_000L, 45_000L)
        assertEquals(formatDateTime(fold.startedAt), gameSessionRow(fold, multiDevice = false).label)
        assertEquals("1h 23m", gameSessionRow(fold, multiDevice = false).secondary)
        assertEquals("1h 23m  ·  Fold", gameSessionRow(fold, multiDevice = true).secondary)
        val both = GameSessions(listOf(fold, flip))
        assertTrue(both.multiDevice); assertFalse(GameSessions(listOf(fold)).multiDevice)
        assertEquals(listOf("1h 23m total", "2 plays", "Last played ${formatDateTime(fold.endedAt)}"), gameSessionsDetails(both))
        assertEquals("1 play", gameSessionsDetails(GameSessions(listOf(flip)))[1])   // a row is a play (schema v2): one launch's slices
        assertEquals(listOf("No plays recorded"), gameSessionsDetails(GameSessions(emptyList())))
    }

    @Test fun bWithoutModalIsNotHandled() = runTest {
        val (c, screen, _) = setup(FakeData(mutableListOf(total(1, "Kirby", 60_000))), this)
        c.open(); advanceUntilIdle()
        assertEquals(false, c.onButton(screen.value, NxButton.B))
    }

    @Test fun loadFailureShowsError() = runTest {
        val data = FakeData(mutableListOf()).apply { fail = true }
        val (c, screen, error) = setup(data, this)
        c.open(); advanceUntilIdle()
        assertEquals("Couldn't read game time", error.value)
        assertEquals(0, (screen.value as Screen.GameTime).rows.size)
    }
}
