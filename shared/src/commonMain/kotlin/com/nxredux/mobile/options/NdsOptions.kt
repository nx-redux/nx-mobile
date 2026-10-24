package com.nxredux.mobile.options

import com.nxredux.mobile.settings.stepFrom

/** A DS setting's stored [value] and what the menu and hotkey labels show. */
interface NdsChoice {
    val value: String
    val label: String
}

/** [AUTO] is a stored choice only: [resolve] turns it into a concrete layout for the game area before anything is drawn. */
enum class NdsPortraitLayout(override val value: String, override val label: String) : NdsChoice {
    AUTO("auto", "Auto"), PIP("pip", "Picture in picture"), STACKED("stacked", "Stacked"), SINGLE("single", "Single screen"),
    LARGE("large", "Large screen"), SIDE_BY_SIDE("side-by-side", "Side by side");

    /**
     * The layout to draw in an [areaWidth] x [areaHeight] game area; never [AUTO]. Auto stacks when the two screens
     * stacked (256x384, 2:3) can be at least 90% as wide as the area, else picture in picture (also for an empty area).
     */
    fun resolve(areaWidth: Float, areaHeight: Float): NdsPortraitLayout {
        if (this != AUTO) return this
        if (areaWidth <= 0f || areaHeight <= 0f) return PIP
        // In tenths, with no 0.9 or 1.5 float factors, so exactly 90% counts (1000 x 1350 is 900 wide).
        return if (minOf(areaWidth, areaHeight * 2f / 3f) * 10f >= areaWidth * 9f) STACKED else PIP
    }
}

enum class NdsLandscapeLayout(override val value: String, override val label: String) : NdsChoice {
    SIDE_BY_SIDE("side-by-side", "Side by side"), SINGLE("single", "Single screen"), PIP("pip", "Picture in picture"),
    LARGE("large", "Large screen"), STACKED("stacked", "Stacked"),
}

enum class NdsScreen(override val value: String, override val label: String) : NdsChoice {
    TOP("top", "Top screen"), BOTTOM("bottom", "Bottom screen");

    val other: NdsScreen get() = if (this == TOP) BOTTOM else TOP
}

enum class PipCorner(override val value: String, override val label: String) : NdsChoice {
    TOP_LEFT("top-left", "Top left"), TOP_RIGHT("top-right", "Top right"),
    BOTTOM_LEFT("bottom-left", "Bottom left"), BOTTOM_RIGHT("bottom-right", "Bottom right"),
}

/** [fraction]: the small screen's width as a share of the big screen's width (picture in picture and large screen). */
enum class PipSize(override val value: String, override val label: String, val fraction: Float) : NdsChoice {
    SMALL("small", "Small", 0.25f), MEDIUM("medium", "Medium", 0.33f), LARGE("large", "Large", 0.40f),
}

/** [alpha]: how opaque the inset is drawn over the big screen. */
enum class PipOpacity(override val value: String, override val label: String) : NdsChoice {
    FULL("100", "100%"), THREE_QUARTERS("75", "75%"), HALF("50", "50%");

    val alpha: Float get() = value.toInt() / 100f
}

/**
 * The DS screen settings in effect; unknown stored values fall back to the defaults. [controller] is the portrait layout
 * used instead of [portrait] while a non-clip-on controller has dropped the pad band ([portraitLayout]).
 */
