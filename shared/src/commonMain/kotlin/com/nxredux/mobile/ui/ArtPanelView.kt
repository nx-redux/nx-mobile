package com.nxredux.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NX_TILE_CORNER
import com.nxredux.mobile.ui.nx.SlidePanel
import com.nxredux.mobile.ui.nx.nxModalButton
import com.nxredux.mobile.ui.nx.nxModalSmall
import com.nxredux.mobile.ui.nx.nxScrollFade

/** The room above the panel's header and under its grid (the achievements panel's). */
private val ART_PANEL_EDGE = 10.dp
private val ART_TILE_GAP = 10.dp
/** A tile's picture box: a screenshot's shape; grids and heroes fit inside it whole. */
private const val ART_TILE_ASPECT = 4f / 3f

/**
 * The art panel ([ArtPanel]) sliding in from the right over a game list: the game's name, then its pictures as a
 * two-column grid of previews (a placeholder while one loads, "Couldn't load" dimmed when it could not), each with its
 * source under it and a check on the one in use; Search again below them, the panel's message above. A message instead
 * while the art is fetched or the list read. A tap on the scrim closes it ([onDismiss]); a tap on a tile or Search again
 * picks it ([onTap]).
 */
@Composable
fun ArtPanelOverlay(panel: ArtPanel?, onTap: (Int) -> Unit, onDismiss: () -> Unit) {
    // The last panel shown, so it slides out with its content after it closed.
    var last by remember { mutableStateOf(panel) }
    if (panel != null) last = panel
    SlidePanel(panel != null, onDismiss) {
        last?.let { ArtPanelBody(it, onTap) }
    }
}

@Composable
private fun ColumnScope.ArtPanelBody(p: ArtPanel, onTap: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Text(
        p.title, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = ART_PANEL_EDGE, bottom = 4.dp),
        color = colors.listText, fontFamily = font, fontSize = nxModalButton(), fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(4.dp))
    Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = ART_PANEL_EDGE), contentAlignment = Alignment.Center) {
        when (p.phase) {
            ArtPanelPhase.FETCHING -> ArtPanelMessage("Fetching art…")
            ArtPanelPhase.LOADING -> ArtPanelMessage("Looking for pictures…")
            ArtPanelPhase.READY -> ArtGrid(p, onTap)
        }
    }
}

@Composable
private fun ArtGrid(p: ArtPanel, onTap: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val grid = rememberLazyGridState()
    val lead = if (p.message != null) 1 else 0   // the message's item before the tiles
    val focusItem = lead + p.focus                // a tile's item, or Search again's after them
    // A d-pad move past the viewport scrolls just far enough to show the focused item whole.
    LaunchedEffect(focusItem, p.tiles.size) {
        val info = grid.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == focusItem }
        val top = info.viewportStartOffset
        val end = info.viewportEndOffset - info.afterContentPadding
        when {
            item == null -> grid.animateScrollToItem(if (p.focus == 0) 0 else focusItem)   // the message too when on the first
            item.offset.y < top -> grid.animateScrollBy((item.offset.y - top).toFloat())
            item.offset.y + item.size.height > end -> grid.animateScrollBy((item.offset.y + item.size.height - end).toFloat())
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(ART_PANEL_COLUMNS), Modifier.fillMaxSize().nxScrollFade(grid, height = 40.dp, strong = true), state = grid,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ART_TILE_GAP), verticalArrangement = Arrangement.spacedBy(ART_TILE_GAP),
    ) {
        p.message?.let { m ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "message") {
                Text(m, color = colors.hint, fontFamily = font, fontSize = nxModalSmall(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
        }
        itemsIndexed(p.tiles, key = { _, t -> t.option.key }) { i, t ->
            ArtTileView(t, lit = i == p.focus, current = t.option.key == p.currentKey) { onTap(i) }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "search-again") {
            val lit = p.onSearchAgain
            Text(
                "Search again", color = if (lit) colors.onAccent else colors.listText, fontFamily = font, fontSize = nxModalSmall(),
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(50))
                    .then(if (lit) Modifier.background(colors.accent) else Modifier.border(1.dp, colors.inkEdge, RoundedCornerShape(50)))
                    .pointerInput(p.tiles.size) { detectTapGestures { onTap(p.tiles.size) } }
                    .padding(vertical = 9.dp),
            )
        }
    }
}

/** One option: its preview in a 4:3 box (outlined in the accent while [lit]), a check while [current], its label under it. */
@Composable
private fun ArtTileView(t: ArtTile, lit: Boolean, current: Boolean, onTap: () -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val shape = RoundedCornerShape(NX_TILE_CORNER.dp)
    Column(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(ART_TILE_ASPECT).clip(shape).background(colors.background)
                .border(if (lit) 3.dp else 1.dp, if (lit) colors.accent else colors.inkEdge, shape),
            contentAlignment = Alignment.Center,
        ) {
            val image = t.image
            when {
                image != null -> AsyncImage(model = image.value, contentDescription = t.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(if (lit) 3.dp else 1.dp))
                t.failed -> Text("Couldn't load", Modifier.alpha(0.6f), color = colors.hint, fontFamily = font, fontSize = nxModalSmall(), textAlign = TextAlign.Center)   // role: a preview that would not load, dimmed
                else -> Box(Modifier.size(22.dp).clip(CircleShape).background(colors.inkEdge))   // loading
            }
            if (current) CurrentMark(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            t.label, Modifier.fillMaxWidth().padding(top = 4.dp), color = if (lit) colors.listText else colors.hint, fontFamily = font,
            fontSize = nxModalSmall(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/** The picture in use now: a tick on an accent disc. */
@Composable
private fun CurrentMark(modifier: Modifier) {
    val colors = LocalNxColors.current
    Canvas(modifier.size(20.dp).clip(CircleShape).background(colors.accent)) {
        val w = size.width
        val tick = Path().apply {
            moveTo(w * 0.27f, w * 0.52f)
            lineTo(w * 0.44f, w * 0.68f)
            lineTo(w * 0.74f, w * 0.35f)
        }
        drawPath(tick, colors.onAccent, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ArtPanelMessage(text: String) {
    val colors = LocalNxColors.current
    Text(text, Modifier.padding(horizontal = 24.dp), color = colors.hint, fontFamily = LocalNxFont.current, fontSize = nxModalSmall(), textAlign = TextAlign.Center)
}
