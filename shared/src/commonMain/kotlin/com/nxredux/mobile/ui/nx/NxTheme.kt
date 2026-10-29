package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.settings.AccentPreset
import com.nxredux.mobile.settings.SECONDARY_TEXT
import com.nxredux.mobile.settings.SecondaryPreset
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
    // NX Redux THEME_COLOR2 (NX teal, the Teal Secondary accent), where the Secondary accent does not reach (ToolAppsScreen's unlit
    // badge); NxOptionsRow's bar (the Settings value pages, the in-game menu) draws [secondaryAccent] instead.
    val optionBar: Color = Color(SecondaryPreset.TEAL.argb),
    val success: Color = Color(0xFF82DC82),     // a good state line (unlocked): NX Redux {130,220,130}
    val warning: Color = Color(0xFFFFC850),     // an attention state line (pending): NX Redux {255,200,80}
    // Darker so they read on the accent (every accent is light, its text black).
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
    /** Text, glyphs and lit icons on the [accent]: black, as every accent preset is light. */
    val onAccent: Color = Color(0xFF000000),
    /**
     * The bar behind a selected option's value on the Settings value pages, the Game Settings modal and the in-game
     * menu's options rows and adjust strip: the Secondary accent setting ([nxColorsFor]), default Teal (NX teal,
     * the [optionBar]). [onSecondaryAccent] is the value's text on it.
     */
    val secondaryAccent: Color = Color(SecondaryPreset.TEAL.argb),
    val onSecondaryAccent: Color = Color(0xFF000000 or SECONDARY_TEXT.toLong()),
) {
    // In the body, not constructor defaults: they follow [onAccent] and cannot drift from it.
    val onAccentMuted: Color = onAccent.copy(alpha = 0.55f)   // a row's secondary text on the accent ([secondarySelected] on white)
    val successOnAccent: Color = successSelected   // [success] on the accent
    val warningOnAccent: Color = warningSelected   // [warning] on the accent

    /** Unselected tab words, the top-right icons and an unselected Grid tile's name: the [main] ink at 38%. */
    val inkDim: Color = main.copy(alpha = 0.38f)
    /** List Home's unselected names: the [main] ink at 70%. */
    val inkName: Color = main.copy(alpha = 0.70f)
    /** An unlit tag pill (Continue) and the unlit plate behind a word: the [main] ink at 14%. */
    val inkFaint: Color = main.copy(alpha = 0.14f)
    /**
     * The ring of every outlined button and segment: NxModal's secondary button, NxDialog, NxSettingsRow's outlined
     * button and NxTouchRows' segmented control. The [main] ink at 30%.
     */
    val inkRing: Color = main.copy(alpha = 0.30f)
    /** Hairlines: a tile's edge, a placeholder spine, a sheet's edge. The [main] ink at 8%. */
    val inkEdge: Color = main.copy(alpha = 0.08f)
    /** The small NX mark at the top right of Settings, setup and the folder picker (SettingsMark): the [main] ink at 9%. */
    val inkMark: Color = main.copy(alpha = 0.09f)
    /** The Settings help note's inner card border (NxSettingInfoPopup): the [main] ink at 10%. */
    val inkLine: Color = main.copy(alpha = 0.10f)
    /** The Settings help note's outer sheet (NxSettingInfoPopup): the [accent] at 14% over black, opaque. */
    val accentFaint: Color = accent.copy(alpha = 0.14f).compositeOver(Color(0xFF000000))
    /**
     * The hint bar over art: NxHintBar's ground and the shade over a hidden bar's kept room (NxScreenScaffold). Its alpha
     * is [NX_HINT_BAR_ALPHA], where an info band's fade starts.
     */
    val scrimHints: Color = Color(0xCC000000)
    /**
     * The Game Switcher's bar (GameSwitcherScreen's NxHintBar and its hidden bar's shade): darker, as screenshots can be
     * light. Its alpha is [NX_SWITCHER_BAR_ALPHA], where the switcher's info band and the tab row's top band fade from.
     */
    val scrimSwitcher: Color = Color(0xE6000000)
    /** What dims a Backdrop game list's screenshot (NxBackdropPicture), under its shade: the picture at 25%. */
    val scrimBackdrop: Color = Color(0xBF000000)
    /** A notice's backing: App's notice line and NxNotifications' toasts. */
    val scrimNotice: Color = Color(0xCC000000)
    /** Over the menu kept still under a tool that has the focus on the top screen (App's PageContent). */
    val scrimUnder: Color = Color(0x66000000)
    /** The in-game menu's wash over the paused frame behind its pages (InGameMenu): the game shows only faintly. */
    val scrimMenuWash: Color = Color(0xF5000000)
    /** The in-game menu's adjust strip over the paused frame (InGameMenu's AdjustContent). */
    val scrimAdjust: Color = Color(0xD9000000)
    /** The screen under a modal: NxModal, GameSettingsModal, RaPanelView, NxTouchRows' picker. */
    val scrimModal: Color = Color(0xB3000000)
    /** Around the Settings help note (NxSettingInfoPopup): everything but the focused row, black at 80%. */
    val scrimHelp: Color = Color(0xCC000000)
    /** NxLoadingOverlay's scrim: darker than [scrimModal], so its message reads over list text. */
    val scrimLoading: Color = Color(0xE6000000)
    /** A modal's panel: NxModal, GameSettingsModal, GameListLayoutMenu, RaPanelView, NxTouchRows' picker. */
    val modalPanel: Color = Color(0xFF121212)
    /** The card in a modal's panel that holds its body: NxModal, GameSettingsModal, NxTouchRows' picker. */
    val modalCard: Color = Color(0xFF1E1E1E)
    /** The fast-forward / rewind indicator's backing (SpeedIndicator). */
    val indicatorBacking: Color = Color(0xB3000000)
    /** A text field's ground: NxTextPrompt, NxSignInPrompt. */
    val fieldGround: Color = Color(0xFF2A2A2A)
    /** The ground behind a picture that may be missing or see-through: NxBadge, NxRichList's round thumbnails. */
    val pictureGround: Color = Color(0xFF1A1A1A)
    /** A hint badge's label on its [main] disc or pill (NxBadgeGlyph). */
    val badgeText: Color = Color(0xFF1C1C1C)
    /** The in-game menu's save-slot card (on [main]): its date line. */
    val slotDate: Color = Color(0xFF3A3A3A)
    /** The save-slot card's dot for the shown slot. */
    val slotDotLit: Color = Color(0xFF111111)
    /** The save-slot card's dots for the other slots. */
    val slotDot: Color = Color(0xFF8C8C8C)
    /** The pad layout editor (PadLayoutEditor): the game picture's stand-in. */
    val editorGameBlock: Color = Color(0xFF3A3A3A)
    /** The pad layout editor: a control overlapping another or the game. */
    val editorOverlap: Color = Color(0xFFE5484D)
    /** The pad layout editor: the hinge between a foldable's two halves. */
    val editorHinge: Color = Color(0xFF6A6A6A)
    /** The pad layout editor's floating toolbar panel. */
    val editorPanel: Color = Color(0xE6121212)
    /** The pad layout editor's toolbar buttons (not the primary one, on the accent). */
    val editorButton: Color = Color(0xFF2A2A2A)
}

/** The Secondary accent's Auto for the accent in use: the colour its swatch shows ([NxSwatches]). */
@Composable
@ReadOnlyComposable
internal fun nxAutoSecondary(): Color {
    val accent = LocalNxColors.current.accent
    val preset = AccentPreset.entries.firstOrNull { Color(it.argb) == accent } ?: AccentPreset.DEFAULT
    return Color(0xFF000000 or SecondaryAccent.Auto.rgb(preset).toLong())
}

/**
 * The theme's colours with [preset] as the accent and its text colour (the Accent setting), and [secondary]'s bar and text
 * for that accent (the Secondary accent setting); everything else as [NxDefaults].
 */
fun nxColorsFor(preset: AccentPreset, secondary: SecondaryAccent = SecondaryAccent.DEFAULT): NxColors = NxColors(
    accent = Color(preset.argb),
    secondaryAccent = Color(0xFF000000 or secondary.rgb(preset).toLong()),
    onSecondaryAccent = Color(0xFF000000 or secondary.onRgb().toLong()),
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
 * The selection highlight's colour (a list's pill, a lit tile's fill or ring, Home's lit card): [NxColors.accent].
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
