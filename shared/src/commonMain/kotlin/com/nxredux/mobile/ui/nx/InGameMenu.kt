package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.ui.SETTINGS_SECTION_SIZE
import com.nxredux.mobile.ui.SETTINGS_SPLIT_LEFT
import com.nxredux.mobile.ui.SHORTCUT_PROMPT
import com.nxredux.mobile.ui.SplitPanes
import com.nxredux.mobile.ui.UNFOCUSED_ALPHA

/** One save-state slot as shown in the preview. [thumbPath] is a coil-loadable model (e.g. a file:// URI). */
data class SlotUi(
    val label: String,
    val subtitle: String,
    val thumbPath: String?,
    val occupied: Boolean,
    val aspect: Float = 4f / 3f,
)

/**
 * The in-game pause menu: fills the top game area with a semi-transparent wash and renders the top page
 * of [state]. The root page keeps the list + save-slot preview layout; other pages draw touch rows ([OptionsBody]:
 * name, description, the option's switch / segments / "Value ›") and page-specific hints; the dimmed parent pane keeps
 * its [NxOptionsRow] pills. Selection is hoisted: touch, the on-screen pad and hardware controllers all drive the host's
 * model; a row's control, info icon and the picker / info popup over the page ([MenuState.popup]) send [onOption]. A tap
 * on a message page, or on the hint bar of any non-root page, calls [onDismiss]. On an Adjust page only a bottom strip is drawn; the rest stays transparent
 * so the paused frame shows ([opaque]: the header on the background above it instead). Its CHANGE hint calls [onChange]
 * with +1 and its NEXT hint sends DOWN to [onPress] (see [adjustHintButton]). The Achievements page draws its rows as
 * the panel's ([rowAchievement] each row's achievement, its badge from [badgePath]); they open nothing.
 *
 * The [panel]'s header ([GamePanelHeaderBar]) spans the top of every page, in place of a page title (the page's name
 * without a panel). Beside the root list sits the rest of the game panel ([GamePanel]; its taps go to [onPanelEvent], its shortcut columns as drawn to
 * [onPanelColumns]), but for Save / Load, which show their slot card, and without [panelBeside] (netplay: an empty right side). While the controller is in the panel
 * ([MenuState.panelFocus]) the root list is dimmed. [opaque]: on the theme's background instead of the wash over the
 * paused frame (over the dual-screen panel, whose place the menu takes: the list seems to slide in beside it).
 *
 * On a wide window ([inGameMenuSplit] on [LocalWindowSize], the Settings rule, reported through [onSplit]) the menu draws
 * as two panes ([menuPanes]) under the one header and over the one hint bar: the root beside the panel (or the slot
 * card), a deeper page beside its dimmed parent (a tap there calls [onParentTap] with the parent's kind). Adjust strips
 * and message pages stay whole. Otherwise (portrait) the root keeps the panel in its right column, the Options page
 * beside the dimmed root too, and a deeper page takes the whole width, with its name in the title row over it. On the
 * root's Options row the right side previews the Options page ([MenuState.preview]), dimmed; a tap on one of its rows
 * calls [onPreviewTap].
 */
@Composable
fun InGameMenu(
    state: MenuState,
    slots: List<SlotUi>,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onChange: (Int) -> Unit = {},
    onPress: (NxButton) -> Unit = {},
    badgePath: (RaAchievement) -> String? = { null },
    rowAchievement: (MenuRow) -> RaAchievement? = { null },
    onInfoTruncated: (String, Boolean) -> Unit = { _, _ -> },
    onSplit: (Boolean) -> Unit = {},
    onParentTap: (PageKind, Int) -> Unit = { _, _ -> },
    onPreviewTap: (Int) -> Unit = {},
    panel: GamePanelState? = null,
    onPanelEvent: (GamePanelEvent) -> Unit = {},
    onPanelColumns: (Int) -> Unit = {},
    panelBeside: Boolean = true,
    opaque: Boolean = false,
    onOption: (OptionEvent) -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = LocalWindowSize.current.takeIf { it != DpSize.Zero } ?: DpSize(maxWidth, maxHeight)
        // The lists' row rule: the root rows' text is the Tools lists' label size; in portrait no bigger than the same
        // window turned to landscape would draw it ([menuRowHeight]).
        val rowHeight = menuRowHeight(maxHeight, if (LocalPortrait.current) minOf(window.width, window.height) else null)
        // The window, as Settings decides; the menu's own area without one.
        val split = inGameMenuSplit(window)
        // After every composition, so the model's input agrees with the panes drawn from the first frame.
        SideEffect { onSplit(split) }
        // A dialog page (Quit's, Reset's) draws over the page under it, which takes no input meanwhile (the scrim covers it).
        val (under, dialog) = menuDialog(state)
        if (under.page.kind is PageKind.Adjust) {
            // Over the dual-screen panel (opaque) the picture is on the other display: the header over the strip instead.
            AdjustContent(under.page, rowHeight, onDismiss, onChange, onPress, header = panel?.header?.takeIf { opaque })
        } else {
            val detail = AchievementsUi(badgePath, rowAchievement)
            val panes = menuPanes(under, split)
            val splitUi = panes?.let { SplitUi(it, wide = split, onParentTap, onPreviewTap) }
            val panelUi = panel?.let { PanelUi(it, onPanelEvent, onPanelColumns) }
            InGameMenuContent(under, slots, rowHeight, onSelect, onActivate, onDismiss, onChange, onPress, detail, onInfoTruncated, splitUi, panelUi, panelBeside, opaque, onOption)
        }
        if (dialog != null) {
            // No key hints: Quit's opens on a tap only (a pad's A / X quit on the row itself); Reset's, from a binding too,
            // reads as any choice dialog (UP/DOWN, A, B cancels).
            NxChoiceDialog(dialog.title, dialog.footer, dialog.rows.map { it.label }, dialog.selected, onChoose = onActivate, onCancel = onDismiss)
        }
        // An options row's picker or full description, over everything (the hint bar too).
        MenuPopupView(state.popup, onOption)
    }
}

