package com.nxredux.mobile.runner

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.nxredux.mobile.BuildConfig
import com.nxredux.mobile.settings.AndroidAppSettings
import com.nxredux.mobile.settings.Prefs
import com.swordfish.libretrodroid.NxLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What [SecondScreenRule] needs to know about a display. [type] is the hidden `Display.getType()` (null: unreadable). */
data class DisplayFacts(val id: Int, val type: Int?, val flags: Int, val on: Boolean, val name: String) {
    companion object {
        const val INTERNAL = 1
        const val EXTERNAL = 2
        const val WIFI = 3
        const val OVERLAY = 4
        const val VIRTUAL = 5
    }
}

/** Which displays may carry the second screen: built-in or wired ones that are on and not private. */
object SecondScreenRule {
    /** [debug] also admits the simulated (overlay) and virtual displays, so it can be tried on an emulator. */
    fun qualifies(f: DisplayFacts, debug: Boolean): Boolean {
        if (f.flags and Display.FLAG_PRIVATE != 0 || !f.on) return false
        return when (f.type) {
            DisplayFacts.INTERNAL, DisplayFacts.EXTERNAL -> true
            DisplayFacts.OVERLAY, DisplayFacts.VIRTUAL -> debug
            DisplayFacts.WIFI -> false
            else -> !f.name.contains("Wifi", ignoreCase = true) && !f.name.contains("Cast", ignoreCase = true)
        }
    }

    /**
     * A qualifying display that is part of the device (a dual-screen handheld's second panel): only [DisplayFacts.INTERNAL].
     * [debugInternal] (a debug build's switch) also counts the simulated (overlay) and virtual displays, for an emulator.
     */
    fun builtIn(f: DisplayFacts, debugInternal: Boolean): Boolean =
        qualifies(f, debug = debugInternal) && f.type in BUILT_IN_TYPES   // overlay and virtual qualify only with the switch

    private val BUILT_IN_TYPES = setOf(DisplayFacts.INTERNAL, DisplayFacts.OVERLAY, DisplayFacts.VIRTUAL)

    /**
     * The second screen's display id for a window on display [hostId]: the first qualifying [presentation] display that
     * is neither the window's own nor the default one, else [default] (the default display's facts, null: unreadable)
     * when the window is elsewhere (a monitor, DeX) and it qualifies. Null: none.
     */
    fun pick(presentation: List<DisplayFacts>, default: DisplayFacts?, hostId: Int, debug: Boolean): Int? {
        presentation.firstOrNull { it.id != hostId && it.id != Display.DEFAULT_DISPLAY && qualifies(it, debug) }?.let { return it.id }
        return default?.takeIf { hostId != Display.DEFAULT_DISPLAY && qualifies(it, debug) }?.id
    }
}

/**
 * The display a second-screen Presentation should go to: [current] is [SecondScreenRule.pick]'s for a window on
 * [hostDisplay] (the display the game is on now), kept up to date between [start] and [stop]; a move of that window to
 * another display calls [refresh]. A display [taken] by something the player keeps there is never picked. Every
 * display's facts are logged once, for device reports.
 */
class SecondScreens(
    context: Context,
    private val debug: Boolean = BuildConfig.DEBUG,
    private val hostDisplay: () -> Int = { Display.DEFAULT_DISPLAY },
    private val taken: (Int) -> Boolean = { false },
) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(DisplayManager::class.java)
    private val logged = HashSet<Int>()
    private var started = false
    private val _current = MutableStateFlow<Display?>(null)
    val current: StateFlow<Display?> = _current

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        manager.unregisterDisplayListener(listener)
        _current.value = null
    }

    /** [d]'s facts as the rule reads them. */
    fun facts(d: Display): DisplayFacts =
        DisplayFacts(d.displayId, typeOf(d), d.flags, d.state == Display.STATE_ON, d.name.orEmpty())

    /**
     * The built-in second display there now ([SecondScreenRule.builtIn]), read at once (no [start] needed): onboarding's
     * Bottom screen step. A debug build counts the emulator's simulated display too while the app's prefs hold
     * [DEBUG_INTERNAL_KEY] = true (set with run-as).
     */
    fun builtInNow(): Display? {
        val debugInternal = debug && Prefs.open(app, AndroidAppSettings.PREFS_NAME).getBoolean(DEBUG_INTERNAL_KEY, false)
        return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && SecondScreenRule.builtIn(facts(it), debugInternal) }
    }

    /** Picks again; nothing while stopped. Main thread. */
    fun refresh() {
        if (!started) return
        val shown = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
        val host = hostDisplay()
        // The default display is no presentation display: it is a candidate only for a window elsewhere.
        val default = if (host != Display.DEFAULT_DISPLAY) manager.getDisplay(Display.DEFAULT_DISPLAY) else null
        val all = shown + listOfNotNull(default)
        val facts = all.associateWith(::facts)
        for (f in facts.values) {
            if (logged.add(f.id)) NxLog.i(TAG, "display $f qualifies=${SecondScreenRule.qualifies(f, debug)}")
        }
        val id = SecondScreenRule.pick(shown.map { facts.getValue(it) }, default?.let { facts.getValue(it) }, host, debug)
        _current.value = all.firstOrNull { it.displayId == id && !taken(id) }
    }

    private companion object {
        const val TAG = "SecondScreens"
        const val DEBUG_INTERNAL_KEY = "debug_second_screen_internal"

        /** `Display.getType()` is hidden; any failure reads as unknown. */
        private val getType = runCatching { Display::class.java.getMethod("getType") }.getOrNull()

        fun typeOf(d: Display): Int? = runCatching { getType?.invoke(d) as? Int }.getOrNull()
    }
}
