package com.nxredux.mobile.ui.skinlayout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.skin.SkinGroup
import com.nxredux.mobile.skin.SkinGroups
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinTweaks
import kotlin.math.abs

/**
 * The skin layout editor's state: [placement] (the layout as placed for a [windowW] x [windowH] window, no tweaks) with
 * the player's [tweaks] (group id to tweak, picture px). What is drawn is always [placed] ([SkinTweaks.apply]), and what is
 * stored is where each group stands there: a drag or resize that a limit, the no-go zone, the window or another group
 * stops is kept as far as it got, so a drag back moves the group at once.
 */
class SkinEditorState(
    private val placement: SkinPlacement, initial: Map<String, SkinTweak>, private val windowW: Float, private val windowH: Float,
) {
    val groups: List<SkinGroup> = SkinGroups.of(placement.spec)

    /** Where no group may go ([SkinTweaks.noGo]), window px. */
    val noGo: List<PxRect> = SkinTweaks.noGo(placement)

    private val home = SkinTweaks.boxes(placement)

    /** [initial] as it lands: what [dirty] compares with. */
    private val saved = achieved(initial)

    var tweaks: Map<String, SkinTweak> by mutableStateOf(saved); private set
    var selected: String? by mutableStateOf(null); private set
    /** "Discard changes?" is showing. */
    var confirmDiscard by mutableStateOf(false); private set

    /** Something changed since [initial] (read back from the drawing, so equal within float noise). */
    val dirty: Boolean get() = tweaks.keys != saved.keys || tweaks.any { (id, t) -> !t.near(saved.getValue(id)) }

    private var cache: Pair<Map<String, SkinTweak>, SkinPlacement>? = null

    /** [placement] with [tweaks] applied: what the editor draws and the game will. */
    fun placed(): SkinPlacement {
        cache?.let { (t, p) -> if (t == tweaks) return p }
        return SkinTweaks.apply(placement, tweaks, windowW, windowH).also { cache = tweaks to it }
    }

    /** Each group's box as drawn now (pieces and controls' hit areas), window px. */
    fun boxOf(id: String): PxRect? = SkinTweaks.boxes(placed())[id]?.box

    /** The group whose drawn box holds [at] (the smallest where boxes overlap, the later one on a tie), or none. */
    fun groupAt(at: Offset): String? {
        val now = SkinTweaks.boxes(placed())
        return groups.reversed().mapNotNull { g -> now[g.id]?.box?.takeIf { it.holds(at) }?.let { g.id to it } }
            .minByOrNull { (_, r) -> r.width * r.height }?.first
    }

    /** Selects the group under [at] ([groupAt]); none there clears the selection. */
    fun select(at: Offset) { selected = groupAt(at) }

    /**
     * The selected group moved by [by] window px (picture px are window px over [SkinPlacement.k]). A diagonal drag
     * that stops against something slides along it: across, then down.
     */
    fun drag(by: Offset) {
        val id = selected ?: return
        val k = placement.k.takeIf { it > 0f } ?: return
        var t = tweaks
        if (by.x != 0f) t = achieved(t.with(id) { it.copy(dx = it.dx + by.x / k) })
        if (by.y != 0f) t = achieved(t.with(id) { it.copy(dy = it.dy + by.y / k) })
        tweaks = t
    }

    /** The selected group's size times [factor], held to [SkinTweaks.MIN_SCALE]..[SkinTweaks.MAX_SCALE] (and the room round it). */
    fun resize(factor: Float) {
        val id = selected ?: return
        if (!factor.isFinite() || factor <= 0f) return
        tweaks = achieved(tweaks.with(id) { it.copy(scale = it.scale * factor) })
    }

    /** The selected group's size now (1: as the layout has it). */
    fun scaleOf(id: String): Float = tweaks[id]?.scale ?: 1f

    fun resetGroup() {
        val id = selected ?: return
        tweaks = achieved(tweaks - id)
    }

    fun resetAll() { tweaks = emptyMap() }

    /**
     * Cancel / Back: true to leave now (nothing changed); else shows "Discard changes?" and returns false. While the
     * prompt shows it stays (false): only its Discard leaves.
     */
    fun requestCancel(): Boolean {
        if (confirmDiscard) return false
        if (!dirty) return true
        confirmDiscard = true
        return false
    }

    fun keepEditing() { confirmDiscard = false }

    private fun Map<String, SkinTweak>.with(id: String, f: (SkinTweak) -> SkinTweak): Map<String, SkinTweak> =
        this + (id to f(this[id] ?: SkinTweak()))

    /**
     * [t] as [SkinTweaks.apply] lands it: each group's move and size read back from where it is drawn (picture px), an
     * untouched group left out. Groups not in this layout are dropped.
     */
    private fun achieved(t: Map<String, SkinTweak>): Map<String, SkinTweak> {
        if (t.isEmpty()) return emptyMap()
        val now = SkinTweaks.boxes(SkinTweaks.apply(placement, t, windowW, windowH))
        val k = placement.k
        val out = LinkedHashMap<String, SkinTweak>()
        for (g in groups) {
            if (g.id !in t) continue
            val a = home[g.id] ?: continue
            val b = now[g.id] ?: continue
            // the box scales about its mid's centre, then moves: read the scale off its width, the move off its centre
            val s = if (a.box.width > 0f) b.box.width / a.box.width else 1f
            val mx = a.mid.x + a.mid.width / 2f; val my = a.mid.y + a.mid.height / 2f
            val dx = (b.box.x + b.box.width / 2f) - (mx + (a.box.x + a.box.width / 2f - mx) * s)
            val dy = (b.box.y + b.box.height / 2f) - (my + (a.box.y + a.box.height / 2f - my) * s)
            val tw = SkinTweak(dx / k, dy / k, s)
            if (abs(tw.dx) > EPS || abs(tw.dy) > EPS || abs(tw.scale - 1f) > EPS_SCALE) out[g.id] = tw
        }
        return out
    }

    private fun SkinTweak.near(o: SkinTweak) = abs(dx - o.dx) <= EPS && abs(dy - o.dy) <= EPS && abs(scale - o.scale) <= EPS_SCALE

    private fun PxRect.holds(p: Offset) = p.x >= x && p.x <= x + width && p.y >= y && p.y <= y + height

    private companion object {
        const val EPS = 0.01f        // picture px: less is no move
        const val EPS_SCALE = 0.0005f
    }
}