/** [under]: the menu drawn as pages; [dialog]: the top page when it draws as a dialog over them ([NxChoiceDialog]). */
internal data class MenuDialog(val under: MenuState, val dialog: MenuPage?)

/** Whether [state]'s top page is a dialog (Quit Game, Reset game?): then the pages under it draw, the dialog over them. */
internal fun menuDialog(state: MenuState): MenuDialog =
    if ((state.page.kind == PageKind.QuitGame || state.page.kind == PageKind.ResetGame) && state.stack.size > 1) {
        MenuDialog(state.copy(stack = state.stack.dropLast(1)), state.page)
    } else {
        MenuDialog(state, null)
    }

/**
 * The two panes to draw ([menuPanes]): [wide] as Settings' two panes ([SplitBody]), else in the root's columns
 * ([RootBody]); the taps on a row of the dimmed parent and of the root's preview.
 */
private class SplitUi(val panes: MenuPanes, val wide: Boolean, val onParentTap: (PageKind, Int) -> Unit, val onPreviewTap: (Int) -> Unit)

/** The root's game panel ([GamePanel]): what it draws, its taps, and the shortcut columns it drew. */
private class PanelUi(val state: GamePanelState, val onEvent: (GamePanelEvent) -> Unit, val onColumns: (Int) -> Unit)

/** The panel (its header drawn across the menu's top instead) in [modifier]'s box, reporting the shortcut grid's columns at that width ([rememberGamePanelColumns]) for the model's UP/DOWN. */
@Composable
private fun MenuGamePanel(panel: PanelUi, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val columns = rememberGamePanelColumns(maxWidth, panel.state.shortcuts)
        SideEffect { panel.onColumns(columns) }
        // The header is the menu's, across its top; the body sits on the menu's surface with the list.
        GamePanel(panel.state, panel.onEvent, Modifier.fillMaxSize(), showHeader = false, opaque = false)
    }
}

@Composable
private fun InGameMenuContent(
    state: MenuState,
    slots: List<SlotUi>,
    rowHeight: Dp,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
    onPress: (NxButton) -> Unit,
    detail: AchievementsUi,
    onInfoTruncated: (String, Boolean) -> Unit,
    split: SplitUi?,
    panel: PanelUi?,
    panelBeside: Boolean,
    opaque: Boolean,
    onOption: (OptionEvent) -> Unit,
) {
    val page = state.page
    // Without the panel beside the root (netplay) the header stays; the root's right side is left empty.
    val beside = panel?.takeIf { panelBeside }
    // The save-slot card's text: the list label size.
    val slotTextSize = nxListLabel()   // Appearance → Text size
    val selectedRow = page.rows.getOrNull(page.selected)
    Column(
        Modifier
            .fillMaxSize()
            .background(if (opaque) LocalNxColors.current.background else LocalNxColors.current.scrimMenuWash)
            .pointerInput(Unit) { detectTapGestures { /* swallow */ } }
            // The wash covers the camera cutout; the cutout above and below pads the content, the side one is in the
            // page inset ([nxPageInset]).
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Vertical)),
    ) {
        // The title and the body's words on the page inset, as every screen's: the body laid out [nxListInset] in from
        // the highlights' edge ([PageInset.pillStart]), where its labels and title always started. The hint bar below
        // spans the width, its glyphs on the inset.
        val inset = nxPageInset()
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = inset.pillStart, end = inset.pillEnd)) {
        // The game header on every page, where the dual-screen panel draws it; without a panel the standard page title.
        if (panel != null) GamePanelHeaderBar(panel.state.header) else NxPageTitle(page.title)
        if (split?.wide == true) {
            SplitBody(state, split, beside, slots, rowHeight, slotTextSize, onSelect, onActivate, onChange, detail, onInfoTruncated, onOption, Modifier.weight(1f))
        } else if (split != null) {
            RootBody(state, split, beside, slots, rowHeight, slotTextSize, onSelect, onActivate, onChange, detail, onInfoTruncated, onOption, Modifier.weight(1f))
        } else when (page.kind) {
            PageKind.Message -> MessageBody(page, onDismiss, Modifier.weight(1f))
            PageKind.Achievements -> AchievementsBody(page, detail, onSelect, Modifier.weight(1f))
            // Keyed by page so each page gets its own scroll position; whole width, so its name shows in the title row.
            else -> key(page.kind) {
                OptionsBody(page, rowHeight, onSelect, onActivate, onOption, onInfoTruncated, Modifier.weight(1f))
            }
        }
        }
        // In landscape the menu covers the pad, so each hint is a tap target that sends its button (see
        // [menuHintButton]); off the root page a tap on the bar's empty space is Back.
        val dismiss by rememberUpdatedState(onDismiss)
        val hintModifier = if (page.kind != PageKind.Root) {
            Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } }
        } else {
            Modifier
        }
        NxHintBar(
            hintsFor(page, selectedRow, state.selectedShowsInfo, state.panelFocus, state.panelTab, panel = beside != null), hintModifier,
            onHint = { onPress(menuHintButton(it)) },
        )
    }
}

