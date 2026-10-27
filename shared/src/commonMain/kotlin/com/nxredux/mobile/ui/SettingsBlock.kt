package com.nxredux.mobile.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import com.nxredux.mobile.ui.nx.rememberNxRowSnapFling
import com.nxredux.mobile.ui.nx.nxRowEdgeMask
import com.nxredux.mobile.ui.nx.nxRowFade
import com.nxredux.mobile.ui.nx.SETTING_DESC_OF_LABEL
import com.nxredux.mobile.ui.nx.nxListLabel
import com.nxredux.mobile.ui.nx.nxOptionsLabelSize
import com.nxredux.mobile.ui.nx.LocalNxFontWeights
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.alpha
import com.nxredux.mobile.ui.nx.LocalNxTapOpens
import com.nxredux.mobile.ui.nx.NX_DISABLED_ALPHA
import com.nxredux.mobile.ui.nx.NxKeepSelectedInView
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.nxScrollFade
import com.nxredux.mobile.ui.nx.nxTap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nx_logo
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.LocalWindowSize
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.nxListInset
import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.pinned
import org.jetbrains.compose.resources.painterResource

/** The window classes the Settings block's geometry follows. */
enum class SettingsSizeClass { PHONE_UPRIGHT, PHONE_LANDSCAPE, TABLET }

/** A tablet-class window (its short side at least 600 dp, either way up); else a phone, upright when taller than wide. */
fun settingsSizeClass(widthDp: Float, heightDp: Float): SettingsSizeClass = when {
    minOf(widthDp, heightDp) >= 600f -> SettingsSizeClass.TABLET
    heightDp > widthDp -> SettingsSizeClass.PHONE_UPRIGHT
    else -> SettingsSizeClass.PHONE_LANDSCAPE
}

/** The side inset of the Settings blocks and the mark: 16 dp upright, 24 dp phone landscape, 48 dp tablet-class. */
fun settingsGutter(c: SettingsSizeClass): Dp = when (c) {
    SettingsSizeClass.PHONE_UPRIGHT -> 16.dp
    SettingsSizeClass.PHONE_LANDSCAPE -> 24.dp
    SettingsSizeClass.TABLET -> 48.dp
}

/** A section word's size on every screen, upright or not (before Appearance → Text size). */
val SETTINGS_SECTION_SIZE: TextUnit = 26.sp

/** The NX mark at the top right: its size and its inset from the top. Its right inset is the gutter. */
val SETTINGS_MARK = 48.dp
val SETTINGS_MARK_TOP = 16.dp

/** The wide rows pane's most width ([settingsFrameWidth]). */
val SETTINGS_FRAME_MAX = 560.dp

/** The least gap between the section block's right edge and the wide rows pane. */
val SETTINGS_FRAME_GAP = 24.dp

/** The wide rows pane's least width: past this the pane keeps it, reaching into the gap rather than vanishing. */
val SETTINGS_FRAME_MIN = 280.dp

/**
 * The wide rows pane's width: [SETTINGS_FRAME_MAX], or less when the room between the section block's right edge
 * ([sectionRight], from the stage's left) plus [SETTINGS_FRAME_GAP] and the right [gutter] is narrower, never under
 * [SETTINGS_FRAME_MIN] (nor the stage inside the gutter). Never sized by the page, so its left edge is the same on every page.
 */
fun settingsFrameWidth(stageWidth: Dp, sectionRight: Dp, gutter: Dp): Dp =
    minOf(SETTINGS_FRAME_MAX, stageWidth - gutter - sectionRight - SETTINGS_FRAME_GAP)
        .coerceAtLeast(minOf(SETTINGS_FRAME_MIN, stageWidth - gutter)).coerceAtLeast(0.dp)

/**
 * A Layouts group's picture width on a phone, the whole room it has: upright ([SettingsSizeClass.PHONE_UPRIGHT]) the
 * window inside the [gutter] on both sides; on its side (the wide stage) from the section block's right edge
 * ([sectionRight]) plus [SETTINGS_FRAME_GAP] to the right gutter, wider than the rows pane. A tablet-class window: null
 * (the rows' width, as before). Never below zero.
 */
