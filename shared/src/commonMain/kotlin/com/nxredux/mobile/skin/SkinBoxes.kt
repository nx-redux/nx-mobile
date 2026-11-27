package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

/** A window's shape as a box size's [BoxWhen] reads it: [wDp] x [hDp], and whether the device is half-folded. */
data class DeviceShape(val wDp: Float, val hDp: Float, val halfFolded: Boolean) {
    val landscape: Boolean get() = wDp > hDp
}

/**
 * Where control boxes end the screen's room, window px ([SkinSolver.placePieces] roomLeft / roomRight / roomBottom):
 * null where the frame keeps its own margin.
 */
data class BoxRoom(val left: Float?, val right: Float?, val bottom: Float?)

/** Whether [s] is the device shape this size was drawn for. */
fun BoxWhen.matches(s: DeviceShape): Boolean =
    (orientation == null || (orientation == "landscape") == s.landscape) &&
        s.wDp in minWdp..maxWdp && s.hDp in minHdp..maxHdp &&
        (halfFolded == null || halfFolded == s.halfFolded)

/**
 * Layouts whose controls are fixed-size boxes ([SkinLayoutSpec.boxes]): each box placed whole by the row rules, the
 * screen in the room they leave. Nothing inside a box moves; a box only shrinks evenly when no size of it fits.
 */
object SkinBoxes {
    const val EDGE_DP = 32f
    const val GAP_DP = 16f
    const val MIN_GAP_DP = 4f
    /** The least a box shrinks to before the layout counts as not fitting (a smaller layout, or a smaller scale, is tried). */
    const val SHRINK_FLOOR = 0.75f

    /**
     * [box]'s size for [shape]: the one drawn for it ([BoxSize.when]); else the one drawn for the nearest device shape of the same orientation and fold (the nearest window in dp), so every layout
     * of a pack falls back to the same device's drawing; else the full one where it fits; else the largest that fits;
     * null when none does. A tagged size wins without [fits]: it was drawn for this window.
     */
    fun pick(box: BoxSpec, shape: DeviceShape, fits: (BoxSize) -> Boolean): BoxSize? {
        box.sizes.firstOrNull { it.`when`?.matches(shape) == true }?.let { return it }
        // (a size drawn for the other orientation, or folded the other way, never stands in)
        fun alike(t: BoxWhen) = (t.orientation == null || (t.orientation == "landscape") == shape.landscape) && (t.halfFolded == null || t.halfFolded == shape.halfFolded)
        fun far(t: BoxWhen) = kotlin.math.hypot((t.minWdp + minOf(t.maxWdp, 1e5f)) / 2f - shape.wDp, (t.minHdp + minOf(t.maxHdp, 1e5f)) / 2f - shape.hDp)
        // (it shrinks evenly after where it does not fit: one device's drawing for every layout of the pack)
        box.sizes.filter { it.`when`?.let(::alike) == true }.minByOrNull { far(it.`when`!!) }?.let { return it }
        val full = box.sizes.firstOrNull() ?: return null
        if (fits(full)) return full
        return box.sizes.filter(fits).maxByOrNull { it.w.toLong() * it.h }
    }

    /** The least screen width the boxes leave, dp (the main menu's least). */
    private const val MIN_SCREEN_DP = 320f

