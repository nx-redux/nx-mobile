package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.TextUnit

/** The tab row's height (the main menu's top band fades from its top edge past it). */
internal val MENU_TAB_ROW_HEIGHT = 44.dp

/** The room under each tab word, which the selected tab's 3 dp underline sits at the bottom of. */
private val TAB_WORD_BOTTOM = 7.dp

/**
 * Where the selected tab's underline ends, in dp from the tab row's top (the words are pinned, [pinned], so the font
 * scale never moves it): the words' box (a word's natural MiSans line, [NX_FONT_LINE] per sp, plus
 * [TAB_WORD_BOTTOM], with [TAB_PLATE_Y] above and below) is centred in [MENU_TAB_ROW_HEIGHT], and the underline sits at
 * the bottom of its inner box: 34.78 in either orientation.
 * The tab row draws from the same constants; this only reads them ([homeStripTop]).
 */
internal fun menuTabUnderlineBottom(): Float =
    MENU_TAB_ROW_HEIGHT.value / 2 + (NxTextSizes.pageTitle.value * NX_FONT_LINE + TAB_WORD_BOTTOM.value) / 2

/** The tab words' centre in dp from the tab row's top: the words' box is centred, the underline room below the words. */
internal fun menuTabWordCentre(): Dp = MENU_TAB_ROW_HEIGHT / 2 - TAB_WORD_BOTTOM / 2

/** The easing every main menu glide uses (CSS cubic-bezier(.2,.8,.2,1)). */
internal val NxMenuEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
internal const val NX_MENU_GLIDE_MS = 240


