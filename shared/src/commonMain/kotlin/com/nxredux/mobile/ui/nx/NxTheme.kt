package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.SecondaryAccent
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.misans_regular
import com.nxredux.mobile.shared.res.misans_semibold
import org.jetbrains.compose.resources.Font

data class NxColors(
    val background: Color = Color(0xFF000000),
    // The UI white (ink): every white the UI draws (text, icons, hint glyphs, separators, borders, the pill below) is this
    // or this at an alpha. #D8D8D8, as pure white on the black ground is harsh. Art, screenshots and game output keep theirs.
    val main: Color = Color(0xFFD8D8D8),        // pill
    val listText: Color = Color(0xFFD8D8D8),
    val listTextSelected: Color = Color(0xFF000000),
    val hint: Color = Color(0xFF999999),        // header title, hint labels, dim text
    val secondarySelected: Color = Color(0x8C000000), // a row's secondary text on the white pill (listTextSelected at 55%)
    val accent1: Color = Color(0xFFD8D8D8),
    val accent2: Color = Color(0xFF9B9B9B),
    // NX Redux THEME_COLOR2 (NX teal, the Teal preset), where the Secondary accent does not reach (ToolAppsScreen's unlit
    // badge); NxOptionsRow's bar (the Settings value pages, the in-game menu) draws [secondaryAccent] instead.
    val optionBar: Color = Color(AccentPreset.TEAL.argb),
    val success: Color = Color(0xFF82DC82),     // a good state line (unlocked): NX Redux {130,220,130}
    val warning: Color = Color(0xFFFFC850),     // an attention state line (pending): NX Redux {255,200,80}
    // Darker so they read on an accent with black text; on a light-text accent (Red, Teal) the plain [success] / [warning] read.
    val successSelected: Color = Color(0xFF237A2E),   // [success] on the white pill: darker so it reads on white
    val warningSelected: Color = Color(0xFF9A6500),   // [warning] on the white pill
    /**
     * The one colour of every selection mark: the NxList / NxRichList pill, the tab underline and tab-focus plate, a game
     * tile's ring, Home's lit cards, ToolAppsScreen's lit tile, NxOptionsRow's label rect (as NX Redux's
     * UI_renderSettingsRow) and the in-game menu's pill, options rows and adjust strip. The Accent setting ([nxColorsFor]);
     * default White, zinc-200 #E4E4E7, a step brighter than the [main] ink so the pill stands out from the text. Plain
     * text, unselected greys, art and separators keep theirs. NxModal's primary button and the unlit plate edges
     * (tilePlate's edge, the 8% edges, the Backdrop placeholder spine, App's 16% console logo) stay on [main] /
     * [listTextSelected] whatever the accent.
     */
    val accent: Color = Color(AccentPreset.DEFAULT.argb),
    /** Text, glyphs and lit icons on the [accent]: the preset's own ([AccentPreset.onRgb]), black but for the light text of Red and Teal. */
    val onAccent: Color = Color(AccentPreset.DEFAULT.onArgb),
    /**
     * The bar behind a selected option's value on the Settings value pages, the Game Settings modal and the in-game
     * menu's options rows and adjust strip: the Secondary accent setting ([nxColorsFor]), default Teal (NX teal,
     * the [optionBar]). [onSecondaryAccent] is the value's text on it.
     */
    val secondaryAccent: Color = Color(AccentPreset.TEAL.argb),
    val onSecondaryAccent: Color = Color(AccentPreset.TEAL.onArgb),
) {
    // In the body, not constructor defaults: they follow [onAccent] and cannot drift from it.
    val onAccentMuted: Color = onAccent.copy(alpha = 0.55f)   // a row's secondary text on the accent ([secondarySelected] on white)
    private val lightOnAccent: Boolean = onAccent != Color.Black
    val successOnAccent: Color = if (lightOnAccent) success else successSelected   // [success] on the accent
    val warningOnAccent: Color = if (lightOnAccent) warning else warningSelected   // [warning] on the accent
}

