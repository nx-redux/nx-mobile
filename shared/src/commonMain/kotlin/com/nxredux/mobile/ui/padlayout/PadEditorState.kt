package com.nxredux.mobile.ui.padlayout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.settings.GroupTweak
import com.nxredux.mobile.settings.PadGroup
import com.nxredux.mobile.settings.PadLayout
import com.nxredux.mobile.settings.PadLayouts
import com.nxredux.mobile.ui.nx.PadGroupBoundsListener
import com.nxredux.mobile.ui.nx.clampTweak
import com.nxredux.mobile.ui.nx.placeGroup
import com.nxredux.mobile.ui.nx.snapToDefault
import kotlin.math.roundToInt

/**
 * The pad layout editor's state. The pads drawn in the editor report each group's default rect, its pad's bounds and
 * unit ([onGroup], root px); drags and the resize handle edit [layout], clamped like the pads clamp it. [besidePicture]:
 * the pad is the landscape halves beside the picture, whose groups stack in another order ([BesideHitOrder]).
 */
class PadEditorState(initial: PadLayout, besidePicture: Boolean = false) : PadGroupBoundsListener {
    private val hitOrder = if (besidePicture) BesideHitOrder else HitOrder
    private class Geom(val default: Rect, val bounds: Rect, val unitPx: Float)

    private val saved = initial
    private val geoms = mutableStateMapOf<PadGroup, Geom>()
    var layout by mutableStateOf(initial); private set
    var selected by mutableStateOf<PadGroup?>(null); private set
    /** "Discard changes?" is showing. */
    var confirmDiscard by mutableStateOf(false); private set

    val dirty: Boolean get() = layout != saved
    /** The groups this pad has (those that reported). */
    val groups: Set<PadGroup> get() = geoms.keys

    override fun onGroup(group: PadGroup, defaultInRoot: Rect, padInRoot: Rect, unitPx: Float, placedInRoot: Rect) {
        if (defaultInRoot.isEmpty) geoms.remove(group)   // the group left the pad
        else geoms[group] = Geom(defaultInRoot, padInRoot, unitPx)
    }

    private fun tweak(group: PadGroup) = layout.tweaks[group] ?: GroupTweak()

    fun rectOf(group: PadGroup): Rect? = geoms[group]?.let { g -> placeGroup(g.default, tweak(group), g.unitPx, g.bounds) }

    /**
     * The group under [p]: the topmost as the pads draw them ([HitOrder], [BesideHitOrder]), so the editor picks the
     * group a game's touch would reach where two overlap.
     */
    fun groupAt(p: Offset): PadGroup? = hitOrder.firstOrNull { rectOf(it)?.contains(p) == true }

    fun select(group: PadGroup?) { selected = group }

    private fun set(group: PadGroup, t: GroupTweak) {
        val g = geoms[group] ?: return
        layout = layout.with(group, clampTweak(g.default, t, g.unitPx, g.bounds))
    }

    /** [group] moved by [delta] px; ignored until its pad has reported a unit. */
    fun dragBy(group: PadGroup, delta: Offset) {
        val g = geoms[group] ?: return
        if (g.unitPx <= 0f) return
        val t = tweak(group)
        set(group, t.copy(dx = t.dx + delta.x / g.unitPx, dy = t.dy + delta.y / g.unitPx))
    }

    fun endDrag(group: PadGroup) {
        if (geoms[group] == null) return
        layout = layout.with(group, snapToDefault(tweak(group)))
    }

    fun scaleOf(group: PadGroup): Float = tweak(group).scale

    /** The selected group's tweak when its resize began: growing it against an edge pushes it in only for the drag. */
    private var resizeFrom: GroupTweak? = null

    fun beginResize() { resizeFrom = selected?.let(::tweak) }

    fun endResize() { resizeFrom = null }

    /**
     * The selected group's size, to the nearest 5 % within 70..150 %. During a resize ([beginResize]) it is placed from
     * where the resize began, so shrinking back after an edge pushed it in returns it to its spot.
     */
    fun setScale(scale: Float) {
        val group = selected ?: return
        val stepped = ((scale * 20f).roundToInt() / 20f).coerceIn(PadLayouts.MIN_SCALE, PadLayouts.MAX_SCALE)
        set(group, (resizeFrom ?: tweak(group)).copy(scale = stepped))
    }

    fun resetGroup() { selected?.let { layout = layout.without(it) } }

    fun resetAll() { layout = PadLayout.EMPTY }

    /**
     * The groups in a pair that overlaps where at least one of the two has been moved or resized. An untweaked pair is
     * the stock pad (the C-stick may cover a cluster's corner by design), so it is never flagged.
     */
    fun overlapping(): Set<PadGroup> {
        val rects = groups.mapNotNull { g -> rectOf(g)?.let { g to it } }
        return rects.flatMap { (a, ra) ->
            rects.filter { (b, rb) -> a != b && ra.overlaps(rb) && (a in layout.tweaks || b in layout.tweaks) }.map { a }
        }.toSet()
    }

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
}

/**
 * The portrait pad's groups topmost first, as it draws: the C-stick over the clusters, the clusters (the PS2's sticks with
 * them, clear of their d-pad / face boxes) over the shoulders, the shoulders over the system rows (the top row over
 * MENU's, which sits under everything with the NX mark, the touch shortcuts beside it).
 */
private val HitOrder = listOf(
    PadGroup.C_STICK, PadGroup.R_STICK, PadGroup.FACE, PadGroup.L_STICK, PadGroup.DPAD, PadGroup.R_SHOULDERS, PadGroup.SYSTEM_RIGHT, PadGroup.L_SHOULDERS, PadGroup.SYSTEM_LEFT,
    PadGroup.SHORTCUT_1, PadGroup.SHORTCUT_2,
)

/**
 * The landscape halves beside the picture topmost first, as they draw: the right half over the left (its view is added
 * last), and in each the bottom row over the rest (it comes last in the column), its touch shortcut over the system
 * group, then the d-pad / face cluster over the shoulders. The shortcut on the second row sits inside the system
 * group's rect (under STICK / SELECT, beside STYLUS / SWAP), so it must come first to be picked at all. No C-stick
 * group: here it is part of the face cluster.
 */
private val BesideHitOrder = listOf(
    PadGroup.SHORTCUT_2, PadGroup.SYSTEM_RIGHT, PadGroup.R_STICK, PadGroup.FACE, PadGroup.R_SHOULDERS,
    PadGroup.SHORTCUT_1, PadGroup.SYSTEM_LEFT, PadGroup.L_STICK, PadGroup.DPAD, PadGroup.L_SHOULDERS,
)

/**
 * The selected group's resize handle: the corner of [rect] nearest [centre] (the edit layer's middle), so it stays on
 * screen and clear of the edges whichever corner of the screen the group sits in.
 */
internal fun resizeHandle(rect: Rect, centre: Offset): Offset =
    listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).minBy { (it - centre).getDistanceSquared() }
