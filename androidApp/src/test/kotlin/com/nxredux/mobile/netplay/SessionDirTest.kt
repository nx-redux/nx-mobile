package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.wizard.SaveSink
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.Rzip
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SessionDirTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Stands in for rsync: writes the pulled name into the staging dir (nothing with [leaveNothing]). */
    private class FakePuller(private val leaveNothing: Boolean = false) : SavePuller {
        val pulls = ArrayList<String>()
        override suspend fun pull(hostIp: String, wireName: String, destDir: File) {
            pulls += "$hostIp $wireName"
            if (!leaveNothing) File(destDir, wireName).writeBytes(byteArrayOf(1, 2))
        }
    }

    private fun SaveSink.pullNow(name: String) = runBlocking { pull("10.0.0.1", name) }

    @Test fun sweep_removes_stale_session_dir() {
        val dir = File(tmp.root, "netplay/session").apply { mkdirs() }
        File(dir, "Game.srm").writeText("x")
        NetplaySessionDir.sweep(tmp.root)
        assertFalse(dir.exists())
    }

    @Test fun sink_promotes_all_or_nothing() {
        val p = FakePuller()
        val sink = NetplaySessionDir.sink(tmp.root, "Game (USA).sfc", p)
        assertTrue(sink.canPull)
        sink.begin(); sink.pullNow("nxsave.srm"); sink.abort()
        assertFalse(File(tmp.root, "netplay/session/Game (USA).srm").exists())
        sink.begin(); sink.pullNow("nxsave.srm"); sink.commit()
        assertTrue(File(tmp.root, "netplay/session/Game (USA).srm").exists())
        assertEquals(listOf("10.0.0.1 nxsave.srm", "10.0.0.1 nxsave.srm"), p.pulls)
    }

    @Test fun a_sink_without_rsync_cannot_pull() {
        val sink = NetplaySessionDir.sink(tmp.root, "Game.sfc", null)
        assertFalse(sink.canPull)
        sink.begin()
        try { sink.pullNow("nxsave.srm"); fail("pulled without rsync") } catch (_: IOException) {}
    }

    @Test fun a_pull_that_leaves_no_file_fails() {
        val sink = NetplaySessionDir.sink(tmp.root, "Game.sfc", FakePuller(leaveNothing = true))
        sink.begin()
        try { sink.pullNow("nxsave.srm"); fail("no file, no success") } catch (_: IOException) {}
    }

    @Test fun only_save_names_are_pulled() {
        val p = FakePuller()
        val sink = NetplaySessionDir.sink(tmp.root, "Game.sfc", p)
        sink.begin()
        try { sink.pullNow("nxsave.brm"); fail("pulled a .brm") } catch (_: IllegalArgumentException) {}
        assertTrue(p.pulls.isEmpty())
    }

    @Test fun release_deletes_only_its_own_claim() {
        NetplaySessionDir.claim(tmp.root, "old")
        // A new launch's commit replaces the copy (unclaimed) before the old screen's release runs.
        val sink = NetplaySessionDir.sink(tmp.root, "Game.sfc", FakePuller())
        sink.begin(); sink.pullNow("nxsave.srm"); sink.commit()
        assertFalse(NetplaySessionDir.release(tmp.root, "old"))
        assertTrue(File(tmp.root, "netplay/session/Game.srm").exists())
        NetplaySessionDir.claim(tmp.root, "new")
        assertTrue(File(tmp.root, "netplay/session/Game.srm").exists())
        assertFalse(NetplaySessionDir.release(tmp.root, "old"))
        assertTrue(NetplaySessionDir.release(tmp.root, "new"))
        assertFalse(NetplaySessionDir.dir(tmp.root).exists())
    }

    @Test fun claim_empties_a_copy_another_screen_claimed() {
        NetplaySessionDir.claim(tmp.root, "old")
        File(NetplaySessionDir.dir(tmp.root), "Game.srm").writeText("stale")
        NetplaySessionDir.claim(tmp.root, "new")
        assertFalse(File(NetplaySessionDir.dir(tmp.root), "Game.srm").exists())
        assertEquals("new", NetplaySessionDir.ownerOf(tmp.root))
    }

    @Test fun a_new_sink_starts_from_an_empty_dir() {
        val old = NetplaySessionDir.sink(tmp.root, "Game.sfc", FakePuller())
        old.begin(); old.pullNow("nxsave.srm"); old.commit()
        NetplaySessionDir.sink(tmp.root, "Game.sfc", FakePuller())   // a new join whose host sends no saves
        assertFalse(File(tmp.root, "netplay/session/Game.srm").exists())
        NetplaySessionDir.claim(tmp.root, "old-screen")
        File(NetplaySessionDir.dir(tmp.root), "Game.srm").writeText("old")
        NetplaySessionDir.sink(tmp.root, "Game.sfc", null)
        assertFalse(NetplaySessionDir.dir(tmp.root).exists())
        assertFalse(NetplaySessionDir.release(tmp.root, "old-screen"))
        NetplaySessionDir.claim(tmp.root, "new-screen")
        assertEquals(listOf(NetplaySessionDir.OWNER_FILE), NetplaySessionDir.dir(tmp.root).list()!!.toList())
    }

    @Test fun startup_sweep_runs_once_per_process() {
        val gate = java.util.concurrent.atomic.AtomicBoolean(false)
        NetplaySessionDir.claim(tmp.root, "stale")
        NetplaySessionDir.sweepOnce(tmp.root, gate) { false }
        assertFalse(NetplaySessionDir.dir(tmp.root).exists())
        NetplaySessionDir.claim(tmp.root, "live")
        NetplaySessionDir.sweepOnce(tmp.root, gate) { false }
        assertEquals("live", NetplaySessionDir.ownerOf(tmp.root))
    }

    @Test fun sweep_once_skips_a_copy_under_a_live_game_process() {
        NetplaySessionDir.claim(tmp.root, "game-process")   // a menu restarted mid-game: the copy is the running game's
        NetplaySessionDir.sweepOnce(tmp.root, java.util.concurrent.atomic.AtomicBoolean(false)) { true }
        assertEquals("game-process", NetplaySessionDir.ownerOf(tmp.root))
    }

    @Test fun claim_release_and_the_sink_share_one_lock() {
        val held = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(1)
        val holder = Thread { synchronized(NetplaySessionDir) { held.countDown(); done.await() } }.apply { start() }
        held.await()
        val claim = Thread { NetplaySessionDir.claim(tmp.root, "b") }.apply { start() }
        val release = Thread { NetplaySessionDir.release(tmp.root, "b") }
        val sink = Thread { NetplaySessionDir.sink(tmp.root, "Game.sfc", null) }.apply { start() }
        claim.join(200); sink.join(200)
        assertTrue(claim.isAlive); assertTrue(sink.isAlive)
        assertFalse(File(tmp.root, "netplay/session").exists())
        done.countDown()
        holder.join(); claim.join(); sink.join()
        release.start(); release.join()
        assertFalse(File(tmp.root, "netplay/session").exists())
    }

    @Test fun host_on_empty_saves_claims_an_empty_copy() {
        val saves = File(tmp.root, "sd/Saves/MD").apply { mkdirs() }
        File(saves, "Sonic.srm").writeText("real")
        NetplaySessionDir.claim(tmp.root, "old")
        File(NetplaySessionDir.dir(tmp.root), "Sonic.srm").writeText("stale")
        assertTrue(NetplaySessionDir.prepareHost(tmp.root, "new", savesDir = null, paths = NxPaths("MD", "x", "Sonic.md")))
        assertEquals(listOf(NetplaySessionDir.OWNER_FILE), NetplaySessionDir.dir(tmp.root).list()!!.toList())
        assertEquals("new", NetplaySessionDir.ownerOf(tmp.root))
        assertEquals("real", File(saves, "Sonic.srm").readText())   // the real save is never touched
    }

    @Test fun a_failed_host_copy_leaves_no_half_copy() {
        val saves = File(tmp.root, "sd/Saves/MD").apply { mkdirs() }
        val srm = File(saves, "Sonic.srm").apply { writeText("srm"); assertTrue(setReadable(false)) }
        try {
            val causes = ArrayList<Throwable>()
            assertFalse(NetplaySessionDir.prepareHost(tmp.root, "me", saves, NxPaths("MD", "x", "Sonic.md")) { causes += it })
            assertEquals(1, causes.size)
            assertEquals(listOf(NetplaySessionDir.OWNER_FILE), NetplaySessionDir.dir(tmp.root).list()!!.toList())
            assertEquals("me", NetplaySessionDir.ownerOf(tmp.root))
        } finally {
            srm.setReadable(true)
        }
    }

    @Test fun host_on_another_core_copies_the_configured_save_as_srm() {
        val saves = File(tmp.root, "sd/Saves/MD").apply { mkdirs() }
        for (n in listOf("Sonic.srm", "Sonic.sav", "Sonic.brm", "Other.srm")) File(saves, n).writeText(n)
        assertTrue(NetplaySessionDir.prepareHost(tmp.root, "me", saves, NxPaths("MD", "x", "Sonic.md")))
        assertEquals(setOf(NetplaySessionDir.OWNER_FILE, "Sonic.srm"), NetplaySessionDir.dir(tmp.root).list()!!.toSet())
        assertEquals("Sonic.srm", File(NetplaySessionDir.dir(tmp.root), "Sonic.srm").readText())
    }

    @Test fun host_finds_a_minui_named_compressed_save_and_decodes_it() {
        val saves = File(tmp.root, "sd/Saves/MD").apply { mkdirs() }
        File(saves, "Sonic.md.sav").writeBytes(Rzip.encode("progress".toByteArray()))
        assertTrue(NetplaySessionDir.prepareHost(tmp.root, "me", saves, NxPaths("MD", "x", "Sonic.md")))
        assertEquals("progress", File(NetplaySessionDir.dir(tmp.root), "Sonic.srm").readText())
    }

    @Test fun host_stages_nothing_for_a_malformed_rzip_save() {
        val saves = File(tmp.root, "sd/Saves/MD").apply { mkdirs() }
        File(saves, "Sonic.srm").writeBytes(Rzip.encode("progress".toByteArray()).copyOf(24))   // cut inside the first chunk
        assertTrue(NetplaySessionDir.prepareHost(tmp.root, "me", saves, NxPaths("MD", "x", "Sonic.md")))
        assertEquals(listOf(NetplaySessionDir.OWNER_FILE), NetplaySessionDir.dir(tmp.root).list()!!.toList())
    }
}
