package com.nxredux.mobile.ui

import androidx.compose.foundation.layout.Box
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.LocalPadBelow
import com.nxredux.mobile.ui.nx.NxListFooter
import com.nxredux.mobile.ui.nx.NxOptionsSidePad
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nxredux.mobile.ui.nx.nxHighlight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxListPitch
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.nxListInset
import com.nxredux.mobile.ui.nx.nxTextScale
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import com.nxredux.mobile.ui.nx.NxHintBar
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nxredux.mobile.ui.nx.NxSettingRow
import org.jetbrains.compose.resources.DrawableResource
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxHintBarStart
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxSettingEvent
import com.nxredux.mobile.ui.nx.NxSettingsList
import com.nxredux.mobile.ui.nx.NxSignInPrompt
import com.nxredux.mobile.ui.nx.NxTextPrompt

/**
 * An onboarding step ([Screen.Onboarding]) as Settings' rows block: the step count its crumb, its headline and lines, its
 * touch rows ([Screen.Onboarding.touchRows], their controls' taps to [onSetting]), from the bottom. A or a tap on the
 * selected row chooses it ([onRow]); B goes back, except on the steps that have nothing before them. With
 * [Screen.Onboarding.prompt] the RetroAchievements sign-in prompt is over it ([onDraftUser], [onDraftPassword]).
 */
@Composable
fun OnboardingScreen(
    screen: Screen.Onboarding, selected: Int, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
    onDraftUser: (String) -> Unit, onDraftPassword: (String) -> Unit, modifier: Modifier = Modifier,
    onSetting: (NxSettingEvent) -> Unit = {},
    onDraftKey: (String) -> Unit = {},
) {
    // The last page: START finishes too, as Press Start does.
    val hints = listOfNotNull(
        if (screen.canGoBack) Hint(NxButton.B, "Back") else null, Hint(NxButton.A, "Choose"),
        if (screen.step == OnboardingStep.DONE) Hint(NxButton.START, "Start") else null,
    )
    if (screen.step == OnboardingStep.WELCOME) {
        OnboardingWelcomePage(screen, onRow, modifier)
        return
    }
    screen.touchRows?.let { rows ->
        val picture = when {
            screen.step == OnboardingStep.TITLE ->
                chromePicture(title = screen.rows.getOrNull(0)?.secondary != "Hide", hints = screen.rows.getOrNull(1)?.secondary != "Hide")
            else -> screen.shot?.let(::layoutShotPicture)
        }
        SetupBlockPage(
            screen.title, screen.details.firstOrNull().orEmpty(), screen.details.drop(1), rows, setupButtons(rows, button = true),
            selected, hints, onSelect, onRow, onHint, modifier, onSetting, picture,
        )
    }
    if (screen.artSignIn) NxSignInPrompt(
        screen.draftUser, screen.draftPassword, onDraftUser, onDraftPassword,
        onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) }, caption = "Sign in to ScreenScraper",
    )
    screen.artKey?.let { key ->
        NxTextPrompt("${key.label} API key", screen.draftKey, onDraftKey, onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) })
    }
    if (screen.prompt) NxSignInPrompt(
        screen.draftUser, screen.draftPassword, onDraftUser, onDraftPassword,
        onConfirm = { onHint(NxButton.A) }, onCancel = { onHint(NxButton.B) },
    )
}

/**
 * A setup page (onboarding's steps, first setup's folder steps): the step group (the [counter], [headline], [lines] and
 * touch [rows], [picture] (a layout's) under the rows) and the last [buttons] rows (Continue, Skip, Pick folder; two for
 * a real choice: Add folder / Skip) as words pinned to the bottom ([SetupButtons]), in the same place on every step. The
 * rows keep the touch rows' selected plate; the buttons select by brightness alone. Either way
 * up the group's top sits on a fixed line ([SETUP_LINE] of the body) and grows down, so the counter, title and rows are
 * level from step to step; a group that would come within [SETUP_BUTTON_GAP] of the button moves up instead, and past
 * the top its rows scroll under the rows block's fades.
 * [lineBreaks]: each line on its own (a list).
 */
