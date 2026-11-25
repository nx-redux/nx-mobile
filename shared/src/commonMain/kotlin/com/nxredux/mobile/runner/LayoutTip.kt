package com.nxredux.mobile.runner

/**
 * The tip that SELECT + Left / Right changes the DS / 3DS / Wii U screen layout (first-time players do not know the
 * chord), on each launch until the player hides it for good from its button ([ACTION]).
 */
enum class TipConsole { DS, N3DS, WIIU }

object LayoutTip {
    /** The layout chord and the swap of the screens, the same on the DS and the 3DS. */
    const val TEXT = "SELECT + Left / Right changes the screen layout\nL3 or SELECT + L swaps the screens"
    /** The Wii U's: L3 is a GamePad button there, so only the SELECT chord swaps. */
    const val WIIU_TEXT = "SELECT + Left / Right changes the screen layout\nSELECT + L swaps the screens"
    const val ACTION = "Don't show again"

    fun text(wiiu: Boolean): String = if (wiiu) WIIU_TEXT else TEXT

    /** Each console dismisses its own tip: a tip hidden on one says nothing about the others. */
    fun hidden(console: TipConsole, ds: Boolean, n3ds: Boolean, wiiu: Boolean): Boolean = when (console) {
        TipConsole.DS -> ds
        TipConsole.N3DS -> n3ds
        TipConsole.WIIU -> wiiu
    }

    /**
     * Due on a DS, 3DS or Wii U game ([n3ds]: any of them) unless [hidden], where the chord works: with no skin, or a
     * skin that lays the screens out by the layout ([skinLayouts]: a regular phone's landscape); a skin placing the
     * screens itself ([skinShown] without it) keeps the chord from the core.
     */
    fun due(n3ds: Boolean, skinShown: Boolean, skinLayouts: Boolean, hidden: Boolean): Boolean =
        n3ds && !hidden && (!skinShown || skinLayouts)
}
