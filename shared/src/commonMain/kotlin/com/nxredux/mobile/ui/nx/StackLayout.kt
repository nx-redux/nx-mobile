package com.nxredux.mobile.ui.nx

import com.nxredux.mobile.settings.VerticalListAlignment
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The vertical stacks (Carousel / Backdrop, orientation Vertical): the horizontal row stood on end. The sizes are the
 * mockup's.
 */

/** Game lists, landscape family: the margin left of the stack and right of the caption, of the body's width. */
private const val SIDE_MARGIN_SHARE = 0.1f
/** Game lists, landscape family: the selected item's right edge to the caption's left edge, dp. */
private const val SIDE_CAPTION_GAP_LAND = 32f
/** Game lists, portrait family: the caption column's least share of the body's width. */
private const val CAPTION_MIN_SHARE_PORT = 0.45f
/**
 * Game lists, landscape family: the caption column's least width, dp. An app-only guard for near-square windows: the
 * tile keeps its height-given size and the caption takes the width left (116 dp on the Flip's 474-wide cover; its text
 * ellipsizes), down to this floor. Phones never reach it (800 × 242: 420 dp).
 */
private const val CAPTION_MIN_LAND = 96f

/** Backdrop-Vertical's box art slot, width over height. */
private const val BOX_STACK_AR = 0.75f
/** Backdrop-Vertical's wanted slot height, of the body's height: landscape family, portrait family. */
private const val BOX_STACK_SHARE_LAND = 0.58f
private const val BOX_STACK_SHARE_PORT = 0.62f
/** Backdrop-Vertical's wanted slot height's clamp, dp. */
private const val BOX_STACK_MIN = 110f
private const val BOX_STACK_MAX = 320f
/** Carousel-Vertical's wanted tile height, of the body's height, and its floor, dp. */
private const val TILE_STACK_SHARE = 0.55f
private const val TILE_STACK_MIN = 100f

/**
 * A vertical stack's measures in dp, body-relative: the selected slot, neighbours' scale, gap, caption room [cap] under
 * the selection, the stack centre (x) and the selected slot's centre (y), and where the caption goes. [sideCap]: the
 * room a side item's caption (a console's count) takes under its slot as drawn at [scale]. Every item's footprint is its
 * slot plus its caption room, and [gap] is the same between any two footprints (on screen).
 */
internal data class StackGeometry(
    val slotW: Float, val slotH: Float, val scale: Float, val gap: Float, val cap: Float,
    val centreX: Float, val centreY: Float,
    val captionX: Float,        // game lists: the caption's left edge (0 on the main menu)
    val captionRight: Float,    // game lists: the caption's right inset from the body's right edge, 24 / 16 (0 on the main menu)
    val sideCap: Float = 0f,
)

/**
 * The row stood on end: a RowGeometry whose "width" is the slot's height, so rowItem / rowSwipe* work unchanged on Y.
 * Its gap holds [StackGeometry.sideCap]: every side item's caption room sits between it and the next one down.
 */
internal fun StackGeometry.asRow(): RowGeometry = RowGeometry(itemW = slotH, itemH = slotW, scale = scale, gap = gap + sideCap, top = 0f)

/** The selected slot as an *untransposed* RowGeometry (width = the slot's width), for the row's content-scale and tile helpers. */
internal fun StackGeometry.asItemRow(): RowGeometry = RowGeometry(itemW = slotW, itemH = slotH, scale = scale, gap = gap, top = 0f)

/**
 * The portrait family for the game-list stacks' sizes: the pad showing, or a *screen* (the whole window, [LocalWindowSize])
 * not wider than tall (large portrait). Not the body: a near-square window's
 * body (less the header and hint bar) can be wider than tall while the screen is not.
 */
internal fun stackPortraitFamily(screenW: Float, screenH: Float, portrait: Boolean): Boolean = portrait || screenW <= screenH

/** cy = body/2 − cap/2: the selection plus its caption room centred as one block. */
internal fun stackCentreY(bodyH: Float, cap: Float): Float = bodyH / 2 - cap / 2