@Composable
internal fun SetupBlockPage(
    counter: String, headline: String, lines: List<String>, rows: List<NxSettingRow>, buttons: Int,
    selected: Int, hints: List<Hint>, onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit,
    modifier: Modifier = Modifier, onSetting: (NxSettingEvent) -> Unit = {}, picture: DrawableResource? = null, lineBreaks: Boolean = false,
) {
    val shown = buttons.coerceIn(0, rows.size)
    val list = rows.dropLast(shown)
    val density = LocalDensity.current
    // The words' line box and the room a 48 dp tap target leaves round it, above and below.
    val word = NxTextSizes.listLabel(NxListPitch) * nxTextScale()
    val extra = ((SETUP_BUTTON_TAP - with(density) { (word * SETUP_WORD_LINE).toDp() }) / 2).coerceAtLeast(0.dp)
    // The group to the button's pill (its word's line box and 4 dp each side), as seen: what the list (~6 dp under its last
    // row) or the lines (~8 dp) leave and the tap target's room above the pill come off SETUP_BUTTON_GAP; past nothing,
    // the group overlaps the target's empty top.
    val gap = SETUP_BUTTON_GAP - (if (list.isEmpty() && picture == null) 8.dp else 6.dp) - extra * 2 + 4.dp
    val listSelected = selected.takeIf { it < list.size } ?: -1
    // The menu pad under the page (setup's pad step) leaves a short body: the buttons go under the rows, scrolling with
    // them (the list keeps a selected one in view), so the head and the rows keep their room.
    val scrollActions = LocalPadBelow.current && shown > 0 && list.isNotEmpty()
    val actionsFooter = NxListFooter(height = { SETUP_SCROLLED_BUTTON_GAP + SETUP_BUTTON_TAP }) {
        // The word on the rows' text edge: the list's side pad back out ([nxListInset] is it plus the rows' own inset).
        Box(Modifier.padding(top = SETUP_SCROLLED_BUTTON_GAP).offset(x = -NxOptionsSidePad)) {
            SetupButtons(rows.takeLast(shown).map { it.label }, list.size, selected, word, onSelect, onRow)
        }
    }
    val plateStart = settingsPlateStart()
    val gutter = settingsPlateEnd()   // the right side's plate edge
    // The rows block's list (its fades, its row pitch) and the hints on the rows' text edge, as Settings' ([settingsSectionStart]).
    CompositionLocalProvider(LocalSettingsBlock provides SettingsBlockMode(crumb = counter), LocalNxHintBarStart provides settingsSectionStart()) {
        Column(modifier.fillMaxSize()) {
            // Sideways a picture takes a column of its own at the right, fitted to its height; upright it scrolls under the rows.
            val side = picture != null && !LocalPortrait.current
            Row(Modifier.weight(1f).fillMaxWidth()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    // The line, from the body's top; the group's room starts [SETTINGS_ROWS_TOP] down it.
                    val line = maxHeight * SETUP_LINE - SETTINGS_ROWS_TOP
                    // At most the Settings rows block's width ([SETTINGS_FRAME_MAX]), from the start gutter: the lines wrap in it and a
                    // row's control stays near its label; the button on the rows' left edge under it.
                    Column(
                        Modifier.fillMaxHeight().padding(start = plateStart, end = gutter, top = SETTINGS_ROWS_TOP, bottom = SETTINGS_BLOCK_BOTTOM)
                            .widthIn(max = SETTINGS_FRAME_MAX).fillMaxWidth(),
                    ) {
                        SetupGroupRoom(line, if (shown > 0) (-gap).coerceAtLeast(0.dp) else 0.dp, Modifier.weight(1f).fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                SetupHeader(counter, headline)
                                // The gaps as seen (glyph to glyph, or plate): the line boxes add ~16 dp under the title (the
                                // lines drawn 4 dp up into it) and ~4 dp under the lines, so their spacers are that much less.
                                if (lines.isNotEmpty()) Box(Modifier.offset(y = -4.dp)) { SettingsDetailLines(lines, separator = if (lineBreaks) "\n" else " ") }
                                if (list.isNotEmpty() || picture != null) {
                                    Spacer(Modifier.height(if (lines.isNotEmpty()) SETUP_ROWS_GAP - 16.dp else SETUP_ROWS_GAP - 12.dp))
                                    Box(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                                        NxSettingsList(
                                            list, if (scrollActions) selected.coerceAtMost(list.size) else listSelected, onSelect, onRow, onSetting,
                                            footer = if (scrollActions) actionsFooter else picture?.takeIf { !side }?.let { layoutPictureFooter(it, LAYOUT_PICTURE_AR) },
                                        )
                                    }
                                }
                            }
                        }
                        if (shown > 0 && !scrollActions) {
                            Spacer(Modifier.height(gap.coerceAtLeast(0.dp)))
                            // The word's line box on the block's bottom (SETTINGS_BLOCK_BOTTOM up, as Settings' section words),
                            // its tap target's room below it hanging into that inset.
                            // The words on the rows' text edge ([nxListInset] in): their pill boxes start at the block's edge, an inset before them.
                            Box(Modifier.offset(y = extra)) {
                                SetupButtons(rows.takeLast(shown).map { it.label }, list.size, selected, word, onSelect, onRow)
                            }
                        }
                    }
                }
                if (side && picture != null) BoxWithConstraints(
                    Modifier.weight(1f).fillMaxHeight().padding(top = SETTINGS_ROWS_TOP, bottom = SETTINGS_BLOCK_BOTTOM, end = gutter),
                    contentAlignment = Alignment.Center,
                ) { FramedPictureFit(picture, LAYOUT_PICTURE_AR, maxWidth, maxHeight) }
            }
            NxHintBar(hints, onHint = onHint)
        }
    }
}

