package com.nxredux.mobile.runner

import android.os.SystemClock
import androidx.activity.ComponentActivity
import com.nxredux.mobile.ui.nx.InGameMenuModel
import com.swordfish.libretrodroid.NxLog
import com.swordfish.libretrodroid.Variable

/** What [Res3dController] needs from [GameActivity]: the game state it reads, the core options and the open menu it writes. */
internal interface Res3dHost {
    val activity: ComponentActivity
    val gameTag: String
    val gameCoreId: String
    val frontendValues: Map<String, String>
    val retroView: com.swordfish.libretrodroid.GLRetroView?
    val quitting: Boolean
    val netplayNow: com.nxredux.mobile.netplay.NetplaySession?
    val ndsView: com.nxredux.mobile.options.NdsView?
    /** melonds_render_mode this game started with, and whether OpenGL fell back to software. */
    val ndsRenderMode: String?
    val ndsFellBack: Boolean
    fun applyOptions(vars: List<Variable>)
    fun withMenu(block: (InGameMenuModel) -> Unit)
    fun withMenuIf(pred: (InGameMenuModel) -> Boolean, block: (InGameMenuModel) -> Unit)
}

/**
 * The DS / 3DS / PS2 3D resolution the app sets for the game ([Nds3dResolution], [N3ds3dResolution]): melonDS's GL scale,
 * Azahar's factor and ARMSX2's upscale follow the layout and the 3D Resolution rows. Owns the [Nds3dSender] and its
 * 100 ms drain; [host] keeps the core, the options and the menu. Main thread.
 */
internal class Res3dController(private val host: Res3dHost, private val renderer: FrontendRenderer) {
    private val sender = Nds3dSender()
    /** The 3D scale last sent to the core (the launch's included); null for another core. */
    var value: Int? = null
        private set
    private var current: Int? = null                   // the scale the DS / 3DS screens show now (FrontendRenderer.onNds3dLayout)
    /** The most any layout reaches on the displays in use; null before the first layout. */
    var displayMax: Int? = null
        private set
    private val tick = Runnable { tick() }

    /** The console whose 3D scale the app sets for this game: a DS on melonDS DS, a 3DS on Azahar, a PS2 on ARMSX2; null for any other. */
    val console: Res3dConsole? get() = Res3dConsole.forTag(host.gameTag)?.takeIf { host.gameCoreId == it.coreId }

    fun applies(): Boolean = console != null

    fun onLayout(current: Int?, displayMax: Int, sizeChange: Boolean) {
        if (!applies()) return
        this.current = current
        this.displayMax = displayMax
        // An open menu's Max Scale / Scale lists follow a rotation or fold.
        host.withMenuIf({ it.displayMax3d != displayMax }) { it.displayMax3d = displayMax }
        // An open menu's DS / 3DS layout row follows a rotation or a controller hiding the pad.
        host.withMenuIf({ it.ndsView != host.ndsView }) { it.ndsView = host.ndsView }
        offer(sizeChange)
    }

    /**
     * A new DS / 3DS 3D scale can reach the core now: not under netplay (no core option changes mid-session, so nothing is
     * offered and the sender keeps the value the core has; [GameActivity.becomeLocal] offers again), not while quitting,
     * not once the view is gone.
     */
    private fun canSend(): Boolean = host.netplayNow == null && !host.quitting && host.retroView?.isAttachedToWindow == true

    /**
     * The 3D scale for the console's rows' values and the layout now, to the core if it changed: at once, or for a game area that
     * only changed size, once it has settled ([Nds3dSender]; [tick] drains it every 100 ms meanwhile).
     */
    fun offer(sizeChange: Boolean) {
        val console = console ?: return
        if (!canSend()) return
        val max = displayMax ?: return
        val value = Nds3dResolution.valueFor(host.frontendValues, current, max, console)
        sender.offer(value, sizeChange, SystemClock.uptimeMillis())?.let(::send)
        scheduleTick()
    }

    private fun tick() {
        if (!canSend()) return   // a held size waits; the next offer (or playLocally's) replaces it
        sender.tick(SystemClock.uptimeMillis())?.let(::send)
        scheduleTick()
    }

    private fun scheduleTick() {
        val root = host.activity.window?.decorView ?: return
        root.removeCallbacks(tick)
        if (sender.pending) root.postDelayed(tick, 100)
    }

    /** onDestroy: no drain after the screen. */
    fun cancelTick() {
        host.activity.window?.decorView?.removeCallbacks(tick)
    }

    /**
     * [value] to melonDS DS or Azahar (a locked option: only the app sets it), which rebuilds its surfaces on its next
     * frame; Azahar's frame size follows a frame or more later ([FrontendRenderer.onN3dsFactorSent]).
     */
    private fun send(value: Int) {
        val console = console ?: return
        this.value = value
        NxLog.i(TAG_LOG, "${host.gameTag} 3D resolution ${value}x (${Nds3dMode.parse(host.frontendValues[console.modeKey]).value})")
        host.applyOptions(listOf(Variable(console.coreKey, console.coreValue(value))))
        if (console == Res3dConsole.N3DS) renderer.onN3dsFactorSent(value)
        host.withMenu { publish(it) }
    }

    /**
     * The launch's DS / 3DS 3D scale (no screens placed yet: the window's display maximum stands in for the shown scale), as a
     * forced option over the locked default; the sender starts from it, so the first layout sends only a different value.
     * The 3DS frame's factor starts from it (or the locked default without one).
     */
    fun launchOption(): Map<String, String> {
        val console = console ?: return emptyMap()
        val shape = host.activity.windowShape()
        val max = console.displayMaxOf(shape.widthPx to shape.heightPx, shape.screenClass)
        if (max == null) {
            if (console == Res3dConsole.N3DS) CoreRegistry.forId(console.coreId)?.defaultOptions?.get(console.coreKey)?.toIntOrNull()?.let(renderer::startN3dsFactor)
            return emptyMap()
        }
        val value = Nds3dResolution.valueFor(host.frontendValues, null, max, console)
        sender.offer(value, sizeChange = false, SystemClock.uptimeMillis())
        this.value = value
        if (console == Res3dConsole.N3DS) renderer.startN3dsFactor(value)
        NxLog.i(TAG_LOG, "${host.gameTag} 3D resolution ${value}x at launch (${Nds3dMode.parse(host.frontendValues[console.modeKey]).value})")
        return mapOf(console.coreKey to console.coreValue(value))
    }

    /**
     * The in-game 3D Resolution rows' live state: the scale in use and whether the running game draws in software (the
     * mode it started with, or OpenGL's fallback; a Render Mode changed in the menu waits for the next launch).
     */
    fun publish(model: InGameMenuModel) {
        val console = console ?: return
        // The 3DS has no software renderer to fall back to (Software API hidden), nor has the PS2: their scale always applies.
        model.setNds3d(value, software = console == Res3dConsole.DS && NdsRenderer.rendersSoftware(host.ndsRenderMode, host.ndsFellBack))
    }
}