/**
 * The root's columns without Settings' two panes (portrait): the root list ([rootListShare]) beside the panel, the slot
 * card or the Options preview; on the Options page the same columns, the root dimmed (its taps to [SplitUi.onParentTap])
 * beside the focused Options page.
 */
@Composable
private fun RootBody(
    state: MenuState,
    split: SplitUi,
    panel: PanelUi?,
    slots: List<SlotUi>,
    rowHeight: Dp,
    slotTextSize: TextUnit,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onChange: (Int) -> Unit,
    detail: AchievementsUi,
    onInfoTruncated: (String, Boolean) -> Unit,
    onOption: (OptionEvent) -> Unit,
    modifier: Modifier,
) {
    val panes = split.panes
    val slotIndex = state.slotIndex
    val listShare = rootListShare(LocalPortrait.current)
    // The labels start [MenuPillPadding] into their pills, where the page title's first letter sits ([nxListInset]).
    // No top room: the panel's tab row sits right under the header, as in the dual-screen panel, the list's first row beside it.
    Row(modifier.fillMaxWidth().padding(start = nxListInset() - MenuPillPadding, end = nxListInset(), bottom = 8.dp)) {
        // Words as Settings' sections: the selected one in the accent while the list has the focus, the main ink while
        // the panel or the page beside it does.
        if (panes.focusLeft) {
            Box(Modifier.weight(listShare).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                RootList(panes.left, rowHeight, onSelect, onActivate, onChange, focused = state.panelFocus == null)
            }
        } else {
            val tap: (Int) -> Unit = { i -> split.onParentTap(panes.left.kind, i) }
            Box(Modifier.weight(listShare).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
                RootList(panes.left, rowHeight, tap, tap, {}, focused = false)
            }
        }
        // Always reserve the right column so the list keeps a fixed width and doesn't shift
        // when Save/Load (which shows the slot preview in place of the panel) is selected.
        val right = Modifier.weight(1f - listShare).fillMaxHeight().padding(start = 12.dp)
        when (val content = panes.right) {
            MenuPaneContent.Slots -> SlotPreview(
                slot = slots.getOrNull(slotIndex),
                slotIndex = slotIndex,
                slotCount = slots.size,
                textSize = slotTextSize,
                onTap = { onActivate(state.page.selected) },
                onChange = onChange,
                modifier = right,
            )
            MenuPaneContent.Panel -> if (panel != null) MenuGamePanel(panel, right) else Spacer(right)
            MenuPaneContent.Empty -> Spacer(right)
            is MenuPaneContent.Page -> PagePane(content.page, !panes.focusLeft, split, rowHeight, onSelect, onActivate, detail, onInfoTruncated, onOption, right)
        }
    }
}

/**
 * A page in the right pane: focused, the page itself (touch rows, or the Achievements rows); beside the focused root its
 * preview ([MenuState.preview]), dimmed with no row highlighted, a tap on a row opening it there ([SplitUi.onPreviewTap]).
 */
@Composable
private fun PagePane(
    page: MenuPage,
    focused: Boolean,
    split: SplitUi,
    rowHeight: Dp,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    detail: AchievementsUi,
    onInfoTruncated: (String, Boolean) -> Unit,
    onOption: (OptionEvent) -> Unit,
    modifier: Modifier,
) {
    when {
        // The Options page (its rows the setting pages) as Settings' section words ([RowsBody]); beside the root, its
        // preview with none selected, every word in inkDim.
        page.kind == PageKind.Options -> key(page.kind) {
            if (focused) RowsBody(page, rowHeight, onSelect, onActivate, {}, modifier, focused = true)
            else RowsBody(page.copy(selected = -1), rowHeight, split.onPreviewTap, split.onPreviewTap, {}, modifier)
        }
        !focused -> key(page.kind) {
            OptionsBody(page.copy(selected = -1), rowHeight, split.onPreviewTap, {}, {}, { _, _ -> }, modifier.alpha(UNFOCUSED_ALPHA))   // role: an unfocused pane, dimmed
        }
        page.kind == PageKind.Achievements -> AchievementsBody(page, detail, onSelect, modifier)
        else -> key(page.kind) { OptionsBody(page, rowHeight, onSelect, onActivate, onOption, onInfoTruncated, modifier) }
    }
}

/**
 * The menu's list row pitch for an area [height] high (the Tools lists' rule); with [landscapeHeight] (portrait: the
 * window's short side, the area turned to landscape) never above what that would give, so a portrait page's text is
 * no bigger than landscape's.
 */
internal fun menuRowHeight(height: Dp, landscapeHeight: Dp?): Dp {
    val own = height / nxRowCountFor(height)
    return landscapeHeight?.let { minOf(own, it / nxRowCountFor(it)) } ?: own
}

/**
 * The root list's share of the root's width beside the panel: narrower in [portrait], so the panel gets the room; still
 * wide enough for the longest root row (Continue, the Disc row with its `<  1/2  >`) at the default text size.
 */
internal fun rootListShare(portrait: Boolean): Float = if (portrait) 0.32f else 0.34f

/**
 * The room above the root list's first row, so its pill's centre sits level with the panel's tab words beside it
 * ([menuTabWordCentre]); none when the row is already taller.
 */
