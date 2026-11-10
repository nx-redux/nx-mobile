package com.nxredux.mobile.sync

import com.nxredux.mobile.netplay.usb.UsbFail
import com.nxredux.mobile.netplay.usb.UsbLinkState
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSyncControllerTest {
    // Every run goes through the parse and plan hops ([DeviceSyncController]'s work context): named, on the test's
    // dispatcher, so the pages, files and virtual time are what they are with no hop at all.
    // [confirm]: A is pressed on the confirm page as soon as it shows, so a test about what follows pairing need not.
    private fun TestScope.rig(p: FakeSyncPlatform = FakeSyncPlatform(), confirm: Boolean = true) =
        DeviceSyncController(backgroundScope as CoroutineScope, p, work = kotlinx.coroutines.CoroutineName("work")) { testScheduler.currentTime }.also { c ->
            c.open()
            if (confirm) backgroundScope.launch { c.page.collect { if (it is DeviceSyncPage.Confirm) c.onButton(NxButton.A) } }
        }

    private fun DeviceSyncController.press(vararg b: NxButton) = b.forEach { onButton(it) }
    private val handheld = "192.168.1.10"
    private val phone = "192.168.1.20"
    private val saveSteps = listOf(SyncStep.SEND_STATES, SyncStep.SEND_SAVES, SyncStep.RECEIVE_STATES, SyncStep.RECEIVE_SAVES)

    @Test fun ready_offers_cable_first_and_up_down_switch() = runTest {
        val c = rig()
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), c.page.value)
        c.press(NxButton.DOWN); assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
        c.press(NxButton.UP); assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), c.page.value)
        c.pick(SyncLink.WIFI); assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
        assertFalse(c.onButton(NxButton.B))
    }

    @Test fun start_is_refused_while_a_game_is_open_or_an_import_runs() = runTest {
        val p = FakeSyncPlatform().apply { gameAlive = true }
        val c = rig(p)
        val notices = mutableListOf<String>()
        backgroundScope.launch { c.notices.collect { notices += it } }
        runCurrent()
        c.press(NxButton.A); runCurrent()
        p.gameAlive = false; p.importingNow = true
        c.press(NxButton.A); runCurrent()
        assertEquals(listOf(DeviceSyncText.CLOSE_GAME, DeviceSyncText.IMPORTING), notices)
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), c.page.value)
        assertFalse(c.running)
    }

    @Test fun wifi_without_wifi_shows_no_wifi_and_b_returns() = runTest {
        val p = FakeSyncPlatform().apply { wifi = null }
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.NoWifi, c.page.value)
        assertTrue(c.onButton(NxButton.B))
        assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
    }

    /** The main path: a new handheld serves, the phone is the client over Wi-Fi. */
    @Test fun wifi_client_pairs_runs_four_steps_and_says_done() = runTest {
        val p = FakeSyncPlatform()
        p.steps[SyncStep.SEND_STATES] = listOf("GB-gambatte/T.st0", " 1 (xfr#1, to-chk=0/3)") to 0
        p.steps[SyncStep.RECEIVE_SAVES] = listOf("GB/A.srm", "GB/B.srm", " 2 (xfr#2, to-chk=0/5)") to 0
        val c = rig(p)
        val got = mutableListOf<Int>()
        backgroundScope.launch { c.received.collect { got += it } }
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.Searching(SyncLink.WIFI, phone), c.page.value)
        assertEquals(listOf(SyncWire.hello(phone)), p.socket.broadcasts)
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        assertTrue(SyncWire.ack(phone) to handheld in p.socket.sent)
        assertEquals(SyncRole.CLIENT, (c.page.value as DeviceSyncPage.Syncing).role)
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps, p.stepsRun)
        assertEquals(5, p.socket.sent.count { it == SyncWire.DONE to handheld })
        // No All files access here: the BIOS steps are skipped and Done says so.
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 2, sent = 1, notes = listOf(DeviceSyncText.BIOS_NO_ACCESS, DeviceSyncText.COLLECTIONS_NO_ACCESS)), c.page.value)
        assertEquals(listOf(2), got)
        assertTrue(p.socket.closed)
        assertEquals(true to DeviceSyncText.TITLE, p.alive.first())
        assertEquals(false to null, p.alive.last())
        assertFalse(c.running)
    }

    @Test fun hello_is_repeated_every_second_while_searching() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        advanceTimeBy(3_100); runCurrent()
        assertEquals(4, p.socket.broadcasts.size)
    }

    /** Our own echo, a payload/source mismatch, a cable-range source and stale READY/DONE never pair. */
    @Test fun discovery_ignores_echoes_mismatches_and_stale_messages() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(phone, SyncWire.hello(phone))
        p.socket.from("192.168.1.11", "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2")
        p.socket.from("10.99.0.2", "HELLO_TRIMUI_SYNC:10.99.0.2:0\u0000v2")
        p.socket.from(handheld, SyncWire.READY)
        p.socket.from(handheld, SyncWire.DONE)
        p.socket.from(handheld, "garbage")
        advanceTimeBy(2_000); runCurrent()
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        assertTrue(p.socket.sent.isEmpty())
    }

    /**
     * The handheld paired with our HELLO, its one ACK was lost, and it answers no more HELLOs, so the phone would search
     * forever while READY kept coming. Five READYs in a row from one device fail visibly.
     */
    @Test fun readies_while_searching_mean_a_lost_ack_and_fail_visibly() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        repeat(4) { p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent() }
        p.socket.from("192.168.1.11", SyncWire.READY); advanceTimeBy(1_000); runCurrent()   // another device starts the count over
        p.socket.from(phone, SyncWire.READY); advanceTimeBy(1_000); runCurrent()            // our own address never counts
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        repeat(4) { p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent() }
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        p.socket.from(handheld, SyncWire.READY); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.MISSED_ACK), c.page.value)
        assertTrue(p.socket.closed)
        assertFalse(c.running)
        assertTrue(p.logs.any { "READY from $handheld" in it })
    }

    /** A phone server re-ACKs our next HELLO, so its READYs before that still pair. */
    @Test fun a_few_readies_then_an_ack_still_pair() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        repeat(3) { p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent() }
        p.socket.from(handheld, "TRIMUI_SYNC_ACK:$handheld:0\u0000v2"); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
    }

    @Test fun an_ack_also_pairs() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "TRIMUI_SYNC_ACK:$handheld:0\u0000v2"); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        assertFalse(p.socket.sent.any { it.first.startsWith(SyncWire.ACK) })   // no ACK for an ACK
    }

    /** The serving side's user may still be deciding: the wait for READY outlasts any confirm, up to an hour. */
    @Test fun no_ready_in_an_hour_fails() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from("192.168.1.99", SyncWire.READY)   // not the peer
        advanceTimeBy(30_500); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        advanceTimeBy(3_570_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.noReady(handheld)), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
    }

    @Test fun a_failed_step_stops_the_sync_without_done() = runTest {
        val p = FakeSyncPlatform()
        p.steps[SyncStep.SEND_SAVES] = emptyList<String>() to 23
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_SAVES, 23)), c.page.value)
        assertEquals(listOf(SyncStep.SEND_STATES, SyncStep.SEND_SAVES), p.stepsRun)
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
        c.press(NxButton.A); runCurrent()   // Retry
        assertEquals(DeviceSyncPage.Searching(SyncLink.WIFI, phone), c.page.value)
    }

    @Test fun progress_and_file_lines_show_while_a_step_runs() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        val pages = mutableListOf<DeviceSyncPage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.page.collect { pages += it } }
        p.steps[SyncStep.SEND_STATES] = listOf("GB-gambatte/T.st0", " 1 (xfr#1, to-chk=2/3)") to 0
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        val s = pages.filterIsInstance<DeviceSyncPage.Syncing>().last { it.step == SyncStep.SEND_STATES }
        assertEquals(1, s.done); assertEquals(3, s.total)
        assertTrue("GB-gambatte/T.st0" in s.lines)
    }

    /** Another phone (c), lower own IP: the phone serves, sends READY each second, finishes on DONE. */
    @Test fun server_role_serves_counts_the_log_and_finishes_on_done() = runTest {
        val p = FakeSyncPlatform().apply { wifi = "192.168.1.5" }
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:c"); runCurrent()
        assertEquals(handheld, p.serverStarted)
        val s = c.page.value as DeviceSyncPage.Syncing
        assertEquals(SyncRole.SERVER, s.role)
        advanceTimeBy(2_100); runCurrent()
        assertEquals(3, p.socket.sent.count { it == SyncWire.READY to handheld })
        p.logLine!!("2026/10/14 23:28:27 [1] shared recv GB-gambatte/T.st0")
        p.logLine!!("2026/10/14 23:28:27 [1] saves recv GB/A.srm")
        p.logLine!!("2026/10/14 23:28:27 [1] saves send GB/B.srm")
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertEquals(listOf("stop"), p.serverCalls)
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 2, sent = 1), c.page.value)
    }

    /** Old firmware (no v2, no c) is refused at pairing: no server, no rsync step, no role decision. */
    @Test fun an_old_firmware_peer_is_refused_without_rsync_or_a_server() = runTest {
        for (ownIp in listOf("192.168.1.5", "192.168.1.20")) {   // whichever side the old IP rule would have chosen
            val p = FakeSyncPlatform().apply { wifi = ownIp }
            val c = rig(p)
            c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
            p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0"); runCurrent()
            assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.OLD_FIRMWARE), c.page.value)
            assertTrue(p.stepsRun.isEmpty())
            assertNull(p.serverStarted)
            assertEquals(emptyList(), p.serverCalls)
            assertTrue(p.socket.closed)
            assertFalse(c.running)
        }
    }

    @Test fun as_client_a_peer_with_roms_on_is_fine() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:R\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(saveSteps, p.stepsRun)
    }

    /** No peer ever answers: the search ends on Failed (letting the screen sleep again) instead of running on. */
    @Test fun a_search_with_no_device_gives_up() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        advanceTimeBy(299_000); runCurrent()
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        advanceTimeBy(1_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_DEVICE), c.page.value)
        assertTrue(p.socket.closed)
        assertEquals(false to null, p.alive.last())
        assertFalse(c.running)
    }

    /** A peer that vanishes (Wi-Fi gone, no FAIL) after a step, no rsync connection open: the server gives up after minutes, not an hour. */
    @Test fun the_server_gives_up_on_a_silent_peer_with_no_connection_open() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/14 23:28:20 [7] connect from UNKNOWN ($handheld)")
        p.logLine!!("2026/10/14 23:28:21 [7] sent 120 bytes  received 4096 bytes  total size 4096")
        advanceTimeBy(299_000); runCurrent()
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_SILENT), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        assertEquals(false to null, p.alive.last())
    }

    /**
     * A client on its confirm page sends nothing (it drops READY until its A), and its page has no time limit: before
     * the first connection the server waits as long as a client waits for READY.
     */
    @Test fun the_server_waits_out_a_slow_confirm_on_the_client() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        assertEquals(emptyList(), p.serverCalls)
        p.logLine!!("2026/10/14 23:58:20 [7] connect from UNKNOWN ($handheld)")
        p.logLine!!("2026/10/14 23:58:21 [7] sent 120 bytes  received 4096 bytes  total size 4096")
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    /** A client that never connects (gone while confirming) still ends the server, after the hour. */
    @Test fun the_server_gives_up_after_an_hour_with_no_connection_ever() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        advanceTimeBy(3_599_000); runCurrent()
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        advanceTimeBy(1_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_NEVER_CONNECTED), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
    }

    /** A step's connection closed (its totals line): the short limit applies again from its last line. */
    @Test fun a_closed_connection_brings_the_short_limit_back() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/14 23:28:20 [7] connect from UNKNOWN ($handheld)")
        advanceTimeBy(1_000_000); runCurrent()
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        p.logLine!!("2026/10/14 23:45:00 [7] sent 120 bytes  received 4096 bytes  total size 4096")
        advanceTimeBy(300_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_SILENT), c.page.value)
    }

    /** A connection that ends in an error closes too. */
    @Test fun a_connection_ended_by_an_error_closes_too() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/14 23:28:20 [7] connect from UNKNOWN ($handheld)")
        p.logLine!!("2026/10/14 23:28:30 [7] rsync error: error in socket IO (code 10) at io.c(848) [receiver=3.4.1]")
        advanceTimeBy(300_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_SILENT), c.page.value)
    }

    /** With a connection open (one big file logs nothing until it is complete) the limit is the hour of silence. */
    @Test fun the_server_gives_up_after_an_hour_with_a_connection_open() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/14 23:28:20 [7] connect from UNKNOWN ($handheld)")
        advanceTimeBy(3_600_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.SERVER_TIMEOUT), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
    }

    /** The hour is of silence: daemon lines and the peer's messages each push it back, so a long transfer is never cut. */
    @Test fun the_server_wait_is_an_idle_timeout() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/14 23:28:20 [1] connect from UNKNOWN ($handheld)")
        advanceTimeBy(3_000_000); runCurrent()
        p.logLine!!("2026/10/14 23:28:27 [1] roms recv GB/Big.gb")
        advanceTimeBy(3_000_000); runCurrent()   // 100 min since the start, 50 since the line
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        p.socket.from(handheld, SyncWire.READY); runCurrent()   // a peer message counts too
        advanceTimeBy(3_000_000); runCurrent()
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        assertEquals(emptyList(), p.serverCalls)
        advanceTimeBy(700_000); runCurrent()   // then an hour of silence
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.SERVER_TIMEOUT), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
    }

    /** A confirmed stop while serving still stops the server. */
    @Test fun stopping_asks_first_and_a_stopped_server_is_still_stopped() = runTest {
        val p = FakeSyncPlatform().apply { wifi = "192.168.1.5" }
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:c"); runCurrent()
        c.press(NxButton.B)
        assertTrue((c.page.value as DeviceSyncPage.Syncing).confirmStop)
        c.press(NxButton.B)
        assertFalse((c.page.value as DeviceSyncPage.Syncing).confirmStop)
        c.press(NxButton.B, NxButton.A); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        assertTrue(p.socket.closed)
        assertFalse(c.running)
    }

    @Test fun the_notification_cancel_stops_like_a_confirmed_stop() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        c.cancel(); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
    }

    /** Re-opening the tool keeps a running sync's page. */
    @Test fun open_while_running_keeps_the_live_page() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        c.open()
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        assertTrue(c.running)
    }

    @Test fun b_while_searching_cancels_back_to_ready() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        assertTrue(c.onButton(NxButton.B)); runCurrent()
        assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
        assertTrue(p.socket.closed)
        assertFalse(c.running)
    }

    @Test fun a_lost_network_fails_and_releases_everything() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.failWith = IOException("ENETUNREACH"); advanceTimeBy(1_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NETWORK_LOST), c.page.value)
        assertEquals(false to null, p.alive.last())
        assertTrue(p.socket.closed)
        assertFalse(c.running)
    }

    @Test fun no_socket_fails() = runTest {
        val p = FakeSyncPlatform().apply { socketOk = false }
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SOCKET), c.page.value)
    }

    @Test fun a_build_without_rsync_says_so_before_pairing() = runTest {
        val p = FakeSyncPlatform().apply { rsync = false }
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.CABLE, DeviceSyncText.NO_RSYNC), c.page.value)
        assertEquals(0, p.cable.starts)   // stops before the link or a HELLO
        assertEquals(emptyList(), p.socket.sent)
    }

    @Test fun cable_waits_for_the_link_then_unicasts_to_the_handheld_and_stops_the_link_it_started() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.WaitingForCable, c.page.value)
        assertEquals(1, p.cable.starts)
        p.cable.state.value = UsbLinkState.Up("10.99.0.1", "10.99.0.2"); runCurrent()
        assertEquals(DeviceSyncPage.Searching(SyncLink.CABLE, "10.99.0.1"), c.page.value)
        assertEquals(listOf(SyncWire.hello("10.99.0.1") to "10.99.0.2"), p.socket.sent)
        p.socket.from("192.168.1.10", "HELLO_TRIMUI_SYNC:192.168.1.10:0\u0000v2")   // not the cable peer
        runCurrent()
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        p.socket.from("10.99.0.2", "HELLO_TRIMUI_SYNC:10.99.0.2:0\u0000v2"); runCurrent()
        val s = c.page.value as DeviceSyncPage.Syncing
        assertEquals(SyncRole.CLIENT, s.role)   // a new handheld: the phone (c) is the client though its IP is lower
        p.socket.from("10.99.0.2", SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(1, p.cable.stops)
    }

    @Test fun a_cable_already_up_is_used_and_left_up() = runTest {
        val p = FakeSyncPlatform()
        p.cable.state.value = UsbLinkState.Up("10.99.0.1", "10.99.0.2")
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        assertEquals(0, p.cable.starts)
        c.press(NxButton.B); runCurrent()
        assertEquals(0, p.cable.stops)
    }

    @Test fun a_cable_failure_shows_its_reason_and_b_on_waiting_stops_the_link() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        val failed = UsbLinkState.Failed(UsbFail.VERSION, handheld = true)
        p.cable.state.value = failed; runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.CABLE, NetplayText.usbFail(failed)), c.page.value)
        assertEquals(1, p.cable.stops)
        c.press(NxButton.B); c.press(NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.WaitingForCable, c.page.value)
        c.press(NxButton.B); runCurrent()
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE), c.page.value)
        assertEquals(2, p.cable.stops)
    }

    /**
     * A cable pulled mid-step would leave rsync waiting for minutes (the VPN swallows its packets, its keep-alives keep
     * `--timeout` from firing). A drop ends the step at once, tells the peer FAIL and stops the link, even one that was
     * up before the run.
     */
    @Test fun a_cable_drop_mid_step_fails_at_once_kills_the_step_and_stops_the_link() = runTest {
        val p = FakeSyncPlatform()
        p.cable.state.value = UsbLinkState.Up("10.99.0.1", "10.99.0.2")
        p.holdAt = SyncStep.SEND_SAVES
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        p.socket.from("10.99.0.2", "HELLO_TRIMUI_SYNC:10.99.0.2:0\u0000v2"); runCurrent()
        p.socket.from("10.99.0.2", SyncWire.READY); runCurrent()
        assertEquals(SyncStep.SEND_SAVES, (c.page.value as DeviceSyncPage.Syncing).step)
        p.cable.drops.value = 1; runCurrent()   // a drop the cable may still ride out: its state stays Up
        assertEquals(DeviceSyncPage.Failed(SyncLink.CABLE, DeviceSyncText.CABLE_DISCONNECTED), c.page.value)
        assertTrue(p.heldCancelled)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(5, p.socket.fails("10.99.0.2"))
        assertEquals(1, p.cable.stops)
        assertFalse(c.running)
        assertEquals(SyncStep.SEND_SAVES, p.stepsRun.last())   // nothing ran after it
    }

    /**
     * The handheld stops its cable as soon as the first DONE arrives, while the phone still repeats it: every step ran,
     * so the run is Done, not a disconnect, and no FAIL follows.
     */
    @Test fun a_cable_drop_after_done_was_sent_is_still_done() = runTest {
        val p = FakeSyncPlatform()
        p.cable.state.value = UsbLinkState.Up("10.99.0.1", "10.99.0.2")
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        p.socket.from("10.99.0.2", "HELLO_TRIMUI_SYNC:10.99.0.2:0\u0000v2"); runCurrent()
        p.socket.from("10.99.0.2", SyncWire.READY); runCurrent()
        assertEquals(1, p.socket.sent.count { it == SyncWire.DONE to "10.99.0.2" })   // the first DONE, the repeats to come
        p.cable.state.value = UsbLinkState.Lost; runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(0, p.socket.fails("10.99.0.2"))
        assertFalse(c.running)
    }

    @Test fun a_lost_cable_while_serving_fails_and_stops_the_daemon() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        p.cable.state.value = UsbLinkState.Up("10.99.0.1", "10.99.0.2"); runCurrent()
        p.socket.from("10.99.0.2", "HELLO_TRIMUI_SYNC:10.99.0.2:c"); runCurrent()
        assertEquals(SyncRole.SERVER, (c.page.value as DeviceSyncPage.Syncing).role)
        p.cable.state.value = UsbLinkState.Lost; runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.CABLE, DeviceSyncText.CABLE_DISCONNECTED), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(5, p.socket.fails("10.99.0.2"))
        assertEquals(1, p.cable.stops)
        assertFalse(c.running)
    }

    @Test fun a_drop_before_the_run_does_not_fail_it_and_a_stop_still_says_stopped() = runTest {
        val p = FakeSyncPlatform()
        p.cable.state.value = UsbLinkState.Up("10.99.0.1", "10.99.0.2")
        p.cable.drops.value = 2   // dropped and came back before this run
        p.holdAt = SyncStep.SEND_SAVES
        val c = rig(p)
        c.press(NxButton.A); runCurrent()
        p.socket.from("10.99.0.2", "HELLO_TRIMUI_SYNC:10.99.0.2:0\u0000v2"); runCurrent()
        p.socket.from("10.99.0.2", SyncWire.READY); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        c.press(NxButton.B, NxButton.A); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.CABLE, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(0, p.cable.stops)   // not started by the run, not dropped: left up
    }

    @Test fun done_offers_sync_again_and_back() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
        c.press(NxButton.A); runCurrent()
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        c.press(NxButton.B); runCurrent()
        assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
    }

    @Test fun lines_keep_the_newest_200() = runTest {
        val p = FakeSyncPlatform()
        p.steps[SyncStep.RECEIVE_SAVES] = (1..250).map { "GB/$it.srm" } to 0
        val c = rig(p)
        val pages = mutableListOf<DeviceSyncPage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.page.collect { pages += it } }
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        val last = pages.filterIsInstance<DeviceSyncPage.Syncing>().last()
        assertEquals(200, last.lines.size)
        assertEquals("GB/250.srm", last.lines.last())
    }

    /** Pairs over Wi-Fi with another phone (c) from a lower own IP: this phone serves. */
    private fun TestScope.serving(p: FakeSyncPlatform): DeviceSyncController {
        p.wifi = "192.168.1.5"
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:c"); runCurrent()
        return c
    }

    /** Pairs over Wi-Fi with a new handheld that serves; the phone holds at [hold] once its lines are out. */
    private fun TestScope.clientAt(p: FakeSyncPlatform, hold: SyncStep): DeviceSyncController {
        p.holdAt = hold
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); runCurrent()
        return c
    }

    private fun FakeSyncSocket.fails(ip: String) = sent.count { it == SyncWire.FAIL to ip }

    /** A failed client run tells the handheld FAIL (as DONE is sent: five times), so it stops waiting for DONE. */
    @Test fun a_failed_client_run_sends_fail_not_done() = runTest {
        val p = FakeSyncPlatform()
        p.steps[SyncStep.SEND_SAVES] = emptyList<String>() to 23
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_SAVES, 23)), c.page.value)
        assertEquals(5, p.socket.fails(handheld))
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
        assertTrue(p.socket.closed)
        assertFalse(c.running)
        assertTrue(p.logs.any { "sending FAIL to $handheld" in it })
    }

    @Test fun a_stopped_client_run_sends_fail() = runTest {
        val p = FakeSyncPlatform()
        val c = clientAt(p, SyncStep.SEND_SAVES)
        c.press(NxButton.B, NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertTrue(c.running)   // still telling the handheld
        advanceTimeBy(1_000); runCurrent()
        assertEquals(5, p.socket.fails(handheld))
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
        assertFalse(c.running)
    }

    @Test fun a_successful_client_run_sends_only_done() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(5, p.socket.sent.count { it == SyncWire.DONE to handheld })
        assertEquals(0, p.socket.fails(handheld))
    }

    /** No peer, no FAIL: B while searching, or an old firmware refused at pairing. */
    @Test fun no_fail_without_a_paired_peer() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        c.press(NxButton.B); advanceTimeBy(1_000); runCurrent()
        assertFalse(p.socket.sent.any { it.first == SyncWire.FAIL })
        val q = FakeSyncPlatform()
        val d = rig(q)
        d.pick(SyncLink.WIFI); d.press(NxButton.A); runCurrent()
        q.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0"); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.OLD_FIRMWARE), d.page.value)
        assertFalse(q.socket.sent.any { it.first == SyncWire.FAIL })
    }

    /** A FAIL before any connection: the client's user pressed B on its confirm page (or its run could not start). */
    @Test fun the_peers_fail_before_any_connection_reads_as_a_cancel() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.socket.from(handheld, SyncWire.FAIL); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_ENDED_EARLY), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        assertEquals(0, p.socket.fails(handheld))
    }

    @Test fun the_server_ends_on_the_peers_fail_without_echoing_it() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/14 23:28:20 [7] connect from UNKNOWN ($handheld)")
        p.socket.from("192.168.1.99", SyncWire.FAIL); runCurrent()   // not the peer
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        p.socket.from(handheld, SyncWire.FAIL); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_FAILED), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        assertEquals(0, p.socket.fails(handheld))
        assertFalse(c.running)
        assertTrue(p.logs.any { "FAIL from $handheld" in it })
    }

    @Test fun a_stopped_server_sends_fail() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        c.press(NxButton.B, NxButton.A); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        assertEquals(5, p.socket.fails(handheld))
        assertFalse(c.running)
    }

    @Test fun a_timed_out_server_sends_fail() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        advanceTimeBy(3_601_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_NEVER_CONNECTED), c.page.value)
        assertEquals(5, p.socket.fails(handheld))
    }

    @Test fun the_server_stops_the_daemon_first_and_counts_its_last_lines() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.onStopServer = {
            p.logLine!!("2026/10/14 23:28:27 [1] saves recv GB/C.srm")
        }
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertEquals(listOf("stop"), p.serverCalls)
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 0), c.page.value)
    }

    @Test fun a_server_that_does_not_start_fails_and_still_tears_down() = runTest {
        val p = FakeSyncPlatform().apply { serverStarts = false }
        val c = serving(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SERVER), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
    }

    @Test fun a_lost_network_while_serving_still_tears_the_server_down() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.socket.failWith = IOException("ENETUNREACH"); advanceTimeBy(1_500); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NETWORK_LOST), c.page.value)
        assertEquals(listOf("stop"), p.serverCalls)
        assertTrue(p.socket.closed)
        assertEquals(false to null, p.alive.last())
    }

    @Test fun running_stays_true_until_the_stopped_server_has_stopped() = runTest {
        val p = FakeSyncPlatform().apply { stopGate = CompletableDeferred() }
        val c = serving(p)
        c.press(NxButton.B, NxButton.A); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertTrue(c.running)
        assertEquals(0, p.stopped)
        p.stopGate!!.complete(Unit); runCurrent()
        assertTrue(c.running)   // then FAIL to the peer
        advanceTimeBy(1_000); runCurrent()
        assertFalse(c.running)
    }

    @Test fun a_while_a_stopped_server_stops_says_to_wait() = runTest {
        val p = FakeSyncPlatform().apply { stopGate = CompletableDeferred() }
        val c = serving(p)
        val notices = mutableListOf<String>()
        backgroundScope.launch { c.notices.collect { notices += it } }
        runCurrent()
        c.press(NxButton.B, NxButton.A); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals(listOf(DeviceSyncText.FINISHING), notices)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(0, p.stopped)
        p.stopGate!!.complete(Unit); advanceTimeBy(1_000); runCurrent()
        c.press(NxButton.A); runCurrent()   // finished: Retry starts again, with no notice
        assertIs<DeviceSyncPage.Searching>(c.page.value)
        assertEquals(listOf(DeviceSyncText.FINISHING), notices)
    }

    @Test fun a_step_that_cannot_run_says_so_not_network_lost() = runTest {
        val p = FakeSyncPlatform()
        p.stepThrows[SyncStep.SEND_SAVES] = IOException("error=2, No such file or directory")
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepError(SyncStep.SEND_SAVES)), c.page.value)
        assertEquals(listOf(SyncStep.SEND_STATES, SyncStep.SEND_SAVES), p.stepsRun)
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
        assertEquals(false to null, p.alive.last())
        assertFalse(c.running)
    }

    @Test fun progress_updates_the_notification_at_most_every_500_ms() = runTest {
        val p = FakeSyncPlatform()
        val c = clientAt(p, SyncStep.SEND_STATES)
        val stepText = DeviceSyncText.step(SyncStep.SEND_STATES)
        fun updates() = p.alive.count { (on, text) -> on && text != null && stepText in text }
        repeat(50) { i ->
            p.stepLine!!(" ${i + 1} (xfr#${i + 1}, to-chk=${49 - i}/50)")
            advanceTimeBy(2)
        }
        assertTrue(updates() <= 2, "${updates()} notification updates for 50 lines in 100 ms")
        assertEquals(50, (c.page.value as DeviceSyncPage.Syncing).done)   // the page itself follows every line
        val before = updates()
        advanceTimeBy(500)
        p.stepLine!!(" 50 (xfr#50, to-chk=0/50)")
        assertEquals(before + 1, updates())   // once 500 ms passed, the next line updates it
        p.holdResult.complete(0); advanceTimeBy(5_000); runCurrent()
        // A step change always updates it, and the run ends with keepAlive(false).
        assertTrue(p.alive.any { (on, text) -> on && text != null && DeviceSyncText.step(SyncStep.SEND_SAVES) in text })
        assertEquals(false to null, p.alive.last())
    }

    @Test fun a_stopped_server_that_received_files_still_reports_them() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        val got = mutableListOf<Int>()
        backgroundScope.launch { c.received.collect { got += it } }
        runCurrent()
        p.logLine!!("2026/10/14 23:28:27 [1] shared recv GB-gambatte/T.st0")
        c.press(NxButton.B, NxButton.A); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(listOf(1), got)
    }

    @Test fun a_client_failing_at_step_4_still_reports_what_step_3_received() = runTest {
        val p = FakeSyncPlatform()
        p.steps[SyncStep.RECEIVE_STATES] = listOf("GB-gambatte/T.st0", " 3 (xfr#3, to-chk=0/3)") to 0
        p.steps[SyncStep.RECEIVE_SAVES] = emptyList<String>() to 12
        val c = rig(p)
        val got = mutableListOf<Int>()
        backgroundScope.launch { c.received.collect { got += it } }
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.RECEIVE_SAVES, 12)), c.page.value)
        assertEquals(listOf(3), got)
    }

    /** Our ACK got lost on Wi-Fi: the handheld keeps saying HELLO, and the phone answers again. */
    @Test fun a_hello_from_the_peer_while_waiting_for_ready_is_acked_again() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        assertEquals(2, p.socket.sent.count { it == SyncWire.ack(phone) to handheld })
    }

    @Test fun a_hello_from_the_peer_while_serving_is_acked_again() = runTest {
        val p = FakeSyncPlatform()
        serving(p)
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:c"); runCurrent()
        assertEquals(2, p.socket.sent.count { it == SyncWire.ack("192.168.1.5") to handheld })
    }

    @Test fun a_progress_line_after_stop_does_not_bring_syncing_back() = runTest {
        val p = FakeSyncPlatform()
        val c = clientAt(p, SyncStep.SEND_STATES)
        assertEquals(SyncStep.SEND_STATES, (c.page.value as DeviceSyncPage.Syncing).step)
        c.press(NxButton.B, NxButton.A)
        p.stepLine!!(" 1 (xfr#1, to-chk=1/3)")
        p.stepLine!!("GB-gambatte/T.st0")
        advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertFalse(c.running)
    }

    @Test fun the_stop_question_survives_a_progress_update() = runTest {
        val p = FakeSyncPlatform()
        val c = clientAt(p, SyncStep.SEND_STATES)
        c.press(NxButton.B)
        p.stepLine!!(" 1 (xfr#1, to-chk=1/3)")
        p.stepLine!!("GB-gambatte/T.st0")
        val s = c.page.value as DeviceSyncPage.Syncing
        assertTrue(s.confirmStop)
        assertEquals(2, s.done)
        assertEquals("GB-gambatte/T.st0", s.lines.last())
        c.press(NxButton.B)
        assertFalse((c.page.value as DeviceSyncPage.Syncing).confirmStop)
        p.holdResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    @Test fun x_toggles_roms_only_when_available_and_resets_on_open() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        val notices = mutableListOf<String>()
        backgroundScope.launch { c.notices.collect { notices += it } }
        runCurrent()
        c.press(NxButton.X); runCurrent()
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE, roms = false, romsAvailable = false), c.page.value)
        assertEquals(emptyList(), notices)   // no ROMs to offer: no toggle, no notice
        p.roms = true; c.open()
        assertEquals(DeviceSyncPage.Ready(SyncLink.CABLE, roms = false, romsAvailable = true), c.page.value)
        c.press(NxButton.X); runCurrent(); assertEquals(true, (c.page.value as DeviceSyncPage.Ready).roms)
        c.press(NxButton.DOWN); assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI, roms = true, romsAvailable = true), c.page.value)
        c.press(NxButton.X); runCurrent()
        assertEquals(listOf(DeviceSyncText.ROMS_ON_NOTICE, DeviceSyncText.ROMS_OFF_NOTICE), notices)   // touch: the icon says what it did
        c.press(NxButton.X)
        c.open(); assertEquals(false, (c.page.value as DeviceSyncPage.Ready).roms)
    }

    @Test fun hello_carries_the_roms_toggle() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.X, NxButton.A); runCurrent()
        assertTrue(p.socket.broadcasts.first().endsWith(":C"))
    }

    private val gb = "Game Boy (GB)"
    private val gbHashes = "$gb/.retroachievements/hashes.json"
    /** A peer's `--list-only -R` of its hashes.json files: exit 23 when other folders have none. */
    private fun hashListing(vararg folders: String) = 23 to (listOf("rsync: [sender] link_stat \"Zed (ZD)/.retroachievements/hashes.json\" (in roms) failed: No such file or directory (2)") +
        folders.map { "-rw-r--r--              2 2026/10/15 19:10:16 $it/.retroachievements/hashes.json" } + "rsync error: some files/attrs were not transferred (see previous errors) (code 23) at main.c(1956) [generator=3.5.0]")
    private fun hashes(vararg keys: String) = """{"version":1,"files":{${keys.joinToString(",") {
        "\"$it\":{\"size\":1,\"mtime\":5,\"console\":4,\"hash\":\"0123456789abcdef0123456789abcdef\",\"game\":7}"
    }}}}"""

    /**
     * Pairs over Wi-Fi with [peer] saying HELLO with [flag] (+ [trailer]), the X toggle pressed first when [toggle], then
     * READY lets the run go. [onRig] runs before Start (collectors).
     */
    private fun TestScope.romClient(
        p: FakeSyncPlatform, toggle: Boolean = true, flag: String = "0", trailer: String = "\u0000v2",
        onRig: (DeviceSyncController) -> Unit = {},
    ): DeviceSyncController {
        val c = rig(p)
        onRig(c)
        c.pick(SyncLink.WIFI)
        if (toggle) c.press(NxButton.X)
        c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:$flag$trailer"); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        return c
    }

    @Test fun roms_on_runs_nine_steps_with_the_console_and_notes_one_side_consoles() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; romHoldAt = 0; romResults[0] = listOf(" 1 (xfr#1, to-chk=1/2)") to 0 }
        val c = romClient(p)
        val s = c.page.value as DeviceSyncPage.Syncing
        assertEquals(SyncStep.SEND_ROMS, s.step)
        assertEquals(9, s.steps)
        assertEquals(gb, s.console)
        assertEquals(1, s.done); assertEquals(2, s.total)
        assertTrue(p.alive.any { (on, t) -> on && t != null && "8/9 Pushing ROMs: $gb" in t })
        p.romHoldResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps, p.stepsRun)
        assertEquals(listOf(SyncStep.SEND_ROMS, SyncStep.SEND_ROMS, SyncStep.RECEIVE_ROMS, SyncStep.RECEIVE_ROMS), p.romRuns.map { it.step })
        assertTrue(p.romRuns.all { it.console == gb })
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 1, notes = listOf("Only on one device, not synced: 3DS.")), c.page.value)
    }

    @Test fun the_handhelds_roms_flag_turns_roms_on() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        val c = romClient(p, toggle = false, flag = "R")
        assertEquals(listOf(SyncStep.SEND_ROMS, SyncStep.SEND_ROMS, SyncStep.RECEIVE_ROMS, SyncStep.RECEIVE_ROMS), p.romRuns.map { it.step })
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    @Test fun roms_off_on_both_sides_runs_no_rom_step() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        val c = romClient(p, toggle = false)
        assertTrue(p.romRuns.isEmpty())
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0), c.page.value)
    }

    @Test fun roms_requested_without_access_skip_and_say_so() = runTest {
        val p = FakeSyncPlatform().apply { roms = false }
        val c = romClient(p, toggle = false, flag = "R")
        assertTrue(p.romRuns.isEmpty())
        assertEquals(saveSteps, p.stepsRun)
        assertEquals(listOf(DeviceSyncText.BIOS_NO_ACCESS, DeviceSyncText.COLLECTIONS_NO_ACCESS, DeviceSyncText.ROMS_SKIPPED), (c.page.value as DeviceSyncPage.Done).notes)
    }

    @Test fun file_times_failing_stops_before_step_one() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; fileTimesOk = false }
        val c = romClient(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_FILE_TIMES), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
        assertTrue(p.romRuns.isEmpty())
    }

    // Saves sync by file times too: a Saves/ that can't keep them (shared storage) stops the run before step 1.
    @Test fun save_file_times_failing_stops_before_step_one() = runTest {
        val p = FakeSyncPlatform().apply { roms = false; saveTimesOk = false }
        val c = romClient(p, toggle = false)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SAVE_FILE_TIMES), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
    }

    @Test fun save_file_times_failing_serves_nothing() = runTest {
        val p = FakeSyncPlatform().apply { saveTimesOk = false }
        val c = serving(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SAVE_FILE_TIMES), c.page.value)
        assertNull(p.serverStarted)
    }

    /** A phone at the lower IP serves (both are client-only), so this phone at .20 is the client. */
    @Test fun a_phone_peer_without_roms_module_is_named() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; remoteListing = 5 to emptyList() }
        val c = romClient(p, toggle = false, flag = "C", trailer = "")
        assertNull(p.serverStarted)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_NO_ROMS), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
    }

    @Test fun a_listing_failure_fails_as_step_5() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; remoteListing = 10 to emptyList() }
        val c = romClient(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, 10)), c.page.value)
    }

    @Test fun a_phone_peer_without_an_extras_list_is_named() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        val c = romClient(p, toggle = false, flag = "C", trailer = "")
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_NO_ROMS), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
    }

    /** The peer phone's extras are planned, and their fingerprints land re-keyed in this phone's file. */
    @Test fun a_phone_peers_extras_are_pulled_and_their_hashes_rekeyed() = runTest {
        val unit = ExtraUnit(2, "GB", "Games", listOf("p.gb"), "p.gb", ".sources/sd/Games/p.gb", "p.gb", emptyList())
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteTexts["nx-extras/extras.json"] = RemoteText.Found(SyncExtras.encode(ExtrasManifest(units = listOf(unit))))
            remoteTexts["roms/$gbHashes"] = RemoteText.Found(hashes(".sources/sd/Games/p.gb"))
            remoteHashes = hashListing(gb)
        }
        val c = romClient(p, toggle = false, flag = "C", trailer = "")
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertTrue(RomRun(SyncStep.RECEIVE_ROMS, gb, RomEnd.Peer("extra-2", "Games"), RomEnd.Home(gb), only = listOf("p.gb"), pair = ConsolePair(gb, gb, "GB").key) in p.romRuns)
        assertTrue("\"p.gb\"" in p.homeTexts[gbHashes]!!)
        assertFalse(".sources" in p.homeTexts[gbHashes]!!)
    }

    @Test fun no_space_maps_to_this_phone_or_the_other_device() = runTest {
        val full = listOf("rsync: write failed on \"x\": No space left on device (28)") to 11
        val pull = FakeSyncPlatform().apply { roms = true; romResults[2] = full }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SPACE_HERE), romClient(pull).page.value)
        val push = FakeSyncPlatform().apply { roms = true; romResults[0] = full }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SPACE_PEER), romClient(push).page.value)
        assertEquals(1, push.romRuns.size)
        val other = FakeSyncPlatform().apply { roms = true; romResults[2] = listOf("rsync error") to 23 }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.RECEIVE_ROMS, 23)), romClient(other).page.value)
    }

    /** A failed run shows rsync's own last error under the reason (the receiver's mkdir refusal, say). */
    @Test fun a_failed_run_shows_rsyncs_last_error() = runTest {
        val mkdir = "rsync: [Receiver] mkdir \"Bad:Name (GB)\" (in roms) failed: Invalid argument (22)"
        val p = FakeSyncPlatform().apply { roms = true; romResults[0] = listOf(mkdir, "rsync error: error in file IO (code 11) at main.c(791) [Receiver=3.4.1]") to 11 }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, 11), detail = mkdir.removePrefix("rsync: ")),
            romClient(p).page.value)
    }

    @Test fun a_failed_save_step_shows_rsyncs_last_error_too() = runTest {
        val p = FakeSyncPlatform()
        p.steps[SyncStep.SEND_SAVES] = listOf("rsync: connection unexpectedly closed (0 bytes received so far) [sender]") to 12
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); advanceTimeBy(300); runCurrent()
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_SAVES, 12),
            detail = "connection unexpectedly closed (0 bytes received so far) [sender]"), c.page.value)
    }

    @Test fun an_unreadable_home_hashes_file_skips_that_merge_and_the_sync_goes_on() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteTexts["roms/$gbHashes"] = RemoteText.Found(hashes("y.gb")); remoteHashes = hashListing(gb)
            homeTexts[gbHashes] = hashes("x.gb")
            unreadableHome += gbHashes
        }
        val c = romClient(p)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(hashes("x.gb"), p.homeTexts[gbHashes])
        assertTrue(p.pushedTexts.isEmpty())
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    @Test fun hashes_are_merged_before_the_first_push() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true; romHoldAt = 0
            remoteTexts["roms/$gbHashes"] = RemoteText.Found(hashes("y.gb")); remoteHashes = hashListing(gb)
            homeTexts[gbHashes] = hashes("x.gb")
            // A handheld's copy keeps the games its folder has and those the push sends there.
            remoteFolders[gb] = 0 to listOf(dir("."), file("y.gb"))
            home = mapOf(null to listOf(gb), gb to listOf("x.gb"))
        }
        val c = romClient(p)
        assertTrue("\"y.gb\"" in p.homeTexts[gbHashes]!!)
        assertTrue("\"x.gb\"" in p.homeTexts[gbHashes]!!)
        assertEquals(1, p.pushedTexts.size)
        val (module, path, text) = p.pushedTexts.single()
        assertEquals("roms", module)
        assertEquals(gbHashes, path)
        assertTrue("\"x.gb\"" in text)
        p.romHoldResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(1, p.pushedTexts.size)
    }

    @Test fun hashes_are_fetched_only_when_the_peer_folder_has_retroachievements() = runTest {
        val with = FakeSyncPlatform().apply { roms = true; remoteHashes = hashListing(gb) }
        romClient(with)
        assertTrue("roms/$gbHashes" in with.fetched)
        val without = FakeSyncPlatform().apply { roms = true; remoteHashes = hashListing("Other (OT)") }
        assertIs<DeviceSyncPage.Done>(romClient(without).page.value)
        assertFalse("roms/$gbHashes" in without.fetched)
    }

    @Test fun no_hashes_anywhere_is_exit_23_with_only_missing_files_and_is_not_a_failure() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; remoteHashes = hashListing() }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        assertTrue(p.fetched.none { "hashes.json" in it })
    }

    @Test fun a_hashes_listing_failure_fails_step_5() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; remoteHashes = 10 to listOf("rsync: failed to connect to 1.2.3.4: Connection refused (111)") }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, 10), detail = "failed to connect to 1.2.3.4: Connection refused (111)"), romClient(p).page.value)
    }

    @Test fun roms_off_lists_no_hashes() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        romClient(p, toggle = false)
        assertEquals(0, p.hashListings)
    }

    @Test fun missing_peer_hashes_is_not_a_failure() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        val c = romClient(p)
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertTrue(p.pushedTexts.isEmpty())
    }

    @Test fun a_failed_hashes_fetch_fails_step_5() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; remoteTexts["roms/$gbHashes"] = RemoteText.Failed(10); remoteHashes = hashListing(gb) }
        val c = romClient(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, 10)), c.page.value)
        assertTrue(p.romRuns.isEmpty())
    }

    @Test fun roms_arrived_fires_only_after_receiving_rom_files() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; romResults[2] = listOf("a.gb", " 1 (xfr#1, to-chk=0/1)") to 0 }
        var arrived = 0
        val c = romClient(p) { c -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.romsArrived.collect { arrived++ } } }
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 0, notes = listOf("Only on one device, not synced: 3DS.")), c.page.value)
        assertEquals(1, arrived)
        val q = FakeSyncPlatform().apply { roms = true; romResults[0] = listOf("b.gb", " 1 (xfr#1, to-chk=0/1)") to 0 }
        var none = 0
        romClient(q) { c -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.romsArrived.collect { none++ } } }
        assertEquals(0, none)
    }

    @Test fun stop_mid_roms_keeps_copied_files_and_page() = runTest {
        val p = FakeSyncPlatform().apply { roms = true; romHoldAt = 2; romResults[2] = listOf("a.gb", " 1 (xfr#1, to-chk=1/2)") to 0 }
        var arrived = 0
        val got = mutableListOf<Int>()
        val c = romClient(p) { c ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.romsArrived.collect { arrived++ } }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.received.collect { got += it } }
        }
        assertEquals(SyncStep.RECEIVE_ROMS, (c.page.value as DeviceSyncPage.Syncing).step)
        c.press(NxButton.B, NxButton.A); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(1, arrived)
        assertEquals(listOf(1), got)
        assertFalse(c.running)
    }

    @Test fun server_serves_rom_modules_when_roms_are_on() = runTest {
        val p = FakeSyncPlatform().apply { wifi = "192.168.1.5" }
        val c = rig(p)
        var arrived = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.romsArrived.collect { arrived++ } }
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:C"); runCurrent()
        assertEquals(true, p.serverRoms)
        p.logLine!!("2026/10/15 12:00:00 [1] roms send $gb/b.gb")
        p.logLine!!("2026/10/15 12:00:00 [1] roms recv $gb/a.gb")
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 1), c.page.value)
        assertEquals(1, arrived)
        val q = FakeSyncPlatform()
        serving(q)
        assertEquals(false, q.serverRoms)
    }

    private val mgba = "Game Boy Advance (MGBA)"
    private val gbaRemote = "Game Boy Advance (GBA)"
    private fun dir(name: String) = "drwxr-xr-x 4,096 2026/10/15 12:00:00 $name"
    private fun file(name: String) = "-rw-r--r-- 1,024 2026/10/15 12:00:00 $name"

    /** The peer keeps a game in a sibling folder and so does this phone: neither side gets a second copy. */
    @Test fun a_pair_with_siblings_lists_them_and_skips_their_names() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(mgba), dir(gbaRemote))
            remoteFolders[mgba] = 0 to listOf(dir("."))
            remoteFolders[gbaRemote] = 0 to listOf(dir("."), file("emerald.gba"))
            home = mapOf(null to listOf(mgba, "Game Boy Advance (GBA2)"), mgba to listOf("Emerald.gba", "New.gba"),
                "Game Boy Advance (GBA2)" to listOf("Sapphire.gba"))
            // Both run under mGBA, so the handheld's (MGBA) folder is their push target.
            gameTags[mgba] = mapOf("Emerald.gba" to "MGBA", "New.gba" to "MGBA")
        }
        val c = romClient(p)
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(listOf(mgba, gbaRemote), p.folderListings)
        val push = p.romRuns.first { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(mgba) && it.to == RomEnd.Peer("roms", mgba) }
        assertTrue("/Emerald.gba" in push.excludes)
        assertFalse("/New.gba" in push.excludes)
        val pull = p.romRuns.first { it.step == SyncStep.RECEIVE_ROMS && it.to == RomEnd.Home(mgba) }
        assertTrue("/Sapphire.gba" in pull.excludes)
    }

    /** A game in both handheld GBA folders is pulled by one pair only, named as that folder's listing spells it. */
    @Test fun a_game_in_both_handheld_gba_folders_is_pulled_once() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gbaRemote), dir(mgba))
            remoteFolders[gbaRemote] = 0 to listOf(dir("."), file("Ruby.gba"), dir(".retroachievements"))
            remoteFolders[mgba] = 0 to listOf(dir("."), file("RUBY.gba"), file("Sapphire.gba"), dir(".retroachievements"))
            home = mapOf(null to listOf(gbaRemote), gbaRemote to listOf("Ruby.gba"))
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        fun pull(remote: String) = p.romRuns.single { it.step == SyncStep.RECEIVE_ROMS && it.from == RomEnd.Peer("roms", remote) }
        assertFalse(pull(gbaRemote).excludes.any { "uby" in it })
        assertTrue("/RUBY.gba" in pull(mgba).excludes && "/Sapphire.gba" !in pull(mgba).excludes)
        assertEquals(2, p.romRuns.count { it.to == RomEnd.Home("$gbaRemote/.retroachievements") && it.ignoreExisting })
        assertTrue(p.logs.any { it.startsWith("device sync: listing of $mgba: exit 0") })
    }

    /** A game's info file (`.media/info/<name>.json`) goes with its game, as its art does. */
    @Test fun device_sync_lists_info_files_with_the_art() = runTest {
        val media = "$gbaRemote/.media"
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gbaRemote), dir(mgba))
            remoteHashes = hashListing(gbaRemote, mgba)
            home = mapOf(null to listOf(gbaRemote), gbaRemote to listOf("A.gba", "B.gba"), media to listOf("boxart", "info", "grid"),
                "$media/boxart" to listOf("A.png", "B.png"), "$media/info" to listOf("A.json", "B.json"), "$media/grid" to listOf("B.png"))
            gameTags[gbaRemote] = mapOf("B.gba" to "MGBA")
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        fun artPush(remote: String) = p.romRuns.single { it.step == SyncStep.SEND_ROMS && it.to == RomEnd.Peer("roms", "$remote/.media") }
        assertEquals(listOf("boxart/A.png", "info/A.json"), artPush(gbaRemote).filesFrom)
        assertEquals(listOf("boxart/B.png", "info/B.json"), artPush(mgba).filesFrom)
    }

    /** Both handheld GBA folders pull into the phone's one GBA folder; each pair merges its own hashes.json, once. */
    @Test fun a_handheld_with_two_gba_folders_gets_two_pairs_with_distinct_keys() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gbaRemote), dir(mgba))
            remoteHashes = hashListing(gbaRemote, mgba)
            home = mapOf(null to listOf(gbaRemote), gbaRemote to listOf("A.gba", "B.gba"))
            gameTags[gbaRemote] = mapOf("B.gba" to "MGBA")
        }
        val c = romClient(p)
        assertIs<DeviceSyncPage.Done>(c.page.value)
        val pushes = p.romRuns.filter { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(gbaRemote) }
        assertEquals(listOf(RomEnd.Peer("roms", gbaRemote), RomEnd.Peer("roms", mgba)), pushes.map { it.to })
        assertEquals(2, pushes.map { it.pair }.toSet().size)
        assertTrue("/B.gba" in pushes[0].excludes && "/A.gba" !in pushes[0].excludes)
        assertTrue("/A.gba" in pushes[1].excludes && "/B.gba" !in pushes[1].excludes)
        assertTrue(p.romRuns.all { it.console == gbaRemote })
        val hashFetches = p.fetched.filter { it.endsWith("hashes.json") }
        assertEquals(listOf("roms/$gbaRemote/.retroachievements/hashes.json", "roms/$mgba/.retroachievements/hashes.json"), hashFetches)
    }

    /** The phone's one hashes.json takes every handheld folder's entries; each folder gets back only its own games'. */
    @Test fun each_handheld_folder_gets_only_its_hashes() = runTest {
        val gbaHashes = "$gbaRemote/.retroachievements/hashes.json"
        val mgbaHashes = "$mgba/.retroachievements/hashes.json"
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gbaRemote), dir(mgba))
            remoteHashes = hashListing(gbaRemote, mgba)
            remoteFolders[gbaRemote] = 0 to listOf(dir("."), file("Only GBA.gba"))
            remoteFolders[mgba] = 0 to listOf(dir("."), file("Only MGBA.gba"))
            remoteTexts["roms/$gbaHashes"] = RemoteText.Found(hashes("Only GBA.gba"))
            remoteTexts["roms/$mgbaHashes"] = RemoteText.Found(hashes("Only MGBA.gba"))
            home = mapOf(null to listOf(gbaRemote), gbaRemote to listOf("Mine.gba", "Mine M.gba"))
            gameTags[gbaRemote] = mapOf("Mine M.gba" to "MGBA")
            homeTexts[gbaHashes] = hashes("Mine.gba", "Mine M.gba")
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        for (k in listOf("Only GBA.gba", "Only MGBA.gba", "Mine.gba", "Mine M.gba")) assertTrue("\"$k\"" in p.homeTexts[gbaHashes]!!, k)
        val pushed = p.pushedTexts.associate { (_, path, text) -> path to text }
        val toGba = pushed.getValue(gbaHashes)
        assertTrue("\"Only GBA.gba\"" in toGba && "\"Mine.gba\"" in toGba)
        assertFalse("\"Only MGBA.gba\"" in toGba || "\"Mine M.gba\"" in toGba)
        val toMgba = pushed.getValue(mgbaHashes)
        assertTrue("\"Only MGBA.gba\"" in toMgba && "\"Mine M.gba\"" in toMgba)
        assertFalse("\"Only GBA.gba\"" in toMgba || "\"Mine.gba\"" in toMgba)
    }

    /** The handheld folder each push goes to (GBA, then MGBA) → its excludes. */
    private fun FakeSyncPlatform.gbaPushes(from: String) = romRuns.filter { it.step == SyncStep.SEND_ROMS && it.from == RomEnd.Home(from) }
        .associate { (it.to as RomEnd.Peer).path to it.excludes }

    /** A `systems.txt` tag of another console is ignored: the game still goes to the Default emulators folder. */
    @Test fun an_out_of_console_sidecar_tag_still_pushes_to_the_default_pair() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gbaRemote), dir(mgba))
            home = mapOf(null to listOf(gbaRemote), gbaRemote to listOf("A.gba", "B.gba"))
            gameTags[gbaRemote] = mapOf("B.gba" to "PS")
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        val pushes = p.gbaPushes(gbaRemote)
        assertFalse("/B.gba" in pushes.getValue(gbaRemote))
        assertTrue("/B.gba" in pushes.getValue(mgba) && "/A.gba" in pushes.getValue(mgba))
    }

    /**
     * A phone whose only Game Boy Advance folder is tagged `(MGBA)`: the tag names only the console, so its untagged games
     * run under the Default emulators pick (gpSP) and go to the handheld's (GBA).
     */
    @Test fun an_emulator_tagged_landing_pushes_its_untagged_games_by_the_default_emulator() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gbaRemote), dir(mgba))
            home = mapOf(null to listOf(mgba), mgba to listOf("A.gba"))
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        val pushes = p.gbaPushes(mgba)
        assertFalse("/A.gba" in pushes.getValue(gbaRemote))
        assertTrue("/A.gba" in pushes.getValue(mgba))
    }

    /**
     * Games pulled from the handheld's (GPGX) Master System folder take GPGX_SMS as their Emulator pick, so its states
     * resume; the (SMS) folder's run on the default; an entry the user set is kept.
     */
    @Test fun games_pulled_from_a_gpgx_folder_get_its_emulator_as_their_pick() = runTest {
        val sms = "Sega Master System (SMS)"; val gpgx = "Sega Master System (GPGX)"
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(sms), dir(gpgx))
            remoteFolders[sms] = 0 to listOf(dir("."), file("Pico.sms"))
            remoteFolders[gpgx] = 0 to listOf(dir("."), file("Alex Kidd.sms"), file("Mine.sms"))
            home = mapOf(null to listOf(sms), sms to listOf("Mine.sms"))
            gameTags[sms] = mapOf("Mine.sms" to "SMS")
            homeTexts["$sms/systems.txt"] = "Mine.sms\tSMS\n"
            onRomRun = { run ->
                if (run.step == SyncStep.RECEIVE_ROMS && run.from == RomEnd.Peer("roms", sms)) home = home + (sms to home.getValue(sms) + "Pico.sms")
                if (run.step == SyncStep.RECEIVE_ROMS && run.from == RomEnd.Peer("roms", gpgx)) home = home + (sms to home.getValue(sms) + "Alex Kidd.sms")
            }
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        assertEquals("Alex Kidd.sms\tGPGX_SMS\nMine.sms\tSMS\n", p.homeTexts["$sms/systems.txt"])
    }

    /**
     * A disc folder pulled from the handheld's (GPGX) Sega CD folder takes GPGX_SEGACD on its sheet in its own systems.txt
     * (where the Emulator menu keeps it), a single file beside it in the console folder's; one with a pick of its own keeps
     * it, and the (SEGACD) folder's discs (the default) get nothing.
     */
    @Test fun a_disc_folder_pulled_from_a_gpgx_folder_gets_its_emulator_on_its_sheet() = runTest {
        val cd = "Sega CD (SEGACD)"; val gpgx = "Sega CD (GPGX)"
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(cd), dir(gpgx))
            remoteFolders[cd] = 0 to listOf(dir("."), dir("Lunar"))
            remoteFolders[gpgx] = 0 to listOf(dir("."), dir("Snatcher"), dir("Popful Mail"), file("Sonic CD.chd"))
            home = mapOf(null to listOf(cd), cd to emptyList())
            onRomRun = { run ->
                if (run.step == SyncStep.RECEIVE_ROMS && run.from == RomEnd.Peer("roms", cd)) {
                    home = home + (cd to home.getValue(cd) + "Lunar") + ("$cd/Lunar" to listOf("Lunar.cue", "Lunar.bin"))
                }
                if (run.step == SyncStep.RECEIVE_ROMS && run.from == RomEnd.Peer("roms", gpgx)) {
                    home = home + (cd to home.getValue(cd) + listOf("Snatcher", "Popful Mail", "Sonic CD.chd")) +
                        ("$cd/Snatcher" to listOf("Snatcher.m3u", "Snatcher (Disc 1).cue")) + ("$cd/Popful Mail" to listOf("Popful Mail.cue"))
                    gameTags["$cd/Popful Mail"] = mapOf("Popful Mail.cue" to "SEGACD")
                    homeTexts["$cd/Popful Mail/systems.txt"] = "Popful Mail.cue\tSEGACD\n"
                }
            }
        }
        assertIs<DeviceSyncPage.Done>(romClient(p).page.value)
        assertEquals("Snatcher.m3u\tGPGX_SEGACD\n", p.homeTexts["$cd/Snatcher/systems.txt"])
        assertEquals("Popful Mail.cue\tSEGACD\n", p.homeTexts["$cd/Popful Mail/systems.txt"])
        assertEquals("Sonic CD.chd\tGPGX_SEGACD\n", p.homeTexts["$cd/systems.txt"])   // no entry for the disc folders
        assertNull(p.homeTexts["$cd/Lunar/systems.txt"])
    }

    /** A peer phone's extras of the console count only less the names its folder has, so that folder is listed. */
    @Test fun a_phone_peer_with_extras_of_the_console_has_its_folder_listed() = runTest {
        val unit = ExtraUnit(2, "GB", "Games", listOf("p.gb"), "p.gb", ".sources/sd/Games/p.gb", "p.gb", emptyList())
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteTexts["nx-extras/extras.json"] = RemoteText.Found(SyncExtras.encode(ExtrasManifest(units = listOf(unit))))
            remoteFolders[gb] = 0 to listOf(dir("."), file("p.gb"))
            home = mapOf(null to listOf(gb), gb to listOf("p.gb", "q.gb"))
        }
        val c = romClient(p, toggle = false, flag = "C", trailer = "")
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(listOf(gb), p.folderListings)
    }

    @Test fun a_pair_without_siblings_lists_no_folder() = runTest {
        val p = FakeSyncPlatform().apply { roms = true }
        romClient(p)
        // Only the save prep's listing (which games the handheld's folders hold), none for the ROM pairing.
        assertEquals(listOf(gb), p.folderListings)
    }

    @Test fun a_sibling_listing_failure_fails_as_step_5() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(mgba), dir(gbaRemote))
            remoteFolders[gbaRemote] = 10 to emptyList()
            home = mapOf(null to listOf(mgba), mgba to emptyList())
        }
        val c = romClient(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_ROMS, 10)), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
        assertTrue(p.romRuns.isEmpty())
    }

    /** A phone with All files access whose `Bios/PS` meets the peer's `bios/ps`; loose files and dot folders stay out. */
    private fun FakeSyncPlatform.withBios() = apply {
        roms = true
        remoteBios = 0 to listOf(dir("."), dir("ps"), dir(".cache"), file("loose.bin"), dir("GBA"))
        homeBios = listOf("PS", "MD")
    }

    @Test fun bios_runs_after_the_save_steps_and_before_the_roms_as_steps_5_and_6_of_9() = runTest {
        val p = FakeSyncPlatform().withBios().apply {
            romHoldAt = 0
            romResults[0] = listOf("PS/scph5501.bin", " 1 (xfr#1, to-chk=0/1)") to 0
            romResults[1] = listOf("PS/scph5500.bin", " 1 (xfr#1, to-chk=0/1)") to 0
        }
        var arrived = 0
        val c = romClient(p) { c -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.romsArrived.collect { arrived++ } } }
        val s = c.page.value as DeviceSyncPage.Syncing
        assertEquals(SyncStep.SEND_BIOS, s.step)
        assertEquals(9, s.steps)
        assertEquals("PS", s.console)
        assertTrue(p.alive.any { (on, t) -> on && t != null && "5/9 Sending BIOS: PS" in t })
        p.romHoldResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps.map { it.name } + listOf("LIST_BIOS", "SEND_BIOS", "RECEIVE_BIOS", "PULL_COLLECTIONS", "SEND_ROMS", "SEND_ROMS", "RECEIVE_ROMS", "RECEIVE_ROMS"), p.calls)
        assertEquals(RomRun(SyncStep.SEND_BIOS, "PS", RomEnd.Bios("PS"), RomEnd.Peer("bios", "ps"), ignoreExisting = true, pair = "PS\u0000ps"), p.romRuns[0])
        assertEquals(RomRun(SyncStep.RECEIVE_BIOS, "PS", RomEnd.Peer("bios", "ps"), RomEnd.Bios("PS"), ignoreExisting = true, pair = "PS\u0000ps"), p.romRuns[1])
        assertTrue(p.alive.any { (on, t) -> on && t != null && "6/9 Receiving BIOS: PS" in t })
        assertTrue(p.alive.any { (on, t) -> on && t != null && "8/9 Pushing ROMs: $gb" in t })
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 1, notes = listOf("Only on one device, not synced: 3DS.")), c.page.value)
        assertEquals(0, arrived)   // a BIOS file is not a ROM: no rescan
    }

    @Test fun bios_runs_with_roms_off_as_steps_5_and_6_of_7() = runTest {
        val p = FakeSyncPlatform().withBios().apply { holdAt = SyncStep.SEND_STATES }
        val c = romClient(p, toggle = false)
        assertEquals(7, (c.page.value as DeviceSyncPage.Syncing).steps)
        p.holdResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(SyncStep.SEND_BIOS, SyncStep.RECEIVE_BIOS), p.romRuns.map { it.step })
        assertTrue(p.alive.any { (on, t) -> on && t != null && "6/7 Receiving BIOS: PS" in t })
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0), c.page.value)
    }

    @Test fun without_all_files_access_bios_is_skipped_with_a_note_and_four_steps() = runTest {
        val p = FakeSyncPlatform().withBios().apply { roms = false; holdAt = SyncStep.SEND_STATES }
        val c = romClient(p, toggle = false)
        assertEquals(4, (c.page.value as DeviceSyncPage.Syncing).steps)
        p.holdResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertEquals(0, p.biosListings)
        assertTrue(p.romRuns.isEmpty())
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0, notes = listOf(DeviceSyncText.BIOS_NO_ACCESS, DeviceSyncText.COLLECTIONS_NO_ACCESS)), c.page.value)
    }

    @Test fun a_handheld_without_a_bios_module_skips_bios_with_the_update_note() = runTest {
        val p = FakeSyncPlatform().withBios().apply { remoteBios = 5 to listOf("@ERROR: Unknown module 'bios'"); holdAt = SyncStep.SEND_STATES }
        val c = romClient(p, toggle = false)
        // The BIOS listing comes only after step 4: the save steps count BIOS in.
        assertEquals(7, (c.page.value as DeviceSyncPage.Syncing).steps)
        assertEquals(0, p.biosListings)
        p.holdResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps.map { it.name } + "LIST_BIOS" + "PULL_COLLECTIONS", p.calls)
        assertTrue(p.romRuns.isEmpty())
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0, notes = listOf(DeviceSyncText.BIOS_OLD_HANDHELD)), c.page.value)
    }

    @Test fun a_phone_peer_without_a_bios_module_skips_bios_with_the_other_phone_note() = runTest {
        val p = FakeSyncPlatform().withBios().apply { remoteBios = 5 to listOf("@ERROR: Unknown module 'bios'") }
        val c = romClient(p, toggle = false, flag = "c", trailer = "")
        assertNull(p.serverStarted)
        assertTrue(p.romRuns.isEmpty())
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0, notes = listOf(DeviceSyncText.BIOS_PEER_PHONE)), c.page.value)
    }

    @Test fun bios_notes_come_before_rom_notes() = runTest {
        val p = FakeSyncPlatform().withBios().apply { remoteBios = 5 to listOf("@ERROR: Unknown module 'bios'") }
        val c = romClient(p)
        assertEquals(listOf(DeviceSyncText.BIOS_OLD_HANDHELD, "Only on one device, not synced: 3DS."), (c.page.value as DeviceSyncPage.Done).notes)
        assertEquals(listOf(SyncStep.SEND_ROMS, SyncStep.SEND_ROMS, SyncStep.RECEIVE_ROMS, SyncStep.RECEIVE_ROMS), p.romRuns.map { it.step })
    }

    @Test fun another_bios_listing_failure_fails_as_step_5_with_rsyncs_error() = runTest {
        val refused = "rsync: failed to connect to 192.168.1.10 (192.168.1.10): Connection refused (111)"
        val p = FakeSyncPlatform().withBios().apply { remoteBios = 10 to listOf(refused, "rsync error: error in socket IO (code 10) at clientserver.c(139)") }
        val c = romClient(p, toggle = false)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_BIOS, 10), detail = refused.removePrefix("rsync: ")), c.page.value)
        assertEquals(saveSteps.map { it.name } + "LIST_BIOS", p.calls)   // the saves moved first
        assertTrue(p.romRuns.isEmpty())
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
    }

    /** Only rsync's `Unknown module` exit 5 is a peer without `[bios]`; another exit 5 (a module whose folder is gone) fails. */
    @Test fun an_exit_5_without_unknown_module_fails_as_step_5() = runTest {
        val p = FakeSyncPlatform().withBios().apply { remoteBios = 5 to listOf("@ERROR: chdir failed", "rsync error: error starting client-server protocol (code 5)") }
        val c = romClient(p, toggle = false)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_BIOS, 5), detail = "chdir failed"), c.page.value)
        assertEquals(saveSteps, p.stepsRun)
        assertTrue(p.romRuns.isEmpty())
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
    }

    @Test fun a_failed_bios_run_fails_with_rsyncs_error_and_runs_no_rom() = runTest {
        val mkdir = "rsync: [Receiver] mkdir \"ps\" (in bios) failed: Permission denied (13)"
        val p = FakeSyncPlatform().withBios().apply { romResults[0] = listOf(mkdir, "rsync error: some files/attrs were not transferred (code 23)") to 23 }
        val c = romClient(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SEND_BIOS, 23), detail = mkdir.removePrefix("rsync: ")), c.page.value)
        assertEquals(1, p.romRuns.size)
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
        val full = FakeSyncPlatform().withBios().apply { romResults[1] = listOf("rsync: write failed on \"x\": No space left on device (28)") to 11 }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.NO_SPACE_HERE), romClient(full, toggle = false).page.value)
    }

    // Collections sync (step 7).

    private val fcHome = "Famicom (FC)"
    private val fcPeer = "Nintendo ES (FC)"

    /** A handheld with `Nintendo ES (FC)` meets this phone's `Famicom (FC)`. */
    private fun FakeSyncPlatform.withFc() = apply {
        roms = true
        remoteListing = 0 to listOf(dir(fcPeer))
        home = mapOf(null to listOf(fcHome), fcHome to listOf("A.nes"))
    }

    @Test fun collections_run_as_step_7_of_9_after_bios_and_before_the_roms() = runTest {
        val gate = CompletableDeferred<Unit>()
        val p = FakeSyncPlatform().withBios().apply { collectionsGate = gate }
        val c = romClient(p)
        val s = c.page.value as DeviceSyncPage.Syncing
        assertEquals(SyncStep.SYNC_COLLECTIONS, s.step)
        assertEquals(9, s.steps)
        assertNull(s.console)
        assertTrue(p.alive.any { (on, t) -> on && t != null && "7/9 Syncing collections" in t })
        gate.complete(Unit); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps.map { it.name } + listOf("LIST_BIOS", "SEND_BIOS", "RECEIVE_BIOS", "PULL_COLLECTIONS",
            "SEND_ROMS", "SEND_ROMS", "RECEIVE_ROMS", "RECEIVE_ROMS"), p.calls)
        assertTrue(p.alive.any { (on, t) -> on && t != null && "6/9 Receiving BIOS: PS" in t })
        assertTrue(p.alive.any { (on, t) -> on && t != null && "8/9 Pushing ROMs: $gb" in t })
        assertTrue(p.alive.any { (on, t) -> on && t != null && "9/9 Pulling ROMs: $gb" in t })
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    @Test fun collections_run_with_roms_off_as_step_7_of_7() = runTest {
        val p = FakeSyncPlatform().withBios().apply { holdAt = SyncStep.SEND_STATES }
        val c = romClient(p, toggle = false)
        assertEquals(7, (c.page.value as DeviceSyncPage.Syncing).steps)
        p.holdResult.complete(0); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps.map { it.name } + listOf("LIST_BIOS", "SEND_BIOS", "RECEIVE_BIOS", "PULL_COLLECTIONS"), p.calls)
        assertTrue(p.alive.any { (on, t) -> on && t != null && "7/7 Syncing collections" in t })
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0), c.page.value)
    }

    @Test fun without_all_files_access_collections_are_skipped_after_the_bios_note() = runTest {
        val p = FakeSyncPlatform().apply { roms = false }
        val c = romClient(p, toggle = false, flag = "R")
        assertFalse("PULL_COLLECTIONS" in p.calls)
        assertEquals(listOf(DeviceSyncText.BIOS_NO_ACCESS, DeviceSyncText.COLLECTIONS_NO_ACCESS, DeviceSyncText.ROMS_SKIPPED),
            (c.page.value as DeviceSyncPage.Done).notes)
    }

    @Test fun a_handheld_without_a_collections_module_skips_with_the_update_note() = runTest {
        val p = FakeSyncPlatform().withBios().apply {
            remoteBios = 5 to listOf("@ERROR: Unknown module 'bios'")
            collectionsPull = CollectionsPull(5, listOf("@ERROR: Unknown module 'collections'"))
        }
        val c = romClient(p)
        assertEquals(listOf(DeviceSyncText.BIOS_OLD_HANDHELD, DeviceSyncText.COLLECTIONS_OLD_HANDHELD, "Only on one device, not synced: 3DS."),
            (c.page.value as DeviceSyncPage.Done).notes)
        assertFalse("PUSH_COLLECTIONS" in p.calls)
        assertEquals(4, p.romRuns.size)   // the ROMs still move
    }

    @Test fun a_phone_peer_without_a_collections_module_skips_with_the_other_phone_note() = runTest {
        val p = FakeSyncPlatform().withBios().apply { collectionsPull = CollectionsPull(5, listOf("@ERROR: Unknown module 'collections'")) }
        val c = romClient(p, toggle = false, flag = "c", trailer = "")
        assertEquals(listOf(DeviceSyncText.COLLECTIONS_PEER_PHONE), (c.page.value as DeviceSyncPage.Done).notes)
    }

    @Test fun another_collections_failure_fails_as_step_7_with_rsyncs_error() = runTest {
        val refused = "rsync: failed to connect to 192.168.1.10 (192.168.1.10): Connection refused (111)"
        val p = FakeSyncPlatform().withBios().apply { collectionsPull = CollectionsPull(10, listOf(refused, "rsync error: error in socket IO (code 10)")) }
        val c = romClient(p)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SYNC_COLLECTIONS, 10), detail = refused.removePrefix("rsync: ")), c.page.value)
        assertEquals(2, p.romRuns.size)   // BIOS only, no ROM run
        assertFalse(p.socket.sent.any { it.first == SyncWire.DONE })
        val chdir = FakeSyncPlatform().withBios().apply { collectionsPull = CollectionsPull(5, listOf("@ERROR: chdir failed")) }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SYNC_COLLECTIONS, 5), detail = "chdir failed"),
            romClient(chdir, toggle = false).page.value)
    }

    @Test fun collections_merge_with_translated_lines_written_here_and_pushed() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            ownCollections["RPGs"] = listOf("/Roms/$fcHome/A.nes")
            ownCollections["Mine"] = listOf("/Roms/$fcHome/M.nes")
            ownCollections["Faves"] = listOf("/Roms/$fcHome/F.nes")
            collectionsPull = CollectionsPull(0, emptyList(), mapOf("rpgs.txt" to "/Roms/$fcPeer/B.nes\r\n/Roms/$fcPeer/A.nes\r\n",
                "faves.txt" to "/Roms/$fcPeer/G.nes\n", "map.txt" to "x\ty\n"))
        }
        val arrived = mutableListOf<SyncedCollections>()
        val c = romClient(p, toggle = false) { c -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.collectionsArrived.collect { arrived += it } } }
        assertEquals(listOf("RPGs" to listOf("/Roms/$fcHome/A.nes", "/Roms/$fcHome/B.nes"), "Faves" to listOf("/Roms/$fcHome/F.nes", "/Roms/$fcHome/G.nes")),
            p.writtenCollections)
        // rpgs.txt already has A (after `\r` and the folder name): only the changed files go back, under the peer's names.
        assertEquals(listOf(mapOf("faves.txt" to "/Roms/$fcPeer/G.nes\n/Roms/$fcPeer/F.nes\n", "Mine.txt" to "/Roms/$fcPeer/M.nes\n")), p.pushedCollections)
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 2, sent = 2), c.page.value)
        // Written by this phone's own writer: no store copy needs to take anything in.
        assertEquals(listOf(SyncedCollections(written = setOf("RPGs", "Faves"))), arrived)
    }

    @Test fun a_collection_that_could_not_be_saved_here_says_so_on_done() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            collectionWriteOk = false
            collectionsPull = CollectionsPull(0, emptyList(), mapOf("X.txt" to "/Roms/$fcPeer/B.nes\n"))
        }
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0, notes = listOf(DeviceSyncText.COLLECTIONS_NOT_SAVED)),
            romClient(p, toggle = false).page.value)
    }

    /** A peer file that would not decode is left alone both ways. */
    @Test fun an_undecodable_peer_collection_is_neither_written_nor_pushed() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            ownCollections["Faves"] = listOf("/Roms/$fcHome/A.nes")
            collectionsPull = CollectionsPull(0, emptyList(), unreadable = setOf("faves.txt"))
        }
        romClient(p, toggle = false)
        assertTrue(p.writtenCollections.isEmpty())
        assertFalse("PUSH_COLLECTIONS" in p.calls)
    }

    /** Each write carries what the merge read, so the writer merges rather than replaces a file that appeared meanwhile. */
    @Test fun writes_carry_their_base_and_an_unreadable_collection_is_left_alone() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            ownCollections["RPGs"] = listOf("/Roms/$fcHome/A.nes")
            ownCollections["Broken"] = null
            collectionsPull = CollectionsPull(0, emptyList(), mapOf("RPGs.txt" to "/Roms/$fcPeer/B.nes\n", "broken.txt" to "/Roms/$fcPeer/C.nes\n",
                "New.txt" to "/Roms/$fcPeer/N.nes\n"))
        }
        romClient(p, toggle = false)
        assertEquals(listOf("RPGs", "New"), p.writtenCollections.map { it.first })
        assertEquals(listOf(listOf("/Roms/$fcHome/A.nes"), null), p.writeBases)
        assertEquals(listOf(mapOf("RPGs.txt" to "/Roms/$fcPeer/B.nes\n/Roms/$fcPeer/A.nes\n")), p.pushedCollections)
    }

    @Test fun nothing_changed_pushes_and_writes_nothing() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            ownCollections["RPGs"] = listOf("/Roms/$fcHome/A.nes")
            collectionsPull = CollectionsPull(0, emptyList(), mapOf("RPGs.txt" to "/Roms/$fcPeer/A.nes\n"))
        }
        val c = romClient(p, toggle = false)
        assertFalse("PUSH_COLLECTIONS" in p.calls)
        assertTrue(p.writtenCollections.isEmpty())
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 0, sent = 0), c.page.value)
    }

    @Test fun a_failed_collections_push_fails_as_step_7() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            ownCollections["Mine"] = listOf("/Roms/$fcHome/M.nes")
            collectionsPushResult = 23 to listOf("rsync: [Receiver] mkstemp \"Mine.txt\" failed: Permission denied (13)")
        }
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.SYNC_COLLECTIONS, 23),
            detail = "[Receiver] mkstemp \"Mine.txt\" failed: Permission denied (13)"), romClient(p, toggle = false).page.value)
    }

    /** ROMs off: the console pairing is still listed so collections translate on every sync. */
    @Test fun with_roms_off_collections_still_translate_folder_names() = runTest {
        val p = FakeSyncPlatform().withFc().apply { collectionsPull = CollectionsPull(0, emptyList(), mapOf("X.txt" to "/Roms/$fcPeer/B.nes\n")) }
        romClient(p, toggle = false)
        assertEquals(listOf("X" to listOf("/Roms/$fcHome/B.nes")), p.writtenCollections)
        assertTrue(p.romRuns.isEmpty())
    }

    /** A failed listing never fails the sync: lines then travel unchanged. */
    @Test fun a_failed_console_listing_leaves_lines_unchanged() = runTest {
        val p = FakeSyncPlatform().withFc().apply {
            remoteListing = 10 to emptyList()
            collectionsPull = CollectionsPull(0, emptyList(), mapOf("X.txt" to "/Roms/$fcPeer/B.nes\n"))
        }
        val c = romClient(p, toggle = false)
        assertEquals(listOf("X" to listOf("/Roms/$fcPeer/B.nes")), p.writtenCollections)
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    @Test fun the_server_reports_collections_it_received() = runTest {
        val p = FakeSyncPlatform()
        val arrived = mutableListOf<SyncedCollections>()
        val c = serving(p)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.collectionsArrived.collect { arrived += it } }
        p.logLine!!("2026/10/15 12:00:00 [1] collections recv RPGs.txt")
        p.logLine!!("2026/10/15 12:00:00 [1] collections send Mine.txt")
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        // The send is the peer reading Mine.txt to merge it: not a change here.
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 0), c.page.value)
        // Written by the daemon, past the app's writer: AppState folds it into a store copy.
        assertEquals(listOf(SyncedCollections(received = setOf("RPGs"))), arrived)
    }

    /** The client reads both collections on every sync; those reads are not counted as sent ("1 received, 2 sent" for one change). */
    @Test fun the_server_counts_no_collection_reads_as_sent() = runTest {
        val p = FakeSyncPlatform()
        val c = serving(p)
        p.logLine!!("2026/10/16 22:50:00 [1] collections send Play Later.txt")
        p.logLine!!("2026/10/16 22:50:00 [1] collections send To Be Completed.txt")
        p.logLine!!("2026/10/16 22:50:01 [1] collections recv To Be Completed.txt")
        p.logLine!!("2026/10/16 22:50:02 [1] saves send GB/B.srm")
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 1), c.page.value)
    }

    @Test fun the_server_counts_bios_files_without_a_rom_rescan() = runTest {
        val p = FakeSyncPlatform()
        var arrived = 0
        val c = serving(p)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { c.romsArrived.collect { arrived++ } }
        p.logLine!!("2026/10/15 12:00:00 [1] bios recv PS/scph5501.bin")
        p.logLine!!("2026/10/15 12:00:00 [1] bios send PS/scph5500.bin")
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertEquals(DeviceSyncPage.Done(SyncLink.WIFI, received = 1, sent = 1), c.page.value)
        assertEquals(0, arrived)
    }

    private val gbaHandheld = "Game Boy Advance (MGBA)"

    /** A handheld peer: the phone's saves are staged in the handheld's layout, steps 2 and 4 use it, the receive folds back. */
    @Test fun handheld_saves_are_staged_folded_and_cleared() = runTest {
        val p = FakeSyncPlatform().apply {
            remoteListing = 0 to listOf(dir(gbaHandheld))
            remoteFolders[gbaHandheld] = 0 to listOf(dir("."), file("Emerald.gba"))
            homeSaves = listOf(SaveFile("GBA/Emerald.srm", 1_000_000L))
            receivedSaves = listOf(SaveFile("MGBA/Emerald.srm", 1_000_000L), SaveFile("MGBA/Emerald.gba.sav", 9_000_000L))
        }
        val c = romClient(p, toggle = false)
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(listOf(listOf(StageCopy("GBA/Emerald.srm", "MGBA/Emerald.srm"))), p.staged)
        assertTrue("MGBA" in p.stagedBy.getValue(SyncStep.SEND_SAVES)!!)
        assertEquals(p.stagedBy.getValue(SyncStep.SEND_SAVES), p.stagedBy.getValue(SyncStep.RECEIVE_SAVES))
        assertNull(p.stagedBy.getValue(SyncStep.SEND_STATES))
        assertEquals(listOf(FoldPlan(listOf(FoldWrite("MGBA/Emerald.gba.sav", "GBA/Emerald.srm", 9_000_000L, battery = true)), emptyList(), 0)), p.folds)
        assertEquals(1, p.stagingCleared)
    }

    @Test fun a_phone_peer_keeps_the_whole_saves_rsync() = runTest {
        val p = FakeSyncPlatform().apply { homeSaves = listOf(SaveFile("GBA/Emerald.srm", 1_000_000L)) }
        val c = romClient(p, toggle = false, flag = "c", trailer = "")
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertTrue(p.staged.isEmpty())
        assertTrue(p.folds.isEmpty())
        assertEquals(0, p.stagingCleared)
        assertEquals(saveSteps, p.stepsRun)
        assertTrue(saveSteps.all { p.stagedBy.getValue(it) == null })
        assertTrue(p.folderListings.isEmpty())
    }

    /** No ROM listing: the saves still move, each to its emulator's folder. */
    @Test fun a_failed_rom_listing_still_syncs_saves() = runTest {
        val p = FakeSyncPlatform().apply {
            remoteListing = 10 to emptyList()
            homeSaves = listOf(SaveFile("GBA/Emerald.srm", 1_000_000L))
            defaults = mapOf("gba" to "MGBA")
        }
        val c = romClient(p, toggle = false)
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(listOf(listOf(StageCopy("GBA/Emerald.srm", "MGBA/Emerald.srm"))), p.staged)
        assertEquals(1, p.folds.size)
    }

    /** A phone ROM's `systems.txt` tag names the game's emulator when the handheld has the game nowhere. */
    @Test fun a_games_systems_txt_tag_picks_its_emulator_folder() = runTest {
        val p = FakeSyncPlatform().apply {
            roms = true
            remoteListing = 0 to listOf(dir(gb))
            home = mapOf(null to listOf("Game Boy Advance (GBA)"), "Game Boy Advance (GBA)" to listOf("Emerald.gba"))
            gameTags["Game Boy Advance (GBA)"] = mapOf("Emerald.gba" to "MGBA")
            homeSaves = listOf(SaveFile("GBA/Emerald.srm", 1_000_000L))
        }
        val c = romClient(p, toggle = false)
        assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(listOf(listOf(StageCopy("GBA/Emerald.srm", "MGBA/Emerald.srm"))), p.staged)
    }

    /** With no handheld listing a GPGX save may be any Sega console's: skipped, never folded into Genesis. */
    @Test fun a_failed_listing_skips_gpgx_saves() = runTest {
        val p = FakeSyncPlatform().apply {
            remoteListing = 10 to emptyList()
            receivedSaves = listOf(SaveFile("GPGX/Alex Kidd.srm", 9_000_000L))
        }
        val c = romClient(p, toggle = false)
        val done = assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(DeviceSyncText.savesSkipped(1), done.notes.last())
        assertEquals(emptyList(), p.folds.single().writes)
    }

    @Test fun skipped_gpgx_saves_add_a_done_note() = runTest {
        val p = FakeSyncPlatform().apply {
            remoteListing = 0 to listOf(dir("Sega Genesis (GPGX)"), dir("Sega Master System (GPGX)"))
            receivedSaves = listOf(SaveFile("GPGX/Lost.srm", 9_000_000L))
        }
        val c = romClient(p, toggle = false)
        val done = assertIs<DeviceSyncPage.Done>(c.page.value)
        assertEquals(DeviceSyncText.savesSkipped(1), done.notes.last())
        assertEquals(1, p.folds.single().skipped)
    }

    @Test fun staging_is_cleared_when_step_4_fails() = runTest {
        val p = FakeSyncPlatform().apply { steps[SyncStep.RECEIVE_SAVES] = emptyList<String>() to 12 }
        val c = romClient(p, toggle = false)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepFailed(SyncStep.RECEIVE_SAVES, 12)), c.page.value)
        assertEquals(1, p.staged.size)
        assertTrue(p.folds.isEmpty())
        assertEquals(1, p.stagingCleared)
    }

    @Test fun a_failed_staging_fails_as_step_2_and_a_failed_fold_as_step_4() = runTest {
        val p = FakeSyncPlatform().apply { stageOk = false }
        val c = romClient(p, toggle = false)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepError(SyncStep.SEND_SAVES)), c.page.value)
        assertTrue(p.stepsRun.isEmpty())
        assertEquals(1, p.stagingCleared)
        val q = FakeSyncPlatform().apply { foldOk = false }
        val d = romClient(q, toggle = false)
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.stepError(SyncStep.RECEIVE_SAVES)), d.page.value)
        assertEquals(1, q.stagingCleared)
    }

    /** Pairs over Wi-Fi as [ownIp] with a device at [handheld] sending [hello]'s flag, A not pressed. */
    private fun TestScope.confirming(ownIp: String = phone, hello: String = "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"): Pair<FakeSyncPlatform, DeviceSyncController> {
        val p = FakeSyncPlatform().apply { wifi = ownIp }
        val c = rig(p, confirm = false)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, hello); runCurrent()
        assertEquals(DeviceSyncPage.Confirm(SyncLink.WIFI, handheld, ownIp), c.page.value)
        return p to c
    }

    private val server = "192.168.1.5"
    private val phonePeer = "HELLO_TRIMUI_SYNC:$handheld:c"

    /** An old or already confirmed server sends READY at once: as client, nothing runs before this side's A. */
    @Test fun as_client_nothing_runs_before_a_on_the_confirm_page() = runTest {
        val (p, c) = confirming()
        repeat(10) { p.socket.from(handheld, SyncWire.READY); advanceTimeBy(1_000); runCurrent() }
        assertIs<DeviceSyncPage.Confirm>(c.page.value)
        assertTrue(p.stepsRun.isEmpty())
        assertTrue(p.calls.isEmpty())
        assertTrue(p.folderListings.isEmpty())
        c.press(NxButton.A); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertEquals(saveSteps, p.stepsRun)
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    /** As server, the daemon and READY wait for this side's A; then READY goes out at once, as an old client expects. */
    @Test fun as_server_the_daemon_starts_only_after_a() = runTest {
        val (p, c) = confirming(server, phonePeer)
        advanceTimeBy(10_000); runCurrent()
        assertNull(p.serverStarted)
        assertFalse(p.socket.sent.any { it.first == SyncWire.READY })
        c.press(NxButton.A); runCurrent()
        assertEquals(handheld, p.serverStarted)
        assertEquals(1, p.socket.sent.count { it == SyncWire.READY to handheld })
        assertEquals(SyncRole.SERVER, assertIs<DeviceSyncPage.Syncing>(c.page.value).role)
        p.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)
    }

    @Test fun b_on_the_confirm_page_sends_fail_and_goes_back_to_ready() = runTest {
        for ((ownIp, hello) in listOf(phone to "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2", server to phonePeer)) {
            val (p, c) = confirming(ownIp, hello)
            c.press(NxButton.B); runCurrent()
            assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(5, p.socket.fails(handheld))
            assertFalse(c.running)
            assertTrue(p.socket.closed)
            assertNull(p.serverStarted)
            assertTrue(p.stepsRun.isEmpty())
            assertEquals(DeviceSyncPage.Ready(SyncLink.WIFI), c.page.value)
        }
    }

    @Test fun the_notifications_cancel_on_the_confirm_page_stops_and_sends_fail() = runTest {
        val (p, c) = confirming()
        c.cancel(); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.STOPPED), c.page.value)
        assertEquals(5, p.socket.fails(handheld))
        assertFalse(c.running)
    }

    @Test fun the_peer_cancelling_while_the_confirm_page_shows_says_so_without_an_echo() = runTest {
        for ((ownIp, hello) in listOf(phone to "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2", server to phonePeer)) {
            val (p, c) = confirming(ownIp, hello)
            p.socket.from("192.168.1.99", SyncWire.FAIL); runCurrent()   // not the peer
            assertIs<DeviceSyncPage.Confirm>(c.page.value)
            p.socket.from(handheld, SyncWire.FAIL); advanceTimeBy(1_000); runCurrent()
            assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_CANCELLED), c.page.value)
            assertEquals(0, p.socket.fails(handheld))
            assertFalse(c.running)
            assertNull(p.serverStarted)
            assertTrue(p.stepsRun.isEmpty())
        }
    }

    /** The peer's ACK was lost and it keeps saying HELLO: the confirm page answers it again, so it pairs too. */
    @Test fun a_hello_from_the_peer_while_confirming_is_acked_again() = runTest {
        val (p, c) = confirming()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        assertEquals(2, p.socket.sent.count { it == SyncWire.ack(phone) to handheld })
        assertIs<DeviceSyncPage.Confirm>(c.page.value)
    }

    @Test fun a_slow_confirm_never_times_out_on_either_side() = runTest {
        val (p, c) = confirming()
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertIs<DeviceSyncPage.Confirm>(c.page.value)
        c.press(NxButton.A); runCurrent()
        advanceTimeBy(10 * 60_000L); runCurrent()   // the server's user still deciding
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        p.socket.from(handheld, SyncWire.READY); advanceTimeBy(5_000); runCurrent()
        assertIs<DeviceSyncPage.Done>(c.page.value)

        val (q, d) = confirming(server, phonePeer)
        advanceTimeBy(30 * 60_000L); runCurrent()
        assertIs<DeviceSyncPage.Confirm>(d.page.value)
        assertNull(q.serverStarted)
        d.press(NxButton.A); runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(d.page.value)
        q.socket.from(handheld, SyncWire.DONE); runCurrent()
        assertIs<DeviceSyncPage.Done>(d.page.value)
    }

    /** The server's user pressed B after this side's A (or its daemon would not start): its FAIL ends the wait for READY at once. */
    @Test fun the_servers_fail_before_ready_ends_the_clients_wait() = runTest {
        val p = FakeSyncPlatform()
        val c = rig(p)
        c.pick(SyncLink.WIFI); c.press(NxButton.A); runCurrent()
        p.socket.from(handheld, "HELLO_TRIMUI_SYNC:$handheld:0\u0000v2"); runCurrent()
        assertIs<DeviceSyncPage.Syncing>(c.page.value)
        p.socket.from(handheld, SyncWire.FAIL); advanceTimeBy(1_000); runCurrent()
        assertEquals(DeviceSyncPage.Failed(SyncLink.WIFI, DeviceSyncText.PEER_ENDED_EARLY), c.page.value)
        assertEquals(0, p.socket.fails(handheld))
        assertTrue(p.stepsRun.isEmpty())
    }
}
