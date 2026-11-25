package com.nxredux.mobile

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.PadEvent
import com.swordfish.libretrodroid.NxLog

/**
 * The menu's refresh rate while buttons drive it. Android 15+'s adaptive refresh rate runs a window at its "normal"
 * category rate (60 Hz on the Galaxy Z Fold's cover screen) unless the screen is touched (touch boost), so a
 * controller's d-pad, a keyboard or a held direction would animate the menu at 60 fps while the same move by finger
 * runs at 120. From a button going down until [LINGER_MS] after the last one comes up, the window's
 * views ask for the display's top rate; otherwise they ask for nothing, so an idle menu (or a held button with nothing
 * moving: a vote only counts on frames that draw) is never held at the top rate. Main thread only.
 *
 * Android 11-14 (API 30-34) have no per-view vote, and some devices there (handhelds such as the Retroid Pocket Nova,
 * Android 13 at 120 Hz) give an app 60 Hz unless it asks. There the window's `preferredDisplayModeId` (the documented
 * app-side choice on those versions) names the mode with the top refresh rate at the display's current resolution
 * ([topRateModeId]: never a resolution switch), and goes back to 0 (no preference) when the boost ends. A finger counts
 * as a held button there too ([onTouch]): not every device boosts on touch by itself. Not `preferredRefreshRate`: it
 * may pick a different resolution group on displays that list several, and a mode id is exact. Not
 * `Surface.setFrameRate`: the window's own surface is not reachable from an app. A mode change can show a blank frame
 * on some panels, so on 30-34 the boost lingers [LINGER_LEGACY_MS] instead, keeping a menu in use at the top mode
 * between presses (a little battery for no 60/120 flip-flop while browsing). API 26-29: nothing. Games (GameActivity,
 * its own window) are left alone here: their view asks for the core's rate (GLRetroView).
 */
internal class FrameRateBoost(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val held = HashSet<NxButton>()
    private var touching = false
    private var boosted = false
    private val off = Runnable { apply(false) }

    /** A pad event on its way to the router: a press boosts, the last release lets go after the linger. */
    fun onPad(event: PadEvent) {
        when (event) {
            is PadEvent.Down -> {
                held += event.button
                press()
            }
            is PadEvent.Up -> {
                held -= event.button
                release()
            }
        }
    }

    /** A touch on the window (API 30-34 only; 35+ has the platform's touch boost): down boosts, up lets go after the linger. */
    fun onTouch(event: MotionEvent) {
        if (Build.VERSION.SDK_INT !in Build.VERSION_CODES.R until Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { touching = true; press() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { touching = false; release() }
        }
    }

    /** Back to no request at once (the activity stops: no frames to vote for, and no stale boost on return). */
    fun reset() {
        held.clear()
        touching = false
        handler.removeCallbacks(off)
        apply(false)
    }

    private fun press() {
        handler.removeCallbacks(off)
        apply(true)
    }

    private fun release() {
        if (held.isNotEmpty() || touching) return
        handler.removeCallbacks(off)
        handler.postDelayed(off, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) LINGER_MS else LINGER_LEGACY_MS)
    }

    private fun apply(boost: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || boost == boosted) return
        boosted = boost
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val rate = if (boost) topRate() else View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            setRate(activity.window.decorView, rate)
        } else {
            setMode(if (boost) topModeId() else 0)
        }
    }

    /** API 30-34: the window's preferred display mode, 0 for none. Only written when it changes (each write relayouts). */
    private fun setMode(id: Int) {
        val window = activity.window
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId == id) return
        attrs.preferredDisplayModeId = id
        window.attributes = attrs
        NxLog.i("NXAPP", "frame rate boost: preferredDisplayModeId=$id")
    }

    /** The mode with the display's top refresh rate at its current resolution; 0 (no preference) without a display. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun topModeId(): Int {
        val display = activity.display ?: return 0
        val current = display.mode
        return topRateModeId(display.supportedModes.map { it.spec() }, current.spec()) ?: 0
    }

    /** The display's highest refresh rate at its current resolution. */
    private fun topRate(): Float {
        val display = activity.display ?: return View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
        val mode = display.mode
        return display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .maxOfOrNull { it.refreshRate } ?: mode.refreshRate
    }

    /** Every view of the window asks for [rate]: the vote is taken from the views that draw (the Compose host). */
    private fun setRate(view: View, rate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        view.requestedFrameRate = rate
        if (view is ViewGroup) for (i in 0 until view.childCount) setRate(view.getChildAt(i), rate)
    }

    companion object {
        /** How long the boost outlives the last release: the menu's longest motion (a 350 ms crossfade after a 150 ms settle). */
        const val LINGER_MS = 700L

        /**
         * API 30-34: the boost is a display mode switch, which some panels show as a blank frame, so it outlives the
         * last release long enough to cover the pauses of a menu in use (reading a row, a press after a look).
         */
        const val LINGER_LEGACY_MS = 3_000L
    }
}

/** A display mode as [topRateModeId] reads it: [android.view.Display.Mode] without the Android types (unit tests). */
internal data class ModeSpec(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

private fun android.view.Display.Mode.spec() = ModeSpec(modeId, physicalWidth, physicalHeight, refreshRate)

/**
 * Of [modes], the id of the one with the highest refresh rate at [current]'s physical resolution (never another
 * resolution); [current] itself on a tie with it. Null when no mode matches the resolution (nothing to ask for).
 */
internal fun topRateModeId(modes: List<ModeSpec>, current: ModeSpec): Int? {
    val same = modes.filter { it.width == current.width && it.height == current.height }
    val top = same.maxOfOrNull { it.refreshRate } ?: return null
    if (current in same && current.refreshRate >= top) return current.id
    return same.first { it.refreshRate == top }.id
}
