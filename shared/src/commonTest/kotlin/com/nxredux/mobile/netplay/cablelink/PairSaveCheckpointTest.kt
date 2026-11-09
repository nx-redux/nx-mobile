package com.nxredux.mobile.netplay.cablelink

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PairSaveCheckpointTest {
    private class Files(var session: ByteArray? = null, var real: ByteArray? = null) : CheckpointFiles {
        var realWrites = 0
        var failReal = false
        override fun readSession(): ByteArray? = session?.copyOf()
        override fun writeSession(bytes: ByteArray) { session = bytes.copyOf() }
        override fun deleteSession() { session = null }
        override fun writeReal(bytes: ByteArray) {
            if (failReal) throw IllegalStateException("disk full")
            realWrites++
            real = bytes.copyOf()
        }
    }

    private fun save(b: Int) = ByteArray(8) { b.toByte() }

    @Test fun onVerified_then_commit_writes_last_verified() {
        val files = Files(real = save(0))
        val cp = PairSaveCheckpoint(files)
        cp.onVerified(120, save(1))
        cp.onVerified(240, save(2))
        cp.onVerified(120, save(9))   // an older checkpoint never replaces a newer one
        assertContentEquals(save(2), files.session)
        assertContentEquals(save(0), files.real)   // nothing reaches the real save before the end
        assertEquals(PairSaveCheckpoint.Commit.Written, cp.commit())
        assertContentEquals(save(2), files.real)
        assertNull(files.session)
        assertEquals(PairSaveCheckpoint.Commit.NothingVerified, cp.commit())   // twice: nothing left to write
        assertEquals(1, files.realWrites)
    }

    @Test fun commit_never_writes_unverified() {
        val files = Files(real = save(0))
        val cp = PairSaveCheckpoint(files)
        val frames = PairCheckpointFrames()
        // Snapshots at the check frames, but the other phone never agreed on any of them.
        for (f in 1L..400L) assertNull(frames.afterFrame(f, verifiedFrame = -1) { save(f.toInt()) })
        assertEquals(PairSaveCheckpoint.Commit.NothingVerified, cp.commit())
        assertContentEquals(save(0), files.real)
        assertEquals(0, files.realWrites)

        // A real-save write that fails keeps the session file for the next launch.
        val failing = Files(real = save(0)).apply { failReal = true }
        val cp2 = PairSaveCheckpoint(failing)
        cp2.onVerified(120, save(3))
        assertIs<PairSaveCheckpoint.Commit.NotWritten>(cp2.commit())
        assertContentEquals(save(3), failing.session)
    }

    @Test fun snapshots_pair_with_the_verified_frame() {
        val frames = PairCheckpointFrames()
        var reads = 0
        fun step(f: Long, verified: Long, b: Int) = frames.afterFrame(f, verified) { reads++; save(b) }
        assertNull(step(119, -1, 1))
        assertNull(step(120, -1, 1))                 // the check frame: a snapshot, not yet verified
        assertNull(step(120, -1, 7))                 // a step that ran no frame: no second snapshot
        assertEquals(1, reads)
        val hit = step(121, 120, 5)                  // the other phone's hash matched frame 120
        assertEquals(120L, hit?.frame)
        assertContentEquals(save(1), hit?.save)       // the save as it was at frame 120, not now
        assertNull(step(122, 120, 5))                // once only
        assertNull(step(240, 120, 2))
        assertNull(step(360, 120, 3))                // 240 was voided (a resync): passed over below
        val late = frames.verified(360)
        assertEquals(360L, late?.frame)
        assertContentEquals(save(3), late?.save)
        assertNull(frames.verified(240))             // dropped when 360 verified
    }

    @Test fun promotes_pending_session_save_on_next_launch() {
        // The process died mid-session: the last verified checkpoint is still in the session file.
        val files = Files(session = save(4), real = save(0))
        assertEquals(PairSaveCheckpoint.Commit.Written, PairSaveCheckpoint(files).promotePending())
        assertContentEquals(save(4), files.real)
        assertNull(files.session)
        // A normal launch with nothing pending touches nothing.
        val clean = Files(real = save(0))
        assertEquals(PairSaveCheckpoint.Commit.NothingVerified, PairSaveCheckpoint(clean).promotePending())
        assertEquals(0, clean.realWrites)
    }
}
