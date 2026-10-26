package com.nxredux.mobile.options

import com.nxredux.mobile.layout.DeviceTraits
import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.settings.stepFrom

/** A DS setting's stored [value] and what the menu and hotkey labels show. */
interface NdsChoice {
    val value: String
    val label: String
}

/** How the two DS screens share the game area, in either orientation. The stored [value] is the same in both. */
enum class NdsLayoutChoice(override val value: String, override val label: String) : NdsChoice {
    LARGE("large", "Large screen"), STACKED("stacked", "Stacked"), SINGLE("single", "Single screen"),
    PIP("pip", "Picture in picture"), SIDE_BY_SIDE("side-by-side", "Side by side"),
}

/**
 * The layout a screen starts with before the player picks one: large screen on the near-square shapes (Fold-shaped,
 * tablets) and in portrait, side by side on a regular phone in landscape. The controller row uses the portrait default.
 */
fun ndsDefault(screen: ScreenClass, landscape: Boolean): NdsLayoutChoice =
    if (landscape && !screen.isFoldShaped && !screen.isTablet) NdsLayoutChoice.SIDE_BY_SIDE else NdsLayoutChoice.LARGE

/** Side by side is not offered in landscape on a Fold-shaped screen: the two screens would be tiny there. */
internal fun offers(choice: NdsLayoutChoice, landscape: Boolean, screen: ScreenClass?): Boolean =
    !(choice == NdsLayoutChoice.SIDE_BY_SIDE && landscape && screen?.isFoldShaped == true)

enum class NdsScreen(override val value: String, override val label: String) : NdsChoice {
    TOP("top", "Top screen"), BOTTOM("bottom", "Bottom screen");

    val other: NdsScreen get() = if (this == TOP) BOTTOM else TOP
}

enum class PipCorner(override val value: String, override val label: String) : NdsChoice {
    TOP_LEFT("top-left", "Top left"), TOP_RIGHT("top-right", "Top right"),
    BOTTOM_LEFT("bottom-left", "Bottom left"), BOTTOM_RIGHT("bottom-right", "Bottom right"),
}

/** [fraction]: the small screen's width as a share of the big screen's width (picture in picture). */
enum class PipSize(override val value: String, override val label: String, val fraction: Float) : NdsChoice {
    SMALL("small", "Small", 0.25f), MEDIUM("medium", "Medium", 0.33f), LARGE("large", "Large", 0.40f),
}

/** [alpha]: how opaque the inset is drawn over the big screen. */
enum class PipOpacity(override val value: String, override val label: String) : NdsChoice {
    FULL("100", "100%"), THREE_QUARTERS("75", "75%"), HALF("50", "50%");

    val alpha: Float get() = value.toInt() / 100f
}

/**
 * The DS screen settings in effect; unknown stored values read as no pick. A null [portrait], [landscape] or [controller] is
 * "not chosen yet": the screen's default applies ([layoutShown]). [controller] is the portrait layout used instead of
 * [portrait] while a non-clip-on controller has dropped the pad band.
 */
data class NdsSettings(
    val portrait: NdsLayoutChoice? = null,
    val landscape: NdsLayoutChoice? = null,
    val big: NdsScreen = NdsScreen.TOP,
    val corner: PipCorner = PipCorner.TOP_RIGHT,
    val size: PipSize = PipSize.MEDIUM,
    val opacity: PipOpacity = PipOpacity.FULL,
    val controller: NdsLayoutChoice? = null,
) {
    /**
     * The layout drawn on [screen]: the pick for the orientation ([controller] in [controllerMode] in portrait), else
     * [ndsDefault]. A saved Side by side in landscape on a Fold-shaped screen shows as Large screen (the stored pick is kept).
     */
    fun layoutShown(inLandscape: Boolean, controllerMode: Boolean, screen: ScreenClass): NdsLayoutChoice {
        val pick = if (inLandscape) landscape else if (controllerMode) controller else portrait
        val layout = pick ?: ndsDefault(screen, inLandscape)
        return if (offers(layout, inLandscape, screen)) layout else NdsLayoutChoice.LARGE
    }

    companion object {
        fun parse(values: Map<String, String>): NdsSettings {
            val d = NdsSettings()
            return NdsSettings(
                portrait = layoutPick(values, NdsOptions.LAYOUT_PORTRAIT),
                landscape = layoutPick(values, NdsOptions.LAYOUT_LANDSCAPE),
                big = pick(values[NdsOptions.BIG_SCREEN], NdsScreen.entries) ?: d.big,
                corner = pick(values[NdsOptions.PIP_CORNER], PipCorner.entries) ?: d.corner,
                size = pick(values[NdsOptions.PIP_SIZE], PipSize.entries) ?: d.size,
                opacity = pick(values[NdsOptions.PIP_OPACITY], PipOpacity.entries) ?: d.opacity,
                controller = layoutPick(values, NdsOptions.LAYOUT_PORTRAIT_CONTROLLER),
            )
        }

        /** A layout row's pick: its current key, else the old key's value unless that was the old default (no pick). */
        private fun layoutPick(values: Map<String, String>, key: String): NdsLayoutChoice? {
            return pick(NdsOptions.layoutValue(values, key), NdsLayoutChoice.entries)
        }

        private fun <E : NdsChoice> pick(value: String?, all: List<E>): E? = all.firstOrNull { it.value == value }
    }
}

