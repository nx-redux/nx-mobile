package com.nxredux.mobile.ui

import com.nxredux.mobile.shared.res.nav_icon_delete
import com.nxredux.mobile.ui.nx.MENU_TAB_ROW_HEIGHT
import com.nxredux.mobile.ui.nx.touchOnly
import com.nxredux.mobile.ui.nx.touchIcons
import com.nxredux.mobile.ui.nx.TOP_ICON_INSET
import com.nxredux.mobile.ui.nx.NxTopIcons
import com.nxredux.mobile.ui.nx.HintTouch
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import com.nxredux.mobile.library.MergedLabels
import com.nxredux.mobile.ui.nx.homeCoverTrim
import coil3.compose.AsyncImagePainter
import com.nxredux.mobile.ui.nx.BoxCover
import com.nxredux.mobile.ui.nx.GameInfoBand
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalLauncherChrome
import com.nxredux.mobile.ui.nx.LocalNxTapOpens
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxScreenBackground
import com.nxredux.mobile.ui.nx.NxTapNeverOpens
import com.nxredux.mobile.ui.nx.SwitcherBarInfoLine
import com.nxredux.mobile.ui.nx.nxEasedFade
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.background
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxDialog
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.NX_SWITCHER_BAR_ALPHA
import com.nxredux.mobile.ui.nx.NX_EASED_FADE_REACH
import com.nxredux.mobile.ui.nx.nxTopBandFade
import com.nxredux.mobile.ui.nx.placeholderBoxSize

/** How far a horizontal swipe travels before it moves to the next or previous game. */
private val SWIPE_THRESHOLD = 24.dp

/** The switcher's hints: B Back, and with a game Y Remove (touch: a trash icon) and A Resume (Start for one with no state to resume). */
internal fun switcherHints(entry: SwitcherEntry?): List<Hint> =
    if (entry == null) listOf(Hint(NxButton.B, "Back"))
    else listOf(
        Hint(NxButton.B, "Back"), Hint(NxButton.Y, "Remove", HintTouch.Icon(Res.drawable.nav_icon_delete)),
        Hint(NxButton.A, if (entry.resume.canResume) "Resume" else "Start"),
    )

/**
 * The switcher's List rows (Layouts → Game Switcher layout: List), in its order: games sharing a name labelled as a
 * merged console list's ([MergedLabels]: "Tetris (GB)", "<stem> (GB)", the stem), and an extra folder's copy of one
 * file under one tag told apart by its folder subtitle, as a game list's.
 */
internal fun switcherListRows(entries: List<SwitcherEntry>): List<NxRow> {
    val labels = MergedLabels.labels(entries.map { MergedLabels.Row(it.game.shownName, it.game.fileName, it.system.tag) })
    return entries.mapIndexed { i, e -> NxRow(labels[i] ?: e.game.shownName, secondary = e.game.subtitle) }
}

/**
 * The Game Switcher in its layout (Layouts → Game Switcher layout): [Screen.Switcher.list] a game list's List of the
 * recent games ([GameSwitcherListScreen]), else Full screen. [info]: the selected game's (empty with Extra info hidden).
 */
@Composable
fun GameSwitcherScreen(
    screen: Screen.Switcher, onButton: (NxButton) -> Unit, modifier: Modifier = Modifier, info: List<InfoLine> = emptyList(),
    onSelect: (Int) -> Unit = {}, onOpen: (Int) -> Unit = {},
) {
    if (screen.list && screen.entries.isNotEmpty()) GameSwitcherListScreen(screen, onButton, onSelect, onOpen, modifier, info)
    else GameSwitcherFullScreen(screen, onButton, modifier, info)
}

/**
 * The switcher's List (NX Redux PR #218): a game list's List titled Recent over its games ([switcherListRows]), the
 * selected one's resume frame behind the rows (else its screenshot, else its abstract picture, as List Home's Continue);
 * the List's own band, glide, marquee and hidden-hints behaviour. UP/DOWN move (AppState); A Resume, Y Remove, B Back.
 */
