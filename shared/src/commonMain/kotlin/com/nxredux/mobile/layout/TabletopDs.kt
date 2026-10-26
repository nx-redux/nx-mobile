package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.NdsLayout
import kotlin.math.floor
import kotlin.math.min

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
 * pixels. The halves set aside half the [reach] each side ([setAsidePx] for a near-square tablet), nothing with a
 * [controller]. Each screen is centred in its area. Only a tablet-class [screen] has the width for the touch screen
 * between the pad halves; a phone-shaped foldable stacks both screens above the hinge instead.
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
): TabletopDsPlacement {
    val topArea = PxRect(0f, cutoutTop.toFloat(), windowW.toFloat(), (hingeTop - cutoutTop).toFloat())
    val bottomArea = PxRect(0f, hingeBottom.toFloat(), windowW.toFloat(), (windowH - hingeBottom).toFloat())
    if (!screen.isTablet) return stackedTop(topArea, kind)
    val aside = if (controller) 0f else setAsidePx(ScreenClass.NEAR_SQUARE_TABLET, reach)
    val bottomSpace = PxRect(aside, bottomArea.y, bottomArea.width - 2f * aside, bottomArea.height)

    val (topW, topH) = when (kind) {
        DsKind.DS -> wholeScale(topArea, DS_W, DS_H, atLeast1 = false)
        DsKind.N3DS -> fitted(topArea, N3DS_TOP_W / N3DS_H)
    }
    val (bottomW, bottomH) = when (kind) {
        DsKind.DS -> wholeScale(bottomSpace, DS_W, DS_H, atLeast1 = true)
        DsKind.N3DS -> fitted(bottomSpace, N3DS_BOTTOM_W / N3DS_H)
    }
    val top = centred(topArea, topW, topH)
    val bottom = centred(bottomArea, bottomW, bottomH)
    if (controller) return TabletopDsPlacement(TabletopDsMode.SPLIT, top, bottom, null, null)
    val half = clusterPlacement(bottomArea, bottom, unitPx, reach, density, toolRow = false)
    return TabletopDsPlacement(TabletopDsMode.SPLIT, top, bottom, half, half)
}

/** Both screens stacked in [area], touch screen under the top one: DS at one whole scale, 3DS as the 400 x 480 frame fitted. */
private fun stackedTop(area: PxRect, kind: DsKind): TabletopDsPlacement {
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
            Pair(
                PxRect(block.x, block.y, w, screenH),
                PxRect(block.x + NdsLayout.wholePx((w - bottomW) / 2f), block.y + screenH, bottomW, screenH),
            )
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

private fun centred(space: PxRect, w: Float, h: Float) =
    PxRect(
        space.x + NdsLayout.wholePx((space.width - w) / 2f),
        space.y + NdsLayout.wholePx((space.height - h) / 2f),
        w,
        h,
    )
