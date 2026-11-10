package com.nxredux.mobile.ui.nx

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.fillMaxHeight
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
 * buttons in a grid (4 columns, 2 or 1 where a label would not fit, [gamePanelColumns]; disabled ones dimmed and inert), Achievements as badge rows, Cheats as
 * rows with a switch, Players as the Players page's rows. The controller's [GamePanelState.focus] sits on the accent pill (the tab row's capsule on
 * Tabs). The Game pill (DS/3DS panel-up) sits bottom-right. The dual-screen panel and the in-game menu share it; the
 * menu draws the header itself across its top ([GamePanelHeaderBar]) and its own tab row across the menu ([showTabs] off:
 * the current tab's body alone), on its own surface ([opaque] off: no black box of the panel's own). [menuInset]: the
 * dual-screen panel the menu takes the place of, padded as the menu's page.
 */
@Composable
fun GamePanel(
    state: GamePanelState, onEvent: (GamePanelEvent) -> Unit, modifier: Modifier = Modifier, showHeader: Boolean = true, opaque: Boolean = true,
    showTabs: Boolean = true, menuInset: Boolean = false,
) {
    val colors = LocalNxColors.current
    val tab = GamePanelModel.resolveTab(state.tab, state.tabs)
    val tabs = state.tabs.ifEmpty { listOf(GamePanelTab.SHORTCUTS) }
    val focused = (state.focus as? PanelFocus.Item)?.index
    // The lists' bottom room: clear of the Game pill when it is drawn.
    val bottom = if (state.showGamePill) GP_LIST_BOTTOM else GP_LIST_END
    // [menuInset]: on the in-game menu's page inset, so the header and what follows sit where the menu puts them when it
    // takes the panel's place.
    val inset = nxPageInset()
    val page = if (menuInset) Modifier.padding(start = inset.pillStart, end = inset.pillEnd) else Modifier
    Box(modifier.fillMaxSize().then(if (opaque) Modifier.background(colors.background) else Modifier)) {
        Column(Modifier.fillMaxSize().then(page)) {
            if (showHeader) GamePanelHeaderBar(state.header)
            if (showTabs) MenuTabRow(
                tabs.map { it.label }, tabs.indexOf(tab).coerceAtLeast(0), onTab = { onEvent(GamePanelEvent.SelectTab(tabs[it])) },
                // The row pads by the page inset itself, which the column already has: its words where the menu's start.
                modifier = if (menuInset) Modifier.offset(x = nxListInset() - inset.start) else Modifier,
                focused = state.focus == PanelFocus.Tabs,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    GamePanelTab.SHORTCUTS -> ShortcutsTab(state.shortcuts, state.slots, state.slot, focused, bottom, onEvent)
                    GamePanelTab.SETTINGS -> SettingsTab(state.quick, bottom, onEvent)
                    GamePanelTab.ACHIEVEMENTS -> AchievementsTab(state.achievements, state.badges, focused, bottom)
                    GamePanelTab.CHEATS -> CheatsTab(state.cheats, state.noCheatsText, focused, bottom, onEvent)
                    GamePanelTab.PLAYERS -> PlayersTab(state.players, state.playerPads, focused, bottom, onEvent)
                }
            }
        }
        if (state.showGamePill) GamePillCorner { onEvent(GamePanelEvent.ShowGame) }
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

/**
 * The shortcut tiles, 4 to a row, or 2 or 1 where a label's longest word would not fit ([rememberGamePanelColumns]); a
 * short last row keeps the cell width. Under them the save-state [slots]' strip ([SlotStrip]) where the game has any.
 */
@Composable
private fun ShortcutsTab(
    shortcuts: List<ShortcutState>, slots: List<SlotUi>, slot: Int?, focused: Int?, bottom: Dp, onEvent: (GamePanelEvent) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = rememberGamePanelColumns(maxWidth, shortcuts)
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().nxScrollFade(scroll, height = 56.dp, strong = true).verticalScroll(scroll)
                .padding(top = 10.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            shortcuts.chunked(columns).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = GP_GUTTER), horizontalArrangement = Arrangement.spacedBy(GP_CELL_GAP)) {
                    row.forEachIndexed { c, s ->
                        ShortcutTile(s, selected = focused == r * columns + c, Modifier.weight(1f), onEvent)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (slots.isNotEmpty()) SlotStrip(slots, slot) { onEvent(GamePanelEvent.SelectSlot(it)) }
        }
    }
}

