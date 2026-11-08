package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.NetplayChannel
import com.nxredux.mobile.netplay.NetplayIoException
import com.nxredux.mobile.netplay.PipeChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WizardTest {
    private val esc = "\u001f"
    private val kartEsc = "Super${esc}Mario${esc}Kart${esc}(USA)"
    private val sonicEsc = "Sonic${esc}The${esc}Hedgehog${esc}(USA)"
    private val hostSfc = HostOffer("Super Mario Kart (USA)", "SFC", "snes9x", pick = null, usb = false)
    private val joinSfc = ClientOffer("super mario kart", any = false, tag = "SFC", core = "snes9x", pick = null, usb = false)
    private val hostMd = HostOffer("Sonic The Hedgehog (USA)", "MD", "picodrive", pick = null, usb = false)
    private val joinMd = ClientOffer("Sonic The Hedgehog (USA)", any = false, tag = "MD", core = "picodrive", pick = null, usb = false)

    /** A raw handheld-style host: reads the client's HELLO, then offers [names] by name. */
    private suspend fun offerSaves(h: PipeChannel, vararg names: String) {
        h.readLine(1000)
        h.writeLine("HELLO 2 $kartEsc host 2")
        h.writeLine("SYNC-READY ${names.size}")
        names.forEach { h.writeLine("FILE $it") }
    }

    // ---- phone <-> phone, handheld-shaped peers ----

    private val redEsc = "Pokemon${esc}Red${esc}(USA)"

    @Test fun a_link_host_skips_the_save_copy() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostGb = HostOffer("Pokemon Red (USA)", "GB", "gambatte", pick = null, usb = false)
        val hostJob = async { Wizard.host(h, hostGb, saves = { error("a link session copies no saves") }) }
        c.writeLine("HELLO 2 $redEsc client caps=core=gambatte,tag=GB")
        assertEquals("HELLO 2 $redEsc host 2 caps=core=gambatte,tag=GB", c.readLine(1000))
        assertEquals("START 2", c.readLine(1000))   // no SYNC-READY
        c.writeLine("START")
        assertEquals(HostOutcome.Started("gambatte", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun phone_to_phone_link_starts_without_touching_saves() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostGba = HostOffer("Pokemon Ruby (USA)", "GBA", "gpsp", pick = null, usb = false)
        val hostJob = async { Wizard.host(h, hostGba, saves = { error("a link session copies no saves") }) }
        val sink = FakeSaveSink()
        val joinGba = ClientOffer("Pokemon Ruby (USA)", any = false, tag = "GBA", core = "gpsp", pick = null, usb = false)
        assertEquals(ClientOutcome.Started("gpsp", 2, emptySaves = false), Wizard.client(c, joinGba, sink))
        assertEquals(HostOutcome.Started("gpsp", "10.0.0.2", emptySaves = false), hostJob.await())
        assertFalse(sink.begun)
    }

    @Test fun phone_to_phone_copies_one_save_with_rsync() = runTest {
        val (h, c) = PipeChannel.pair()
        val served = FakeHostSaves(files = listOf("nxsave.srm"))
        val hostJob = async { Wizard.host(h, hostSfc, saves = { served }) }
        val sink = FakeSaveSink()
        assertEquals(ClientOutcome.Started("snes9x", 2, emptySaves = false), Wizard.client(c, joinSfc, sink))
        assertEquals(HostOutcome.Started("snes9x", "10.0.0.2", emptySaves = false), hostJob.await())
        assertEquals(listOf("10.0.0.1" to "nxsave.srm"), sink.pulled)
        assertTrue(sink.committed)
        assertEquals(listOf("10.0.0.2"), served.started)
        assertTrue(served.closed)
    }

    @Test fun a_handheld_client_with_four_fields_and_no_caps_starts() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves() }) }
        c.writeLine("HELLO 2 $kartEsc client")
        assertEquals("HELLO 2 $kartEsc host 2 caps=core=snes9x,tag=SFC", c.readLine(1000))
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("snes9x", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun a_handheld_host_offers_a_save_by_name_and_the_client_pulls_it() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink()
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        assertEquals("HELLO 2 super${esc}mario${esc}kart client caps=core=snes9x,tag=SFC,auto=1", h.readLine(1000))
        h.writeLine("HELLO 2 $kartEsc host 2")
        h.writeLine("SYNC-READY 1")
        h.writeLine("FILE nxsave.srm")
        assertEquals("SYNC-DONE", h.readLine(1000))
        h.writeLine("START 2")
        assertEquals("START", h.readLine(1000))
        assertEquals(ClientOutcome.Started("snes9x", 2, emptySaves = false), clientJob.await())
        assertEquals(listOf("10.0.0.1" to "nxsave.srm"), sink.pulled)
        assertTrue(sink.committed)
    }

    // ---- no rsync: decline / skip, both on empty saves ----

    @Test fun a_client_without_rsync_fails_offered_saves_and_the_host_turns_it_away() = runTest {
        val (h, c) = PipeChannel.pair()
        val served = FakeHostSaves(files = listOf("nxsave.srm", "nxsave.sav"))
        val hostJob = async { Wizard.host(h, hostSfc, saves = { served }) }
        val sink = FakeSaveSink(canPull = false)
        assertEquals(ClientOutcome.Refused("Could not copy the host's saves."), Wizard.client(c, joinSfc, sink))
        assertEquals(HostOutcome.Turned("sync"), hostJob.await())
        assertFalse(sink.begun)
        assertTrue(served.closed)
    }

    @Test fun without_rsync_the_client_sends_sync_fail_never_sync_done() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink(canPull = false)
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        offerSaves(h, "nxsave.srm", "nxsave.sav")
        assertEquals("SYNC-FAIL", h.readLine(1000))
        assertEquals(ClientOutcome.Refused("Could not copy the host's saves."), clientJob.await())
        assertFalse(sink.begun)
        assertTrue(sink.pulled.isEmpty())
    }

    @Test fun without_rsync_an_empty_sync_ready_still_starts() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink(canPull = false)
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        offerSaves(h)
        assertEquals("SYNC-DONE", h.readLine(1000))
        h.writeLine("START 2")
        assertEquals("START", h.readLine(1000))
        assertEquals(ClientOutcome.Started("snes9x", 2, emptySaves = false), clientJob.await())
        assertFalse(sink.begun)
    }

    @Test fun a_host_without_rsync_skips_sync_ready_and_both_play_empty() = runTest {
        val (h, c) = PipeChannel.pair()
        val served = FakeHostSaves(files = listOf("nxsave.srm"), canServe = false)
        val hostJob = async { Wizard.host(h, hostSfc, saves = { served }) }
        c.writeLine("HELLO 2 $kartEsc client")
        c.readLine(1000)
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("snes9x", "10.0.0.2", emptySaves = true), hostJob.await())
        assertTrue(served.started.isEmpty())
        assertTrue(served.closed)
    }

    @Test fun a_host_with_no_saves_skips_sync_and_keeps_its_real_saves() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves(files = emptyList(), canServe = false) }) }
        assertEquals(ClientOutcome.Started("snes9x", 2, emptySaves = false), Wizard.client(c, joinSfc, FakeSaveSink()))
        assertEquals(HostOutcome.Started("snes9x", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    // ---- save copy failures ----

    @Test fun a_daemon_that_does_not_start_turns_the_joiner_away_with_reject_sync() = runTest {
        val (h, c) = PipeChannel.pair()
        val served = FakeHostSaves(files = listOf("nxsave.srm"), startOk = false)
        val hostJob = async { Wizard.host(h, hostSfc, saves = { served }) }
        assertEquals(ClientOutcome.Refused("The host could not share\nits saves."), Wizard.client(c, joinSfc, FakeSaveSink()))
        assertEquals(HostOutcome.Turned("sync"), hostJob.await())
        assertTrue(served.closed)
    }

    @Test fun a_host_save_read_failure_sends_reject_sync() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { throw IllegalStateException("disk") }) }
        assertEquals(ClientOutcome.Refused("The host could not share\nits saves."), Wizard.client(c, joinSfc, FakeSaveSink()))
        assertEquals(HostOutcome.Turned("sync"), hostJob.await())
    }

    @Test fun a_failed_pull_sends_sync_fail_and_the_host_turns_it_away() = runTest {
        val (h, c) = PipeChannel.pair()
        val served = FakeHostSaves(files = listOf("nxsave.srm"))
        val hostJob = async { Wizard.host(h, hostSfc, saves = { served }) }
        val sink = FakeSaveSink(failOn = "nxsave.srm")
        assertEquals(ClientOutcome.Refused("Could not copy the host's saves."), Wizard.client(c, joinSfc, sink))
        assertEquals(HostOutcome.Turned("sync"), hostJob.await())
        assertTrue(sink.aborted)
        assertFalse(sink.committed)
        assertTrue(served.closed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun the_host_waits_30_s_for_sync_done() = runTest {
        val (h, c) = PipeChannel.pair()
        val served = FakeHostSaves(files = listOf("nxsave.srm"))
        val hostJob = async { Wizard.host(h, hostSfc, saves = { served }) }
        c.writeLine("HELLO 2 $kartEsc client")
        c.readLine(1000)
        assertEquals("SYNC-READY 1", c.readLine(1000))
        assertEquals("FILE nxsave.srm", c.readLine(1000))
        val from = testScheduler.currentTime
        testScheduler.advanceTimeBy(Wizard.PULL_MS - 1)
        testScheduler.runCurrent()
        assertFalse(hostJob.isCompleted)
        assertEquals(HostOutcome.Turned("sync"), hostJob.await())
        assertEquals(Wizard.PULL_MS, testScheduler.currentTime - from)
        assertTrue(served.closed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun a_client_cancelled_mid_pull_aborts_the_sink() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink(pullMs = 10_000)
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        offerSaves(h, "nxsave.srm")
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        assertTrue(sink.begun)
        clientJob.cancel()
        clientJob.join()
        assertTrue(sink.aborted)
        assertFalse(sink.committed)
    }

    @Test fun one_pull_gets_15_s() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink(pullMs = 15_001)
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        offerSaves(h, "nxsave.srm")
        assertEquals("SYNC-FAIL", h.readLine(60_000))
        assertEquals(ClientOutcome.Refused("Could not copy the host's saves."), clientJob.await())
        assertTrue(sink.aborted)
    }

    @Test fun all_pulls_share_23_s() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink(pullMs = 12_000)   // 12 s + 12 s > 23 s
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        offerSaves(h, "nxsave.srm", "nxsave.sav")
        assertEquals("SYNC-FAIL", h.readLine(60_000))
        assertEquals(ClientOutcome.Refused("Could not copy the host's saves."), clientJob.await())
    }

    @Test fun pulls_inside_the_budget_succeed() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink(pullMs = 11_000)   // 22 s in all
        val clientJob = async { Wizard.client(c, joinSfc, sink) }
        offerSaves(h, "nxsave.srm", "nxsave.sav")
        assertEquals("SYNC-DONE", h.readLine(60_000))
        h.writeLine("START 2")
        assertEquals(ClientOutcome.Started("snes9x", 2, emptySaves = false), clientJob.await())
        assertEquals(2, sink.pulled.size)
    }

    @Test fun only_nxsave_srm_and_sav_are_taken() = runTest {
        for (bad in listOf("nxsave.brm", "nxsave.rtc", "../nxsave.srm", "Game.srm")) {
            val (h, c) = PipeChannel.pair()
            val sink = FakeSaveSink()
            val job = async { Wizard.client(c, joinSfc, sink) }
            offerSaves(h, bad)
            assertEquals("SYNC-FAIL", h.readLine(1000), bad)
            assertEquals(ClientOutcome.Refused("The host offered an\nunexpected file."), job.await(), bad)
            assertFalse(sink.begun, bad)
        }
    }

    @Test fun more_than_32_files_is_sync_fail() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2"); h.writeLine("SYNC-READY 33")
        assertEquals("SYNC-FAIL", h.readLine(1000))
        assertEquals(ClientOutcome.Refused("The host offered more saves\nthan expected."), job.await())
    }

    @Test fun a_sync_ready_without_a_count_is_an_unexpected_reply_without_sync_fail() = runTest {
        val (h, c) = PipeChannel.pair()
        val sink = FakeSaveSink()
        val job = async { Wizard.client(c, joinSfc, sink) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2"); h.writeLine("SYNC-READY many")
        assertEquals(ClientOutcome.Refused("Unexpected reply from the host."), job.await())
        assertFailsWith<NetplayIoException> { h.readLine(100) }
        assertFalse(sink.begun)
    }

    @Test fun a_negative_sync_ready_count_is_sync_fail() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2"); h.writeLine("SYNC-READY -1")
        assertEquals("SYNC-FAIL", h.readLine(1000))
        assertEquals(ClientOutcome.Refused("The host offered more saves\nthan expected."), job.await())
    }

    @Test fun a_line_that_is_not_file_is_unexpected() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2"); h.writeLine("SYNC-READY 1"); h.writeLine("START 2")
        assertEquals("SYNC-FAIL", h.readLine(1000))
        assertEquals(ClientOutcome.Refused("Unexpected reply from the host."), job.await())
    }

    @Test fun a_silent_file_line_is_stopped_responding() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2"); h.writeLine("SYNC-READY 1")
        assertEquals("SYNC-FAIL", h.readLine(10_000))
        assertEquals(ClientOutcome.Refused("The host stopped responding."), job.await())
    }

    // ---- core check, joining ----

    @Test fun a_core_reject_on_auto_asks_for_one_retry_with_the_hosts_core() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinMd, FakeSaveSink()) }
        assertEquals("HELLO 2 $sonicEsc client caps=core=picodrive,tag=MD,auto=1", h.readLine(1000))
        h.writeLine("REJECT core-genesis_plus_gx.GPGX")
        assertEquals(ClientOutcome.Retry("genesis_plus_gx"), job.await())
    }

    @Test fun a_second_core_reject_is_final() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinMd.copy(core = "genesis_plus_gx", mayRetry = false), FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("REJECT core-picodrive.MD")
        assertEquals(ClientOutcome.Refused("The host is using\nPicoDrive (MD folder).\n\nPick the game from the same\nfolder on both devices."), job.await())
    }

    @Test fun a_pinned_emulator_is_told_to_change_it() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinMd.copy(core = "genesis_plus_gx", pick = "genesis_plus_gx"), FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("REJECT core-picodrive.MD")
        assertEquals(ClientOutcome.Refused("The host is using PicoDrive.\nSet Emulator to PicoDrive."), job.await())
    }

    @Test fun a_core_this_tag_does_not_offer_names_the_hosts_folder() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("REJECT core-fbneo.FBN")
        assertEquals(ClientOutcome.Refused("The host is using\nFinalBurn Neo (FBN folder).\n\nPick the game from the same\nfolder on both devices."), job.await())
    }

    @Test fun a_bare_core_reject_is_a_plain_refusal() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("REJECT core")
        assertEquals(ClientOutcome.Refused("The host refused the connection."), job.await())
    }

    @Test fun a_host_hello_naming_another_core_switches_on_auto() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinMd, FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("HELLO 2 $sonicEsc host 2 caps=core=genesis_plus_gx,tag=GPGX")
        h.writeLine("START 2")
        assertEquals("START", h.readLine(1000))
        assertEquals(ClientOutcome.Started("genesis_plus_gx", 2, emptySaves = false), job.await())
    }

    @Test fun a_pinned_joiner_stops_on_another_core_and_sends_nothing() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinMd.copy(pick = "picodrive"), FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("HELLO 2 $sonicEsc host 2 caps=core=genesis_plus_gx,tag=GPGX")
        assertEquals(ClientOutcome.Refused("The host is using Genesis Plus GX.\nSet Emulator to Genesis Plus GX."), job.await())
        assertFailsWith<NetplayIoException> { h.readLine(100) }
    }

    @Test fun a_host_of_another_game_is_refused_locally() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("HELLO 2 Other host 2")
        assertEquals(ClientOutcome.Refused("The host is running a\ndifferent game."), job.await())
    }

    // ---- core check, hosting ----

    @Test fun an_auto_host_adopts_the_joiners_core() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostMd, saves = { FakeHostSaves() }) }
        c.writeLine("HELLO 2 $sonicEsc client caps=core=genesis_plus_gx,tag=GPGX")
        assertEquals("HELLO 2 $sonicEsc host 2 caps=core=genesis_plus_gx,tag=MD", c.readLine(1000))
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("genesis_plus_gx", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun a_pinned_host_turns_another_core_away_naming_its_own() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostMd.copy(pick = "picodrive"), saves = { FakeHostSaves() }) }
        c.writeLine("HELLO 2 $sonicEsc client caps=core=genesis_plus_gx,tag=GPGX")
        assertEquals("REJECT core-picodrive.MD", c.readLine(1000))
        assertEquals(HostOutcome.Turned("core", "genesis_plus_gx"), hostJob.await())
    }

    private val rubyEsc = "Pokemon${esc}Ruby${esc}(USA)"
    private val hostGbaUsb = HostOffer("Pokemon Ruby (USA)", "GBA", "gpsp", pick = null, usb = true)
    private val joinMgbaUsb = ClientOffer("Pokemon Ruby (USA)", any = false, tag = "MGBA", core = "mgba", pick = null, usb = true)

    @Test fun an_auto_host_keeps_its_core_for_a_phone_joiner_on_auto() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostGbaUsb, saves = { error("a link session copies no saves") }) }
        c.writeLine("HELLO 2 $rubyEsc client caps=core=mgba,tag=MGBA,auto=1")
        assertEquals("HELLO 2 $rubyEsc host 2 caps=core=gpsp,tag=GBA", c.readLine(1000))   // its own core, never auto
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("gpsp", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun an_auto_host_adopts_a_handheld_joiners_core() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostGbaUsb, saves = { error("a link session copies no saves") }) }
        c.writeLine("HELLO 2 $rubyEsc client caps=core=mgba,tag=MGBA")
        assertEquals("HELLO 2 $rubyEsc host 2 caps=core=mgba,tag=GBA", c.readLine(1000))
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("mgba", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun phone_to_phone_on_auto_follows_the_host() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostGbaUsb, saves = { error("a link session copies no saves") }) }
        assertEquals(ClientOutcome.Started("gpsp", 2, emptySaves = false), Wizard.client(c, joinMgbaUsb, FakeSaveSink()))
        assertEquals(HostOutcome.Started("gpsp", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun a_client_on_auto_sends_auto_1_and_a_pinned_one_does_not() = runTest {
        val sent = mapOf(
            joinMgbaUsb to "core=mgba,tag=MGBA,auto=1",
            joinMgbaUsb.copy(pick = "mgba") to "core=mgba,tag=MGBA",
            joinMgbaUsb.copy(tag = "GBA", core = "gpsp", usb = false) to "core=gpsp,tag=GBA",   // over Wi-Fi GBA has one core
            ClientOffer("Contra (USA)", any = false, tag = "FC", core = "fceumm", pick = null, usb = false) to "core=fceumm,tag=FC",
        )
        for ((offer, caps) in sent) {
            val (h, c) = PipeChannel.pair()
            val job = async { Wizard.client(c, offer, FakeSaveSink()) }
            assertEquals(caps, HelloCodec.parse(h.readLine(1000))!!.caps, offer.toString())
            h.writeLine("REJECT")
            job.await()
        }
    }

    @Test fun a_host_turns_away_a_core_its_tag_does_not_offer() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves() }) }
        c.writeLine("HELLO 2 $kartEsc client caps=core=fbneo,tag=FBN")
        assertEquals("REJECT core-snes9x.SFC", c.readLine(1000))
        assertEquals(HostOutcome.Turned("core", "fbneo"), hostJob.await())
    }

    @Test fun host_gates_run_version_game_role_before_core() = runTest {
        val cases = listOf(
            "HELLO 1 $kartEsc client" to "REJECT version",
            "HELLO 2 Other client caps=core=fbneo,tag=FBN" to "REJECT game",
            "HELLO 2 $kartEsc host 2" to "REJECT role",
        )
        for ((line, reply) in cases) {
            val (h, c) = PipeChannel.pair()
            val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves() }) }
            c.writeLine(line)
            assertEquals(reply, c.readLine(1000), line)
            assertEquals(HostOutcome.Turned(reply.removePrefix("REJECT ")), hostJob.await(), line)
        }
    }

    @Test fun any_skips_the_game_gate() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves() }) }
        c.writeLine("HELLO 2 Other client any")
        assertEquals("HELLO 2 $kartEsc host 2 caps=core=snes9x,tag=SFC", c.readLine(1000))
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("snes9x", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun a_line_that_is_not_hello_is_turned_away_without_a_reply() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves() }) }
        c.writeLine("GOODBYE")
        assertEquals(HostOutcome.Turned(null), hostJob.await())
    }

    // ---- long game names: the handheld reads the game token with %255s, a line is at most 511 bytes ----

    @Test fun a_300_char_rom_name_is_sent_as_a_255_char_token() = runTest {
        val long = "Long Game " + "x".repeat(290)   // 300 chars
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc.copy(game = long, any = true), FakeSaveSink()) }
        val line = h.readLine(1000)
        val token = line.split(' ')[2]
        assertEquals(255, token.length)
        assertEquals(long.take(255).replace(' ', '\u001f'), token)
        assertEquals("HELLO 2 $token client any caps=core=snes9x,tag=SFC,auto=1", line)
        assertTrue(line.encodeToByteArray().size + 1 <= 511)
        h.writeLine("REJECT game")
        job.await()
    }

    @Test fun a_host_with_a_300_char_rom_name_answers_with_a_255_char_token() = runTest {
        val long = "Long Game " + "x".repeat(290)
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc.copy(game = long), saves = { FakeHostSaves() }) }
        c.writeLine("HELLO 2 ${long.replace(' ', '\u001f')} client")
        val line = c.readLine(1000)
        assertEquals("HELLO 2 ${long.take(255).replace(' ', '\u001f')} host 2 caps=core=snes9x,tag=SFC", line)
        assertTrue(line.length + 1 <= 511)
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertEquals(HostOutcome.Started("snes9x", "10.0.0.2", emptySaves = false), hostJob.await())
    }

    @Test fun a_multibyte_game_token_is_cut_to_255_bytes_on_a_character_boundary() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc.copy(game = "é".repeat(200), any = true), FakeSaveSink()) }
        val token = h.readLine(1000).split(' ')[2]   // PipeChannel reads one char per byte
        assertEquals(254, token.length)              // 127 two-byte characters, never half of one
        h.writeLine("REJECT game")
        job.await()
    }

    // ---- waits ----

    @Test fun a_silent_host_is_no_answer() = runTest {
        val (_, c) = PipeChannel.pair()
        assertEquals(ClientOutcome.Refused("The host did not answer.\n\nPlease try again."), Wizard.client(c, joinSfc, FakeSaveSink()))
    }

    @Test fun a_quiet_host_is_waited_on_twelve_times() = runTest {
        val (h, c) = PipeChannel.pair()
        var waits = 0
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink(), onWaiting = { assertEquals(2, it); waits++ }) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2")
        assertEquals(ClientOutcome.Refused("The host did not answer.\n\nPlease try again."), job.await())
        assertEquals(Wizard.START_WAIT_TRIES, waits)
    }

    @Test fun a_closed_host_stops_the_wait_at_once() = runTest {
        var waits = 0
        val ch = ScriptedChannel(listOf("HELLO 2 $kartEsc host 2"))
        assertEquals(ClientOutcome.Refused("The host did not answer.\n\nPlease try again."), Wizard.client(ch, joinSfc, FakeSaveSink(), onWaiting = { waits++ }))
        assertEquals(1, waits)
    }

    @Test fun a_reject_while_waiting_for_start_shows_its_reason() = runTest {
        val (h, c) = PipeChannel.pair()
        val job = async { Wizard.client(c, joinSfc, FakeSaveSink()) }
        h.readLine(1000); h.writeLine("HELLO 2 $kartEsc host 2"); h.writeLine("REJECT sync")
        assertEquals(ClientOutcome.Refused("The host could not share\nits saves."), job.await())
    }
}

/** Replies with [lines] in order, then reports the stream closed (EOF), as a socket channel would. */
private class ScriptedChannel(lines: List<String>) : NetplayChannel {
    private val queue = ArrayDeque(lines)
    override val remoteAddress = "10.0.0.1"
    override suspend fun readLine(timeoutMs: Long): String = queue.removeFirstOrNull() ?: throw NetplayIoException("closed")
    override suspend fun writeLine(line: String) {}
    override fun close() {}
}