/**
 * The main menu's category tabs: plain words in the ink, the current one full and the rest in [NxColors.inkDim], with a 3 dp pill under it that glides to the next
 * tab (L1/R1 and LEFT/RIGHT switch tabs; no shoulder glyphs are drawn). Left-aligned on the gutter in either
 * orientation. Words that do not fit the row scroll, keeping the current tab in view. [overArt] (a Backdrop game tab,
 * over its picture): the inactive words are the ink at 62% instead, and every word gets a dark shadow
 * ([overArtShadow]). [focused] (the d-pad is on the tab row): the current word sits on the accent pill ([TAB_PLATE_X] ×
 * [TAB_PLATE_Y] round it) in place of the underline. [onLongPress]: a long press anywhere on the row opens the
 * hidden-tab menu. [underline] false: no pill under the current word (the main menu's bottom tabs; the lit word alone
 * marks it). [trailing]: drawn at the row's right end (the touch-only Recent and Search icons,
 * [launcherTopActions]); the words keep the rest of the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuTabRow(
    labels: List<String>, selected: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier, overArt: Boolean = false, focused: Boolean = false,
    onLongPress: (() -> Unit)? = null, underline: Boolean = true, trailing: (@Composable () -> Unit)? = null,
    labelSize: TextUnit = NxTextSizes.pageTitle.pinned() * nxTextScale(),   // the words' size: the page title's, or (the bottom tabs) a hint's
    hints: (@Composable (room: Dp) -> Unit)? = null,   // at the row's right end, given the room the words leave ([TAB_HINTS_GAP] clear of them)
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    val density = LocalDensity.current
    val bounds = remember(labels) { mutableStateMapOf<Int, Pair<Int, Int>>() }   // tab -> (x, width) in px, in the words' row
    val target = bounds[selected]
    // The pill's x and width in px. A freshly composed page (back from a list, the switcher, start-up) snaps it to the
    // first measured tab, so it never grows in from the left edge during the page slide; later changes glide.
    val x = remember { Animatable(0f) }
    val w = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val (tx, tw) = target ?: return@LaunchedEffect
        if (!placed) {
            x.snapTo(tx.toFloat()); w.snapTo(tw.toFloat()); placed = true
        } else {
            val spec = tween<Float>(NX_MENU_GLIDE_MS, easing = NxMenuEasing)
            launch { x.animateTo(tx.toFloat(), spec) }
            launch { w.animateTo(tw.toFloat(), spec) }
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(target, scroll.viewportSize, focused) {
        val (tx, tw) = target ?: return@LaunchedEffect
        val view = scroll.viewportSize
        if (view <= 0) return@LaunchedEffect
        val inset = with(density) { TAB_PLATE_X.roundToPx() }   // the words sit this far into the scrolled box
        // Focused, the capsule reaches [TAB_PLATE_X] past the word: the fade's margin grows by that, so it stays clear too.
        val margin = with(density) { TAB_EDGE_FADE.roundToPx() } + if (focused) inset else 0
        val to = tabScrollTarget(tx + inset, tw, scroll.value, view, scroll.maxValue, margin) ?: return@LaunchedEffect
        scroll.animateScrollTo(to, tween(NX_MENU_GLIDE_MS, easing = NxMenuEasing))
    }
    val gap = if (portrait) 14.dp else 20.dp
    val size = labelSize   // Appearance → Text size
    var rowPx by remember { mutableIntStateOf(0) }   // the row's width inside its padding, for [hints]' room
    Row(
        // The gutter less the pill's side room, which the words' box takes back inside (so the pill is never clipped).
        // The long press before the padding, so the side gutters take it too.
        modifier.fillMaxWidth().height(MENU_TAB_ROW_HEIGHT)
            .then(if (onLongPress == null) Modifier else Modifier.pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) })
            .padding(horizontal = pageGutter(portrait).dp - TAB_PLATE_X).onSizeChanged { rowPx = it.width },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The first word starts on the gutter (as a Grid game list's MenuTitleRow name does). The words scroll when they
        // do not fit (five tabs on a narrow portrait phone); the pill scrolls with them, and the selected tab is scrolled
        // into view. A word cut at the room's edge fades out there.
        Box(Modifier.weight(1f)) { Box(Modifier.nxScrollEdgeFade(scroll).horizontalScroll(scroll).padding(horizontal = TAB_PLATE_X, vertical = TAB_PLATE_Y)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                val shadow = if (overArt) overArtShadow(colors.background) else null
                labels.forEachIndexed { i, label ->
                    val lit = focused && i == selected
                    Text(
                        label, color = when { lit -> colors.onAccent; i == selected || overArt -> colors.main; else -> colors.inkDim },
                        fontFamily = LocalNxFont.current, fontSize = size, maxLines = 1,
                        style = if (shadow == null || lit) LocalTextStyle.current else LocalTextStyle.current.copy(shadow = shadow),
                        // role: an unselected word over art, brighter than inkDim; the alpha fades its shadow with it
                        modifier = Modifier.alpha(if (i != selected && overArt) 0.62f else 1f)
                            .onGloballyPositioned { val v = it.positionInParent().x.toInt() to it.size.width; if (bounds[i] != v) bounds[i] = v }
                            .combinedClickable(onClick = { onTab(i) }, onLongClick = onLongPress)
                            .padding(bottom = TAB_WORD_BOTTOM)
                            .then(if (lit) Modifier.tabPlate(colors.accent) else Modifier),
                    )
                }
            }
            if (placed && !focused && underline) Box(
                Modifier.align(Alignment.BottomStart).offset { IntOffset(x.value.roundToInt(), 0) }
                    .width(with(density) { w.value.toDp() }).height(3.dp).background(colors.accent, RoundedCornerShape(2.dp)),
            )
        } }
        // The row's end padding is the gutter less [TAB_PLATE_X]; the icons' own inset takes the rest, so they end near the gutter.
        trailing?.invoke()
        // The words' own width as laid out (their box's plate room each side): what is left beside them is the hints'.
        hints?.let { h ->
            val plate = with(density) { TAB_PLATE_X.roundToPx() }
            val wordsPx = (bounds.values.maxOfOrNull { it.first + it.second } ?: 0) + plate * 2
            val room = with(density) { (rowPx - wordsPx - TAB_HINTS_GAP.roundToPx() - plate).coerceAtLeast(0).toDp() }
            // The words' own room under them ([TAB_WORD_BOTTOM]), so the hints sit level with them.
            Box(Modifier.padding(end = TAB_PLATE_X, bottom = TAB_WORD_BOTTOM)) { h(room) }
        }
    }
}

/**
 * A game list's stand-in for the tab row: its console's (or collection's) name in grey and semibold, at the tab words'
 * size ([NxTextSizes.pageTitle]), [MENU_TAB_ROW_HEIGHT] high, so the list's rows sit where the main menu's do. Its first
 * letter sits [textStart] in, where the text of the page below starts: the list labels' inset ([nxListInset]) by
 * default, a Grid, Carousel or Backdrop body's gutter on those game lists. It is at most [NX_TITLE_MAX_SHARE] of the
 * row's width (from its first letter): a longer title cuts its middle with "…", keeping the tool or list's name before
 * the first " | " ([splitTitleLead]) and the [suffix] (a count, "(3/20)") whole. [overArt] (a Backdrop game list, over
 * its picture): the name gets the tabs' dark shadow ([overArtShadow]). [trailing]: drawn at the row's right end, on the
 * right gutter (the touch-only Recent and Search icons, [launcherTopActions]).
 */
