package com.nxredux.mobile.ui.nx

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The one set of text sizes outside the main menu's own layouts (Home, Grid, Carousel, Backdrop, the pad). Each role's
 * factor is defined here once; pages ask for a role, never multiply a size themselves.
 *
 * Most roles are a share of the **list label** ([listLabel]: 62% of the row pitch, so it follows the screen's row count
 * ): the headline above it, the secondary text one [STEP] below it, a caption two steps below. The page
 * title, the hint bar, the pad's labels and the in-game notices are fixed; the modal and in-game
 * message texts are fixed. No page text is a share of the screen height.
 */
object NxTextSizes {
    /** The list label as a share of its row pitch. */
    const val LABEL_OF_PITCH = 0.62f
    /**
     * One step smaller than the text it goes with: the secondary text. Of the list label it is a row's muted secondary
     * span, a detail line under a headline, an achievement's info lines and a rich row's title; of a rich title, its
     * second line; of the save-slot label, its date; of the adjust strip's label, its value.
     */
    const val STEP = 0.8f
    /** A line box as a share of its text size (rich rows, the achievement page). */
    const val LINE = 1.2f
    /** A rich row's two line boxes together (title and its secondary), as a share of the title. */
    const val RICH_BLOCK_OF_TITLE = (1f + STEP) * LINE
    /** The bold headline of a detail, progress, achievement or empty page. */
    const val HEADLINE = 1.15f
    /** The hint bar's words as a share of the glyph height. */
    const val HINT_OF_GLYPH = 0.6f
    /** A badge glyph's text as a share of its disc. */
    const val BADGE_OF_DISC = 0.62f

    // --- The list label and its shares (all follow the list's row pitch) ---

    /** The list label of rows [pitch] apart. */
    fun listLabel(pitch: Dp): TextUnit = (pitch.value * LABEL_OF_PITCH).sp
    /** One step smaller than [size] ([STEP]); of the list label, the secondary text. */
    fun secondary(size: TextUnit): TextUnit = size * STEP
    /** Two steps below the list label: a caption, a rich row's second line. */
    fun caption(label: TextUnit): TextUnit = secondary(secondary(label))
    /** The line height (box) for text of [size]. */
    fun line(size: TextUnit): TextUnit = size * LINE
    fun headline(label: TextUnit): TextUnit = label * HEADLINE

    /** The list label where the space's height is unbounded (a scrolling parent). */
    val LABEL_FALLBACK: TextUnit = 16.sp

    // --- Fixed: not the screen ---

    /** The page title and the main menu's tab words, either orientation. */
    val pageTitle: TextUnit = 14.sp
    /** The hint bar's words. */
    val hint: TextUnit = (HINT_GLYPH_SIZE.value * HINT_OF_GLYPH).sp
    /** A pad label drawn as a badge on a disc [disc] across. */
    fun badge(disc: Dp): TextUnit = (disc.value * BADGE_OF_DISC).sp
    /** A pad button's small label (SELECT, START, the shoulders), the same on every screen, in the menu and in game. */
    val padLabel: TextUnit = 12.sp
    /** The game info line (the List's band, the Game Switcher's). */
    val gameInfo: TextUnit = 14.sp
    /** A modal's body text: a text field, a popup list's rows, the Game Settings title. */
    val modalText: TextUnit = 16.sp
    /** A modal's small text: its caption, a field's label, its key hints, the Game Settings subtitle. */
    val modalSmall: TextUnit = 12.sp
    /** A modal's pill button. */
    val modalButton: TextUnit = 14.sp
    /** A dialog's message. */
    val dialogBody: TextUnit = 15.sp
    /** The in-game menu's message page and its line height. */
    val menuMessage: TextUnit = 16.sp
    val menuMessageLine: TextUnit = 22.sp
    /** The in-game options page's info area and its line height. */
    val menuInfo: TextUnit = 13.sp
    val menuInfoLine: TextUnit = 17.sp
    /** The same in portrait, smaller so another row fits above the on-screen pad. */
    val menuInfoPortrait: TextUnit = 11.sp
    val menuInfoLinePortrait: TextUnit = 14.sp

    /** The in-game notices (toasts, the progress pill). */
    val notice: TextUnit = 15.sp
}

/**
 * A modal's text sizes under Appearance → Text size ([nxTextScale]) and, as every list's text, Android's font size (held
 * by [NxTheme]): [NxTextSizes.modalText] and its siblings at Large.
 */
@androidx.compose.runtime.Composable
internal fun nxModalText(): TextUnit = NxTextSizes.modalText * nxTextScale()

@androidx.compose.runtime.Composable
internal fun nxModalSmall(): TextUnit = NxTextSizes.modalSmall * nxTextScale()

@androidx.compose.runtime.Composable
internal fun nxModalButton(): TextUnit = NxTextSizes.modalButton * nxTextScale()

@androidx.compose.runtime.Composable
internal fun nxDialogBody(): TextUnit = NxTextSizes.dialogBody * nxTextScale()
