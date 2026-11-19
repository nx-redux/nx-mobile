package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** How far (a share of its window's dp) a baked size's device-shape tag reaches each way. */
const val BAKE_TOLERANCE = 0.08f

/** [pack] with [boxes] (layout id -> boxes) written into its layouts. */
fun withBoxes(pack: SkinPackManifest, boxes: Map<String, List<BoxSpec>>): SkinPackManifest =
    pack.copy(layouts = pack.layouts.map { l -> boxes[l.id]?.let { l.copy(boxes = it) } ?: l })

/**
 * [v]'s layouts (the main menu's for null) as boxes, from how they place today on each of [devices]: every control and
 * button piece sorted into a slot, each box drawn so the box solver puts it, and the screen in the room it leaves, where
 * today's placement has them. One size per device class, tagged with its shape; equal sizes merged.
 */
fun bake(pack: SkinPackManifest, v: SkinVariant?, devices: List<DeviceClass>): Map<String, List<BoxSpec>> {
    val target = v ?: SkinVariant.MENU
    val legacy = pack.copy(layouts = pack.layouts.map { if (it.variant == target) it.copy(boxes = emptyList()) else it })
    // layout id -> the sizes each class gave its slots (a slot it has nothing in: an empty size, no box there)
    val perLayout = LinkedHashMap<String, MutableList<Map<BoxSlot, BoxSize>>>()
    for (d in devices) {
        val p = placeFor(legacy, v, d) ?: continue
        val layout = legacy.layouts.first { it.id == p.spec.id }
        perLayout.getOrPut(layout.id) { ArrayList() } += boxesOf(p, layout, d)
    }
    return perLayout.mapValues { (_, classes) ->
        val slots = classes.flatMap { it.keys }.toSortedSet()
        slots.map { slot ->
            val sizes = ArrayList<BoxSize>()
            for (c in classes) {
                val tagged = c[slot] ?: BoxSize(0, 0, `when` = c.values.firstOrNull()?.`when`)
                // one size per device class: merging two classes' tags into one range would claim the windows between them
                if (sizes.none { sameDrawing(it, tagged) && it.`when` == tagged.`when` }) sizes += tagged
            }
            // the full size first: the biggest box
            BoxSpec(slot, sizes.sortedByDescending { it.w.toLong() * it.h })
        }
    }
}

/** The shoulders and their companions (the arcade's turbo caps beside them): each its own group. */
private val SHOULDERS = setOf("L", "R", "L1", "R1", "L2", "R2", "ZL", "ZR", "TURBO_A", "TURBO_B")
private val C_BUTTONS = setOf("CU", "CL", "CR", "CD")
private val FACE = setOf("A", "B", "X", "Y")

/**
 * Each of [buttons]' group id, by what it is rather than what it overlaps: the d-pad, the stick (its knob too), a
 * C-stick, the C buttons' diamond, the face buttons, each shoulder its own; other caps joined by the outline (a well)
 * they share. A group's id is its controls' ids sorted and joined with `+` (as [SkinGroups] names them).
 */
private fun buttonGroups(p: SkinPlacement, buttons: List<SkinPiece>): Map<SkinPiece, String> {
    fun holds(r: PxRect, c: PlacedControl) = c.x >= r.x && c.x <= r.x + r.width && c.y >= r.y && c.y <= r.y + r.height
    val controls = p.controls.filter { !it.spec.id.startsWith("TOUCH_") }
    fun inside(q: SkinPiece) = controls.filter { c -> holds(q.dst, c) && (q.alt == null || c.spec.alt == null || q.alt == c.spec.alt) }
    fun kind(q: SkinPiece): String {
        // (a switch's drawings, one per mode, are a cap in its well: the 3DS's stick / d-pad toggle)
        q.alt?.let { alt -> return if (inside(q).let { cs -> cs.isNotEmpty() && cs.all { it.spec.id in CAPS } }) "CAP" else alt.uppercase() }
        if (q.knob) return q.knobOf
        val ids = inside(q).map { it.spec.id }.toSet()
        return when {
            "DPAD" in ids -> "DPAD"
            "STICK" in ids -> "STICK"
            "CSTICK" in ids -> "CSTICK"
            ids.any { it in C_BUTTONS } -> "C"
            ids.any { it in FACE } && ids.all { it in FACE } -> "FACE"
            ids.size == 1 && ids.single() in SHOULDERS -> ids.single()
            else -> "CAP"
        }
    }
    val kinds = buttons.map { kind(it) }
    // (edge to edge counts: a well's end and its middle side by side)
    fun touch(a: PxRect, b: PxRect) = a.x <= b.x + b.width + 0.5f && b.x <= a.x + a.width + 0.5f && a.y < b.y + b.height && b.y < a.y + a.height
    val parent = IntArray(buttons.size) { it }
    fun root(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; return r }
    for (i in buttons.indices) for (j in i + 1 until buttons.size)
        if (kinds[i] == kinds[j] && kinds[i] !in SHOULDERS && touch(buttons[i].dst, buttons[j].dst)) parent[root(i)] = root(j)
    val out = HashMap<SkinPiece, String>()
    for (g in buttons.indices.groupBy { root(it) }.values) {
        val ids = g.flatMap { i -> inside(buttons[i]).map { it.spec.id } }.toSortedSet()
        val id = if (ids.isEmpty()) "${kinds[g.first()]}@${g.first()}" else ids.joinToString("+")
        g.forEach { out[buttons[it]] = id }
    }
    return out
}

/** Two drawings of a box the same within half a picture px (their tags aside). */
private fun sameDrawing(a: BoxSize, b: BoxSize): Boolean {
    fun near(x: Float, y: Float) = kotlin.math.abs(x - y) <= 0.5f
    return a.w == b.w && a.h == b.h && a.pieces.size == b.pieces.size && a.controls.size == b.controls.size &&
        a.pieces.zip(b.pieces).all { (p, q) -> p.sx == q.sx && p.sy == q.sy && p.x == q.x && p.y == q.y && near(p.scale, q.scale) && p.alt == q.alt } &&
        a.controls.zip(b.controls).all { (p, q) -> p.id == q.id && p.alt == q.alt && near(p.x, q.x) && near(p.y, q.y) && near(p.r, q.r) }
}

/** [p] (today's placement of [layout] on [d]) as one size per slot, each tagged with [d]'s shape. */
private fun boxesOf(p: SkinPlacement, layout: SkinLayoutSpec, d: DeviceClass): Map<BoxSlot, BoxSize> {
    val k = p.k
    val dp = d.dpi / 160f
    val w = d.w.toFloat(); val h = d.h.toFloat()
    val edge = SkinBoxes.EDGE_DP * dp; val gap = SkinBoxes.GAP_DP * dp
    val foldBoard = layout.pieces.any { it.glass != null && it.anchor == PieceAnchor.CENTRE } && layout.tree.parts.any { it.isHinge }
    val beside = w > h || foldBoard
    fun isScreen(q: SkinPiece) = q.piece < 0 || q.strip || p.spec.pieces[q.piece].glass != null
    val slices = p.pieces.filter { it.piece < 0 && !it.strip }.map { it.dst }
    // the screen the boxes' room ends: a fold board's lower one, else the frame
    val lower = p.pieces.filter { it.piece >= 0 && !it.strip && p.spec.pieces[it.piece].let { s -> s.glass != null && s.anchor == PieceAnchor.CENTRE } }.map { it.dst }
    val room = (if (foldBoard && lower.isNotEmpty()) lower else slices).let { r ->
        PxRect(r.minOf { it.x }, r.minOf { it.y }, r.maxOf { it.x + it.width } - r.minOf { it.x }, r.maxOf { it.y + it.height } - r.minOf { it.y })
    }
    val frameBottom = slices.maxOfOrNull { it.y + it.height } ?: p.glasses.maxOf { it.rect.y + it.rect.height }
    val buttons = p.pieces.filter { !isScreen(it) }
    val controls = p.controls.filter { !it.spec.id.startsWith("TOUCH_") }
    fun mid(r: PxRect) = (r.x + r.width / 2f) to (r.y + r.height / 2f)
    // the side elements' middle height: a centred one above it is the top row (under the screen), below it the bottom row
    val sideYs = (buttons.map { mid(it.dst) } + controls.map { it.x to it.y }).filter { kotlin.math.abs(it.first - w / 2f) >= 2f }.map { it.second }.sorted()
    val sideMid = sideYs.getOrElse(sideYs.size / 2) { h / 2f }
    fun slotAt(x: Float, y: Float): BoxSlot = when {
        kotlin.math.abs(x - w / 2f) < 2f -> if (beside) (if (y > room.y + room.height / 2f) BoxSlot.BOTTOM_ROW else BoxSlot.TOP_ROW)
            else (if (y < sideMid) BoxSlot.TOP_ROW else BoxSlot.BOTTOM_ROW)
        x < w / 2f -> BoxSlot.LEFT
        else -> BoxSlot.RIGHT
    }
    fun holds(r: PxRect, c: PlacedControl) = c.x >= r.x && c.x <= r.x + r.width && c.y >= r.y && c.y <= r.y + r.height
    // whole groups (as the controls editor has them: a well and its caps, a ring and its buttons, each shoulder alone)
    // go to one slot, by the group's middle
    val groupOf = buttonGroups(p, buttons)
    val groupSlot = buttons.groupBy { groupOf.getValue(it) }.values.flatMap { g ->
        val x0 = g.minOf { it.dst.x }; val x1 = g.maxOf { it.dst.x + it.dst.width }
        val y0 = g.minOf { it.dst.y }; val y1 = g.maxOf { it.dst.y + it.dst.height }
        val s = slotAt((x0 + x1) / 2f, (y0 + y1) / 2f)
        g.map { it to s }
    }.toMap()
    val pieceSlot = buttons.associateWith { groupSlot.getValue(it) }
    // a control goes with the smallest piece holding it
    val controlSlot = controls.associateWith { c ->
        buttons.filter { holds(it.dst, c) }.minByOrNull { it.dst.width * it.dst.height }?.let { pieceSlot.getValue(it) } ?: slotAt(c.x, c.y)
    }
    val bySlot = BoxSlot.entries.associateWith { s -> buttons.filter { pieceSlot[it] == s } to controls.filter { controlSlot[it] == s } }
        .filterValues { (ps, cs) -> ps.isNotEmpty() || cs.isNotEmpty() }
    fun extent(s: BoxSlot): PxRect {
        val (ps, cs) = bySlot.getValue(s)
        val xs = ps.flatMap { listOf(it.dst.x, it.dst.x + it.dst.width) } + cs.map { it.x }
        val ys = ps.flatMap { listOf(it.dst.y, it.dst.y + it.dst.height) } + cs.map { it.y }
        return PxRect(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
    }
    val e = bySlot.keys.associateWith { extent(it) }
    // the boxes' lines, as the solver draws them: rows and side boxes chosen so the room they leave is today's screen's
    val bottomRowTop = e[BoxSlot.BOTTOM_ROW]?.let { if (beside) room.y + room.height + gap else minOf(it.y, h - edge - 1f) }
    val sidesBottom = bottomRowTop?.let { it - gap } ?: (h - edge)
    val sideTop = listOfNotNull(e[BoxSlot.LEFT], e[BoxSlot.RIGHT]).minOfOrNull { it.y } ?: sidesBottom
    val sidesTop = if (beside) sideTop else if (BoxSlot.TOP_ROW in e) maxOf(sideTop, room.y + room.height + 2f * gap + 1f) else room.y + room.height + gap
    val rects = e.mapValues { (s, r) ->
        when (s) {
            BoxSlot.LEFT -> if (beside) PxRect(edge, r.y, room.x - gap - edge, sidesBottom - r.y)
                else PxRect(edge, sidesTop, maxOf(1f, r.x + r.width - edge), sidesBottom - sidesTop)
            BoxSlot.RIGHT -> if (beside) PxRect(room.x + room.width + gap, r.y, (w - edge) - (room.x + room.width + gap), sidesBottom - r.y)
                else PxRect(r.x, sidesTop, maxOf(1f, (w - edge) - r.x), sidesBottom - sidesTop)
            BoxSlot.TOP_ROW -> {
                val bw = 2f * maxOf(w / 2f - r.x, r.x + r.width - w / 2f)
                if (beside) PxRect((w - bw) / 2f, frameBottom + gap, bw, maxOf(1f, r.y + r.height - frameBottom - gap))
                else PxRect((w - bw) / 2f, room.y + room.height + gap, bw, sidesTop - gap - (room.y + room.height + gap))
            }
            BoxSlot.BOTTOM_ROW -> {
                val bw = 2f * maxOf(w / 2f - r.x, r.x + r.width - w / 2f)
                PxRect((w - bw) / 2f, bottomRowTop!!, bw, (h - edge) - bottomRowTop)
            }
        }
    }
    val tag = BoxWhen(
        orientation = if (w > h) "landscape" else "portrait",
        minWdp = w / dp * (1f - BAKE_TOLERANCE), maxWdp = w / dp * (1f + BAKE_TOLERANCE),
        minHdp = h / dp * (1f - BAKE_TOLERANCE), maxHdp = h / dp * (1f + BAKE_TOLERANCE),
        halfFolded = d.hingeY != null,
    )
    return rects.mapValues { (s, r) ->
        val (ps, cs) = bySlot.getValue(s)
        val ox = r.x; val oy = r.y
        BoxSize(
            w = Math.round(r.width / k), h = Math.round(r.height / k),
            pieces = ps.map { q ->
                val spec = p.spec.pieces[q.piece]
                val scale = q.dst.height / q.src.height / k
                val across = q.dst.width / q.src.width / k
                spec.copy(sx = q.src.x.toInt(), sy = q.src.y.toInt(), w = q.src.width.toInt(), h = q.src.height.toInt(),
                    x = Math.round((q.dst.x - ox) / k), y = Math.round((q.dst.y - oy) / k), scale = scale,
                    scaleX = if (kotlin.math.abs(across - scale) < 0.001f) 0f else across, glass = null, strip = null, grow = 0,
                    group = groupOf[q])
            },
            controls = cs.map { c ->
                c.spec.copy(x = (c.x - ox) / k, y = (c.y - oy) / k, r = c.spec.r / k, hw = c.spec.hw / k, hh = c.spec.hh / k, capR = c.spec.capR / k,
                    arm = c.spec.arm / k, labelDy = c.spec.labelDy / k, labelDx = c.spec.labelDx / k, labelH = c.spec.labelH / k,
                    travel = c.spec.travel / k, knobTravel = c.spec.knobTravel / k)
            },
            `when` = tag,
        )
    }
}

/** The pad drawn under another on the same side (the d-pad under the stick, a stick under the face buttons): this much of its size. */
const val LOWER_PAD = 0.85f

/** The pad groups ([buttonGroups] ids) that stack: the stick, the d-pad, the C-stick, the face buttons. */
private val PAD_GROUPS = setOf("STICK", "DPAD", "CSTICK", "A+B+X+Y")

/**
 * The pack's drawing rule for two pads on one side: each pad under another (overlapping it across) drawn at
 * [LOWER_PAD] of its size about its middle. A switch board's two (one spot, one shown at a time) are not stacked.
 */
fun lowerPadsSmaller(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    list.map { box -> if (box.slot != BoxSlot.LEFT && box.slot != BoxSlot.RIGHT) box else box.copy(sizes = box.sizes.map { z -> lowerPadSmaller(z) }) }
}

private fun lowerPadSmaller(z: BoxSize): BoxSize {
    data class Pad(val id: String, val x0: Float, val x1: Float, val y0: Float, val y1: Float) {
        val cx get() = (x0 + x1) / 2f; val cy get() = (y0 + y1) / 2f
    }
    val pads = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }.groupBy { it.group!! }.map { (id, ps) ->
        Pad(id, ps.minOf { it.x.toFloat() }, ps.maxOf { it.x + it.w * it.acrossScale }, ps.minOf { it.y.toFloat() }, ps.maxOf { it.y + it.h * it.scale })
    }.sortedBy { it.cy }
    if (pads.size < 2) return z
    val top = pads.first()
    // two large controls in a box: the lower one smaller (not one drawn smaller already: a nub, the Fold upright
    // 3DS's d-pad and C-stick)
    val lower = pads.drop(1).filter { it.x0 < top.x1 && top.x0 < it.x1 && it.cy > top.y1 - 1f &&
        (it.x1 - it.x0) > 0.9f * (top.x1 - top.x0) }
    if (lower.isEmpty()) return z
    var out = z
    for (pad in lower) {
        val ids = pad.id.split("+").toSet()
        fun sx(x: Float) = pad.cx + (x - pad.cx) * LOWER_PAD
        fun sy(y: Float) = pad.cy + (y - pad.cy) * LOWER_PAD
        out = out.copy(
            pieces = out.pieces.map { p -> if (p.group != pad.id || p.alt != null) p else p.copy(
                x = Math.round(sx(p.x.toFloat())), y = Math.round(sy(p.y.toFloat())), scale = p.scale * LOWER_PAD,
                scaleX = if (p.scaleX > 0f) p.scaleX * LOWER_PAD else 0f) },
            controls = out.controls.map { c -> if (c.id !in ids || c.alt != null) c else c.copy(
                x = sx(c.x), y = sy(c.y), r = c.r * LOWER_PAD, hw = c.hw * LOWER_PAD, hh = c.hh * LOWER_PAD, capR = c.capR * LOWER_PAD,
                arm = c.arm * LOWER_PAD, travel = c.travel * LOWER_PAD, knobTravel = c.knobTravel * LOWER_PAD) },
        )
    }
    return out
}

/**
 * The pack's drawing rule for a side with two pads stacked, lifted above the other side to fit them (the Dreamcast's
 * fold boards): moved down so its shoulder is level with the other's (the upper pad then level with the other side's
 * pad), the lower pad's gap under the upper one halved to make the room. Left as it is where the lower pad would then
 * end nearer the window's bottom than the inner gap.
 */
fun levelStackedSides(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    val left = list.firstOrNull { it.slot == BoxSlot.LEFT } ?: return@mapValues list
    val right = list.firstOrNull { it.slot == BoxSlot.RIGHT } ?: return@mapValues list
    val pairs = left.sizes.mapNotNull { l -> right.sizes.firstOrNull { it.`when` == l.`when` }?.let { l to it } }
    val newL = HashMap<BoxSize, BoxSize>(); val newR = HashMap<BoxSize, BoxSize>()
    for ((l, r) in pairs) {
        fun shoulder(z: BoxSize) = z.controls.filter { it.id in SHOULDERS && it.alt == null }.minOfOrNull { it.y }
        val sl = shoulder(l) ?: continue; val sr = shoulder(r) ?: continue
        // both boxes stand on one line: a shoulder's height above it is its y less the box's height
        val dy = (sr - r.h) - (sl - l.h)
        if (kotlin.math.abs(dy) < 1f) continue
        if (dy > 0f) levelled(l, dy)?.let { newL[l] = it } else levelled(r, -dy)?.let { newR[r] = it }
    }
    list.map { b -> when (b.slot) {
        BoxSlot.LEFT -> b.copy(sizes = b.sizes.map { newL[it] ?: it })
        BoxSlot.RIGHT -> b.copy(sizes = b.sizes.map { newR[it] ?: it })
        else -> b
    } }
}

/** [z] moved down by [dy], its lower stacked pad (and what is under it) by [dy] less half its gap; null without two stacked or off its foot. */
private fun levelled(z: BoxSize, dy: Float): BoxSize? {
    fun top(p: PieceSpec) = p.y.toFloat()
    fun foot(p: PieceSpec) = p.y + p.h * p.scale
    val pads = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }.groupBy { it.group!! }
        .map { (id, ps) -> Triple(id, ps.minOf { top(it) }, ps.maxOf { foot(it) }) }.sortedBy { it.second }
    if (pads.size < 2) return null
    val upper = pads[0]; val lower = pads[1]
    val gap = lower.second - upper.third
    if (gap <= 0f) return null
    val down = dy - gap / 2f
    // (the box's foot is the edge gap over the window's bottom: a pad may reach down to the inner gap over it, as the
    // rules let the d-pad end 16 dp up; picture px at the pack's 420 dpi)
    val reach = (SkinBoxes.EDGE_DP - SkinBoxes.GAP_DP) * 420f / 160f
    if (lower.third + down > maxOf(z.h + reach, lower.third) + 0.5f) return null
    fun moveFor(y: Float) = if (y >= lower.second - 0.5f) down else dy
    return z.copy(
        pieces = z.pieces.map { p -> moveFor(p.y + p.h * p.scale / 2f).let { m -> p.copy(y = Math.round(p.y + m)) } },
        controls = z.controls.map { c -> c.copy(y = c.y + moveFor(c.y)) },
    )
}

