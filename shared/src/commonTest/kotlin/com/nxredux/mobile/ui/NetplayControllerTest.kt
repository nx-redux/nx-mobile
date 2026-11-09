package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.FakeLan
import com.nxredux.mobile.netplay.FakeNetplayPlatform
import com.nxredux.mobile.netplay.FoundHost
import com.nxredux.mobile.netplay.NetplayIoException
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.PipeChannel
import com.nxredux.mobile.netplay.RejectReason
import com.nxredux.mobile.netplay.WifiInfo
import com.nxredux.mobile.netplay.wizard.FakeHostSaves
import com.nxredux.mobile.netplay.wizard.Hello
import com.nxredux.mobile.netplay.wizard.HelloCodec
import com.nxredux.mobile.netplay.wizard.HostSaves
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetplayControllerTest {
    private val esc = "\u001f"

    private fun entry(tag: String, file: String): Listed {
        val s = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())
        val g = GameEntry(ContentRef("Home/Roms/X ($tag)/$file"), file, Names.stripExtension(file), folder = ContentRef("Home/Roms/X ($tag)"), sourceId = GamePath.HOME, relativeDir = "Roms/X ($tag)")
        return Listed(s, g)
    }

    private val kart = entry("SFC", "Super Mario Kart (USA).sfc")
    private val sonic = entry("MD", "Sonic The Hedgehog (USA).md")

    private class Launches { val all = ArrayList<Pair<Listed, NetplaySession>>() }

    private fun TestScope.controller(platform: FakeNetplayPlatform, launches: Launches = Launches()): NetplayController =
        // The handshakes' own context is a second dispatcher on the same scheduler: a real hop, still on virtual time.
        NetplayController(backgroundScope as CoroutineScope, platform, { e, s -> launches.all += e to s }, work = StandardTestDispatcher(testScheduler))

    private fun NetplayController.press(vararg buttons: NxButton) = buttons.forEach(::onButton)

    @Test fun role_page_lists_emulators_for_two_core_tags() = runTest {
        val c = controller(FakeNetplayPlatform())
        c.open(sonic)
        assertEquals(
            NetplayPage.Role("Sonic The Hedgehog (USA)", listOf("Auto (match the other device)", "PicoDrive", "Genesis Plus GX"), pick = 0, selected = 0, needWifi = false),
            c.page.value,
        )
        c.open(entry("FC", "Contra (USA).nes"))
        assertNull(assertIs<NetplayPage.Role>(c.page.value).emulators)
    }

    private val ruby = entry("GBA", "Pokemon Ruby (USA).gba")

    @Test fun gba_shows_the_emulator_row_with_gpsp_and_mgba() = runTest {
        val c = controller(FakeNetplayPlatform())
        c.open(ruby); runCurrent()
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(listOf("Auto (match the other device)", "gpSP", "mGBA"), role.emulators)
        assertEquals(listOf(NetplayRoleRow.HOST, NetplayRoleRow.JOIN, NetplayRoleRow.EMULATOR), role.rows)
    }

    @Test fun mgba_pinned_over_wifi_is_refused_with_the_cable_only_text() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(ruby); runCurrent()
        c.press(NxButton.DOWN, NxButton.DOWN, NxButton.RIGHT, NxButton.RIGHT, NxButton.UP, NxButton.UP)   // Emulator = mGBA, on Host
        assertEquals(2, assertIs<NetplayPage.Role>(c.page.value).pick)
        c.press(NxButton.A)
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals("mGBA links this game over the USB cable only.\n\nPick USB Cable, or another emulator.", err.text)
        assertEquals(0, p.adverts.size)
        c.press(NxButton.B)
        assertEquals(2, assertIs<NetplayPage.Role>(c.page.value).pick)                     // back on the Role page, pick kept
        c.press(NxButton.DOWN, NxButton.A)                                                  // Join Game
        assertEquals(NetplayText.cableOnly("mGBA"), assertIs<NetplayPage.Error>(c.page.value).text)
        assertEquals(0, p.browsing)
    }

    @Test fun gba_on_auto_over_wifi_joins_with_gpsp_and_no_auto() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(ruby); runCurrent()
        c.press(NxButton.DOWN, NxButton.A)   // Join on Auto over Wi-Fi: mGBA is not a choice for a game without a Wi-Fi mode
        p.found.emit(FoundHost("10.0.0.1", "Pokemon Ruby (USA)")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals("core=gpsp,tag=GBA,wl=none", HelloCodec.parse(h.readLine(1000))!!.caps)
    }

    @Test fun a_gba_game_is_probed_once_off_the_main_thread_and_its_mode_goes_in_the_caps() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd }; gbaMode = "rfu" }
        val c = controller(p)
        c.open(ruby)
        assertIs<NetplayPage.Progress>(c.page.value)                       // the probe runs on the work context
        runCurrent()
        assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(listOf("Pokemon Ruby (USA).gba"), p.probes)
        c.press(NxButton.DOWN, NxButton.A)   // Join on Auto over Wi-Fi: with a Wi-Fi mode either core can link
        p.found.emit(FoundHost("10.0.0.1", "Pokemon Ruby (USA)")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals("core=gpsp,tag=GBA,wl=rfu,auto=1", HelloCodec.parse(h.readLine(1000))!!.caps)
        assertEquals(1, p.probes.size)
    }

    @Test fun mgba_pinned_over_wifi_hosts_a_wifi_mode_game() = runTest {
        val p = FakeNetplayPlatform().apply { gbaMode = "poke" }
        val c = controller(p)
        c.open(ruby); runCurrent()
        c.press(NxButton.DOWN, NxButton.DOWN, NxButton.RIGHT, NxButton.RIGHT, NxButton.UP, NxButton.UP, NxButton.A)   // Emulator = mGBA, Host
        runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
        assertEquals(1, p.adverts.size)
    }

    @Test fun host_turns_away_a_game_in_another_link_mode_and_says_so() = runTest {
        val p = FakeNetplayPlatform().apply { gbaMode = "poke" }
        val c = controller(p)
        c.open(ruby); runCurrent()
        c.press(NxButton.DOWN, NxButton.DOWN, NxButton.RIGHT, NxButton.RIGHT, NxButton.UP, NxButton.UP, NxButton.A)   // Emulator = mGBA, Host
        runCurrent()
        val (h, cEnd) = PipeChannel.pair()
        p.lan.pending.send(h)
        cEnd.writeLine("HELLO 2 Pokemon${esc}Ruby${esc}(USA) client caps=core=gpsp,tag=GBA,wl=rfu"); runCurrent()
        assertEquals("REJECT link-mgba.poke.rfu", cEnd.readLine(1000)); runCurrent()
        assertEquals(NetplayText.LINK_TURNED_AWAY, assertIs<NetplayPage.Hosting>(c.page.value).status)
        advanceTimeBy(NetplayController.NOTICE_MS + 1)
        assertEquals(NetplayText.WAITING_PLAYER, assertIs<NetplayPage.Hosting>(c.page.value).status)
    }

    @Test fun only_gba_link_games_are_probed_and_b_during_the_probe_closes() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart)
        assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(emptyList<String>(), p.probes)
        c.open(ruby)
        c.press(NxButton.B)
        assertNull(c.page.value)
        runCurrent()
        assertNull(c.page.value)                                           // a late probe result opens nothing
    }

    @Test fun a_console_folder_game_listed_under_the_default_emulator_offers_that_emulator_tag() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        val system = SystemFolder("Sega Genesis", "GPGX", CoreRegistry.forTag("GPGX"), emptyList(), folderName = "Sega Genesis (MD)")
        val game = GameEntry(ContentRef("Home/Roms/Sega Genesis (MD)/Sonic.md"), "Sonic.md", "Sonic", folder = ContentRef("Home/Roms/Sega Genesis (MD)"), sourceId = GamePath.HOME, relativeDir = "Roms/Sega Genesis (MD)")
        c.open(Listed(system, game))
        c.press(NxButton.DOWN, NxButton.A)
        p.found.emit(FoundHost("10.0.0.1", "Sonic")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertTrue(HelloCodec.parse(h.readLine(1000))!!.caps.contains("tag=GPGX"))
    }

    @Test fun emulator_row_cycles_with_left_and_right() = runTest {
        val c = controller(FakeNetplayPlatform())
        c.open(sonic)
        c.press(NxButton.DOWN, NxButton.DOWN, NxButton.RIGHT)
        assertEquals(1, assertIs<NetplayPage.Role>(c.page.value).pick)
        c.press(NxButton.RIGHT, NxButton.RIGHT)
        assertEquals(0, assertIs<NetplayPage.Role>(c.page.value).pick)   // wraps
        c.press(NxButton.LEFT)
        assertEquals(2, assertIs<NetplayPage.Role>(c.page.value).pick)
        c.press(NxButton.UP, NxButton.LEFT)   // on Join Game: LEFT changes nothing
        assertEquals(2, assertIs<NetplayPage.Role>(c.page.value).pick)
    }

    @Test fun no_wifi_shows_need_wifi() = runTest {
        val p = FakeNetplayPlatform(wifiInfo = null)
        val c = controller(p)
        c.open(entry("FC", "Contra (USA).nes"))
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertTrue(role.needWifi)
        c.press(NxButton.A)   // Host Game
        assertEquals(NetplayText.NEED_WIFI, assertIs<NetplayPage.Error>(c.page.value).text)
        c.press(NxButton.B)
        assertIs<NetplayPage.Role>(c.page.value)
        c.press(NxButton.DOWN, NxButton.A)   // Join Game
        assertEquals(NetplayText.NEED_WIFI, assertIs<NetplayPage.Error>(c.page.value).text)
        c.press(NxButton.A)
        c.press(NxButton.DOWN, NxButton.A)   // Host, Join, Wi-Fi settings
        assertEquals(1, p.wifiSettingsOpened)
        assertEquals(0, p.adverts.size)
        p.wifiInfo = WifiInfo("10.0.0.1", "Pixel")   // back from Android's settings, now on Wi-Fi
        c.press(NxButton.UP, NxButton.UP, NxButton.A)
        runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
    }

    @Test fun host_and_join_launch_both_sides() = runTest {
        val lan = FakeLan()
        val hostP = FakeNetplayPlatform(lan, WifiInfo("10.0.0.1", "Host Phone"))
        val clientP = FakeNetplayPlatform(lan, WifiInfo("10.0.0.2", "Client Phone"))
        val hostLaunches = Launches(); val clientLaunches = Launches()
        val host = controller(hostP, hostLaunches)
        val client = controller(clientP, clientLaunches)

        host.open(kart); host.press(NxButton.A); runCurrent()
        assertEquals(NetplayPage.Hosting("Super Mario Kart (USA)", "Host Phone  10.0.0.1", NetplayText.WAITING_PLAYER), host.page.value)
        assertEquals("Super Mario Kart (USA)", hostP.adverts.single().game)
        assertEquals(listOf(true), hostP.screenOn)

        client.open(kart); client.press(NxButton.DOWN, NxButton.A); runCurrent()
        assertEquals(NetplayPage.Hosts(emptyList(), 0, NetplayText.SEARCHING), client.page.value)
        clientP.found.emit(FoundHost("10.0.0.1", "Super Mario Kart (USA)")); runCurrent()
        assertEquals(listOf(FoundHost("10.0.0.1", "Super Mario Kart (USA)")), assertIs<NetplayPage.Hosts>(client.page.value).hosts)
        client.press(NxButton.A); runCurrent()

        assertEquals(listOf(NetplaySession(NetplayRole.HOST, "10.0.0.2", "snes9x", "snes9x")), hostLaunches.all.map { it.second })
        assertEquals(listOf(NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "snes9x", "snes9x")), clientLaunches.all.map { it.second })
        assertEquals(kart, hostLaunches.all.single().first)
        assertTrue(hostP.adverts.single().stopped)
        assertEquals(false, hostP.screenOn.last())
        assertEquals(0, clientP.browsing)
        assertNull(host.page.value); assertNull(client.page.value)
    }

    @Test fun join_different_game_asks_join_anyway() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A)
        val other = FoundHost("10.0.0.1", "Super Metroid (USA)")
        p.found.emit(other); runCurrent()
        c.press(NxButton.A)
        assertEquals(NetplayPage.JoinAnyway(other, NetplayText.joinAnyway("Super Metroid (USA)", "Super Mario Kart (USA)")), c.page.value)
        c.press(NxButton.B)
        assertIs<NetplayPage.Hosts>(c.page.value)
        assertTrue(p.connected.isEmpty())
        c.press(NxButton.A, NxButton.A); runCurrent()   // Join anyway
        assertEquals(listOf("10.0.0.1"), p.connected)
        val hello = assertNotNull(HelloCodec.parse(h.readLine(1000)))
        assertEquals(Hello.CLIENT, hello.role)
        assertTrue(hello.any)
        assertEquals("Super Mario Kart (USA)", hello.game)
    }

    @Test fun same_game_joins_without_asking() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A)
        p.found.emit(FoundHost("10.0.0.1", "super mario kart")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals(NetplayText.JOIN, assertIs<NetplayPage.Progress>(c.page.value).title)
        val hello = assertNotNull(HelloCodec.parse(h.readLine(1000)))
        assertEquals(Hello.CLIENT, hello.role)
        assertEquals(false, hello.any)
    }

    @Test fun refusal_returns_to_host_list_with_message() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A)
        val found = FoundHost("10.0.0.1", "Super Mario Kart (USA)")
        p.found.emit(found); runCurrent()
        c.press(NxButton.A); runCurrent()
        h.readLine(1000)
        h.writeLine("REJECT role"); runCurrent()
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals(NetplayText.reject(RejectReason.ROLE), err.text)
        assertIs<NetplayPage.Hosts>(err.back)
        assertTrue(cEnd.closed)
        c.press(NxButton.A)
        assertEquals(listOf(found), assertIs<NetplayPage.Hosts>(c.page.value).hosts)
    }

    @Test fun connect_failure_shows_connect_failed_after_three_tries() = runTest {
        val p = FakeNetplayPlatform().apply { connectTo = { throw NetplayIoException("timeout") } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A)
        p.found.emit(FoundHost("10.0.0.1", "Super Mario Kart (USA)")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals(NetplayText.CONNECTING, assertIs<NetplayPage.Progress>(c.page.value).line)
        advanceTimeBy(2 * NetplayController.CONNECT_RETRY_MS + 1); runCurrent()
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals(NetplayText.CONNECT_FAILED, err.text)
        assertIs<NetplayPage.Hosts>(err.back)
        assertEquals(List(3) { "10.0.0.1" }, p.connected)
    }

    @Test fun no_host_after_120_seconds() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A); runCurrent()
        assertEquals(1, p.browsing)
        advanceTimeBy(119_000)
        assertEquals(NetplayText.SEARCHING, assertIs<NetplayPage.Hosts>(c.page.value).empty)
        advanceTimeBy(2_000)
        assertEquals(NetplayText.NO_HOST, assertIs<NetplayPage.Hosts>(c.page.value).empty)
        c.press(NxButton.B); runCurrent()
        assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(0, p.browsing)
    }

    @Test fun host_cancel_closes_advertise_and_listener() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
        assertEquals(1, p.acceptsStarted)
        c.press(NxButton.B)
        assertTrue(p.adverts.single().stopped)
        assertEquals(false, p.screenOn.last())
        assertIs<NetplayPage.Role>(c.page.value)
        runCurrent()
        assertEquals(1, p.acceptsCancelled)
    }

    @Test fun close_while_hosting_stops_everything() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        c.close(); runCurrent()
        assertNull(c.page.value)
        assertTrue(p.adverts.single().stopped)
        assertEquals(listOf(true, false), p.screenOn)
        assertEquals(1, p.acceptsCancelled)
    }

    @Test fun host_turns_away_a_client_and_keeps_waiting() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        val (h, cEnd) = PipeChannel.pair()
        p.lan.pending.send(h)
        cEnd.writeLine("HELLO 1 x client"); runCurrent()
        assertEquals("REJECT version", cEnd.readLine(1000))
        runCurrent()
        assertTrue(h.closed)
        assertEquals(NetplayText.WAITING_PLAYER, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals(2, p.acceptsStarted)
        assertTrue(!p.adverts.single().stopped)
    }

    @Test fun a_failed_save_transfer_shows_for_1_5_s_then_waits_again() = runTest {
        val p = FakeNetplayPlatform().apply { hostSavesFor = { FakeHostSaves(files = listOf("nxsave.srm")) } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        val (h, cEnd) = PipeChannel.pair()
        p.lan.pending.send(h)
        cEnd.writeLine(HelloCodec.format(Hello.client("Super Mario Kart (USA)", false, "core=snes9x,tag=SFC"))); runCurrent()
        assertEquals("snes9x", HelloCodec.parse(cEnd.readLine(1000))!!.core)
        assertEquals("SYNC-READY 1", cEnd.readLine(1000))
        assertEquals("FILE nxsave.srm", cEnd.readLine(1000))
        assertEquals(NetplayText.SENDING, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals(listOf("10.0.0.2"), p.served.single().started)
        // the game's content goes along, so the host can name the save after an archive's inner file
        assertEquals(listOf(Triple("SFC", "Super Mario Kart (USA).sfc", kart.game.ref)), p.hostSavesCalls)
        cEnd.writeLine("SYNC-FAIL"); runCurrent()
        assertEquals(NetplayText.SAVE_TRANSFER_FAILED, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals("Save transfer failed - waiting for another player", NetplayText.SAVE_TRANSFER_FAILED)   // one line on the status
        assertTrue(h.closed)
        assertTrue(p.served.single().closed)
        advanceTimeBy(1_499)
        assertEquals(NetplayText.SAVE_TRANSFER_FAILED, assertIs<NetplayPage.Hosting>(c.page.value).status)
        advanceTimeBy(2)
        assertEquals(NetplayText.WAITING_PLAYER, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals(2, p.acceptsStarted)
        assertTrue(!p.adverts.single().stopped)
    }

    @Test fun a_save_read_failure_also_shows_save_transfer_failed() = runTest {
        val fake = FakeNetplayPlatform()
        val failing = object : com.nxredux.mobile.netplay.NetplayPlatform by fake {
            override suspend fun hostSaves(tag: String, romFileName: String, content: ContentRef): HostSaves = throw IllegalStateException("disk")
        }
        val c = NetplayController(backgroundScope, failing, { _, _ -> }, work = StandardTestDispatcher(testScheduler))
        c.open(kart); c.press(NxButton.A); runCurrent()
        val (h, cEnd) = PipeChannel.pair()
        fake.lan.pending.send(h)
        cEnd.writeLine(HelloCodec.format(Hello.client("Super Mario Kart (USA)", false, "core=snes9x,tag=SFC"))); runCurrent()
        assertEquals(NetplayText.SAVE_TRANSFER_FAILED, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals(Hello.HOST, HelloCodec.parse(cEnd.readLine(1000))!!.role)
        assertEquals("REJECT sync", cEnd.readLine(1000))   // the joiner is told the host could not share its saves
        assertTrue(h.closed)
        advanceTimeBy(1_501)
        assertEquals(NetplayText.WAITING_PLAYER, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals(2, fake.acceptsStarted)
    }

    @Test fun wifi_lost_while_hosting_stops_and_says_so() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        assertIs<NetplayPage.Hosting>(c.page.value)
        advanceTimeBy(5_000)
        assertIs<NetplayPage.Hosting>(c.page.value)   // still on Wi-Fi: polling changes nothing
        p.wifiInfo = null
        advanceTimeBy(NetplayController.WIFI_POLL_MS + 1); runCurrent()
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals(NetplayText.WIFI_LOST, err.text)
        assertIs<NetplayPage.Role>(err.back)
        assertTrue(p.adverts.single().stopped)
        assertEquals(false, p.screenOn.last())
        assertEquals(1, p.acceptsCancelled)
        c.press(NxButton.A)
        assertTrue(assertIs<NetplayPage.Role>(c.page.value).needWifi)
    }

    @Test fun wifi_lost_while_browsing_stops_and_says_so() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A); runCurrent()
        assertEquals(1, p.browsing)
        p.wifiInfo = null
        advanceTimeBy(NetplayController.WIFI_POLL_MS + 1); runCurrent()
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals(NetplayText.WIFI_LOST, err.text)
        assertIs<NetplayPage.Role>(err.back)
        assertEquals(0, p.browsing)
        advanceTimeBy(NetplayController.NO_HOST_MS)   // the no-host timer is gone too
        assertEquals(NetplayText.WIFI_LOST, assertIs<NetplayPage.Error>(c.page.value).text)
    }

    @Test fun wifi_lost_while_joining_closes_the_channel() = runTest {
        val (_, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(kart); c.press(NxButton.DOWN, NxButton.A)
        p.found.emit(FoundHost("10.0.0.1", "Super Mario Kart (USA)")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertIs<NetplayPage.Progress>(c.page.value)
        p.wifiInfo = null
        advanceTimeBy(NetplayController.WIFI_POLL_MS + 1); runCurrent()
        assertEquals(NetplayText.WIFI_LOST, assertIs<NetplayPage.Error>(c.page.value).text)
        assertTrue(cEnd.closed)
        assertEquals(0, p.browsing)
    }

    @Test fun a_pinned_emulator_sends_that_core() = runTest {
        val (h, cEnd) = PipeChannel.pair()
        val p = FakeNetplayPlatform().apply { connectTo = { cEnd } }
        val c = controller(p)
        c.open(sonic)
        c.press(NxButton.DOWN, NxButton.DOWN, NxButton.RIGHT, NxButton.RIGHT, NxButton.UP, NxButton.A)   // Emulator = Genesis Plus GX, then Join
        p.found.emit(FoundHost("10.0.0.1", "Sonic The Hedgehog (USA)")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals("core=genesis_plus_gx,tag=MD", HelloCodec.parse(h.readLine(1000))!!.caps)
    }

    @Test fun md_netplay_runs_picodrive_which_is_also_the_folder_core() = runTest {
        val lan = FakeLan()
        val hostP = FakeNetplayPlatform(lan, WifiInfo("10.0.0.1", "Host Phone"))
        val clientP = FakeNetplayPlatform(lan, WifiInfo("10.0.0.2", "Client Phone"))
        val hostLaunches = Launches(); val clientLaunches = Launches()
        val host = controller(hostP, hostLaunches)
        val client = controller(clientP, clientLaunches)
        host.open(sonic); host.press(NxButton.A); runCurrent()
        client.open(sonic); client.press(NxButton.DOWN, NxButton.A); runCurrent()
        clientP.found.emit(FoundHost("10.0.0.1", "Sonic The Hedgehog (USA)")); runCurrent()
        client.press(NxButton.A); runCurrent()
        assertEquals(listOf(NetplaySession(NetplayRole.HOST, "10.0.0.2", "picodrive", "picodrive")), hostLaunches.all.map { it.second })
        assertEquals(listOf(NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "picodrive", "picodrive")), clientLaunches.all.map { it.second })
    }

    @Test fun join_retries_once_with_the_hosts_core() = runTest {
        val (h1, c1) = PipeChannel.pair()
        val (h2, c2) = PipeChannel.pair()
        val ends = ArrayDeque(listOf(c1, c2))
        val p = FakeNetplayPlatform().apply { connectTo = { ends.removeFirst() } }
        val launches = Launches()
        val c = controller(p, launches)
        c.open(sonic); c.press(NxButton.DOWN, NxButton.A)
        p.found.emit(FoundHost("10.0.0.1", "Sonic The Hedgehog (USA)")); runCurrent()
        c.press(NxButton.A); runCurrent()
        assertEquals("picodrive", HelloCodec.parse(h1.readLine(1000))!!.core)
        h1.writeLine("REJECT core-genesis_plus_gx.GPGX"); runCurrent()
        assertEquals("core=genesis_plus_gx,tag=MD,auto=1", HelloCodec.parse(h2.readLine(1000))!!.caps)
        h2.writeLine("HELLO 2 Sonic${esc}The${esc}Hedgehog${esc}(USA) host 2 caps=core=genesis_plus_gx,tag=GPGX")
        h2.writeLine("START 2"); runCurrent()
        assertEquals("START", h2.readLine(1000)); runCurrent()
        assertEquals(listOf(NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "genesis_plus_gx", "picodrive")), launches.all.map { it.second })
        assertEquals(2, p.connected.size)
    }

    @Test fun host_turns_away_another_core_flashes_and_keeps_waiting() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(kart); c.press(NxButton.A); runCurrent()
        val (h, cEnd) = PipeChannel.pair()
        p.lan.pending.send(h)
        cEnd.writeLine("HELLO 2 Super${esc}Mario${esc}Kart${esc}(USA) client caps=core=fbneo,tag=FBN"); runCurrent()
        assertEquals("REJECT core-snes9x.SFC", cEnd.readLine(1000)); runCurrent()
        assertEquals("A player using FinalBurn Neo was turned away.", assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertTrue(h.closed)
        advanceTimeBy(NetplayController.NOTICE_MS + 1)
        assertEquals(NetplayText.WAITING_PLAYER, assertIs<NetplayPage.Hosting>(c.page.value).status)
        assertEquals(2, p.acceptsStarted)
        assertFalse(p.adverts.single().stopped)
    }

    @Test fun taps_select_rows_on_role_and_host_list() = runTest {
        val p = FakeNetplayPlatform()
        val c = controller(p)
        c.open(sonic)
        c.select(2)
        assertEquals(2, assertIs<NetplayPage.Role>(c.page.value).selected)
        c.select(9)   // no such row
        assertEquals(2, assertIs<NetplayPage.Role>(c.page.value).selected)
        c.select(1); c.press(NxButton.A)
        p.found.emit(FoundHost("10.0.0.3", "A")); p.found.emit(FoundHost("10.0.0.4", "B")); runCurrent()
        c.select(1)
        assertEquals(1, assertIs<NetplayPage.Hosts>(c.page.value).selected)
        p.found.emit(FoundHost("10.0.0.3", "A2")); runCurrent()   // a host seen again updates its row in place
        assertEquals(listOf(FoundHost("10.0.0.3", "A2"), FoundHost("10.0.0.4", "B")), assertIs<NetplayPage.Hosts>(c.page.value).hosts)
        assertEquals(1, assertIs<NetplayPage.Hosts>(c.page.value).selected)
    }

    @Test fun y_does_nothing_and_b_on_role_closes() = runTest {
        val c = controller(FakeNetplayPlatform())
        c.open(kart)
        val before = c.page.value
        c.press(NxButton.Y)
        assertEquals(before, c.page.value)
        c.press(NxButton.B)
        assertNull(c.page.value)
    }
}