fun settingsLayoutPictureWidth(sizeClass: SettingsSizeClass, windowWidth: Dp, sectionRight: Dp, gutter: Dp): Dp? = when (sizeClass) {
    SettingsSizeClass.PHONE_UPRIGHT -> (windowWidth - gutter * 2).coerceAtLeast(0.dp)
    SettingsSizeClass.PHONE_LANDSCAPE -> (windowWidth - gutter - sectionRight - SETTINGS_FRAME_GAP).coerceAtLeast(0.dp)
    SettingsSizeClass.TABLET -> null
}

/** The wide rows pane's left edge from the stage's left: its right edge at the [gutter] ([settingsFrameWidth]). */
fun settingsFrameLeft(stageWidth: Dp, sectionRight: Dp, gutter: Dp): Dp =
    stageWidth - gutter - settingsFrameWidth(stageWidth, sectionRight, gutter)

/**
 * The blocks' bottom inset inside the screen slot. App already pads the shell by the system bars (the gesture bar
 * included) and HandheldShell puts the slot above the on-screen pad, so this is all that is added here.
 */
val SETTINGS_BLOCK_BOTTOM = 24.dp


/** The label line ("Settings") above the first section: [NxTextSizes.menuInfo], this far above it. */
private val SETTINGS_LABEL_GAP = 6.dp

/** A section's line box as a share of its size. */
private const val SETTINGS_SECTION_LINE = 1.2f

/**
 * Where the section words start: the gutter plus a list row's inset ([nxListInset]), so they line up with the row labels
 * of a Settings page (whose plates sit at the gutter) and every Settings screen keeps the same side room.
 */
@Composable
internal fun settingsSectionStart(): Dp = settingsGutter(settingsSizeClass()) + nxListInset()

/** The size class of the window the screen is in ([LocalWindowSize]). */
@Composable
internal fun settingsSizeClass(): SettingsSizeClass = LocalWindowSize.current.let { settingsSizeClass(it.width.value, it.height.value) }

/**
 * The Settings section block: "Settings" in hint at 13 sp, 6 dp above the sections; the sections at
 * [SETTINGS_SECTION_SIZE], one per 1.2 line, the [current] one in the accent (the [main][com.nxredux.mobile.ui.nx.NxColors.main]
 * ink while [rowsFocused]) and the rest inkDim. No Back word: B (or the back gesture) goes back.
 * No pill, no underline, no plate; it never scrolls. A tap on a section calls [onTap] with its index.
 */
@Composable
fun SettingsSectionBlock(
    sections: List<SettingsSection>, current: Int, rowsFocused: Boolean,
    onTap: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val scale = nxTextScale()   // Appearance → Text size
    val wordStyle = settingsWordStyle()
    Column(modifier) {
        Text(
            SETTINGS_TITLE, color = colors.hint, fontFamily = font, fontSize = NxTextSizes.menuInfo.pinned() * scale,
            maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = SETTINGS_LABEL_GAP),
        )
        sections.forEachIndexed { i, section ->
            val color = when {
                i != current -> colors.inkDim
                rowsFocused -> colors.main
                else -> colors.accent
            }
            SettingsWord(section.label, color, wordStyle) { onTap(i) }
        }
    }
}

/**
 * A block word's style: [base] (a section's [SETTINGS_SECTION_SIZE]) under Appearance →
 * Text size, letter spacing −0.01 em, on a 1.2 line box with the word centred in it, so each word is one pitch.
 */
