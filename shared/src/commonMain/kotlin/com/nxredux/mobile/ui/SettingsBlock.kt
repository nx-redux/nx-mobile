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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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

/** A section word's size: 24 sp on a phone held upright, else 26 sp (before Appearance → Text size). */
fun settingsSectionSize(c: SettingsSizeClass): TextUnit = if (c == SettingsSizeClass.PHONE_UPRIGHT) 24.sp else 26.sp

/** The rows block's left edge on a wide window, as a share of the width. */
const val SETTINGS_ROWS_LEFT = 0.44f

/**
 * The blocks' bottom inset inside the screen slot. App already pads the shell by the system bars (the gesture bar
 * included) and HandheldShell puts the slot above the on-screen pad, so this is all that is added here.
 */
val SETTINGS_BLOCK_BOTTOM = 24.dp

/** The NX mark at the top right: its size and its inset from the top. Its right inset is the gutter. */
val SETTINGS_MARK = 48.dp
val SETTINGS_MARK_TOP = 16.dp

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
 * [settingsSectionSize], one per 1.2 line, the [current] one in the accent (the [main][com.nxredux.mobile.ui.nx.NxColors.main]
 * ink while [rowsFocused]) and the rest inkDim; then, one pitch below the last, "Back" in inkDim ([onBack] null: none).
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
    val size = settingsSectionSize(settingsSizeClass()).pinned() * scale
    val pitch = size * SETTINGS_SECTION_LINE
    val pitchDp = with(LocalDensity.current) { pitch.toDp() }
    val wordStyle = TextStyle(
        fontFamily = font, fontSize = size, letterSpacing = (-0.01).em, lineHeight = pitch,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
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
            SettingsWord("Back", colors.inkDim, wordStyle, onBack)
        }
    }
}

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