/**
 * [this] stack, sized for the shown title's body ([titleRoomSizingBody]), re-centred in its whole box [boxH]
 * (a hidden page title's room included): only [StackGeometry.centreY] moves, so the selection, its caption and every
 * item move up by half the room together; the slots, scale, gaps and caption column stay. [boxH] the sizing body
 * itself (the title shown): unchanged.
 */
internal fun StackGeometry.centredIn(boxH: Float): StackGeometry = copy(centreY = stackCentreY(boxH, cap))

/**
 * Item at [distance] (index − pos): rowItem on asRow() (dx is the Y offset), plus (cap − sideCap) · min(1, distance)
 * below the selection: the selection's own caption room in place of a side item's.
 */
internal fun stackItem(style: RowStyle, distance: Float, g: StackGeometry): RowItem {
    val r = rowItem(style, distance, g.asRow())
    val extra = g.cap - g.sideCap
    return if (distance > 0f && extra != 0f) r.copy(dx = r.dx + extra * min(1f, distance)) else r
}

/**
 * The items a game list's stack shows on one screen, its page for LEFT / RIGHT: the selection and the neighbours within
 * the three steps [stackItem] draws whose centre lies in the body ([bodyH] tall, [g] centred in it).
 */
internal fun stackPageItems(style: RowStyle, g: StackGeometry, bodyH: Float): Int =
    (-3..3).count { d -> (g.centreY + stackItem(style, d.toFloat(), g).dx) in 0f..bodyH }

/** A console's count: 8 dp under its logo, in the item (so scaled with it). */
internal const val COUNT_UNDER_LOGO = 8f
/** The count's line at full size, dp: 14 sp, about 18 (a full-size cap of 26 less the 8 above it). */
internal const val COUNT_LINE_DEFAULT = 18f

/**
 * The room a console's count takes under its slot, as drawn, for an item at [scale] and a count line [countLine] dp
 * tall at full size: the 8 dp gap scaled with the item, and the line as drawn (14 sp scaled, at least [MIN_COUNT_SP],
 * [countGrowth]).
 */
internal fun countRoom(scale: Float, countLine: Float): Float =
    if (countLine <= 0f) 0f else COUNT_UNDER_LOGO * scale + countLine * scale * countGrowth(scale)

/**
 * An item's reach from its slot's centre at full size, item units: [up] to the top of the mark as drawn (half a logo
 * drawn shorter than its slot), [down] to the bottom of the mark, then, with a [countLine] (dp at full size, 0: none),
 * the count [COUNT_UNDER_LOGO] under it. The main menu's consoles: the step to the next item is from what is drawn.
 * [padUp] / [padDown]: while selected, its controller's reach from the slot's centre at full size, dp, clearance
 * included (0: none, [withPad]); a side item never shows its pad, so only the selection's counts.
 */
internal data class StackReach(val up: Float, val down: Float, val countLine: Float = 0f, val padUp: Float = 0f, val padDown: Float = 0f)

/** The reach up / down at [scale] as drawn: the mark and the gap scale with the item; the count is held to [MIN_COUNT_SP] ([countRoom]). */
internal fun StackReach.upAt(scale: Float): Float = up * scale
internal fun StackReach.downAt(scale: Float): Float = down * scale + countRoom(scale, countLine)

/** The selected item's reach: its mark and count at full size, or its pad's when that reaches further. */
internal fun StackReach.upSelected(): Float = max(upAt(1f), padUp)
internal fun StackReach.downSelected(): Float = max(downAt(1f), padDown)

/** A console's reach: its logo drawn [drawnH] tall centred in the slot, its count [countLine] under it (0 when it has none). */
internal fun logoReach(drawnH: Float, countLine: Float): StackReach = StackReach(drawnH / 2, drawnH / 2, countLine)

/**
 * The centre of item [j] when [k] is selected, from the body's centre, dp: the selected item's mark and caption centred
 * as one block, and the same [StackGeometry.gap] from every item's bottom (its count) to the next one's top, each side
 * item at [StackGeometry.scale], the selection at 1. The selection's pad, when it has one, takes the place of its mark
 * in the step to each neighbour ([upSelected]).
 */
