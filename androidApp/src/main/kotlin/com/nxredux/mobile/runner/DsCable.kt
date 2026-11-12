package com.nxredux.mobile.runner

import android.app.Activity
import android.net.VpnService
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.nxredux.mobile.netplay.usb.DsCableAuto
import com.nxredux.mobile.settings.PrefsProvider
import com.swordfish.libretrodroid.LibretroNetplay
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * A DS game on the automatic route and the phone-to-phone USB cable: from the first time the game's wireless comes on,
 * the cable is started when another phone is on the port and the room search is told how far it is ([DsCableAuto]).
 * The cable itself is the menu process's ([PrefsProvider.usbCableAuto]); Android's one-time VPN consent is asked here,
 * over the game. The cable this started stops with the game.
 *
 * Made with the activity (it registers for the consent's result). Main thread.
 */
class DsCable(private val activity: ComponentActivity, private val scope: CoroutineScope) {
    private val auto = DsCableAuto()
    private var job: Job? = null
    @Volatile private var token: Long? = null
    private var consent: CompletableDeferred<Boolean>? = null
    private val askConsent = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        consent?.complete(r.resultCode == Activity.RESULT_OK)
    }

    /** The game's wireless is on: the cable is watched from here to the game's end. */
    fun wirelessOn() {
        if (job != null) return
        job = scope.launch { watch() }
    }

    private suspend fun watch() {
        val app = activity.applicationContext
        suspend fun ask(start: Boolean) = withContext(Dispatchers.IO) {
            runCatching { PrefsProvider.usbCableAuto(app, start) }.onFailure { NxLog.w(TAG, "no answer on the USB cable", it) }.getOrNull()
        }
        while (scope.isActive) {
            ask(start = false)?.let { answer ->
                var step = auto.step(answer.status, SystemClock.elapsedRealtime())
                if (step.start && consented()) ask(start = true)?.let { started ->
                    started.token?.let { token = it; NxLog.i(TAG, "DS link: USB cable started for this game") }
                    step = auto.step(started.status, SystemClock.elapsedRealtime())
                }
                LibretroNetplay.dsSetCable(step.cable.code)
            }
            delay(POLL_MS)
        }
    }

    /** Android's VPN consent (the cable's network is a VPN), asked once ever; false when the player says no. */
    private suspend fun consented(): Boolean {
        val intent = runCatching { VpnService.prepare(activity) }.getOrNull() ?: return true
        val answer = CompletableDeferred<Boolean>()
        consent = answer
        runCatching { askConsent.launch(intent) }.onFailure { answer.complete(false) }
        return answer.await().also { if (!it) auto.refuse() }
    }

    /** The game ends: the cable it started stops (one the sheet or Device Sync started is theirs). */
    fun close() {
        if (job == null) return
        job?.cancel()
        job = null
        LibretroNetplay.dsSetCable(DsCableAuto.Cable.NONE.code)
        val app = activity.applicationContext
        token?.let { t ->
            token = null
            thread(name = "usb-cable-stop") {
                runCatching { PrefsProvider.usbCableStopIf(app, t) }.onFailure { NxLog.w(TAG, "could not stop the USB cable", it) }
            }
        }
    }

    private companion object {
        const val TAG = "NxDsCable"
        const val POLL_MS = 1_000L
    }
}
