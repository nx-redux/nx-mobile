package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nxredux.mobile.achievements.RaProgress
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
import com.nxredux.mobile.ui.nowMs
import com.nxredux.mobile.ui.panelArt
import com.nxredux.mobile.ui.whenText
import org.jetbrains.compose.resources.painterResource

/** A box art on the stats page's shelf, with its [caption] (hours played, or when it was added; a collection's lead with the console). */
data class ShelfGame(val entry: Listed, val caption: String)

/**
 * The top screen's page for a highlighted console or collection ([key]: which, [statsKey]). Four numbers: games
 * ([gamesNote] under it), play time and [sessions], [lastPlayed] (game and when), then [fourth] (label to value,
 * [fourthNote] under it); a [shelf] of up to five games titled [shelfTitle]; [chips]. [logoTag]: the console whose logo
 * leads the header; null for a collection.
 */
data class TopStats(
    val key: String,
    val title: String,
    val subtitle: String,
    val logoTag: String?,
    val games: Int,
    val gamesNote: String,
    val neverPlayed: Int,
    val totalMs: Long,
    val sessions: Long,
    val lastPlayed: Pair<Listed, Long>?,
    val fourth: Pair<String, String>,
    val fourthNote: String,
    val shelf: List<ShelfGame>,
    val shelfTitle: String,
    val chips: List<String>,
)

/** Which stats page [top] shows, as [TopStats.key]; null for any other top content. */
fun statsKey(top: TopContent): String? = when (top) {
    is TopContent.ConsoleStats -> consoleKey(top.systems)
    is TopContent.CollectionStats -> collectionKey(top.name)
    else -> null
}

private fun consoleKey(systems: List<SystemFolder>) = "console:" + systems.joinToString(",") { it.tag }
private fun collectionKey(name: String) = "collection:$name"

/** [systems]' page over their [entries]: the emulators in the header; chips for games first seen since [weekStart], [cheatGames] and [bios]. */
fun consoleStats(
    systems: List<SystemFolder>, entries: List<Listed>, info: Map<String, GameInfo>, now: Long, weekStart: Long,
    bios: BiosMissing?, cheatGames: Int,
): TopStats {
    val t = tally(entries, info)
    val earned = t.ra.sumOf { it.unlocked }
    val inGames = t.ra.count { it.unlocked > 0 }
    val (shelfTitle, shelf) = shelf(entries, info, now, withConsole = false)
    return TopStats(
        key = consoleKey(systems),
        title = systems.first().displayName,
        subtitle = systems.mapNotNull { it.core?.name }.distinct().joinToString(INFO_SEPARATOR),
        logoTag = systems.first().tag,
        games = entries.size, gamesNote = "${t.neverPlayed} never played", neverPlayed = t.neverPlayed,
        totalMs = t.totalMs, sessions = t.sessions, lastPlayed = t.last,
        fourth = "Achievements" to "$earned", fourthNote = if (inGames == 1) "in 1 game" else "in $inGames games",
        shelf = shelf, shelfTitle = shelfTitle,
        chips = consoleChips(entries, now, weekStart, bios, cheatGames),
    )
}

/** A console's chips: games first seen since [weekStart], games with cheats, a missing BIOS; each only when there is one. */
fun consoleChips(entries: List<Listed>, now: Long, weekStart: Long, bios: BiosMissing?, cheatGames: Int): List<String> {
    val added = entries.count { e -> e.game.firstSeen?.let { it in weekStart..now } == true }
    return listOfNotNull(
        "Added this week: $added".takeIf { added > 0 },
        (if (cheatGames == 1) "Cheats for 1 game" else "Cheats for $cheatGames games").takeIf { cheatGames > 0 },
        when (bios) { BiosMissing.REQUIRED -> "BIOS missing"; BiosMissing.OPTIONAL -> "BIOS missing (optional)"; null -> null },
    )
}