internal fun stackCentreAt(j: Int, k: Int, g: StackGeometry, reach: (Int) -> StackReach): Float {
    fun up(m: Int) = if (m == k) reach(m).upSelected() else reach(m).upAt(g.scale)
    fun down(m: Int) = if (m == k) reach(m).downSelected() else reach(m).downAt(g.scale)
    val sel = reach(k)
    var y = -(sel.downAt(1f) - sel.upAt(1f)) / 2
    if (j > k) for (m in k until j) y += down(m) + g.gap + up(m + 1)
    else for (m in k downTo j + 1) y -= up(m) + g.gap + down(m - 1)
    return y
}

/**
 * Item [i]'s Y offset from [StackGeometry.centreY] at the animated position [pos] ([stackItem]'s dx with per-item reach):
 * [stackCentreAt] eased between the two selections either side of [pos] (past an end: carried on linearly), moved so
 * the selected block sits on the body's centre (centreY holds cap/2 above it).
 */
internal fun stackReachDy(i: Int, pos: Float, count: Int, g: StackGeometry, reach: (Int) -> StackReach): Float {
    val base = g.cap / 2
    if (count <= 1) return base + stackCentreAt(i, 0, g, reach)
    val k = floor(pos).toInt().coerceIn(0, count - 2)
    val f = pos - k
    val a = stackCentreAt(i, k, g, reach)
    val b = stackCentreAt(i, k + 1, g, reach)
    return base + a + (b - a) * f
}

/**
 * UP / DOWN one item. Past an end a [fresh] press wraps (UP on the first to the last, DOWN on the last to the first, as
 * the List), a held key's repeat stays. LEFT / RIGHT and the rest: [index]
 * (the caller switches tab on the main menu). The caller keeps [index] in `0 until count`. An empty stack is all top:
 * UP is null (the main menu's tab row), anything else [index].
 */
internal fun stackStep(index: Int, count: Int, button: NxButton, fresh: Boolean): Int? {
    if (count <= 0) return if (button == NxButton.UP) null else index
    return when (button) {
        NxButton.UP -> if (index > 0) index - 1 else if (fresh) count - 1 else index
        NxButton.DOWN -> if (index < count - 1) index + 1 else if (fresh) 0 else index
        else -> index
    }
}

/**
 * The main menu's stacks: [BackdropSlot.LOGO] the horizontal Carousel's logo slot ([backdropSlotGeometry]) s 0.5 g 16,
 * cap = [countRoom] at 1 (26 for the default 18 dp line) and sideCap = [countRoom] at 0.5: the count is part of every
 * console's footprint, so the 16 dp gap is the same under the selection and between side items. COLLECTION 400×100
 * s 0.5 g 14 cap 0 (the count is inside the slot); TOOL [TOOL_SLOT_W]×140 s 0.6 g 10 cap 0. Centred horizontally
 * (centreX = bodyW/2), centreY = stackCentreY (the selected logo and its count centred as one block). captionX /
 * captionRight unused (0). slotW held to bodyW − 2·gutter. [countLine]: the count's line at full size, dp (measured; 0:
 * no counts). BOX is not a main-menu slot (require).
 */