/**
 * The pack's drawing rule for the DS's side caps: a pair of caps with a third under it (SWAP under SELECT / START on
 * the right; MENU under MIC / SWAP on the left), the three in one row in one well at the pair's height, SWAP nearest
 * the screen; the pair's well cut to three (its ends and its flat middle, as the fold board's rows are), the third's own
 * well gone, the row from the box's inner edge outward at the pair's pitch, or less to fit the box.
 */
fun swapInTheRow(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    list.map { b ->
        // each side's three, from the window's edge in to the screen
        // (the 3DS's left: its stick / d-pad switch, MIC and MENU)
        val trios = when (b.slot) { BoxSlot.RIGHT -> listOf(listOf("START", "SELECT", "SWAP")); BoxSlot.LEFT -> listOf(listOf("MIC", "MENU", "SWAP"), listOf("STICK_SWAP", "MIC", "MENU")); else -> null }
        if (trios == null) b else b.copy(sizes = b.sizes.map { z -> trios.firstNotNullOfOrNull { rowOfThree(z, b.slot, it) } ?: z })
    }
}

private fun rowOfThree(z: BoxSize, slot: BoxSlot, outerToInner: List<String>): BoxSize? {
    // (the switch is two alternates on one spot: either stands for it)
    val caps = outerToInner.map { id -> z.controls.firstOrNull { it.id == id && it.alt == null } ?: z.controls.firstOrNull { it.id == id } ?: return null }
    // two level with each other (the pair), the third under them
    val pair = caps.filter { a -> caps.count { kotlin.math.abs(it.y - a.y) < 1f } == 2 }
    if (pair.size != 2) return null
    val third = caps.first { it !in pair }
    if (third.y <= pair[0].y + 1f) return null
    fun holds(p: PieceSpec, c: ControlSpec) = c.x >= p.x && c.x <= p.x + p.w * p.acrossScale && c.y >= p.y && c.y <= p.y + p.h * p.scale
    fun centredOn(p: PieceSpec, c: ControlSpec) = kotlin.math.abs(p.x + p.w * p.acrossScale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
    // the pair's well: its pieces holding either cap but neither's own (a pill cut in ends and a middle, or one piece)
    // (by place, not group: its ends and middle may carry different groups)
    val capPiece = caps.flatMap { c -> z.pieces.filter { centredOn(it, c) } }.toSet()
    val x0 = pair.minOf { it.x - 2f * it.r }; val x1 = pair.maxOf { it.x + 2f * it.r }
    val pairWell = z.pieces.filter { p -> p !in capPiece && kotlin.math.abs(p.y + p.h * p.scale / 2f - pair[0].y) < 2f &&
        p.x < x1 && p.x + p.w * p.acrossScale > x0 }
    // the whole pill its pieces were cut from: their span in the picture (the tallest alone may be either end)
    val well = pairWell.maxByOrNull { it.h * it.scale }?.let { t ->
        val same = pairWell.filter { it.sy == t.sy && it.h == t.h }
        val x0 = same.minOf { it.sx }
        t.copy(sx = x0, w = same.maxOf { it.sx + it.w } - x0, scaleX = 0f)
    } ?: return null
    val thirdWell = z.pieces.filter { p -> holds(p, third) && !centredOn(p, third) && p !in pairWell }
    val pairPitch = kotlin.math.abs(pair[1].x - pair[0].x)
    val ends = well.h * well.scale
    val pitch = minOf(pairPitch, (z.w - ends) / 2f).coerceAtLeast(2f * caps.maxOf { it.r })
    val inner = if (slot == BoxSlot.RIGHT) ends / 2f else z.w - ends / 2f
    val step = if (slot == BoxSlot.RIGHT) pitch else -pitch
    // from the screen side out: the last of [outerToInner] at the inner end
    val at = outerToInner.reversed().mapIndexed { i, id -> id to inner + i * step }.toMap()
    val y = pair[0].y
    val left = at.values.min() - ends / 2f
    val end = well.h / 2
    val mid = 2f * pitch / well.scale
    val top = Math.round(y - ends / 2f)
    val group = (caps.map { it.id }.sorted()).joinToString("+")
    val cut = listOf(
        well.copy(sx = well.sx, w = end, x = Math.round(left), y = top, scaleX = 0f),
        well.copy(sx = well.sx + end - 1, w = 2, x = Math.round(left + end * well.scale), y = top, scaleX = mid * well.scale / 2f),
        well.copy(sx = well.sx + well.w - end, w = end,
            x = Math.round(left + (end + mid) * well.scale), y = top, scaleX = 0f),
    ).map { it.copy(group = group) }
    val label = pair.first()
    return z.copy(
        pieces = cut + z.pieces.filter { it !in pairWell && it !in thirdWell }.map { p ->
            caps.firstOrNull { centredOn(p, it) }?.let { c ->
                val nx = at.getValue(c.id)
                p.copy(x = Math.round(nx - p.w * p.acrossScale / 2f), y = Math.round(y - p.h * p.scale / 2f), group = group)
            } ?: p
        },
        controls = z.controls.map { c -> if (caps.any { it.id == c.id && kotlin.math.abs(it.x - c.x) < 1f && kotlin.math.abs(it.y - c.y) < 1f })
            c.copy(x = at.getValue(c.id), y = y, labelDx = label.labelDx, labelDy = label.labelDy, labelH = label.labelH) else c },
    )
}

/**
 * The pack's drawing rule for the caps under a side's pad (its ring: the d-pad, the stick, the face buttons): a group
 * of them clearly further down moved up so its top row is the inner gap (16 dp) under the ring, the rows under it kept
 * as far apart.
 */
fun capsUnderPads(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    list.map { b -> if (b.slot != BoxSlot.LEFT && b.slot != BoxSlot.RIGHT) b else b.copy(sizes = b.sizes.map { capsUp(it) ?: it }) }
}

private fun capsUp(z: BoxSize): BoxSize? {
    val ring = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }
    if (ring.isEmpty()) return null
    val foot = ring.maxOf { it.y + it.h * it.scale }
    val caps = z.controls.filter { it.id in CAPS && it.alt == null && it.y > foot }
    // a row of caps (a lone cap keeps its own rule, the Dreamcast's START level with the column across; a column keeps
    // its level with the column across, the DS's tall shell)
    val topY = caps.minOfOrNull { it.y } ?: return null
    if (caps.count { kotlin.math.abs(it.y - topY) < 1f } < 2) return null
    val ids = caps.map { it.id }.toSet()
    val pieces = z.pieces.filter { p -> p.group != null && p.group!!.split("+").any { it in ids } && p.y >= foot - 0.5f }
    if (pieces.isEmpty()) return null
    val target = SkinBoxes.GAP_DP * 420f / 160f
    val gap = pieces.minOf { it.y }.toFloat() - foot
    // only a row clearly further than the gap (a board drawn smaller than its density keeps its own 16 dp)
    if (gap <= target * 1.5f) return null
    val dy = target - gap
    return z.copy(
        pieces = z.pieces.map { if (it in pieces) it.copy(y = Math.round(it.y + dy)) else it },
        controls = z.controls.map { if (it in caps) it.copy(y = it.y + dy) else it },
    )
}

