package com.nxredux.mobile.runner

/**
 * The tip that SELECT + Left / Right changes the DS / 3DS screen layout (user: first-time players do not know the chord), on
 * each launch until the player hides it for good from its button ([ACTION]).
 */
object LayoutTip {
    /** The layout chord, and the console's swap of the screens (user: the same tip): the DS's R2, the 3DS's SELECT + R. */
    fun text(ds: Boolean): String = "SELECT + Left / Right changes the screen layout\n" + if (ds) "R2 swaps the screens" else "SELECT + R swaps the screens"
    const val ACTION = "Don't show again"


    /**
     * Due on a DS or 3DS game ([n3ds]: either) unless [hidden], where the chord works: with no skin, or a skin that lays the
     * screens out by the layout ([skinLayouts]: a regular phone's landscape); a skin placing the screens itself
     * ([skinShown] without it) keeps the chord from the core.
     */
    fun due(n3ds: Boolean, skinShown: Boolean, skinLayouts: Boolean, hidden: Boolean): Boolean =
        n3ds && !hidden && (!skinShown || skinLayouts)
}
