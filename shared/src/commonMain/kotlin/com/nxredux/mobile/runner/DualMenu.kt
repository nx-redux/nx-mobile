package com.nxredux.mobile.runner

import com.nxredux.mobile.options.PxRect

/** Where the open in-game menu draws: over the game picture, or in the game panel's content area under its header. */
enum class MenuHost { PICTURE, PANEL }

/**
 * Where the open menu was put: on the second display or here ([onSecond]), in the panel or over the picture
 * ([inPanel]), and the second display's window then ([secondWindow], compared by identity): a window made again while
 * the menu is open has nothing on it yet, so the same flags on a new window are a new placement.
 */
data class MenuPlacement(val onSecond: Boolean, val inPanel: Boolean, val secondWindow: Any?)

/**
 * The in-game menu on dual screen. The bottom screen is where you act and the top one only informs: with the panel on
 * its own display the menu takes the panel's place, the picture left to show the paused game.
 */
object DualMenu {
    /**
     * [MenuHost.PANEL] while the panel is up beside the picture on another display (PANEL, PANEL_UP); else over the
     * picture, as on one screen (a DS / 3DS split has no panel, a skin's panel glass with no second display is one screen).
     */
    fun menuHost(mode: DualMode, panelShown: Boolean): MenuHost =
        if (panelShown && (mode == DualMode.PANEL || mode == DualMode.PANEL_UP)) MenuHost.PANEL else MenuHost.PICTURE

    /**
     * The menu lays out upright: for a pad skin's tall [glass] (here), else for an upright window here
     * ([portraitWindow]); never on the second display ([onSecond]), whose window is its own. The dual-screen panel in the
     * menu's place takes the same, so its page inset matches.
     */
    fun menuPortrait(glass: PxRect?, onSecond: Boolean, portraitWindow: Boolean): Boolean =
        glass?.let { it.height > it.width } ?: (!onSecond && portraitWindow)

    /**
     * The menu draws on the second display: the picture's display for [MenuHost.PICTURE], the other one for
     * [MenuHost.PANEL]. Never without a window there ([secondWindow]) or a dual mode.
     */
    fun menuOnSecond(host: MenuHost, mode: DualMode, pictureOn: PictureOn, secondWindow: Boolean): Boolean {
        if (!secondWindow || mode == DualMode.OFF) return false
        return when (host) {
            MenuHost.PICTURE -> pictureOn == PictureOn.SECOND
            MenuHost.PANEL -> pictureOn == PictureOn.MAIN
        }
    }
}
