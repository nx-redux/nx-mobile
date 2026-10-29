package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.GridTile
import org.jetbrains.compose.resources.painterResource
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The main menu's Consoles, Collections and Tools tabs in the Grid style: each tile its mark on the ground (a console's
 * logo, a collection's name, a tool's icon) inside a faint edge ([MARK_EDGE_ALPHA]), no plate or ring.
 * Every logo fills the same optical area ([consoleLogoBox]), so a thin wordmark and a two-line logo read at one weight.
 * Unselected logos are the ink at [CONSOLE_DIM_ALPHA]; the selected one is the accent; the selection eases over
 * [NX_DRAIN_MS] ([MenuMarkCell]), in the Grid's rows ([NxTileGrid]).
 */

/** An unselected console logo's ink. */
private const val CONSOLE_DIM_ALPHA = 0.42f   // role: an unselected console logo

/** A Grid mark tile's edge: a touch over an unlit game tile's ([NxColors.inkEdge]), as the mark tile has no picture to edge. */
private const val MARK_EDGE_ALPHA = 0.12f   // role: a Grid mark tile's edge

/** A logo's optical area, as a share of its cell's, and the most of the cell's width and height it may take. */
private const val CONSOLE_LOGO_AREA = 0.15f
private const val CONSOLE_LOGO_MAX_W = 0.82f
private const val CONSOLE_LOGO_MAX_H = 0.5f

/** A tool's name's gap under its icon. */
private val TOOL_NAME_GAP = 6.dp

/**
 * The box a logo [aspect] (height over width) is drawn in, dp, in a cell [cellW] × [cellH]: the same area for every logo
 * ([CONSOLE_LOGO_AREA] of the cell's), so a thin wordmark grows and a tall two-line logo shrinks, held within
 * [CONSOLE_LOGO_MAX_W] of the width and [CONSOLE_LOGO_MAX_H] of the height (keeping its aspect).
 */
internal fun consoleLogoBox(aspect: Float, cellW: Float, cellH: Float): Pair<Float, Float> {
    val a = aspect.coerceAtLeast(0.01f)
    var w = sqrt(CONSOLE_LOGO_AREA * cellW * cellH / a)
    var h = w * a
    val fit = min(1f, min(CONSOLE_LOGO_MAX_W * cellW / w, CONSOLE_LOGO_MAX_H * cellH / h))
    w *= fit; h *= fit
    return w to h
}

/**
 * One main menu tile in the Grid, [cellW] × [cellH] dp, as its mark on the ground in a faint edge (no plate or ring): a
 * console's logo ([consoleLogoBox]) or, without one, its name; a collection's name; a tool's icon with its name under it.
 * The mark is the ink at [CONSOLE_DIM_ALPHA], easing to the accent as it is selected ([lit]), in the tile's centre; no
 * game count (the selection's colour alone marks it).
 */
@Composable
internal fun MenuMarkCell(tile: GridTile, lit: Boolean, cellW: Float, cellH: Float, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    val litness = nxLitness(lit)
    val dim = colors.main.copy(alpha = CONSOLE_DIM_ALPHA)   // role: an unselected console logo
    val ink = lerp(dim, colors.accent, litness())
    val edge = colors.main.copy(alpha = MARK_EDGE_ALPHA)   // role: a Grid mark tile's edge
    val small = nxDescSize()   // a Settings row's description size
    @Composable fun name(text: String) = Text(
        text, color = ink, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
        fontSize = nxListLabel(), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Column(
        modifier.fillMaxSize().border(1.dp, edge, RoundedCornerShape(NX_TILE_CORNER.dp)).wrapContentHeight(Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (tile) {
            is GridTile.Logo -> tile.res?.let { res ->
                val (w, h) = consoleLogoBox(logoAspect(res) ?: 0.3f, cellW, cellH)
                Image(painterResource(res), contentDescription = tile.name, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(w.dp, h.dp))
            } ?: name(tile.name)
            is GridTile.Collection -> name(tile.name)
            is GridTile.Tool -> {
                // At most [TOOL_ICON_SHARE]; weighted, so its name is measured first and a short cell shrinks the icon,
                // never the name's two lines.
                val icon = (min(cellW, cellH) * TOOL_ICON_SHARE).dp
                ToolIcon(
                    tile.icon, tile.appId, ink,
                    Modifier.weight(1f, fill = false).sizeIn(maxWidth = icon, maxHeight = icon).aspectRatio(1f, matchHeightConstraintsFirst = true),
                )
                Spacer(Modifier.height(TOOL_NAME_GAP))
                // Its own lines ("Retro / Achievements"), so a long name in an outer column keeps off the screen's edge;
                // the icon and the lines it has centred in the tile together.
                Text(
                    tile.lines.joinToString("\n"), color = ink, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Medium,
                    fontSize = small, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    lineHeight = small * 1.3f, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            is GridTile.Game -> Unit
        }
    }
}

/** A tool's icon, as a share of its cell's shorter side. */
private const val TOOL_ICON_SHARE = 0.4f

/** The Grid's title for its selected [tile]: a console's or collection's name over its game count (null while unknown); null for anything else. */
internal fun gridTitle(tile: GridTile?): Pair<String, Int?>? = when (tile) {
    is GridTile.Logo -> tile.name to tile.count
    is GridTile.Collection -> tile.name to tile.count
    else -> null
}

/** The word after a game count [n] in the Grid's title, as Home's " achievements": " game" or " games". */
internal fun gamesWord(n: Int): String = if (n == 1) " game" else " games"
