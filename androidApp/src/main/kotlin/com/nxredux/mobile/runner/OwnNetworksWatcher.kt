package com.nxredux.mobile.runner

import android.content.Context
import android.net.ConnectivityManager
import com.nxredux.mobile.netplay.OwnNetworks
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The DS link and this phone's own hotspot: turning one on or off tells no callback an app can register, so the
 * phone's interfaces are read every [TICK_MS] and the link is told of each change ([OwnNetworks]). Only while the game
 * is started: in the background it has left the room anyway.
 */
internal class OwnNetworksWatcher(private val owner: LifecycleOwner, context: Context) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                withContext(Dispatchers.IO) {
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