internal fun rootListTopPad(rowHeight: Dp): Dp = (menuTabWordCentre() - rowHeight / 2).coerceAtLeast(0.dp)

/**
 * The root page's list: its rows at [rowHeight], an Option row (Disc N/M) with its value, the change arrows while selected.
 * Words as Settings' sections, no pill ([menuWordInk]): the rest in inkDim, the selected one in the accent while
 * [focused], else the main ink (the controller in the panel, or a page beside it has the focus; a tap still brings it
 * back). Save, Load and the plain Quit take two taps ([inGameRowTakesTwoTaps]); while Save or Load is highlighted a
 * sideways swipe on the list steps the slot, as on the slot card ([slotSwipe]).
 */
@Composable
private fun RootList(page: MenuPage, pageRowHeight: Dp, onSelect: (Int) -> Unit, onActivate: (Int) -> Unit, onChange: (Int) -> Unit, focused: Boolean = true) {
    val onSlot = page.rows.getOrNull(page.selected) is MenuRow.Slot
    val rowHeight = menuWordPitch(nxOptionsRowHeight(pageRowHeight, LocalNxOptionText.current))   // Appearance → Text size, as the option pages
    CompositionLocalProvider(LocalNxTapOpens provides { i -> !inGameRowTakesTwoTaps(page.rows.getOrNull(i)) }) { NxList(
        rows = page.rows.mapIndexed { i, row ->
            val value = (row as? MenuRow.Option)?.valueLabel
            NxRow(row.label, secondary = value?.let { if (i == page.selected) "<  $it  >" else it })
        },
        selectedIndex = page.selected,
        onSelect = onSelect,
        onOpen = onActivate,
        // Level with the panel's tab row: its first pill centred on the tab words.
        modifier = Modifier.padding(top = rootListTopPad(rowHeight)).fillMaxWidth().height(rowHeight * page.rows.size.coerceAtLeast(1))
            .then(if (onSlot) Modifier.slotSwipe(onChange) else Modifier),
        rowHeightOverride = rowHeight,
        pillPaddingOverride = MenuPillPadding,
        textSizeOverride = menuWordSize(), restInk = LocalNxColors.current.inkDim, wordInk = menuWordInk(focused),
    ) }
}

/** The in-game menu's word lists' selected word: the accent while the list has the focus, else the main ink (Settings' sections). */
@Composable
private fun menuWordInk(focused: Boolean): Color = if (focused) LocalNxColors.current.accent else LocalNxColors.current.main

/** A word list's row pitch: the menu's [base] pitch, or [MENU_WORD_PITCH] × the word size where that is more, so words keep air between them. */
@Composable
private fun menuWordPitch(base: Dp, inner: Boolean = false): Dp = maxOf(base, with(LocalDensity.current) { (menuWordSize(inner) * MENU_WORD_PITCH).toDp() })

/** A word list row's least pitch as a share of its word size. */
private const val MENU_WORD_PITCH = 1.4f

/**
 * The in-game menu's words at Settings' section size ([SETTINGS_SECTION_SIZE], under Appearance → Text size only); an
 * [inner] list (the Options page's) a step smaller ([MENU_INNER_WORD_SIZE]).
 */
@Composable
internal fun menuWordSize(inner: Boolean = false): TextUnit = (if (inner) MENU_INNER_WORD_SIZE else SETTINGS_SECTION_SIZE).pinned() * nxTextScale()

/** An inner word list's size (the Options page's), a step under the root list's [SETTINGS_SECTION_SIZE]. */
private val MENU_INNER_WORD_SIZE = 22.sp

/**
 * The two panes between the title and the hint bar ([SplitPanes], the Settings frame): the focused one takes the
 * input callbacks; the dimmed one ([UNFOCUSED_ALPHA]) sends its taps to [SplitUi]. The root's right is the game panel
 * (full brightness: it is tapped itself) or the slot card.
 */
@Composable
private fun SplitBody(
    state: MenuState,
    split: SplitUi,
    panel: PanelUi?,
    slots: List<SlotUi>,
    rowHeight: Dp,
    slotTextSize: TextUnit,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onChange: (Int) -> Unit,
    detail: AchievementsUi,
    onInfoTruncated: (String, Boolean) -> Unit,
    onOption: (OptionEvent) -> Unit,
    modifier: Modifier,
) {
    val panes = split.panes
    Box(modifier.fillMaxWidth()) {
        SplitPanes(
            // No divider: the list and the panel (or the page) are one surface.
            leftShare = SETTINGS_SPLIT_LEFT,
            divider = false,
            left = {
                if (panes.focusLeft) {
                    PaneBody(panes.left, rowHeight, onSelect, onActivate, onChange, detail, focused = state.panelFocus == null)
                } else {
                    val tap: (Int) -> Unit = { i -> split.onParentTap(panes.left.kind, i) }
                    PaneBody(panes.left, rowHeight, tap, tap, {}, detail, focused = false)
                }
            },
            right = {
                when (val right = panes.right) {
                    MenuPaneContent.Empty -> Unit
                    MenuPaneContent.Panel -> panel?.let { MenuGamePanel(it, Modifier.fillMaxSize()) }
                    // Full brightness, unlike a page preview: the card is swiped and tapped itself.
                    MenuPaneContent.Slots -> SlotPreview(
                        slot = slots.getOrNull(state.slotIndex), slotIndex = state.slotIndex, slotCount = slots.size,
                        textSize = slotTextSize, onTap = { onActivate(state.page.selected) }, onChange = onChange,
                        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = nxListInset(), top = 8.dp, bottom = 8.dp),   // its end on the page inset
                    )
                    // The focused page: touch rows (the dimmed parent on the left keeps its pills); or the root's preview.
                    is MenuPaneContent.Page ->
                        PagePane(right.page, !panes.focusLeft, split, rowHeight, onSelect, onActivate, detail, onInfoTruncated, onOption, Modifier.fillMaxSize())
                }
            },
        )
    }
}