/** The Fold 8's inner screen sideways, flat or half-folded (932 x 704 dp): its device-shape tag. */
private fun BoxWhen.fold8Sideways(): Boolean {
    val w = (minWdp + maxWdp) / 2f; val h = (minHdp + maxHdp) / 2f
    return orientation == "landscape" && kotlin.math.abs(w - 932f) < 12f && kotlin.math.abs(h - 704f) < 12f
}

/**
 * The pack's drawing for the 3DS on the Fold 8's inner screen sideways: down a side with two pads, the gap from
 * the upper pad to the lower one and from the lower one to the caps' row each halved. [isFor]: the layouts it is for.
 */
fun fold8SidewaysGapsHalved(boxes: Map<String, List<BoxSpec>>, isFor: (String) -> Boolean): Map<String, List<BoxSpec>> = boxes.mapValues { (id, list) ->
    if (!isFor(id)) return@mapValues list
    capRowsLevelOnFold8Sideways(list.map { b -> if (b.slot != BoxSlot.LEFT && b.slot != BoxSlot.RIGHT) b else b.copy(sizes = b.sizes.map { z ->
        if (z.`when`?.fold8Sideways() != true) return@map z
        data class Pad(val id: String, val y0: Float, val y1: Float)
        val pads = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }.groupBy { it.group!! }
            .map { (g, ps) -> Pad(g, ps.minOf { it.y.toFloat() }, ps.maxOf { it.y + it.h * it.scale }) }.sortedBy { it.y0 }
        if (pads.size < 2) return@map z
        val (upper, lower) = pads[0] to pads[1]
        val up1 = maxOf(0f, lower.y0 - upper.y1) / 2f
        val capPieces = z.pieces.filter { p -> p.group != null && p.group !in PAD_GROUPS && p.group!!.split("+").any { it in CAPS } && p.y >= lower.y1 - 0.5f }
        val capIds = capPieces.flatMap { it.group!!.split("+") }.filter { it in CAPS }.toSet()
        val up2 = up1 + (capPieces.minOfOrNull { it.y.toFloat() }?.let { maxOf(0f, it - lower.y1) / 2f } ?: 0f)
        val lowerIds = lower.id.split("+").toSet()
        z.copy(
            pieces = z.pieces.map { p -> when {
                p.group == lower.id -> p.copy(y = Math.round(p.y - up1))
                p in capPieces -> p.copy(y = Math.round(p.y - up2))
                else -> p } },
            controls = z.controls.map { c -> when (c.id) {
                in lowerIds -> c.copy(y = c.y - up1)
                in capIds -> c.copy(y = c.y - up2)
                else -> c } },
        )
    }) })
}

