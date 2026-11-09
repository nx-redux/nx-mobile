package com.nxredux.mobile.ui.skinlayout

import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweaks
import com.nxredux.mobile.skin.TweakRules

/**
 * One layout's edit as both hosts open and save it (Appearance → Button layout, the in-game Adjust buttons): the stored
 * tweaks for a console's layout in, the edited ones back into [AppSettings.skinTweaks] or, for the overlay (Pad skin
 * Off), [AppSettings.overlayTweaks].
 */
object SkinLayoutEdit {
    /**
     * The editor's state for [placement] (as placed for a [w] x [h] window before any tweak, its stick or d-pad picked),
     * starting from [consoleId]'s stored tweaks for its layout; [overlay] edits the overlay's, which start as the skin's
     * where the player never edited a group there. [minTouchPx] is the smallest a control's hit box may shrink to.
     * Never a tweaked placement: the tweaks would add up.
     */
    fun state(settings: AppSettings, consoleId: String, placement: SkinPlacement, w: Int, h: Int, overlay: Boolean, minTouchPx: Float): SkinEditorState {
        val layoutId = placement.spec.id
        val skin = SkinTweaks.of(settings.skinTweaks, consoleId, layoutId)
        return if (!overlay) SkinEditorState(placement, skin, w.toFloat(), h.toFloat(), TweakRules.SKIN, minTouchPx)
        else SkinEditorState(
            placement, SkinTweaks.overlayOf(settings.skinTweaks, settings.overlayTweaks, consoleId, layoutId), w.toFloat(), h.toFloat(),
            TweakRules.OVERLAY, minTouchPx, skin, SkinTweaks.of(settings.overlayTweaks, consoleId, layoutId).keys,
        )
    }

    /** [state]'s [SkinEditorState.saved] as [consoleId]'s for [layoutId] ([SkinTweaks.withLayout]); the rest stays as stored. */
    fun save(settings: AppSettings, consoleId: String, layoutId: String, state: SkinEditorState) {
        if (state.rules == TweakRules.OVERLAY) settings.overlayTweaks = SkinTweaks.withLayout(settings.overlayTweaks, consoleId, layoutId, state.saved())
        else settings.skinTweaks = SkinTweaks.withLayout(settings.skinTweaks, consoleId, layoutId, state.saved())
    }
}
