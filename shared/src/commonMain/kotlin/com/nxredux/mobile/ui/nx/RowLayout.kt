package com.nxredux.mobile.ui.nx

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Carousel / Backdrop: LEFT/RIGHT one item; null past an end (the main menu switches tab there); UP/DOWN nothing.
 * The caller keeps [index] in `0 until count`.
 */
internal fun rowStep(index: Int, count: Int, button: NxButton): Int? {
    if (count <= 0) return index
    return when (button) {
        NxButton.LEFT -> if (index > 0) index - 1 else null
        NxButton.RIGHT -> if (index < count - 1) index + 1 else null
        else -> index
    }
}

internal enum class RowStyle { CAROUSEL, BACKDROP }

/**
 * A row's measures in dp: the selected item's size, the neighbours' scale, the gap, the row's top below the tabs.
 * [taper]: each side item past the first is this much of the one before ([sideScale]); 1, all sides at [scale].
 */
internal data class RowGeometry(val itemW: Float, val itemH: Float, val scale: Float, val gap: Float, val top: Float, val taper: Float = 1f)

/** Steps out past which a tapered row's side items stop shrinking ([RowGeometry.sideScale]; from four on they are hidden). */
internal const val TAPER_STEPS = 3f

/** The selection's centre to its first neighbour's: the selection's half, the gap and the neighbour's half. */
internal fun RowGeometry.rowFirstStep(): Float = itemW / 2 + gap + itemW * scale / 2

/**
 * The scale of an item [d] steps (≥ 0) from the selection: 1 at it, linear to [RowGeometry.scale] one out, then
 * × [RowGeometry.taper] per step, continuously, held from [TAPER_STEPS] on. Continuous, so a sliding item's size never jumps.
 */
internal fun RowGeometry.sideScale(d: Float): Float = when {
    d <= 1f -> 1f + (scale - 1f) * d
    else -> scale * taper.pow(min(d, TAPER_STEPS) - 1f)
}

/**
 * The centre of an item [d] steps (≥ 0) from the selection's. The first step is linear: the selection's half, the gap
 * and a neighbour's half. Past it, the running sum of the side items' widths ([sideScale]) and gaps,
 * ∫₁ᵈ (itemW · sideScale(u) + gap) du, so a tapered item's speed follows its size and never changes in a jump.
 * Untapered: a neighbour and the gap per step, the neighbours at rest [RowGeometry.gap] apart edge to edge (a tapered
 * row's within a dp).
 */
internal fun RowGeometry.restDx(d: Float): Float {
    var x = rowFirstStep() * min(d, 1f)
    if (d > 1f) {
        val b = min(d, TAPER_STEPS) - 1f
        x += itemW * scale * (if (taper == 1f) b else (taper.pow(b) - 1f) / ln(taper)) + gap * b
    }
    if (d > TAPER_STEPS) x += (itemW * sideScale(TAPER_STEPS) + gap) * (d - TAPER_STEPS)
    return x
}

/**
 * The row at full size, one size in both orientations (the landscape size; dp is about physical size, so a row looks the same on
 * every screen), for a body [bodyH] tall with [captionDp] under the row (the Carousel's measured caption room; Backdrop's
 * games caption gap and reserve; 0 for the main menu's rows). Never shrunk for width: a narrow body shows less of the
 * neighbours. Shrunk only to fit the height ([rowFactor]). [logoSlot]: Backdrop's consoles (the wide logo slot instead of
 * the box art slot); the Tools and Collections tabs' is [backdropSlotGeometry].
 */
internal fun rowGeometry(style: RowStyle, logoSlot: Boolean, bodyH: Float, captionDp: Float = 0f): RowGeometry {
    val spec = when (style) {
        RowStyle.CAROUSEL -> RowGeometry(itemW = 340f, itemH = 240f, scale = 0.62f, gap = 16f, top = 20f)
        // The Consoles logo slot (330 × 130): the selected console at [LOGO_SELECTED_SCALE] of it, its side logos at
        // [LOGO_SIDE_SCALE], so the focused console stands out (the handheld's way).
        RowStyle.BACKDROP -> if (logoSlot) RowGeometry(
            itemW = 330f * LOGO_SELECTED_SCALE, itemH = 130f * LOGO_SELECTED_SCALE,
            scale = LOGO_SIDE_SCALE / LOGO_SELECTED_SCALE, gap = 28f, top = 92f,
        )
            else RowGeometry(itemW = 170f, itemH = 150f, scale = 0.62f, gap = 28f, top = 92f)
    }
    return spec.scaledBy(rowFactor(spec.itemH, bodyH, captionDp))
}