/**
 * Sideways (the Fold 8, a tablet): the caps' rows each side level on the window (the side boxes' main pads stand level: the stick,
 * the face's middle), the higher row down to the lower (the regular 3DS's SWAP / SELECT / START, under its face, down to
 * MIC / MENU under its d-pad) (the rows level, as the DS's).
 */
private fun capRowsLevelOnFold8Sideways(list: List<BoxSpec>): List<BoxSpec> {
    val l = list.firstOrNull { it.slot == BoxSlot.LEFT } ?: return list
    val r = list.firstOrNull { it.slot == BoxSlot.RIGHT } ?: return list
    val out = list.associate { it.slot to it.sizes.toMutableList() }
    for ((li, lz) in l.sizes.withIndex()) {
        if (lz.`when`?.orientation != "landscape") continue
        val ri = r.sizes.indexOfFirst { it.`when` == lz.`when` }.takeIf { it >= 0 } ?: continue
        val rz = r.sizes[ri]
        fun main(z: BoxSize, ids: List<String>) = z.controls.filter { it.id in ids && it.alt == null }.map { it.y }.average().toFloat()
        fun rowY(z: BoxSize) = z.controls.filter { it.id in CAPS }.maxOfOrNull { it.y }
        val ly = rowY(lz) ?: continue; val ry = rowY(rz) ?: continue
        val lo = ly - main(lz, listOf("STICK")); val ro = ry - main(rz, listOf("X", "Y", "A", "B"))
        // (a side without the pad itself, the cover's stick / d-pad switch: as it is)
        if (lo.isNaN() || ro.isNaN() || kotlin.math.abs(lo - ro) < 0.5f) continue
        fun down(z: BoxSize, dy: Float): BoxSize {
            val caps = z.controls.filter { it.id in CAPS }
            val ids = caps.map { it.id }.toSet()
            val moved = z.copy(pieces = z.pieces.map { p -> if (p.group != null && p.group !in PAD_GROUPS && p.group!!.split("+").any { it in ids }) p.copy(y = Math.round(p.y + dy)) else p },
                controls = z.controls.map { if (it.id in ids) it.copy(y = it.y + dy) else it })
            return moved.copy(h = maxOf(z.h, Math.round(moved.pieces.maxOf { it.y + it.h * it.scale })))
        }
        if (lo > ro) out.getValue(BoxSlot.RIGHT)[ri] = down(rz, lo - ro) else out.getValue(BoxSlot.LEFT)[li] = down(lz, ro - lo)
    }
    return list.map { b -> b.copy(sizes = out.getValue(b.slot).toList()) }
}

/**
 * The pack's drawing rule after the pads are set as the menu's ([padsAsTheMenu]): a side's row of caps a pad now covers
 * moved under the side's lowest pad by the inner gap (16 dp) (the regular 3DS's MIC / MENU, drawn between its stick and
 * d-pad, under its d-pad once the d-pad came up). [rowsUp]: layouts whose rows further down come up to the gap too.
 */
fun capsClearOfPads(boxes: Map<String, List<BoxSpec>>, rowsUp: (String) -> Boolean): Map<String, List<BoxSpec>> = boxes.mapValues { (id, list) ->
    list.map { b -> if (b.slot != BoxSlot.LEFT && b.slot != BoxSlot.RIGHT) b else b.copy(sizes = b.sizes.map { z ->
        capsOffPads(z) ?: (if (rowsUp(id)) capsUp(z) else null) ?: z
    }) }
}

private fun capsOffPads(z: BoxSize): BoxSize? {
    val ring = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }
    if (ring.isEmpty()) return null
    fun r(p: PieceSpec) = floatArrayOf(p.x.toFloat(), p.y.toFloat(), p.x + p.w * p.acrossScale, p.y + p.h * p.scale)
    fun meet(a: FloatArray, b: FloatArray) = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]
    val capPieces = z.pieces.filter { p -> p.group != null && p.group!!.split("+").any { it in CAPS } && p.group !in PAD_GROUPS }
    if (capPieces.none { c -> ring.any { meet(r(c), r(it)) } }) return null
    val ids = capPieces.flatMap { it.group!!.split("+") }.filter { it in CAPS }.toSet()
    val caps = z.controls.filter { it.id in ids }
    val foot = ring.maxOf { it.y + it.h * it.scale }
    val dy = foot + SkinBoxes.GAP_DP * 420f / 160f - capPieces.minOf { it.y }
    val moved = z.copy(pieces = z.pieces.map { if (it in capPieces) it.copy(y = Math.round(it.y + dy)) else it },
        controls = z.controls.map { if (it in caps) it.copy(y = it.y + dy) else it })
    return moved.copy(h = maxOf(z.h, Math.round(moved.pieces.maxOf { it.y + it.h * it.scale })))
}

/**
 * The pack's drawing rule for the DS's cover sideways (its column shell): SWAP over to the right with SELECT / START
 * (as the regular shell has it), set under them so [swapInTheRow] puts the three in a row; MENU up into SWAP's place
 * beside MIC, in their well, its own well gone.
 */
