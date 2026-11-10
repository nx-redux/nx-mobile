package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.ui.BiosMissing
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.TopContent
import com.nxredux.mobile.ui.abstractArtFor
import com.nxredux.mobile.ui.appId
import com.nxredux.mobile.ui.gamesLabel
import com.nxredux.mobile.ui.nowMs
import com.nxredux.mobile.ui.panelArt
import com.nxredux.mobile.ui.whenText

/** A game on the stats page's shelf: its play time ([ms], 0 on a newest-added shelf), when it was [added], its [resume] picture once read. */
data class ShelfGame(val entry: Listed, val ms: Long, val added: Long?, val resume: String? = null)

/**
 * The top screen's page for a highlighted console or collection ([key]: which, [statsKey]): its [title], its [games],
 * the [consoles] a collection spans (null for a console), the time [playedMs] and when one was [lastPlayed] (null: never),
 * the [bios] pill (a console's only) and a [shelf] of up to three games, the most played topped up with the newest added.
 */
data class TopStats(
    val key: String,
    val title: String,
    val games: Int,
    val consoles: Int?,
    val playedMs: Long,
    val lastPlayed: Long?,
    val bios: BiosMissing?,
    val shelf: List<ShelfGame>,
)

/** Which stats page [top] shows, as [TopStats.key]; null for any other top content. */
fun statsKey(top: TopContent): String? = when (top) {
    is TopContent.ConsoleStats -> consoleKey(top.systems)
    is TopContent.CollectionStats -> collectionKey(top.name)
    else -> null
}

private fun consoleKey(systems: List<SystemFolder>) = "console:" + systems.joinToString(",") { it.tag }
private fun collectionKey(name: String) = "collection:$name"

/** [systems]' page over their [entries]. */
fun consoleStats(systems: List<SystemFolder>, entries: List<Listed>, info: Map<String, GameInfo>, bios: BiosMissing? = null): TopStats {
    val shelf = shelf(entries, info)
    return TopStats(
        key = consoleKey(systems), title = systems.first().displayName, games = entries.size, consoles = null,
        playedMs = playedMs(entries, info), lastPlayed = lastPlayed(entries, info), bios = bios, shelf = shelf,
    )
}

/** Collection [name]'s page over its [entries]. */
fun collectionStats(name: String, entries: List<Listed>, info: Map<String, GameInfo>): TopStats {
    val shelf = shelf(entries, info)
    return TopStats(
        key = collectionKey(name), title = name, games = entries.size,
        consoles = entries.map { it.system.displayName }.distinct().size,   // a merged console (GBA and MGBA) is one console
        playedMs = playedMs(entries, info), lastPlayed = lastPlayed(entries, info), bios = null, shelf = shelf,
    )
}

private fun playedMs(entries: List<Listed>, info: Map<String, GameInfo>) = entries.sumOf { (info[it.game.path]?.totalMs ?: 0L).coerceAtLeast(0) }

private fun lastPlayed(entries: List<Listed>, info: Map<String, GameInfo>) = entries.mapNotNull { info[it.game.path]?.lastPlayedAt }.maxOrNull()

/**
 * The page's one line at [now]: "71 games · 86 h played · last played today", a collection's consoles after its games;
 * the play time without any, the last played without a play.
 */
internal fun statsLine(stats: TopStats, now: Long): String = listOfNotNull(
    gamesLabel(stats.games),
    stats.consoles?.let { if (it == 1) "1 console" else "$it consoles" },
    "${hoursText(stats.playedMs)} played".takeIf { stats.playedMs > 0 },
    stats.lastPlayed?.let { "last played " + whenText(now, it).lowercase() },
).joinToString(INFO_SEPARATOR)

/** The BIOS pill's words; null (no pill) when nothing is missing. */
internal fun biosPill(bios: BiosMissing?): String? = when (bios) {
    BiosMissing.REQUIRED -> "BIOS missing"
    BiosMissing.OPTIONAL -> "BIOS missing (optional)"
    null -> null
}

/** The played games by time, then the newest added to fill the shelf, never the same game twice. */
private fun shelf(entries: List<Listed>, info: Map<String, GameInfo>): List<ShelfGame> {
    val played = entries.mapNotNull { e -> info[e.game.path]?.totalMs?.takeIf { it > 0 }?.let { e to it } }
        .sortedWith(compareByDescending<Pair<Listed, Long>> { it.second }.thenBy { it.first.shown.lowercase() })
        .take(SHELF_MAX).map { (e, ms) -> ShelfGame(e, ms, e.game.firstSeen) }
    val taken = played.map { it.entry.game.path }.toSet()
    val fill = entries.filter { it.game.path !in taken }
        .sortedWith(compareByDescending<Listed, Long?>(nullsFirst()) { it.game.firstSeen }.thenBy { it.shown.lowercase() })
        .take(SHELF_MAX - played.size).map { e -> ShelfGame(e, 0, e.game.firstSeen) }
    return played + fill
}

