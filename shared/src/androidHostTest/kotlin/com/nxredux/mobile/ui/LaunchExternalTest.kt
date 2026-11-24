package com.nxredux.mobile.ui

import com.nxredux.mobile.external.ExternalApps
import com.nxredux.mobile.external.ExternalEmulator
import com.nxredux.mobile.external.ExternalLaunch
import com.nxredux.mobile.external.ExternalLaunchResult
import com.nxredux.mobile.external.GamePass
import com.nxredux.mobile.gametime.GameSessions
import com.nxredux.mobile.gametime.GameTimeData
import com.nxredux.mobile.gametime.GameTotal
import com.nxredux.mobile.gametime.WindowTotals
import com.nxredux.mobile.library.FakeRomsSource
import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Sidecars
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.EmulatorRunner
import com.nxredux.mobile.runner.GameSession
import com.nxredux.mobile.settings.FakeAppSettings
import com.nxredux.mobile.storage.MemorySidecarStore
import com.nxredux.mobile.storage.Recent
import com.nxredux.mobile.storage.NoUserData
import com.nxredux.mobile.storage.Recents
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.UserDataStore
import com.nxredux.mobile.ui.nx.ListSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A launch that opens in a standalone emulator: the routing, its notices, Recently Played, play time and Kids Mode. */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchExternalTest {
    private fun app(id: String, name: String, tags: Set<String>, pass: GamePass = GamePass.VIEW_DATA) = ExternalEmulator(
        id = id, name = name, packages = listOf("test.$id"), activity = "test.$id.Main", pass = pass,
        extraKey = if (pass == GamePass.VIEW_DATA) null else "path", tags = tags,
    )
    private val duckstation = app("duckstation", "DuckStation", setOf("PS"))
    private val eden = app("eden", "Eden", setOf("SWITCH"))
    private val table = listOf(duckstation, eden)

    private class Apps(var installed: Set<ExternalEmulator>, var result: ExternalLaunchResult = ExternalLaunchResult.STARTED) : ExternalApps {
        val launched = ArrayList<ExternalLaunch>()
        override fun installedPackage(e: ExternalEmulator): String? = e.packages.first().takeIf { e in installed }
        override fun label(packageId: String): String? = installed.firstOrNull { it.packages.first() == packageId }?.name
        override suspend fun launch(request: ExternalLaunch): ExternalLaunchResult { launched += request; return result }
    }

    private class Runner : EmulatorRunner {
        val launched = ArrayList<GameSession>()
        override suspend fun launch(session: GameSession, recent: Recent) { launched += session }
    }

    /** Records the plays written whole ([GameTimeData.recordPlay]); every other call is a test bug. */
    private class Plays : GameTimeData {
        val recorded = ArrayList<Pair<String, Long>>()
        override suspend fun recordPlay(romPath: String, tag: String, name: String, startMs: Long, endMs: Long) { recorded += romPath to endMs - startMs }
        override suspend fun totals(): List<GameTotal> = error("unused")
        override suspend fun grandTotal(): Long = error("unused")
        override suspend fun members(target: Long): List<GameTotal> = error("unused")
        override suspend fun sessions(target: Long): GameSessions = error("unused")
        override suspend fun mergeCandidates(target: Long): List<GameTotal> = error("unused")
        override suspend fun merge(from: Long, into: Long, name: String): Unit = error("unused")
        override suspend fun unmerge(gameId: Long): Unit = error("unused")
        override suspend fun delete(target: Long): Unit = error("unused")
        override suspend fun byPath(): Map<String, GameTotal> = error("unused")
        override suspend fun window(sinceMs: Long, nowMs: Long): WindowTotals = error("unused")
        override suspend fun playedPaths(sinceMs: Long, nowMs: Long): Set<String> = error("unused")
        override suspend fun renamePaths(map: Map<String, String>): Unit = error("unused")
    }

    private class Host : LaunchHost {
        var kids = false
        val notices = ArrayList<String>()
        val recents = ArrayList<Recent>()
        var playTimeChanges = 0
        override fun kidsLocked() = false
        override fun kidsOn() = kids
        override fun dropPushedTab() {}
        override fun launchApp(entry: Listed) = false
        override fun showNotice(text: String) { notices += text }
        override fun rememberMenuSelection() {}
        override suspend fun moveInIndex(game: GameEntry, from: SystemFolder, toTag: String) = false
        override suspend fun refreshOpenList(select: String?) {}
        override fun openMainRow(s: Screen.Systems, index: Int, forward: NavMotion): Job? = null
        override suspend fun externalOffered(entry: Listed): (ExternalEmulator) -> Boolean = { true }
        override fun realPathOf(game: GameEntry): String? = null
        override fun recordRecent(recent: Recent) { recents += recent }
        override fun playTimeChanged() { playTimeChanges++ }
    }

    private class Switcher : LaunchSwitcher {
        override fun cancelLoad() {}
        override fun onRunnerCalled(fromSwitcher: Boolean) {}
    }

    private fun entry(tag: String, file: String): Listed {
        val g = GameEntry(
            ref = ContentRef("Home/Roms/$tag/$file"), fileName = file, shownName = file.substringBeforeLast('.'),
            folder = ContentRef("Home/Roms/$tag"), sourceId = Recents.HOME, relativeDir = tag,
        )
        return Listed(SystemFolder(tag, tag, CoreRegistry.forTag(tag), listOf(g)), g)
    }

    private class Rig(
        val launches: LaunchController, val apps: Apps, val runner: Runner, val host: Host, val plays: Plays, val settings: FakeAppSettings,
        val clock: LongArray, val screen: MutableStateFlow<Screen>,
    )

    /** Every game has a save state in slot 0. */
    private object Saved : UserDataStore by NoUserData {
        override suspend fun resumeInfo(tag: String, fileName: String) = ResumeInfo(0)
    }

    private fun TestScope.rig(installed: Set<ExternalEmulator>): Rig {
        val source = FakeRomsSource(dirs = mapOf(
            "Home" to listOf("Roms"), "Home/Roms" to listOf("PS", "SWITCH"),
            "Home/Roms/PS" to listOf("Crash.cue"), "Home/Roms/SWITCH" to listOf("Zelda.nsp"),
        ))
        val settings = FakeAppSettings(ContentRef("Home"))
        val apps = Apps(installed)
        val runner = Runner()
        val plays = Plays()
        val clock = longArrayOf(1_000_000L)
        val deps = AppDeps(
            settings, source, runner, NoopFolderPicker, compute = UnconfinedTestDispatcher(testScheduler),
            gameTime = plays, now = { clock[0] }, externalApps = apps, userData = Saved,
        )
        val host = Host()
        val home = { ContentRef("Home") as ContentRef? }
        val screen = MutableStateFlow<Screen>(Screen.Loading)
        val launches = LaunchController(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler)), screen,
            MutableStateFlow(ListSelection(0, 1)), MutableStateFlow(null), deps, Sidecars(source, MemorySidecarStore(), home),
            Switcher(), host, externalTable = table,
        )
        return Rig(launches, apps, runner, host, plays, settings, clock, screen)
    }

    @Test fun newConsoleWithoutAppShowsNoticeAndNoRecent() = runTest {
        val r = rig(installed = emptySet())
        r.launches.launch(entry("SWITCH", "Zelda.nsp"), resumeSlot = null); advanceUntilIdle()
        assertEquals(listOf("No emulator for Nintendo Switch is installed. Install one that can open these games, then try again."), r.host.notices)
        assertTrue(r.host.recents.isEmpty())
        assertTrue(r.apps.launched.isEmpty())
        assertTrue(r.runner.launched.isEmpty())
    }

    @Test fun newConsoleWithAppHandsOffAndRecordsRecentWithoutPlayTime() = runTest {
        val r = rig(installed = setOf(eden))
        val zelda = entry("SWITCH", "Zelda.nsp")
        r.launches.launch(zelda, resumeSlot = null); advanceUntilIdle()
        val sent = r.apps.launched.single()
        assertEquals(eden, sent.emulator)
        assertEquals("test.eden", sent.packageId)
        assertEquals(zelda.game.ref, sent.ref)
        assertEquals(listOf(Recent(zelda.game.path, "Zelda")), r.host.recents)
        r.clock[0] += 3_600_000L
        r.launches.onForeground(); advanceUntilIdle()
        assertTrue(r.plays.recorded.isEmpty())
        assertTrue(r.host.notices.isEmpty())
    }

    @Test fun pickedAppOnCoredConsoleRecordsPlayTimeCappedAt12h() = runTest {
        val r = rig(installed = setOf(duckstation))
        val crash = entry("PS", "Crash.cue")
        r.settings.externalPicks = mapOf(crash.game.path to "duckstation")
        r.launches.launch(crash, resumeSlot = null); advanceUntilIdle()
        assertEquals(duckstation, r.apps.launched.single().emulator)
        assertTrue(r.runner.launched.isEmpty())
        r.clock[0] += 13 * 3_600_000L
        r.launches.onForeground(); advanceUntilIdle()
        assertEquals(listOf(crash.game.path to 12 * 3_600_000L), r.plays.recorded)
        assertEquals(1, r.host.playTimeChanges)
        // The session ended with that return: another one records nothing.
        r.launches.onForeground(); advanceUntilIdle()
        assertEquals(1, r.plays.recorded.size)
    }

    @Test fun shortExternalPlayRecordsItsLength() = runTest {
        val r = rig(installed = setOf(duckstation))
        val crash = entry("PS", "Crash.cue")
        r.settings.externalPicks = mapOf(crash.game.path to "duckstation")
        r.launches.launch(crash, resumeSlot = null); advanceUntilIdle()
        r.clock[0] += 25 * 60_000L
        r.launches.onForeground(); advanceUntilIdle()
        assertEquals(listOf(crash.game.path to 25 * 60_000L), r.plays.recorded)
    }

    @Test fun kidsModeRefusesExternal_stillAllowsCores() = runTest {
        val r = rig(installed = setOf(duckstation, eden))
        r.host.kids = true
        val crash = entry("PS", "Crash.cue")
        r.settings.externalPicks = mapOf(crash.game.path to "duckstation")
        r.launches.launch(crash, resumeSlot = null); advanceUntilIdle()
        r.launches.launch(entry("SWITCH", "Zelda.nsp"), resumeSlot = null); advanceUntilIdle()
        assertTrue(r.apps.launched.isEmpty())
        assertEquals(List(2) { "Games in other emulators are off in Kids Mode." }, r.host.notices)
        assertTrue(r.host.recents.isEmpty())
        // Without a pick the same game runs on our core, as Kids Mode allows.
        r.settings.externalPicks = emptyMap()
        r.launches.launch(crash, resumeSlot = null); advanceUntilIdle()
        assertEquals(listOf("PS"), r.runner.launched.map { it.systemTag })
    }

    @Test fun noPickRunsOurCore() = runTest {
        val r = rig(installed = setOf(duckstation))
        r.launches.launch(entry("PS", "Crash.cue"), resumeSlot = null); advanceUntilIdle()
        assertEquals(1, r.runner.launched.size)
        assertTrue(r.apps.launched.isEmpty())
    }

    @Test fun refusedHandOffsSayWhy() = runTest {
        val r = rig(installed = setOf(eden))
        val zelda = entry("SWITCH", "Zelda.nsp")
        r.apps.result = ExternalLaunchResult.NO_ACCESS
        r.launches.launch(zelda, resumeSlot = null); advanceUntilIdle()
        r.apps.result = ExternalLaunchResult.FAILED
        r.launches.launch(zelda, resumeSlot = null); advanceUntilIdle()
        assertEquals(listOf("This emulator can't open games from this folder.", "Could not open Eden."), r.host.notices)
        assertTrue(r.host.recents.isEmpty())
    }

    @Test fun opensElsewhereNamesTheInstalledAppOrNone() = runTest {
        val tail = ". Save states, the in-game menu and achievement unlocks are not available there."
        val r = rig(installed = setOf(eden, duckstation))
        assertEquals("Opens in Eden$tail", r.launches.opensElsewhere(entry("SWITCH", "Zelda.nsp")))
        val crash = entry("PS", "Crash.cue")
        assertNull(r.launches.opensElsewhere(crash))                       // no pick: our core
        r.settings.externalPicks = mapOf(crash.game.path to "duckstation")
        assertEquals("Opens in DuckStation$tail", r.launches.opensElsewhere(crash))
        // No app installed: never a name the player doesn't have.
        r.apps.installed = emptySet()
        assertEquals("Opens in another emulator$tail", r.launches.opensElsewhere(entry("SWITCH", "Zelda.nsp")))
        assertNull(r.launches.opensElsewhere(crash))                       // the pick went with its app
    }

    @Test fun noResumeMarkerForAGameThatOpensElsewhere() = runTest {
        val r = rig(installed = setOf(duckstation))
        val crash = entry("PS", "Crash.cue")
        r.screen.value = Screen.Games(ListSource.Console(crash.system), listOf(crash))
        r.launches.refreshResume(); advanceUntilIdle()
        assertEquals(ResumeInfo(0), r.launches.resumeInfo.value)
        r.settings.externalPicks = mapOf(crash.game.path to "duckstation")
        r.launches.refreshResume(); advanceUntilIdle()
        assertEquals(ResumeInfo.NONE, r.launches.resumeInfo.value)
    }
}