/**
 * The left pane's page: the root list, the Achievements page, or a page of word rows (the parent of the page on the
 * right). The word lists mark [focused] by their selected word's ink ([menuWordInk]); the Achievements page dims
 * ([UNFOCUSED_ALPHA]) without it.
 */
@Composable
private fun PaneBody(
    page: MenuPage,
    rowHeight: Dp,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onChange: (Int) -> Unit,
    detail: AchievementsUi,
    focused: Boolean,
) {
    when (page.kind) {
        // At the top, its first row beside the panel's tab row.
        PageKind.Root -> Box(
            Modifier.fillMaxSize().padding(start = nxListInset() - MenuPillPadding, end = 12.dp, bottom = 8.dp),
            contentAlignment = Alignment.TopStart,
        ) { RootList(page, rowHeight, onSelect, onActivate, onChange, focused) }
        PageKind.Achievements -> AchievementsBody(page, detail, onSelect, Modifier.fillMaxSize().alpha(if (focused) 1f else UNFOCUSED_ALPHA))   // role: an unfocused pane, dimmed
        else -> key(page.kind) { RowsBody(page, rowHeight, onSelect, onActivate, onChange, Modifier.fillMaxSize(), focused) }
    }
}

/** Horizontal padding inside a label pill on the root list and the adjust strip (the rows pages use [NxOptionsRow]). */
private val MenuPillPadding = 10.dp


/**
 * A page of words: the split menu's parent page ([PaneBody]), and the Options page itself ([PagePane]). Its rows as
 * Settings' section words (the labels alone at [menuWordSize]; the selected one's ink by [focused], [menuWordInk]), from
 * the top level with the root list. A tap goes to [onSelect] / [onActivate] (the parent tap).
 */
@Composable
private fun RowsBody(
    page: MenuPage,
    rowHeight: Dp,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onChange: (Int) -> Unit,
    modifier: Modifier,
    focused: Boolean = false,
) {
    val itemHeight = menuWordPitch(nxOptionsRowHeight(rowHeight, LocalNxOptionText.current), inner = true)   // Appearance → Text size
    BoxWithConstraints(
        modifier.fillMaxWidth().padding(start = nxListInset() - MenuPillPadding, end = 12.dp, top = rootListTopPad(itemHeight)),
        contentAlignment = Alignment.TopStart,
    ) {
        val shown = minOf(page.rows.size, maxOf(1, (maxHeight / itemHeight).toInt()))
        NxList(
            rows = page.rows.map { NxRow(it.label) },
            selectedIndex = page.selected, onSelect = onSelect, onOpen = onActivate,
            modifier = Modifier.fillMaxWidth().height(itemHeight * shown.coerceAtLeast(1)),
            rowHeightOverride = itemHeight, pillPaddingOverride = MenuPillPadding,
            textSizeOverride = menuWordSize(inner = true), restInk = LocalNxColors.current.inkDim, wordInk = menuWordInk(focused),
        )
    }
}

/**
 * A rows-page row as an options row: an option shows its value (`< value >` while selected); a submenu and an action a
 * `>` (NX Redux ma_menu.c), so they take the two-layer bar too.
 */
internal fun menuOptionRow(row: MenuRow): NxRow = when (row) {
    is MenuRow.Option -> NxRow(row.label, secondary = row.valueLabel, cycleable = true)
    is MenuRow.Submenu, is MenuRow.Action -> NxRow(row.label, secondary = ">")
    is MenuRow.Slot -> NxRow(row.label)
}

/**
 * Whether root [row] takes two taps, a tap to highlight and a tap on the highlighted one to act ([LocalNxTapOpens]):
 * Save and Load, whose slot is chosen first, and the plain Quit after netplay, which quits with nothing to confirm.
 * Quit itself opens its dialog on one tap (the dialog is the second step).
 */
internal fun inGameRowTakesTwoTaps(row: MenuRow?): Boolean =
    row is MenuRow.Slot || (row as? MenuRow.Action)?.action == MenuAction.QUIT_ONLY

/** The slot step of a sideways swipe [drag] px long: one slot past [threshold] (finger left: the next), however far. */
internal fun slotSwipeStep(drag: Float, threshold: Float): Int = when {
    drag <= -threshold -> 1
    drag >= threshold -> -1
    else -> 0
}

/** How far a swipe must go to step the slot. */
private val SlotSwipeDistance = 40.dp

/** A sideways swipe steps the save slot ([slotSwipeStep]) through [onChange], +1 the next slot, as RIGHT. */
@Composable
private fun Modifier.slotSwipe(onChange: (Int) -> Unit): Modifier {
    val change by rememberUpdatedState(onChange)
    return pointerInput(Unit) {
        var drag = 0f
        detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onDragEnd = { slotSwipeStep(drag, SlotSwipeDistance.toPx()).takeIf { it != 0 }?.let { change(it) } },
        ) { c, dx -> c.consume(); drag += dx }
    }
}