/** Collection [name]'s page over its [entries]: the consoles it spans in the header, Finished (every achievement) fourth. */
fun collectionStats(name: String, entries: List<Listed>, info: Map<String, GameInfo>, now: Long): TopStats {
    val t = tally(entries, info)
    // A merged console (GBA and MGBA) is one console: grouped by name, shown by its first tag.
    val consoles = entries.groupBy { it.system.displayName }.values.map { it.first().system.tag }
    val finished = t.ra.count { it.total > 0 && it.unlocked >= it.total }
    val (shelfTitle, shelf) = shelf(entries, info, now, withConsole = true)
    return TopStats(
        key = collectionKey(name), title = name, subtitle = consoles.joinToString(INFO_SEPARATOR), logoTag = null,
        games = entries.size, gamesNote = if (consoles.size == 1) "1 console" else "${consoles.size} consoles", neverPlayed = t.neverPlayed,
        totalMs = t.totalMs, sessions = t.sessions, lastPlayed = t.last,
        fourth = "Finished" to "$finished", fourthNote = "all achievements",
        shelf = shelf, shelfTitle = shelfTitle,
        chips = listOfNotNull("Never played: ${t.neverPlayed}".takeIf { t.neverPlayed > 0 }),
    )
}

private class Tally(val neverPlayed: Int, val totalMs: Long, val sessions: Long, val last: Pair<Listed, Long>?, val ra: List<RaProgress>)

private fun tally(entries: List<Listed>, info: Map<String, GameInfo>): Tally {
    var never = 0; var total = 0L; var sessions = 0L; var last: Pair<Listed, Long>? = null
    val ra = ArrayList<RaProgress>()
    for (e in entries) {
        val i = info[e.game.path]
        val ms = i?.totalMs ?: 0L
        val plays = i?.plays ?: 0L
        if (ms <= 0 && plays <= 0 && i?.lastPlayedAt == null) never++
        total += ms.coerceAtLeast(0)
        sessions += plays.coerceAtLeast(0)
        i?.lastPlayedAt?.let { at -> if (last == null || at > last!!.second) last = e to at }
        i?.ra?.let(ra::add)
    }
    return Tally(never, total, sessions, last, ra)
}

/** Most played (by play time) when anything was; else the newest added, so the shelf is never empty. */
private fun shelf(entries: List<Listed>, info: Map<String, GameInfo>, now: Long, withConsole: Boolean): Pair<String, List<ShelfGame>> {
    fun caption(e: Listed, text: String?) = listOfNotNull(e.system.tag.takeIf { withConsole }, text).joinToString(INFO_SEPARATOR)
    val played = entries.mapNotNull { e -> info[e.game.path]?.totalMs?.takeIf { it > 0 }?.let { e to it } }
    if (played.isNotEmpty()) {
        return "Most played" to played.sortedWith(compareByDescending<Pair<Listed, Long>> { it.second }.thenBy { it.first.shown.lowercase() })
            .take(SHELF_MAX).map { (e, ms) -> ShelfGame(e, caption(e, hoursText(ms))) }
    }
    return "Newest added" to entries.sortedWith(compareByDescending<Listed, Long?>(nullsFirst()) { it.game.firstSeen }.thenBy { it.shown.lowercase() })
        .take(SHELF_MAX).map { e -> ShelfGame(e, caption(e, e.game.firstSeen?.let { whenText(now, it) })) }
}

/** "86h" from an hour up, else "45m" (a game under a minute: "0m"). */
internal fun hoursText(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60_000L
    return if (m >= 60) "${m / 60}h" else "${m}m"
}

private const val SHELF_MAX = 5

/**
 * The top screen's console / collection stats: the header (console logo, title, emulators or consoles), the four
 * numbers, the shelf and the chips. Nothing scrolls: on a short screen the chips go first, then the shelf shrinks, and
 * the shelf shows the tiles that fit its width. [onLaunch]: a shelf game's clean tap. [prefer]: Layouts →
 * Backdrop art.
 */
