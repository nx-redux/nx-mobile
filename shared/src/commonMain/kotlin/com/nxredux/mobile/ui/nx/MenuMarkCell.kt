package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.gamesLabel
import org.jetbrains.compose.resources.painterResource
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The main menu's Consoles, Collections and Tools tabs in the Grid style: each tile its mark alone on the ground (a
 * console's logo, a collection's name, a tool's icon), no plate, border or ring.
 * Every logo fills the same optical area ([consoleLogoBox]), so a thin wordmark and a two-line logo read at one weight.
 * Unselected logos are the ink at [CONSOLE_DIM_ALPHA]; the selected one is the accent, its game count under it at the
 * row-info size in inkDim; the selection eases over [NX_DRAIN_MS] ([MenuMarkCell]), in the Grid's rows ([NxTileGrid]).
 */

/** An unselected console logo's ink. */
private const val CONSOLE_DIM_ALPHA = 0.42f   // role: an unselected console logo

/** A logo's optical area, as a share of its cell's, and the most of the cell's width and height it may take. */
private const val CONSOLE_LOGO_AREA = 0.15f
private const val CONSOLE_LOGO_MAX_W = 0.82f
private const val CONSOLE_LOGO_MAX_H = 0.5f

/** The count's gap under its logo. */
private val CONSOLE_COUNT_GAP = 6.dp

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

/** A [MenuMarkCell] mark's place when raised: halfway between the cell's centre (0) and its top (-1). */
private const val RAISED_BIAS = -0.5f

/**
 * One main menu tile in the Grid, [cellW] × [cellH] dp, as its mark alone on the ground (no plate, border or ring): a
 * console's logo ([consoleLogoBox]) or, without one, its name; a collection's name; a tool's icon with its name under it.
 * The mark is the ink at [CONSOLE_DIM_ALPHA], easing to the accent as it is selected ([lit]); a console's or collection's
 * game count shows under the selected one only, at the row-info size in inkDim, crossfading with it. The count's line
 * is kept either way, so the mark never moves. [raised]: the mark sits halfway between the cell's centre and its top
 * (a tall cell, the grid's pad case), so the room above the first row halves.
 */
@Composable
internal fun MenuMarkCell(tile: GridTile, lit: Boolean, cellW: Float, cellH: Float, modifier: Modifier = Modifier, raised: Boolean = false) {
    val colors = LocalNxColors.current
    val litness = nxLitness(lit)
    val dim = colors.main.copy(alpha = CONSOLE_DIM_ALPHA)   // role: an unselected console logo
    val ink = lerp(dim, colors.accent, litness())
    val small = nxOptionsLabelSize(nxListLabel()) * SETTING_DESC_OF_LABEL   // a Settings row's description size
    val smallLine = with(LocalDensity.current) { (small * 1.3f).toDp() }
    @Composable fun name(text: String) = Text(
        text, color = ink, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
        fontSize = nxListLabel(), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    val count = when (tile) { is GridTile.Logo -> tile.count; is GridTile.Collection -> tile.count; else -> null }
    Column(
        modifier.fillMaxSize().wrapContentHeight(BiasAlignment.Vertical(if (raised) RAISED_BIAS else 0f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (tile) {
            is GridTile.Logo -> tile.res?.let { res ->
                val (w, h) = consoleLogoBox(logoAspect(res) ?: 0.3f, cellW, cellH)
                Image(painterResource(res), contentDescription = tile.name, contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(w.dp, h.dp))
            } ?: name(tile.name)
            is GridTile.Collection -> name(tile.name)
            is GridTile.Tool -> {
                ToolIcon(tile.icon, tile.appId, ink, Modifier.size((min(cellW, cellH) * TOOL_ICON_SHARE).dp))
                Spacer(Modifier.height(CONSOLE_COUNT_GAP))
                // One line where it fits, else it wraps between its words onto a second ("Retro Achievements").
                Text(
                    tile.lines.joinToString(" "), color = ink, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Medium,
                    fontSize = small, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    lineHeight = small * 1.3f, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            is GridTile.Game -> Unit
        }
        if (tile !is GridTile.Tool) {
            Spacer(Modifier.height(CONSOLE_COUNT_GAP))
            Box(Modifier.height(smallLine), contentAlignment = Alignment.Center) {
                count?.let {
                    Text(
                        gamesLabel(it), color = colors.inkDim, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Medium,
                        fontSize = small, maxLines = 1, modifier = Modifier.graphicsLayer { alpha = litness() },
                    )
                }
            }
        }
    }
}

/** A tool's icon, as a share of its cell's shorter side. */
private const val TOOL_ICON_SHARE = 0.4f