/**
 * The height-fit factor f = min(1, (body − ring room − caption) / the full-size [itemH]): the selected item, its
 * [GRID_RING_ROOM] and the [captionDp] under it fit the body [bodyH], the row scaled down uniformly just enough; never
 * grown past full size. The caption is measured at the UI and font scale, so it fits at any.
 */
internal fun rowFactor(itemH: Float, bodyH: Float, captionDp: Float): Float =
    ((bodyH - GRID_RING_ROOM - captionDp) / itemH).coerceIn(0f, 1f)

private fun RowGeometry.scaledBy(f: Float): RowGeometry = copy(itemW = itemW * f, itemH = itemH * f, gap = gap * f, top = top * f)

/**
 * A Backdrop row's slot: games' box art, the wide logo slot (Consoles), the tool slot (Tools), or the collection slot
 * (Collections: the tool slot's height and gap, wider, its sides at [COLLECTION_SIDE_SCALE]).
 */
internal enum class BackdropSlot { BOX, LOGO, TOOL, COLLECTION }

/**
 * The Backdrop row's geometry for [slot] on a body [bodyH] tall with [captionDp] under it, one size in both orientations
 * as [rowGeometry]. The tool slot is sized to the tool, not the logo: 150 × 160, gap
 * 14, sides at 0.62, so the focused tool and two each side sit close together. The Collections tab's slot has the same
 * height and gap but is [COLLECTION_SLOT_W] wide, so a name shrinks less; its sides are at [COLLECTION_SIDE_SCALE], so
 * the selected name is twice its neighbours'. Both shrink with [rowFactor] as the other slots. [BackdropSlot.LOGO] is
 * [rowGeometry]'s; [BackdropSlot.BOX] is the box art's base size before NxBackdrop grows it into the games band.
 */
internal fun backdropSlotGeometry(slot: BackdropSlot, bodyH: Float, captionDp: Float = 0f, portraitW: Float? = null): RowGeometry = when (slot) {
    BackdropSlot.BOX -> rowGeometry(RowStyle.BACKDROP, false, bodyH, captionDp)
    // On a portrait body [portraitW] wide, the console's pad held to [CONSOLE_PAD_MAX_SHARE] of it.
    BackdropSlot.LOGO -> rowGeometry(RowStyle.BACKDROP, true, bodyH, captionDp).let { g -> portraitW?.let { g.scaledBy(consolePortraitFit(g.itemH, it)) } ?: g }
    BackdropSlot.TOOL, BackdropSlot.COLLECTION -> RowGeometry(
        itemW = if (slot == BackdropSlot.COLLECTION) COLLECTION_SLOT_W else TOOL_SLOT_W, itemH = TOOL_SLOT_H,
        scale = if (slot == BackdropSlot.COLLECTION) COLLECTION_SIDE_SCALE else 0.62f, gap = TOOL_SLOT_GAP, top = 92f,
    ).scaledBy(rowFactor(TOOL_SLOT_H, bodyH, captionDp))
}

private const val LOGO_SIDE_SCALE = 0.45f
/** The Consoles row's selected console, of the 330 × 130 logo slot; the side logos keep [LOGO_SIDE_SCALE] of it. */
internal const val LOGO_SELECTED_SCALE = 0.8f
/** The Collections row's side items' scale: half the selected name's size. */
internal const val COLLECTION_SIDE_SCALE = 0.5f
internal const val TOOL_SLOT_W = 150f
/** The Collections row's slot width before [rowFactor] (the landscape 240, in both orientations). */
internal const val COLLECTION_SLOT_W = 240f
private const val TOOL_SLOT_H = 160f
private const val TOOL_SLOT_GAP = 14f

/**
 * The gap between neighbouring content-sized items (the Carousel's Tools and Collections), times the slot's
 * ([TOOL_SLOT_GAP]): the handheld's 2x (its Row_itemDxVar), as the Brick's.
 */
internal const val CONTENT_SLOT_GAP_K = 2f

/**
 * Item [index]'s centre offset (dp) from the row's centre, as the handheld's Row_itemDxVar, when each item is
 * [widths] dp wide at full size (the Carousel's Tools and Collections, [CONTENT_SLOT_GAP_K]): the selection full size,
 * the others at [RowGeometry.scale], [RowGeometry.gap] between neighbouring edges whatever their widths. At rest on an
 * item it is centred; between two rests either side of [pos] each centre moves linearly (beyond the ends, on along the
 * end pair's line). Equal widths: [rowItem]'s dx exactly. One item (or an index outside) follows [pos] at its width.
 */
