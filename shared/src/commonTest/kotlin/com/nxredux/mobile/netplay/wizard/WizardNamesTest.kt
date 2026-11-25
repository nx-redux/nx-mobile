package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.NetplayIoException
import com.nxredux.mobile.netplay.PipeChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WizardNamesTest {
    private val esc = "\u001f"
    private val redEsc = "Pokemon${esc}Red${esc}(USA)"
    private val hostSfc = HostOffer("Super Mario Kart (USA)", "SFC", "snes9x", pick = null, usb = false, name = "Syuhada")
    private val joinSfc = ClientOffer("super mario kart", any = false, tag = "SFC", core = "snes9x", pick = null, usb = false, name = "Nova")
    private val hostGb = HostOffer("Pokemon Red (USA)", "GB", "gambatte", pick = null, usb = false, name = "Syuhada")
    private val joinGb = ClientOffer("Pokemon Red (USA)", any = false, tag = "GB", core = "gambatte", pick = null, usb = false, name = "Nova")

    @Test fun two_phones_trade_names_after_start() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostSfc, saves = { FakeHostSaves() }) }
        val joined = Wizard.client(c, joinSfc, FakeSaveSink()) as ClientOutcome.Started
        val hosted = hostJob.await() as HostOutcome.Started
        assertEquals("Syuhada", joined.peerName)
        assertEquals("Nova", hosted.peerName)
    }

    @Test fun a_link_session_trades_names_too() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostGb, saves = { error("a link session copies no saves") }) }
        val joined = Wizard.client(c, joinGb, FakeSaveSink()) as ClientOutcome.Started
        assertEquals("Syuhada", joined.peerName)
        assertEquals("Nova", (hostJob.await() as HostOutcome.Started).peerName)
    }

    @Test fun a_side_with_a_name_says_so_in_its_caps() = runTest {
        val (h, c) = PipeChannel.pair()
        async { Wizard.host(h, hostGb, saves = { error("unused") }) }
        c.writeLine("HELLO 2 $redEsc client caps=core=gambatte,tag=GB")
        assertEquals("HELLO 2 $redEsc host 2 caps=core=gambatte,tag=GB,nm=1", c.readLine(1000))
    }

    @Test fun a_handheld_joiner_never_gets_a_name_line() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostGb, saves = { error("a link session copies no saves") }) }
        c.writeLine("HELLO 2 $redEsc client caps=core=gambatte,tag=GB")   // no nm=1
        c.readLine(1000)
        assertEquals("START 2", c.readLine(1000))
        c.writeLine("START")
        assertNull((hostJob.await() as HostOutcome.Started).peerName)
        assertFailsWith<NetplayIoException> { c.readLine(1000) }   // nothing more was sent
    }

    @Test fun a_joiner_with_a_name_sends_none_to_an_older_host() = runTest {
        val (h, c) = PipeChannel.pair()
        val older = hostGb.copy(name = null)
        val hostJob = async { Wizard.host(h, older, saves = { error("a link session copies no saves") }) }
        val joined = Wizard.client(c, joinGb, FakeSaveSink()) as ClientOutcome.Started
        assertNull(joined.peerName)
        assertNull((hostJob.await() as HostOutcome.Started).peerName)
        assertFailsWith<NetplayIoException> { h.readLine(1000) }   // the joiner sent no NAME either
    }

    @Test fun a_missing_name_line_only_costs_the_name() = runTest {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostGb, saves = { error("a link session copies no saves") }) }
        c.writeLine("HELLO 2 $redEsc client caps=core=gambatte,tag=GB,nm=1")
        c.readLine(1000)
        c.readLine(1000)   // START 2
        c.writeLine("START")   // then no NAME
        val hosted = hostJob.await()
        assertTrue(hosted is HostOutcome.Started)
        assertNull(hosted.peerName)
    }

    @Test fun name_lines_are_cleaned() {
        assertEquals("NAME Syuhada${esc}Fo", NameLine.format("Syuhada Fold 8"))   // a Player name: 10 characters
        assertEquals("Syuhada F", NameLine.parse("NAME Syuhada${esc}F"))
        assertEquals("abcdefghij", NameLine.parse("NAME abcdefghijklmnop"))
        assertEquals("ab", NameLine.parse("NAME a\u0007b"))
        assertNull(NameLine.parse("NAME"))
        assertNull(NameLine.parse("START"))
        assertNull(NameLine.parse("NAME a b"))
        assertNull(NameLine.parse(null))
    }
}
