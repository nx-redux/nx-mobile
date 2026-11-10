package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.library.SystemFolder
import com.nxredux.mobile.library.path
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.ui.BiosMissing
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.TopContent
import com.nxredux.mobile.ui.abstractArtFor
import com.nxredux.mobile.ui.gamesLabel
import com.nxredux.mobile.ui.nowMs
import com.nxredux.mobile.ui.panelArt
import com.nxredux.mobile.ui.whenText

/** A game on the stats page's shelf: its play time ([ms], 0 for a newest-added one filling the shelf), its [resume] picture once read. */
data class ShelfGame(val entry: Listed, val ms: Long, val resume: String? = null)

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
        .take(SHELF_MAX).map { (e, ms) -> ShelfGame(e, ms) }
    val taken = played.map { it.entry.game.path }.toSet()
    val fill = entries.filter { it.game.path !in taken }
        .sortedWith(compareByDescending<Listed, Long?>(nullsFirst()) { it.game.firstSeen }.thenBy { it.shown.lowercase() })
        .take(SHELF_MAX - played.size).map { e -> ShelfGame(e, 0) }
    return played + fill
}

/** The shelf's label: all played, all filled, or both. */
internal fun shelfLabel(shelf: List<ShelfGame>): String = when {
    shelf.none { it.ms > 0 } -> "NEWEST ADDED"
    shelf.all { it.ms > 0 } -> "MOST PLAYED"
    else -> "PLAYED & NEW"
}

/**
 * A tile's line under its name: "24 h" for a played game, "Not played yet" for a filled one (its first-seen time can be
 * the file's own date, years before the player added it).
 */
internal fun shelfCaption(game: ShelfGame): String = if (game.ms > 0) hoursText(game.ms) else "Not played yet"

/** "86 h" from an hour up, else "45 m"; a play under a minute is "< 1 m" so it never reads as none. */
internal fun hoursText(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60_000L
    return when {
        m >= 60 -> "${m / 60} h"
        m == 0L && ms > 0 -> "< 1 m"
        else -> "$m m"
    }
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

/** The small mark fits above the stats' [content] (its title block and shelf) in a page [height] tall (inside its padding). */
internal fun statsMark(content: Dp, height: Dp): Boolean = content + TOP_MARK_BAND <= height

/**
 * The top screen's console / collection stats: the title, one quiet line of facts and the BIOS pill (only when one is
 * missing) over the shelf, all anchored to the bottom; the shelf is its label over three 16:9 pictures, each captioned
 * with its game's name and play time (or "Not played yet"). Nothing scrolls: a short screen drops the captions, then
 * the shelf ([shelfFit]), and the title block stays at the bottom. The small NX mark sits at the top right where the
 * content leaves it room ([statsMark]). [onLaunch]: a picture's clean tap. [prefer]: Layouts → Backdrop art, for a game whose only picture is
 * its box.
 */
@Composable
fun TopStatsPage(stats: TopStats, onLaunch: (Listed) -> Unit, prefer: BackdropArt = BackdropArt.BOX3D) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val now = remember(stats) { nowMs() }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        // The title block's height decides how much of the shelf fits, so it is measured first.
        SubcomposeLayout(Modifier.fillMaxSize().padding(topPagePadding(maxWidth, maxHeight))) { constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth)
            val head = subcompose("head") {
                Column {
                    // Trimmed line spacing: the title and the facts line read as one block.
                    Text(
                        stats.title, color = colors.listText, fontFamily = font, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeightStyle = TOP_TIGHT_LINES),
                    )
                    Text(
                        statsLine(stats, now), color = colors.hint, fontFamily = font, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(lineHeightStyle = TOP_TIGHT_LINES), modifier = Modifier.padding(top = 2.dp),
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
                Shelf(stats, fit == ShelfFit.CAPTIONED, width * TS_TILE_GAP, prefer, onLaunch)
            }.first().measure(loose)
            val shelfTop = constraints.maxHeight - (shelf?.height ?: 0)
            val headTop = if (shelf == null) shelfTop - head.height else shelfTop - TS_BREATH.roundToPx() - head.height
            // Only where the content leaves its band free: the mark never costs the shelf or the captions.
            val mark = if (statsMark((constraints.maxHeight - headTop).toDp(), constraints.maxHeight.toDp())) {
                subcompose("mark") { Box(Modifier.fillMaxWidth()) { TopMark() } }.first().measure(loose)
            } else null
            layout(constraints.maxWidth, constraints.maxHeight) {
                mark?.place(0, 0)
                head.place(0, headTop)
                shelf?.place(0, shelfTop)
            }
        }
    }
}

@Composable
private fun Shelf(stats: TopStats, captions: Boolean, gap: Dp, prefer: BackdropArt, onLaunch: (Listed) -> Unit) {
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
                        Text(shelfCaption(g), color = colors.hint, fontFamily = font, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A tile's [shelfImage] ([TopPicture]). */
@Composable
private fun ShelfPicture(game: ShelfGame, prefer: BackdropArt, modifier: Modifier) {
    val e = game.entry
    TopPicture(e, shelfImage(e.game.screenshotRef?.value, game.resume, panelArt(prefer, e.game) ?: abstractArtFor(e)), modifier)
}

private const val TS_TILE_GAP = 0.04f       // between the shelf's tiles, of its width
private val TS_BREATH: Dp = 24.dp           // between the line (or pill) and the shelf
private val TS_LABEL: Dp = 28.dp            // the shelf's label, its gap to the pictures included
private val TS_CAPTIONS: Dp = 52.dp         // a tile's two caption lines, their space above included
