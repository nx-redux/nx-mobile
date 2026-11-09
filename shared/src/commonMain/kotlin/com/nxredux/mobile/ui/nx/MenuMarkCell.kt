package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.TOOL_APPS_ICON_MAX
import com.nxredux.mobile.ui.TOOL_APPS_ICON_SHARE
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

private const val CONSOLE_DIM_ALPHA = 0.42f   // role: an unselected console logo

/** A Grid mark tile's edge: a touch over an unlit game tile's ([NxColors.inkEdge]), as the mark tile has no picture to edge. */
private const val MARK_EDGE_ALPHA = 0.12f   // role: a Grid mark tile's edge

/** A logo's optical area, as a share of its cell's, and the most of the cell's width and height it may take. */
private const val CONSOLE_LOGO_AREA = 0.15f
private const val CONSOLE_LOGO_MAX_W = 0.82f
private const val CONSOLE_LOGO_MAX_H = 0.5f

/** A name's room from the tile's left and right edges (a tool's, a collection's), so a long line wraps clear of them. */
private val MARK_NAME_INSET = 12.dp

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
    // A name broken only between words ([markNameLines]), each line on its own: one wider than the tile ends in "…".
    val nameStyle = TextStyle(fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxListLabel(), textAlign = TextAlign.Center)
    val measurer = rememberTextMeasurer()
    @Composable fun name(text: String) = BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = MARK_NAME_INSET)) {
        val width = constraints.maxWidth
        val lines = remember(text, width, nameStyle) {
            markNameLines(text, MARK_NAME_LINES) { measurer.measure(it, nameStyle, softWrap = false, maxLines = 1).size.width <= width }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEach { Text(it, color = ink, style = nameStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (tile is GridTile.Tool) {
        // The app drawer's tile: the icon at a fixed spot from the top, two lines kept for the name whatever it needs, so
        // every tool's icon lines up across a row.
        Column(
            modifier.fillMaxSize().border(1.dp, edge, RoundedCornerShape(NX_TILE_CORNER.dp))
                .padding(horizontal = MARK_NAME_INSET, vertical = TOOL_TILE_PAD_V.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Weighted, so the name's two lines are measured first: a tile short of its height shrinks the icon, never the name.
            val icon = min(cellW * TOOL_APPS_ICON_SHARE, TOOL_APPS_ICON_MAX).dp
            ToolIcon(
                tile.icon, tile.appId, ink,
                Modifier.weight(1f, fill = false).sizeIn(maxWidth = icon, maxHeight = icon).aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
            Spacer(Modifier.height(TOOL_TILE_NAME_GAP.dp))
            Text(
                tile.lines.joinToString("\n"), color = ink, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = TOOL_TILE_NAME_SP.sp,
                    lineHeight = (TOOL_TILE_NAME_SP * TOOL_TILE_LINE).sp, textAlign = TextAlign.Center,
                ),
            )
        }
        return
    }
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
            is GridTile.Tool -> Unit   // drawn above
            is GridTile.Game -> Unit
        }
    }
}

/** The most lines a collection's or console's name takes in its tile. */
private const val MARK_NAME_LINES = 2

/**
 * A tile's name in at most [max] lines, broken only between words: each line takes the words that [fits] on it, the
 * last any left over. A word wider than the line stays whole on a line of its own, and the line ends in "…" when drawn.
 */
internal fun markNameLines(name: String, max: Int, fits: (String) -> Boolean): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in name.split(' ').filter { it.isNotEmpty() }) {
        val next = if (line.isEmpty()) word else "$line $word"
        if (line.isEmpty() || lines.size == max - 1 || fits(next)) line = next
        else { lines += line; line = word }
    }
    if (line.isNotEmpty()) lines += line
    return lines
}

/** The Grid's title for its selected [tile]: a console's or collection's name over its game count (null while unknown); null for anything else. */
internal fun gridTitle(tile: GridTile?): Pair<String, Int?>? = when (tile) {
    is GridTile.Logo -> tile.name to tile.count
    is GridTile.Collection -> tile.name to tile.count
    else -> null
}

/** The word after a game count [n] in the Grid's title, as Home's " achievements": " game" or " games". */
internal fun gamesWord(n: Int): String = if (n == 1) " game" else " games"
