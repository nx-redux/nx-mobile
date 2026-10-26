package com.nxredux.mobile.layout

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.NdsLayout
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Which two-screen console a half-folded layout is for. */
enum class DsKind { DS, N3DS }

/** SPLIT: top screen above the hinge, touch screen below it. STACKED_TOP: both screens stacked above the hinge. */
enum class TabletopDsMode { SPLIT, STACKED_TOP }

/**
 * Where the half-folded Fold puts a DS / 3DS. SPLIT: the [top] screen above the hinge, the [bottom] (touch) screen
 * below it, and the pad halves ([left], [right]) beside the bottom screen; both clusters are null with a controller.
 * STACKED_TOP: [top] and [bottom] are the two screens stacked in the area above the hinge and the clusters are null
 * (the normal portrait pad sits below the hinge).
 */
data class TabletopDsPlacement(
    val mode: TabletopDsMode,
    val top: PxRect,
    val bottom: PxRect,
    val left: ClusterPlacement?,
    val right: ClusterPlacement?,
)

private const val DS_W = 256f
private const val DS_H = 192f
private const val N3DS_TOP_W = 400f
private const val N3DS_BOTTOM_W = 320f
private const val N3DS_H = 240f
private const val EPSILON = 0.001f

/**
 * The top screen fills the area above the hinge (x 0..[windowW], [cutoutTop]..[hingeTop]); the bottom screen sits in
 * the area below it ([hingeBottom]..[windowH]) between the pad halves. DS screens are whole multiples of 256 x 192
 * (the bottom never below 1x); 3DS screens are fitted at their aspect (400 x 240 top, 320 x 240 bottom) in whole
 * pixels. The halves set aside [LayoutConstants.TABLETOP_DS_SET_ASIDE] of the [reach] each side (the touch screen
 * overlaps each by a quarter of it at most), nothing with a [controller]. The screens meet at the fold, as on a DS:
 * the top one's bottom edge on [hingeTop], the touch screen's top edge on [hingeBottom], each centred across.
 * A hinge reading that leaves no room for the split (a 1x DS touch screen, or any 3DS one, between the halves below
 * it, or the top screen above it) returns STACKED_TOP: on a tablet-class window that reading is not used for the DS
 * layout, and the game lays out flat instead ([usableTabletop]). Only a tablet-class [screen] has the width for the touch screen
 * between the pad halves; a phone-shaped foldable stacks both screens above the hinge instead. [secondRow]: the halves
 * draw their second row (STYLUS / SWAP), so their boxes are that row taller, as the landscape pad's. [big]: the screen
 * drawn first (above the hinge, or on top in the stack); after a 3DS SWAP the touch screen, so each rect is fitted to
 * the aspect of the screen it shows (the DS's two screens share one).
 */
fun tabletopDs(
    windowW: Int,
    windowH: Int,
    screen: ScreenClass,
    cutoutTop: Int,
    hingeTop: Int,
    hingeBottom: Int,
    kind: DsKind,
    unitPx: Float,
    reach: Reach,
    density: Float,
    controller: Boolean,
    secondRow: Boolean = false,
    big: NdsScreen = NdsScreen.TOP,
): TabletopDsPlacement {
    val topArea = PxRect(0f, cutoutTop.toFloat(), windowW.toFloat(), (hingeTop - cutoutTop).toFloat())
    val bottomArea = PxRect(0f, hingeBottom.toFloat(), windowW.toFloat(), (windowH - hingeBottom).toFloat())
    if (!screen.isTablet) return stackedTop(topArea, kind, big)
    val aside = if (controller) 0f else LayoutConstants.TABLETOP_DS_SET_ASIDE * reach.total
    val bottomSpace = PxRect(aside, bottomArea.y, bottomArea.width - 2f * aside, bottomArea.height)

    val (topW, topH) = when (kind) {
        DsKind.DS -> wholeScale(topArea, DS_W, DS_H, atLeast1 = true)
        DsKind.N3DS -> fitted(topArea, n3dsAspect(big))
    }
    val (bottomW, bottomH) = when (kind) {
        DsKind.DS -> wholeScale(bottomSpace, DS_W, DS_H, atLeast1 = true)
        DsKind.N3DS -> fitted(bottomSpace, n3dsAspect(big.other))
    }
    // The touch screen must fit below the hinge between the halves, and the top screen above it (DS: 1x at least).
    if (bottomW < 1f || bottomH < 1f || bottomW > bottomSpace.width || bottomH > bottomSpace.height ||
        topW < 1f || topH < 1f || topH > topArea.height
    ) return stackedTop(topArea, kind, big)
    val top = atFold(topArea, topW, topH, y = hingeTop - topH)
    val bottom = atFold(bottomArea, bottomW, bottomH, y = hingeBottom.toFloat())
    if (controller) return TabletopDsPlacement(TabletopDsMode.SPLIT, top, bottom, null, null)
    // One placement for both halves: the caller mirrors it to the right edge.
    val half = clusterPlacement(bottomArea, bottom, unitPx, reach, density, toolRow = secondRow)
    return TabletopDsPlacement(TabletopDsMode.SPLIT, top, bottom, half, half)
}