internal fun mainMenuStackGeometry(
    slot: BackdropSlot, bodyW: Float, bodyH: Float, portrait: Boolean, gutter: Float, countLine: Float = COUNT_LINE_DEFAULT,
): StackGeometry {
    require(slot != BackdropSlot.BOX) { "the box art slot is a game list's" }
    data class Spec(val w: Float, val h: Float, val s: Float, val gap: Float, val cap: Float)
    val spec = when (slot) {
        // The horizontal Carousel's logo slot on this body ([backdropSlotGeometry], portrait's width cap too),
        // so a console is the same size, and its pad, whichever way the Carousel runs.
        BackdropSlot.LOGO -> backdropSlotGeometry(BackdropSlot.LOGO, bodyH, portraitW = bodyW.takeIf { portrait })
            .let { Spec(it.itemW, it.itemH, 0.5f, 16f, countRoom(1f, countLine)) }
        BackdropSlot.COLLECTION -> Spec(400f, 100f, 0.5f, 14f, 0f)
        else -> Spec(TOOL_SLOT_W, 140f, 0.6f, 10f, 0f)
    }
    val w = min(spec.w, bodyW - 2 * gutter)
    return StackGeometry(
        slotW = w, slotH = spec.h, scale = spec.s, gap = spec.gap, cap = spec.cap,
        centreX = bodyW / 2, centreY = stackCentreY(bodyH, spec.cap), captionX = 0f, captionRight = 0f,
        sideCap = if (slot == BackdropSlot.LOGO) countRoom(spec.s, countLine) else 0f,
    )
}

/**
 * Game-list Backdrop-Vertical: ar 0.75, s 0.5, g 16, cap 0; want = clamp(0.58 · body, 110, 320) (landscape family) or
 * clamp(0.62 · body, 110, 320) (portrait family), the slot never wider than [BOX_MAX_SHARE] of the body; then
 * [stackSide], on the [alignment] side. RIGHT: the caption starts where LEFT's selected box is drawn, not on LEFT's
 * caption inset: the [placeholderBoxSize] case (0.72) centred in the 0.75 slot at scale 1, x − 0.36·h. Real box art is
 * Fit and varies per game, so the case stands for it. Landscape 99 (96 + 3), Flip 31.2, Fold cover 49.7, large
 * portrait 68. A box margin that would leave the caption less than [BACKDROP_RIGHT_CAPTION_KEEP] of its mirrored width
 * keeps the mirror. Its text is right-aligned ([StackCaption]), so it ends LEFT's caption gap before the stack.
 */
internal fun backdropStackGeometry(
    bodyW: Float, bodyH: Float, screenW: Float, screenH: Float, portrait: Boolean, gutter: Float,
    alignment: VerticalListAlignment = VerticalListAlignment.LEFT,
): StackGeometry {
    val portraitFamily = stackPortraitFamily(screenW, screenH, portrait)
    val share = if (portraitFamily) BOX_STACK_SHARE_PORT else BOX_STACK_SHARE_LAND
    // Never a slot (so a box) wider than [BOX_MAX_SHARE] of the body, the minimum height giving way.
    val want = min((share * bodyH).coerceIn(BOX_STACK_MIN, BOX_STACK_MAX), BOX_MAX_SHARE * bodyW / BOX_STACK_AR)
    val left = stackSide(bodyW, portraitFamily, gutter, want, BOX_STACK_AR)
    val side = left.aligned(alignment, bodyW)
    val placed = if (alignment == VerticalListAlignment.RIGHT) {
        val boxLeft = left.centreX - placeholderBoxSize(left.w, left.h).first / 2
        val mirrored = bodyW - side.captionRight - side.captionX
        if (bodyW - side.captionRight - boxLeft >= BACKDROP_RIGHT_CAPTION_KEEP * mirrored) side.copy(captionX = boxLeft) else side
    } else side
    return placed.geometry(bodyH, scale = 0.5f, gap = 16f)
}

/** Backdrop RIGHT: the caption's least width with LEFT's box margin, of its mirrored width (else it keeps the mirror). */
private const val BACKDROP_RIGHT_CAPTION_KEEP = 0.4f

/**
 * Game-list Carousel-Vertical: the horizontal Carousel's *unscaled* tile geometry for [portrait] (340:240 s 0.62 g 16 carouselH
 * 240, or 256:176 s 0.6 g 12 carouselH 176), cap 0; want = clamp(0.55 · body, 100, carouselH); then [stackSide], on the
 * [alignment] side.
 */
