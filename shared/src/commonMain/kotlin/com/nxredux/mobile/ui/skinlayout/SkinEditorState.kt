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
import com.nxredux.mobile.skin.TweakRules
import kotlin.math.abs

/**
 * The skin layout editor's state: [placement] (the layout as placed for a [windowW] x [windowH] window, no tweaks) with
 * the player's [tweaks] (group id to tweak, picture px). What is drawn is always [placed] ([SkinTweaks.apply]), and what is
 * stored is where each group stands there: a drag or resize that a limit, the no-go zone, the window or another group
 * stops is kept as far as it got, so a drag back moves the group at once.
 *
 * It edits the skin's layout ([TweakRules.SKIN]) or the overlay's ([TweakRules.OVERLAY], Pad skin Off). The overlay
 * starts from the skin's tweaks ([skinBase]) and keeps a tweak of its own only for the groups the player edited there
 * ([owned]); the rest keep following the skin. [minTouchPx] is the smallest a control's hit box may be shrunk to.
 */
class SkinEditorState(
    private val placement: SkinPlacement, initial: Map<String, SkinTweak>, private val windowW: Float, private val windowH: Float,
    val rules: TweakRules = TweakRules.SKIN, private val minTouchPx: Float = 0f,
    private val skinBase: Map<String, SkinTweak> = emptyMap(), owned: Set<String> = emptySet(),
) {
    val groups: List<SkinGroup> = SkinGroups.of(placement.spec)

    /** Where no group may go ([SkinTweaks.noGo]), window px. */
    val noGo: List<PxRect> = SkinTweaks.noGo(placement, rules)

    private val home = SkinTweaks.boxes(placement)

    var tweaks: Map<String, SkinTweak> by mutableStateOf(achieved(initial)); private set

    /** The overlay groups with a tweak of their own (always empty for the skin). */
    var owned: Set<String> by mutableStateOf(if (rules == TweakRules.OVERLAY) owned else emptySet()); private set

    /** What a save stores: the skin's [tweaks]; the overlay's only for the [owned] groups (one sitting at home stores an identity tweak, so it stops following the skin). */
    fun saved(): Map<String, SkinTweak> =
        if (rules == TweakRules.SKIN) tweaks
        else owned.filter { id -> groups.any { it.id == id } }.associateWith { tweaks[it] ?: SkinTweak() }

    /** [initial] as [saved] gives it: what [dirty] compares with. */
    private val initialSaved = saved()
    var selected: String? by mutableStateOf(null); private set
    /** "Discard changes?" is showing. */
    var confirmDiscard by mutableStateOf(false); private set

    /** Something changed since [initial] (read back from the drawing, so equal within float noise). */
    val dirty: Boolean get() = saved().let { now -> now.keys != initialSaved.keys || now.any { (id, t) -> !t.near(initialSaved.getValue(id)) } }

    private var cache: Pair<Map<String, SkinTweak>, SkinPlacement>? = null

    /** [placement] with [tweaks] applied: what the editor draws and the game will. */
    fun placed(): SkinPlacement {
        cache?.let { (t, p) -> if (t == tweaks) return p }
        return SkinTweaks.apply(placement, tweaks, windowW, windowH, rules, minTouchPx).also { cache = tweaks to it }
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
        if (by.x == 0f && by.y == 0f) return
        var t = tweaks
        if (by.x != 0f) t = achieved(t.with(id) { it.copy(dx = it.dx + by.x / k) })
        if (by.y != 0f) t = achieved(t.with(id) { it.copy(dy = it.dy + by.y / k) })
        tweaks = t
        own(id)
    }

    /** The selected group's size times [factor], held to the room round it. */
    fun resize(factor: Float) {
        val id = selected ?: return
        if (!factor.isFinite() || factor <= 0f) return
        tweaks = achieved(tweaks.with(id) { it.copy(scale = it.scale * factor) })
        own(id)
    }

    /** The selected group's size now (1: as the layout has it). */
    fun scaleOf(id: String): Float = tweaks[id]?.scale ?: 1f

    /** The selected group back to the skin layout's own (the overlay: its skin tweak, and it follows the skin again). */
    fun resetGroup() {
        val id = selected ?: return
        val back = skinBase[id]
        tweaks = achieved(if (rules == TweakRules.OVERLAY && back != null) tweaks + (id to back) else tweaks - id)
        owned = owned - id
    }

    /** Every group back to the skin layout's own (the overlay: the skin's tweaks, none of its own). */
    fun resetAll() {
        tweaks = if (rules == TweakRules.OVERLAY) achieved(skinBase) else emptyMap()
        owned = emptySet()
    }

    /** An edited group is the overlay's own from now on. */
    private fun own(id: String) { if (rules == TweakRules.OVERLAY) owned = owned + id }

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
        val now = SkinTweaks.boxes(SkinTweaks.apply(placement, t, windowW, windowH, rules, minTouchPx))
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