/** A 3DS screen's width over height: 400 x 240 on top, 320 x 240 the touch screen. */
private fun n3dsAspect(screen: NdsScreen): Float = (if (screen == NdsScreen.TOP) N3DS_TOP_W else N3DS_BOTTOM_W) / N3DS_H

/**
 * Both screens stacked in [area], [big] first: DS at one whole scale, 3DS as the 400 x 480 frame fitted, the touch
 * screen at its narrower width centred wherever it is.
 */
private fun stackedTop(area: PxRect, kind: DsKind, big: NdsScreen): TabletopDsPlacement {
    val (top, bottom) = when (kind) {
        DsKind.DS -> {
            val fit = floor(min(area.width / DS_W, area.height / (2f * DS_H)) + EPSILON).coerceAtLeast(1f)
            val block = centred(area, DS_W * fit, 2f * DS_H * fit)
            Pair(
                PxRect(block.x, block.y, block.width, DS_H * fit),
                PxRect(block.x, block.y + DS_H * fit, block.width, DS_H * fit),
            )
        }
        DsKind.N3DS -> {
            val (w, h) = fitted(area, N3DS_TOP_W / (2f * N3DS_H))
            val block = centred(area, w, h)
            val screenH = NdsLayout.wholePx(h / 2f)
            val bottomW = NdsLayout.wholePx(w * N3DS_BOTTOM_W / N3DS_TOP_W)
            fun widthOf(s: NdsScreen) = if (s == NdsScreen.TOP) w else bottomW
            fun at(s: NdsScreen, y: Float) = PxRect(block.x + NdsLayout.wholePx((w - widthOf(s)) / 2f), y, widthOf(s), screenH)
            Pair(at(big, block.y), at(big.other, block.y + screenH))
        }
    }
    return TabletopDsPlacement(TabletopDsMode.STACKED_TOP, top, bottom, null, null)
}

/** The largest whole multiple of [w] x [h] inside [space] (1x at least when [atLeast1]). */
private fun wholeScale(space: PxRect, w: Float, h: Float, atLeast1: Boolean): Pair<Float, Float> {
    val fit = floor(min(space.width / w, space.height / h) + EPSILON).coerceAtLeast(if (atLeast1) 1f else 0f)
    return Pair(w * fit, h * fit)
}

/** The biggest [aspect] (width / height) rectangle inside [space], whole pixels, by the repo's rounding. */
private fun fitted(space: PxRect, aspect: Float): Pair<Float, Float> =
    if (space.width / aspect <= space.height) {
        Pair(NdsLayout.wholePx(space.width), NdsLayout.wholePx(space.width / aspect))
    } else {
        Pair(NdsLayout.wholePx(space.height * aspect), NdsLayout.wholePx(space.height))
    }

/** A [w] x [h] screen centred across [space] at [y] (against the fold). */
private fun atFold(space: PxRect, w: Float, h: Float, y: Float) =
    PxRect(space.x + NdsLayout.wholePx((space.width - w) / 2f), y, w, h)

