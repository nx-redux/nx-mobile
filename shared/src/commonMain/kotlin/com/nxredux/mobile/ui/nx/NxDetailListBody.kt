package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.SubcomposeLayout
import com.nxredux.mobile.ui.LocalSettingsBlock

/**
 * A tool page in the nx-redux shape: grey title, a details block (headline + dim lines), the rows, the hint bar.
 * [headline] false draws every detail line as a caption (dim, one step below the dim lines): a short explanation, not a
 * status. [headlineStartEllipsis]: the headline is a path, kept to one line by cutting its start ("…/Games/NXRedux"),
 * so the folder itself stays readable.
 */
@Composable
fun NxDetailListBody(
    title: String, details: List<String>, rows: List<NxRow>, selectedIndex: Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null,
    headline: Boolean = true,
    headlineStartEllipsis: Boolean = false,
    rowKind: NxRowKind = NxRowKind.PILL,   // OPTIONS: NX Redux's options rows at 0.75 of the pitch
    rowText: ((TextUnit) -> TextUnit)? = null,   // pill rows: the row label's size from the list label (a row's secondary follows one step below); same pitch
    rowsAtTop: Boolean = false,   // a page without details: rows directly under the title instead of centred in the space
    pinnedRows: Int = 0,   // pill rows: the first this many stay above the scrolling rest, half a row apart
    pinnedBottom: Int = 0,   // pill rows: the last this many stay under the scrolling rest, half a row apart (a picker's actions)
    actionRows: Int = 0,   // options rows: the last this many are pill rows at the list pitch under the options, half a row apart (Continue)
    actionsAtTop: Boolean = false,   // the action rows at the title's right instead ([NxTitleActions]): a landscape setup page
    titleActions: Int = 0,   // the last this many rows at the title's right, the action rows (if any) the ones before them; not with actionsAtTop
    actionsOutlined: Boolean = true,   // the action rows under the options with the button ring
    landscapeColumns: IntRange? = null,   // these detail lines in columns on a landscape body (a long console list)
    landscapeColumnCount: Int = 2,
    landscapeWidth: Float = 1f,   // a landscape body's details and rows at most this share of its width (setup: long lines wrap)
    landscapeSplit: Boolean = false,   // pill rows on a landscape body: details and pinned rows at the left, the rest a full-height list at the right
    quietRows: Boolean = false,   // pill rows: the unlit rows in inkName and the bottom-pinned actions in inkDim: the folder picker
    fromLine: Float? = null,   // the title starts this share of the body down and the rows grow under it, the bottom-pinned rows staying at the bottom; a longer page moves up (the folder picker)
) {
    require(!(actionsAtTop && titleActions > 0)) { "actionsAtTop and titleActions do not combine" }
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val listState = rememberLazyListState()
    // The Settings rows block ([LocalSettingsBlock]): the title its first line, the details under it in the hint grey,
    // the rows from the bottom (the landscape split, pinned, action and title rows are a whole page's).
    LocalSettingsBlock.current?.let { block ->
        val header: (@Composable () -> Unit)? = if (details.isEmpty()) null else ({
            val size = NxTextSizes.secondary(NxTextSizes.listLabel(NxListPitch) * nxTextScale())
            Text(
                details.joinToString("\n"), color = colors.hint, fontFamily = font, fontSize = size, lineHeight = size * 1.35f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = nxListInset()).padding(bottom = 6.dp),
            )
        })
        NxRowsBlockPage(block, title, rows, { selectedIndex }, hints, onSelect, onOpen, modifier, onHint, rowKind = rowKind, header = header, listState = listState)
        return
    }
    // Touch only: the hints' icon stand-ins ([HintTouch.Icon]) sit at the title's right.
    val touch = touchOnly()
    val icons: (@Composable () -> Unit)? = if (touch && onHint != null) ({ NxTopIcons(hints.touchIcons(onHint)) }) else null
    BoxWithConstraints(modifier.fillMaxSize()) {
        val bodyHeight = maxHeight
        // The device's orientation, not this body's shape: a portrait page above the on-screen pad is wide and short.
        val landscape = !LocalPortrait.current
        // The details and the list capped to a share of a landscape body's width, at its start; the title keeps the width.
        val cap = if (landscape && landscapeWidth < 1f) Modifier.widthIn(max = maxWidth * landscapeWidth) else Modifier
        val inset = nxListInset()   // the list's row-label inset, so title and details line up with the labels
        // Sized from the row labels, not the screen height, so a tall display does not blow the details up.
        val pitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)   // Appearance → Text size
        val label = nxListLabelSize(pitch)
        // The headline and lines from the fixed pitch, not this body's: a page under the on-screen pad (a shorter body,
        // fewer and taller rows) words them at the same size as one without it.
        val detailLabel = NxTextSizes.listLabel(NxListPitch) * nxTextScale()   // Appearance → Text size
        val headlineSize = NxTextSizes.headline(detailLabel)
        val dimSize = NxTextSizes.secondary(detailLabel)
        // Rows at the body's pitch, not one fitted to what the details leave, so one detail line more or less never
        // changes the pitch or text size. Only whole rows show; the rest scroll, fading at the edge more is that way
        // ([nxScrollFade]). With details the list starts a fixed gap under them; without, it is centred in the space. An
        // options list that fits is only as tall as its rows.
        val listPitch = bodyHeight / nxRowCountFor(bodyHeight, pitch)
        val rowHeight = listPitch   // options rows are list rows (Text size already in the pitch)
        // An options page with no details lays out its title as the titled lists do ([NxTitledBody]), its rows at the top under it.
        val balanced = rowKind == NxRowKind.OPTIONS && details.isEmpty()
        // Options rows, and any page's rows under details, sit at the top of the list area.
        val underDetails = details.isNotEmpty()
        val listAlign = if (underDetails || rowsAtTop || rowKind == NxRowKind.OPTIONS) Alignment.TopCenter else Alignment.Center
        // Pinned rows (pill rows only): drawn on their own above the rest, which scrolls in what they leave.
        // Title actions: the last rows, at the title's right; the list shows the rest ([body]).
        val atTitle = if (actionsAtTop) 0 else titleActions.coerceIn(0, rows.size)
        val body = rows.dropLast(atTitle)
        val pinned = if (rowKind == NxRowKind.PILL) pinnedRows.coerceIn(0, body.size) else 0
        val head = body.take(pinned)
        // Action rows (options pages), or a pill list's bottom-pinned rows: drawn as pill rows under the rest, which
        // scrolls in what they leave.
        val actions = (if (rowKind == NxRowKind.OPTIONS) actionRows else if (rowKind == NxRowKind.PILL) pinnedBottom else 0).coerceIn(0, body.size - pinned)
        val rest = body.drop(pinned).dropLast(if (rowKind == NxRowKind.PILL) actions else 0)
        val options = if (actions > 0) body.dropLast(actions) else body
        val tail = if (actionsAtTop) emptyList() else body.takeLast(actions)
        // The list's selection: none while a title action is selected (an index past the list would draw an empty pill).
        val listSelected = selectedIndex.takeIf { it < body.size } ?: -1
        // Landscape split: the scrolling rows get their own column at the right, so more of them show.
        val split = landscapeSplit && landscape && rowKind == NxRowKind.PILL && (details.isNotEmpty() || pinnedRows > 0)   // the left holds something
        val headHere = if (split) 0 else pinned   // the pinned rows the list area draws (split: the left column has them)
        val headHeight = with(LocalDensity.current) { nxRowsSlotPx(rowHeight.roundToPx(), pinned).toDp() }
        val topActions = (if (actionsAtTop) body.takeLast(actions) else rows.takeLast(atTitle)).map { it.label }
        val listArea: @Composable (Modifier) -> Unit = { area -> BoxWithConstraints(area, contentAlignment = listAlign) {
            val slack = 2f / LocalDensity.current.density
            val headHeight = if (split) 0.dp else headHeight
            val headGap = if (headHere > 0) rowHeight * NX_OPTIONS_GAP else 0.dp
            val tailHeight = with(LocalDensity.current) { nxRowsSlotPx(listPitch.roundToPx(), tail.size).toDp() }
            val tailGap = if (tail.isNotEmpty()) listPitch * NX_OPTIONS_GAP else 0.dp
            val listHeight = maxHeight - headHeight - headGap - tailHeight - tailGap
            // Under details the list keeps a fixed gap from the last detail line.
            val gap = if (underDetails && !split) NxDetailListGap else 0.dp
            val scrolls = if (rowKind == NxRowKind.OPTIONS) nxOptionsListHeight(options, rowHeight).value > (listHeight - gap).value + slack   // its gaps are half rows
                else rest.size > nxWholeRows(listHeight - gap, rowHeight, pxSlack = slack)
            val whole = nxWholeRows(listHeight - gap, rowHeight, pxSlack = slack)
            // The rows' slot in whole px, the rows' own rounded px, so it rests on whole rows ([nxRowsSlotPx]); an
            // options list that fits takes only its own height.
            val rowsSlot = with(LocalDensity.current) { nxRowsSlotPx(rowHeight.roundToPx(), whole).toDp() }
            val optionsHeight = if (rowKind == NxRowKind.OPTIONS && !scrolls) nxOptionsFitHeight(options, rowHeight) else rowsSlot
            Column(Modifier.fillMaxWidth()) {
                if (gap > 0.dp) Spacer(Modifier.height(gap))
                if (headHere > 0) {
                    // The pill leaves the pinned rows past their end and enters the rest from above (and back), so a
                    // step across the gap still reads as one move.
                    NxList(
                        head, selectedIndex.coerceAtMost(pinned), onSelect, onOpen, Modifier.fillMaxWidth().height(headHeight),
                        rowHeightOverride = rowHeight, textSizeOverride = rowText?.invoke(label) ?: label,
                    )
                    Spacer(Modifier.height(headGap))
                }
                if (rowKind == NxRowKind.OPTIONS) {
                    NxOptionsList(
                        options, selectedIndex.takeIf { it < options.size } ?: -1, onSelect, onOpen, rowHeight = rowHeight, listText = label,
                        modifier = Modifier.fillMaxWidth().height(optionsHeight), listState = listState, scrollFade = true,
                    )
                } else {
                    NxList(
                        rest, (listSelected - pinned).takeIf { listSelected >= 0 && it in rest.indices } ?: -1, { onSelect(it + pinned) }, { onOpen(it + pinned) },
                        Modifier.fillMaxWidth().height(rowsSlot).nxScrollFade(listState, height = rowHeight / 2, strong = true),
                        listState = listState, rowHeightOverride = rowHeight, textSizeOverride = rowText?.invoke(label) ?: label, edgeMargin = 1,
                        restInk = if (quietRows) colors.inkName else null,
                    )
                }
                if (tail.isNotEmpty()) {
                    // The pill enters the action rows from above as the options' selection leaves them (and back).
                    Spacer(Modifier.height(tailGap))
                    val tailStart = body.size - tail.size
                    // Quiet actions: the word alone while unlit, the selected one on the pill (touch too).
                    val tailLit = if (listSelected < 0) -1 else listSelected - tailStart
                    NxList(
                        tail, tailLit, { onSelect(it + tailStart) }, { onOpen(it + tailStart) },
                        Modifier.fillMaxWidth().height(tailHeight), rowHeightOverride = listPitch, textSizeOverride = label, outlined = actionsOutlined,
                        restInk = if (quietRows) colors.inkDim else null,
                    )
                }
            }
        } }
        val trailing: (@Composable () -> Unit)? = if (topActions.isEmpty()) icons else ({
            NxTitleActions(topActions, rows.size - topActions.size, selectedIndex, onSelect, onOpen, trailing = icons)
        })
        Column(Modifier.fillMaxSize()) {
            if (balanced) NxTitledBody(title, Modifier.weight(1f).fillMaxWidth(), trailing = trailing) { listArea(Modifier.fillMaxSize()) }
            else {
                val titleView: @Composable () -> Unit = { NxPageTitle(title, bottom = if (headline) 8.dp else 0.dp, trailing = trailing) }   // a caption sits right under the title
                val lined = fromLine != null && !split && landscapeColumns == null
                if (!lined) titleView()
                // A landscape body lays [landscapeColumns] out in [landscapeColumnCount] columns, filled down then across.
                // Only when the lines in one column would leave the rows too little room (measured below).
                val columnable = landscapeColumns?.takeIf { landscape && it.first > 0 && it.last < details.size && it.count() > 1 }
                val dimLine: @Composable (String, Modifier) -> Unit = { line, m ->
                    Text(line, color = colors.hint, fontFamily = font, fontWeight = FontWeight.Normal,
                        fontSize = if (headline) dimSize else NxTextSizes.caption(detailLabel), modifier = m)
                }
                val left: @Composable (IntRange?) -> Unit = { columned ->
                    Column(cap.padding(horizontal = inset)) {
                        details.forEachIndexed { i, line ->
                            if (columned != null && i in columned) {
                                if (i == columned.first) {
                                    val lines = details.subList(columned.first, columned.last + 1)
                                    val count = landscapeColumnCount.coerceAtLeast(1)
                                    val down = (lines.size + count - 1) / count
                                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        (0 until count).map { c -> lines.drop(c * down).take(down) }.forEach { col ->
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                col.forEach { dimLine(it, Modifier) }
                                            }
                                        }
                                    }
                                }
                                return@forEachIndexed
                            }
                            if (i == 0 && headline && headlineStartEllipsis) {
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = headlineSize)
                                    val measurer = rememberTextMeasurer()
                                    val width = constraints.maxWidth
                                    val shown = remember(line, style, width) {
                                        startEllipsized(line) { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width <= width }
                                    }
                                    Text(shown, color = colors.listText, style = style, maxLines = 1, softWrap = false)
                                }
                            } else if (i == 0 && headline) {
                                Text(line, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = headlineSize)
                            } else {
                                if (i > 0) Spacer(Modifier.height(2.dp))
                                Text(line, color = colors.hint, fontFamily = font, fontWeight = FontWeight.Normal, fontSize = if (headline) dimSize else NxTextSizes.caption(detailLabel))
                            }
                        }
                    }
                    // Touch only: the hints' page buttons ([HintTouch.Button]) sit under the details, left on the text, with
                    // room under them before the list (Artwork's fetch, the achievement download: Pause and Cancel, or Done).
                    val buttons = hints.touchButtons()
                    if (touch && onHint != null && buttons.isNotEmpty()) NxTouchButtons(buttons, onHint, Modifier.padding(start = inset, top = 12.dp, bottom = NxTouchButtonsGap), Alignment.Start)
                }
                if (lined) {
                    // The title and details from the line, the rows under them; as high as the rows (and the pinned
                    // ones at the bottom) need when they would not fit below it, at the top at most.
                    val density = LocalDensity.current
                    val listNeed = with(density) {
                        val tailNeed = if (tail.isNotEmpty()) listPitch * tail.size + listPitch * NX_OPTIONS_GAP else 0.dp
                        ((if (underDetails) NxDetailListGap else 0.dp) + rowHeight * rest.size + tailNeed).roundToPx()
                    }
                    SubcomposeLayout(Modifier.weight(1f).fillMaxWidth()) { c ->
                        val head = subcompose("head") { Column { titleView(); left(null) } }.map { it.measure(c.copy(minHeight = 0)) }
                        val headH = head.sumOf { it.height }
                        val y = minOf((c.maxHeight * fromLine!!).toInt(), c.maxHeight - headH - listNeed).coerceAtLeast(0)
                        val listH = (c.maxHeight - y - headH).coerceAtLeast(0)
                        val list = subcompose("list") { listArea(Modifier.fillMaxWidth().height(with(density) { listH.toDp() })) }
                            .map { it.measure(c.copy(minHeight = 0, maxHeight = listH)) }
                        layout(c.maxWidth, c.maxHeight) {
                            head.forEach { it.place(0, y) }
                            list.forEach { it.place(0, y + headH) }
                        }
                    }
                } else if (split) Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        left(null)
                        if (pinned > 0) {
                            Spacer(Modifier.height(NxDetailListGap))
                            NxList(
                                head, selectedIndex.coerceAtMost(pinned), onSelect, onOpen, Modifier.fillMaxWidth().height(headHeight),
                                rowHeightOverride = rowHeight, textSizeOverride = rowText?.invoke(label) ?: label,
                            )
                        }
                    }
                    listArea(Modifier.weight(1f).fillMaxHeight())
                } else if (columnable == null) {
                    left(null)
                    listArea(Modifier.weight(1f).then(cap).fillMaxWidth())
                } else {
                    // The lines in one column first: columns only when that leaves the rows less than they need.
                    val density = LocalDensity.current
                    val listNeed = with(density) {
                        val rowsNeed = if (rowKind == NxRowKind.OPTIONS) nxOptionsListHeight(options, rowHeight) else rowHeight * rest.size
                        val tailNeed = if (tail.isNotEmpty()) listPitch * tail.size + listPitch * NX_OPTIONS_GAP else 0.dp
                        (NxDetailListGap + headHeight + (if (pinned > 0) rowHeight * NX_OPTIONS_GAP else 0.dp) + rowsNeed + tailNeed).roundToPx()
                    }
                    SubcomposeLayout(Modifier.weight(1f).fillMaxWidth()) { c ->
                        val oneColumn = subcompose("probe") { left(null) }
                            .sumOf { it.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity)).height }
                        val columned = columnable.takeIf { oneColumn + listNeed > c.maxHeight }
                        val page = subcompose("page") {
                            Column(Modifier.fillMaxSize()) {
                                left(columned)
                                listArea(Modifier.weight(1f).then(cap).fillMaxWidth())
                            }
                        }.map { it.measure(c) }
                        layout(c.maxWidth, c.maxHeight) { page.forEach { it.place(0, 0) } }
                    }
                }
            }
            NxHintBar(hints, onHint = onHint)
        }
    }
}

/**
 * [text] cut from the start until [fits] takes it: whole leading folders first ("…/Games/NXRedux"), then characters
 * when even the last one is too long ("…Redux"). Unchanged when it already fits.
 */
internal fun startEllipsized(text: String, fits: (String) -> Boolean): String {
    if (fits(text)) return text
    var slash = text.indexOf('/', 1)
    while (slash > 0) {
        val candidate = "…" + text.substring(slash)
        if (fits(candidate)) return candidate
        slash = text.indexOf('/', slash + 1)
    }
    for (k in 1 until text.length) {
        val candidate = "…" + text.substring(k)
        if (fits(candidate)) return candidate
    }
    return "…"
}

/** A detail page's gap between its last detail line and its list. */
private val NxDetailListGap = 8.dp

/** The room under a page's touch buttons, before its list. */
private val NxTouchButtonsGap = 16.dp

/** Setup's pages in landscape: the details and rows at most this share of the width, so long lines wrap. */
const val NxSetupLandscapeWidth = 0.8f
