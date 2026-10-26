package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.HomeStrip
import com.nxredux.mobile.ui.MainRow
import kotlin.math.max

/** Minimal Home's names: List Home's rows ([homeListRows]), only Continue keeping its tag (no shelf tags). */
internal fun homeMinimalRows(rows: List<MainRow>): List<NxRow> = rows.mapNotNull { r ->
    homeListRows(listOf(r)).firstOrNull()?.let { if (r is MainRow.Continue) it else it.copy(tag = null) }
}

/**
 * Home in the Minimal style (Layouts → Home layout: Minimal): the stats strip, then Home's [rows] as big names
 * ([homeMinimalRows]: Continue, the pinned games, the shelves' games, the pinned tools) in the shared Minimal layout
 * ([MinimalScreen]), the selection one row under the strip and the selected name's tag at the end of its name. The
 * names run on behind the strip, the tab row and the hints to the screen's edges. [selected] keeps the selected name for
 * the open band MainMenuScreen draws over the whole screen ([MinimalOpenOverlay]). MainMenuScreen draws this under its tab row.
 */
@Composable
internal fun HomeMinimalScreen(
    rows: List<MainRow>, selection: () -> Int, strip: HomeStrip?, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?, onHint: ((NxButton) -> Unit)?,
    selected: MinimalSelected, modifier: Modifier = Modifier, leaving: Boolean = false,
) {
    val chrome = LocalLauncherChrome.current
    val portrait = LocalPortrait.current
    val gutter = if (portrait) 16.dp else 24.dp
    val shownStrip = strip?.takeIf { chrome.homeStats }   // Layouts → Home stats hides it
    val frame = HomeFrame(0f, 0f, portrait = portrait, textScale = LocalDensity.current.fontScale, titleHidden = false, stripScale = nxTextScale())
    val stripLines = if (shownStrip?.most != null) 2 else 1
    // With the tab row hidden the strip moves into its room as a header, as on the other Home styles.
    val header = if (!chrome.tabRow && shownStrip != null) homeStatsHeader(frame.copy(titleHidden = true), stripLines) else null
    val pageStrip = shownStrip?.takeIf { header == null }
    val (capTop, inkH) = homeStripInk(frame, stripLines)
    val inkTop = homeListStripInkTop(homeUnderlineAbove(frame), inkH, null)
    val names = remember(rows) { homeMinimalRows(rows) }
    val top = when {
        header != null -> header.height.toFloat()
        pageStrip != null -> max(0f, inkTop + inkH + 4f)
        else -> 0f
    }
    MinimalScreen(
        names, selection, hints, onSelect, onOpen, onLongPress, onHint, selected, modifier, top = top,
        hideHints = chrome.emptyBarRoom, leaving = leaving,
    ) {
        HomeStripOverlay(shownStrip, pageStrip, header, stripLines, inkTop, capTop, frame, gutter)
    }
}