fun swapToTheRight(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    val left = list.firstOrNull { it.slot == BoxSlot.LEFT } ?: return@mapValues list
    val right = list.firstOrNull { it.slot == BoxSlot.RIGHT } ?: return@mapValues list
    val newL = HashMap<BoxSize, BoxSize>(); val newR = HashMap<BoxSize, BoxSize>()
    for (l in left.sizes) {
        val r = right.sizes.firstOrNull { it.`when` == l.`when` } ?: continue
        val mic = l.controls.firstOrNull { it.id == "MIC" } ?: continue
        val swap = l.controls.firstOrNull { it.id == "SWAP" } ?: continue
        val menu = l.controls.firstOrNull { it.id == "MENU" } ?: continue
        val sel = r.controls.firstOrNull { it.id == "SELECT" } ?: continue
        val st = r.controls.firstOrNull { it.id == "START" } ?: continue
        if (r.controls.any { it.id == "SWAP" } || kotlin.math.abs(mic.y - swap.y) > 1f || menu.y <= mic.y + 1f || kotlin.math.abs(sel.y - st.y) > 1f) continue
        fun holds(p: PieceSpec, c: ControlSpec) = c.x >= p.x && c.x <= p.x + p.w * p.acrossScale && c.y >= p.y && c.y <= p.y + p.h * p.scale
        fun centredOn(p: PieceSpec, c: ControlSpec) = kotlin.math.abs(p.x + p.w * p.acrossScale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
        val swapCap = l.pieces.firstOrNull { centredOn(it, swap) } ?: continue
        val menuCap = l.pieces.firstOrNull { centredOn(it, menu) } ?: continue
        val menuWell = l.pieces.filter { holds(it, menu) && !centredOn(it, menu) }
        val pairGroup = "MENU+MIC"
        newL[l] = l.copy(
            pieces = l.pieces.filter { it !== swapCap && it !in menuWell }.map { p ->
                when {
                    p === menuCap -> p.copy(x = Math.round(swap.x - p.w * p.acrossScale / 2f), y = Math.round(swap.y - p.h * p.scale / 2f), group = pairGroup)
                    p.group?.split("+")?.contains("SWAP") == true -> p.copy(group = pairGroup)
                    else -> p
                }
            },
            controls = l.controls.filter { it !== swap }.map { c -> if (c === menu) c.copy(x = swap.x, y = swap.y, labelDx = mic.labelDx, labelDy = mic.labelDy, labelH = mic.labelH) else c },
        )
        // under SELECT, a cap's height down: [swapInTheRow] then sets the three in a row
        val under = sel.y + 2f * swap.r + 10f
        newR[r] = r.copy(
            pieces = r.pieces + swapCap.copy(x = Math.round(sel.x - swapCap.w * swapCap.acrossScale / 2f), y = Math.round(under - swapCap.h * swapCap.scale / 2f), group = "SWAP"),
            controls = r.controls + swap.copy(x = sel.x, y = under),
        )
    }
    if (newL.isEmpty()) list else list.map { b -> when (b.slot) {
        BoxSlot.LEFT -> b.copy(sizes = b.sizes.map { newL[it] ?: it })
        BoxSlot.RIGHT -> b.copy(sizes = b.sizes.map { newR[it] ?: it })
        else -> b
    } }
}

/**
 * The pack's drawing rule for the DS's fold boards: the row of caps under the touch screen split to the sides, MIC and
 * MENU in a row on the left (MENU nearer the screen), SWAP, SELECT and START on the right (SWAP nearer the screen), each
 * the inner gap under its side's pad, in a well cut from the row's; the row gone, so the touch screen's frame reaches
 * down as the main menu's does; the side boxes as much taller, so their pads stay where the main menu has them.
 */
fun bottomRowToTheSides(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    val left = list.firstOrNull { it.slot == BoxSlot.LEFT } ?: return@mapValues list
    val right = list.firstOrNull { it.slot == BoxSlot.RIGHT } ?: return@mapValues list
    val row = list.firstOrNull { it.slot == BoxSlot.BOTTOM_ROW } ?: return@mapValues list
    val newL = HashMap<BoxSize, BoxSize>(); val newR = HashMap<BoxSize, BoxSize>(); val newB = HashMap<BoxSize, BoxSize>()
    val gap = SkinBoxes.GAP_DP * 420f / 160f
    for (b in row.sizes) {
        val ids = setOf("MIC", "MENU", "SWAP", "SELECT", "START")
        if (b.controls.count { it.id in ids } < 5) continue
        val l = left.sizes.firstOrNull { it.`when` == b.`when` } ?: continue
        val r = right.sizes.firstOrNull { it.`when` == b.`when` } ?: continue
        fun centredOn(p: PieceSpec, c: ControlSpec) = kotlin.math.abs(p.x + p.w * p.acrossScale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
        val caps = b.controls.filter { it.id in ids }
        // each cap's own piece: the smallest centred on it (the row's pill may be centred on the middle one)
        val capPieces = caps.mapNotNull { c -> b.pieces.filter { centredOn(it, c) }.minByOrNull { it.w * it.acrossScale } }
        // the row's pill: its widest piece that is no cap (whole, or an end of a cut well)
        val well = b.pieces.filter { it !in capPieces }.maxByOrNull { it.h * it.scale } ?: continue
        val pitch0 = caps.sortedBy { it.x }.zipWithNext().minOf { (a, c) -> c.x - a.x }
        // the boxes stand on one line the row's height and a gap lower now: as much taller, so they stand as they did
        val grow = Math.round(b.h + gap)
        fun side(z: BoxSize, order: List<String>, slot: BoxSlot): BoxSize {
            val ring = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }
            val foot = ring.maxOfOrNull { it.y + it.h * it.scale } ?: (z.h * 0.6f)
            // the inner gap as this board is drawn: the shoulder's over the ring (16 dp at the board's own scale, which a
            // dense screen draws smaller than its density)
            val shoulderFoot = z.pieces.filter { it.group in SHOULDERS }.maxOfOrNull { it.y + it.h * it.scale }
            val inner = shoulderFoot?.let { sf -> ring.minOfOrNull { it.y.toFloat() }?.minus(sf) }?.takeIf { it > 0f } ?: gap
            val ends = well.h * well.scale
            val y = foot + inner + ends / 2f
            val n = order.size
            val pitch = minOf(pitch0, (z.w - ends) / (n - 1)).coerceAtLeast(2f * caps.maxOf { it.r })
            val inEdge = if (slot == BoxSlot.RIGHT) ends / 2f else z.w - ends / 2f
            val step = if (slot == BoxSlot.RIGHT) pitch else -pitch
            val at = order.reversed().mapIndexed { i, id -> id to inEdge + i * step }.toMap()
            val leftEnd = at.values.min() - ends / 2f
            val end = well.h / 2
            val mid = (n - 1) * pitch / well.scale
            val top = Math.round(y - ends / 2f)
            val group = order.sorted().joinToString("+")
            val cut = listOf(
                well.copy(w = end, x = Math.round(leftEnd), y = top, scaleX = 0f, group = group),
                well.copy(sx = well.sx + end - 1, w = 2, x = Math.round(leftEnd + end * well.scale), y = top, scaleX = mid * well.scale / 2f, group = group),
                well.copy(sx = well.sx + well.w - end, w = end, x = Math.round(leftEnd + (end + mid) * well.scale), y = top, scaleX = 0f, group = group),
            )
            val mine = caps.filter { it.id in order }
            val pieces = capPieces.mapNotNull { p -> mine.firstOrNull { centredOn(p, it) }?.let { c ->
                p.copy(x = Math.round(at.getValue(c.id) - p.w * p.acrossScale / 2f), y = Math.round(y - p.h * p.scale / 2f), group = group) } }
            // the pads stay where the main menu has them (the baseline: no shift from the menu into the game), so the box
            // grows by what it lost under it and stands as it stood
            return z.copy(h = z.h + grow, pieces = z.pieces + cut + pieces, controls = z.controls + mine.map { it.copy(x = at.getValue(it.id), y = y) })
        }
        newL[l] = side(l, listOf("MIC", "MENU"), BoxSlot.LEFT)
        newR[r] = side(r, listOf("START", "SELECT", "SWAP"), BoxSlot.RIGHT)
        newB[b] = BoxSize(0, 0, `when` = b.`when`)
    }
    if (newB.isEmpty()) list else list.map { box -> when (box.slot) {
        BoxSlot.LEFT -> box.copy(sizes = box.sizes.map { newL[it] ?: it })
        BoxSlot.RIGHT -> box.copy(sizes = box.sizes.map { newR[it] ?: it })
        BoxSlot.BOTTOM_ROW -> box.copy(sizes = box.sizes.map { newB[it] ?: it })
        else -> box
    } }
}

/**
 * The pack's baseline rule: a game's side boxes moved whole so its pads stand where the main menu's do on the same
 * device ([menu]: the menu's boxes, sizes tagged for the same device classes): the left's upper pad (the stick, else the
 * d-pad) on the menu's d-pad, the right's face buttons (or the C buttons) on the menu's face buttons, so going from the
 * menu into a game shifts nothing. As they are where the menu has no box for the device.
 */
fun padsAsTheMenu(boxes: Map<String, List<BoxSpec>>, menu: Map<String, List<BoxSpec>>, familyOf: (String) -> SkinFamily?): Map<String, List<BoxSpec>> {
    // (only a board of the menu's own kind: a game's own shape there, the cover's large two-screen layout, keeps its pads)
    fun menuSize(family: SkinFamily?, slot: BoxSlot, w: BoxWhen?) = menu.filterKeys { familyOf(it) == family }.values
        .firstNotNullOfOrNull { l -> l.filter { it.slot == slot }.flatMap { it.sizes }.firstOrNull { it.`when` == w && w != null }?.let { it to l } }
    // where a layout's side boxes stand: over its bottom row (its height and the inner gap, 16 dp = 42 picture px at any
    // density) where it has one for the device
    fun lineOf(layout: List<BoxSpec>, w: BoxWhen?): Float = layout.firstOrNull { it.slot == BoxSlot.BOTTOM_ROW }?.sizes
        ?.firstOrNull { it.`when` == w && (it.pieces.isNotEmpty() || it.controls.isNotEmpty()) }?.let { it.h + SkinBoxes.GAP_DP * 420f / 160f } ?: 0f
    fun anchor(z: BoxSize, slot: BoxSlot, isMenu: Boolean): Pair<Float, Float>? {
        fun c(id: String) = z.controls.firstOrNull { it.id == id && it.alt == null }
        return if (slot == BoxSlot.LEFT) {
            (if (isMenu) c("DPAD") else listOfNotNull(c("STICK"), c("DPAD")).minByOrNull { it.y })?.let { it.x to it.y }
        } else {
            val face = listOf("X", "Y", "A", "B").mapNotNull(::c).takeIf { it.size == 4 } ?: if (isMenu) null else listOf("CU", "CL", "CR", "CD").mapNotNull(::c).takeIf { it.size == 4 }
            face?.let { f -> f.map { it.x }.average().toFloat() to f.map { it.y }.average().toFloat() }
        }
    }
    return boxes.mapValues { (id, list) -> list.map { b ->
        if (b.slot != BoxSlot.LEFT && b.slot != BoxSlot.RIGHT) b else b.copy(sizes = b.sizes.map { z ->
            val (m, menuLayout) = menuSize(familyOf(id), b.slot, z.`when`) ?: return@map z
            val (gx, gy) = anchor(z, b.slot, false) ?: return@map z
            val (mx, my) = anchor(m, b.slot, true) ?: return@map z
            // each box stands on its layout's line (over its bottom row, if any); across, the left from its left edge, the
            // right from its right
            val dy = (my - m.h - lineOf(menuLayout, z.`when`)) - (gy - z.h - lineOf(list, z.`when`))
            val dx = if (b.slot == BoxSlot.LEFT) mx - gx else (mx - m.w) - (gx - z.w)
            if (kotlin.math.abs(dx) < 1f && kotlin.math.abs(dy) < 1f) return@map z
            z.copy(pieces = z.pieces.map { it.copy(x = Math.round(it.x + dx), y = Math.round(it.y + dy)) },
                controls = z.controls.map { it.copy(x = it.x + dx, y = it.y + dy) })
        })
    } }
}

/**
 * The Fold 8 Ultra's inner screen upright, flat or half-folded (752 x 835 dp), or sideways half-folded (835 x 752 dp):
 * its device-shape tag.
 */
private fun BoxWhen.ultraUpright(): Boolean {
    val w = (minWdp + maxWdp) / 2f; val h = (minHdp + maxHdp) / 2f
    fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < 12f
    return (orientation == "portrait" && near(w, 752f) && near(h, 835f)) ||
        (orientation == "landscape" && halfFolded == true && near(w, 835f) && near(h, 752f))
}

/**
 * The pack's drawing for the Ultra's inner screen upright and sideways half-folded: two pads stacked on a side, the
 * lower one moved up by half its gap under the upper one (the board is short there: the d-pad otherwise sits low).
 */
fun ultraUprightLowerPadUp(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    list.map { b -> if (b.slot != BoxSlot.LEFT && b.slot != BoxSlot.RIGHT) b else b.copy(sizes = b.sizes.map { z ->
        if (z.`when`?.ultraUpright() != true) return@map z
        val pads = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }.groupBy { it.group!! }
            .map { (id, ps) -> Triple(id, ps.minOf { it.y.toFloat() }, ps.maxOf { it.y + it.h * it.scale }) }.sortedBy { it.second }
        if (pads.size < 2) return@map z
        val gap = pads[1].second - pads[0].third
        if (gap <= 0f) return@map z
        val up = gap / 2f
        val lowerTop = pads[1].second
        z.copy(pieces = z.pieces.map { p -> if (p.y + p.h * p.scale / 2f >= lowerTop - 0.5f) p.copy(y = Math.round(p.y - up)) else p },
            controls = z.controls.map { c -> if (c.y >= lowerTop - 0.5f) c.copy(y = c.y - up) else c })
    }) }
}

/** The Ultra's inner screen sideways, flat or half-folded (835 x 752 dp): its device-shape tag. */
private fun BoxWhen.ultraSideways(): Boolean {
    val w = (minWdp + maxWdp) / 2f; val h = (minHdp + maxHdp) / 2f
    return orientation == "landscape" && kotlin.math.abs(w - 835f) < 12f && kotlin.math.abs(h - 752f) < 12f
}

