package com.nxredux.mobile.ui

import com.nxredux.mobile.library.GameEntry
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.netplay.FakeNetplayPlatform
import com.nxredux.mobile.netplay.NetplaySession
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.GamePath
import com.nxredux.mobile.ui.nx.NxButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

internal fun netplayEntry(tag: String, file: String): Listed {
    val s = SystemFolder(tag, tag, CoreRegistry.forTag(tag), emptyList())
    val g = GameEntry(ContentRef("Home/Roms/X ($tag)/$file"), file, Names.stripExtension(file), folder = ContentRef("Home/Roms/X ($tag)"), sourceId = GamePath.HOME, relativeDir = "Roms/X ($tag)")
    return Listed(s, g)
}

internal class NetplayLaunches { val all = ArrayList<Pair<Listed, NetplaySession>>() }

internal fun TestScope.netplayController(platform: FakeNetplayPlatform, launches: NetplayLaunches = NetplayLaunches()): NetplayController =
    // The handshakes' own context is a second dispatcher on the same scheduler: a real hop, still on virtual time.
    NetplayController(backgroundScope as CoroutineScope, platform, { e, s -> launches.all += e to s }, work = StandardTestDispatcher(testScheduler))

internal fun NetplayController.press(vararg buttons: NxButton) = buttons.forEach(::onButton)