/**
 * A setup page's buttons, as every action: the selected one ([first] + its place is [selected]) on the pill
 * ([nxHighlight], its on-pill text), the others the word alone in inkDim, with a pad or touch alike (the page opens on
 * its first action, lit). Each word stays on its text edge, the pill reaching [nxListInset] past it either side, so the words
 * keep their places and spacing whichever is lit; a tap target [SETUP_BUTTON_TAP] tall. A tap selects and opens it.
 * [rest]: the unlit words' ink, inkDim on a setup page; a modal's are the list text ([NxModal], [NxChoiceDialog]).
 */
@Composable
internal fun SetupButtons(
    labels: List<String>, first: Int, selected: Int, size: TextUnit, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    rest: Color = LocalNxColors.current.inkDim,
) {
    val colors = LocalNxColors.current
    val inset = nxListInset()
    val pill = nxHighlight()
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, text ->
            val index = first + i
            val lit = index == selected
            Box(
                Modifier.heightIn(min = SETUP_BUTTON_TAP).clickable { onSelect(index); onOpen(index) },
                contentAlignment = Alignment.CenterStart,
            ) {
                val ink = if (lit) colors.onAccent else rest
                Box(Modifier.then(if (lit) Modifier.background(pill, RoundedCornerShape(50)) else Modifier).padding(horizontal = inset, vertical = 4.dp)) {
                    Text(
                        text, color = ink,
                        fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size * SETUP_WORD_LINE, maxLines = 1,
                    )
                }
            }
            if (i < labels.lastIndex) Spacer(Modifier.width(SETUP_BUTTON_SPACE - inset * 2))
        }
    }
}

/** Above buttons scrolled under the rows: with the tap target's own room round the pill and the list's under its last row, ~24 dp as seen. */
private val SETUP_SCROLLED_BUTTON_GAP = 4.dp
/** A setup button's tap target's height. */
private val SETUP_BUTTON_TAP = 48.dp
/** Between two setup buttons' words. */
private val SETUP_BUTTON_SPACE = 32.dp
/** A setup button word's line box as a share of its size. */
private const val SETUP_WORD_LINE = 1.25f

/**
 * The step group's room above the button: the group (its one child) placed with its top on [line] (from the room's top),
 * or as low as it fits when it is taller than the room below the line, [overlap] past the room's bottom allowed (the
 * button's tap target's empty top). The child gets the whole room as its most, so a taller group scrolls its rows.
 */
@Composable
private fun SetupGroupRoom(line: Dp, overlap: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, c ->
        val over = overlap.roundToPx()
        val group = measurables.first().measure(c.copy(minHeight = 0, maxHeight = c.maxHeight + over))
        val lowest = (c.maxHeight + over - group.height).coerceAtLeast(0)
        val y = minOf(line.roundToPx().coerceAtLeast(0), lowest)
        layout(c.maxWidth, c.maxHeight) { group.place(0, y) }
    }
}

/** Where a setup page's step group starts, either way up: this share of the body down. */
private const val SETUP_LINE = 0.5f
/** The least room between the step group and the button under it, as seen. */
private val SETUP_BUTTON_GAP = 24.dp
/** A setup page's lines (or title) down to its first row. */
private val SETUP_ROWS_GAP = 24.dp

/**
 * How many of [rows]' last are a setup page's buttons: none without [button]; the two last when they are plain actions
 * and the page is just them, or they follow rows with a value (a real choice: Add folder / Skip, Yes / Not now; the
 * artwork step's Fetch now / Skip under its accounts); else the last alone (Continue, Skip, Pick folder).
 */
internal fun setupButtons(rows: List<NxSettingRow>, button: Boolean): Int {
    if (!button || rows.isEmpty()) return 0
    val plain = rows.takeLastWhile { it.choices.isEmpty() && it.value == null && it.enabled }.size   // an action: no value, not a dimmed line
    return if (plain == 2 && (rows.size == 2 || rows[rows.size - 3].value != null)) 2 else 1
}