internal fun rowItemDxVar(g: RowGeometry, index: Int, pos: Float, widths: FloatArray): Float {
    val n = widths.size
    if (n <= 1 || index !in 0 until n) return (index - pos) * ((widths.firstOrNull() ?: g.itemW) * g.scale + g.gap)
    val k0 = floor(pos).toInt().coerceIn(0, n - 2)
    val f = pos - k0
    return (1 - f) * rowRestDx(g, k0, index, widths) + f * rowRestDx(g, k0 + 1, index, widths)
}

/** Item [i]'s centre with the selection resting on [k] ([rowItemDxVar]). */
private fun rowRestDx(g: RowGeometry, k: Int, i: Int, widths: FloatArray): Float {
    if (i == k) return 0f
    val dir = if (i > k) 1 else -1
    var x = widths[k] / 2 + g.gap
    var j = k + dir
    while (j != i) { x += widths[j] * g.scale + g.gap; j += dir }
    return dir * (x + widths[i] * g.scale / 2)
}

/** A tool tile's icon (dp), name (sp) and the gap between them (dp); the pair is centred as one group. */
internal data class ToolTileSize(val iconDp: Float, val nameSp: Float, val gapDp: Float)

/** The Grid's tool tile: a 46 dp icon (38 in portrait) and a 13 sp name, 6 dp apart. */
internal fun gridToolSize(portrait: Boolean): ToolTileSize =
    if (portrait) ToolTileSize(38f, 13f, 6f) else ToolTileSize(46f, 13f, 6f)

private const val TOOL_ICON_SHARE = 0.3f   // a Carousel tool icon's height, of the tile's
private const val TOOL_PAD = 8f            // the tool tile's side padding, dp (NxMenuTile)
// The longest tool name line, "Achievements" (12 characters), in ems of the tile font's SemiBold, a little generous.
internal const val TOOL_LONGEST_EM = 12 * 0.62f

/**
 * A Carousel tool tile, scaled with the tile [g]: the icon [TOOL_ICON_SHARE] of its height (72 dp at the full-size 240),
 * the name 20 sp at full size, shrinking with the tile, in both orientations, and never wider than "Achievements" fits in the
 * tile (so "Retro / Achievements" keeps its two lines).
 */
internal fun carouselToolSize(g: RowGeometry): ToolTileSize {
    val icon = TOOL_ICON_SHARE * g.itemH
    val name = min(20f * g.itemH / 240f, (g.itemW - 2 * TOOL_PAD) / TOOL_LONGEST_EM)
    return ToolTileSize(icon, name, icon / 8)
}

/** One item of the row at [distance] (index − animated position) from the selection. */
internal data class RowItem(val dx: Float, val scale: Float, val dim: Float, val alpha: Float, val visible: Boolean)

/**
 * The item at [distance]: its centre's offset from the selection's ([RowGeometry.restDx]), its scale (1 at the centre,
 * [RowGeometry.scale] one step out, tapering on by [RowGeometry.sideScale]), and how it recedes: Carousel darkens (a black layer 0.45 + 0.15 × d, at most
 * 0.85), Backdrop fades (0.5 one step out, −0.12 per step, never below 0.2). Between three and four steps it fades
 * out (Carousel's tile alpha 4 − d at the capped dim, Backdrop's alpha 0.26 × (4 − d)), so nothing pops; from four
 * steps on it is hidden.
 */
internal fun rowItem(style: RowStyle, distance: Float, g: RowGeometry): RowItem {
    val d = abs(distance)
    val sign = if (distance < 0) -1f else 1f
    val t = min(d, 1f)
    val dx = sign * g.restDx(d)
    val scale = g.sideScale(d)
    return when (style) {
        RowStyle.CAROUSEL -> {
            val dim = if (d <= 1f) 0.60f * t else min(0.85f, 0.45f + 0.15f * d)
            RowItem(dx, scale, dim, alpha = if (d <= 3f) 1f else 4f - d, visible = d < 4f)
        }
        RowStyle.BACKDROP -> {
            val alpha = when {
                d <= 1f -> 1f - 0.5f * t
                d <= 3f -> max(0.2f, 0.5f - 0.12f * (d - 1f))
                else -> 0.26f * (4f - d)
            }
            RowItem(dx, scale, dim = 0f, alpha = alpha, visible = d < 4f)
        }
    }
}

/**
 * A main-menu Consoles or Collections item's opacity at [distance] (index − pos), the Carousel row and its vertical stack
 * alike: the selection 1, the first neighbour 0.35, 0.2 from two steps out, fading to 0 between 3 and
 * 4 steps (where [rowItem] hides it); linear between the stops, so it stays smooth while sliding. Tools and game lists
 * keep [rowItem]'s.
 */
