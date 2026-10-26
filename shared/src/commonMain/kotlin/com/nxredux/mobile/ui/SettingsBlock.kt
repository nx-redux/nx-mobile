package com.nxredux.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import com.nxredux.mobile.ui.nx.FooterMode
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

/** The Back word's size, a step under the sections' (before Appearance → Text size). */
val SETTINGS_BACK_SIZE: TextUnit = 24.sp

/** The NX mark at the top right: its size and its inset from the top. Its right inset is the gutter. */
val SETTINGS_MARK = 48.dp
val SETTINGS_MARK_TOP = 16.dp

/** The wide rows pane's most width ([settingsFrameWidth]). */
val SETTINGS_FRAME_MAX = 560.dp

/** The least gap between the section block's right edge and the wide rows pane. */
val SETTINGS_FRAME_GAP = 24.dp

/** The wide rows pane's top line ([settingsTopLine]), as a share of the band's height. */
const val SETTINGS_TOP_LINE = 0.4f

/** Between a Layouts group's picture and the top line under it, on the wide stage. */
val SETTINGS_PICTURE_GAP = 24.dp

/**
 * The wide rows pane's width: [SETTINGS_FRAME_MAX], or less when the room between the section block's right edge
 * ([sectionRight], from the stage's left) plus [SETTINGS_FRAME_GAP] and the right [gutter] is narrower. Never sized by
 * the page, so its left edge is the same on every page.
 */
fun settingsFrameWidth(stageWidth: Dp, sectionRight: Dp, gutter: Dp): Dp =
    minOf(SETTINGS_FRAME_MAX, stageWidth - gutter - sectionRight - SETTINGS_FRAME_GAP).coerceAtLeast(0.dp)

/** The wide rows pane's left edge from the stage's left: its right edge at the [gutter] ([settingsFrameWidth]). */
fun settingsFrameLeft(stageWidth: Dp, sectionRight: Dp, gutter: Dp): Dp =
    stageWidth - gutter - settingsFrameWidth(stageWidth, sectionRight, gutter)

/** The wide rows pane's top line: [SETTINGS_TOP_LINE] of the [band] (the stage: the screen inside the system bars), every page. */
fun settingsTopLine(band: Dp): Dp = band * SETTINGS_TOP_LINE

/**
 * A framed picture's width ([mat] each side, the picture [aspect] wide over high inside) in a room [width] by [height]:
 * the room's width, or narrower so its height fits (the aspect kept); never below zero.
 */
fun settingsPictureWidth(width: Dp, height: Dp, aspect: Float, mat: Dp): Dp =
    minOf(width, (height - mat * 2) * aspect + mat * 2).coerceAtLeast(0.dp)

/** The least room above a picture over the top line: under the small mark ([SETTINGS_MARK_TOP] + [SETTINGS_MARK]), 16 dp clear. */
val SETTINGS_PICTURE_TOP = SETTINGS_MARK_TOP + SETTINGS_MARK + 16.dp

/**
 * The blocks' bottom inset inside the screen slot. App already pads the shell by the system bars (the gesture bar
 * included) and HandheldShell puts the slot above the on-screen pad, so this is all that is added here.
 */
val SETTINGS_BLOCK_BOTTOM = 24.dp


/** The label line ("Settings") above the first section: [NxTextSizes.menuInfo], this far above it. */
private val SETTINGS_LABEL_GAP = 6.dp

/** A section's line box as a share of its size. */
private const val SETTINGS_SECTION_LINE = 1.2f

/** The Back word is drawn only in touch mode (no controller, the on-screen pad hidden); with a pad, B goes back. */
fun settingsBackShown(footer: FooterMode): Boolean = footer == FooterMode.BUTTONS

/** The size class of the window the screen is in ([LocalWindowSize]). */
@Composable
internal fun settingsSizeClass(): SettingsSizeClass = LocalWindowSize.current.let { settingsSizeClass(it.width.value, it.height.value) }

/**
 * The Settings section block: "Settings" in hint at 13 sp, 6 dp above the sections; the sections at
 * [SETTINGS_SECTION_SIZE], one per 1.2 line, the [current] one in the accent (the [main][com.nxredux.mobile.ui.nx.NxColors.main]
 * ink while [rowsFocused]) and the rest inkDim; then, one pitch below the last, "Back" in inkDim at [SETTINGS_BACK_SIZE] ([onBack] null: none).
 * No pill, no underline, no plate; it never scrolls. A tap on a section calls [onTap] with its index.
 */
@Composable
fun SettingsSectionBlock(
    sections: List<SettingsSection>, current: Int, rowsFocused: Boolean,
    onTap: (Int) -> Unit, onBack: (() -> Unit)?, modifier: Modifier = Modifier,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val scale = nxTextScale()   // Appearance → Text size
    val wordStyle = settingsWordStyle()
    val pitchDp = settingsWordPitch(wordStyle)
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
        if (onBack != null) {
            Spacer(Modifier.height(pitchDp))
            SettingsWord("Back", colors.inkDim, settingsWordStyle(SETTINGS_BACK_SIZE), onBack)
        }
    }
}

