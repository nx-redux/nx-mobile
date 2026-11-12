package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.FakeLan
import com.nxredux.mobile.netplay.FakeNetplayPlatform
import com.nxredux.mobile.netplay.FoundHost
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.PipeChannel
import com.nxredux.mobile.netplay.WifiInfo
import com.nxredux.mobile.netplay.usb.FakeUsbCable
import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.usb.UsbFail
import com.nxredux.mobile.netplay.usb.UsbHello
import com.nxredux.mobile.netplay.usb.UsbLinkState
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetplayUsbControllerTest {
    private fun entry(tag: String, file: String): Listed {
        val s = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())
        val g = GameEntry(ContentRef("Home/Roms/X ($tag)/$file"), file, Names.stripExtension(file), folder = ContentRef("Home/Roms/X ($tag)"), sourceId = GamePath.HOME, relativeDir = "Roms/X ($tag)")
        return Listed(s, g)
    }

    private val kart = entry("SFC", "Super Mario Kart (USA).sfc")
    private val title = "Super Mario Kart (USA)"
    private val hostUp = UsbLinkState.Up(UsbHello.HOST_IP, UsbHello.DEVICE_IP)
    private val deviceUp = UsbLinkState.Up(UsbHello.DEVICE_IP, UsbHello.HOST_IP)

    private class Launches { val all = ArrayList<Pair<Listed, NetplaySession>>() }

    private fun TestScope.controller(platform: FakeNetplayPlatform, launches: Launches = Launches()): NetplayController =
        NetplayController(backgroundScope as CoroutineScope, platform, { e, s -> launches.all += e to s }, work = StandardTestDispatcher(testScheduler))

    private fun NetplayController.press(vararg buttons: NxButton) = buttons.forEach(::onButton)

    /** A platform with a cable that links at once as [up] on start (or stays Starting when null). */
    private fun usbPlatform(lan: FakeLan = FakeLan(), up: UsbLinkState.Up? = hostUp, wifi: WifiInfo? = WifiInfo("10.0.0.1", "Pixel")) =
        FakeNetplayPlatform(lan, wifi).apply {
            lastLink = NetplayLink.USB
            usbCable = FakeUsbCable().apply { if (up != null) onStart = { it.state.value = up } }
        }

    @Test fun role_page_offers_connection_only_with_a_cable() = runTest {
        val plain = controller(FakeNetplayPlatform().apply { usbCable = FakeUsbCable(availableNow = false) })
        plain.open(kart)
        assertNull(assertIs<NetplayPage.Role>(plain.page.value).connection)

        val p = FakeNetplayPlatform(wifiInfo = null).apply { usbCable = FakeUsbCable() }
        val c = controller(p)
        c.open(kart)
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.WIFI, role.connection)
        assertEquals(listOf(NetplayRoleRow.CONNECTION, NetplayRoleRow.EMULATOR, NetplayRoleRow.HOST, NetplayRoleRow.JOIN, NetplayRoleRow.WIFI), role.rows)
        assertEquals(2, role.selected)                                    // Host Game is highlighted, under the tabs
        c.press(NxButton.UP, NxButton.UP, NxButton.RIGHT)
        val usb = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.USB, usb.connection)
        assertEquals(listOf(NetplayRoleRow.CONNECTION, NetplayRoleRow.EMULATOR, NetplayRoleRow.HOST, NetplayRoleRow.JOIN), usb.rows)   // no Wi-Fi row
        assertEquals(NetplayLink.USB, p.lastLink)                         // remembered
        c.press(NxButton.A)
        assertEquals(NetplayLink.WIFI, assertIs<NetplayPage.Role>(c.page.value).connection)
    }

    @Test fun a_tap_on_a_connection_tab_picks_it_and_is_remembered() = runTest {
        val p = FakeNetplayPlatform().apply { usbCable = FakeUsbCable() }
        val c = controller(p)
        c.open(kart)
        val first = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(listOf(NetplayLink.WIFI, NetplayLink.USB), first.connections)
        c.pickConnection(NetplayLink.USB)
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.USB, role.connection)
        assertEquals(NetplayRoleRow.CONNECTION, role.rows[role.selected])
        assertEquals(NetplayLink.USB, p.lastLink)
        c.pickConnection(NetplayLink.CABLE_LINK_WIFI)   // not offered here
        assertEquals(NetplayLink.USB, assertIs<NetplayPage.Role>(c.page.value).connection)
    }

    private val emerald = entry("MGBA", "Pokemon Emerald (USA).gba")

    @Test fun mgba_offers_the_cable_only_for_a_game_without_a_wifi_mode() = runTest {
        val noCable = controller(FakeNetplayPlatform().apply { usbCable = FakeUsbCable(availableNow = false) })
        assertTrue(noCable.offers(emerald))
        assertTrue(noCable.offers(kart))
        noCable.open(emerald); runCurrent()
        val err = assertIs<NetplayPage.Error>(noCable.page.value)         // read first: then the cable-only page
        assertEquals(NetplayText.cableOnlyNoUsb("mGBA"), err.text)
        noCable.onButton(NxButton.B)
        assertNull(noCable.page.value)

        val p = FakeNetplayPlatform().apply { usbCable = FakeUsbCable(); lastLink = NetplayLink.WIFI }
        val c = controller(p)
        assertTrue(c.offers(emerald))
        c.open(emerald); runCurrent()
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.USB, role.connection)                     // whatever the last choice was
        assertEquals(listOf(NetplayRoleRow.CONNECTION, NetplayRoleRow.EMULATOR, NetplayRoleRow.HOST, NetplayRoleRow.JOIN), role.rows)
        assertEquals(listOf(NetplayText.AUTO, "mGBA", "gpSP"), role.emulators)
        c.press(NxButton.UP, NxButton.UP, NxButton.A, NxButton.RIGHT)
        assertEquals(NetplayLink.USB, assertIs<NetplayPage.Role>(c.page.value).connection)   // nothing to switch to
        assertEquals(NetplayLink.WIFI, p.lastLink)                         // the remembered choice is left alone
    }

    @Test fun without_a_cable_an_mgba_wifi_mode_game_still_reaches_the_role_page() = runTest {
        val p = FakeNetplayPlatform().apply { usbCable = FakeUsbCable(availableNow = false); gbaMode = "poke" }
        val c = controller(p)
        c.open(emerald); runCurrent()
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertNull(role.connection)                                        // Wi-Fi only
        c.press(NxButton.A); runCurrent()                                  // Host on Auto: mGBA over Wi-Fi
        assertIs<NetplayPage.Hosting>(c.page.value)
    }

    @Test fun mgba_offers_wifi_too_for_a_wifi_mode_game() = runTest {
        val p = FakeNetplayPlatform().apply { usbCable = FakeUsbCable(); lastLink = NetplayLink.WIFI; gbaMode = "rfu" }
        val c = controller(p)
        c.open(emerald); runCurrent()
        assertEquals(NetplayLink.WIFI, assertIs<NetplayPage.Role>(c.page.value).connection)   // the last choice
        c.press(NxButton.UP, NxButton.UP, NxButton.A)
        assertEquals(NetplayLink.USB, assertIs<NetplayPage.Role>(c.page.value).connection)
        assertEquals(NetplayLink.USB, p.lastLink)
    }

    @Test fun the_last_connection_is_shown_again() = runTest {
        val c = controller(usbPlatform())
        c.open(kart)
        assertEquals(NetplayLink.USB, assertIs<NetplayPage.Role>(c.page.value).connection)
    }

    @Test fun host_and_join_over_the_cable_launch_both_sides() = runTest {
        val lan = FakeLan()
        val hostP = usbPlatform(lan, hostUp, wifi = null)                 // no Wi-Fi needed over the cable
        val clientP = usbPlatform(lan, deviceUp, wifi = null).apply { ownAddress = UsbHello.DEVICE_IP }
        val hostLaunches = Launches(); val clientLaunches = Launches()
        val host = controller(hostP, hostLaunches)
        val client = controller(clientP, clientLaunches)

        host.open(kart); host.press(NxButton.A); runCurrent()
        assertEquals(NetplayPage.Hosting(title, NetplayText.USB_ADDRESS, NetplayText.WAITING_PLAYER, NetplayText.USB_CONNECTED), host.page.value)
        assertEquals(listOf(title to UsbHello.DEVICE_IP), hostP.unicast)

        clientP.found.emit(FoundHost(UsbHello.HOST_IP, title))           // the host's unicast discovery
        client.open(kart); client.press(NxButton.RIGHT, NxButton.A); runCurrent()

        assertEquals(listOf(NetplaySession(NetplayRole.HOST, UsbHello.DEVICE_IP, "snes9x", "snes9x", overUsb = true)), hostLaunches.all.map { it.second })
        assertEquals(listOf(NetplaySession(NetplayRole.CLIENT, UsbHello.HOST_IP, "snes9x", "snes9x", overUsb = true)), clientLaunches.all.map { it.second })
        assertEquals(listOf(NetplayLink.USB), clientP.connectedOver)
        assertEquals(0, hostP.usbCable!!.stops)                            // the game keeps the cable
        assertEquals(0, clientP.usbCable!!.stops)
        assertTrue(hostP.adverts.single().stopped)
        assertNull(host.page.value); assertNull(client.page.value)
    }

    @Test fun usb_join_without_a_discovery_packet_joins_after_the_peek() = runTest {
        val (h, cEnd) = PipeChannel.pair(hostSees = UsbHello.DEVICE_IP, clientSees = UsbHello.HOST_IP)
        val p = usbPlatform(up = deviceUp).apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.RIGHT, NxButton.A); runCurrent()
        assertEquals(NetplayPage.Progress(NetplayText.JOIN, NetplayText.CONNECTING), c.page.value)
        assertEquals(emptyList(), p.connected)
        advanceTimeBy(NetplayController.USB_PEEK_MS + 1); runCurrent()
        assertEquals(listOf(UsbHello.HOST_IP), p.connected)
        h.close()
    }

    @Test fun usb_join_to_another_game_asks_and_b_goes_back_stopping_the_cable() = runTest {
        val p = usbPlatform(up = deviceUp)
        val c = controller(p)
        p.found.emit(FoundHost(UsbHello.HOST_IP, "Super Metroid (USA)"))
        c.open(kart); c.press(NxButton.RIGHT, NxButton.A); runCurrent()
        assertIs<NetplayPage.JoinAnyway>(c.page.value)
        c.press(NxButton.B)
        assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(1, p.usbCable!!.stops)
    }

    @Test fun no_link_in_10_s_names_the_phone_or_the_handheld() = runTest {
        val p = usbPlatform(up = null)
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertEquals(NetplayPage.Progress(title, NetplayText.USB_CONNECT + "\n\n" + NetplayText.USB_STARTING), c.page.value)
        advanceTimeBy(NetplayController.USB_LINK_MS + 1); runCurrent()
        assertEquals(NetplayText.USB_NO_LINK, assertIs<NetplayPage.Error>(c.page.value).text)
        assertEquals(1, p.usbCable!!.stops)

        val q = usbPlatform(up = null).apply { usbCable!!.onStart = { it.state.value = UsbLinkState.Starting(handheld = true) } }
        val d = controller(q)
        d.open(kart); d.press(NxButton.A); runCurrent()
        advanceTimeBy(NetplayController.USB_LINK_MS + 1); runCurrent()
        assertEquals(NetplayText.USB_NO_LINK_HANDHELD, assertIs<NetplayPage.Error>(d.page.value).text)
        d.press(NxButton.A)
        assertIs<NetplayPage.Role>(d.page.value)
    }

    @Test fun cable_failures_show_their_texts() = runTest {
        val cases = mapOf(
            UsbLinkState.Failed(UsbFail.VERSION) to NetplayText.USB_VERSION,
            UsbLinkState.Failed(UsbFail.VPN_BLOCKED) to NetplayText.USB_VPN_BLOCKED,
            UsbLinkState.Failed(UsbFail.UNSUPPORTED) to NetplayText.USB_UNAVAILABLE,
            UsbLinkState.Failed(UsbFail.DENIED) to NetplayText.USB_DENIED,
            UsbLinkState.Failed(UsbFail.NO_LINK, handheld = true) to NetplayText.USB_NO_LINK_HANDHELD,
            UsbLinkState.Failed(UsbFail.PEER_HOST) to NetplayText.USB_PEER_HOST,
        )
        for ((failed, text) in cases) {
            val p = usbPlatform(up = null).apply { usbCable!!.onStart = { it.state.value = failed } }
            val c = controller(p)
            c.open(kart); c.press(NxButton.A); runCurrent()
            assertEquals(text, assertIs<NetplayPage.Error>(c.page.value).text)
        }
    }

    @Test fun a_cable_lost_during_bring_up_says_usb_cable_disconnected() = runTest {
        val p = usbPlatform(up = null).apply { usbCable!!.onStart = { it.state.value = UsbLinkState.Lost } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertEquals(NetplayText.USB_LOST, assertIs<NetplayPage.Error>(c.page.value).text)   // not 10 s later as "No USB link"
        assertEquals(1, p.usbCable!!.stops)
    }

    @Test fun consent_is_waited_out_without_the_10_s_limit() = runTest {
        val p = usbPlatform(up = null).apply { usbCable!!.onStart = { it.state.value = UsbLinkState.Consent } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertEquals(NetplayPage.Progress(title, NetplayText.USB_VPN_NOTE), c.page.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(NetplayPage.Progress(title, NetplayText.USB_VPN_NOTE), c.page.value)
        p.usbCable!!.state.value = UsbLinkState.Starting(handheld = false); runCurrent()
        assertEquals(NetplayPage.Progress(title, NetplayText.USB_CONNECT + "\n\n" + NetplayText.USB_STARTING), c.page.value)
        p.usbCable!!.state.value = hostUp; runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
    }

    @Test fun usb_permission_pauses_the_10_s_wait_and_restarts_it() = runTest {
        val p = usbPlatform(up = null)
        val c = controller(p)
        val starting = NetplayPage.Progress(title, NetplayText.USB_CONNECT + "\n\n" + NetplayText.USB_STARTING)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertEquals(starting, c.page.value)
        p.usbCable!!.state.value = UsbLinkState.Permission; runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(starting, c.page.value)                               // still waiting, no "No USB link"
        p.usbCable!!.state.value = UsbLinkState.Starting(handheld = false); runCurrent()
        advanceTimeBy(9_000); runCurrent()
        assertEquals(starting, c.page.value)                               // the 10 s began after the dialog
        p.usbCable!!.state.value = hostUp; runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
        assertEquals(0, p.usbCable!!.stops)
    }

    @Test fun a_late_consent_shows_the_vpn_note_and_is_not_timed_out() = runTest {
        val p = usbPlatform(up = null)
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        advanceTimeBy(5_000); runCurrent()
        p.usbCable!!.state.value = UsbLinkState.Consent; runCurrent()
        assertEquals(NetplayPage.Progress(title, NetplayText.USB_VPN_NOTE), c.page.value)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(NetplayPage.Progress(title, NetplayText.USB_VPN_NOTE), c.page.value)
        p.usbCable!!.state.value = hostUp; runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
    }

    @Test fun cable_lost_while_hosting_says_usb_cable_disconnected() = runTest {
        val p = usbPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
        p.usbCable!!.state.value = UsbLinkState.Lost
        advanceTimeBy(NetplayController.WIFI_POLL_MS + 1); runCurrent()
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals(NetplayText.USB_LOST, err.text)
        assertTrue(p.adverts.single().stopped)
        assertEquals(1, p.acceptsCancelled)
        assertEquals(1, p.usbCable!!.stops)
        c.press(NxButton.A)
        assertEquals(NetplayLink.USB, assertIs<NetplayPage.Role>(c.page.value).connection)
    }

    @Test fun usb_host_closes_a_caller_that_is_not_the_cable_peer() = runTest {
        val p = usbPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        val (stranger, _) = PipeChannel.pair(hostSees = "192.168.1.50")
        p.lan.pending.send(stranger); runCurrent()
        assertTrue(stranger.closed)
        assertEquals(NetplayPage.Hosting(title, NetplayText.USB_ADDRESS, NetplayText.WAITING_PLAYER, NetplayText.USB_CONNECTED), c.page.value)
        assertEquals(2, p.acceptsStarted)                                  // listening again
    }

    @Test fun b_during_bring_up_goes_back_and_stops_the_cable() = runTest {
        val p = usbPlatform(up = null)
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        c.press(NxButton.B); runCurrent()
        assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(1, p.usbCable!!.stops)
        advanceTimeBy(NetplayController.USB_LINK_MS + 1); runCurrent()
        assertIs<NetplayPage.Role>(c.page.value)                           // the cancelled wait shows nothing later
    }

    @Test fun close_during_bring_up_stops_the_cable() = runTest {
        val p = usbPlatform(up = null)
        val c = controller(p)
        c.open(kart); c.press(NxButton.RIGHT, NxButton.A); runCurrent()
        c.close()
        assertEquals(1, p.usbCable!!.stops)
        assertNull(c.page.value)
    }

    @Test fun wifi_hosting_is_unchanged_with_a_cable_present() = runTest {
        val p = FakeNetplayPlatform().apply { usbCable = FakeUsbCable() }
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertEquals(NetplayPage.Hosting(title, "Pixel  10.0.0.1", NetplayText.WAITING_PLAYER), c.page.value)
        assertEquals(0, p.usbCable!!.starts)
        assertEquals(emptyList(), p.unicast)
    }
}
