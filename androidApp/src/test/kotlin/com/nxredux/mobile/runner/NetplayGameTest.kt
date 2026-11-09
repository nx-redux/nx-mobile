package com.nxredux.mobile.runner

import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.swordfish.libretrodroid.LibretroNetplay
import com.nxredux.mobile.netplay.NetplayCores
import com.nxredux.mobile.netplay.wizard.CableLinkSetup
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class NetplayGameTest {
    private fun notice(state: Int, seconds: Int = 0, reason: Int = LibretroNetplay.REASON_NONE, onCopy: Boolean = false, connectingMs: Long = 0) =
        netplayNotice(state, seconds, reason, onCopy, connectingMs)

    @Test fun countdowns_show_as_the_pill() {
        assertEquals(NetplayNotice.Pill(NetplayText.waiting(12)), notice(LibretroNetplay.STATE_STALLED, 12))
        assertEquals(NetplayNotice.Pill(NetplayText.peerPaused(7)), notice(LibretroNetplay.STATE_PEER_PAUSED, 7))
    }

    @Test fun a_running_session_or_a_local_pause_shows_nothing() {
        // A local pause reports RUNNING with its own countdown: only its eventual ENDED is shown.
        assertEquals(NetplayNotice.Clear, notice(LibretroNetplay.STATE_RUNNING, 25))
        assertEquals(NetplayNotice.Clear, notice(LibretroNetplay.STATE_SYNCING))
        assertEquals(NetplayNotice.Clear, notice(LibretroNetplay.STATE_IDLE))
    }

    @Test fun ended_says_whether_progress_is_kept() {
        assertEquals(NetplayNotice.Ended("${NetplayText.ENDED}\n${NetplayText.ENDED_DETAIL}"),
            notice(LibretroNetplay.STATE_ENDED, reason = LibretroNetplay.REASON_PEER_LEFT))
        assertEquals(NetplayNotice.Ended("${NetplayText.ENDED_COPY}\n${NetplayText.ENDED_DETAIL}"),
            notice(LibretroNetplay.STATE_ENDED, reason = LibretroNetplay.REASON_TIMEOUT, onCopy = true))
    }

    @Test fun failed_says_why() {
        assertEquals(NetplayNotice.Failed(NetplayText.FAILED_MISMATCH),
            notice(LibretroNetplay.STATE_FAILED, reason = LibretroNetplay.REASON_STATE_MISMATCH))
        assertEquals(NetplayNotice.Failed(NetplayText.FAILED_CONNECT), notice(LibretroNetplay.STATE_FAILED, reason = LibretroNetplay.REASON_CONNECT))
        assertEquals(NetplayNotice.Failed(NetplayText.FAILED_TIMEOUT), notice(LibretroNetplay.STATE_FAILED, reason = LibretroNetplay.REASON_TIMEOUT))
        assertEquals(NetplayNotice.Failed(NetplayText.FAILED_IO), notice(LibretroNetplay.STATE_FAILED, reason = LibretroNetplay.REASON_IO))
        assertEquals(NetplayNotice.Failed(NetplayText.FAILED), notice(LibretroNetplay.STATE_FAILED, reason = LibretroNetplay.REASON_NONE))
        assertEquals("Could not reach the other device.", NetplayText.FAILED_CONNECT)
        assertEquals("The other device stopped responding.", NetplayText.FAILED_TIMEOUT)
        assertEquals("The connection was lost.", NetplayText.FAILED_IO)
    }

    @Test fun no_peer_within_the_connect_timeout_fails() {
        assertEquals(45_000L, NETPLAY_CONNECT_TIMEOUT_MS)
        assertEquals(NetplayNotice.Clear, notice(LibretroNetplay.STATE_CONNECTING, connectingMs = NETPLAY_CONNECT_TIMEOUT_MS - 1))
        assertEquals(NetplayNotice.Failed(NetplayText.FAILED_CONNECT), notice(LibretroNetplay.STATE_CONNECTING, connectingMs = NETPLAY_CONNECT_TIMEOUT_MS))
    }

    @Test fun the_session_comes_from_the_extras() {
        assertEquals(NetplaySession(NetplayRole.HOST, "10.0.0.9", "picodrive", "genesis_plus_gx"),
            netplaySessionFrom("HOST", "10.0.0.9", "picodrive", "genesis_plus_gx"))
        assertEquals(NetplaySession(NetplayRole.CLIENT, "10.0.0.2", "snes9x", "snes9x"),
            netplaySessionFrom("CLIENT", "10.0.0.2", "snes9x", "snes9x"))
        assertNull(netplaySessionFrom(null, null, "snes9x", null))          // every other launch
        assertNull(netplaySessionFrom("BOGUS", "10.0.0.2", "snes9x", "snes9x"))
        assertNull(netplaySessionFrom("CLIENT", "", "snes9x", "snes9x"))   // a client needs the host
        assertNull(netplaySessionFrom("HOST", "10.0.0.2", "snes9x", null))
    }

    @Test fun both_roles_need_the_peer_as_ipv4() {
        // The host's engine accepts only this address: without one the session fails closed.
        assertNull(netplaySessionFrom("HOST", "", "snes9x", "snes9x"))
        assertNull(netplaySessionFrom("HOST", null, "snes9x", "snes9x"))
        assertNull(netplaySessionFrom("HOST", "fe80::1", "snes9x", "snes9x"))
        assertNull(netplaySessionFrom("CLIENT", "not an ip", "snes9x", "snes9x"))
        assertEquals("10.0.0.2", netplaySessionFrom("HOST", "::ffff:10.0.0.2", "snes9x", "snes9x")?.peerIp)
    }

    @Test fun empty_saves_travel_in_the_extras() {
        assertEquals(NetplaySession(NetplayRole.HOST, "10.0.0.9", "snes9x", "snes9x", emptySaves = true),
            netplaySessionFrom("HOST", "10.0.0.9", "snes9x", "snes9x", emptySaves = true))
        assertEquals(false, netplaySessionFrom("CLIENT", "10.0.0.2", "snes9x", "snes9x")?.emptySaves)
    }

    @Test fun over_usb_travels_in_the_extras() {
        assertEquals(true, netplaySessionFrom("HOST", "10.99.0.2", "snes9x", "snes9x", overUsb = true)?.overUsb)
        assertEquals(false, netplaySessionFrom("CLIENT", "10.0.0.2", "snes9x", "snes9x")?.overUsb)
    }

    @Test fun first_frame_notices() {
        val real = NetplaySession(NetplayRole.HOST, "10.0.0.2", "snes9x", "snes9x")
        assertEquals(emptyList<String>(), netplayFirstFrameNotices(real, "Snes9x", ownCopyFailed = false))
        val other = NetplaySession(NetplayRole.HOST, "10.0.0.2", "picodrive", "genesis_plus_gx")
        assertEquals(listOf(NetplayText.usingCopy("PicoDrive")), netplayFirstFrameNotices(other, "PicoDrive", ownCopyFailed = false))
        assertEquals(listOf(NetplayText.usingCopy("PicoDrive"), NetplayText.OWN_COPY_FAILED), netplayFirstFrameNotices(other, "PicoDrive", ownCopyFailed = true))
        assertEquals(listOf(NetplayText.SAVES_NOT_COPIED), netplayFirstFrameNotices(real.copy(emptySaves = true), "Snes9x", ownCopyFailed = false))
        val joiner = NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "snes9x", "snes9x")
        assertEquals(listOf(NetplayText.SAVES_NOT_COPIED), netplayFirstFrameNotices(joiner.copy(emptySaves = true), "Snes9x", ownCopyFailed = false))
        assertEquals(emptyList<String>(), netplayFirstFrameNotices(joiner, "Snes9x", ownCopyFailed = false))
    }

    /** Records every engine call in order; [state] and [reason] are what status reports. */
    private class FakeEngine(var state: Int = LibretroNetplay.STATE_RUNNING, var reason: Int = LibretroNetplay.REASON_NONE) : LockstepEngine {
        val calls: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
        var pairOk = true
        var verified = -1L
        override fun shutdown() { calls += "shutdown" }
        override fun setPresyncFrames(frames: Int) { calls += "presync $frames" }
        override fun setPairMode(on: Boolean): Boolean { calls += "pair $on"; return !on || pairOk }
        override fun setDelayPin(frames: Int) { calls += "pin $frames" }
        override fun start(role: Int, peerIp: String, port: Int): Boolean { calls += "start"; return true }
        override fun status(): IntArray = intArrayOf(state, 0, reason)
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun leave() { calls += "leave" }
        override fun currentDelay(): Int = 3
        override fun lastVerifiedFrame(): Long { calls += "verified"; return verified }
    }

    private val cable = NetplaySession(NetplayRole.HOST, "10.0.0.2", "mgba", "gpsp", cableLink = true, otherRomPath = "/c/rom", localSlot = 1)

    /** Lets the engine thread finish what was queued on it. */
    private fun drainEngine() = NetplayEngine.executor.submit {}.get()

    @Test fun developer_pin_reaches_engine() = runBlocking {
        val engine = FakeEngine()
        val delays = java.util.Collections.synchronizedList(ArrayList<Int>())
        val game = NetplayGame(cable, this, { 0L }, {}, engine, delayPin = 3, onDelay = { delays += it })
        assertTrue(game.start())
        // Pair mode is asked for after the previous session's shutdown and before the start, with the pin.
        assertEquals(listOf("shutdown", "presync 0", "pair true", "pin 3", "start"), engine.calls.toList())
        delay(50)
        assertEquals(3, delays.firstOrNull())                               // the Developer overlay's reading
        game.detach()

        // Any other session asks pair mode off (a failed start can leave a request set) and never pins.
        val plain = FakeEngine()
        val other = NetplayGame(NetplaySession(NetplayRole.HOST, "10.0.0.2", "snes9x", "snes9x"), this, { 0L }, {}, plain, delayPin = 3)
        assertTrue(other.start())
        assertEquals(listOf("shutdown", "presync 0", "pair false", "pin 0", "start"), plain.calls.toList())
        other.detach()

        // A core without the pair hash: no start.
        val noPair = FakeEngine().apply { pairOk = false }
        assertFalse(NetplayGame(cable, this, { 0L }, {}, noPair, delayPin = 0).start())
        assertFalse("start" in noPair.calls)
        assertEquals(8, NetplayCores.CABLE_LINK_DELAY_MAX)
    }

    @Test fun drift_and_too_slow_end_with_copy_and_commit() = runBlocking {
        assertEquals(NetplayNotice.Ended(NetplayText.ENDED_DRIFT), notice(LibretroNetplay.STATE_ENDED, reason = LibretroNetplay.REASON_DRIFT))
        assertEquals(NetplayNotice.Ended(NetplayText.ENDED_TOO_SLOW), notice(LibretroNetplay.STATE_ENDED, reason = LibretroNetplay.REASON_TOO_SLOW))
        assertEquals("The two games drifted apart, so the session ended. Your progress up to a few seconds ago is saved.", NetplayText.ENDED_DRIFT)
        assertEquals("This phone can't run both games at full speed, so the session ended.", NetplayText.ENDED_TOO_SLOW)
        // The other player leaving keeps today's text.
        assertEquals(NetplayNotice.Ended("${NetplayText.ENDED}\n${NetplayText.ENDED_DETAIL}"), notice(LibretroNetplay.STATE_ENDED, reason = LibretroNetplay.REASON_PEER_LEFT))
        assertEquals(NetplayNotice.Pill(NetplayText.SYNCING), notice(LibretroNetplay.STATE_RESYNCING))

        for (reason in listOf(LibretroNetplay.REASON_DRIFT, LibretroNetplay.REASON_TOO_SLOW, LibretroNetplay.REASON_PEER_LEFT, LibretroNetplay.REASON_TIMEOUT)) {
            val engine = FakeEngine(LibretroNetplay.STATE_ENDED, reason).apply { verified = 360 }
            val notices = java.util.Collections.synchronizedList(ArrayList<NetplayNotice>())
            val endings = java.util.Collections.synchronizedList(ArrayList<Long>())
            val game = NetplayGame(cable, this, { 0L }, { notices += it }, engine, onEnding = { endings += it; engine.calls += "ending" })
            assertTrue(game.start())
            delay(50)
            drainEngine()
            assertEquals(listOf(360L), endings.toList())                    // the checkpoint's frame, once, for the commit
            val tail = engine.calls.toList().dropWhile { it != "verified" }
            assertEquals(listOf("verified", "ending", "shutdown"), tail)     // read before the shutdown resets it
            assertTrue(notices.single() is NetplayNotice.Ended)
            assertFalse(game.live)
        }

        // Leave commits too, before the DISCONNECT and shutdown.
        val engine = FakeEngine().apply { verified = 120 }
        val endings = java.util.Collections.synchronizedList(ArrayList<Long>())
        val game = NetplayGame(cable, this, { 0L }, {}, engine, onEnding = { endings += it; engine.calls += "ending" })
        assertTrue(game.start())
        game.leave()
        drainEngine()
        assertEquals(listOf(120L), endings.toList())
        assertEquals(listOf("verified", "ending", "leave", "shutdown"), engine.calls.toList().dropWhile { it != "verified" })
    }

    @Test fun a_cable_link_session_comes_from_the_extras() {
        val setup = CableLinkSetup("/files/link-roms/abc", "/files/cablelink/session/other.sav", 2, "real", 1_700_000_000L)
        val s = netplaySessionFrom("CLIENT", "10.0.0.1", "mgba", "gpsp", cableLink = setup)
        assertEquals(NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "mgba", "gpsp").withCableLink(setup), s)
        assertNull(s?.linkKind)                                             // the lockstep engine, not the GBA link
        assertNull(netplaySessionFrom("CLIENT", "10.0.0.1", "mgba", "gpsp", cableLink = setup.copy(otherRomPath = "")))
        assertNull(netplaySessionFrom("CLIENT", "10.0.0.1", "mgba", "gpsp", cableLink = setup.copy(localSlot = 0)))
        assertNull(netplaySessionFrom("CLIENT", "10.0.0.1", "mgba", "gpsp", cableLink = setup.copy(biosMode = "")))
    }
}
