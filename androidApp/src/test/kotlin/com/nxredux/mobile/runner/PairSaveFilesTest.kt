package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.cablelink.CheckpointFiles
import com.nxredux.mobile.netplay.cablelink.PairSaveCheckpoint
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.Rzip
import com.nxredux.mobile.storage.SaveFormat
import com.nxredux.mobile.storage.SaveNaming
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PairSaveFilesTest {
    private val root: File = Files.createTempDirectory("pair-saves").toFile()

    @After fun cleanUp() {
        root.walkTopDown().forEach { it.setWritable(true) }
        root.deleteRecursively()
    }

    /** Records commits and writes; [post] runs at once, in call order. */
    private class Files0 : CheckpointFiles {
        var session: ByteArray? = null
        var real: ByteArray? = null
        val log = ArrayList<String>()
        override fun readSession() = session
        override fun writeSession(bytes: ByteArray) { session = bytes; log += "checkpoint ${bytes[0]}" }
        override fun deleteSession() { session = null }
        override fun writeReal(bytes: ByteArray) { real = bytes; log += "commit ${bytes[0]}" }
    }

    private fun save(b: Int) = ByteArray(4) { b.toByte() }

    @Test fun ended_then_destroyed_commits_once_with_the_last_verdict_first() {
        val files = Files0()
        val session = PairSaveSession(PairSaveCheckpoint(files)) { it.run() }
        session.afterFrame(120, -1) { save(1) }
        session.afterFrame(121, 120) { save(9) }                 // verified: the frame-120 save
        session.afterFrame(240, 120) { save(2) }                 // its verdict arrives with the ending
        assertTrue(session.end(240))                              // the engine ended (drift, peer left, ...)
        assertFalse(session.end(null))                            // then the screen goes: no second commit
        session.afterFrame(360, 360) { save(3) }                 // frames after the end are never saved
        assertEquals(listOf("checkpoint 1", "checkpoint 2", "commit 2"), files.log)
        assertArrayEquals(save(2), files.real)
        assertNull(files.session)
    }

    @Test fun leave_then_destroyed_commits_once() {
        val files = Files0()
        val posted = ArrayList<Runnable>()
        val session = PairSaveSession(PairSaveCheckpoint(files)) { posted += it }
        session.afterFrame(120, -1) { save(5) }
        assertTrue(session.end(120))                              // Leave reads the verified frame before the shutdown
        assertFalse(session.end(null))
        posted.forEach { it.run() }                               // the writer thread, later, in order
        assertEquals(listOf("checkpoint 5", "commit 5"), files.log)
    }

    @Test fun destroyed_without_a_verdict_commits_the_last_checkpoint_only() {
        val files = Files0()
        val session = PairSaveSession(PairSaveCheckpoint(files)) { it.run() }
        session.afterFrame(120, -1) { save(1) }
        session.afterFrame(130, 120) { save(1) }
        session.afterFrame(240, 120) { save(2) }                 // never verified
        assertTrue(session.end(null))
        assertArrayEquals(save(1), files.real)
    }

    private val naming = SaveNaming(saveFormat = SaveFormat.MINUI)
    private val paths = NxPaths("GBA", "mgba", "Pokemon Ruby (USA).gba", naming)

    @Test fun a_damaged_real_save_keeps_the_session_file() {
        val saves = File(root, "Saves/GBA").apply { mkdirs() }
        val target = File(saves, paths.saveRam.substringAfterLast('/'))
        target.writeBytes(Rzip.encode(ByteArray(64) { 7 }).copyOf(24))   // claims RZIP, cut short
        val battery = BatterySaves(saves, paths)
        assertNull(battery.load())
        assertTrue(battery.damaged)
        saves.setWritable(false)                                          // it can't be set aside: never overwritten
        val session = File(root, "session.verified")
        val files = AndroidCheckpointFiles(session, battery)
        val cp = PairSaveCheckpoint(files)
        cp.onVerified(120, save(3))
        assertTrue(cp.commit() is PairSaveCheckpoint.Commit.NotWritten)
        assertTrue(session.isFile)                                        // kept for a later launch

        // An unchanged real save counts as written.
        saves.setWritable(true)
        val ok = File(root, "Saves/GBA2").apply { mkdirs() }
        val battery2 = BatterySaves(ok, paths).also { it.write(save(4)) }
        val session2 = File(root, "session2.verified")
        val cp2 = PairSaveCheckpoint(AndroidCheckpointFiles(session2, battery2))
        cp2.onVerified(120, save(4))
        assertEquals(PairSaveCheckpoint.Commit.Written, cp2.commit())
        assertFalse(session2.exists())
    }

    @Test fun the_session_file_ignores_the_save_format() {
        val a = PairSaveFiles.sessionFile(root, NxPaths("GBA", "mgba", "Ruby.gba", SaveNaming(saveFormat = SaveFormat.MINUI)))
        for (format in SaveFormat.entries) {
            assertEquals(a, PairSaveFiles.sessionFile(root, NxPaths("GBA", "gpsp", "Ruby.gba", SaveNaming(saveFormat = format))))
        }
        assertEquals(a, PairSaveFiles.sessionFile(root, NxPaths("MGBA", "mgba", "Ruby.gba", naming)))   // either GBA folder
    }

    @Test fun a_real_save_changed_since_the_checkpoint_is_kept_before_promotion() {
        val saves = File(root, "Saves/GBA").apply { mkdirs() }
        val real = File(saves, "Ruby.srm").apply { writeBytes(save(8)) }
        val session = File(root, "s.verified").apply { writeBytes(save(1)) }
        val files = AndroidCheckpointFiles(session, BatterySaves(saves, paths))
        real.setLastModified(session.lastModified() - 10_000)
        assertNull(files.keepNewerReal(real))                              // older: the crash's checkpoint wins
        real.setLastModified(session.lastModified() + 10_000)              // Device Sync brought a newer one
        val bak = files.keepNewerReal(real)
        assertNotNull(bak)
        assertTrue(bak!!.name.startsWith("Ruby.srm.") && bak.name.endsWith(".bak"))
        assertArrayEquals(save(8), bak.readBytes())
    }

    private fun damagedConfigured(saves: File): File =
        File(saves, paths.saveRam.substringAfterLast('/')).apply { writeBytes(Rzip.encode(ByteArray(64) { 7 }).copyOf(24)) }

    @Test fun the_save_sent_is_the_one_the_launch_loads() {
        val saves = File(root, "Saves/GBA").apply { mkdirs() }
        val configured = damagedConfigured(saves)
        val alternate = paths.saveRamCandidates().map { it.substringAfterLast('/') }.first { it != configured.name }
        File(saves, alternate).writeBytes(save(6))
        val filesDir = File(root, "files")
        assertArrayEquals(save(6), PairSaveFiles.launchSave(filesDir, root, paths))
        assertArrayEquals(save(6), BatterySaves(saves, paths).load())

        // A checkpoint a dead session left comes first: the launch promotes it.
        PairSaveFiles.sessionFile(filesDir, paths).apply { parentFile!!.mkdirs(); writeBytes(save(2)) }
        assertArrayEquals(save(2), PairSaveFiles.launchSave(filesDir, root, paths))
    }

    @Test fun promotion_sets_a_damaged_real_save_aside_first() {
        val saves = File(root, "Saves/GBA").apply { mkdirs() }
        val configured = damagedConfigured(saves)
        val damagedBytes = configured.readBytes()
        val session = File(root, "s.verified").apply { writeBytes(save(3)) }
        configured.setLastModified(session.lastModified() - 10_000)
        val battery = BatterySaves(saves, paths)
        val files = AndroidCheckpointFiles(session, battery)
        assertEquals(PairSaveCheckpoint.Commit.Written, files.promotePending(File(root, paths.saveRam)))
        val baks = saves.listFiles()!!.filter { it.name.endsWith(".bak") }
        assertEquals(1, baks.size)
        assertArrayEquals(damagedBytes, baks.single().readBytes())
        assertArrayEquals(save(3), Rzip.decode(configured.readBytes()))
        assertFalse(session.exists())

        // Nothing pending: the real save is not even read.
        val clean = AndroidCheckpointFiles(File(root, "none.verified"), BatterySaves(saves, paths))
        assertEquals(PairSaveCheckpoint.Commit.NothingVerified, clean.promotePending(File(root, paths.saveRam)))
    }
}
