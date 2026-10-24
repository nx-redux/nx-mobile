package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.ui.raBadgeModel
import org.jetbrains.compose.resources.painterResource

/**
 * The game panel, in the in-game menu's look: the header (art or console logo, title, play time, achievement count,
 * clock and battery), the tab words with the underline ([MenuTabRow]), then the current tab: Shortcuts as hollow pill
 * buttons in a grid (4 columns, 2 when narrow; disabled ones dimmed and inert), Achievements as badge rows, Cheats as
 * rows with a switch, Players as the Players page's rows. The controller's [GamePanelState.focus] sits on the accent pill (the tab row's capsule on
 * Tabs). The Game pill (DS/3DS panel-up) sits bottom-right. The dual-screen panel and the in-game menu share it; the
 * menu draws the header itself across its top ([GamePanelHeaderBar]) and the panel with [showHeader] off, on its own
 * surface ([opaque] off: no black box of the panel's own).
 */
@Composable
fun GamePanel(
    state: GamePanelState, onEvent: (GamePanelEvent) -> Unit, modifier: Modifier = Modifier, showHeader: Boolean = true, opaque: Boolean = true,
) {
    val colors = LocalNxColors.current
    val tab = GamePanelModel.resolveTab(state.tab, state.tabs)
    val tabs = state.tabs.ifEmpty { listOf(GamePanelTab.SHORTCUTS) }
    val focused = (state.focus as? PanelFocus.Item)?.index
    // The lists' bottom room: clear of the Game pill when it is drawn.
    val bottom = if (state.showGamePill) GP_LIST_BOTTOM else GP_LIST_END
    Box(modifier.fillMaxSize().then(if (opaque) Modifier.background(colors.background) else Modifier)) {
        Column(Modifier.fillMaxSize()) {
            if (showHeader) GamePanelHeaderBar(state.header)
            MenuTabRow(
                tabs.map { it.label }, tabs.indexOf(tab).coerceAtLeast(0), onTab = { onEvent(GamePanelEvent.SelectTab(tabs[it])) },
                focused = state.focus == PanelFocus.Tabs,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    GamePanelTab.SHORTCUTS -> ShortcutsTab(state.shortcuts, focused, bottom, onEvent)
                    GamePanelTab.ACHIEVEMENTS -> AchievementsTab(state.achievements, state.badges, focused, bottom)
                    GamePanelTab.CHEATS -> CheatsTab(state.cheats, state.noCheatsText, focused, bottom, onEvent)
                    GamePanelTab.PLAYERS -> PlayersTab(state.players, state.playerPads, focused, bottom, onEvent)
                }
            }
        }
        if (state.showGamePill) PanelPill("Game", Modifier.align(Alignment.BottomEnd).padding(GP_PILL_MARGIN)) { onEvent(GamePanelEvent.ShowGame) }
    }
}

/**
 * The panel's header: art (or the console logo) on the left, title and the time / achievement lines beside it, clock and
 * battery top-right. Full width at the top of the dual-screen panel and of the in-game menu, at the same place in both.
 */