@Composable
private fun settingsWordStyle(base: TextUnit = SETTINGS_SECTION_SIZE): TextStyle {
    val size = base.pinned() * nxTextScale()
    val pitch = size * SETTINGS_SECTION_LINE
    return TextStyle(
        fontFamily = LocalNxFont.current, fontSize = size, letterSpacing = (-0.01).em, lineHeight = pitch,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** One line of [style] (a section word's pitch) in dp. */
@Composable
private fun settingsWordPitch(style: TextStyle): Dp = with(LocalDensity.current) { style.lineHeight.toDp() }

/** One word of the block on its own line box, tappable without a ripple. */
@Composable
private fun SettingsWord(text: String, color: Color, style: TextStyle, onTap: () -> Unit) {
    Text(
        text, color = color, style = style, maxLines = 1, softWrap = false,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    )
}

/** The small NX mark, [SETTINGS_MARK] square, in the [inkMark][com.nxredux.mobile.ui.nx.NxColors.inkMark] ink. */
@Composable
fun SettingsMark(modifier: Modifier = Modifier) {
    Image(
        painterResource(Res.drawable.nx_logo), contentDescription = null,
        colorFilter = ColorFilter.tint(LocalNxColors.current.inkMark), modifier = modifier.size(SETTINGS_MARK),
    )
}

/**
 * The Settings screens' background: plain, the small mark at the top right (the gutter in, [SETTINGS_MARK_TOP] down)
 * and nothing else above; the large centred mark the other tool pages have is not drawn.
 */
@Composable
internal fun SettingsBackground() {
    Box(Modifier.fillMaxSize().background(LocalNxColors.current.background)) {
        SettingsMark(Modifier.align(Alignment.TopEnd).padding(top = SETTINGS_MARK_TOP, end = settingsGutter(settingsSizeClass())))
    }
}

/**
 * A page drawn as the Settings rows block ([SettingsRowsBlock]) rather than as a whole page: its title the block's first
 * line, its rows anchored to the bottom. [crumb]: the words before the title ("Settings" on a narrow window, one level at
 * a time, as "Settings / Layouts", the block then the gutter in from both sides); null on the wide stage, where the
 * section block beside it already says Settings. [pictureWidth]: a Layouts group's picture under the rows at this width, right
 * edge at the gutter, wider than the pane ([settingsLayoutPictureWidth], a phone on its side); null: the rows' width.
 * [minHeight]: the least the block is, its first line, header and rows from the top (the section block's height, so a
 * short page's first line sits where "Settings" does); a longer page grows up from the bottom past it.
 */
data class SettingsBlockMode(val crumb: String?, val pictureWidth: Dp? = null, val minHeight: Dp = 0.dp)

/**
 * Whether [screen] draws as the rows block on a narrow window (one level at a time): a block page ([settingsBlockPage])
 * other than the root, which is the section block alone.
 */
fun settingsNarrowBlock(screen: Screen): Boolean = screen !is Screen.SettingsMenu && settingsBlockPage(screen)

/** The rows block's first line: "[crumb] / [title]", or the title alone without a crumb. */
fun settingsBlockLine(crumb: String?, title: String?): String =
    listOfNotNull(crumb, title?.takeIf { it.isNotEmpty() }).joinToString(SETTINGS_CRUMB_SEPARATOR)

/** Between the crumb and the title in the rows block's first line. */
private const val SETTINGS_CRUMB_SEPARATOR = " / "

/** Set while a Settings page is drawn as the rows block ([SettingsBlockMode]); null everywhere else (a page draws whole). */
val LocalSettingsBlock = compositionLocalOf<SettingsBlockMode?> { null }

/**
 * The rows block's edge fade, by row ([nxRowFade]): how many rows in view are dimmed while there is more beyond, the
 * last ones in view below (the first ones above once scrolled), at [SETTINGS_ROWS_FADE_STEPS]. The selection stops
 * clear of them, on the last full-opacity row.
 */
const val SETTINGS_ROWS_FADE_ROWS = 2

/** The dimmed rows' opacities, from the inner one to the one at the edge ([SETTINGS_ROWS_FADE_ROWS] of them). */
val SETTINGS_ROWS_FADE_STEPS = listOf(0.6f, 0.25f)

/** The least room the rows block leaves above it when it fills the height (it then scrolls). */
val SETTINGS_ROWS_TOP = 24.dp

/**
 * Whether [screen] draws as the rows block one level at a time (a narrow window, [settingsNarrowBlock]): the root,
 * every Settings page and sub-page, and Emulation's Default settings pages. A Layouts group keeps its page with the
 * picture, and the Android games picker its grid. The wide stage adds the Layouts groups ([settingsWideBlockPage]).
 */
fun settingsBlockPage(screen: Screen): Boolean = when (screen) {
    is Screen.SettingsMenu, is Screen.EmulatorList, is Screen.EmulatorConsoles -> true
    is Screen.Settings -> !screen.section.layoutGroup
    is Screen.EmulatorOptions -> !gameSettingsModal(screen)
    else -> false
}

/**
 * Whether [screen] draws in the wide stage's fixed frame: a block page ([settingsBlockPage]) or a Layouts group (its
 * rows, then its picture under them, in the block). The Android games picker draws whole.
 */
fun settingsWideBlockPage(screen: Screen): Boolean =
    settingsBlockPage(screen) || (screen is Screen.Settings && screen.section.layoutGroup)

/**
 * [content] given the section block's size ([SettingsSectionBlock] for [sections]): its widest line as drawn, so the
 * wide frame keeps clear of it, and its height, the rows block's least ([SettingsBlockMode.minHeight]); measured in the
 * same pass (no frame late).
 */
@Composable
internal fun WithSettingsSectionSize(sections: List<SettingsSection>, content: @Composable (DpSize) -> Unit) {
    // The content slot is kept while neither the size nor [content] changes, so a remeasure does not recompose the stage.
    val slot = remember { SectionSizeSlot() }
    SubcomposeLayout { constraints ->
        val probe = subcompose("probe") {
            SettingsSectionBlock(sections, current = 0, rowsFocused = false, onTap = {})
        }
        val width = probe.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val height = probe.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
        val placeables = subcompose("content", slot.of(DpSize(width.toDp(), height.toDp()), content)).map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
    }
}

/** [WithSettingsSectionSize]'s content slot: the same lambda for the same size and content. */
private class SectionSizeSlot {
    private var size: DpSize? = null
    private var content: (@Composable (DpSize) -> Unit)? = null
    private var slot: @Composable () -> Unit = {}

    fun of(size: DpSize, content: @Composable (DpSize) -> Unit): @Composable () -> Unit {
        if (size != this.size || content !== this.content) {
            this.size = size
            this.content = content
            slot = { content(size) }
        }
        return slot
    }
}

/**
 * The rows block: anchored to the bottom of what it is given, [SETTINGS_BLOCK_BOTTOM] up, so it sits on the section
 * block's baseline, and growing up as far as [SETTINGS_ROWS_TOP] under the top; past that its rows scroll. Narrow: the
 * gutter in from both sides; wide: the fixed frame's width ([settingsFrameWidth]), its right edge the gutter in. Its
 * first line is [title] in hint at 13 sp (after [mode]'s crumb in inkDim), level with the row labels and 6 dp above the
 * first row; then [header] (a banner, a page's lines), then [rows], which take what is left and scroll there (each list
 * fades the edge it has more beyond over [SETTINGS_ROWS_FADE_ROWS] rows). No pane header, nothing at the top.
 */
@Composable
fun SettingsRowsBlock(
    mode: SettingsBlockMode, title: String?, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null, rows: @Composable () -> Unit,
) {
    val colors = LocalNxColors.current
    val scale = nxTextScale()
    val gutter = settingsGutter(settingsSizeClass())
    Column(
        modifier.fillMaxSize().padding(
            start = if (mode.crumb != null) gutter else 0.dp,   // narrow: the gutter on both sides; wide: from the frame's left edge
            top = SETTINGS_ROWS_TOP, end = gutter, bottom = SETTINGS_BLOCK_BOTTOM,
        ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        // At least [SettingsBlockMode.minHeight], from the top: a short page's first line level with the section block's.
        Column(Modifier.fillMaxWidth().heightIn(min = mode.minHeight)) {
            val text = settingsBlockLine(mode.crumb, title)
            val crumbEnd = if (mode.crumb != null && text.length > mode.crumb.length) mode.crumb.length + SETTINGS_CRUMB_SEPARATOR.length else mode.crumb?.length ?: 0
            val line = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.inkDim)) { append(text.take(crumbEnd)) }   // "Settings /" in inkDim
                append(text.drop(crumbEnd))   // the title in the hint grey
            }
            if (line.isNotEmpty()) Text(
                line, color = colors.hint, fontFamily = LocalNxFont.current, fontSize = NxTextSizes.menuInfo.pinned() * scale,
                maxLines = 1, softWrap = false,
                modifier = Modifier.fillMaxWidth().padding(start = nxListInset(), end = nxListInset(), bottom = SETTINGS_LABEL_GAP),
            )
            header?.invoke()
            Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { rows() }
        }
    }
}

/** An unselected word-list row's tag against the row's own inkDim (ink-38 × 0.6); the selected row's tag is inkDim. */
const val SETTINGS_TAG_OF_ROW = 0.6f

/** A list's edge fade: [SETTINGS_ROWS_FADE_ROWS] of its [row] pitch in the rows block, else the list's own [default]. */
@Composable
fun settingsRowsFade(row: Dp, default: Dp): Dp = if (LocalSettingsBlock.current != null) row * SETTINGS_ROWS_FADE_ROWS else default


/**
 * A pill-list page's rows in the Settings rows block, drawn as the section block's words ([settingsWordStyle]): one per
 * pitch, the [selection] row in the accent and the rest inkDim, no pill. A row's tag after its name (an emulator's
 * consoles: 3DS, WII · GC) is at a Settings row's description size, medium weight, in its row's inkDim at
 * [SETTINGS_TAG_OF_ROW] (the selected row's in inkDim itself, never the accent), so the edge fade, applied to the whole row,
 * never leaves a tag brighter than its name. A tap selects, a tap on the selected row opens ([nxTap]); the selection is
 * kept in view and the edges fade like the other rows.
 */
@Composable
internal fun SettingsWordList(
    rows: List<NxRow>, selection: () -> Int, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier, listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val colors = LocalNxColors.current
    val style = settingsWordStyle()
    val tapOpens = LocalNxTapOpens.current
    val tagSize = nxOptionsLabelSize(nxListLabel()) * SETTING_DESC_OF_LABEL   // a Settings row's description size
    val tagFont = LocalNxFontWeights.current
    NxKeepSelectedInView(listState, selection, rows.size, SETTINGS_ROWS_FADE_ROWS)   // the selection clear of the fade
    val pitchPx = with(LocalDensity.current) { settingsWordPitch(style).roundToPx() }
    // As tall as a whole number of rows, snapping to a row's top: at rest it ends on a row boundary.
    BoxWithConstraints(modifier) {
        val fit = if (constraints.hasBoundedHeight) (constraints.maxHeight / maxOf(1, pitchPx)) * pitchPx else Int.MAX_VALUE
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = with(LocalDensity.current) { fit.toDp() }).nxRowEdgeMask(listState),
            state = listState, flingBehavior = rememberNxRowSnapFling(listState),
        ) {
            itemsIndexed(rows) { i, row ->
                val selected = i == selection()
                val text = buildAnnotatedString {
                    append(row.label)
                    row.secondary?.let {
                        append("  ")
                        // The selected row's tag in inkDim (not the accent); the others their row's inkDim at [SETTINGS_TAG_OF_ROW].
                        val tag = if (selected) colors.inkDim else colors.inkDim.let { it.copy(alpha = it.alpha * SETTINGS_TAG_OF_ROW) }   // role: a word list's tag
                        withStyle(SpanStyle(color = tag, fontSize = tagSize, fontFamily = tagFont, fontWeight = FontWeight.Medium)) { append(it) }
                    }
                }
                Text(
                    text, color = if (selected) colors.accent else colors.inkDim, style = style, maxLines = 1, softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().nxRowFade(listState, i, SETTINGS_ROWS_FADE_STEPS).padding(start = nxListInset())
                        .alpha(if (row.enabled) 1f else NX_DISABLED_ALPHA)   // role: a disabled row
                        .clickable(remember { MutableInteractionSource() }, indication = null) { nxTap(i, selected, tapOpens(i), onSelect, onOpen) },
                )
            }
        }
    }
}