@Composable
private fun GameSwitcherListScreen(
    screen: Screen.Switcher, onButton: (NxButton) -> Unit, onSelect: (Int) -> Unit, onOpen: (Int) -> Unit,
    modifier: Modifier, info: List<InfoLine>,
) {
    val entry = screen.entries.getOrNull(screen.index)
    // Equal hints keep one list, so the hint bar recomposes only when they change (a move between two resumable games: never).
    val hints = rememberUpdatedState(remember(entry?.resume?.canResume, entry == null) { switcherHints(entry) })
    val shownInfo = rememberUpdatedState(info)
    val index = rememberUpdatedState(screen.index)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        // The pictures in turn ([continuePictures]): one that will not load gives way to the next, as Home's Continue card.
        val pictures = remember(entry) { entry?.let { continuePictures(it.resume.previewPath, Listed(it.system, it.game)) }.orEmpty() }
        var failed by remember(entry) { mutableIntStateOf(0) }
        val picture = pictures.getOrNull(failed)
        // The resume frame is a copy of the whole game view: trimmed of its letterbox first ([homeCoverTrim]), as Continue's.
        val context = LocalPlatformContext.current
        val preview = entry?.resume?.previewPath
        if (picture == null) NxScreenBackground(url = null, fallback = Res.drawable.background, fade = false, modifier = Modifier.fillMaxSize(), centred = true)
        else KeepUntilLoaded(picture) { shown, done ->
            val request = remember(shown, context) {
                if (shown == preview) ImageRequest.Builder(context).data(shown).transformations(homeCoverTrim()).build() else null
            }
            NxScreenBackground(
                url = shown, fallback = null, fade = true, modifier = Modifier.fillMaxSize(), request = request,
                onFailed = { done(); if (shown == picture) failed++ }, onLoaded = done,
            )
        }
        // A row launches its game: tap to highlight, tap again to resume, as a game list's.
        CompositionLocalProvider(LocalNxTapOpens provides NxTapNeverOpens) {
            GameListFrame(
                "Recent", MenuStyle.LIST, hints, { index.value }, dataKey = screen.entries,
                gridTiles = { emptyList() }, backdropGames = { emptyList() },
                listRows = { switcherListRows(screen.entries) }, captionTitle = { entry?.game?.shownName },
                onSelect = onSelect, onOpen = onOpen, onHint = onButton, info = { shownInfo.value }, recentIcon = false,
            )
        }
        if (screen.confirmRemove && entry != null) RemoveDialog(entry, onButton)
    }
}

/**
 * [content] for [model], with the picture last drawn kept under it until [model] has loaded (or failed): back from a
 * game the switcher's resume frame is read again under a new version (the same scene), and a game's picture after
 * another's, with no blank frame between. [content] gets the model to draw and the callback for its load's end.
 */
@Composable
private fun KeepUntilLoaded(model: String, content: @Composable (shown: String, done: () -> Unit) -> Unit) {
    var drawn by remember { mutableStateOf(model) }
    Box(Modifier.fillMaxSize()) {
        // One keyed loop: the picture drawn keeps its own composition (and its loaded image) as it moves under the new one.
        for (m in if (drawn != model) listOf(drawn, model) else listOf(model)) key(m) {
            content(m) { if (m == model) drawn = model }
        }
    }
}

@Composable
private fun RemoveDialog(entry: SwitcherEntry, onButton: (NxButton) -> Unit) = NxDialog(
    "Remove", "Remove \"${entry.game.shownName}\" from recents?",
    onConfirm = { onButton(NxButton.A) }, confirmLabel = "Remove", onCancel = { onButton(NxButton.B) },
)

/**
 * nx-redux's Game Switcher: one recent game at a time — its resume screenshot (or box art) filling the
 * screen, the title on top (the system and n / N under it), the game's info above the hints, and
 * B Back · Y Remove · A Resume. LEFT/RIGHT (or a horizontal swipe) moves.
 * Y asks for confirmation first ([Screen.Switcher.confirmRemove]). NX Redux PR #218: the info line follows Extra info
 * (the caller's [info]), and with Button hints hidden (where they can be) it moves into the bar's row, over its scrim.
 */