@Composable
private fun MessageBody(page: MenuPage, onDismiss: () -> Unit, modifier: Modifier) {
    val colors = LocalNxColors.current
    val dismiss by rememberUpdatedState(onDismiss)
    val scroll = rememberScrollState()
    val stepPx = with(LocalDensity.current) { MessageScrollStep.toPx() }
    // Controller UP/DOWN (the model counts them in scrollSeq): one step per press, clamped by the scroll state.
    LaunchedEffect(page.scrollSeq) { if (page.scrollSeq > 0) scroll.animateScrollBy(page.scrollDir * stepPx) }
    // Message pages have no rows, so a tap anywhere on the message dismisses it (touch equivalent of B).
    Box(
        modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { dismiss() } }.padding(horizontal = nxListInset()),   // on the page inset
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = page.footer,
            color = colors.listText,
            fontFamily = LocalNxFont.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = NxTextSizes.menuMessage,
            lineHeight = NxTextSizes.menuMessageLine,
            textAlign = TextAlign.Center,
            // No line cap: an option's full description can outgrow the page, so it scrolls (centred when it fits).
            modifier = Modifier.verticalScroll(scroll).padding(vertical = 8.dp),
        )
    }
}

/** What the Achievements page draws: the host's badge lookup, and the achievement each of its rows stands for. */
private class AchievementsUi(
    val badgePath: (RaAchievement) -> String?,
    val rowAchievement: (MenuRow) -> RaAchievement?,
)

/**
 * The Achievements page: its rows as the panel's ([AchievementRow]: badge, name with the points pill, description), the
 * row's label kept (its `[M]` mute marker); a tap selects it (X then mutes it), nothing opens. Empty (Locked only with
 * none left), the page's footer in the middle.
 */
@Composable
private fun AchievementsBody(page: MenuPage, detail: AchievementsUi, onSelect: (Int) -> Unit, modifier: Modifier) {
    if (page.rows.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(page.footer, color = LocalNxColors.current.hint, fontFamily = LocalNxFont.current, fontSize = NxTextSizes.menuInfoLine, textAlign = TextAlign.Center)
        }
        return
    }
    // The rows fade where more scrolls in (the lists' cue), the selection a row clear of the fades.
    val state = rememberFocusedListState(page.selected, margin = 1)
    LazyColumn(
        modifier.fillMaxWidth().nxScrollFade(state, height = 56.dp, strong = true), state = state,
        contentPadding = PaddingValues(horizontal = NxOptionsSidePad, vertical = 8.dp),
    ) {
        itemsIndexed(page.rows) { i, row ->
            val selected = i == page.selected
            val tap = Modifier.clickable { onSelect(i) }
            // A row with no achievement behind it (a host without the lookup) still shows and takes the selection.
            val a = detail.rowAchievement(row)
            if (a != null) AchievementRow(row.label, a, detail.badgePath(a), selected, tap) else PlainPanelRow(row.label, selected, tap)
        }
    }
}


/**
 * Adjust mode: one strip at the bottom of the menu area (label left on the accent, `<  value  >` right on the Secondary
 * accent's bar, the options rows' [nxOptionsSelection]) over the hint line. Everything above is transparent, so the
 * paused frame, redrawn after each change, is fully visible; a tap on it (or on the B hint) leaves adjust mode, and
 * `<` / `>` change the value.
 */
@Composable
private fun AdjustContent(
    page: MenuPage,
    rowHeight: Dp,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
    onPress: (NxButton) -> Unit,
    header: GamePanelHeader? = null,
) {
    val row = page.rows.firstOrNull() as? MenuRow.Option ?: return
    val selection = nxOptionsSelection(LocalNxColors.current)
    val font = LocalNxFont.current
    val fontSize = nxListLabel()
    val valueSize = NxTextSizes.secondary(fontSize)
    val pillHeight = rowHeight * 0.84f
    val dismiss by rememberUpdatedState(onDismiss)
    val background = LocalNxColors.current.background
    val inset = nxPageInset()
    Column(Modifier.fillMaxSize().then(if (header != null) Modifier.background(background).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Vertical)) else Modifier)) {
        header?.let { Box(Modifier.padding(start = inset.pillStart, end = inset.pillEnd)) { GamePanelHeaderBar(it) } }   // as the menu's, on the page inset
        Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTapGestures { dismiss() } })
        Column(
            Modifier.fillMaxWidth()
                .background(LocalNxColors.current.scrimAdjust)
                .pointerInput(Unit) { detectTapGestures { /* swallow */ } },
        ) {
            // The label on the page inset ([nxPageInset]), its pill [MenuPillPadding] outside it; the bar ends as far in.
            Box(
                Modifier.fillMaxWidth().height(rowHeight).padding(start = inset.start - MenuPillPadding, end = inset.end - MenuPillPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    Modifier.fillMaxWidth().height(pillHeight).background(selection.bar, RoundedCornerShape(50)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Box(
                            Modifier.height(pillHeight).background(selection.fill, RoundedCornerShape(50))
                                .padding(horizontal = MenuPillPadding),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = row.label,
                                color = selection.text,
                                fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = fontSize, lineHeight = fontSize,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    AdjustArrow("<", valueSize, selection.barText) { onChange(-1) }
                    Text(
                        text = row.valueLabel,
                        color = selection.barText,
                        fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = valueSize, lineHeight = valueSize,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    AdjustArrow(">", valueSize, selection.barText) { onChange(1) }
                }
            }
            NxHintBar(hintsFor(page, row), onHint = { hint ->
                when (val b = adjustHintButton(hint)) {
                    null -> Unit
                    NxButton.B -> dismiss()
                    NxButton.RIGHT -> onChange(1)
                    else -> onPress(b)
                }
            })
        }
    }
}

/**
 * The button a tapped adjust-strip hint stands for: CHANGE (◄►) steps the value forward like RIGHT,
 * NEXT (▲▼) moves to the next visual row like DOWN, DONE is B. Null for a hint that does nothing.
 */
internal fun adjustHintButton(hint: NxButton): NxButton? = when (hint) {
    NxButton.LEFT -> NxButton.RIGHT
    NxButton.UP -> NxButton.DOWN
    NxButton.B -> NxButton.B
    else -> null
}

@Composable
private fun AdjustArrow(text: String, size: TextUnit, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size,
        )
    }
}