@Composable
fun GamePanelHeaderBar(h: GamePanelHeader) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Row(
        // The room under it is the header's own, so the menu and the dual-screen panel put what follows at one place.
        Modifier.fillMaxWidth().padding(start = GP_GUTTER, end = GP_GUTTER, top = 12.dp, bottom = GP_HEADER_BOTTOM),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(GP_ART).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            val logo = consoleLogo(h.consoleTag)
            when {
                h.artPath != null -> AsyncImage(model = h.artPath, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                logo != null -> Image(painterResource(logo), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), colorFilter = ColorFilter.tint(colors.listText))
            }
        }
        // Centred on the art (the clock stays top-right); the achievement line only for a game with a set.
        Column(Modifier.weight(1f).align(Alignment.CenterVertically), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(h.title, color = colors.listText, fontFamily = font, fontSize = GP_TITLE_SIZE, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(GamePanelModel.timeLine(h), color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 2, overflow = TextOverflow.Ellipsis)
            GamePanelModel.raLine(h)?.let {
                Text(it, color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(h.clockText, color = colors.listText, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1)
            h.batteryPercent?.let { Text("$it%", color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1) }
        }
    }
}

/** The shortcut pills, 4 to a row (2 under [GP_NARROW]); a short last row keeps the cell width. */
@Composable
private fun ShortcutsTab(shortcuts: List<ShortcutState>, focused: Int?, bottom: Dp, onEvent: (GamePanelEvent) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = gamePanelColumns(maxWidth)
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().nxScrollFade(scroll, height = 56.dp, strong = true).verticalScroll(scroll)
                .padding(start = GP_GUTTER, end = GP_GUTTER, top = 10.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            shortcuts.chunked(columns).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { c, s ->
                        val i = r * columns + c
                        ShortcutPill(s, selected = focused == i, Modifier.weight(1f)) { GamePanelModel.eventFor(s)?.let(onEvent) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The shortcut grid's columns in a panel [width] wide: 4, or 2 under [GP_NARROW] (the in-game menu steps by them). */
internal fun gamePanelColumns(width: Dp): Int = if (width < GP_NARROW) 2 else 4

/**
 * A hollow pill ([nxButtonRing]): the label, its value under it; at 0.35 alpha and not clickable while disabled. A toggle
 * ([ShortcutState.on]) that is on is a filled quick-settings tile instead (the list text colour, dark words). The
 * controller's pill ([selected]) gets an accent ring a gap outside it, so it reads on an outlined and a filled pill
 * alike (an accent fill would look like an on toggle with a light accent).
 */
@Composable
private fun ShortcutPill(s: ShortcutState, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val filled = s.on == true
    // The controller's pill scrolls into view in the grid's scroll.
    val into = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) into.bringIntoView() }
    val shape = RoundedCornerShape(50)
    Column(
        modifier.bringIntoViewRequester(into).heightIn(min = GP_PILL_HEIGHT)
            // Drawn outside the pill, in the grid's spacing (so the focus moving never resizes one), and before the dimming
            // (an alpha layer would clip it to the pill and fade it on a disabled one).
            .then(if (selected) Modifier.drawBehind { focusRing(colors.accent) } else Modifier)
            .alpha(if (s.enabled) 1f else GP_DISABLED_ALPHA)
            .clip(shape)
            .then(if (filled) Modifier.background(colors.listText) else Modifier.nxButtonRing(colors))
            .then(if (s.enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            s.label, color = if (filled) colors.background else colors.listText, fontFamily = font, fontSize = GP_LABEL_SIZE,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        s.value?.let {
            Text(it, color = if (filled) colors.background else colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1)
        }
    }
}

/** The controller's ring round a shortcut pill: [GP_FOCUS_RING] wide, [GP_FOCUS_GAP] outside its rounded edge. */
private fun DrawScope.focusRing(color: Color) {
    val out = (GP_FOCUS_GAP + GP_FOCUS_RING / 2).toPx()
    val h = size.height + out * 2
    drawRoundRect(
        color, topLeft = Offset(-out, -out), size = Size(size.width + out * 2, h), cornerRadius = CornerRadius(h / 2),
        style = Stroke(GP_FOCUS_RING.toPx()),
    )
}

/** The achievements as [AchievementRow]s; they open nothing (the description is on the row). */
@Composable
private fun AchievementsTab(list: List<RaAchievement>, badges: Map<String, String>, focused: Int?, bottom: Dp) {
    // The rows fade where more scrolls in (the lists' cue), the focus an item clear of the fades.
    val state = rememberFocusedListState(focused, margin = 1)
    LazyColumn(
        Modifier.fillMaxSize().nxScrollFade(state, height = 56.dp, strong = true), state = state,
        contentPadding = PaddingValues(start = GP_LIST_GUTTER, end = GP_LIST_GUTTER, top = 6.dp, bottom = bottom),
    ) {
        itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
            AchievementRow(a.title, a, raBadgeModel(badges, a), focused == i, Modifier)
        }
    }
}

/**
 * One achievement row, the panel's Achievements tab's and the in-game Achievements page's: the [badge] (its locked
 * variant while locked, [raBadgeModel]: the badge alone says unlocked or not), then [title] with the points in a small
 * pill ([GamePanelModel.pointsBadge]) over the description (2 lines at most); on the accent pill while [selected].
 */
@Composable
internal fun AchievementRow(title: String, a: RaAchievement, badge: String?, selected: Boolean, modifier: Modifier) {
    val colors = LocalNxColors.current
    PanelListRow(selected, modifier) {
        Box(Modifier.size(GP_BADGE).clip(RoundedCornerShape(3.dp)).background(colors.main.copy(alpha = 0.13f))) {
            badge?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RowText(title, selected, Modifier.weight(1f, fill = false))
                GamePanelModel.pointsBadge(a)?.let { NxCountPill(it, selected, GP_BADGE_TEXT_SIZE) }
            }
            if (a.description.isNotEmpty()) Text(
                a.description, color = if (selected) colors.onAccentMuted else colors.hint, fontFamily = LocalNxFont.current, fontSize = GP_SMALL_SIZE,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The game's cheats, each with its switch ([NxSwitch]); a tap anywhere on a row flips it ([GamePanelModel.cheatEvent]),
 * live, as A on the controller's row does. None: [empty] (how to add some), centred.
 */
@Composable
private fun CheatsTab(cheats: List<CheatRow>, empty: String, focused: Int?, bottom: Dp, onEvent: (GamePanelEvent) -> Unit) {
    val colors = LocalNxColors.current
    if (cheats.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(start = GP_GUTTER, end = GP_GUTTER, bottom = bottom), contentAlignment = Alignment.Center) {
            Text(empty, color = colors.hint, fontFamily = LocalNxFont.current, fontSize = GP_SMALL_SIZE, textAlign = TextAlign.Center)
        }
        return
    }
    // Item indices are the focus indices and the cheats' own (no header item).
    val state = rememberFocusedListState(focused, margin = 1)   // fades as the Achievements tab's
    LazyColumn(
        Modifier.fillMaxSize().nxScrollFade(state, height = 56.dp, strong = true), state = state,
        contentPadding = PaddingValues(start = GP_LIST_GUTTER, end = GP_LIST_GUTTER, top = 6.dp, bottom = bottom),
    ) {
        itemsIndexed(cheats) { i, c ->
            val flip = { GamePanelModel.cheatEvent(i, cheats)?.let(onEvent); Unit }
            val selected = focused == i
            PanelListRow(selected, Modifier.clickable(onClick = flip)) {
                Column(Modifier.weight(1f)) {
                    RowText(c.name, selected)
                    // The file it came from, when several were merged (two can name a cheat alike).
                    if (c.source.isNotEmpty()) Text(
                        c.source, color = if (selected) colors.onAccentMuted else colors.hint, fontFamily = LocalNxFont.current, fontSize = GP_SMALL_SIZE,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                NxSwitch(c.on, onClick = flip, lit = selected)
            }
        }
    }
}

/**
 * As the Players page: "Player N" with its pad; a tap steps it to the next of Empty and every connected pad
 * ([GamePanelModel.nextPlayer]): Empty benches the pad that was there, a benched pad chosen plays again.
 */
@Composable
private fun PlayersTab(players: List<PlayerSlotRow>, pads: List<PlayerPadRow>, focused: Int?, bottom: Dp, onEvent: (GamePanelEvent) -> Unit) {
    // Item indices are the focus indices: the slots (no header item).
    val state = rememberFocusedListState(focused, margin = 1)   // fades as the Achievements tab's
    LazyColumn(
        Modifier.fillMaxSize().nxScrollFade(state, height = 56.dp, strong = true), state = state,
        contentPadding = PaddingValues(start = GP_LIST_GUTTER, end = GP_LIST_GUTTER, top = 6.dp, bottom = bottom),
    ) {
        itemsIndexed(players) { i, s ->
            PlayerRow("Player ${i + 1}", GamePanelModel.playerValue(s), focused == i, Modifier.clickable { onEvent(GamePanelModel.nextPlayer(i, players, pads)) })
        }
    }
}

/**
 * A list's state that follows the controller's [focused] item (its LazyColumn index): the least scroll that shows it
 * whole, or a jump to it when it is off screen. [margin]: items kept in view past it on both sides (clamped to the
 * list), so it never rests under a scroll fade while more is that way: the item below first, else the one above.
 */
@Composable
internal fun rememberFocusedListState(focused: Int?, margin: Int = 0): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(focused) {
        val i = focused ?: return@LaunchedEffect
        // Not laid out yet (the tab's first frame): wait for it, so the focus is measured against real rows.
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) withFrameNanos { }
        val info = state.layoutInfo
        val top = info.viewportStartOffset + info.beforeContentPadding
        val bottom = info.viewportEndOffset - info.afterContentPadding
        val below = minOf(info.totalItemsCount - 1, i + margin)
        val above = maxOf(0, i - margin)
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val b = info.visibleItemsInfo.firstOrNull { it.index == below }
        val a = info.visibleItemsInfo.firstOrNull { it.index == above }
        when {
            // Past the bottom: the item below lands on the last line.
            b == null && below > last -> state.animateScrollToItem(below, -(bottom - top - (info.visibleItemsInfo.lastOrNull()?.size ?: 0)))
            b != null && b.offset + b.size > bottom -> state.animateScrollBy((b.offset + b.size - bottom).toFloat())
            a == null -> state.animateScrollToItem(above)
            a.offset < top -> state.animateScrollBy((a.offset - top).toFloat())
        }
    }
    return state
}

/** A row with only its [label], in the panel rows' frame: the Achievements page's fallback for a row without an achievement. */
@Composable
internal fun PlainPanelRow(label: String, selected: Boolean, modifier: Modifier) {
    PanelListRow(selected, modifier) { RowText(label, selected, Modifier.weight(1f)) }
}

@Composable
private fun PlayerRow(label: String, value: String, selected: Boolean, modifier: Modifier) {
    val colors = LocalNxColors.current
    PanelListRow(selected, modifier) {
        RowText(label, selected, Modifier.weight(1f))
        Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = LocalNxFont.current, fontSize = GP_SMALL_SIZE, color = if (selected) colors.onAccentMuted else colors.hint)
    }
}

/** A list row's frame: at least [GP_ROW_HEIGHT] high, on the accent pill while [selected]. */
@Composable
private fun PanelListRow(selected: Boolean, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    val colors = LocalNxColors.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = GP_ROW_HEIGHT).clip(RoundedCornerShape(GP_ROW_HEIGHT / 2))
            .then(if (selected) Modifier.background(colors.accent) else Modifier).then(modifier).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun RowText(text: String, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    Text(
        text, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = LocalNxFont.current, fontSize = GP_LABEL_SIZE,
        color = if (selected) colors.onAccent else colors.listText,
    )
}

/** A small hollow pill ([nxButtonRing]) on black: the panel's Game pill, and the Panel pill over the DS/3DS screen. */
@Composable
fun PanelPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    Box(
        modifier.clip(RoundedCornerShape(50)).background(colors.background).nxButtonRing(colors).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = GP_SMALL_SIZE, maxLines = 1)
    }
}

/** Onboarding's Bottom screen step on the second display: one big "This is the bottom screen" button in the middle. */
@Composable
fun BottomScreenButton(onClick: () -> Unit) {
    val colors = LocalNxColors.current
    Box(Modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).nxButtonRing(colors).clickable(onClick = onClick)
                .padding(horizontal = 32.dp, vertical = 18.dp),
        ) {
            Text("This is the bottom screen", color = colors.listText, fontFamily = LocalNxFont.current, fontSize = 22.sp, maxLines = 1)
        }
    }
}

private val GP_GUTTER = 16.dp
private val GP_LIST_GUTTER = 8.dp
private val GP_LIST_BOTTOM = 52.dp   // the last row clears the Game pill
private val GP_LIST_END = 8.dp       // without the pill
private val GP_PILL_MARGIN = 10.dp
private val GP_ART = 64.dp
private val GP_HEADER_BOTTOM = 16.dp   // between the header and the tabs (and the menu's list beside them)
private val GP_BADGE = 32.dp
private val GP_ROW_HEIGHT = 44.dp
private val GP_PILL_HEIGHT = 52.dp
private val GP_FOCUS_RING = 2.dp     // the controller's ring round a shortcut pill
private val GP_FOCUS_GAP = 3.dp      // between that ring and the pill
private val GP_NARROW = 360.dp       // narrower: the shortcut grid goes to 2 columns
private const val GP_DISABLED_ALPHA = 0.35f
private val GP_TITLE_SIZE = 17.sp
private val GP_LABEL_SIZE = 15.sp
private val GP_SMALL_SIZE = 13.sp
private val GP_BADGE_TEXT_SIZE = 11.sp   // the points pill
