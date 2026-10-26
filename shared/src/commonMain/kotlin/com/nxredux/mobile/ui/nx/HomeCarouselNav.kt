package com.nxredux.mobile.ui.nx

/** What a d-pad press does on the Carousel Home ([homeCarouselStep]). */
sealed interface HomeCarouselMove {
    data class To(val index: Int) : HomeCarouselMove
    data class SwitchTab(val step: Int) : HomeCarouselMove
    data object Stay : HomeCarouselMove
}

/**
 * The Carousel Home's d-pad (NX Redux home_carousel_layout.c): rows 0 until [rowCount] are the carousel, the rest up to
 * [total] the tool dock. LEFT / RIGHT step along the row or the dock, no wrap; past either end, switch tab. DOWN from
 * the row: the dock square last used ([dockLast]), else its first; nothing without a dock. UP from the dock: the row's
 * item last used ([rowLast]); UP from the row stays. Stale memory is clamped.
 */
internal fun homeCarouselStep(index: Int, rowCount: Int, total: Int, button: NxButton, rowLast: Int, dockLast: Int?): HomeCarouselMove {
    val inDock = index >= rowCount
    val lo = if (inDock) rowCount else 0
    val hi = if (inDock) total - 1 else rowCount - 1
    return when (button) {
        NxButton.LEFT -> if (index > lo) HomeCarouselMove.To(index - 1) else HomeCarouselMove.SwitchTab(-1)
        NxButton.RIGHT -> if (index < hi) HomeCarouselMove.To(index + 1) else HomeCarouselMove.SwitchTab(1)
        NxButton.DOWN -> if (inDock || total <= rowCount) HomeCarouselMove.Stay
            else HomeCarouselMove.To((dockLast ?: rowCount).coerceIn(rowCount, total - 1))
        NxButton.UP -> if (inDock) HomeCarouselMove.To(rowLast.coerceIn(0, rowCount - 1)) else HomeCarouselMove.Stay
        else -> HomeCarouselMove.Stay
    }
}