    /**
     * [spec] (a layout with [SkinLayoutSpec.boxes]) in a [windowW] x [windowH] window at [k] window px per picture px:
     * each box's size picked ([pick]) and the boxes placed by the row rules, the screen placed by [screen] (the caller's
     * [SkinSolver.placePieces], its hinge and frame rules) in the [BoxRoom] they leave, the boxes shrunk evenly where
     * they do not fit, down to [SHRINK_FLOOR]; null below that. Side boxes stand beside the screen sideways and beside
     * a fold board's lower screen, else under the screen; rows are centred across. The result's spec is the layout with
     * the boxes' pieces and controls as its own, so groups, tweaks and the switch work on it as on any layout.
     */
    fun place(
        spec: SkinLayoutSpec, windowW: Int, windowH: Int, k: Float, densityDpi: Int, hingeY: Float?,
        screen: (SkinLayoutSpec, BoxRoom) -> SkinPlacement?,
    ): SkinPlacement? {
        // a pack's box with no sizes passed over, a slot's first box only
        val boxes = spec.boxes.filter { it.sizes.isNotEmpty() }.distinctBy { it.slot }
        if (boxes.isEmpty() || k <= 0f || densityDpi <= 0) return null
        val dp = densityDpi / 160f
        val w = windowW.toFloat(); val h = windowH.toFloat()
        val edge = EDGE_DP * dp; val gap = GAP_DP * dp
        val shape = DeviceShape(w / dp, h / dp, hingeY != null)
        val foldBoard = spec.width >= 1500 && spec.tree.parts.any { it.isHinge }
        val beside = w > h || (spec.pieces.any { it.glass != null && it.anchor == PieceAnchor.CENTRE } && foldBoard)
        val minScreen = MIN_SCREEN_DP * dp
        // room across for both side boxes, and the height a box may take
        val sideRoom = if (beside) w - 2f * edge - 2f * gap - minScreen else w - 2f * edge - gap
        val tall = h - 2f * edge
        val sideCount = boxes.count { it.slot == BoxSlot.LEFT || it.slot == BoxSlot.RIGHT }
        fun fits(b: BoxSpec, z: BoxSize) = z.h * k <= tall && (b.slot !in setOf(BoxSlot.LEFT, BoxSlot.RIGHT) || z.w * k <= sideRoom / maxOf(1, sideCount))
        val picked = boxes.associate { b -> b.slot to (pick(b, shape) { fits(b, it) } ?: b.sizes.first()) }
        // a size with nothing in it: no box there on this device shape, unless it has a height (it then holds the
        // menu's room, so the screen stays the menu's size where the menu has caps in that row)
        val sizes = picked.filterValues { it.pieces.isNotEmpty() || it.controls.isNotEmpty() || it.h > 0 }
        fun size(s: BoxSlot) = sizes[s]
        val sides = listOf(BoxSlot.LEFT, BoxSlot.RIGHT).filter { it in sizes }
        // one shrink factor for all the boxes: across, the side boxes and the least screen; down, every box's height
        val needW = sides.sumOf { (sizes.getValue(it).w * k).toDouble() }.toFloat()
        val rowsH = listOfNotNull(size(BoxSlot.TOP_ROW), size(BoxSlot.BOTTOM_ROW)).sumOf { (it.h * k).toDouble() }.toFloat()
        val sideH = sides.maxOfOrNull { sizes.getValue(it).h * k } ?: 0f
        val needH = if (beside) maxOf(sideH, rowsH) else sideH + rowsH + minScreen
        val roomH = if (beside) tall else h - 2f * edge - gap * listOfNotNull(size(BoxSlot.TOP_ROW), size(BoxSlot.BOTTOM_ROW)).size - gap
        // sizes drawn for this device shape are its tuning: they never shrink
        val tuned = picked.values.all { it.`when`?.matches(shape) == true }
        val f = if (tuned) 1f else minOf(1f, if (needW > 0f) sideRoom / needW else 1f, if (needH > 0f) roomH / needH else 1f)
        if (f < SHRINK_FLOOR) return null
        fun bw(s: BoxSlot) = (size(s)?.w ?: 0) * k * f
        fun bh(s: BoxSlot) = (size(s)?.h ?: 0) * k * f
        // the boxes' lines: the bottom row on the bottom edge, the side boxes over it (under the screen) or beside the screen
        val bottomRowTop = size(BoxSlot.BOTTOM_ROW)?.let { h - edge - bh(BoxSlot.BOTTOM_ROW) }
        val sidesBottom = (bottomRowTop?.let { it - gap } ?: (h - edge))
        val sidesTop = sidesBottom - maxOf(bh(BoxSlot.LEFT), bh(BoxSlot.RIGHT))
        val topRowTop = size(BoxSlot.TOP_ROW)?.let { (if (beside) sidesTop else sidesTop - gap) - bh(BoxSlot.TOP_ROW) }
        val room = if (beside) BoxRoom(
            left = size(BoxSlot.LEFT)?.let { edge + bw(BoxSlot.LEFT) + gap },
            right = size(BoxSlot.RIGHT)?.let { w - edge - bw(BoxSlot.RIGHT) - gap },
            bottom = bottomRowTop?.let { it - gap },
        // under a screen: its room ends over the boxes (a layout with a screen piece of its own, the cover's touch
        // screen, keeps its frames' own rules)
        ) else BoxRoom(null, null, if (spec.pieces.any { it.glass != null }) null else (topRowTop ?: sidesTop) - gap)
        // the layout's own pieces and controls are for app versions without boxes (and its pickers): only its screens stay
        val bare = spec.copy(boxes = emptyList(), pieces = spec.pieces.filter { it.glass != null }, controls = emptyList())
        val base = screen(bare, room) ?: return null
        val frameBottom = base.pieces.filter { it.piece < 0 && !it.strip }.maxOfOrNull { it.dst.y + it.dst.height } ?: base.glasses.maxOfOrNull { it.rect.y + it.rect.height } ?: 0f
        val fold = if (foldBoard) (hingeY ?: (h / 2f)) else null
        // the control area: a fold board's half under the fold, else (sideways) the window's height; an upright phone's
        // boxes keep to the bottom (its screen takes the height they leave)
        val area: ClosedFloatingPointRange<Float>? = if (fold != null) fold..h else if (w > h) 0f..h else null
        /** [z]'s main pad's middle (box px): its own [BoxSize.mainY], else the stick or d-pad, the face diamond's four. */
        fun mainY(s: BoxSlot, z: BoxSize): Float? {
            z.mainY?.let { return it }
            // (a switch board's stick and d-pad are alternates on one spot: either will do)
            fun c(id: String) = z.controls.firstOrNull { it.id == id && it.alt == null } ?: z.controls.firstOrNull { it.id == id }
            return if (s == BoxSlot.LEFT) (c("STICK") ?: c("DPAD"))?.y
                else (listOf("X", "Y", "A", "B").mapNotNull(::c).takeIf { it.size == 4 } ?: listOf("CU", "CL", "CR", "CD").mapNotNull(::c).takeIf { it.size == 4 })
                    ?.map { it.y }?.average()?.toFloat()
        }
        /** A side box's top: its main pad (the stick, else the d-pad; the face buttons) centred in the area, kept on it. */
        fun centredTop(s: BoxSlot): Float {
            val bottomLine = sidesBottom - bh(s)
            val z = size(s) ?: return bottomLine
            val a = area ?: return bottomLine
            val main = mainY(s, z) ?: return bottomLine
            val s0 = k * f
            val top = z.pieces.minOfOrNull { it.y.toFloat() } ?: 0f
            // its foot: its lowest piece, or the word under a cap where that hangs lower
            val foot = maxOf(z.pieces.maxOfOrNull { it.y + it.h * it.scale } ?: z.h.toFloat(),
                z.controls.filter { it.labelH > 0f }.maxOfOrNull { it.y + it.labelDy + it.labelH / 2f } ?: 0f)
            var oy = (a.start + a.endInclusive) / 2f - main * s0
            // on the window: the box's lowest piece no nearer the bottom than the least gap (the row rules' 4 dp), its
            // highest under the fold
            oy -= maxOf(0f, oy + foot * s0 - (h - MIN_GAP_DP * dp))
            oy += maxOf(0f, (fold ?: 0f) - (oy + top * s0))
            return oy
        }
        // both sides lift alike where one must (its main pad then still level with the other's)
        val centred = listOf(BoxSlot.LEFT, BoxSlot.RIGHT).associateWith { centredTop(it) }
        val lift = if (area == null) 0f else listOf(BoxSlot.LEFT, BoxSlot.RIGHT).filter { it in sizes }.maxOfOrNull { s ->
            val z = sizes.getValue(s)
            val main = mainY(s, z) ?: return@maxOfOrNull 0f
            ((area.start + area.endInclusive) / 2f - main * k * f) - centred.getValue(s)
        } ?: 0f
        fun sideTop(s: BoxSlot): Float {
            val c = centred.getValue(s)
            if (area == null || s !in sizes) return c
            // the other side's lift too, where it lifted more (never past this one's own room above)
            val own = run {
                val main = mainY(s, sizes.getValue(s)) ?: return c
                ((area.start + area.endInclusive) / 2f - main * k * f) - c
            }
            val more = lift - own
            if (more <= 0f) return c
            val top = (sizes.getValue(s).pieces.minOfOrNull { it.y.toFloat() } ?: 0f) * k * f
            return maxOf((fold ?: 0f) - top, c - more)
        }
        fun origin(s: BoxSlot): Pair<Float, Float> = when (s) {
            BoxSlot.LEFT -> edge to sideTop(s)
            BoxSlot.RIGHT -> (w - edge - bw(s)) to sideTop(s)
            BoxSlot.BOTTOM_ROW -> (w - bw(s)) / 2f to bottomRowTop!!
            BoxSlot.TOP_ROW -> (w - bw(s)) / 2f to (if (beside) frameBottom + gap else topRowTop!!)
        }
        // the boxes on the window and under a folding board's fold: else this layout does not fit
        // (a smaller scale or another layout is tried, as the pieces of a layout without boxes are held to)
        for (s in sizes.keys) {
            val (ox, oy) = origin(s)
            val z = sizes.getValue(s)
            // what the box draws (its pieces; its own height may reach past them)
            val top = oy + (z.pieces.minOfOrNull { it.y.toFloat() } ?: 0f) * k * f
            val foot = oy + (z.pieces.maxOfOrNull { it.y + it.h * it.scale } ?: z.h.toFloat()) * k * f
            if (ox < -0.5f || top < -0.5f || ox + bw(s) > w + 0.5f || foot > h + 0.5f) return null
            if (fold != null && top < fold - 0.5f) return null
        }
        val anchorOf = mapOf(BoxSlot.LEFT to PieceAnchor.LEFT, BoxSlot.RIGHT to PieceAnchor.RIGHT, BoxSlot.TOP_ROW to PieceAnchor.CENTRE, BoxSlot.BOTTOM_ROW to PieceAnchor.CENTRE)
        val pieces = ArrayList(bare.pieces); val placedPieces = ArrayList(base.pieces)
        val controls = ArrayList<ControlSpec>(); val placedControls = ArrayList(base.controls)
        for (b in boxes) {
            val z = sizes[b.slot] ?: continue
            val (ox, oy) = origin(b.slot)
            val px = ox / k; val py = oy / k   // the box's corner, picture px
            val a = anchorOf.getValue(b.slot)
            for (p in z.pieces) {
                val q = p.copy(x = (px + p.x * f).toInt(), y = (py + p.y * f).toInt(), scale = p.scale * f, scaleX = if (p.scaleX > 0f) p.scaleX * f else 0f,
                    anchor = a, level = PieceLevel.TOP, inward = 0, drop = -1f)
                placedPieces += SkinPiece(PxRect(p.sx.toFloat(), p.sy.toFloat(), p.w.toFloat(), p.h.toFloat()),
                    PxRect(ox + p.x * f * k, oy + p.y * f * k, p.w * p.acrossScale * f * k, p.h * p.scale * f * k), alt = p.alt, knob = p.knob, knobOf = p.knobOf, piece = pieces.size)
                pieces += q
            }
            for (c in z.controls) {
                val q = c.scaled(f).copy(x = px + c.x * f, y = py + c.y * f, anchor = a, level = PieceLevel.TOP, inward = 0, drop = -1f)
                controls += q
                placedControls += PlacedControl(q.scaled(k), q.x * k, q.y * k)
            }
        }
        return base.copy(spec = bare.copy(pieces = pieces, controls = controls), pieces = placedPieces, controls = placedControls, boxed = true)
    }
}
