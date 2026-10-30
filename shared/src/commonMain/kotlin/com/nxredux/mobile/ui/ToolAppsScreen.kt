package com.nxredux.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.nx.nxPageInset
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.NxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.LocalNxTapOpens
import com.nxredux.mobile.ui.nx.NX_TILE_CORNER
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxEmptyState
import com.nxredux.mobile.ui.nx.NxPageTitle
import com.nxredux.mobile.ui.nx.NxScreenScaffold
import com.nxredux.mobile.ui.nx.nxHighlight
import com.nxredux.mobile.ui.nx.ToolIcon
import com.nxredux.mobile.ui.nx.nxRowCountFor
import com.nxredux.mobile.ui.nx.nxTap
import com.nxredux.mobile.ui.nx.tilePlate
import com.nxredux.mobile.ui.nx.toolIcon
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The grid's page title: the app drawer's tool name, or what the games picker picks. */
internal fun toolAppsTitle(pick: AppPick): String = if (pick == AppPick.GAMES) "Android games" else checkNotNull(ToolKind.APPS.toolName)

/** The gap between the picker's tiles, dp (its sides are the page inset, [nxPageInset]). */
internal const val TOOL_APPS_GAP = 8f
private const val TOOL_APPS_MIN_TILE = 80f      // a tile's narrowest width, dp
private const val TOOL_APPS_ICON_SHARE = 0.6f   // an app icon's side, of its tile's width
private const val TOOL_APPS_ICON_MAX = 64f      // dp
private const val TOOL_APPS_HIDDEN_ALPHA = 0.5f // a hidden app's icon and name: dimmed

/** How many picker tiles of at least [TOOL_APPS_MIN_TILE] fit a body [widthDp] wide less its side insets ([sides], both together); one at least. */
internal fun toolAppsColumns(widthDp: Float, sides: Float): Int =
    max(1, floor((widthDp - sides + TOOL_APPS_GAP) / (TOOL_APPS_MIN_TILE + TOOL_APPS_GAP)).toInt())

/**
 * How far to scroll (px, + down) so the selected row [selRow] shows whole: the rows are all [rowH] tall, [gap] apart, and
 * row [firstRow] (the first one laid out) sits at [firstTop] in a viewport whose whole rows end at [end]. Above it: its
 * top to 0; below it: its bottom to [end]; whole already: 0.
 */
internal fun toolAppsScrollDelta(selRow: Int, firstRow: Int, firstTop: Float, rowH: Float, gap: Float, end: Float): Float {
    val top = firstTop + (selRow - firstRow) * (rowH + gap)
    return when {
        top < 0 -> top
        top + rowH > end -> top + rowH - end
        else -> 0f
    }
}

/** A picker tile's width: [columns] tiles and their gaps fill a body [widthDp] wide less its side insets ([sides]) exactly. */
internal fun toolAppsTileWidth(widthDp: Float, columns: Int, sides: Float): Float =
    (widthDp - sides - (columns - 1) * TOOL_APPS_GAP) / columns

/**
 * Every launchable app as a tile, its own icon over its name. The app drawer (Tools → Apps): an app in Tools carries a
 * check badge; A or a tap on the selected tile opens it, MENU or a long press ([onLongPress]) its menu. The Android games
 * picker: an app in the console carries the badge, the rest are dimmed, and A shows or hides one. The d-pad moves by
 * tiles (AppState learns the columns through [onColumns]), B goes back. [titled]: the games picker in the two-pane
 * Settings' right pane, which names the page as the Settings pages do; the drawer is always titled.
 */
@Composable
fun ToolAppsScreen(
    screen: Screen.ToolApps, selectedIndex: Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onColumns: (Int) -> Unit, modifier: Modifier = Modifier,
    onHint: ((NxButton) -> Unit)? = null,
    onLongPress: ((Int) -> Unit)? = null,
    titled: Boolean = false,
    hints: Boolean = true,   // false: the second display's unfocused preview, with no hint bar
) {
    val drawer = screen.pick == AppPick.TOOLS
    // No other launchable app: a short note in place of the grid, B still going back.
    if (screen.apps.isEmpty()) {
        NxEmptyState("No apps to show", "No other launchable app is installed.", listOf(Hint(NxButton.B, "Back")), onHint ?: {}, modifier)
        return
    }
    NxScreenScaffold(
        rows = emptyList(), selectedIndex = selectedIndex, showArt = false,
        hints = if (!hints) emptyList() else if (drawer) listOf(Hint(NxButton.MENU, "Options"), Hint(NxButton.B, "Back"), Hint(NxButton.A, "Open"))
            else listOf(Hint(NxButton.B, "Back"), Hint(NxButton.A, "Select")),
        onSelect = onSelect, onOpen = onOpen, onHint = onHint, modifier = modifier,
        grid = { m ->
            ToolAppsGrid(
                screen.apps, selectedIndex, onSelect, onOpen, onColumns, onLongPress.takeIf { drawer },
                if (titled || drawer) toolAppsTitle(screen.pick) else null, dimHidden = !drawer, m,
            )
        },
    )
}

