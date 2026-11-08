package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect

/**
 * A DS / 3DS's two screens as regions of the core frame (a pad skin's glasses): [big] (the top screen, or the touch
 * screen after SWAP) at [top], the other at [bottom], in the game area's pixels. The DS cuts its 256 x 384 stack
 * ([NdsLayout.src]); the 3DS its Stacked frame, the same 400 x 480 frame the Dual screen split cuts ([DualScreen.n3dsSrc]).
 * The touch screen takes touches wherever it is drawn. Only the top screen is shaded ([NdsLayout.takesShader]).
 */
fun dsScreenRegions(top: PxRect, bottom: PxRect, kind: DsKind, big: NdsScreen): List<NdsRegion> {
    fun src(screen: NdsScreen) = if (kind == DsKind.DS) NdsLayout.src(screen) else DualScreen.n3dsSrc(screen)
    val other = big.other
    return listOf(
        NdsRegion(big, src(big), top, shaded = NdsLayout.takesShader(big), touch = big == NdsScreen.BOTTOM),
        NdsRegion(other, src(other), bottom, shaded = NdsLayout.takesShader(other), touch = other == NdsScreen.BOTTOM),
    )
}