/** The Fold 8's inner screen upright, flat or half-folded (704 x 932 dp): its device-shape tag. */
private fun BoxWhen.fold8Upright(): Boolean {
    val w = (minWdp + maxWdp) / 2f; val h = (minHdp + maxHdp) / 2f
    return orientation == "portrait" && kotlin.math.abs(w - 704f) < 12f && kotlin.math.abs(h - 932f) < 12f
}

private fun BoxWhen.wdp() = (minWdp + maxWdp) / 2f

/**
 * The Ultra sideways: the d-pad where the user put the regular 3DS's with the controls editor (48 px right, 66 px up), the
 * New 3DS's C-stick level with it and moved as far in from the right (its gap to the touch screen the d-pad's).
 */
private fun sidewaysPadsUp(out: Map<BoxSlot, MutableList<BoxSize>>, sides: List<Pair<BoxSlot, Int>>) {
    val li = sides.firstOrNull { it.first == BoxSlot.LEFT }?.second ?: return
    val l0 = out.getValue(BoxSlot.LEFT)[li]
    val l = l0.copy(pieces = l0.pieces.map { if (it.group == "DPAD") it.copy(x = Math.round(it.x + 48.43f), y = Math.round(it.y - 66.28f)) else it },
        controls = l0.controls.map { if (it.id == "DPAD") it.copy(x = it.x + 48.43f, y = it.y - 66.28f) else it })
    out.getValue(BoxSlot.LEFT)[li] = l
    val ri = sides.firstOrNull { it.first == BoxSlot.RIGHT }?.second ?: return
    val r = out.getValue(BoxSlot.RIGHT)[ri]
    val cs = r.controls.firstOrNull { it.id == "CSTICK" } ?: return
    val dpad = l.controls.firstOrNull { it.id == "DPAD" && it.alt == null } ?: return
    // the boxes' main pads stand level on the window (the stick, the face's middle): the right box's px from the left's
    fun main(z: BoxSize, ids: List<String>) = z.controls.filter { it.id in ids && it.alt == null }.map { it.y }.average().toFloat()
    val dy = dpad.y + (main(r, listOf("X", "Y", "A", "B")) - main(l, listOf("STICK"))) - cs.y
    out.getValue(BoxSlot.RIGHT)[ri] = r.copy(pieces = r.pieces.map { if (it.group == "CSTICK") it.copy(x = Math.round(it.x - 48.43f), y = Math.round(it.y + dy)) else it },
        controls = r.controls.map { if (it.id == "CSTICK") it.copy(x = it.x - 48.43f, y = it.y + dy) else it })
}

/**
 * [out]'s right box for [tag] (the New 3DS's) as the regular 3DS's ([regular]'s): its face and R, ZR (L2's twin) beside R
 * as ZL beside L, level with it; its C-stick left out ([newCStickAsTheDpad] puts one back).
 */
private fun newRightAsTheRegular(out: Map<BoxSlot, MutableList<BoxSize>>, regular: List<BoxSpec>, tag: BoxWhen) {
    val ri = out[BoxSlot.RIGHT]?.indexOfFirst { it.`when` == tag }?.takeIf { it >= 0 } ?: return
    val li = out[BoxSlot.LEFT]?.indexOfFirst { it.`when` == tag }?.takeIf { it >= 0 } ?: return
    val reg = regular.firstOrNull { it.slot == BoxSlot.RIGHT }?.sizes?.firstOrNull { it.`when` == tag } ?: return
    val mine = out.getValue(BoxSlot.RIGHT)[ri]; val left = out.getValue(BoxSlot.LEFT)[li]
    fun c(z: BoxSize, id: String) = z.controls.firstOrNull { it.id == id && it.alt == null }
    val l = c(left, "L") ?: return; val zl = c(left, "L2") ?: return
    val r = c(reg, "R") ?: return; val zr = c(mine, "R2") ?: return
    // the gap between the drawn pieces mirrored (a pill need not be centred on its control)
    fun span(z: BoxSize, g: String) = z.pieces.filter { it.group == g }.let { ps -> ps.minOf { it.x.toFloat() } to ps.maxOf { it.x + it.w * it.acrossScale } }
    val gap = span(left, "L2").first - span(left, "L").second
    val zrW = span(mine, "R2").let { it.second - it.first }
    val dx = (span(reg, "R").first - gap - zrW) - span(mine, "R2").first
    val dy = (r.y + (zl.y - l.y)) - zr.y
    val tx = zr.x + dx; val ty = zr.y + dy
    val zrPieces = mine.pieces.filter { it.group == "R2" }.map { it.copy(x = Math.round(it.x + dx), y = Math.round(it.y + dy)) }
    out.getValue(BoxSlot.RIGHT)[ri] = reg.copy(pieces = reg.pieces + zrPieces, controls = reg.controls + zr.copy(x = tx, y = ty))
}

/** [out]'s right box for [tag]: the Fold upright's C-stick (the d-pad's size) placed as the d-pad mirrored (a New 3DS's only). */
private fun newCStickAsTheDpad(out: Map<BoxSlot, MutableList<BoxSize>>, sides: List<Pair<BoxSlot, Int>>, list: List<BoxSpec>, tag: BoxWhen, rowW: Int) {
    val li = sides.firstOrNull { it.first == BoxSlot.LEFT }?.second ?: return
    val ri = sides.firstOrNull { it.first == BoxSlot.RIGHT }?.second ?: return
    val l = out.getValue(BoxSlot.LEFT)[li]; val r = out.getValue(BoxSlot.RIGHT)[ri]
    // (a New 3DS only: its own right had the C-stick)
    if (list.first { it.slot == BoxSlot.RIGHT }.sizes.firstOrNull { it.`when` == tag }?.controls?.none { it.id == "CSTICK" } != false) return
    fun x0(ps: List<PieceSpec>) = ps.minOf { it.x.toFloat() }
    fun x1(ps: List<PieceSpec>) = ps.maxOf { it.x + it.w * it.acrossScale }
    fun y0(ps: List<PieceSpec>) = ps.minOf { it.y.toFloat() }
    val dpad = l.pieces.filter { it.group == "DPAD" && it.alt == null }
    if (dpad.isEmpty()) return
    // the Fold upright's C-stick: the same drawing at the d-pad's size
    val fold = list.first { it.slot == BoxSlot.RIGHT }.sizes.firstOrNull { it.`when`?.fold8Upright() == true } ?: return
    val big = fold.pieces.filter { it.group == "CSTICK" }
    val bigC = fold.controls.firstOrNull { it.id == "CSTICK" } ?: return
    if (big.isEmpty()) return
    val dev = GOLDEN_DEVICES.firstOrNull { it.hingeY == null && kotlin.math.abs(it.w * 160f / it.dpi - tag.wdp()) < 12f && it.h > it.w == (tag.orientation == "portrait") } ?: return
    val winW = dev.w.toFloat(); val edge = SkinBoxes.EDGE_DP * dev.dpi / 160f
    // the boxes' main pads stand level on the window (the stick, the face's middle): the right box's px from the left's
    fun main(z: BoxSize, ids: List<String>) = z.controls.filter { it.id in ids && it.alt == null }.map { it.y }.average().toFloat()
    val dy = main(r, listOf("X", "Y", "A", "B")) - main(l, listOf("STICK"))
    val gap = ((winW - rowW) / 2f - edge) - x1(dpad)
    val left = (winW + rowW) / 2f - (winW - edge - r.w) + gap
    val top = y0(dpad) + dy
    val mx = left - x0(big); val my = top - y0(big)
    val moved = big.map { it.copy(x = Math.round(it.x + mx), y = Math.round(it.y + my)) }
    val c = bigC.copy(x = bigC.x + mx, y = bigC.y + my)
    val z = r.copy(pieces = r.pieces.filter { it.group != "CSTICK" } + moved, controls = r.controls.filter { it.id != "CSTICK" } + c)
    out.getValue(BoxSlot.RIGHT)[ri] = z.copy(h = maxOf(z.h, Math.round(z.pieces.maxOf { it.y + it.h * it.scale })))
}

/**
 * [z]'s lower pad (the d-pad under the stick; the C-stick under the face) moved up by half its gap under the upper one,
 * and across to [near] px from [rowEnd] (the caps' row's near end, box px): right toward it [toTheRight], else left.
 */
private fun padUpAndIn(z: BoxSize, rowEnd: Float, toTheRight: Boolean, near: Float, up: Float?): BoxSize? {
    val pads = z.pieces.filter { it.group in PAD_GROUPS && it.alt == null }.groupBy { it.group!! }
        .map { (id, ps) -> Triple(id, ps.minOf { it.y.toFloat() }, ps.maxOf { it.y + it.h * it.scale }) }.sortedBy { it.second }
    if (pads.size < 2) return null
    val lowerTop = pads[1].second
    val lower = z.pieces.filter { it.y + it.h * it.scale / 2f >= lowerTop - 0.5f }
    // a lower pad only (not the face under the Ultra upright New 3DS's nub)
    fun width(g: String) = z.pieces.filter { it.group == g }.let { ps -> ps.maxOf { it.x + it.w * it.acrossScale } - ps.minOf { it.x.toFloat() } }
    if (width(pads[1].first) > 1.05f * width(pads[0].first)) return null
    val gap = pads[1].second - pads[0].third
    // [up]: px up; null: half the gap under the upper pad and 25 px
    val dy = up ?: ((if (gap > 0f) gap / 2f else 0f) + 25f)
    val dx = if (toTheRight) maxOf(0f, rowEnd - near - lower.maxOf { it.x + it.w * it.acrossScale })
        else minOf(0f, rowEnd + near - lower.minOf { it.x.toFloat() })
    return z.copy(pieces = z.pieces.map { p -> if (p.y + p.h * p.scale / 2f >= lowerTop - 0.5f) p.copy(x = Math.round(p.x + dx), y = Math.round(p.y - dy)) else p },
        controls = z.controls.map { c -> if (c.y >= lowerTop - 0.5f) c.copy(x = c.x + dx, y = c.y - dy) else c })
}