/**
 * The theme's colours with [preset] as the accent and its text colour (the Accent setting), and [secondary]'s bar and text
 * for that accent (the Secondary accent setting); everything else as [NxDefaults].
 */
fun nxColorsFor(preset: AccentPreset, secondary: SecondaryAccent = SecondaryAccent.DEFAULT): NxColors = NxColors(
    accent = Color(preset.argb), onAccent = Color(preset.onArgb),
    secondaryAccent = Color(0xFF000000 or secondary.rgb(preset).toLong()),
    onSecondaryAccent = Color(0xFF000000 or secondary.onRgb(preset).toLong()),
)

object NxDefaults { val colors = NxColors() }

val LocalNxColors = staticCompositionLocalOf { NxDefaults.colors }
/**
 * The app's font: MiSans Semibold alone, so every weight asked of it (none set, Normal, SemiBold, Bold) draws Semibold,
 * the one look the UI is drawn in.
 */
val LocalNxFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * MiSans with both bundled weights: FontWeight.Normal draws Regular, SemiBold and Bold draw Semibold. For the text set
 * apart from its name by a lighter weight, asked for with FontWeight.Normal: the settings rows' descriptions and
 * paragraphs (NxSettingsRow) and the in-game menu's option descriptions (InGameMenuOptions). Not [LocalNxFont], where an
 * unset weight is Normal: that text would all turn Regular.
 */
val LocalNxFontWeights = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * The selection highlight's colour (a list's pill, a lit tile's fill or ring, Home's lit card): [NxColors.accent]. The
 * main menu's tab focus dims the whole content as one layer instead ([com.nxredux.mobile.ui.mainMenuContentAlpha]).
 */
@Composable
fun nxHighlight(): Color = LocalNxColors.current.accent

/** The system font scale (Settings → Display → Font size) the app's text follows, held to this range ([nxFontScale]). */
internal const val NX_FONT_SCALE_MIN = 0.85f
internal const val NX_FONT_SCALE_MAX = 1.3f

/** The font scale drawn at for the system's [system]: as set, held to [NX_FONT_SCALE_MIN]..[NX_FONT_SCALE_MAX]. */
internal fun nxFontScale(system: Float): Float = system.coerceIn(NX_FONT_SCALE_MIN, NX_FONT_SCALE_MAX)

/**
 * The app's colours and font, and its text's font scale held to [nxFontScale]: a larger system font grows the reading
 * text up to [NX_FONT_SCALE_MAX] and no further, so the fixed layouts never clip. Everything sized from
 * [LocalDensity]'s font scale (the rows, the strip, the captions) follows the held one.
 */
@Composable
fun NxTheme(colors: NxColors = NxDefaults.colors, content: @Composable () -> Unit) {
    val semibold = Font(Res.font.misans_semibold, FontWeight.SemiBold)
    val regular = Font(Res.font.misans_regular, FontWeight.Normal)
    val font = remember(semibold) { FontFamily(semibold) }
    val weights = remember(regular, semibold) { FontFamily(regular, semibold) }
    val system = LocalDensity.current
    val held = nxFontScale(system.fontScale)
    val density = remember(system.density, held) { Density(system.density, held) }
    CompositionLocalProvider(
        LocalNxColors provides colors,
        LocalNxFont provides font,
        LocalNxFontWeights provides weights,
        LocalDensity provides density,
        content = content,
    )
}

/**
 * [this] drawn at its size whatever the font scale (an sp size divided by it): text on a fixed shape, which could only
 * clip if it grew. The pad's labels, the tab words and page title, the hint bar, a list row's tag and Home's badges.
 */
@Composable
@ReadOnlyComposable
fun TextUnit.pinned(): TextUnit = if (isSp) (value / LocalDensity.current.fontScale).sp else this