private fun centred(space: PxRect, w: Float, h: Float) =
    PxRect(
        space.x + NdsLayout.wholePx((space.width - w) / 2f),
        space.y + NdsLayout.wholePx((space.height - h) / 2f),
        w,
        h,
    )

/**
 * The half-folded DS / 3DS pad halves' boxes, px of a root that fills the window: each [widthPx] x [heightPx] at
 * [topMarginPx] from the top, against its own side edge (the right half mirrors the left); [insetPx] moves the buttons
 * in from that edge.
 */
data class TabletopDsPadBoxes(val widthPx: Int, val heightPx: Int, val topMarginPx: Int, val insetPx: Float)

/**
 * The pad halves' boxes for placement [p] (window px) in a root [rootH] tall, kept inside it; null when [p] has no
 * halves (a controller, or both screens stacked above the hinge with the portrait pad below).
 */
fun tabletopDsPadBoxes(p: TabletopDsPlacement, rootH: Int): TabletopDsPadBoxes? {
    val half = p.left ?: return null
    val h = half.boxHeightPx.roundToInt().coerceAtMost(rootH)
    val top = half.topMarginPx.roundToInt().coerceIn(0, rootH - h)
    return TabletopDsPadBoxes(half.boxWidthPx.roundToInt(), h, top, half.extraInsetPx)
}

/**
 * The Pad layout editor's half-folded preview on its window: the screens' [placement], the [hingeY] it splits at, the
 * pad's [unitPx] (the cover screen's button size) and the halves' [boxes].
 */
data class HalfFoldedPreview(val placement: TabletopDsPlacement, val hingeY: Float, val unitPx: Float, val boxes: TabletopDsPadBoxes)

/**
 * The half-folded DS / 3DS layout as the game draws it ([tabletopDs], [tabletopDsPadBoxes]) on a [windowW] x [windowH]
 * editor window folded at its middle, for the Pad layout editor, which runs no game: [kind]'s screens, the estimated
 * reach (nothing is measured there), no controller. A phone-shaped [screen] is laid out as a tablet-class one, so the
 * halves always show. Null when the window has no room for them. Arguments as [besideMeasures].
 */
fun halfFoldedPreview(
    windowW: Float,
    windowH: Float,
    screen: ScreenClass,
    cutoutTop: Float,
    ydpi: Float,
    density: Float,
    cutoutLeft: Float,
    cutoutRight: Float,
    secondRow: Boolean,
    faceUnits: Float,
    systemPills: Int,
    kind: DsKind,
    mic: Boolean = false,
): HalfFoldedPreview? {
    val unit = tabletopDsUnitPx(ydpi, density)
    val r = reach(unit, density, cutoutLeft, cutoutRight, null, faceUnits, systemPills, mic)
    val hinge = (windowH / 2f).roundToInt()
    val tablet = if (screen.isTablet) screen else ScreenClass.NEAR_SQUARE_TABLET
    val p = tabletopDs(
        windowW.roundToInt(), windowH.roundToInt(), tablet, cutoutTop.roundToInt(), hinge, hinge, kind, unit, r, density,
        controller = false, secondRow = secondRow,
    )
    val boxes = tabletopDsPadBoxes(p, windowH.roundToInt()) ?: return null
    return HalfFoldedPreview(p, hinge.toFloat(), unit, boxes)
}

/**
 * Whether a half-folded DS / 3DS [placement] can be used on a window whose game view spans both halves ([spansHinge]:
 * the Fold's [com.nxredux.mobile.runner.TabletopPad.DS]): only as the split. A hinge reading the split cannot fit
 * (STACKED_TOP there) is not used: no pad would be drawn below and the stack could run past the top area, so the game
 * lays out flat, as when not half-folded. A phone-shaped window's stack (its game view above the hinge, the portrait
 * pad below) is always usable.
 */
fun usableTabletop(spansHinge: Boolean, placement: TabletopDsPlacement): Boolean =
    !spansHinge || placement.mode == TabletopDsMode.SPLIT
