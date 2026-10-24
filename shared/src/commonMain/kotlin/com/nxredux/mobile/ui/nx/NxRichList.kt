package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nx_gamepad
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** How a rich row's subtitle reads: dim by default, or a good / attention state. */
enum class NxTone { DIM, SUCCESS, WARNING }

/**
 * One part of a styled rich-row subtitle (NX Redux's "1h 37m total · 5m 6s avg"): [emphasis] parts are the bold values,
 * the rest (words, separators) are dimmed.
 */
data class NxSubtitleSpan(val text: String, val emphasis: Boolean)

/** How far non-emphasis subtitle spans are dimmed against the row's subtitle colour. */
private const val NX_SUBTITLE_DIM_ALPHA = 0.55f

/**
 * One row of an [NxRichList]: [title] on the first line, [subtitle] on the second in the [subtitleTone] colour, and an
 * optional [thumbnail] (any Coil model string: a `file://` path or a content uri) at the left. [placeholder]: the icon
 * drawn in the hint colour on the dark thumbnail slot when there is no [thumbnail] (the gamepad by default, so no slot is empty).
 * [subtitleSpans], when set, replaces [subtitle] on screen with bold values and dimmed words in the same tone colour.
 * [titleBadge]: a short count in a small pill after the title (an achievement's points, [NxCountPill]).
 */
data class NxRichRow(
    val title: String,
    val subtitle: String? = null,
    val thumbnail: String? = null,
    val subtitleTone: NxTone = NxTone.DIM,
    val enabled: Boolean = true,
    val placeholder: DrawableResource = Res.drawable.nx_gamepad,
    val subtitleSpans: List<NxSubtitleSpan>? = null,
    val titleBadge: String? = null,
)

/** A short count ([text]) in a small hollow pill ([nxButtonRing]), dim, or on the accent's muted colour while [lit]. */
@Composable
internal fun NxCountPill(text: String, lit: Boolean, fontSize: TextUnit) {
    val colors = LocalNxColors.current
    Box(Modifier.clip(RoundedCornerShape(50)).nxButtonRing(colors).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(text, color = if (lit) colors.onAccentMuted else colors.hint, fontFamily = LocalNxFont.current, fontSize = fontSize, lineHeight = fontSize, maxLines = 1)
    }
}

/** The colour of a subtitle in [tone]; [selected] rows sit on the accent pill, so they take its variants. */
fun NxColors.toneColor(tone: NxTone, selected: Boolean): Color = when (tone) {
    NxTone.DIM -> if (selected) onAccentMuted else hint
    NxTone.SUCCESS -> if (selected) successOnAccent else success
    NxTone.WARNING -> if (selected) warningOnAccent else warning
}

/** How far a rich row's thumbnail sits inside the capsule's left, top and bottom edges (NX Redux's 4px image padding). */
internal val NxRichThumbGap = 4.dp

/**
 * A rich row targets 1.5 plain pitches; the count is whole so rows tile the list height exactly. The count is
 * the nearest whole number, not the floor: a rich row is tall, so on a short list (the phone's top half) flooring could
 * make rows up to twice the target, and more so above a footer (a game's achievements) than on a full-height list.
 */
internal fun nxRichRowCountFor(listHeight: Dp, pitch: Dp = NxListPitch): Int =
    maxOf(1, (listHeight.value / (pitch.value * 1.5f)).roundToInt())