internal fun carouselStackGeometry(
    bodyW: Float, bodyH: Float, screenW: Float, screenH: Float, portrait: Boolean, gutter: Float,
    alignment: VerticalListAlignment = VerticalListAlignment.LEFT,
): StackGeometry {
    val spec = if (portrait) RowGeometry(itemW = 256f, itemH = 176f, scale = 0.6f, gap = 12f, top = 0f)
        else RowGeometry(itemW = 340f, itemH = 240f, scale = 0.62f, gap = 16f, top = 0f)
    val want = (TILE_STACK_SHARE * bodyH).coerceIn(TILE_STACK_MIN, spec.itemH)
    return stackSide(bodyW, stackPortraitFamily(screenW, screenH, portrait), gutter, want, spec.itemW / spec.itemH)
        .aligned(alignment, bodyW).geometry(bodyH, scale = spec.scale, gap = spec.gap)
}

/**
 * The side arrangement both share: from [want] and [ar], the item's (w, h), centre x, capX and capR.
 * Landscape family: h = round(want), w = round(h·ar), L = max(gutter, round(0.1·W)), x = L + w/2, capX = x + w/2 + 32,
 * capR = L: one margin left of the stack and right of the caption. App-only guard (near-square windows): the caption
 * column (W − 2L − w − 32) keeps at least [CAPTION_MIN_LAND] (96 dp), w capped to floor(W − 96 − 2L − 32) and
 * h = round(w / ar). The margin stays 0.1·W (a fixed dp one would move every phone).
 * Portrait family: capR = gutter, gap = gutter, area = W − capR − ceil(0.45·W) − gap; h, w as landscape, then if
 * w > area − gutter: w = area − gutter, h = round(w / ar); x = max(gutter + w/2, area/2); capX = round(x + w/2 + gap).
 */
internal data class StackSide(val w: Float, val h: Float, val centreX: Float, val captionX: Float, val captionRight: Float)

internal fun stackSide(bodyW: Float, portraitFamily: Boolean, gutter: Float, want: Float, ar: Float): StackSide {
    var h = jsRound(want)
    var w = jsRound(h * ar)
    if (!portraitFamily) {
        val margin = max(gutter, jsRound(SIDE_MARGIN_SHARE * bodyW))
        val most = bodyW - CAPTION_MIN_LAND - 2 * margin - SIDE_CAPTION_GAP_LAND
        if (w > most) {
            w = max(1f, floor(most))
            h = jsRound(w / ar)
        }
        val x = margin + w / 2
        return StackSide(w, h, x, x + w / 2 + SIDE_CAPTION_GAP_LAND, margin)
    }
    val capR = gutter
    val gap = gutter
    val area = bodyW - capR - ceil(CAPTION_MIN_SHARE_PORT * bodyW) - gap
    if (w > area - gutter) {   // the width cap wins over any minimum height
        w = area - gutter
        h = jsRound(w / ar)
    }
    val x = max(gutter + w / 2, area / 2)
    return StackSide(w, h, x, jsRound(x + w / 2 + gap), capR)
}

/**
 * [VerticalListAlignment.LEFT]: as [stackSide] lays it out. RIGHT: mirrored about the body's middle, every family alike:
 * the stack's centre at W − x, and the caption column [capR, W − capX], so the two insets swap (its text, right-aligned
 * by [StackCaption], ends the caption gap before the stack, as LEFT's starts the gap after it).
 */
internal fun StackSide.aligned(alignment: VerticalListAlignment, bodyW: Float): StackSide = when (alignment) {
    VerticalListAlignment.LEFT -> this
    VerticalListAlignment.RIGHT -> StackSide(w, h, centreX = bodyW - centreX, captionX = captionRight, captionRight = captionX)
}

private fun StackSide.geometry(bodyH: Float, scale: Float, gap: Float): StackGeometry = StackGeometry(
    slotW = w, slotH = h, scale = scale, gap = gap, cap = 0f,
    centreX = centreX, centreY = stackCentreY(bodyH, 0f), captionX = captionX, captionRight = captionRight,
)

/** JS Math.round: half up (kotlin.math.round is half-to-even, 196.5 → 196). */
internal fun jsRound(v: Float): Float = floor(v + 0.5f)
