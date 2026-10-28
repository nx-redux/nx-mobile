package com.nxredux.mobile.cheats.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CheatDbControllerTest {
    private val tags = setOf("FDS", "GBA")
    private val table = listOf("FDS", "GBA")

    private fun controller(fake: FakeCheatDbPlatform, scope: CoroutineScope, states: MutableList<CheatDbState>, messages: MutableList<CheatDbMessage>): CheatDbController {
        val c = CheatDbController(fake, tags, scope, now = { 42L })
        scope.launch { c.state.collect { states += it } }
        scope.launch { c.messages.collect { messages += it } }
        return c
    }

    @Test fun refresh_reads_the_manifest() = runTest {
        val fake = FakeCheatDbPlatform().apply { manifestText = CheatDbManifest("s", 1L, listOf("Cheats/FDS/a.cht")).render() }
        val states = ArrayList<CheatDbState>(); val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), states, messages)
        assertEquals(CheatDbState.Unknown, c.state.value)
        c.refresh()
        assertEquals(CheatDbState.Installed(CheatDbManifest("s", 1L, listOf("Cheats/FDS/a.cht"))), c.state.value)
        fake.manifestText = null
        c.refresh()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
    }

    @Test fun refresh_treats_an_unparsable_manifest_as_not_installed() = runTest {
        val fake = FakeCheatDbPlatform().apply { manifestText = "# nx-mobile cheatdb 9\nCheats/FDS/a.cht\n" }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
        fake.manifestText = CheatDbManifest("s", 1L, emptyList()).render()   // parses, but lists nothing
        c.refresh()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
    }

    @Test fun download_installs_writes_the_manifest_and_notifies() = runTest {
        val fake = FakeCheatDbPlatform()
        val states = ArrayList<CheatDbState>(); val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), states, messages)
        c.refresh()
        c.download()
        val installed = assertIs<CheatDbState.Installed>(c.state.value)
        assertEquals(CheatDbManifest("stamp-2", 42L, listOf("Cheats/FDS/a.cht", "Cheats/GBA/a.cht"), table), installed.manifest)
        assertEquals(installed.manifest.render(), fake.manifestText)
        assertEquals(listOf("/cache/cheats.zip"), fake.discarded)
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches installed: 2 files for 2 systems")), messages)
        // Progress reached the UI in order: Downloading(0, unknown), Downloading(100), Downloading(1000), Installing FDS 1/2, Installing GBA 2/2.
        assertEquals(
            listOf(CheatDbState.Downloading(0, -1, null), CheatDbState.Downloading(100, 1000, null), CheatDbState.Downloading(1000, 1000, null), CheatDbState.Installing("FDS", 1, 2, null), CheatDbState.Installing("GBA", 2, 2, null)),
            states.filter { it is CheatDbState.Downloading || it is CheatDbState.Installing },
        )
    }

    @Test fun an_update_deletes_what_the_previous_install_wrote_and_this_one_did_not() = runTest {
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/a.cht", "Cheats/GPGX/old.cht"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render() }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh(); c.download()
        assertEquals(listOf("Cheats/FDS/a.cht", "Cheats/GBA/a.cht"), assertIs<CheatDbState.Installed>(c.state.value).manifest.files)
        assertEquals(listOf(listOf("Cheats/GPGX/old.cht")), fake.pruned)   // a.cht was rewritten, so it stays
    }

    @Test fun a_fresh_install_prunes_nothing() = runTest {
        val fake = FakeCheatDbPlatform()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh(); c.download()
        assertEquals(emptyList(), fake.pruned)
    }

    @Test fun unknown_total_is_passed_through() = runTest {
        val fake = FakeCheatDbPlatform().apply { reportedTotal = -1L }
        val states = ArrayList<CheatDbState>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), states, ArrayList())
        c.refresh(); c.download()
        assertTrue(states.any { it == CheatDbState.Downloading(100, -1, null) })
        assertIs<CheatDbState.Installed>(c.state.value)
    }

    @Test fun no_space_refuses_before_downloading() = runTest {
        val fake = FakeCheatDbPlatform().apply { free = CheatDatabase.NEEDED_FREE_BYTES - 1 }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Failure("Not enough space (needs about 400 MB free)")), messages)
        assertEquals(null, fake.manifestText)
    }

    @Test fun network_failure_restores_not_installed() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadError = CheatDbException(CheatDbException.Kind.NETWORK) }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Failure("Download failed (check Wi-Fi)")), messages)
    }

    @Test fun corrupt_archive_while_updating_restores_the_previous_install() = runTest {
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/old.cht"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); installError = CheatDbException(CheatDbException.Kind.CORRUPT) }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertEquals(CheatDbState.Installed(previous), c.state.value)
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Failure("Downloaded archive is corrupt")), messages)
        assertEquals(previous.render(), fake.manifestText)     // untouched
        assertEquals(listOf("/cache/cheats.zip"), fake.discarded)
    }

    @Test fun storage_failure_on_a_fresh_install_returns_to_not_installed_and_allows_retry() = runTest {
        val fake = FakeCheatDbPlatform().apply { installError = CheatDbException(CheatDbException.Kind.STORAGE) }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
        assertEquals(CheatDbMessage.Failure("Could not write the cheat files"), messages.last())
        assertEquals(listOf("/cache/cheats.zip"), fake.discarded)
        fake.installError = null
        c.download()
        assertIs<CheatDbState.Installed>(c.state.value)
    }

    @Test fun refresh_result_does_not_overwrite_a_running_job() = runTest {
        val fake = FakeCheatDbPlatform().apply {
            manifestText = CheatDbManifest("s", 1L, listOf("Cheats/FDS/a.cht")).render()
            readGate = CompletableDeferred(); downloadGate = CompletableDeferred()
        }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh()   // suspends in readManifest
        c.download()
        assertIs<CheatDbState.Downloading>(c.state.value)
        fake.readGate!!.complete(Unit)
        assertIs<CheatDbState.Downloading>(c.state.value)
        fake.downloadGate!!.complete(Unit)
        assertIs<CheatDbState.Installed>(c.state.value)
    }

    @Test fun cancel_during_download_restores_the_previous_state() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadGate = CompletableDeferred() }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertIs<CheatDbState.Downloading>(c.state.value)
        assertTrue(c.busy)
        c.cancel()
        assertEquals(CheatDbState.NotInstalled, c.state.value)
        assertTrue(messages.isEmpty())
        assertTrue(fake.discarded.isEmpty())   // no archive existed yet; the platform deletes its own .part
    }

    @Test fun cancel_during_install_discards_the_archive_and_restores() = runTest {
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/old.cht"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); installGate = CompletableDeferred() }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh(); c.download()
        assertIs<CheatDbState.Installing>(c.state.value)
        c.cancel()
        assertEquals(CheatDbState.Installed(previous), c.state.value)
        assertEquals(listOf("/cache/cheats.zip"), fake.discarded)
    }

    @Test fun check_for_updates_up_to_date_stale_and_unreachable() = runTest {
        val previous = CheatDbManifest("stamp-2", 1L, listOf("Cheats/FDS/old.cht"), table)
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render() }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh()
        c.checkForUpdates()
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches are up to date")), messages)
        assertEquals(CheatDbState.Installed(previous), c.state.value)
        fake.remote = "stamp-3"; fake.downloadStamp = "stamp-3"
        c.checkForUpdates()
        assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
        assertEquals("stamp-3", assertIs<CheatDbState.Installed>(c.state.value).manifest.stamp)
        fake.remote = null
        c.checkForUpdates()
        assertEquals(CheatDbMessage.Failure("Could not check for updates (Wi-Fi?)"), messages.last())
        assertIs<CheatDbState.Installed>(c.state.value)
    }

    @Test fun a_changed_table_reinstalls_though_the_archive_is_the_same() = runTest {
        for (installedFor in listOf(null, listOf("FDS", "GBA", "GPGX"))) {   // before tags were recorded; an older table
            val previous = CheatDbManifest("stamp-2", 1L, listOf("Cheats/FDS/a.cht", "Cheats/GPGX/old.cht"), installedFor)
            val fake = FakeCheatDbPlatform().apply { manifestText = previous.render() }
            val messages = ArrayList<CheatDbMessage>()
            val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
            c.refresh(); c.checkForUpdates()
            assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
            assertEquals(table, assertIs<CheatDbState.Installed>(c.state.value).manifest.tags)
            assertEquals(listOf(listOf("Cheats/GPGX/old.cht")), fake.pruned)
        }
    }

    @Test fun an_unknown_stored_stamp_always_downloads() = runTest {
        val previous = CheatDbManifest(CheatDbManifest.UNKNOWN_STAMP, 1L, listOf("Cheats/FDS/old.cht"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); remote = "installed" }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.checkForUpdates()
        assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
    }

    // Last-Modified headers (RFC 1123) and GitHub commit dates (ISO 8601), oldest to newest.
    private val zipOld = "Mon, 01 Jan 2026 00:00:00 GMT"
    private val zipMid = "Tue, 02 Jan 2026 12:00:00 GMT"
    private val zipNew = "Wed, 03 Jan 2026 00:00:00 GMT"
    private val commitBetweenOldAndMid = "2026-01-02T00:00:00Z"
    private val commitAfterNew = "2026-01-04T00:00:00Z"

    private fun commit(sha: String, iso: String?) = CheatDbCommit(sha, iso?.let(CheatDbDates::parseIso))

    private fun commitController(fake: FakeCheatDbPlatform, scope: CoroutineScope, messages: MutableList<CheatDbMessage>) =
        controller(fake, scope, ArrayList(), messages).also { it.refresh() }

    private fun withCommit(commit: String?, stamp: String = zipOld) =
        CheatDbManifest(stamp, 1L, listOf("Cheats/FDS/old.cht"), table, chtCommit = commit)

    private fun installed(c: CheatDbController) = assertIs<CheatDbState.Installed>(c.state.value).manifest

    @Test fun dates_parse_across_both_formats() {
        assertEquals(CheatDbDates.parseIso("2026-01-02T12:00:00Z"), CheatDbDates.parseHttp(zipMid))
        assertEquals(1_700_000_000_000L, CheatDbDates.parseHttp("Tue, 14 Nov 2023 22:13:20 GMT"))
        assertEquals(1_700_000_000_000L, CheatDbDates.parseIso("2023-11-14T22:13:20Z"))
        assertEquals(null, CheatDbDates.parseHttp("stamp-1"))
        assertEquals(null, CheatDbDates.parseIso("yesterday"))
    }

    @Test fun the_same_cht_commit_is_up_to_date_though_the_zip_date_moved() = runTest {
        val fake = FakeCheatDbPlatform().apply { manifestText = withCommit("aaa").render(); remote = zipNew; commit = commit("aaa", commitBetweenOldAndMid) }
        val messages = ArrayList<CheatDbMessage>()
        val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.checkForUpdates()
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches are up to date")), messages)
        assertTrue(fake.downloads.isEmpty())
    }

    @Test fun a_new_commit_the_zip_was_built_after_downloads_and_records_it() = runTest {
        val fake = FakeCheatDbPlatform().apply {
            manifestText = withCommit("aaa").render(); remote = zipNew; downloadStamp = zipNew; commit = commit("bbb", commitBetweenOldAndMid)
        }
        val messages = ArrayList<CheatDbMessage>()
        val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.checkForUpdates()
        assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
        assertEquals("bbb", installed(c).chtCommit)
    }

    @Test fun a_new_commit_with_an_unmoved_zip_date_is_up_to_date_and_keeps_the_old_commit() = runTest {
        val fake = FakeCheatDbPlatform().apply { manifestText = withCommit("aaa").render(); remote = zipOld; commit = commit("bbb", commitBetweenOldAndMid) }
        val messages = ArrayList<CheatDbMessage>()
        val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.checkForUpdates()
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches are up to date")), messages)
        assertTrue(fake.downloads.isEmpty())
        assertEquals("aaa", installed(c).chtCommit)
    }

    @Test fun a_new_commit_newer_than_a_moved_zip_is_up_to_date_for_now() = runTest {
        // The buildbot rebuilt on its schedule (the date moved) but before the commit: the zip lacks it.
        val fake = FakeCheatDbPlatform().apply { manifestText = withCommit("aaa").render(); remote = zipNew; commit = commit("bbb", commitAfterNew) }
        val messages = ArrayList<CheatDbMessage>()
        val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.checkForUpdates()
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches are up to date")), messages)
        assertTrue(fake.downloads.isEmpty())
        assertEquals("aaa", installed(c).chtCommit)
    }

    @Test fun a_zip_older_than_the_newest_commit_does_not_record_it() = runTest {
        // The check saw an older state; by download time the commit is newer than the zip that came.
        val previous = withCommit("aaa")
        val fake = FakeCheatDbPlatform().apply {
            manifestText = previous.render(); remote = zipNew; downloadStamp = zipMid; commit = commit("bbb", "2026-01-02T18:00:00Z")
        }
        val messages = ArrayList<CheatDbMessage>()
        val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.download()
        assertEquals("aaa", installed(c).chtCommit)
        // Fresh install: no previous commit to keep.
        val fresh = FakeCheatDbPlatform().apply { downloadStamp = zipMid; commit = commit("bbb", "2026-01-02T18:00:00Z") }
        val c2 = controller(fresh, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c2.download()
        assertEquals(null, installed(c2).chtCommit)
    }

    @Test fun without_a_stored_commit_or_an_api_answer_the_zip_date_decides() = runTest {
        // Older install: no commit stored. Then the API failing / answering must not change the old behaviour.
        for ((stored, api) in listOf<Pair<String?, CheatDbCommit?>>(null to commit("bbb", commitBetweenOldAndMid), "aaa" to null, null to null)) {
            val fake = FakeCheatDbPlatform().apply { manifestText = withCommit(stored).render(); remote = zipOld; commit = api }
            val messages = ArrayList<CheatDbMessage>()
            val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
            c.checkForUpdates()
            assertEquals(CheatDbMessage.Notice("Cheats & Patches are up to date"), messages.last())
            fake.remote = zipNew; fake.downloadStamp = zipNew
            c.checkForUpdates()
            assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
            // Recorded only when the zip is newer than the commit (the first row's commit is older than zipNew).
            assertEquals(api?.sha ?: stored, installed(c).chtCommit)
        }
    }

    @Test fun a_changed_table_still_downloads_when_the_commit_is_the_same() = runTest {
        val previous = withCommit("aaa", zipMid).copy(tags = listOf("FDS"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); remote = zipMid; commit = commit("aaa", commitBetweenOldAndMid) }
        val messages = ArrayList<CheatDbMessage>()
        val c = commitController(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), messages)
        c.checkForUpdates()
        assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
    }

    @Test fun a_fresh_install_records_the_commit_and_a_failing_api_records_none() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadStamp = zipNew; commit = commit("ccc", commitBetweenOldAndMid) }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.download()
        assertEquals("ccc", CheatDbManifest.parse(fake.manifestText!!)!!.chtCommit)
        // A failing API on an update keeps the commit already recorded; on a fresh install there is none to keep.
        fake.commit = null
        c.download()
        assertEquals("ccc", CheatDbManifest.parse(fake.manifestText!!)!!.chtCommit)
        val fresh = FakeCheatDbPlatform().apply { downloadStamp = zipNew }
        controller(fresh, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList()).download()
        assertEquals(null, CheatDbManifest.parse(fresh.manifestText!!)!!.chtCommit)
    }

    @Test fun a_patches_failure_with_a_commit_still_follows_the_libretro_answer() = runTest {
        val previous = CheatDbManifest(zipOld, 1L, listOf("Cheats/PS2/a.cht", "Cheats/PS2/Patches/a.pnach"), listOf("FDS", "PS2"), "patches-2", "aaa")
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); remote = zipNew; commit = commit("aaa", commitBetweenOldAndMid); patchesRemote = null }
        val messages = ArrayList<CheatDbMessage>()
        val c = CheatDbController(fake, setOf("PS2", "FDS"), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { 42L })
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        scope.launch { c.messages.collect { messages += it } }
        c.refresh(); c.checkForUpdates()
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches are up to date")), messages)
    }

    @Test fun remove_deletes_exactly_the_manifest_files() = runTest {
        val previous = CheatDbManifest("s", 1L, listOf("Cheats/FDS/a.cht", "Cheats/GBA/b.cht"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render() }
        val messages = ArrayList<CheatDbMessage>()
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.remove()
        assertEquals(listOf(previous.files), fake.deleted)
        assertEquals(CheatDbState.NotInstalled, c.state.value)
        assertEquals(listOf<CheatDbMessage>(CheatDbMessage.Notice("Cheats & Patches removed")), messages)
    }

    @Test fun actions_while_busy_are_ignored() = runTest {
        val fake = FakeCheatDbPlatform().apply { downloadGate = CompletableDeferred() }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh(); c.download()
        c.download(); c.checkForUpdates(); c.remove(); c.refresh()
        assertIs<CheatDbState.Downloading>(c.state.value)
        assertEquals(0, fake.remoteCalls)
        fake.downloadGate!!.complete(Unit)
        assertIs<CheatDbState.Installed>(c.state.value)
        assertEquals(1, fake.discarded.size)
    }

    @Test fun cancel_once_the_files_are_written_still_records_them() = runTest {
        // The install returned: its files are on disk, so the manifest must list them or Remove could never find them.
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/old.cht"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); manifestGate = CompletableDeferred() }
        val c = controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), ArrayList())
        c.refresh(); c.download()
        assertTrue(c.busy)
        c.cancel()   // too late to take the install back: ignored
        assertTrue(c.busy)
        fake.manifestGate!!.complete(Unit)
        val installed = CheatDbManifest("stamp-2", 42L, listOf("Cheats/FDS/a.cht", "Cheats/GBA/a.cht"), table)
        assertEquals(CheatDbState.Installed(installed), c.state.value)
        assertEquals(installed.render(), fake.manifestText)
    }

    @Test fun a_scope_cancelled_while_the_manifest_is_written_still_writes_it() = runTest {
        val fake = FakeCheatDbPlatform().apply { manifestGate = CompletableDeferred() }
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val c = controller(fake, scope, ArrayList(), ArrayList())
        c.refresh(); c.download()
        scope.coroutineContext[Job]!!.cancel()
        fake.manifestGate!!.complete(Unit)
        assertEquals(CheatDbManifest("stamp-2", 42L, listOf("Cheats/FDS/a.cht", "Cheats/GBA/a.cht"), table).render(), fake.manifestText)
        assertEquals(listOf("/cache/cheats.zip"), fake.discarded)
    }

    // PS2: its rows come from PCSX2's patches.zip, the second source, installed in the same call as libretro's.

    private fun ps2Controller(fake: FakeCheatDbPlatform, scope: CoroutineScope, states: MutableList<CheatDbState>, messages: MutableList<CheatDbMessage>): CheatDbController {
        val c = CheatDbController(fake, setOf("FDS", "PS2"), scope, now = { 42L })
        scope.launch { c.state.collect { states += it } }
        scope.launch { c.messages.collect { messages += it } }
        return c
    }

    @Test fun ps2_downloads_both_archives_and_installs_them_together() = runTest {
        val fake = FakeCheatDbPlatform()
        val states = ArrayList<CheatDbState>(); val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), states, messages)
        c.refresh(); c.download()
        assertEquals(listOf(CheatDbSource.LIBRETRO, CheatDbSource.PCSX2), fake.downloads)
        assertEquals(listOf(listOf(CheatDbSource.LIBRETRO, CheatDbSource.PCSX2)), fake.installs)   // one call: a cancel takes back both
        assertEquals(listOf(CheatDbSource.PCSX2), fake.optional)   // the patches are best effort beside the cheats
        val manifest = assertIs<CheatDbState.Installed>(c.state.value).manifest
        assertEquals(CheatDbManifest("stamp-2", 42L, listOf("Cheats/FDS/a.cht", "Cheats/PS2/Patches/a.pnach"), listOf("FDS", "PS2"), "patches-2"), manifest)
        assertEquals(listOf("/cache/cheats.zip", "/cache/patches.zip"), fake.discarded)
        assertEquals(CheatDbMessage.Notice("Cheats & Patches installed: 2 files for 2 systems"), messages.last())
        assertTrue(states.any { it is CheatDbState.Downloading && it.source == CheatDbSource.PCSX2 })
        assertTrue(states.any { it == CheatDbState.Installing("PS2", 2, 2, null) })
    }

    @Test fun ps2_alone_downloads_only_the_patches() = runTest {
        val fake = FakeCheatDbPlatform()
        val c = CheatDbController(fake, setOf("PS2"), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { 42L })
        c.refresh(); c.download()
        assertEquals(listOf(CheatDbSource.PCSX2), fake.downloads)
        val manifest = assertIs<CheatDbState.Installed>(c.state.value).manifest
        assertEquals(CheatDbManifest.UNKNOWN_STAMP, manifest.stamp)
        assertEquals("patches-2", manifest.patchesStamp)
    }

    // PCSX2's patches.zip is best effort beside libretro's cheats.zip: its failure never blocks the other consoles' cheats.

    @Test fun a_failed_patches_download_still_installs_the_cheats_and_keeps_the_previous_patches() = runTest {
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/old.cht", "Cheats/PS2/Patches/old.pnach"), listOf("FDS", "PS2"), "patches-1")
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); failSource = CheatDbSource.PCSX2 }
        val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertEquals(listOf(listOf(CheatDbSource.LIBRETRO)), fake.installs)
        assertEquals(listOf("/cache/cheats.zip"), fake.discarded)
        // The old patches and their stamp stay listed (Remove finds them; the next check fetches patches.zip again).
        val manifest = assertIs<CheatDbState.Installed>(c.state.value).manifest
        assertEquals(CheatDbManifest("stamp-2", 42L, listOf("Cheats/FDS/a.cht", "Cheats/PS2/Patches/old.pnach"), listOf("FDS", "PS2"), "patches-1"), manifest)
        assertEquals(listOf(listOf("Cheats/FDS/old.cht")), fake.pruned)   // only what libretro's install dropped
        assertEquals(CheatDbMessage.Notice("Cheats & Patches installed: 2 files for 2 systems (PS2 patches could not be downloaded)"), messages.last())
    }

    @Test fun a_fresh_install_without_the_patches_fetches_them_on_the_next_update_check() = runTest {
        val fake = FakeCheatDbPlatform().apply { failSource = CheatDbSource.PCSX2 }
        val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        val manifest = assertIs<CheatDbState.Installed>(c.state.value).manifest
        assertEquals(CheatDbManifest("stamp-2", 42L, listOf("Cheats/FDS/a.cht"), listOf("FDS", "PS2"), null), manifest)
        assertTrue(manifest.render().lines().none { it.startsWith("patches_stamp") })
        fake.failSource = null; fake.downloads.clear()
        c.checkForUpdates()
        assertEquals(listOf(CheatDbSource.LIBRETRO, CheatDbSource.PCSX2), fake.downloads)
        assertEquals("patches-2", assertIs<CheatDbState.Installed>(c.state.value).manifest.patchesStamp)
        assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
    }

    @Test fun a_failed_patches_install_keeps_the_cheats_and_the_previous_patches() = runTest {
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/a.cht", "Cheats/PS2/Patches/old.pnach"), listOf("FDS", "PS2"), "patches-1")
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); failInstallSource = CheatDbSource.PCSX2 }
        val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        val manifest = assertIs<CheatDbState.Installed>(c.state.value).manifest
        assertEquals(listOf("Cheats/FDS/a.cht", "Cheats/PS2/Patches/old.pnach"), manifest.files)
        assertEquals("patches-1", manifest.patchesStamp)
        assertEquals("stamp-2", manifest.stamp)
        assertEquals(listOf("/cache/cheats.zip", "/cache/patches.zip"), fake.discarded)
        assertTrue((messages.last() as CheatDbMessage.Notice).text.endsWith("(PS2 patches could not be downloaded)"))
    }

    @Test fun a_failed_cheats_download_or_install_keeps_the_previous_install() = runTest {
        val previous = CheatDbManifest("stamp-1", 1L, listOf("Cheats/FDS/old.cht", "Cheats/PS2/Patches/old.pnach"), listOf("FDS", "PS2"), "patches-1")
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render(); failSource = CheatDbSource.LIBRETRO }
        val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.download()
        assertEquals(listOf(CheatDbSource.LIBRETRO), fake.downloads)   // patches.zip is not fetched for nothing
        assertEquals(emptyList(), fake.installs)
        assertEquals(CheatDbState.Installed(previous), c.state.value)
        assertEquals(CheatDbMessage.Failure(CheatDbException.Kind.NETWORK.message), messages.last())
        // The cheats archive is corrupt: the whole install fails (the platform takes back both archives' new files).
        fake.failSource = null; fake.failInstallSource = CheatDbSource.LIBRETRO
        c.download()
        assertEquals(CheatDbState.Installed(previous), c.state.value)
        assertEquals(previous.render(), fake.manifestText)
        assertEquals(CheatDbMessage.Failure(CheatDbException.Kind.CORRUPT.message), messages.last())
    }

    @Test fun ps2_alone_needs_the_patches() = runTest {
        val fake = FakeCheatDbPlatform().apply { failSource = CheatDbSource.PCSX2 }
        val c = CheatDbController(fake, setOf("PS2"), CoroutineScope(UnconfinedTestDispatcher(testScheduler)), now = { 42L })
        c.refresh(); c.download()
        assertEquals(CheatDbState.NotInstalled, c.state.value)   // nothing else to install: a failure, as before
        assertEquals(emptyList(), fake.installs)
    }

    @Test fun ps2_update_check_compares_both_stamps() = runTest {
        val current = CheatDbManifest("stamp-2", 1L, listOf("Cheats/FDS/a.cht", "Cheats/PS2/Patches/a.pnach"), listOf("FDS", "PS2"), "patches-2")
        val fake = FakeCheatDbPlatform().apply { manifestText = current.render() }
        val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.checkForUpdates()
        assertEquals(2, fake.remoteCalls)
        assertEquals(CheatDbMessage.Notice("Cheats & Patches are up to date"), messages.last())
        assertEquals(emptyList(), fake.downloads)
        // Only patches.zip changed: everything is downloaded again.
        fake.patchesRemote = "patches-3"; fake.patchesDownloadStamp = "patches-3"
        c.checkForUpdates()
        assertEquals(listOf(CheatDbSource.LIBRETRO, CheatDbSource.PCSX2), fake.downloads)
        assertEquals("patches-3", assertIs<CheatDbState.Installed>(c.state.value).manifest.patchesStamp)
        // patches.zip unreachable: decided by buildbot's answer alone, up to date here.
        fake.patchesRemote = null; fake.downloads.clear()
        c.checkForUpdates()
        assertEquals(CheatDbMessage.Notice("Cheats & Patches are up to date"), messages.last())
        assertEquals(emptyList(), fake.downloads)
        // ...and an update when cheats.zip changed.
        fake.remote = "stamp-3"; fake.downloadStamp = "stamp-3"
        c.checkForUpdates()
        assertEquals(listOf(CheatDbSource.LIBRETRO, CheatDbSource.PCSX2), fake.downloads)
        // buildbot unreachable: the check fails, as before.
        fake.remote = null
        c.checkForUpdates()
        assertEquals(CheatDbMessage.Failure("Could not check for updates (Wi-Fi?)"), messages.last())
    }

    @Test fun a_libretro_only_install_gets_the_patches_on_the_next_update_check() = runTest {
        // A manifest from before patches.zip was a source: its rows lack PS2 and it has no patches stamp.
        val previous = CheatDbManifest("stamp-2", 1L, listOf("Cheats/FDS/a.cht"), listOf("FDS"))
        val fake = FakeCheatDbPlatform().apply { manifestText = previous.render() }
        val messages = ArrayList<CheatDbMessage>()
        val c = ps2Controller(fake, CoroutineScope(UnconfinedTestDispatcher(testScheduler)), ArrayList(), messages)
        c.refresh(); c.checkForUpdates()
        assertEquals(CheatDbMessage.Notice("Cheats & Patches updated: 2 files for 2 systems"), messages.last())
        assertEquals(listOf("FDS", "PS2"), assertIs<CheatDbState.Installed>(c.state.value).manifest.tags)
    }
}