/**
 * The general two-line list with a thumbnail (NX Redux's rich rows): each row is ~1.5 plain rows tall, with a circular
 * thumbnail concentric in the capsule's left end, the title at the plain list's secondary size ([NxTextSizes.secondary])
 * and the subtitle a step under that ([NxTextSizes.caption]). The highlighted row sits on the
 * accent capsule pill, which wraps the thumbnail and both lines. Rows without art show their [NxRichRow.placeholder] in
 * the same slot, so a mixed list stays aligned. Taps follow [nxTap]; [onLongPress] (when set) gets a long-pressed row.
 * Scrolling and the gliding pill follow [NxList].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NxRichList(
    rows: List<NxRichRow>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onLongPress: ((Int) -> Unit)? = null,
    labelSize: TextUnit = TextUnit.Unspecified,   // the page's list label size ([nxListLabelSize] of its body); else of this list's height
    edgeMargin: Int = 0,   // rows kept in view past the selection ([NxKeepSelectedInView]): 1 under a scroll fade
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val tapOpens = LocalNxTapOpens.current
    NxKeepSelectedInView(listState, selectedIndex, rows.size, edgeMargin)
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val listPitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)   // Appearance → Text size
        val count = nxRichRowCountFor(maxHeight, listPitch)
        // Whole-px rows and a list of exactly [count] of them, so it rests on whole rows ([nxRowsSlotPx]).
        val rowPx = nxFitRowPx(with(density) { maxHeight.roundToPx() }, count)
        val rowHeight = with(density) { rowPx.toDp() }
        val listHeight = with(density) { nxRowsSlotPx(rowPx, count).toDp() }
        // Text follows the plain list's: the title at the list's secondary size, the subtitle a step under it (a caption),
        // only the row is taller, for the thumbnail. Each line's box is [NxTextSizes.line] of
        // its size with the glyphs centred in it, and the two boxes are centred in the row, so the text sits midway with
        // even room above and below. A short slot can round the rows down, so the title is capped where both boxes still fit.
        val label = NxTextSizes.secondary(if (labelSize != TextUnit.Unspecified) labelSize else nxListLabelSize(maxHeight, listPitch))
        val cap = with(density) { (rowHeight / NxTextSizes.RICH_BLOCK_OF_TITLE).toSp() }
        val titleSize = if (label.value > cap.value) cap else label
        val subtitleSize = NxTextSizes.secondary(titleSize)
        val lineStyle = LocalTextStyle.current.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))
        // The capsule fills the row; the thumbnail sits concentric in its left end, [gap] from the edge on the left, top and
        // bottom (NX Redux's 4px image padding). The text starts the plain list's text padding ([nxListInset]) after it and
        // stops where the right end's semicircle starts (half the row), mirroring the thumbnail in the left one.
        val gap = NxRichThumbGap
        // In whole pixels, so the gaps above and below the thumbnail match its left gap exactly (no odd pixel below it).
        val thumb = with(density) { (rowHeight.roundToPx() - gap.roundToPx() * 2).toDp() }
        val sidePad = maxWidth * 0.044f
        val identity = remember(rows) { nxPillListIdentity(rows) }
        val glide = rememberNxPillGlide(identity, selectedIndex, rows.size, count)
        val pitch = with(density) { rowHeight.roundToPx() }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(listHeight).nxPillGlide(glide, listState, pitch, nxHighlight()), flingBehavior = rememberNxRowSnapFling(listState)) {
            itemsIndexed(rows) { i, row ->
                val selected = i == selectedIndex
                val lit = i == glide.litIndex   // on-pill colours follow the gliding pill
                Box(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .combinedClickable(
                            onClick = { nxTap(i, selected, tapOpens(i), onSelect, onOpen) },
                            onLongClick = onLongPress?.let { lp -> { lp(i) } },
                        )
                        .padding(end = sidePad),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // The capsule is drawn once behind the list (nxPillGlide); the row reports its capsule-content size.
                    Row(
                        Modifier.onSizeChanged { glide.sizes[i] = it }
                            .padding(start = gap, end = rowHeight / 2)
                            .fillMaxHeight()
                            .alpha(if (row.enabled) 1f else NX_DISABLED_ALPHA),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val shape = CircleShape
                        Box(
                            Modifier.size(thumb).clip(shape).background(Color(0xFF1A1A1A), shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (row.thumbnail != null) {
                                AsyncImage(model = row.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Image(
                                    painterResource(row.placeholder), contentDescription = null,
                                    colorFilter = ColorFilter.tint(colors.hint), modifier = Modifier.size(thumb * 0.6f),
                                )
                            }
                        }
                        Spacer(Modifier.width(nxListInset()))   // the plain list's text padding
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    row.title,
                                    color = if (lit) colors.onAccent else colors.listText,
                                    style = lineStyle, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = titleSize, lineHeight = NxTextSizes.line(titleSize),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false).then(if (selected) Modifier.basicMarquee() else Modifier),
                                )
                                row.titleBadge?.let {
                                    Spacer(Modifier.width(6.dp))
                                    NxCountPill(it, lit, subtitleSize * 0.85f)
                                }
                            }
                            val toneColor = colors.toneColor(row.subtitleTone, lit)
                            val spans = row.subtitleSpans
                            if (spans != null) {
                                val styled = buildAnnotatedString {
                                    spans.forEach { span ->
                                        val style = if (span.emphasis) SpanStyle(color = toneColor, fontWeight = FontWeight.Bold)
                                        else SpanStyle(color = toneColor.copy(alpha = toneColor.alpha * NX_SUBTITLE_DIM_ALPHA), fontWeight = FontWeight.Normal)
                                        withStyle(style) { append(span.text) }
                                    }
                                }
                                Text(
                                    styled,
                                    style = lineStyle, fontFamily = font, fontSize = subtitleSize, lineHeight = NxTextSizes.line(subtitleSize),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            } else row.subtitle?.let {
                                Text(
                                    it,
                                    color = toneColor,
                                    style = lineStyle, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = subtitleSize, lineHeight = NxTextSizes.line(subtitleSize),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
