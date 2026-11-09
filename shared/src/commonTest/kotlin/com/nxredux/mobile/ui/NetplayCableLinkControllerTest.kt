package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.CableLinkRefused
import com.nxredux.mobile.netplay.FakeLan
import com.nxredux.mobile.netplay.FakeNetplayPlatform
import com.nxredux.mobile.netplay.FoundHost
import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.WifiInfo
import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import com.nxredux.mobile.netplay.cablelink.MemoryCacheStore
import com.nxredux.mobile.netplay.cablelink.RomHash
import com.nxredux.mobile.netplay.usb.FakeUsbCable
import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.wizard.CableLinkFiles
import com.nxredux.mobile.netplay.wizard.CableLinkOffer
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NetplayCableLinkControllerTest {
    private fun entry(tag: String, file: String): Listed {
        val s = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())
        val g = GameEntry(ContentRef("Home/Roms/X ($tag)/$file"), file, Names.stripExtension(file), folder = ContentRef("Home/Roms/X ($tag)"), sourceId = GamePath.HOME, relativeDir = "Roms/X ($tag)")
        return Listed(s, g)
    }

    private val ruby = entry("GBA", "Pokemon Ruby (USA).gba")
    private val sapphire = entry("GBA", "Pokemon Sapphire (USA).gba")
    private val emerald = entry("MGBA", "Pokemon Emerald (USA).gba")
    private val kart = entry("SFC", "Super Mario Kart (USA).sfc")

    private class Launches { val all = ArrayList<Pair<Listed, NetplaySession>>() }

    private fun TestScope.controller(platform: FakeNetplayPlatform, launches: Launches = Launches()): NetplayController =
        NetplayController(backgroundScope as CoroutineScope, platform, { e, s -> launches.all += e to s }, work = StandardTestDispatcher(testScheduler))

    private fun NetplayController.press(vararg buttons: NxButton) = buttons.forEach(::onButton)

    private class Files(val own: ByteArray) : CableLinkFiles {
        override val cache = LinkRomCache(MemoryCacheStore())
        override suspend fun findRom(hash: String, sizeBytes: Long): String? = cache.find(hash)
        override suspend fun readOwnRom(chunk: suspend (ByteArray, Int) -> Unit) = chunk(own, own.size)
        override suspend fun ownSave(): ByteArray? = null
        override suspend fun writeOtherSave(bytes: ByteArray): String = "/session/other.sav"
    }

    private fun offerFor(rom: ByteArray) = CableLinkOffer(RomHash.sha1Hex(sequenceOf(rom)), rom.size.toLong(), null, Files(rom), clock = { 1_700_000_000L })

    private fun cablePlatform(lan: FakeLan = FakeLan(), wifi: WifiInfo = WifiInfo("10.0.0.1", "Pixel"), rom: ByteArray = ByteArray(2_000) { it.toByte() }) =
        FakeNetplayPlatform(lan, wifi).apply { cableLinkFor = { offerFor(rom) } }

    @Test fun cable_link_is_a_third_connection_for_gba_games_only() = runTest {
        val p = cablePlatform()
        val c = controller(p)
        c.open(ruby); runCurrent()
        val wifi = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.WIFI, wifi.connection)                    // a row now, even without a cable
        assertEquals(listOf(NetplayRoleRow.CONNECTION, NetplayRoleRow.HOST, NetplayRoleRow.JOIN, NetplayRoleRow.EMULATOR), wifi.rows)
        c.press(NxButton.UP, NxButton.RIGHT)
        val cable = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.CABLE_LINK_WIFI, cable.connection)
        assertEquals(listOf(NetplayRoleRow.CONNECTION, NetplayRoleRow.HOST, NetplayRoleRow.JOIN), cable.rows)   // always mGBA: no Emulator row
        assertEquals(NetplayLink.CABLE_LINK_WIFI, p.lastLink)
        c.press(NxButton.RIGHT)
        assertEquals(NetplayLink.WIFI, assertIs<NetplayPage.Role>(c.page.value).connection)

        // With a cable: Wi-Fi, USB Cable, Cable Link over Wi-Fi.
        val withUsb = cablePlatform().apply { usbCable = FakeUsbCable() }
        val u = controller(withUsb)
        u.open(ruby); runCurrent()
        u.press(NxButton.UP, NxButton.RIGHT)
        assertEquals(NetplayLink.USB, assertIs<NetplayPage.Role>(u.page.value).connection)
        u.press(NxButton.RIGHT)
        assertEquals(NetplayLink.CABLE_LINK_WIFI, assertIs<NetplayPage.Role>(u.page.value).connection)

        // Not for another console, whatever was picked last.
        val other = controller(cablePlatform().apply { lastLink = NetplayLink.CABLE_LINK_WIFI })
        other.open(kart)
        assertNull(assertIs<NetplayPage.Role>(other.page.value).connection)
    }

    @Test fun an_mgba_game_without_a_wifi_mode_offers_cable_link_without_a_cable() = runTest {
        val c = controller(cablePlatform())
        c.open(emerald); runCurrent()
        val role = assertIs<NetplayPage.Role>(c.page.value)
        assertEquals(NetplayLink.CABLE_LINK_WIFI, role.connection)          // not the cable-only error page
        assertEquals(listOf(NetplayRoleRow.CONNECTION, NetplayRoleRow.HOST, NetplayRoleRow.JOIN), role.rows)
    }

    @Test fun cable_link_host_and_join_launch_mgba_on_both_sides_for_two_games() = runTest {
        val lan = FakeLan()
        val hostP = cablePlatform(lan, WifiInfo("10.0.0.1", "Host Phone"), ByteArray(3_000) { (it * 7).toByte() }).apply { lastLink = NetplayLink.CABLE_LINK_WIFI }
        val clientP = cablePlatform(lan, WifiInfo("10.0.0.2", "Client Phone"), ByteArray(2_500) { (it * 3).toByte() }).apply { lastLink = NetplayLink.CABLE_LINK_WIFI }
        val hostLaunches = Launches(); val clientLaunches = Launches()
        val host = controller(hostP, hostLaunches)
        val client = controller(clientP, clientLaunches)
        val clientLines = ArrayList<String>()
        backgroundScope.launch { client.page.collect { (it as? NetplayPage.Progress)?.let { p -> clientLines += p.line } } }

        host.open(ruby); runCurrent()
        assertEquals(NetplayLink.CABLE_LINK_WIFI, assertIs<NetplayPage.Role>(host.page.value).connection)
        host.press(NxButton.A); runCurrent()                                // Host Game (below the Connection row)
        assertIs<NetplayPage.Hosting>(host.page.value)

        client.open(sapphire); runCurrent()
        client.press(NxButton.DOWN, NxButton.A); runCurrent()
        clientP.found.emit(FoundHost("10.0.0.1", "Pokemon Ruby (USA)")); runCurrent()
        client.press(NxButton.A); runCurrent()                              // another game: no "Join anyway?"

        val h = hostLaunches.all.single().second
        val g = clientLaunches.all.single().second
        assertEquals(NetplaySession(NetplayRole.HOST, "10.0.0.2", "mgba", "gpsp").withCableLink(
            com.nxredux.mobile.netplay.wizard.CableLinkSetup(h.otherRomPath, null, 1, "hle", 1_700_000_000L)), h)
        assertTrue(h.cableLink && g.cableLink)
        assertEquals("mgba", g.coreId)                                      // a GBA folder defaults to gpSP
        assertEquals("gpsp", g.folderCoreId)
        assertEquals(2, g.localSlot)
        assertTrue(h.otherRomPath.isNotEmpty() && g.otherRomPath.isNotEmpty())   // each received the other's game
        assertTrue(clientLines.any { it.startsWith(NetplayText.RECEIVING_GAME) || it.startsWith(NetplayText.SENDING_GAME) }, clientLines.toString())   // the game transfer replaces "Waiting for host to start..."
        assertNull(host.page.value); assertNull(client.page.value)
    }

    @Test fun a_game_that_cant_be_offered_says_why() = runTest {
        val p = FakeNetplayPlatform().apply { cableLinkFor = { throw CableLinkRefused(NetplayText.CABLE_LINK_ARCHIVE) }; lastLink = NetplayLink.CABLE_LINK_WIFI }
        val c = controller(p)
        c.open(ruby); runCurrent()
        c.press(NxButton.A); runCurrent()
        val err = assertIs<NetplayPage.Error>(c.page.value)
        assertEquals(NetplayText.CABLE_LINK_ARCHIVE, err.text)
        assertTrue(p.adverts.isEmpty())                                      // never hosted
        c.press(NxButton.B)
        assertIs<NetplayPage.Role>(c.page.value)
    }

    @Test fun transfer_text_counts_up() {
        assertEquals("Receiving game... 0%", NetplayText.transfer(sending = false, done = 0, total = 1_000))
        assertEquals("Sending game... 50%", NetplayText.transfer(sending = true, done = 500, total = 1_000))
        assertEquals("Sending game... 100%", NetplayText.transfer(sending = true, done = 1_000, total = 1_000))
    }
}
