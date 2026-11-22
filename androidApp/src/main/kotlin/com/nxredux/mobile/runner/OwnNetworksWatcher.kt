package com.nxredux.mobile.runner

import android.content.Context
import android.net.ConnectivityManager
import com.nxredux.mobile.netplay.OwnNetworks
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The DS link and this phone's own hotspot: turning one on or off tells no callback an app can register, so the
 * phone's interfaces are read every [TICK_MS] and the link is told of each change ([OwnNetworks]).
 */
internal class OwnNetworksWatcher(private val scope: CoroutineScope, context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            var last: List<OwnNetworks.Subnet>? = null
            while (isActive) {
                val now = runCatching { OwnNetworks.list(cm) }.getOrDefault(emptyList())
                if (now != last) {
                    last = now
                    LibretroNetplay.dsSetLocalNetworks(now.map { it.broadcast }.toIntArray(), now.map { it.mask }.toIntArray())
                    if (now.isNotEmpty()) NxLog.i(TAG, "DS link: this phone serves ${now.size} network(s) of its own (hotspot)")
                }
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TAG = "NxDsLink"
        const val TICK_MS = 2_000L
    }
}
