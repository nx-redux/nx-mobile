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
import androidx.compose.foundation.layout.Row
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
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.settings.GameListArt
import com.nxredux.mobile.ui.nx.nxModalSmall
import com.nxredux.mobile.ui.nx.nxModalText
import com.nxredux.mobile.ui.nx.nxScrollFade

/** The room above the panel's header and under its grid (the achievements panel's). */
private val ART_PANEL_EDGE = 10.dp
private val ART_TILE_GAP = 10.dp
/** The grid's common columns: a 2-column section's tile spans 3, a 3-column one's 2. */
private const val ART_GRID_COLUMNS = 6

/**
 * The art panel ([ArtPanel]) sliding in from the right over a game list: the game's name, then its pictures in one
 * section per art kind (a heading, then previews shaped to the art: landscape screenshots, portrait boxes, wide wheels;
 * a placeholder while one loads, "Couldn't load" dimmed when it could not), each with its source under it and a check
 * on the one in use in its section; Search again below them, the panel's message above. A message instead while the
 * art is fetched or the list read. After a box or wheel pick Layouts does not show, the question card over the
 * pictures ([backdropArt] and [listArt] are Layouts' values now; [onAnswer] with Switch or Keep). A tap on the scrim
 * closes it ([onDismiss]); a tap on a tile or Search again picks it ([onTap]).
 */
@Composable
fun ArtPanelOverlay(
    panel: ArtPanel?, backdropArt: BackdropArt, listArt: GameListArt,
    onTap: (Int) -> Unit, onAnswer: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    // The last panel shown, so it slides out with its content after it closed.
    var last by remember { mutableStateOf(panel) }
    if (panel != null) last = panel
    SlidePanel(panel != null, onDismiss) {
        last?.let { ArtPanelBody(it, backdropArt, listArt, onTap, onAnswer) }
    }
}

@Composable
private fun ColumnScope.ArtPanelBody(p: ArtPanel, backdropArt: BackdropArt, listArt: GameListArt, onTap: (Int) -> Unit, onAnswer: (Boolean) -> Unit) {
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
            ArtPanelPhase.READY -> ArtGrid(p, Modifier.alpha(if (p.question != null) 0.35f else 1f), onTap)   // role: the pictures dimmed under the question
        }
        p.question?.let { q -> LayoutQuestionCard(q, layoutQuestionText(q, backdropArt, listArt), onAnswer, Modifier.align(Alignment.BottomCenter)) }
    }
}

/** The grid item of the flat [focus]: after the message's item, each section's heading and tiles; Search again's after them all. */
private fun gridItemOf(p: ArtPanel, focus: Int): Int {
    var item = if (p.message != null) 1 else 0
    var left = focus
    for (s in p.sections) {
        if (left < s.tiles.size) return item + 1 + left
        item += 1 + s.tiles.size
        left -= s.tiles.size
    }
    return item
}

@Composable
private fun ArtGrid(p: ArtPanel, modifier: Modifier, onTap: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val grid = rememberLazyGridState()
    val focusItem = gridItemOf(p, p.focus)   // a tile's item, or Search again's after them
    val tileCount = p.sections.sumOf { it.tiles.size }
    // A d-pad move past the viewport scrolls just far enough to show the focused item whole.
    LaunchedEffect(focusItem, tileCount) {
        val info = grid.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == focusItem }
        val top = info.viewportStartOffset
        val end = info.viewportEndOffset - info.afterContentPadding
        when {
            // The message and the first heading too when on the first tile; a section's heading with its first row.
            item == null -> grid.animateScrollToItem(if (p.focus == 0) 0 else focusItem)
            item.offset.y < top -> grid.animateScrollBy((item.offset.y - top).toFloat())
            item.offset.y + item.size.height > end -> grid.animateScrollBy((item.offset.y + item.size.height - end).toFloat())
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(ART_GRID_COLUMNS), modifier.fillMaxSize().nxScrollFade(grid, height = 40.dp, strong = true), state = grid,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ART_TILE_GAP), verticalArrangement = Arrangement.spacedBy(ART_TILE_GAP),
    ) {
        p.message?.let { m ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "message") {
                Text(m, color = colors.hint, fontFamily = font, fontSize = nxModalSmall(), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
        }
        var first = 0
        for (section in p.sections) {
            val base = first
            item(span = { GridItemSpan(maxLineSpan) }, key = "heading-${section.slot}") {
                Text(
                    section.title, Modifier.fillMaxWidth().padding(top = if (base == 0) 0.dp else 8.dp), color = colors.hint, fontFamily = font,
                    fontSize = nxModalSmall(), fontWeight = FontWeight.SemiBold,
                )
            }
            val span = ART_GRID_COLUMNS / section.columns
            itemsIndexed(section.tiles, key = { _, t -> t.option.key }, span = { _, _ -> GridItemSpan(span) }) { j, t ->
                val i = base + j
                ArtTileView(t, section.aspect, lit = i == p.focus, current = p.isCurrent(t)) { onTap(i) }
            }
            first += section.tiles.size
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "search-again") {
            val lit = p.onSearchAgain
            Text(
                "Search again", color = if (lit) colors.onAccent else colors.listText, fontFamily = font, fontSize = nxModalSmall(),
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(50))
                    .then(if (lit) Modifier.background(colors.accent) else Modifier.border(1.dp, colors.inkEdge, RoundedCornerShape(50)))
                    .pointerInput(tileCount) { detectTapGestures { onTap(tileCount) } }
                    .padding(vertical = 9.dp),
            )
        }
    }
}

/**
 * The question after a box or wheel pick, a card in the panel: "Show this picture?", its [text], then Switch and Keep
 * (the lit one in the accent; a tap answers, [onAnswer] true for Switch).
 */
@Composable
private fun LayoutQuestionCard(q: LayoutQuestion, text: String, onAnswer: (Boolean) -> Unit, modifier: Modifier) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(colors.modalCard)
            .border(1.dp, colors.inkEdge, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(LAYOUT_QUESTION_TITLE, color = colors.listText, fontFamily = font, fontSize = nxModalButton(), fontWeight = FontWeight.SemiBold)
        Text(text, color = colors.hint, fontFamily = font, fontSize = nxModalText())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ART_TILE_GAP)) {
            for ((label, yes) in listOf(LAYOUT_QUESTION_YES to true, LAYOUT_QUESTION_NO to false)) {
                val lit = q.yesFocused == yes
                Text(
                    label, color = if (lit) colors.onAccent else colors.listText, fontFamily = font, fontSize = nxModalSmall(),
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .then(if (lit) Modifier.background(colors.accent) else Modifier.border(1.dp, colors.inkEdge, RoundedCornerShape(50)))
                        .pointerInput(yes) { detectTapGestures { onAnswer(yes) } }
                        .padding(vertical = 9.dp),
                )
            }
        }
    }
}

/** One option: its preview in a box of [aspect] (outlined in the accent while [lit]) fitted whole, a check while [current], its label under it. */
@Composable
private fun ArtTileView(t: ArtTile, aspect: Float, lit: Boolean, current: Boolean, onTap: () -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val shape = RoundedCornerShape(NX_TILE_CORNER.dp)
    Column(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspect).clip(shape).background(colors.background)
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