@Composable
private fun GameSwitcherFullScreen(screen: Screen.Switcher, onButton: (NxButton) -> Unit, modifier: Modifier, info: List<InfoLine>) {
    val colors = LocalNxColors.current
    val hintsShown = !LocalLauncherChrome.current.emptyBarRoom
    val font = LocalNxFont.current
    val entry = screen.entries.getOrNull(screen.index)
    Box(
        modifier.fillMaxSize().background(Color.Black)
            .pointerInput(screen.entries.size) {
                var dx = 0f
                val swipe = SWIPE_THRESHOLD.toPx()   // the same finger travel on every screen density
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx < -swipe) onButton(NxButton.RIGHT) else if (dx > swipe) onButton(NxButton.LEFT) },
                ) { _, amount -> dx += amount }
            },
    ) {
        if (entry == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No Recents", color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                Text("Play a game to see it here", color = colors.hint, fontFamily = font, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            // Tapping the preview resumes; the hint bar drawn on top swallows its own taps (see below).
            Box(Modifier.fillMaxSize().pointerInput(entry) { detectTapGestures { onButton(NxButton.A) } })
            // The resume preview; else the box chain: the 3D box Fit, a 2D front or grid in the case, else "No Preview".
            val preview = entry.resume.previewPath
            var failed by remember(entry) { mutableStateOf(emptySet<String>()) }
            val box = boxShown(entry.game.boxartRef?.value, entry.game.box2dRef?.value, entry.game.gridRef?.value, failed)
            when {
                preview != null -> KeepUntilLoaded(preview) { shown, done ->
                    AsyncImage(
                        model = shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        onState = { if (it is AsyncImagePainter.State.Success || it is AsyncImagePainter.State.Error) done() },
                    )
                }
                box is BoxShown.Art3d -> AsyncImage(
                    model = box.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                    onState = { if (it is AsyncImagePainter.State.Error) failed = failed + box.url },
                )
                box is BoxShown.Cover -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val (w, h) = placeholderBoxSize(maxWidth.value, maxHeight.value * 0.8f)
                    BoxCover(box.url, w, h, null) { failed = failed + box.url }
                }
                else -> Text("No Preview", color = colors.hint, fontFamily = font, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
            }
            // The title, and under it the game's system and its place among the recents, on a full-width fade mirroring the
            // info band's: the switcher bar's alpha at the top edge, easing to clear [NX_EASED_FADE_REACH] below the
            // subtitle, still dark across the two lines.
            Column(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .nxTopBandFade(colors.background)
                    // On touch with the trash icon, the name stops short of its target.
                    .padding(start = 20.dp, end = 20.dp + if (touchOnly() && switcherHints(entry).touchIcons(onButton).isNotEmpty()) MENU_TAB_ROW_HEIGHT else 0.dp, top = 10.dp, bottom = NX_EASED_FADE_REACH),
            ) {
                Text(
                    text = entry.game.shownName,
                    color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start,
                )
                Text(
                    text = switcherSubtitle(entry.system.displayName, screen.index, screen.entries.size),
                    color = colors.hint, fontFamily = font, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Touch only, Remove is a trash icon at the top right, level with the game's name.
            if (touchOnly()) NxTopIcons(
                switcherHints(entry).touchIcons(onButton),
                Modifier.align(Alignment.TopEnd).padding(end = 20.dp - TOP_ICON_INSET),
            )
        }
        // The info band and the hints together at the bottom, so the band sits directly on the bar.
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            // Darker than the menus' bar, on an eased fade that stays dark across the text: a light screenshot would wash
            // the info line out.
            val hints = switcherHints(entry)
            // Consume taps on the bar itself so a miss between hints never falls through to the preview's resume.
            val swallow = Modifier.pointerInput(Unit) { detectTapGestures { } }
            if (hintsShown) {
                if (entry != null) GameInfoBand(info, edgeAlpha = NX_SWITCHER_BAR_ALPHA, fadeReach = NX_EASED_FADE_REACH)
                NxHintBar(hints, modifier = swallow, onHint = onButton, scrim = colors.scrimSwitcher)
            } else {
                // Hidden: the bar's row keeps its room and its scrim, the info line centred in it under the same fade.
                if (entry != null && info.isNotEmpty()) Box(Modifier.fillMaxWidth().height(NX_EASED_FADE_REACH).nxEasedFade(colors.background, NX_SWITCHER_BAR_ALPHA))
                Box(swallow) {
                    NxHintBar(hints, Modifier.alpha(0f))   // role: laid out unseen, the shown bar's room exactly
                    Box(Modifier.matchParentSize().background(colors.scrimSwitcher))
                    if (entry != null) SwitcherBarInfoLine(info, Modifier.matchParentSize())
                }
            }
        }
        if (screen.confirmRemove && entry != null) RemoveDialog(entry, onButton)
    }
}