/**
 * The DS screen settings: frontend `nx_` keys, per game with a console default, stripped before the
 * core sees them, shown on the Options → Console Settings page for NDS only ([ConsoleSettings.tableFor]).
 */
object NdsOptions {
    // The layout keys are versioned because the defaults changed (per screen, no Auto): an old value that was just the
    // old default reads as no pick, so the new default applies ([OLD_LAYOUT_KEYS]).
    const val LAYOUT_PORTRAIT = "nx_nds_layout_portrait_v3"
    const val LAYOUT_PORTRAIT_CONTROLLER = "nx_nds_layout_portrait_controller_v2"
    const val LAYOUT_LANDSCAPE = "nx_nds_layout_landscape_v2"
    const val BIG_SCREEN = "nx_nds_big_screen"
    const val PIP_CORNER = "nx_nds_pip_corner"
    const val PIP_SIZE = "nx_nds_pip_size"
    const val PIP_OPACITY = "nx_nds_pip_opacity"
    const val CORE_RESOLUTION = "melonds_opengl_resolution"

    /** Each layout key to its previous key and that key's old default. */
    val OLD_LAYOUT_KEYS: Map<String, Pair<String, String>> = mapOf(
        LAYOUT_PORTRAIT to ("nx_nds_layout_portrait_v2" to "auto"),
        LAYOUT_PORTRAIT_CONTROLLER to ("nx_nds_layout_portrait_controller" to "stacked"),
        LAYOUT_LANDSCAPE to ("nx_nds_layout_landscape" to "stacked"),
    )

    /**
     * The stored value of layout row [key] in [values]: its own, else the old key's unless that was the old default; null
     * (no pick) when neither is set or the value is not a layout (a migrated old "auto").
     */
    fun layoutValue(values: Map<String, String>, key: String): String? {
        val (oldKey, oldDefault) = OLD_LAYOUT_KEYS.getValue(key)
        return FrontendOptions.migrateKey(values, key, oldKey, oldDefault)?.takeIf { v -> NdsLayoutChoice.entries.any { it.value == v } }
    }

    /** The keys that can be absent ("no pick"): left out of [FrontendOptions.defaults] and of a config read when unset. */
    val NO_PICK_KEYS: Set<String> = OLD_LAYOUT_KEYS.keys

    private const val SPLIT_NOTE = "Screen Scaling and the screen offsets do not apply to the DS screens."

    /** The DS / 3DS layout rows' note: half-folded, the fold places the screens and SELECT + Left/Right does nothing. */
    const val FOLD_NOTE = "This is the layout when not half-folded:\nhalf-folded, the fold sets it."

    /** [FOLD_NOTE] on its own line for a device that [canHalfFold]; nothing elsewhere, where it never applies. */
    fun foldNote(canHalfFold: Boolean): String = if (canHalfFold) "\n$FOLD_NOTE" else ""

    fun appliesTo(tag: String): Boolean = tag.trim().uppercase() == "NDS"

    private fun <E : NdsChoice> row(key: String, label: String, all: List<E>, default: E, info: String, brief: String = "") =
        FrontendOption(key, label, all.map { it.value }, default.value, info, visual = true, valueLabels = all.map { it.label }, brief = brief)