/**
 * The hint bar's hints for [page] with [row] highlighted; while the controller is in the root's game panel ([panelFocus],
 * on [panelTab]) the panel's: L1/R1 tabs, A on an item (Open on the Achievements or Players tab row), B back to the list.
 * With a [panel] drawn beside the root, a root row that shows it (not Save / Load, not Options with its preview, not the
 * Disc row's ◄► Change) leads with ◄► Panel (RIGHT enters it).
 */
internal fun hintsFor(
    page: MenuPage, row: MenuRow?, infoTruncated: Boolean = false, panelFocus: PanelFocus? = null, panelTab: GamePanelTab? = null,
    panel: Boolean = false,
): List<Hint> {
    val hints = pageHints(page, row, infoTruncated, panelFocus, panelTab)
    val entersPanel = panel && panelFocus == null && page.kind == PageKind.Root && row is MenuRow.Action
    return if (entersPanel) listOf(Hint(NxButton.RIGHT, "Panel")) + hints else hints
}

private fun pageHints(page: MenuPage, row: MenuRow?, infoTruncated: Boolean, panelFocus: PanelFocus?, panelTab: GamePanelTab?): List<Hint> = when {
    panelFocus != null -> buildList {
        add(Hint(NxButton.L1, "Tab"))
        if (panelFocus is PanelFocus.Item) { if (panelTab != GamePanelTab.ACHIEVEMENTS) add(Hint(NxButton.A, "Select")) }   // its rows open nothing
        else if (panelTab == GamePanelTab.ACHIEVEMENTS || panelTab == GamePanelTab.PLAYERS) add(Hint(NxButton.A, "Open"))
        add(Hint(NxButton.B, "Back"))
        add(Hint(NxButton.R1, "Tab"))
    }
    page.kind is PageKind.Adjust -> listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.UP, "Next"), Hint(NxButton.B, "Done"))
    page.kind == PageKind.Message -> listOf(Hint(NxButton.B, "Back"))
    page.kind == PageKind.Achievements && row != null -> listOf(Hint(NxButton.Y, "Locked only"), Hint(NxButton.X, "Mute"), Hint(NxButton.B, "Back"))
    // Filtered to nothing ("Everything unlocked."): Y still brings the full list back.
    page.kind == PageKind.Achievements -> listOf(Hint(NxButton.Y, "Locked only"), Hint(NxButton.B, "Back"))
    page.rows.isEmpty() -> listOf(Hint(NxButton.B, "Back"))   // e.g. an empty category: A does nothing
    page.kind == PageKind.Root && row is MenuRow.Slot ->
        listOf(Hint(NxButton.LEFT, "Slot"), Hint(NxButton.A, row.label), Hint(NxButton.B, "Resume"))
    // The root has no info area (Disc N/M): A opens the info there.
    page.kind == PageKind.Shortcuts && row is MenuRow.Option -> shortcutHints(row)
    page.kind == PageKind.Root && row is MenuRow.Option -> buildList {
        add(Hint(NxButton.LEFT, "Change"))
        if (row.fullInfo.isNotBlank()) add(Hint(NxButton.A, "Info"))
        add(Hint(NxButton.B, "Resume"))
    }
    row is MenuRow.Option -> optionHints(row, infoTruncated)
    // Options: A opens its page (previewed on the right).
    page.kind == PageKind.Root && row is MenuRow.Submenu -> listOf(Hint(NxButton.A, "Open"), Hint(NxButton.B, "Resume"))
    row is MenuRow.Submenu -> buildList {
        add(Hint(NxButton.A, "Open"))
        if (infoTruncated) add(Hint(NxButton.X, "Info"))
        add(Hint(NxButton.B, "Back"))
    }
    // A pad picks how to quit on the Quit row itself (a tap opens the Quit dialog).
    page.kind == PageKind.Root && (row as? MenuRow.Action)?.action == MenuAction.QUIT ->
        listOf(Hint(NxButton.A, "Save & Quit"), Hint(NxButton.X, "Quit"), Hint(NxButton.B, "Resume"))
    page.kind == PageKind.Root && (row as? MenuRow.Action)?.action == MenuAction.QUIT_ONLY ->
        listOf(Hint(NxButton.A, "Quit"), Hint(NxButton.B, "Resume"))
    page.kind == PageKind.Root -> listOf(Hint(NxButton.A, "Select"), Hint(NxButton.B, "Resume"))
    else -> buildList {
        add(Hint(NxButton.A, "Select"))
        if (infoTruncated) add(Hint(NxButton.X, "Info"))
        add(Hint(NxButton.B, "Back"))
    }
}