/**
 * The pack's drawing for the 3DS on the Fold 8's and the Ultra's inner screens upright, and the Ultra's sideways: MIC, MENU, SWAP, SELECT, START out of the
 * side rows into one row in the bottom row box, under the touch screen (its room ends over it), one well for the five
 * cut from the side rows' well. [isFor]: the layouts it is for.
 */
fun fold8UprightCapsInTheBottomRow(boxes: Map<String, List<BoxSpec>>, isFor: (String) -> Boolean): Map<String, List<BoxSpec>> = boxes.mapValues { (id, list) ->
    if (!isFor(id)) return@mapValues list
    val order = listOf("MIC", "MENU", "SWAP", "SELECT", "START")
    val out = list.associate { b -> b.slot to b.sizes.toMutableList() }.toMutableMap()
    val tags = list.filter { it.slot == BoxSlot.LEFT || it.slot == BoxSlot.RIGHT }.flatMap { b -> b.sizes.mapNotNull { it.`when` } }
        .filter { it.fold8Upright() || (it.ultraUpright() && it.orientation == "portrait") || it.ultraSideways() }.distinct()
    var row: MutableList<BoxSize>? = out[BoxSlot.BOTTOM_ROW]
    for (tag in tags) {
        // the New 3DS on the Ultra: its right as the regular 3DS's (the face full size, R level with L), ZR beside R
        val sideways = tag.ultraSideways()
        if (!tag.fold8Upright() && !sideways && "3dsr" !in id) boxes[id.replace("3ds", "3dsr")]?.let { newRightAsTheRegular(out, it, tag) }
        val sides = listOf(BoxSlot.LEFT, BoxSlot.RIGHT).mapNotNull { s -> out[s]?.indexOfFirst { it.`when` == tag }?.takeIf { it >= 0 }?.let { s to it } }
        val caps = sides.flatMap { (s, i) -> out.getValue(s)[i].controls.filter { it.id in order } }
        if (order.any { id -> caps.none { it.id == id } }) continue
        fun centredOn(p: PieceSpec, c: ControlSpec) = kotlin.math.abs(p.x + p.w * p.acrossScale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
        fun isCapPiece(p: PieceSpec) = p.group?.split("+")?.any { it in order } == true
        val sidePieces = sides.flatMap { (s, i) -> out.getValue(s)[i].pieces.filter(::isCapPiece) }
        val capPieces = caps.flatMap { c -> sidePieces.filter { centredOn(it, c) }.map { c.id to it } }
        val wells = sidePieces.filter { p -> capPieces.none { it.second === p } }
        // the well's ends by the half of the picture they are cut from (the two side rows' boxes place them apart)
        val leftEnd = wells.filter { it.scaleX == 0f }.minByOrNull { it.sx } ?: continue
        val rightEnd = wells.filter { it.scaleX == 0f }.maxByOrNull { it.sx } ?: continue
        val mid = wells.firstOrNull { it.scaleX > 0f } ?: continue
        // the pitch the side rows had
        val pitch = sides.mapNotNull { (s, i) -> out.getValue(s)[i].controls.filter { it.id in order }.sortedBy { it.x }.zipWithNext().minOfOrNull { (a, b) -> b.x - a.x } }.minOrNull() ?: continue
        val ends = leftEnd.h * leftEnd.scale
        val endW = leftEnd.w * leftEnd.scale
        val group = order.sorted().joinToString("+")
        val cy = ends / 2f
        val at = order.mapIndexed { i, cid -> cid to ends / 2f + i * pitch }.toMap()
        val well = listOf(
            leftEnd.copy(x = 0, y = 0, group = group),
            mid.copy(x = Math.round(endW), y = 0, scaleX = 4f * pitch / mid.w, group = group),
            rightEnd.copy(x = Math.round(endW + 4f * pitch), y = 0, group = group),
        )
        val capsMoved = capPieces.map { (cid, p) -> p.copy(x = Math.round(at.getValue(cid) - p.w * p.acrossScale / 2f), y = Math.round(cy - p.h * p.scale / 2f), group = group) }
        val label = caps.first { it.labelH > 0f }
        val ctrls = order.map { cid -> caps.first { it.id == cid && it.alt == null }.copy(x = at.getValue(cid), y = cy, labelDx = 0f, labelDy = label.labelDy, labelH = label.labelH) }
        val w = Math.round(2f * endW + 4f * pitch)
        val h = Math.round(maxOf(ends, cy + label.labelDy + label.labelH / 2f))
        val size = BoxSize(w, h, pieces = well + capsMoved, controls = ctrls, `when` = tag)
        // a new row: empty on every other device shape (else the nearest tag's would stand there too)
        if (row == null) { row = list.first { it.slot == BoxSlot.LEFT }.sizes.map { BoxSize(0, 0, `when` = it.`when`) }.toMutableList(); out[BoxSlot.BOTTOM_ROW] = row }
        val ri = row.indexOfFirst { it.`when` == tag }
        if (ri >= 0) row[ri] = size else row += size
        for ((s, i) in sides) {
            val z = out.getValue(s)[i]
            var kept = z.copy(pieces = z.pieces.filter { !isCapPiece(it) }, controls = z.controls.filter { it.id !in order })
            // the left's lower pad (the d-pad) into the room the caps left: up by half its gap under the stick, across to
            // the inner gap from the row (the row centred on the window, the box on the edge; picture px = window px here)
            // the device the tag was baked on: its window and density (a tuned board's picture px are its window px)
            val dev = GOLDEN_DEVICES.firstOrNull { it.hingeY == null && kotlin.math.abs(it.w * 160f / it.dpi - tag.wdp()) < 12f && it.h > it.w == (tag.orientation == "portrait") }
                ?: error("no device for $tag")
            val winW = dev.w.toFloat(); val edge = SkinBoxes.EDGE_DP * dev.dpi / 160f
            // where the user put the regular 3DS's d-pad with the controls editor: on the Fold 5 px from the row, up half its
            // gap under the stick and 25 px; on the Ultra (its d-pad up by half its gap already, [ultraUprightLowerPadUp])
            // 222 px from the row, clear of the touch screen's frame, and 30 px up
            val (near, up) = if (tag.fold8Upright()) 5f to null else 222f to 30f
            // (sideways the pads keep their places: only the caps move)
            if (!sideways) kept = when (s) {
                BoxSlot.LEFT -> padUpAndIn(kept, rowEnd = (winW - w) / 2f - edge, toTheRight = true, near = near, up = up)
                // (the right box on the right edge: the row's right end in its px)
                else -> padUpAndIn(kept, rowEnd = (winW + w) / 2f - (winW - edge - kept.w), toTheRight = false, near = near, up = up)
            } ?: kept
            out.getValue(s)[i] = kept.copy(h = Math.round(kept.pieces.maxOfOrNull { it.y + it.h * it.scale } ?: kept.h.toFloat()))
        }
        // the New 3DS on the Ultra: its nub over the face a joystick at the d-pad's size instead (as the Fold's C-stick),
        // where the d-pad is mirrored on the right: level with it, as far from the row
        if (!tag.fold8Upright() && !sideways) newCStickAsTheDpad(out, sides, list, tag, w)
        if (sideways) sidewaysPadsUp(out, sides)
    }
    val slots = list.map { it.slot }.toMutableList().also { if (BoxSlot.BOTTOM_ROW !in it && out.containsKey(BoxSlot.BOTTOM_ROW)) it += BoxSlot.BOTTOM_ROW }
    slots.map { slot -> BoxSpec(slot, out.getValue(slot).toList()) }
}

/**
 * The pack's drawing rule for a fold board's small caps spread over its boxes (the 3DS's: MIC / SWAP left, MENU in a row
 * under the touch screen, SELECT / START right): gathered into a row each side, the inner gap under the side's lowest pad
 * (the d-pad; the face buttons, or the New 3DS's C-stick under them), the switch / MIC / MENU on the left and SWAP /
 * SELECT / START on the right (SWAP and MENU nearest the screen), each in a well cut from one of theirs; the rows they
 * left emptied. [isFold]: the layouts it is for.
 */
fun capsToTheSides(boxes: Map<String, List<BoxSpec>>, isFold: (String) -> Boolean): Map<String, List<BoxSpec>> = boxes.mapValues { (id, list) ->
    if (!isFold(id)) return@mapValues list
    val left = list.firstOrNull { it.slot == BoxSlot.LEFT } ?: return@mapValues list
    val right = list.firstOrNull { it.slot == BoxSlot.RIGHT } ?: return@mapValues list
    val rows = list.filter { it.slot == BoxSlot.TOP_ROW || it.slot == BoxSlot.BOTTOM_ROW }
    val gap = SkinBoxes.GAP_DP * 420f / 160f
    val out = list.associate { b -> b.slot to b.sizes.toMutableList() }
    fun centredOn(p: PieceSpec, c: ControlSpec) = kotlin.math.abs(p.x + p.w * p.acrossScale / 2f - c.x) < 2f && kotlin.math.abs(p.y + p.h * p.scale / 2f - c.y) < 2f
    for ((li, l) in left.sizes.withIndex()) {
        val ri = right.sizes.indexOfFirst { it.`when` == l.`when` }.takeIf { it >= 0 } ?: continue
        val r = right.sizes[ri]
        val rowSizes = rows.mapNotNull { b -> b.sizes.indexOfFirst { it.`when` == l.`when` }.takeIf { it >= 0 }?.let { b.slot to it } }
        fun capsOf(z: BoxSize) = z.controls.filter { it.id in CAPS }
        val inRows = rowSizes.flatMap { (s, i) -> capsOf(out.getValue(s)[i]) }
        // only where the caps are spread: some in a row under the screen
        if (inRows.isEmpty()) continue
        val all = capsOf(l) + capsOf(r) + inRows
        if (all.map { it.id }.toSet().size < 4) continue
        val allPieces = l.pieces + r.pieces + rowSizes.flatMap { (s, i) -> out.getValue(s)[i].pieces }
        fun isCapPiece(p: PieceSpec) = p.group?.split("+")?.any { it in CAPS } == true
        // each cap's own piece (the smallest centred on it; a switch: its alternates' pieces) and a well to cut rows from
        val capPieces = all.flatMap { c -> allPieces.filter { centredOn(it, c) && isCapPiece(it) }.let { ps ->
            val small = ps.minOfOrNull { it.w * it.acrossScale } ?: return@let emptyList()
            ps.filter { it.w * it.acrossScale <= small + 0.5f }.map { c.id to it } } }.distinct()
        // the whole pill its pieces were cut from (a box may hold only an end of it): their span in the picture
        val well = allPieces.filter { isCapPiece(it) && capPieces.none { (_, p) -> p === it } }.let { ws ->
            val tall = ws.maxByOrNull { it.h * it.scale } ?: return@let null
            val same = ws.filter { it.sy == tall.sy && it.h == tall.h }
            val x0 = same.minOf { it.sx }
            tall.copy(sx = x0, w = same.maxOf { it.sx + it.w } - x0, scaleX = 0f)
        } ?: continue
        val pitch0 = all.map { it.x }.let { 0f }.let { _ ->
            val pair = (capsOf(l) + capsOf(r)).groupBy { kotlin.math.round(it.y) }.values.firstOrNull { it.size >= 2 }
            pair?.sortedBy { it.x }?.zipWithNext()?.minOf { (a, b) -> b.x - a.x } ?: (well.h * well.scale * 1.0f)
        }
        val ends = well.h * well.scale
        fun side(z: BoxSize, order: List<String>, slot: BoxSlot): BoxSize {
            val kept = z.copy(pieces = z.pieces.filter { !isCapPiece(it) }, controls = z.controls.filter { it.id !in CAPS })
            val pads = kept.pieces.filter { it.group in PAD_GROUPS && it.alt == null }
            val foot = pads.maxOfOrNull { it.y + it.h * it.scale } ?: (z.h * 0.6f)
            val shoulderFoot = kept.pieces.filter { it.group in SHOULDERS }.maxOfOrNull { it.y + it.h * it.scale }
            val inner = shoulderFoot?.let { sf -> pads.minOfOrNull { it.y.toFloat() }?.minus(sf) }?.takeIf { it > 0f } ?: gap
            val y = foot + inner + ends / 2f
            val ids = order.filter { id -> all.any { it.id == id } }
            if (ids.isEmpty()) return kept
            val n = ids.size
            val r0 = all.maxOf { it.r }
            val pitch = if (n == 1) 0f else minOf(pitch0, (z.w - ends) / (n - 1)).coerceAtLeast(2f * r0)
            val inEdge = if (slot == BoxSlot.RIGHT) ends / 2f else z.w - ends / 2f
            val step = if (slot == BoxSlot.RIGHT) pitch else -pitch
            val at = ids.reversed().mapIndexed { i, cid -> cid to inEdge + i * step }.toMap()
            val leftEnd = at.values.min() - ends / 2f
            val end = well.h / 2
            val mid = (n - 1) * pitch / well.scale
            val top = Math.round(y - ends / 2f)
            val group = ids.sorted().joinToString("+")
            val cut = listOfNotNull(
                well.copy(w = end, x = Math.round(leftEnd), y = top, scaleX = 0f, group = group),
                if (mid > 0f) well.copy(sx = well.sx + end - 1, w = 2, x = Math.round(leftEnd + end * well.scale), y = top, scaleX = mid * well.scale / 2f, group = group) else null,
                well.copy(sx = well.sx + well.w - end, w = end, x = Math.round(leftEnd + (end + mid) * well.scale), y = top, scaleX = 0f, group = group),
            )
            val caps = capPieces.filter { it.first in at }.map { (cid, p) -> p.copy(x = Math.round(at.getValue(cid) - p.w * p.acrossScale / 2f), y = Math.round(y - p.h * p.scale / 2f), group = group) }
            val label = all.first { it.labelH > 0f }
            val ctrls = all.filter { it.id in at }.map { it.copy(x = at.getValue(it.id), y = y, labelDx = 0f, labelDy = label.labelDy, labelH = label.labelH) }
            // its foot no higher than it was (a row it took from under the screen stood lower)
            val newFoot = (kept.pieces + cut + caps).maxOf { it.y + it.h * it.scale }
            return kept.copy(h = maxOf(z.h, Math.round(newFoot)), pieces = kept.pieces + cut + caps, controls = kept.controls + ctrls)
        }
        out.getValue(BoxSlot.LEFT)[li] = side(l, listOf("STICK_SWAP", "MIC", "MENU"), BoxSlot.LEFT)
        out.getValue(BoxSlot.RIGHT)[ri] = side(r, listOf("START", "SELECT", "SWAP"), BoxSlot.RIGHT)
        for ((s, i) in rowSizes) {
            val z = out.getValue(s)[i]
            val rest = z.copy(pieces = z.pieces.filter { !isCapPiece(it) }, controls = z.controls.filter { it.id !in CAPS })
            out.getValue(s)[i] = if (rest.pieces.isEmpty() && rest.controls.isEmpty()) BoxSize(0, 0, `when` = z.`when`) else rest
        }
    }
    list.map { b -> b.copy(sizes = out.getValue(b.slot).toList()) }
}

/** The small caps a side's row holds (not its pads or shoulders). */
private val CAPS = setOf("MIC", "MENU", "SWAP", "SELECT", "START", "STICK_SWAP")

/**
 * The pack's drawing rule for the side caps: where both sides have a row of them (the DS's MIC / MENU, and SWAP /
 * SELECT / START), the left row moved up or down to the right row's height (its well with it).
 */
fun capRowsLevel(boxes: Map<String, List<BoxSpec>>): Map<String, List<BoxSpec>> = boxes.mapValues { (_, list) ->
    val left = list.firstOrNull { it.slot == BoxSlot.LEFT } ?: return@mapValues list
    val right = list.firstOrNull { it.slot == BoxSlot.RIGHT } ?: return@mapValues list
    val changed = HashMap<BoxSize, BoxSize>()
    for (l in left.sizes) {
        val r = right.sizes.firstOrNull { it.`when` == l.`when` } ?: continue
        val lc = l.controls.filter { it.id in CAPS && it.alt == null }; val rc = r.controls.filter { it.id in CAPS && it.alt == null }
        // a row: two or more caps level with each other
        fun row(cs: List<ControlSpec>) = cs.takeIf { it.size >= 2 && it.all { c -> kotlin.math.abs(c.y - it[0].y) < 1f } }
        val lr = row(lc) ?: continue; val rr = row(rc) ?: continue
        // both boxes stand on one line: a cap's height above it is its y less the box's height
        val dy = (rr[0].y - r.h) - (lr[0].y - l.h)
        if (kotlin.math.abs(dy) < 1f) continue
        val ids = lr.map { it.id }.toSet()
        val groups = l.pieces.filter { p -> p.group != null && p.group!!.split("+").any { it in ids } }.mapNotNull { it.group }.toSet()
        changed[l] = l.copy(
            pieces = l.pieces.map { p -> if (p.group in groups) p.copy(y = Math.round(p.y + dy)) else p },
            controls = l.controls.map { c -> if (c.id in ids && c.alt == null) c.copy(y = c.y + dy) else c },
        )
    }
    if (changed.isEmpty()) list else list.map { b -> if (b.slot == BoxSlot.LEFT) b.copy(sizes = b.sizes.map { changed[it] ?: it }) else b }
}

private val bakeJson = Json { prettyPrint = true; encodeDefaults = false }

/**
 * With SKIN_BAKE=1, [boxes] (a fresh bake, placed as the rules have it) merged into the JSON file at SKIN_BAKE_OUT
 * (other layouts' entries kept), with the pack's drawing rules applied ([lowerPadsSmaller]).
 */
fun writeBake(fresh: Map<String, List<BoxSpec>>, menu: Map<String, List<BoxSpec>>? = null, familyOf: (String) -> SkinFamily? = { null },
    asBaked: Set<String> = emptySet()) {
    if (System.getenv("SKIN_BAKE") != "1") return
    // [asBaked]: layouts written as the rules placed them, no drawing rule yet (a console just converted, for review)
    val raw = fresh.filterKeys { it in asBaked }
    val fold: (String) -> Boolean = { familyOf(it) == SkinFamily.TABLET }
    // the drawing rules: sideways and on fold boards only (an upright phone keeps its own drawing)
    val rowsFor: (String) -> Boolean = { familyOf(it) == SkinFamily.TABLET || familyOf(it) == SkinFamily.GRIP || familyOf(it) == null }
    val todo = fresh.filterKeys { it !in asBaked }
    val upright = todo.filterKeys { !rowsFor(it) }
    val shaped = bottomRowToTheSides(ultraUprightLowerPadUp(levelStackedSides(lowerPadsSmaller(todo.filterKeys { rowsFor(it) }))))
    val drawn = fold8UprightCapsInTheBottomRow(capRowsLevel(capsUnderPads(swapInTheRow(swapToTheRight(capsToTheSides(shaped, fold))))),
        isFor = { fold(it) && "3ds" in it }) + upright
    val boxes = (if (menu == null) drawn else fold8SidewaysGapsHalved(capsClearOfPads(padsAsTheMenu(drawn.filterKeys { it !in asBaked }, menu, familyOf),
        rowsUp = { "3ds" in it && rowsFor(it) }), isFor = { "3ds" in it })) + raw
    val out = File(System.getenv("SKIN_BAKE_OUT") ?: error("SKIN_BAKE_OUT not set"))
    val old: Map<String, kotlinx.serialization.json.JsonElement> = if (out.exists()) Json.parseToJsonElement(out.readText()).jsonObject else emptyMap()
    val merged = old + boxes.mapValues { (_, b) -> bakeJson.encodeToJsonElement(ListSerializer(BoxSpec.serializer()), b) }
    out.parentFile?.mkdirs()
    out.writeText(bakeJson.encodeToString(JsonObject.serializer(), JsonObject(merged.toSortedMap())))
}