/**
 * A block word's style: [base] (a section's [SETTINGS_SECTION_SIZE], or Back's [SETTINGS_BACK_SIZE]) under Appearance →
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
 * section block beside it already says Settings. [onBack]: the Back word one pitch under the rows (narrow, touch mode
 * only, [settingsBackShown]); null: none. [topLine]: the wide stage's fixed frame ([settingsTopLine]): the block's first
 * line this far from the top on every page, the rows growing down from it; null: anchored to the bottom (narrow).
 */
data class SettingsBlockMode(val crumb: String?, val onBack: (() -> Unit)? = null, val topLine: Dp? = null)

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

/** The rows block's fade at an edge it scrolls past. */
val SETTINGS_ROWS_FADE = 24.dp

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
 * picture above the top line, its rows in the frame). The Android games picker draws whole.
 */
fun settingsWideBlockPage(screen: Screen): Boolean =
    settingsBlockPage(screen) || (screen is Screen.Settings && screen.section.layoutGroup)

/**
 * [content] given the section block's width ([SettingsSectionBlock] for [sections], with the Back word when [back]): the
 * block's own widest line as drawn, measured in the same pass (no frame late), so the wide frame can keep clear of it.
 */
@Composable
internal fun WithSettingsSectionWidth(sections: List<SettingsSection>, back: Boolean, content: @Composable (Dp) -> Unit) {
    SubcomposeLayout { constraints ->
        val probe = subcompose("probe") {
            SettingsSectionBlock(sections, current = 0, rowsFocused = false, onTap = {}, onBack = if (back) ({}) else null)
        }
        val width = probe.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val placeables = subcompose("content") { content(width.toDp()) }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
    }
}

/**
 * The rows block. Narrow ([SettingsBlockMode.topLine] null): anchored to the bottom of what it is given,
 * [SETTINGS_BLOCK_BOTTOM] up and the gutter in from both sides. Wide: a fixed frame, its right edge the gutter in, its
 * first line [SettingsBlockMode.topLine] from the top on every page, growing down to [SETTINGS_BLOCK_BOTTOM] above the
 * bottom; [above] (a Layouts group's picture) in the room over that line, [SETTINGS_PICTURE_GAP] clear of it and of the
 * mark at the top ([SETTINGS_PICTURE_TOP]), given that room. Its first line is [title] in hint at 13 sp (after [mode]'s
 * crumb in inkDim), level with the row labels and 6 dp above the first row; then [header] (a banner, a page's lines),
 * then [rows], which take what is left and scroll there (each list fades the edge it has more beyond, [SETTINGS_ROWS_FADE]).
 */
@Composable
fun SettingsRowsBlock(
    mode: SettingsBlockMode, title: String?, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null, above: (@Composable () -> Unit)? = null, rows: @Composable () -> Unit,
) {
    val colors = LocalNxColors.current
    val scale = nxTextScale()
    val gutter = settingsGutter(settingsSizeClass())
    val topLine = mode.topLine
    Box(modifier.fillMaxSize()) {
        if (topLine != null && above != null && topLine - SETTINGS_PICTURE_GAP > SETTINGS_PICTURE_TOP) Box(
            Modifier.fillMaxWidth().height(topLine - SETTINGS_PICTURE_GAP).padding(top = SETTINGS_PICTURE_TOP, end = gutter),
            contentAlignment = Alignment.BottomStart,
        ) { above() }
        Column(
            Modifier.fillMaxSize().padding(
                start = if (mode.crumb != null) gutter else 0.dp,   // narrow: the gutter on both sides; wide: from the frame's left edge
                top = topLine ?: SETTINGS_ROWS_TOP, end = gutter, bottom = SETTINGS_BLOCK_BOTTOM,
            ),
            verticalArrangement = if (topLine != null) Arrangement.Top else Arrangement.Bottom,
        ) {
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
            mode.onBack?.let { back ->
                // The section block's Back: inkDim at [SETTINGS_BACK_SIZE], one section pitch under the rows, level with the row labels.
                Spacer(Modifier.height(settingsWordPitch(settingsWordStyle())))
                Box(Modifier.padding(start = nxListInset())) { SettingsWord("Back", colors.inkDim, settingsWordStyle(SETTINGS_BACK_SIZE), back) }
            }
        }
    }
}

/** A list's edge fade: [SETTINGS_ROWS_FADE] in the rows block, else the list's own [default]. */
@Composable
fun settingsRowsFade(default: Dp): Dp = if (LocalSettingsBlock.current != null) SETTINGS_ROWS_FADE else default