/** The picker's body: [title] in the band a titled list page has, then the tiles, scrolled to keep the selection whole. */
@Composable
private fun ToolAppsGrid(
    apps: List<ToolAppRow>, selectedIndex: Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onColumns: (Int) -> Unit,
    onLongPress: ((Int) -> Unit)?, title: String?, dimHidden: Boolean, modifier: Modifier,
) {
    val tapOpens = LocalNxTapOpens.current
    val inset = nxPageInset()   // the title and the first column on it, the last column's end too
    BoxWithConstraints(modifier) {
        val sides = (inset.start + inset.end).value
        val columns = toolAppsColumns(maxWidth.value, sides)
        val tileW = toolAppsTileWidth(maxWidth.value, columns, sides)
        // The scaffold's titled band: half a list row of this body tall (at least), the title at its start.
        val band = maxHeight / maxOf(2, nxRowCountFor(maxHeight)) / 2
        LaunchedEffect(columns) { onColumns(columns) }
        val grid = rememberLazyGridState()
        val gapPx = with(LocalDensity.current) { TOOL_APPS_GAP.dp.toPx() }
        // A d-pad move past the viewport scrolls just far enough to show the selected row whole, at the edge it came in
        // from: animated for a step, at once for a jump of more than a screen (a wrap).
        LaunchedEffect(selectedIndex, columns) {
            val info = grid.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return@LaunchedEffect
            if (selectedIndex !in apps.indices) return@LaunchedEffect
            val end = (info.viewportEndOffset - info.afterContentPadding).toFloat()
            val delta = toolAppsScrollDelta(selectedIndex / columns, first.index / columns, first.offset.y.toFloat(), first.size.height.toFloat(), gapPx, end)
            if (abs(delta) > end) grid.scrollBy(delta) else if (delta != 0f) grid.animateScrollBy(delta)
        }
        Column(Modifier.fillMaxSize()) {
            if (title != null) {
                Box(Modifier.fillMaxWidth().heightIn(min = band), contentAlignment = Alignment.CenterStart) { NxPageTitle(title, textStart = inset.start) }   // on the tiles' inset
            }
            LazyVerticalGrid(
                GridCells.Fixed(columns), Modifier.weight(1f).fillMaxWidth(), state = grid,
                contentPadding = PaddingValues(start = inset.start, end = inset.end, top = TOOL_APPS_GAP.dp, bottom = TOOL_APPS_GAP.dp),
                horizontalArrangement = Arrangement.spacedBy(TOOL_APPS_GAP.dp), verticalArrangement = Arrangement.spacedBy(TOOL_APPS_GAP.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.id }) { i, app ->
                    // A tap selects a tile; a tap on the selected one acts as A does; a long press opens the drawer's menu.
                    ToolAppTile(app, lit = i == selectedIndex, tileW, dim = dimHidden && !app.shown, onLongPress = onLongPress?.let { f -> { f(i) } }) {
                        nxTap(i, i == selectedIndex, tapOpens(i), onSelect, onOpen)
                    }
                }
            }
        }
    }
}

/** One app: its icon (the generic app glyph until it loads, or when it has none) over its name on two lines; [dim]: faded. */
@Composable
private fun ToolAppTile(app: ToolAppRow, lit: Boolean, tileW: Float, dim: Boolean, onLongPress: (() -> Unit)?, onTap: () -> Unit) {
    val colors = LocalNxColors.current
    val content = if (lit) colors.onAccent else colors.listText
    val iconDp = min(tileW * TOOL_APPS_ICON_SHARE, TOOL_APPS_ICON_MAX)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NX_TILE_CORNER.dp))
            .then(if (lit) Modifier.background(nxHighlight()) else Modifier.tilePlate(colors.background, colors.inkEdge))
            .combinedClickable(remember { MutableInteractionSource() }, indication = null, onLongClick = onLongPress, onClick = onTap),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 10.dp).alpha(if (dim) TOOL_APPS_HIDDEN_ALPHA else 1f),   // role: a hidden app, dimmed
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolIcon(toolIcon(ToolKind.APP), app.id, content, Modifier.size(iconDp.dp))
            Spacer(Modifier.height(6.dp))
            val size = 12.sp
            Text(
                app.label, color = content, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.15f, textAlign = TextAlign.Center),
            )
        }
        if (app.shown) toolAppBadgeColors(colors, lit).let { (disc, tick) -> CheckBadge(disc, tick, Modifier.align(Alignment.TopEnd).padding(5.dp)) }
    }
}

/**
 * A shown app's mark as (disc, tick). Unlit: NX teal ([NxColors.optionBar]) under the UI white ([NxColors.main]). Lit:
 * the accent inverted, its text colour ([NxColors.onAccent]) under the [NxColors.accent], since NX teal
 * would disappear into a dark accent (Teal, Red) on the lit tile.
 */
internal fun toolAppBadgeColors(colors: NxColors, lit: Boolean): Pair<Color, Color> =
    if (lit) colors.onAccent to colors.accent else colors.optionBar to colors.main

/** A shown app's mark: an [ink] tick on a [fill] disc ([toolAppBadgeColors]). */
@Composable
private fun CheckBadge(fill: Color, ink: Color, modifier: Modifier) {
    Canvas(modifier.size(18.dp).clip(CircleShape).background(fill)) {
        val w = size.width
        val tick = Path().apply {
            moveTo(w * 0.27f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.68f)
            lineTo(w * 0.74f, w * 0.35f)
        }
        drawPath(tick, ink, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
