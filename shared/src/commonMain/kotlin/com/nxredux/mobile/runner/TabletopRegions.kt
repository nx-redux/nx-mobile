package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.DsKind
import com.nxredux.mobile.layout.TabletopDsPlacement
import com.nxredux.mobile.options.NdsScreen

/**
 * The half-folded DS / 3DS screens as regions of the core frame: [big] (the top screen, or the touch screen after SWAP)
 * at the placement's top rect, the other at its bottom rect, in the placement's pixels. The DS cuts its 256 x 384 stack
 * ([NdsLayout.src]); the 3DS its Stacked frame, the same 400 x 480 frame the Dual screen split cuts ([DualScreen.n3dsSrc]).
 * The touch screen takes touches wherever it is drawn. Only the top screen is shaded ([NdsLayout.takesShader]).
 */
fun tabletopRegions(p: TabletopDsPlacement, kind: DsKind, big: NdsScreen): List<NdsRegion> {
    fun src(screen: NdsScreen) = if (kind == DsKind.DS) NdsLayout.src(screen) else DualScreen.n3dsSrc(screen)
    val other = big.other
    return listOf(
        NdsRegion(big, src(big), p.top, shaded = NdsLayout.takesShader(big), touch = big == NdsScreen.BOTTOM),
        NdsRegion(other, src(other), p.bottom, shaded = NdsLayout.takesShader(other), touch = other == NdsScreen.BOTTOM),
    )
}
