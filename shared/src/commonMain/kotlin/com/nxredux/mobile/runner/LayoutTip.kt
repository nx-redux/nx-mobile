package com.nxredux.mobile.runner

/**
 * The tip that SELECT + Left / Right changes the DS / 3DS screen layout (first-time players do not know the chord), on
 * each launch until the player hides it for good from its button ([ACTION]).
 */
object LayoutTip {
    /** The layout chord and the swap of the screens, the same on the DS and the 3DS. */
    const val TEXT = "SELECT + Left / Right changes the screen layout\nL3 or SELECT + L swaps the screens"
    const val ACTION = "Don't show again"


    /**
     * Due on a DS or 3DS game ([n3ds]: either) unless [hidden], where the chord works: with no skin, or a skin that lays the
     * screens out by the layout ([skinLayouts]: a regular phone's landscape); a skin placing the screens itself
     * ([skinShown] without it) keeps the chord from the core.
     */
    fun due(n3ds: Boolean, skinShown: Boolean, skinLayouts: Boolean, hidden: Boolean): Boolean =
        n3ds && !hidden && (!skinShown || skinLayouts)
}