@Composable
fun MenuTitleRow(
    title: String, modifier: Modifier = Modifier, overArt: Boolean = false, textStart: Dp = nxListInset(), suffix: String? = null,
    textModifier: Modifier = Modifier,   // on the title's text (a Minimal morph finds it, and hides it while it lands)
    trailing: (@Composable () -> Unit)? = null,
    endInset: Dp = 0.dp,   // the trailing icons this much further in (a List: its names' inset, so both sides match)
) {
    val style = menuTitleStyle(overArt)
    val (lead, body) = splitTitleLead(title)
    BoxWithConstraints(modifier.fillMaxWidth().height(MENU_TAB_ROW_HEIGHT), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.padding(start = textStart).widthIn(max = maxWidth * NX_TITLE_MAX_SHARE).then(textModifier)) {
            if (lead.isNotEmpty()) Text(lead, style = style, maxLines = 1, softWrap = false)
            if (body.isNotEmpty()) Text(body, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (!suffix.isNullOrEmpty()) Text(" $suffix", style = style, maxLines = 1, softWrap = false)
        }
        if (trailing != null) {
            val gutter = nxPageGutter()
            Box(Modifier.align(Alignment.CenterEnd).padding(end = gutter - TOP_ICON_INSET + endInset)) { trailing() }
        }
    }
}

/** A page title's text style ([MenuTitleRow]), shadowed over art. */
@Composable
internal fun menuTitleStyle(overArt: Boolean = false): TextStyle {
    val shadow = if (overArt) overArtShadow(LocalNxColors.current.background) else null
    return LocalTextStyle.current.let { if (shadow == null) it else it.copy(shadow = shadow) }.copy(
        color = LocalNxColors.current.hint, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
        fontSize = NxTextSizes.pageTitle.pinned() * nxTextScale(),   // Appearance → Text size
    )
}

/** The most of the title row's width a page title takes, measured from its first letter. */
internal const val NX_TITLE_MAX_SHARE = 0.8f

/**
 * [title] split into the part a cut keeps whole, up to and including the first " | " (the tool's or list's name,
 * "RetroAchievements | "), and the rest, which a cut ellipsizes. No separator: the whole title is the rest.
 */
internal fun splitTitleLead(title: String): Pair<String, String> {
    val at = title.indexOf(TITLE_SEPARATOR)
    return if (at < 0) "" to title else title.substring(0, at + TITLE_SEPARATOR.length) to title.substring(at + TITLE_SEPARATOR.length)
}

private const val TITLE_SEPARATOR = " | "

/** While [scroll] has more on a side, that edge of the viewport fades the content out over [TAB_EDGE_FADE]. */
private fun Modifier.nxScrollEdgeFade(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = TAB_EDGE_FADE.toPx().coerceAtMost(size.width / 2)
        if (scroll.value > 0) drawRect(Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.Black, startX = 0f, endX = fade), blendMode = BlendMode.DstIn)
        if (scroll.value < scroll.maxValue) drawRect(
            Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = size.width - fade, endX = size.width), blendMode = BlendMode.DstIn,
        )
    }

private val TAB_EDGE_FADE = 16.dp

/** The focused tab's pill reaches this far past its word on each side, and above and below it. */
private val TAB_PLATE_X = 10.dp
private val TAB_PLATE_Y = 4.dp

/** The focused tab's capsule in [color], drawn behind its word with [TAB_PLATE_X] × [TAB_PLATE_Y] room round it. */
private fun Modifier.tabPlate(color: Color): Modifier = drawBehind {
    val x = TAB_PLATE_X.toPx()
    val y = TAB_PLATE_Y.toPx()
    val h = size.height + 2 * y
    drawRoundRect(color, Offset(-x, -y), Size(size.width + 2 * x, h), CornerRadius(h / 2))
}

/**
 * Where to scroll the tabs so the tab at [tx] ([tw] wide, px in the words' row) is in the [view] with [margin] (the edge
 * fade) clear on the side it came in from, so neither the fade nor the edge dims it; clamped to 0..[max]. Null when it
 * is already clear (or the clamp leaves the scroll where it is).
 */
internal fun tabScrollTarget(tx: Int, tw: Int, scroll: Int, view: Int, max: Int, margin: Int): Int? {
    val to = when {
        tx - margin < scroll -> tx - margin
        tx + tw + margin > scroll + view -> tx + tw - view + margin
        else -> return null
    }.coerceIn(0, maxOf(0, max))
    return to.takeIf { it != scroll }
}

/** The dark shadow the tab row's words and the title row's name take over art (as the info line's). */
private fun overArtShadow(ground: Color) = Shadow(ground, blurRadius = OVER_ART_BLUR_PX)

private const val OVER_ART_BLUR_PX = 6f

/** The least room between the last tab word and the hints beside the tabs ([MenuTabRow]'s hints). */
private val TAB_HINTS_GAP = 24.dp
