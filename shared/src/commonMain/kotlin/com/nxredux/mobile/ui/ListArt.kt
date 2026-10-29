package com.nxredux.mobile.ui

import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.ui.nx.pageGutter

/** What a List-style game list draws behind or beside its names (Layouts → List art). */
sealed interface ListArtShown {
    /** The full-screen picture ([gameBackground]); null: the NX mark, as an empty list shows. */
    data class Full(val url: String?) : ListArtShown
    /** [url] whole, in a box on the right over plain black; [fallback] (the full-screen picture) if it fails to load. */
    data class Boxed(val url: String, val fallback: String?) : ListArtShown
    /** No picture and no background: plain black. */
    data object None : ListArtShown
}

/**
 * The List art for the highlighted [e] by Layouts → List art ([art]): Screenshot, the full-screen picture; Mix, 3D
 * box art, 2D box art or Wheel, that picture in a box on the right, a game without it showing its full-screen picture
 * (the handheld's ROM_findListArt); None, nothing at all, whatever is highlighted.
 */
internal fun listArtShown(e: Listed?, art: GameListArt, portrait: Boolean): ListArtShown {
    if (art == GameListArt.NONE) return ListArtShown.None
    if (e == null) return ListArtShown.Full(null)
    val full = gameBackground(e, portrait)
    val boxed = when (art) {
        GameListArt.MIX -> e.game.mixRef
        GameListArt.BOX3D -> e.game.boxartRef
        GameListArt.BOX2D -> e.game.box2dRef
        GameListArt.WHEEL -> e.game.wheelRef
        GameListArt.SCREENSHOT, GameListArt.NONE -> null
    }
    return boxed?.let { ListArtShown.Boxed(it.value, full) } ?: ListArtShown.Full(full)
}

/** The List art box's share of the page's width, on the right. */
internal const val LIST_ART_WIDTH_SHARE = 0.38f

/** The List art box's share of the page's height, centred. */
internal const val LIST_ART_HEIGHT_SHARE = 0.7f

/** The box's end gutter in dp: the page's (16 with the portrait pad, 24 otherwise). */
internal fun listArtGutter(portrait: Boolean): Float = pageGutter(portrait)

/**
 * Where a List's names stop with a boxed List art, in dp from the list's end on a [widthDp] page: the box, its gutter
 * and an 8 dp gap. Applied whenever List art is a boxed value, so the names never jump as a game falls back to its screenshot.
 */
internal fun listArtLabelEnd(widthDp: Float, portrait: Boolean): Float = widthDp * LIST_ART_WIDTH_SHARE + listArtGutter(portrait) + 8f