/**
 * The save-state slots, a strip that scrolls sideways to the panel's edges: each slot's screenshot (or "Empty"), its
 * name and when it was saved. A tap picks the slot Save and Load use ([onPick], 1-based); the picked one ([slot]) wears
 * the accent.
 */
@Composable
private fun SlotStrip(slots: List<SlotUi>, slot: Int?, onPick: (Int) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("Save states", Modifier.padding(horizontal = GP_GUTTER), color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1)
        // Opens on the picked slot, one card before it in view.
        val list = rememberLazyListState(initialFirstVisibleItemIndex = ((slot ?: 1) - 2).coerceIn(0, (slots.size - 1).coerceAtLeast(0)))
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 8.dp), state = list,
            contentPadding = PaddingValues(horizontal = GP_GUTTER), horizontalArrangement = Arrangement.spacedBy(GP_CELL_GAP),
        ) {
            itemsIndexed(slots) { i, s ->
                val picked = slot == i + 1
                val shape = RoundedCornerShape(GP_SLOT_RADIUS)
                Column(Modifier.width(GP_SLOT_W).clip(shape).clickable { onPick(i + 1) }) {
                    Box(
                        Modifier.fillMaxWidth().height(GP_SLOT_H).clip(shape)
                            .background(if (s.thumbPath != null) Color.Black else Color.Transparent)
                            .border(if (picked) GP_SLOT_PICK else GP_TILE_RING, if (picked) colors.accent else colors.hint.copy(alpha = 0.35f), shape),   // role: the picked slot's accent ring, else a faint outline
                        contentAlignment = Alignment.Center,
                    ) {
                        val thumb = s.thumbPath
                        if (thumb != null) {
                            val context = coil3.compose.LocalPlatformContext.current
                            AsyncImage(
                                model = remember(thumb, context) { coil3.request.ImageRequest.Builder(context).data(thumb).memoryCacheKey(thumb).build() },
                                contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(GP_SLOT_PICK),
                            )
                        } else Text(if (s.occupied) s.label else "Empty", color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1)
                    }
                    Text(
                        s.label, Modifier.padding(top = 6.dp, start = 2.dp), color = if (picked) colors.accent else colors.listText,
                        fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1,
                    )
                    Text(
                        if (s.occupied) s.subtitle else " ", Modifier.padding(start = 2.dp, bottom = 2.dp), color = colors.hint,
                        fontFamily = font, fontSize = GP_SLOT_DATE, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The shortcut grid's columns in a panel [width] wide (the second-screen panel's): the most of 4, 2 and 1 whose
 * cell holds a tile as wide as the widest label word or value ([widest]), so none is cut; 2 under [GP_NARROW] at most.
 */
internal fun gamePanelColumns(width: Dp, widest: Dp): Int {
    val most = if (width < GP_NARROW) 2 else 4
    val inner = width - GP_GUTTER * 2
    return listOf(4, 2).filter { it <= most }.firstOrNull { n -> (inner - GP_CELL_GAP * (n - 1)) / n >= widest + GP_PILL_PAD * 2 } ?: 1
}

/** [gamePanelColumns] for [shortcuts] in [width]: their labels' words and values measured as the tiles draw them. */
@Composable
internal fun rememberGamePanelColumns(width: Dp, shortcuts: List<ShortcutState>): Int = gamePanelColumns(width, rememberWidestShortcut(shortcuts))

/** The widest of [shortcuts]' label words (a label wraps to two lines) and values, measured as the tiles draw them. */
@Composable
private fun rememberWidestShortcut(shortcuts: List<ShortcutState>): Dp {
    val measurer = rememberTextMeasurer()
    val font = LocalNxFont.current
    val density = LocalDensity.current
    return remember(shortcuts.map { it.label to it.value }, font, density) {
        val label = TextStyle(fontFamily = font, fontSize = GP_LABEL_SIZE)
        val value = TextStyle(fontFamily = font, fontSize = GP_SMALL_SIZE)
        val px = shortcuts.maxOfOrNull { s ->
            maxOf(s.label.split(' ').maxOf { measurer.measure(it, label, maxLines = 1).size.width }, s.value?.let { measurer.measure(it, value, maxLines = 1).size.width } ?: 0)
        } ?: 0
        with(density) { px.toDp() }
    }
}

/**
 * A shortcut's tile: a rounded outline, the label (up to two lines) centred in it, its value under it; at 0.35 alpha and
 * not clickable while disabled. A toggle ([ShortcutState.on]) that is on is filled (the list text colour, dark words),
 * as a quick-settings tile. A touch button's tile ([Shortcut.TOUCH]) sends its press and its release
 * ([GamePanelEvent.Touch]), so a hold runs while the finger is down, and shades while pressed; any other sends
 * [GamePanelModel.eventFor] on a tap. The controller's tile ([selected]) gets an accent ring a gap outside it, so it
 * reads on an outlined and a filled tile alike (an accent fill would look like an on toggle with a light accent).
 */
@Composable
private fun ShortcutTile(s: ShortcutState, selected: Boolean, modifier: Modifier, onEvent: (GamePanelEvent) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val filled = s.on == true
    // The controller's tile scrolls into view in the grid's scroll.
    val into = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) into.bringIntoView() }
    val shape = RoundedCornerShape(GP_TILE_RADIUS)
    var pressed by remember { mutableStateOf(false) }
    val send by rememberUpdatedState(onEvent)
    val touch = s.action?.takeIf { s.shortcut == Shortcut.TOUCH }
    val press = when {
        !s.enabled -> Modifier
        touch != null -> Modifier.pointerInput(touch) {
            detectTapGestures(onPress = {
                pressed = true; send(GamePanelEvent.Touch(touch, down = true))
                try { tryAwaitRelease() } finally { pressed = false; send(GamePanelEvent.Touch(touch, down = false)) }
            })
        }
        else -> Modifier.clickable { GamePanelModel.eventFor(s)?.let(send) }
    }
    Column(
        modifier.bringIntoViewRequester(into).heightIn(min = GP_TILE_HEIGHT)
            // Drawn outside the tile, in the grid's spacing (so the focus moving never resizes one), and before the dimming
            // (an alpha layer would clip it to the tile and fade it on a disabled one).
            .then(if (selected) Modifier.drawBehind { focusRing(colors.accent) } else Modifier)
            .alpha(if (s.enabled) 1f else NX_DISABLED_ALPHA)   // role: a disabled control
            .clip(shape)
            .then(
                if (filled) Modifier.background(colors.listText)
                else Modifier.background(if (pressed) colors.listText.copy(alpha = 0.16f) else Color.Transparent)   // role: a pressed tile's shade
                    .border(GP_TILE_RING, colors.hint.copy(alpha = 0.6f), shape),   // role: a button's grey ring
            )
            .then(press)
            .padding(horizontal = GP_PILL_PAD, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            s.label, color = if (filled) colors.background else colors.listText, fontFamily = font, fontSize = GP_LABEL_SIZE,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        s.value?.let {
            Text(it, color = if (filled) colors.background else colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1)
        }
    }
}

/** The controller's ring round a shortcut tile: [GP_FOCUS_RING] wide, [GP_FOCUS_GAP] outside its rounded edge. */
private fun DrawScope.focusRing(color: Color) {
    val out = (GP_FOCUS_GAP + GP_FOCUS_RING / 2).toPx()
    drawRoundRect(
        color, topLeft = Offset(-out, -out), size = Size(size.width + out * 2, size.height + out * 2),
        cornerRadius = CornerRadius(GP_TILE_RADIUS.toPx() + out), style = Stroke(GP_FOCUS_RING.toPx()),
    )
}

/**
 * The Settings tab: a row a setting ([QuickSetting]), its name then its control, changed as it is touched (the volume as
 * the thumb moves, an option on a tap), then "All options" (the in-game menu's Options page). A row the game's layout
 * holds is dimmed and takes no touch.
 */
@Composable
private fun SettingsTab(rows: List<QuickSetting>, bottom: Dp, onEvent: (GamePanelEvent) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().nxScrollFade(scroll, height = 56.dp, strong = true).verticalScroll(scroll)
            .padding(start = GP_GUTTER, end = GP_GUTTER, top = 6.dp, bottom = bottom),
    ) {
        rows.forEach { row ->
            val enabled = (row as? QuickSetting.Choice)?.enabled != false
            Row(
                Modifier.fillMaxWidth().heightIn(min = GP_QUICK_ROW).alpha(if (enabled) 1f else NX_DISABLED_ALPHA),   // role: a disabled control
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(row.label, Modifier.width(GP_QUICK_LABEL), color = colors.listText, fontFamily = font, fontSize = GP_LABEL_SIZE, maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (row) {
                    is QuickSetting.Volume -> {
                        // The thumb follows the finger; the device's volume has whole steps, so the row's own value lands on release.
                        var drag by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = drag ?: row.value, onValueChange = { drag = it; onEvent(GamePanelEvent.SetVolume(it)) },
                            onValueChangeFinished = { drag = null }, modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = colors.listText, activeTrackColor = colors.listText,
                                inactiveTrackColor = colors.hint.copy(alpha = 0.3f),   // role: the track past the thumb
                            ),
                        )
                        Text("${((drag ?: row.value) * 100).roundToInt()}%", Modifier.width(44.dp), color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 1, textAlign = TextAlign.End)
                    }
                    is QuickSetting.Choice -> if (row.segments) QuickSegments(row, Modifier.weight(1f), onEvent) else QuickStepper(row, Modifier.weight(1f), onEvent)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = GP_QUICK_ROW).clip(RoundedCornerShape(GP_SLOT_RADIUS))
                .clickable { onEvent(GamePanelEvent.OpenOptions) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Changes apply now. Save them from All options.", Modifier.weight(1f), color = colors.hint, fontFamily = font, fontSize = GP_SMALL_SIZE, maxLines = 2)
            Text("All options  \u203A", color = colors.listText, fontFamily = font, fontSize = GP_LABEL_SIZE, maxLines = 1)
        }
    }
}

/** A [QuickSetting.Choice]'s values side by side in one ring, the one in use filled; a tap on another sets it. */
@Composable
private fun QuickSegments(c: QuickSetting.Choice, modifier: Modifier, onEvent: (GamePanelEvent) -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    Row(modifier.height(GP_QUICK_CONTROL).clip(shape).border(GP_TILE_RING, colors.hint.copy(alpha = 0.6f), shape)) {   // role: a button's grey ring
        c.values.forEachIndexed { i, v ->
            val on = i == c.index
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(if (on) colors.listText else Color.Transparent)
                    .then(if (c.enabled && !on) Modifier.clickable { onEvent(GamePanelEvent.SetQuick(c.key, v)) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    c.labels.getOrElse(i) { v }, color = if (on) colors.background else colors.hint, fontFamily = LocalNxFont.current,
                    fontSize = GP_SMALL_SIZE, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A [QuickSetting.Choice]'s value between two arrows in one ring: a tap on its left half steps back, on its right half on. */
@Composable
private fun QuickStepper(c: QuickSetting.Choice, modifier: Modifier, onEvent: (GamePanelEvent) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val shape = RoundedCornerShape(50)
    val step = { delta: Int -> GamePanelModel.quickStep(c, delta)?.let { onEvent(GamePanelEvent.SetQuick(c.key, it)) } }
    Box(modifier.height(GP_QUICK_CONTROL).clip(shape).border(GP_TILE_RING, colors.hint.copy(alpha = 0.6f), shape)) {   // role: a button's grey ring
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().then(if (c.enabled) Modifier.clickable { step(-1) } else Modifier))
            Box(Modifier.weight(1f).fillMaxHeight().then(if (c.enabled) Modifier.clickable { step(1) } else Modifier))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\u2039", color = colors.hint, fontFamily = font, fontSize = GP_TITLE_SIZE, maxLines = 1)
            Text(
                c.labels.getOrNull(c.index) ?: GamePanelModel.quickFileLabel(c.current), Modifier.weight(1f), color = colors.listText,
                fontFamily = font, fontSize = GP_LABEL_SIZE, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Text("\u203A", color = colors.hint, fontFamily = font, fontSize = GP_TITLE_SIZE, maxLines = 1)
        }
    }
}

/** The achievements as [AchievementRow]s; they open nothing (the description is on the row). */
@Composable
private fun AchievementsTab(list: List<RaAchievement>, badges: Map<String, String>, focused: Int?, bottom: Dp) {
    PanelLazyList(focused, bottom) {
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
        Box(Modifier.size(GP_BADGE).clip(RoundedCornerShape(3.dp)).background(colors.inkFaint)) {
            badge?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RowText(title, selected, Modifier.weight(1f, fill = false))
                GamePanelModel.pointsBadge(a)?.let { NxCountPill(it, selected, GP_BADGE_TEXT_SIZE) }
            }
            if (a.description.isNotEmpty()) RowSubText(a.description, selected, maxLines = 2)
        }
    }
}

/**
 * The game's cheats, each with its switch ([NxSwitch]); a tap anywhere on a row flips it ([GamePanelModel.cheatEvent]),
 * live, as A on the controller's row does. None: [empty] (how to add some), centred.
 */
@Composable
private fun CheatsTab(cheats: List<CheatRow>, empty: String, focused: Int?, bottom: Dp, onEvent: (GamePanelEvent) -> Unit) {
    if (cheats.isEmpty()) {
        CentredNote(empty, Modifier.fillMaxSize().padding(start = GP_GUTTER, end = GP_GUTTER, bottom = bottom), GP_SMALL_SIZE)
        return
    }
    // Item indices are the focus indices and the cheats' own (no header item).
    PanelLazyList(focused, bottom) {
        itemsIndexed(cheats) { i, c ->
            val flip = { GamePanelModel.cheatEvent(i, cheats)?.let(onEvent); Unit }
            val selected = focused == i
            PanelListRow(selected, Modifier.clickable(onClick = flip)) {
                Column(Modifier.weight(1f)) {
                    RowText(c.name, selected)
                    // The file it came from, when several were merged (two can name a cheat alike).
                    if (c.source.isNotEmpty()) RowSubText(c.source, selected)
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
    PanelLazyList(focused, bottom) {
        itemsIndexed(players) { i, s ->
            PlayerRow("Player ${i + 1}", GamePanelModel.playerValue(s), focused == i, Modifier.clickable { onEvent(GamePanelModel.nextPlayer(i, players, pads)) })
        }
    }
}

/**
 * A panel list following the controller's [focused] item ([rememberFocusedListState]): the rows fade where more scrolls
 * in (the lists' cue), the focus an item clear of the fades. By default the panel tabs' frame, its end [bottom] clear.
 */
@Composable
internal fun PanelLazyList(
    focused: Int?,
    bottom: Dp = GP_LIST_END,
    modifier: Modifier = Modifier.fillMaxSize(),
    contentPadding: PaddingValues = PaddingValues(start = GP_LIST_GUTTER, end = GP_LIST_GUTTER, top = 6.dp, bottom = bottom),
    content: LazyListScope.() -> Unit,
) {
    val state = rememberFocusedListState(focused, margin = 1)
    LazyColumn(modifier.nxScrollFade(state, height = 56.dp, strong = true), state = state, contentPadding = contentPadding, content = content)
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
    PanelListRow(selected, modifier) {
        RowText(label, selected, Modifier.weight(1f))
        RowSubText(value, selected)
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

/** A row's second line or value, small, muted on the accent pill while [selected]. */
@Composable
private fun RowSubText(text: String, selected: Boolean, maxLines: Int = 1) {
    val colors = LocalNxColors.current
    Text(
        text, color = if (selected) colors.onAccentMuted else colors.hint, fontFamily = LocalNxFont.current, fontSize = GP_SMALL_SIZE,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
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
private val GP_LIST_BOTTOM = 76.dp   // the last row clears the Game pill (52 dp, 16 from the corner)
private val GP_LIST_END = 8.dp       // without the pill
private val GP_ART = 64.dp
private val GP_HEADER_BOTTOM = 16.dp   // between the header and the tabs (and the menu's list beside them)
private val GP_BADGE = 32.dp
private val GP_ROW_HEIGHT = 44.dp
private val GP_TILE_HEIGHT = 72.dp
private val GP_SLOT_W = 124.dp       // a save-state slot's card on the strip
private val GP_SLOT_H = 93.dp        // its screenshot box: 4:3
private val GP_SLOT_RADIUS = 10.dp
private val GP_SLOT_PICK = 2.dp      // the picked slot's accent ring
private val GP_SLOT_DATE = 12.sp
private val GP_QUICK_ROW = 50.dp      // a Settings tab row
private val GP_QUICK_LABEL = 124.dp   // its name's column
private val GP_QUICK_CONTROL = 38.dp  // its segments or stepper
private val GP_TILE_RADIUS = 18.dp
private val GP_TILE_RING = 1.5.dp     // a tile's grey outline
private val GP_FOCUS_RING = 2.dp     // the controller's ring round a shortcut tile
private val GP_FOCUS_GAP = 3.dp      // between that ring and the tile
private val GP_NARROW = 360.dp       // narrower: the shortcut grid goes to 2 columns (or 1)
private val GP_CELL_GAP = 10.dp      // between shortcut tiles
private val GP_PILL_PAD = 10.dp      // a shortcut tile's label from its sides
private val GP_TITLE_SIZE = 17.sp
private val GP_LABEL_SIZE = 15.sp
private val GP_SMALL_SIZE = 13.sp
private val GP_BADGE_TEXT_SIZE = 11.sp   // the points pill
