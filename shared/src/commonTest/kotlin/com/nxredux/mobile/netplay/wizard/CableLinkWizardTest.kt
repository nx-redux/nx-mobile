package com.nxredux.mobile.netplay.wizard

import com.nxredux.mobile.netplay.NetplayRole
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.netplay.NetplayText
import com.nxredux.mobile.netplay.PipeChannel
import com.nxredux.mobile.netplay.cablelink.LinkRomCache
import com.nxredux.mobile.netplay.cablelink.MemoryCacheStore
import com.nxredux.mobile.netplay.cablelink.RomHash
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CableLinkWizardTest {
    private val esc = "\u001f"
    private val rubyEsc = "Pokemon${esc}Ruby${esc}(USA)"

    private fun rom(seed: Int, size: Int = 3_000) = ByteArray(size) { (it * 31 + seed).toByte() }
    private fun hash(b: ByteArray) = RomHash.sha1Hex(sequenceOf(b))

    private val ruby = rom(1)
    private val sapphire = rom(2, 4_100)
    private val rubyHash = hash(ruby)
    private val sapphireHash = hash(sapphire)
    private val biosA = "a".repeat(40)
    private val biosB = "b".repeat(40)

    /** One phone's files: [library] maps hash to path, [own] is its game, [save] its battery save. */
    private class FakeFiles(val own: ByteArray, val save: ByteArray?, val library: Map<String, String> = emptyMap()) : CableLinkFiles {
        val store = MemoryCacheStore()
        override val cache = LinkRomCache(store)
        var romReads = 0
        val otherSaves = ArrayList<ByteArray>()
        override suspend fun findRom(hash: String, sizeBytes: Long): String? = library[hash] ?: cache.find(hash)
        override suspend fun readOwnRom(chunk: suspend (ByteArray, Int) -> Unit) {
            romReads++
            own.toList().chunked(1_000).forEach { part -> chunk(part.toByteArray(), part.size) }
        }
        override suspend fun ownSave(): ByteArray? = save
        override suspend fun writeOtherSave(bytes: ByteArray): String { otherSaves += bytes; return "/session/other.sav" }
    }

    private fun offer(rom: ByteArray, files: FakeFiles, bios: String? = null, clock: Long = 1_700_000_000L) =
        CableLinkOffer(hash(rom), rom.size.toLong(), bios, files, clock = { clock })

    private fun hostOffer(cl: CableLinkOffer?, game: String = "Pokemon Ruby (USA)") =
        HostOffer(game, "GBA", "gpsp", pick = null, usb = false, wl = "poke", cableLink = cl)

    private fun clientOffer(cl: CableLinkOffer?, game: String = "Pokemon Ruby (USA)", any: Boolean = false) =
        ClientOffer(game, any = any, tag = "GBA", core = "gpsp", pick = null, usb = false, wl = "poke", cableLink = cl)

    private data class Ends(val host: HostOutcome, val client: ClientOutcome)

    private suspend fun kotlinx.coroutines.test.TestScope.play(
        host: HostOffer, client: ClientOffer,
        hostProgress: MutableList<CableLinkProgress> = ArrayList(), clientProgress: MutableList<CableLinkProgress> = ArrayList(),
    ): Ends {
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, host, saves = { error("Cable Link copies saves itself") }, onTransfer = { hostProgress += it }) }
        val out = Wizard.client(c, client, FakeSaveSink(), onTransfer = { clientProgress += it })
        if (out !is ClientOutcome.Started) c.close()
        return Ends(hostJob.await(), out)
    }

    @Test fun peer_without_capability_is_refused_both_sides() = runTest {
        // Host offers Cable Link, the joiner picked plain Wi-Fi.
        val hostFiles = FakeFiles(ruby, null)
        val a = play(hostOffer(offer(ruby, hostFiles)), clientOffer(null))
        assertEquals(HostOutcome.Turned("cablelink"), a.host)
        assertEquals(ClientOutcome.Refused(NetplayText.CABLE_LINK_NEEDS_UPDATE), a.client)

        // The joiner offers it, the host does not.
        val b = play(hostOffer(null), clientOffer(offer(ruby, FakeFiles(ruby, null))))
        assertEquals(HostOutcome.Turned("cablelink"), b.host)
        assertEquals(ClientOutcome.Refused(NetplayText.CABLE_LINK_NEEDS_UPDATE), b.client)

        // An older peer (no cl=1) joining a Cable Link host gets the new reason.
        val (h, c) = PipeChannel.pair()
        val hostJob = async { Wizard.host(h, hostOffer(offer(ruby, FakeFiles(ruby, null))), saves = { error("none") }) }
        c.writeLine("HELLO 2 $rubyEsc client caps=core=gpsp,tag=GBA,wl=poke")
        assertEquals("REJECT cablelink", c.readLine(1000))
        assertEquals(HostOutcome.Turned("cablelink"), hostJob.await())

        // An older host answering a Cable Link joiner is told why.
        val (h2, c2) = PipeChannel.pair()
        val joinJob = async { Wizard.client(c2, clientOffer(offer(ruby, FakeFiles(ruby, null))), FakeSaveSink()) }
        assertEquals("core=mgba,tag=GBA,cl=1", HelloCodec.parse(h2.readLine(1000))!!.caps)
        h2.writeLine("HELLO 2 $rubyEsc host 2 caps=core=gpsp,tag=GBA,wl=poke")
        assertEquals(ClientOutcome.Refused(NetplayText.CABLE_LINK_NEEDS_UPDATE), joinJob.await())
        assertEquals("REJECT cablelink", h2.readLine(1000))
    }

    @Test fun same_dump_sends_nothing() = runTest {
        val hostFiles = FakeFiles(ruby, null, library = mapOf(rubyHash to "/roms/Ruby.gba"))
        val clientFiles = FakeFiles(ruby, null, library = mapOf(rubyHash to "/sd/GBA/Ruby.gba"))
        val hostProgress = ArrayList<CableLinkProgress>()
        val clientProgress = ArrayList<CableLinkProgress>()
        val e = play(hostOffer(offer(ruby, hostFiles)), clientOffer(offer(ruby, clientFiles)), hostProgress, clientProgress)
        assertEquals("/roms/Ruby.gba", assertIs<HostOutcome.Started>(e.host).cableLink!!.otherRomPath)
        assertEquals("/sd/GBA/Ruby.gba", assertIs<ClientOutcome.Started>(e.client).cableLink!!.otherRomPath)
        assertEquals(0, hostFiles.romReads + clientFiles.romReads)
        assertTrue(hostFiles.store.files.isEmpty() && clientFiles.store.files.isEmpty())
        assertTrue(hostProgress.isEmpty() && clientProgress.isEmpty())
    }

    @Test fun guest_missing_rom_receives_into_cache() = runTest {
        val hostFiles = FakeFiles(ruby, null, library = mapOf(sapphireHash to "/roms/Sapphire.gba"))
        val clientFiles = FakeFiles(sapphire, null)
        val clientProgress = ArrayList<CableLinkProgress>()
        val hostProgress = ArrayList<CableLinkProgress>()
        val e = play(
            hostOffer(offer(ruby, hostFiles)), clientOffer(offer(sapphire, clientFiles), game = "Pokemon Sapphire (USA)", any = true),
            hostProgress, clientProgress,
        )
        val c = assertIs<ClientOutcome.Started>(e.client).cableLink!!
        assertEquals("/mem/$rubyHash", c.otherRomPath)
        assertContentEquals(ruby, clientFiles.store.files[rubyHash])
        assertEquals("/roms/Sapphire.gba", assertIs<HostOutcome.Started>(e.host).cableLink!!.otherRomPath)
        assertEquals(0, clientFiles.romReads)
        assertEquals(CableLinkProgress(sending = false, done = ruby.size.toLong(), total = ruby.size.toLong()), clientProgress.last())
        assertEquals(CableLinkProgress(sending = true, done = ruby.size.toLong(), total = ruby.size.toLong()), hostProgress.last())
    }

    @Test fun both_sides_missing_roms_both_transfer() = runTest {
        val hostFiles = FakeFiles(ruby, null)
        val clientFiles = FakeFiles(sapphire, null)
        val e = play(hostOffer(offer(ruby, hostFiles)), clientOffer(offer(sapphire, clientFiles), game = "Pokemon Sapphire (USA)", any = true))
        assertEquals("/mem/$sapphireHash", assertIs<HostOutcome.Started>(e.host).cableLink!!.otherRomPath)
        assertEquals("/mem/$rubyHash", assertIs<ClientOutcome.Started>(e.client).cableLink!!.otherRomPath)
        assertContentEquals(sapphire, hostFiles.store.files[sapphireHash])
        assertContentEquals(ruby, clientFiles.store.files[rubyHash])
    }

    @Test fun corrupted_transfer_is_rejected() = runTest {
        val clientFiles = FakeFiles(sapphire, null)
        val (h, c) = PipeChannel.pair()
        val joinJob = async { Wizard.client(c, clientOffer(offer(sapphire, clientFiles), game = "x", any = true), FakeSaveSink()) }
        h.readLine(1000)
        h.writeLine("HELLO 2 $rubyEsc host 2 caps=core=mgba,tag=GBA,cl=1")
        h.writeLine("CABLE $rubyHash ${ruby.size} none")
        assertEquals("CABLE $sapphireHash ${sapphire.size} none", h.readLine(1000))
        assertEquals("NEED $rubyHash", h.readLine(1000))
        h.writeLine("NEED none")
        h.writeLine("ROM ${ruby.size}")
        val damaged = ruby.copyOf().also { it[100] = (it[100] + 1).toByte() }
        h.writeBytes(damaged, 0, damaged.size)
        assertEquals("ROM-BAD", h.readLine(1000))
        assertEquals(ClientOutcome.Refused(NetplayText.GAME_DAMAGED), joinJob.await())
        assertTrue(clientFiles.store.files.isEmpty())

        // A hash that is not one never reaches the cache.
        val (h2, c2) = PipeChannel.pair()
        val join2 = async { Wizard.client(c2, clientOffer(offer(sapphire, FakeFiles(sapphire, null))), FakeSaveSink()) }
        h2.readLine(1000)
        h2.writeLine("HELLO 2 $rubyEsc host 2 caps=core=mgba,tag=GBA,cl=1")
        h2.writeLine("CABLE ../../etc/passwd 3000 none")
        assertEquals(ClientOutcome.Refused(NetplayText.UNEXPECTED), join2.await())
    }

    @Test fun saves_swap_both_ways_and_none_is_allowed() = runTest {
        val hostSave = ByteArray(128 * 1024) { (it % 251).toByte() }
        val hostFiles = FakeFiles(ruby, hostSave)
        val clientFiles = FakeFiles(ruby, null)
        val e = play(hostOffer(offer(ruby, hostFiles)), clientOffer(offer(ruby, clientFiles)))
        assertEquals("/session/other.sav", assertIs<ClientOutcome.Started>(e.client).cableLink!!.otherSavePath)
        assertContentEquals(hostSave, clientFiles.otherSaves.single())
        assertNull(assertIs<HostOutcome.Started>(e.host).cableLink!!.otherSavePath)
        assertTrue(hostFiles.otherSaves.isEmpty())

        val clientSave = ByteArray(64 * 1024) { (it % 13).toByte() }
        val hostFiles2 = FakeFiles(ruby, hostSave)
        val clientFiles2 = FakeFiles(ruby, clientSave)
        val e2 = play(hostOffer(offer(ruby, hostFiles2)), clientOffer(offer(ruby, clientFiles2)))
        assertIs<HostOutcome.Started>(e2.host)
        assertIs<ClientOutcome.Started>(e2.client)
        assertContentEquals(clientSave, hostFiles2.otherSaves.single())
        assertContentEquals(hostSave, clientFiles2.otherSaves.single())
    }

    @Test fun bios_real_only_when_hashes_equal() = runTest {
        suspend fun modes(hostBios: String?, clientBios: String?): Pair<String, String> {
            val e = play(hostOffer(offer(ruby, FakeFiles(ruby, null), hostBios)), clientOffer(offer(ruby, FakeFiles(ruby, null), clientBios)))
            return assertIs<HostOutcome.Started>(e.host).cableLink!!.biosMode to assertIs<ClientOutcome.Started>(e.client).cableLink!!.biosMode
        }
        assertEquals("real" to "real", modes(biosA, biosA))
        assertEquals("hle" to "hle", modes(biosA, biosB))
        assertEquals("hle" to "hle", modes(biosA, null))
        assertEquals("hle" to "hle", modes(null, null))
    }

    @Test fun session_fields_set_for_host_and_guest() = runTest {
        val e = play(
            hostOffer(offer(ruby, FakeFiles(ruby, null), clock = 1_111L)),
            clientOffer(offer(ruby, FakeFiles(ruby, null), clock = 2_222L)),
        )
        val host = assertIs<HostOutcome.Started>(e.host)
        val client = assertIs<ClientOutcome.Started>(e.client)
        assertEquals("mgba", host.core)
        assertEquals("mgba", client.core)
        assertEquals(1, host.cableLink!!.localSlot)
        assertEquals(2, client.cableLink!!.localSlot)
        assertEquals(1_111L, host.cableLink!!.rtcEpoch)
        assertEquals(1_111L, client.cableLink!!.rtcEpoch)

        val hostSession = NetplaySession(NetplayRole.HOST, host.peer, host.core, "gpsp").withCableLink(host.cableLink!!)
        assertTrue(hostSession.cableLink)
        assertEquals(1, hostSession.localSlot)
        assertEquals(host.cableLink!!.otherRomPath, hostSession.otherRomPath)
        assertEquals(1_111L, hostSession.rtcEpoch)
        assertEquals("hle", hostSession.biosMode)
        val guestSession = NetplaySession(NetplayRole.CLIENT, "10.0.0.1", client.core, "gpsp").withCableLink(client.cableLink!!)
        assertEquals(2, guestSession.localSlot)
        assertNull(guestSession.otherSavePath)

        val plain = NetplaySession(NetplayRole.CLIENT, "10.0.0.1", "gpsp", "gpsp")
        assertEquals(false, plain.cableLink)
        assertEquals(2, plain.localSlot)
    }

    @Test fun cable_link_caps_and_reject_token_fit_the_handheld_grammar() {
        val caps = Caps.of("mgba", "MGBA", auto = true, wl = "poke", cableLink = true)
        assertEquals("core=mgba,tag=MGBA,cl=1", caps)
        assertTrue(Caps.isValid(caps))
        assertTrue(HelloCodec.parse("HELLO 2 $rubyEsc client caps=$caps")!!.cableLink)
        assertEquals(false, HelloCodec.parse("HELLO 2 $rubyEsc client caps=core=mgba,tag=MGBA,cl=0")!!.cableLink)
        assertEquals("REJECT cablelink", Reject(com.nxredux.mobile.netplay.RejectReason.CABLE_LINK).format())
        assertTrue("cablelink".length <= Reject.REASON_MAX)
        assertEquals(Reject(com.nxredux.mobile.netplay.RejectReason.CABLE_LINK), Reject.parse("REJECT cablelink"))
    }
}