internal fun mainMenuSideAlpha(distance: Float): Float {
    val d = abs(distance)
    return when {
        d <= 1f -> 1f - (1f - MENU_FIRST_ALPHA) * d
        d <= 2f -> MENU_FIRST_ALPHA - (MENU_FIRST_ALPHA - MENU_FAR_ALPHA) * (d - 1f)
        d <= 3f -> MENU_FAR_ALPHA
        else -> max(0f, MENU_FAR_ALPHA * (4f - d))
    }
}

/** Whether a main-menu row of [slot] fades its sides with [mainMenuSideAlpha]: Consoles (logo) and Collections. */
internal fun fadesFurther(slot: BackdropSlot): Boolean = slot == BackdropSlot.LOGO || slot == BackdropSlot.COLLECTION

internal const val MENU_FIRST_ALPHA = 0.35f
internal const val MENU_FAR_ALPHA = 0.2f

/**
 * The indices a row composes around the animated position [pos]: those less than four steps away (the only ones
 * [rowItem] shows), clamped to `0 until count`. Empty for no items.
 */
internal fun rowWindow(pos: Float, count: Int, reach: Float = 4f): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val first = (floor(pos - reach).toInt() + 1).coerceIn(0, count - 1)
    val last = (ceil(pos + reach).toInt() - 1).coerceIn(0, count - 1)
    return first..last
}

/**
 * How far a row or stack composes, in steps (for scrolling at 120 Hz): one past the [rowWindow] it shows, so the
 * item a step brings into view was composed (hidden) a step earlier, and the item a step brings in at the far end is
 * composed while still hidden.
 */
internal const val ROW_COMPOSE_REACH = 5f

/** The indices a row composes around [pos]: [rowWindow] at [ROW_COMPOSE_REACH]; the ones past four steps stay hidden. */
internal fun rowComposeWindow(pos: Float, count: Int): IntRange = rowWindow(pos, count, ROW_COMPOSE_REACH)

/**
 * A console logo's inset in a Carousel tile, (sides, top and bottom) in dp: the Grid's 22 × 30 dp, scaled by the row's
 * own factor (the tile's width over the full-size 340, both orientations), never grown.
 */
internal fun rowLogoInset(g: RowGeometry): Pair<Float, Float> {
    val f = min(1f, g.itemW / 340f)
    return Pair(22f * f, 30f * f)
}

/**
 * A Carousel / Backdrop row's top y (the centre tile's, or the Backdrop band's) in a body [bodyDp] tall: one block, the
 * ring or shadow room above ([GRID_RING_ROOM]), the row [itemH] and the caption room [captionDp] under it, centred in the
 * body, between the header and the hint bar (a row has no info band). The caller keeps the caption room on every tab,
 * so the row stays at one height on L1/R1. A body too short for the block keeps the row [GRID_RING_ROOM] under the
 * header.
 */
internal fun rowBlockTop(bodyDp: Float, itemH: Float, captionDp: Float): Float {
    val block = GRID_RING_ROOM + itemH + captionDp
    return max(GRID_RING_ROOM, (bodyDp - block) / 2 + GRID_RING_ROOM)
}

/**
 * For Layouts → Page title Hide: a Grid, Carousel or Backdrop (row or stack) takes the hidden page title's room
 * [titleRoomDp] into its own box, [boxDp] tall, instead of a spacer above it. Its items are sized from this body, the box
 * less the room (exactly the shown title's body), so their size, count and spacing don't change; its block is then
 * centred in the whole box, from where the title would be down to the hint bar (or its kept room), so it moves up by
 * half the room. No room (the title shown): the box itself.
 */
internal fun titleRoomSizingBody(boxDp: Float, titleRoomDp: Float): Float = (boxDp - titleRoomDp.coerceAtLeast(0f)).coerceAtLeast(0f)

/** A portrait game list Carousel's selected tile is at most this share of the body's width ([carouselRowPlace]). */
internal const val CAROUSEL_TILE_MAX_SHARE = 0.6f

/** A Carousel's first neighbour, of the selected tile, and each further one's of the one before ([carouselRowPlace]). */
internal const val CAROUSEL_SIDE_SCALE = 0.75f
internal const val CAROUSEL_TAPER = 0.8f

/** A Carousel's selected tile (Home's and a game list's) is never wider than this, dp (its height in proportion). */
internal const val CAROUSEL_MAX_W = 300f

/** A Carousel row's geometry (Home's and a game list's) and its top in its box ([carouselRowPlace]). */
internal data class CarouselRowPlace(val g: RowGeometry, val top: Float)