/** The shelf's label: all played, all filled, or both. */
internal fun shelfLabel(shelf: List<ShelfGame>): String = when {
    shelf.none { it.ms > 0 } -> "NEWEST ADDED"
    shelf.all { it.ms > 0 } -> "MOST PLAYED"
    else -> "PLAYED & NEW"
}

/** A tile's line under its name: "24 h" for a played game, "added yesterday" for a filled one (null without a first-seen time). */
internal fun shelfCaption(game: ShelfGame, now: Long): String? =
    if (game.ms > 0) hoursText(game.ms) else game.added?.let { "added " + whenText(now, it).lowercase() }

/** "86 h" from an hour up, else "45 m"; a play under a minute is "< 1 m" so it never reads as none. */
internal fun hoursText(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60_000L
    return when {
        m >= 60 -> "${m / 60} h"
        m == 0L && ms > 0 -> "< 1 m"
        else -> "$m m"
    }
}

/** What a shelf tile draws: a picture filling its 16:9, a box art fitted over a blurred copy of itself, or a placeholder when the game has neither. */
sealed interface ShelfImage {
    data class Shot(val url: String) : ShelfImage
    data class Box(val url: String) : ShelfImage
    data object Placeholder : ShelfImage
}

/** The scraped [screenshot], else the [resume] save's picture, else the [box] art. */
internal fun shelfImage(screenshot: String?, resume: String?, box: String?): ShelfImage = when {
    screenshot != null -> ShelfImage.Shot(screenshot)
    resume != null -> ShelfImage.Shot(resume)
    box != null -> ShelfImage.Box(box)
    else -> ShelfImage.Placeholder
}

/** What of the shelf a short top screen keeps: the pictures and their captions, the pictures alone, or nothing. */
internal enum class ShelfFit { CAPTIONED, PICTURES, NONE }

/**
 * The shelf in the [width] x [height] left under the title block: its label over three 16:9 tiles in a row (gaps
 * [TS_TILE_GAP] of the width) after a [TS_BREATH] from the line, with their captions while they fit, else without.
 */
internal fun shelfFit(width: Dp, height: Dp): ShelfFit {
    val tile = (width - width * TS_TILE_GAP * (SHELF_MAX - 1)) / SHELF_MAX * 9f / 16f
    val pictures = TS_BREATH + TS_LABEL + tile
    return when {
        height >= pictures + TS_CAPTIONS -> ShelfFit.CAPTIONED
        height >= pictures -> ShelfFit.PICTURES
        else -> ShelfFit.NONE
    }
}

private const val SHELF_MAX = 3

/**
 * The top screen's console / collection stats: the title, one quiet line of facts and the BIOS pill (only when one is
 * missing) over the shelf, all anchored to the bottom; the shelf is its label over three 16:9 pictures, each captioned
 * with its game's name and play time (or when it was added). Nothing scrolls: a short screen drops the captions, then
 * the shelf ([shelfFit]), and the title block stays at the bottom. [onLaunch]: a picture's clean tap. [prefer]: Layouts → Backdrop art, for a game whose only picture is
 * its box.
 */
@Composable
fun TopStatsPage(stats: TopStats, onLaunch: (Listed) -> Unit, prefer: BackdropArt = BackdropArt.BOX3D) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val now = remember(stats) { nowMs() }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val vPad = minOf(maxWidth * TS_PAD, maxHeight * TS_PAD_MAX_H)
        // The title block's height decides how much of the shelf fits, so it is measured first.
        SubcomposeLayout(Modifier.fillMaxSize().padding(horizontal = maxWidth * TS_PAD, vertical = vPad)) { constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth)
            val head = subcompose("head") {
                Column {
                    // Trimmed line spacing: the title and the facts line read as one block.
                    Text(
                        stats.title, color = colors.listText, fontFamily = font, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeightStyle = TS_TIGHT_LINES),
                    )
                    Text(
                        statsLine(stats, now), color = colors.hint, fontFamily = font, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(lineHeightStyle = TS_TIGHT_LINES), modifier = Modifier.padding(top = 2.dp),
                    )
                    biosPill(stats.bios)?.let { pill ->
                        Text(
                            pill, color = colors.alertText, fontFamily = font, fontSize = 13.sp, maxLines = 1,
                            modifier = Modifier.padding(top = 12.dp).border(1.dp, colors.alertLine, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                }
            }.first().measure(loose)
            val width = constraints.maxWidth.toDp()
            val fit = if (stats.shelf.isEmpty()) ShelfFit.NONE else shelfFit(width, (constraints.maxHeight - head.height).toDp())
            val shelf = if (fit == ShelfFit.NONE) null else subcompose("shelf") {
                Shelf(stats, fit == ShelfFit.CAPTIONED, width * TS_TILE_GAP, now, prefer, onLaunch)
            }.first().measure(loose)
            val shelfTop = constraints.maxHeight - (shelf?.height ?: 0)
            val headTop = if (shelf == null) shelfTop - head.height else shelfTop - TS_BREATH.roundToPx() - head.height
            layout(constraints.maxWidth, constraints.maxHeight) {
                head.place(0, headTop)
                shelf?.place(0, shelfTop)
            }
        }
    }
}

