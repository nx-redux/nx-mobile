package com.nxredux.mobile.netplay

import com.nxredux.mobile.netplay.usb.FakeUsbCable
import com.nxredux.mobile.netplay.usb.NetplayLink
import com.nxredux.mobile.netplay.usb.UsbCable
import com.nxredux.mobile.netplay.wizard.FakeHostSaves
import com.nxredux.mobile.netplay.wizard.FakeSaveSink
import com.nxredux.mobile.netplay.wizard.HostSaves
import com.nxredux.mobile.netplay.wizard.SaveSink
import com.nxredux.mobile.runner.ContentRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** The wire between fake platforms: a client's [FakeNetplayPlatform.connect] hands the host's [FakeNetplayPlatform.accept] its pipe end. */
class FakeLan {
    val pending = Channel<NetplayChannel>(Channel.UNLIMITED)
}

/**
 * A [NetplayPlatform] over [PipeChannel]: [accept] takes the host end of a pipe a client opened with [connect] (or one a
 * test put into [lan]), [connect] makes a [PipeChannel.pair] and returns its other end ([connectTo] overrides it).
 * [found] feeds [browse]; [wifi] is [wifiInfo]; every [hostSaves] call builds one [hostSavesFor] (kept in [served]).
 * [gbaLinkMode] answers [gbaMode] and records the file it was asked about in [probes].
 */
class FakeNetplayPlatform(
    val lan: FakeLan = FakeLan(),
    var wifiInfo: WifiInfo? = WifiInfo("10.0.0.1", "Pixel"),
) : NetplayPlatform {
    inner class FakeAdvert(val game: String) : Advert {
        var stopped = false
        override fun stop() { stopped = true }
    }

    val adverts = ArrayList<FakeAdvert>()
    val screenOn = ArrayList<Boolean>()
    var wifiSettingsOpened = 0
    var acceptsStarted = 0
    var acceptsCancelled = 0
    var browsing = 0
    val found = MutableSharedFlow<FoundHost>(replay = 64)
    var connectTo: (suspend (String) -> NetplayChannel)? = null
    val connected = ArrayList<String>()
    var hostSavesFor: () -> FakeHostSaves = { FakeHostSaves() }
    val served = ArrayList<FakeHostSaves>()
    /** Every [hostSaves] call's arguments. */
    val hostSavesCalls = ArrayList<Triple<String, String, ContentRef>>()
    var sink = FakeSaveSink()
    /** The host end's remoteAddress for this platform's connects (the address this device connects from). */
    var ownAddress = "10.0.0.2"
    var usbCable: FakeUsbCable? = null
    override val usb: UsbCable? get() = usbCable
    override var lastLink = NetplayLink.WIFI
    /** advertiseTo calls: game to target IP. */
    val unicast = ArrayList<Pair<String, String>>()
    val connectedOver = ArrayList<NetplayLink>()
    var gbaMode = "none"
    val probes = ArrayList<String>()

    override fun wifi(): WifiInfo? = wifiInfo
    override fun openWifiSettings() { wifiSettingsOpened++ }
    override fun advertise(game: String): Advert = FakeAdvert(game).also { adverts += it }
    override fun advertiseTo(game: String, ip: String): Advert = FakeAdvert(game).also { adverts += it; unicast += game to ip }
    override fun browse(): Flow<FoundHost> = found.onStart { browsing++ }.onCompletion { browsing-- }
    override suspend fun accept(): NetplayChannel {
        acceptsStarted++
        try {
            return lan.pending.receive()
        } catch (e: CancellationException) {
            acceptsCancelled++
            throw e
        }
    }
    override suspend fun connect(ip: String, link: NetplayLink): NetplayChannel {
        connected += ip
        connectedOver += link
        connectTo?.let { return it(ip) }
        val (h, c) = PipeChannel.pair(hostSees = ownAddress, clientSees = ip)
        lan.pending.send(h)
        return c
    }
    override suspend fun hostSaves(tag: String, romFileName: String, content: ContentRef): HostSaves {
        hostSavesCalls += Triple(tag, romFileName, content)
        return hostSavesFor().also { served += it }
    }
    override fun gbaLinkMode(romFileName: String, content: ContentRef): String {
        probes += romFileName
        return gbaMode
    }
    override fun clientSink(romFileName: String): SaveSink = sink
    override fun keepScreenOn(on: Boolean) { screenOn += on }
}