/**
 * The Carousel row (Home's and a game list's) in a box [boxDp] tall with [captionDp] of caption room under it: sized from the shown
 * title's body ([titleRoomSizingBody]: a hidden title's [titleRoomDp] left out), the block ([rowBlockTop])
 * centred in the whole box. On a portrait body [portraitW] wide, the selected tile (and the row's gaps) is
 * shrunk to at most [CAROUSEL_TILE_MAX_SHARE] of it, so its neighbours show either side. The first
 * neighbours are at [CAROUSEL_SIDE_SCALE], each further one [CAROUSEL_TAPER] of the one before. The selection is never
 * wider than [CAROUSEL_MAX_W].
 */
internal fun carouselRowPlace(boxDp: Float, titleRoomDp: Float, captionDp: Float, portraitW: Float? = null): CarouselRowPlace {
    val spec = rowGeometry(RowStyle.CAROUSEL, false, titleRoomSizingBody(boxDp, titleRoomDp), captionDp)
    val capped = spec.scaledBy(min(1f, CAROUSEL_MAX_W / spec.itemW))
    val sides = capped.copy(scale = CAROUSEL_SIDE_SCALE, taper = CAROUSEL_TAPER)
    val g = sides.let { g ->
        portraitW?.let { w -> g.scaledBy(min(1f, CAROUSEL_TILE_MAX_SHARE * w / g.itemW)) } ?: g
    }
    return CarouselRowPlace(g, rowBlockTop(boxDp, g.itemH, captionDp))
}

/**
 * A game-list row's tiles in its area ([areaDp] tall: the box, a hidden title's room included, the kept
 * hint-bar room not): an unselected tile, the image alone ([itemH], scaled about its centre), centred by itself.
 */
internal fun rowNeighbourTop(areaDp: Float, itemH: Float): Float = (areaDp - itemH) / 2

/**
 * The selected tile's top: the tile ([itemH]) and its caption as drawn ([drawnCaptionDp]: the name lines
 * that show and the info lines, 0 for none) [captionGap] under it, centred together in the area ([areaDp]). The
 * caption reserve it doesn't fill doesn't count, so the tile sits half the caption's drawn height (and its gap)
 * above its neighbours ([rowNeighbourTop]): one more name line moves it up half a line. A tile with no caption is
 * centred as a neighbour. Never above [GRID_RING_ROOM] (the lit ring's room), unless the neighbours are.
 */
internal fun rowSelectedTop(areaDp: Float, itemH: Float, captionGap: Float, drawnCaptionDp: Float): Float {
    val under = if (drawnCaptionDp > 0f) captionGap + drawnCaptionDp else 0f
    return ((areaDp - itemH - under) / 2).coerceAtLeast(min(GRID_RING_ROOM, rowNeighbourTop(areaDp, itemH)))
}

/**
 * A game list Carousel's selected tile top (dp, in its box) for the tile and its drawn caption ([rowSelectedTop]'s) to
 * sit centred on the screen's centre line [centreLine] (dp from the box's top), kept inside the box [areaDp].
 */
internal fun rowSelectedTopOn(centreLine: Float, areaDp: Float, itemH: Float, captionGap: Float, drawnCaptionDp: Float): Float {
    val under = if (drawnCaptionDp > 0f) captionGap + drawnCaptionDp else 0f
    val lo = min(GRID_RING_ROOM, rowNeighbourTop(areaDp, itemH))
    return (centreLine - (itemH + under) / 2).coerceAtMost(areaDp - itemH - under).coerceAtLeast(lo)
}

/**
 * A game list Carousel neighbour's top (dp, in its box) for its centre on the screen's centre line: [screenCentreY] and
 * the box's top [boxTopY] in window px ([density] px per dp), the tile [itemH] dp tall. Null until both are measured.
 */
internal fun screenCentreLineTop(screenCentreY: Float?, boxTopY: Float?, density: Float, itemH: Float): Float? {
    if (screenCentreY == null || boxTopY == null || density <= 0f) return null
    return (screenCentreY - boxTopY) / density - itemH / 2
}

/**
 * The top of the item at [distance] (index − animated position) between [neighbourTop] and [selectedTop]:
 * the selected one's at 0, the neighbours' from one step out, linear in between, the same weight as [rowItem]'s scale,
 * so the tile rises and grows (and sinks and shrinks) in step with the row's slide.
 */
internal fun rowItemTop(neighbourTop: Float, selectedTop: Float, distance: Float): Float {
    val w = 1f - min(abs(distance), 1f)
    return neighbourTop + (selectedTop - neighbourTop) * w
}