@Composable
private fun Shelf(stats: TopStats, captions: Boolean, gap: Dp, now: Long, prefer: BackdropArt, onLaunch: (Listed) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            shelfLabel(stats.shelf), color = colors.hint, fontFamily = font, fontSize = 12.sp,
            letterSpacing = 1.5.sp, maxLines = 1, modifier = Modifier.height(TS_LABEL),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            repeat(SHELF_MAX) { i ->
                val g = stats.shelf.getOrNull(i)
                if (g == null) Spacer(Modifier.weight(1f))
                else Column(Modifier.weight(1f)) {
                    ShelfPicture(g, prefer, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).cleanTap { onLaunch(g.entry) })
                    if (captions) Column(Modifier.height(TS_CAPTIONS).padding(top = 8.dp)) {
                        Text(g.entry.shown, color = colors.listText, fontFamily = font, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        shelfCaption(g, now)?.let {
                            Text(it, color = colors.hint, fontFamily = font, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** A tile's [shelfImage]: a picture cropped to 16:9, or the box fitted over a blurred, darkened copy of itself; an app's icon, else the console's logo or the game's name on a quiet surface. */
@Composable
private fun ShelfPicture(game: ShelfGame, prefer: BackdropArt, modifier: Modifier) {
    val e = game.entry
    Box(modifier.background(LocalNxColors.current.pictureGround), contentAlignment = Alignment.Center) {
        val icon = rememberAppIcon(e.appId)
        val colors = LocalNxColors.current
        when (val image = shelfImage(e.game.screenshotRef?.value, game.resume, panelArt(prefer, e.game) ?: abstractArtFor(e))) {
            is ShelfImage.Shot -> AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            is ShelfImage.Box -> {
                AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(TS_BLUR))
                Box(Modifier.fillMaxSize().background(BOX_DIM))
                AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            ShelfImage.Placeholder -> {
                Box(Modifier.fillMaxSize().background(colors.modalCard), contentAlignment = Alignment.Center) {
                    val logo = consoleLogo(e.system.tag)
                    when {
                        icon != null -> Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.6f))
                        logo != null -> Image(painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.6f), colorFilter = ColorFilter.tint(colors.hint))
                        else -> Text(
                            e.shown, color = colors.hint, fontFamily = LocalNxFont.current, fontSize = 14.sp, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val TS_PAD = 0.07f            // of the width, every side
private const val TS_PAD_MAX_H = 0.08f      // a wide, short screen's top and bottom padding: at most this of its height
private const val TS_TILE_GAP = 0.04f       // between the shelf's tiles, of its width
private val TS_BREATH: Dp = 24.dp           // between the line (or pill) and the shelf
private val TS_LABEL: Dp = 28.dp            // the shelf's label, its gap to the pictures included
private val TS_CAPTIONS: Dp = 52.dp         // a tile's two caption lines, their space above included
private val TS_BLUR: Dp = 16.dp
private val BOX_DIM = Color.Black.copy(alpha = 0.45f)   // role: darkens the blurred box copy so the fitted box stands out

/**
 * Whether a lift ends a tap: quick (a finger or palm resting on the cover is not one), within the touch slop, and not
 * consumed (a parent took the gesture, or the system cancelled it).
 */
internal fun isCleanTap(heldMs: Long, movedPx: Float, consumed: Boolean, timeoutMs: Long, slopPx: Float): Boolean =
    !consumed && heldMs <= timeoutMs && movedPx <= slopPx

/**
 * A tap that only taps: one finger, lifted within the long-press time and the touch slop of where it went down. A drag
 * or a second finger is not one, and nothing is consumed, so a swipe over the tile does what it did before.
 */
private fun Modifier.cleanTap(onTap: () -> Unit): Modifier = composed {
    val action = rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var clean = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.size > 1) clean = false
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) clean = false
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) clean = false
                if (change.changedToUp()) {
                    val moved = (change.position - down.position).getDistance()
                    if (clean && isCleanTap(change.uptimeMillis - down.uptimeMillis, moved, change.isConsumed, viewConfiguration.longPressTimeoutMillis, viewConfiguration.touchSlop)) action.value()
                    break
                }
                if (!change.pressed) break
            }
        }
    }
}

private val TS_TIGHT_LINES = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