@Composable
fun TopStatsPage(stats: TopStats, onLaunch: (Listed) -> Unit, prefer: BackdropArt = BackdropArt.BOX3D) {
    val colors = LocalNxColors.current
    val now = remember(stats) { nowMs() }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).padding(TS_PAD)) {
        val chips = maxHeight >= TS_CHIPS_MIN && stats.chips.isNotEmpty()
        val shelf = maxHeight >= TS_SHELF_MIN && stats.shelf.isNotEmpty()
        val tiles = ((maxWidth + TS_GAP) / (TS_TILE_MIN + TS_GAP)).toInt().coerceIn(1, SHELF_MAX)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsHeader(stats)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TS_GAP)) {
                val last = stats.lastPlayed
                StatTile("Games", "${stats.games}", stats.gamesNote, Modifier.weight(1f))
                StatTile("Play time", hoursText(stats.totalMs), if (stats.sessions == 1L) "1 session" else "${stats.sessions} sessions", Modifier.weight(1f))
                StatTile("Last played", last?.let { whenText(now, it.second) } ?: "Never", last?.first?.shown.orEmpty(), Modifier.weight(1f))
                StatTile(stats.fourth.first, stats.fourth.second, stats.fourthNote, Modifier.weight(1f))
            }
            if (shelf) {
                Label(stats.shelfTitle)
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(TS_GAP)) {
                    stats.shelf.take(tiles).forEach { ShelfTile(it, prefer, Modifier.weight(1f).cleanTap { onLaunch(it.entry) }) }
                    repeat(tiles - stats.shelf.size.coerceAtMost(tiles)) { Spacer(Modifier.weight(1f)) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (chips) StatsChips(stats.chips)
        }
    }
}

@Composable
private fun StatsHeader(stats: TopStats) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.logoTag?.let(::consoleLogo)?.let { logo ->
            Image(painterResource(logo), contentDescription = null, colorFilter = ColorFilter.tint(colors.listText), modifier = Modifier.size(width = 70.dp, height = 40.dp))
        }
        Column {
            Text(stats.title, color = colors.listText, fontFamily = font, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stats.subtitle.isNotEmpty()) {
                Text(stats.subtitle, color = colors.hint, fontFamily = font, fontSize = TS_SMALL, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, note: String, modifier: Modifier) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Column(modifier.border(1.dp, colors.inkFaint, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
        Label(label)
        Text(value, color = colors.listText, fontFamily = font, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // An empty note keeps its line: the four tiles stay one height.
        Text(note, color = colors.hint, fontFamily = font, fontSize = TS_LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), color = LocalNxColors.current.hint, fontFamily = LocalNxFont.current, fontSize = TS_LABEL,
        letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ShelfTile(game: ShelfGame, prefer: BackdropArt, modifier: Modifier) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val e = game.entry
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            val icon = rememberAppIcon(e.appId)
            val art = panelArt(prefer, e.game) ?: abstractArtFor(e)
            when {
                icon != null -> Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.7f))
                art != null -> AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
        Text(e.shown, color = colors.listText, fontFamily = font, fontSize = TS_SMALL, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (game.caption.isNotEmpty()) Text(game.caption, color = colors.hint, fontFamily = font, fontSize = TS_LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsChips(chips: List<String>) {
    val colors = LocalNxColors.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), maxLines = 1) {
        chips.forEach {
            Text(
                it, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = TS_CHIP, maxLines = 1,
                modifier = Modifier.border(1.dp, colors.inkDim, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 2.dp),
            )
        }
    }
}

private val TS_PAD = 14.dp
private val TS_GAP = 8.dp
private val TS_TILE_MIN = 72.dp
private val TS_SMALL = 13.sp
private val TS_CHIP = 11.sp
private val TS_LABEL = 10.sp
private val TS_CHIPS_MIN: Dp = 260.dp   // shorter top screens drop the chips first
private val TS_SHELF_MIN: Dp = 170.dp   // then the shelf, after it has shrunk to a sliver

/**
 * A tap that only taps: one finger, up within the touch slop of where it went down. A drag or a second finger is not
 * one, and nothing is consumed, so a swipe over the tile does what it did before.
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
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) clean = false
                if (change.changedToUp()) {
                    if (clean) action.value()
                    break
                }
                if (!change.pressed) break
            }
        }
    }
}