    /**
     * The three layout rows for [screen] (null: unknown, so every value and the regular phone's defaults). The row default
     * is the screen's [ndsDefault], shown while nothing is stored. Landscape omits Side by side on a Fold-shaped screen.
     */
    fun layoutRows(screen: ScreenClass?, canHalfFold: Boolean = DeviceTraits.canHalfFold): List<FrontendOption> {
        val fold = foldNote(canHalfFold)
        val known = screen ?: ScreenClass.REGULAR_PHONE
        val portraitDefault = ndsDefault(known, false)
        val landscapeAll = NdsLayoutChoice.entries.filter { offers(it, true, screen) }
        return listOf(
            row(
                LAYOUT_PORTRAIT, "Layout (portrait)", NdsLayoutChoice.entries, portraitDefault,
                "How the two screens share a portrait screen.\n" +
                    "Large screen: the big one, the other centred below it at\nleast half its size; under 2x, it fills the leftover height up to 2x.\n" +
                    "Stacked: one above the other.\nSingle screen: the big one only.\n" +
                    "Picture in picture: one big, the other small in a corner.\n" +
                    "Side by side: half the width each.\nThe screens use whole-number sizes, except the picture-in-picture\ninset and, a Large screen's touch screen filling leftover height in portrait.\n" +
                    "SELECT + Left/Right cycles it, or Layout (portrait, controller)\nwhile a controller hides the pad.\n$SPLIT_NOTE$fold",
            ),
            row(
                LAYOUT_PORTRAIT_CONTROLLER, "Layout (portrait, controller)", NdsLayoutChoice.entries, portraitDefault,
                "The portrait layout while a controller that is not a clip-on\n(Pocket Taco, FlipPad) is connected and the pad is hidden.\n" +
                    "SELECT + Left/Right cycles it then.\n$SPLIT_NOTE$fold",
                brief = "Used while a controller (not a clip-on) hides the pad",
            ),
            row(
                LAYOUT_LANDSCAPE, "Layout (landscape)", landscapeAll, ndsDefault(known, true),
                "How the two screens share a landscape screen.\nLarge screen: the big one, the other centred below it at\nleast half its size.\n" +
                    "Stacked: one above the other, the same size.\nSingle screen: the big one, centred.\n" +
                    "Picture in picture: the big one centred, the other small in a corner.\n" +
                    (if (NdsLayoutChoice.SIDE_BY_SIDE in landscapeAll) "Side by side: half the width each.\n" else "") +
                    "The screens use whole-number sizes, except the picture-in-picture\ninset and, a Large screen's touch screen filling leftover height in portrait.\nSELECT + Left/Right cycles it.\n$SPLIT_NOTE$fold",
            ),
        )
    }

    private val otherRows: List<FrontendOption> = listOf(
        row(
            BIG_SCREEN, "Big Screen", NdsScreen.entries, NdsScreen.TOP,
            "The screen shown big in picture in picture, large screen\nand single screen, and first in stacked and side by side.\nR2 swaps it.",
        ),
        row(
            PIP_CORNER, "Inset Corner", PipCorner.entries, PipCorner.TOP_RIGHT,
            "Where the small screen sits in picture in picture\n(large screen keeps it centred below). SELECT + Up/Down moves it to the next corner.",
        ),
        row(
            PIP_SIZE, "Inset Size", PipSize.entries, PipSize.MEDIUM,
            "The small screen's width in picture in picture:\n25%, 33% or 40% of the big screen's width.",
        ),
        row(
            PIP_OPACITY, "Inset Opacity", PipOpacity.entries, PipOpacity.FULL,
            "How opaque the small screen is: below 100%,\nthe big screen shows through it.",
        ),
    )

    /** The page's rows for [screen] (null: unknown). */
    fun tableFor(screen: ScreenClass?, canHalfFold: Boolean = DeviceTraits.canHalfFold): List<FrontendOption> =
        layoutRows(screen, canHalfFold) + otherRows

    /**
     * Every row with every layout value ([tableFor] an unknown screen). Built when read, so the info text follows
     * [com.nxredux.mobile.layout.DeviceTraits.canHalfFold] whenever it is set.
     */
    val table: List<FrontendOption> get() = tableFor(null)

    /**
     * SELECT + Left/Right: the next layout ([delta] steps, wrapping) to set, as key to value: [LAYOUT_LANDSCAPE], else
     * [LAYOUT_PORTRAIT_CONTROLLER] in [controllerMode], else [LAYOUT_PORTRAIT]. It steps from the layout showing on [screen]
     * ([NdsSettings.layoutShown]), so an unset row moves from its default and the value saved is always a pick. Side by
     * side is skipped in landscape on a Fold-shaped screen.
     */
    fun cycleLayout(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, delta: Int, screen: ScreenClass): Pair<String, String> {
        val showing = layoutShown(values, landscape, controllerMode, screen)
        val next = NdsLayoutChoice.entries.filter { offers(it, landscape, screen) }.stepFrom(showing, delta)
        val key = if (landscape) LAYOUT_LANDSCAPE else if (controllerMode) LAYOUT_PORTRAIT_CONTROLLER else LAYOUT_PORTRAIT
        return key to next.value
    }

    /** The layout drawn for the row in use on [screen] ([NdsSettings.layoutShown] of the parsed [values]). */
    fun layoutShown(values: Map<String, String>, landscape: Boolean, controllerMode: Boolean, screen: ScreenClass): NdsLayoutChoice =
        NdsSettings.parse(values).layoutShown(landscape, controllerMode, screen)

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
