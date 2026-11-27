package com.nxredux.mobile.runner

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions

/**
 * The DS and 3DS Download Play clients draw on the top screen alone: until the game they receive starts, both rows show
 * only that screen, then the player's own layout again.
 */
object DownloadPlayScreen {
    val N3DS_LAYOUT_KEYS = listOf(N3dsOptions.LAYOUT_PORTRAIT, N3dsOptions.LAYOUT_PORTRAIT_CONTROLLER, N3dsOptions.LAYOUT_LANDSCAPE)

    private fun keys(n3ds: Boolean): List<String> =
        if (n3ds) N3DS_LAYOUT_KEYS else NdsOptions.LAYOUT_KEYS.toList() + NdsOptions.BIG_SCREEN

    /** [values] with the top screen alone in every orientation. */
    fun single(values: Map<String, String>, n3ds: Boolean): Map<String, String> =
        if (n3ds) values + N3DS_LAYOUT_KEYS.associateWith { "single_screen" }
        else values + NdsOptions.LAYOUT_KEYS.associateWith { "single" } + (NdsOptions.BIG_SCREEN to "top")

    /** [now] with the layout of [stored] back: a row that had no pick has none again, so the screen's default applies. */
    fun restored(now: Map<String, String>, stored: Map<String, String>, n3ds: Boolean): Map<String, String> {
        val layout = keys(n3ds)
        return now.filterKeys { it !in layout } + stored.filterKeys { it in layout }
    }
}