data class NdsSettings(
    val portrait: NdsPortraitLayout = NdsPortraitLayout.AUTO,
    val landscape: NdsLandscapeLayout = NdsLandscapeLayout.STACKED,
    val big: NdsScreen = NdsScreen.TOP,
    val corner: PipCorner = PipCorner.TOP_RIGHT,
    val size: PipSize = PipSize.MEDIUM,
    val opacity: PipOpacity = PipOpacity.FULL,
    val controller: NdsPortraitLayout = NdsPortraitLayout.STACKED,
) {
    /** The portrait layout in use: [controller] in [controllerMode] (no pad band, a non-clip-on controller), else [portrait]. */
    fun portraitLayout(controllerMode: Boolean): NdsPortraitLayout = if (controllerMode) controller else portrait

    /** [portraitLayout] resolved for an [areaWidth] x [areaHeight] game area ([NdsPortraitLayout.resolve]); never AUTO. */
    fun portraitLayout(controllerMode: Boolean, areaWidth: Float, areaHeight: Float): NdsPortraitLayout =
        portraitLayout(controllerMode).resolve(areaWidth, areaHeight)

    companion object {
        fun parse(values: Map<String, String>): NdsSettings {
            val d = NdsSettings()
            return NdsSettings(
                portrait = pick(values[NdsOptions.LAYOUT_PORTRAIT], NdsPortraitLayout.entries, d.portrait),
                landscape = pick(values[NdsOptions.LAYOUT_LANDSCAPE], NdsLandscapeLayout.entries, d.landscape),
                big = pick(values[NdsOptions.BIG_SCREEN], NdsScreen.entries, d.big),
                corner = pick(values[NdsOptions.PIP_CORNER], PipCorner.entries, d.corner),
                size = pick(values[NdsOptions.PIP_SIZE], PipSize.entries, d.size),
                opacity = pick(values[NdsOptions.PIP_OPACITY], PipOpacity.entries, d.opacity),
                controller = pick(values[NdsOptions.LAYOUT_PORTRAIT_CONTROLLER], NdsPortraitLayout.entries, d.controller),
            )
        }

        private fun <E : NdsChoice> pick(value: String?, all: List<E>, default: E): E = all.firstOrNull { it.value == value } ?: default
    }
}

/**
 * The DS screen settings: frontend `nx_` keys, per game with a console default, stripped before the
 * core sees them, shown on the Options → Console Settings page for NDS only ([ConsoleSettings.tableFor]).
 */
object NdsOptions {
    const val LAYOUT_PORTRAIT = "nx_nds_layout_portrait_v2"
    const val LAYOUT_PORTRAIT_CONTROLLER = "nx_nds_layout_portrait_controller"
    const val LAYOUT_LANDSCAPE = "nx_nds_layout_landscape"
    const val BIG_SCREEN = "nx_nds_big_screen"
    const val PIP_CORNER = "nx_nds_pip_corner"
    const val PIP_SIZE = "nx_nds_pip_size"
    const val PIP_OPACITY = "nx_nds_pip_opacity"
    const val CORE_RESOLUTION = "melonds_opengl_resolution"

    private const val SPLIT_NOTE = "Screen Scaling and the screen offsets do not apply to the DS screens."

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "NDS"

    /** The layouts SELECT + Left/Right walks: Auto is left to the menu, so a press always lands on a layout it can show. */
    val CONCRETE_PORTRAIT: List<NdsPortraitLayout> = NdsPortraitLayout.entries - NdsPortraitLayout.AUTO

    private fun <E : NdsChoice> row(key: String, label: String, all: List<E>, default: E, info: String, brief: String = "") =
        FrontendOption(key, label, all.map { it.value }, default.value, info, visual = true, valueLabels = all.map { it.label }, brief = brief)

