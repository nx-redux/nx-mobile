package com.nxredux.mobile.ui.skinlayout

import com.nxredux.mobile.settings.AppSettings
import com.nxredux.mobile.skin.SkinPlacement
import com.nxredux.mobile.skin.SkinTweak
import com.nxredux.mobile.skin.SkinTweaks

/**
 * One layout's edit as both hosts open and save it (Appearance → Button layout, the in-game Adjust buttons): the stored
 * tweaks for a console's layout in, the edited ones back into [AppSettings.skinTweaks].
 */
object SkinLayoutEdit {
    /**
     * The editor's state for [placement] (as placed for a [w] x [h] window before any tweak, its stick or d-pad picked),
     * starting from [consoleId]'s stored tweaks for its layout. Never a tweaked placement: the tweaks would add up.
     */
    fun state(settings: AppSettings, consoleId: String, placement: SkinPlacement, w: Int, h: Int): SkinEditorState =
        SkinEditorState(placement, SkinTweaks.of(settings.skinTweaks, consoleId, placement.spec.id), w.toFloat(), h.toFloat())

    /** [tweaks] as [consoleId]'s for [layoutId] ([SkinTweaks.withLayout]); the rest stays as stored. */
    fun save(settings: AppSettings, consoleId: String, layoutId: String, tweaks: Map<String, SkinTweak>) {
        settings.skinTweaks = SkinTweaks.withLayout(settings.skinTweaks, consoleId, layoutId, tweaks)
    }
}