/**
 * An Options → Shortcuts row: waiting for a press, only B Cancel (every other key is the press); a bind row's A Bind, a
 * touch button's Change and Choose; then X None (here) and Y Default (back to Controls → Shortcuts).
 */
private fun shortcutHints(row: MenuRow.Option): List<Hint> = buildList {
    if (row.valueLabel == SHORTCUT_PROMPT) { add(Hint(NxButton.B, "Cancel")); return@buildList }
    if (row.choices.isEmpty()) add(Hint(NxButton.A, "Bind")) else { add(Hint(NxButton.LEFT, "Change")); add(Hint(NxButton.A, "Choose")) }
    add(Hint(NxButton.X, "None"))
    add(Hint(NxButton.Y, "Default"))
    add(Hint(NxButton.B, "Back"))
}

/**
 * An option row: Change; A's Adjust on a visual row, Toggle on a switch, Choose on a picker (none on a segmented or a
 * value-only row, where A steps or does nothing); X Info when its description is cut off on its row or says more
 * ([MenuState.selectedShowsInfo], passed as [infoTruncated]).
 */
private fun optionHints(row: MenuRow.Option, infoTruncated: Boolean): List<Hint> = buildList {
    add(Hint(NxButton.LEFT, "Change"))
    when {
        row.visual -> add(Hint(NxButton.A, "Adjust"))
        row.control == NxSettingControl.SWITCH -> add(Hint(NxButton.A, "Toggle"))
        row.control == NxSettingControl.PICKER -> add(Hint(NxButton.A, "Choose"))
    }
    if (infoTruncated && row.fullInfo.isNotBlank()) add(Hint(NxButton.X, "Info"))
    add(Hint(NxButton.B, "Back"))
}

/**
 * The button a tapped menu hint sends: CHANGE (◄►) steps the value forward like RIGHT (and SLOT/NEXT step forward
 * too); every other hint sends its own button.
 */
internal fun menuHintButton(hint: NxButton): NxButton = if (hint == NxButton.LEFT) NxButton.RIGHT else hint

/** The slot card's widest (a tablet, the unfolded Fold): the screenshot keeps its aspect within it. */
private val SlotCardMax = 480.dp
private val SlotCardPadSide = 8.dp
private val SlotCardPadTop = 3.dp
private val SlotCardPadBottom = 5.dp
private val SlotDotsStrip = 18.dp   // the dot pager's row: 6 above the 9 dp selected dot, 3 below

/** How far one controller UP/DOWN scrolls a message page. */
private val MessageScrollStep = 66.dp

/**
 * Screenshot preview of the chosen slot + a dot pager + the slot's label/timestamp. A sideways swipe steps the slot
 * ([slotSwipe], [onChange]); a tap saves or loads it ([onTap]), as a second tap on the highlighted Save / Load row.
 * Top-left in its box (level with the list's first row), at most [SlotCardMax] wide; the screenshot shrinks to fit the
 * height, so the date and the dots always show.
 */
@Composable
private fun SlotPreview(
    slot: SlotUi?,
    slotIndex: Int,
    slotCount: Int,
    textSize: TextUnit,
    onTap: () -> Unit,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tap by rememberUpdatedState(onTap)
    val thumb = slot?.thumbPath
    val aspect = slot?.aspect ?: (4f / 3f)
    val small = NxTextSizes.secondary(textSize)
    val occupied = slot?.occupied == true
    // A white "polaroid" card that hugs the screenshot (photo box uses the shot's own aspect, so no
    // black padding). The top strip shows the save's date/time; the bottom strip holds the dot pager.
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopStart) {
        // The card's own strips, fixed; the screenshot takes the rest of the width (capped), then of the height.
        val topStrip = if (occupied) with(LocalDensity.current) { small.toDp() } + 4.dp else 5.dp
        val chromeHeight = SlotCardPadTop + topStrip + SlotDotsStrip + SlotCardPadBottom
        var photoW = minOf(maxWidth, SlotCardMax) - SlotCardPadSide * 2
        var photoH = photoW / aspect
        if (maxHeight.value.isFinite() && photoH + chromeHeight > maxHeight) {
            photoH = (maxHeight - chromeHeight).coerceAtLeast(0.dp)
            photoW = photoH * aspect
        }
        Column(
            Modifier.width(photoW + SlotCardPadSide * 2).slotSwipe(onChange).pointerInput(Unit) { detectTapGestures { tap() } }
                .clip(RoundedCornerShape(12.dp)).background(LocalNxColors.current.main)
                .padding(start = SlotCardPadSide, end = SlotCardPadSide, top = SlotCardPadTop, bottom = SlotCardPadBottom),
        ) {
            if (slot != null && occupied) {
                Text(
                    text = slot.subtitle,
                    color = LocalNxColors.current.slotDate,
                    fontFamily = LocalNxFont.current,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = small,
                    lineHeight = small,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            } else {
                // No datetime for an empty slot: keep the top border thin, like the sides.
                Spacer(Modifier.height(5.dp))
            }
            Box(
                Modifier.size(photoW, photoH).clip(RoundedCornerShape(3.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = "Empty Slot",
                        color = LocalNxColors.current.main,
                        fontFamily = LocalNxFont.current,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = textSize,
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().height(SlotDotsStrip),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(slotCount) { i ->
                        if (i > 0) Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(if (i == slotIndex) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(if (i == slotIndex) LocalNxColors.current.slotDotLit else LocalNxColors.current.slotDot),
                        )
                    }
                }
            }
        }
    }
}
