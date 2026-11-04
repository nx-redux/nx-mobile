package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.DualMode
import com.nxredux.mobile.runner.PictureOn

/** Where the game panel goes: nowhere, in a skin's glass, over the whole main window, or on the second display. */
enum class PanelSpot { NONE, SKIN_GLASS, WINDOW, SECOND_DISPLAY }

/**
 * The pad skin with the dual screen: whether the skin shows in a [DualMode], which of its glasses holds the game panel,
 * and where the panel goes. The panel is in one place only.
 */
object SkinDual {
    /**
     * The skin shows with no second display, with a one-screen game's panel (PANEL), and for a DS split (one DS screen
     * on main, in the skin's glass). Never for a 3DS, nor with both DS screens side by side on main (PANEL_UP).
     */
    fun skinAllowed(mode: DualMode, ds: Boolean, n3ds: Boolean): Boolean = !n3ds && when (mode) {
        DualMode.OFF, DualMode.PANEL -> true
        DualMode.SPLIT -> ds
        DualMode.PANEL_UP -> false
    }

    /**
     * The glass of [p] that holds the panel: the fold board's panel glass with no second display; the picture glass
     * (the landscape skin's middle screen) while the picture is on the second display; else none.
     */
    fun panelGlass(p: SkinPlacement?, mode: DualMode, pictureOn: PictureOn): PlacedGlass? = when {
        p == null -> null
        mode == DualMode.OFF -> p.glass(GlassScreen.PANEL)
        mode == DualMode.PANEL && pictureOn == PictureOn.SECOND -> p.glass(GlassScreen.PICTURE)
        else -> null
    }

    /**
     * Where the panel goes: in [glass] ([panelGlass]) when there is one; for PANEL / PANEL_UP with a second display's
     * window ([secondWindow]), on the touch screen: the second display while the picture is on main, else main (the
     * glass, or the whole window). SPLIT has no panel (its pill only).
     */
    fun panelSpot(mode: DualMode, pictureOn: PictureOn, secondWindow: Boolean, glass: PlacedGlass?): PanelSpot = when (mode) {
        DualMode.OFF -> if (glass != null) PanelSpot.SKIN_GLASS else PanelSpot.NONE
        DualMode.PANEL, DualMode.PANEL_UP -> when {
            !secondWindow -> PanelSpot.NONE
            pictureOn == PictureOn.MAIN -> PanelSpot.SECOND_DISPLAY
            glass != null -> PanelSpot.SKIN_GLASS
            else -> PanelSpot.WINDOW
        }
        DualMode.SPLIT -> PanelSpot.NONE
    }

    /** The biggest 256:192 (one DS screen) rect centred in [glass]. */
    fun dsScreenIn(glass: PxRect): PxRect {
        val aspect = 256f / 192f
        val w = minOf(glass.width, glass.height * aspect)
        val h = w / aspect
        return PxRect(glass.x + (glass.width - w) / 2f, glass.y + (glass.height - h) / 2f, w, h)
    }
}