    val table: List<FrontendOption> = listOf(
        row(
            LAYOUT_PORTRAIT, "Layout (portrait)", NdsPortraitLayout.entries, NdsPortraitLayout.AUTO,
            "How the two screens share a portrait screen.\nAuto: stacked when both screens fit the width, else picture in picture.\n" +
                "Picture in picture: one big, the other small in a corner.\n" +
                "Stacked: one above the other.\nSingle screen: the big one only.\n" +
                "Large screen: one big, the other small above or below it.\n" +
                "Side by side: half the width each.\nSELECT + Left/Right cycles it, or Layout (portrait, controller)\n" +
                "while a controller hides the pad.\n$SPLIT_NOTE",
        ),
        row(
            LAYOUT_PORTRAIT_CONTROLLER, "Layout (portrait, controller)", NdsPortraitLayout.entries, NdsPortraitLayout.STACKED,
            "The portrait layout while a controller that is not a clip-on\n(Pocket Taco, FlipPad) is connected and the pad is hidden.\n" +
                "SELECT + Left/Right cycles it then.\n$SPLIT_NOTE",
            brief = "Used while a controller (not a clip-on) hides the pad",
        ),
        row(
            LAYOUT_LANDSCAPE, "Layout (landscape)", NdsLandscapeLayout.entries, NdsLandscapeLayout.STACKED,
            "How the two screens share a landscape screen.\nSide by side: half the width each.\nSingle screen: the big one, centred.\nPicture in picture: the big one centred, the other small in a corner.\n" +
                "Large screen: one big, the other small beside it.\nStacked: one above the other, the first three times the size.\nSELECT + Left/Right cycles it.\n$SPLIT_NOTE",
        ),
        row(
            BIG_SCREEN, "Big Screen", NdsScreen.entries, NdsScreen.TOP,
            "The screen shown big in picture in picture, large screen\nand single screen, and first in stacked and side by side.\nWith the on-screen pad over the game, only this screen takes\ntouches. R2 swaps it.",
        ),
        row(
            PIP_CORNER, "Inset Corner", PipCorner.entries, PipCorner.TOP_RIGHT,
            "Where the small screen sits in picture in picture\nand large screen. SELECT + Up/Down moves it to the next corner.",
        ),
        row(
            PIP_SIZE, "Inset Size", PipSize.entries, PipSize.MEDIUM,
            "The small screen's width in picture in picture and large screen:\n25%, 33% or 40% of the big screen's width.",
        ),
        row(
            PIP_OPACITY, "Inset Opacity", PipOpacity.entries, PipOpacity.FULL,
            "How opaque the small screen is: below 100%,\nthe big screen shows through it.",
        ),
    )

    /**
     * SELECT + Left/Right: the next layout ([delta] steps, wrapping) to set, as key to value. Landscape: the next [NdsLandscapeLayout]
     * under [LAYOUT_LANDSCAPE] (one key, pad or controller). Portrait: for the key in use ([LAYOUT_PORTRAIT_CONTROLLER] in
     * [controllerMode], else [LAYOUT_PORTRAIT]), stepping among [CONCRETE_PORTRAIT] from the layout showing in the [areaWidth] x
     * [areaHeight] game area (what Auto resolved to), so the value saved is concrete.
     */
    fun cycleLayout(
        values: Map<String, String>,
        landscape: Boolean,
        controllerMode: Boolean,
        delta: Int,
        areaWidth: Float,
        areaHeight: Float,
    ): Pair<String, String> {
        if (landscape) {
            val all = NdsLandscapeLayout.entries
            val next = all.stepFrom(NdsSettings.parse(values).landscape, delta)
            return LAYOUT_LANDSCAPE to next.value
        }
        val all = CONCRETE_PORTRAIT
        val showing = NdsSettings.parse(values).portraitLayout(controllerMode, areaWidth, areaHeight)
        val next = all.stepFrom(showing, delta)
        return (if (controllerMode) LAYOUT_PORTRAIT_CONTROLLER else LAYOUT_PORTRAIT) to next.value
    }

    /** Clockwise, the order SELECT + Down (+1) / Up (-1) walks. */
    private val CLOCKWISE = listOf(PipCorner.TOP_LEFT, PipCorner.TOP_RIGHT, PipCorner.BOTTOM_RIGHT, PipCorner.BOTTOM_LEFT)

    /** SELECT + Up/Down: the inset corner [delta] steps clockwise (wrapping) to set; saved even when PiP is not showing. */
    fun cycleCorner(values: Map<String, String>, delta: Int): Pair<String, String> {
        val next = CLOCKWISE.stepFrom(NdsSettings.parse(values).corner, delta)
        return PIP_CORNER to next.value
    }

    /** R2: the other screen as the big one. */
    fun swapBigScreen(values: Map<String, String>): Pair<String, String> = BIG_SCREEN to NdsSettings.parse(values).big.other.value

    /** The label a hotkey shows for its new value ("Bottom screen", "Stacked"). */
    fun valueLabel(key: String, value: String): String = FrontendOptions.option(key)?.labelOf(value) ?: value
}
