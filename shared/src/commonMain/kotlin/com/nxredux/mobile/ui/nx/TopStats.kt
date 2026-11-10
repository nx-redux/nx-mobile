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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

/** A game on the stats page's shelf: its play time ([ms], 0 on a newest-added shelf), when it was [added], its [resume] picture once read. */
data class ShelfGame(val entry: Listed, val ms: Long, val added: Long?, val resume: String? = null)

/** One of the page's big numbers: the [value] over its dim [caption]. */
data class StatNumber(val value: String, val caption: String)

/**
 * The top screen's page for a highlighted console or collection ([key]: which, [statsKey]): its [title], two or three
 * [numbers], the [bios] pill (a console's only) and a [shelf] of up to four games, the most played or else the
 * [newest] added.
 */
data class TopStats(
    val key: String,
    val title: String,
    val numbers: List<StatNumber>,
    val bios: BiosMissing?,
    val shelf: List<ShelfGame>,
    val newest: Boolean,
)

/** Which stats page [top] shows, as [TopStats.key]; null for any other top content. */
fun statsKey(top: TopContent): String? = when (top) {
    is TopContent.ConsoleStats -> consoleKey(top.systems)
    is TopContent.CollectionStats -> collectionKey(top.name)
    else -> null
}

private fun consoleKey(systems: List<SystemFolder>) = "console:" + systems.joinToString(",") { it.tag }
private fun collectionKey(name: String) = "collection:$name"

/** [systems]' page over their [entries]: games and how many are unplayed, play time, achievements earned. */
fun consoleStats(systems: List<SystemFolder>, entries: List<Listed>, info: Map<String, GameInfo>, bios: BiosMissing? = null): TopStats {
    val t = tally(entries, info)
    val earned = t.ra.sumOf { it.unlocked }
    val (newest, shelf) = shelf(entries, info)
    return TopStats(
        key = consoleKey(systems),
        title = systems.first().displayName,
        numbers = listOfNotNull(
            gamesNumber(entries.size, "${t.neverPlayed} unplayed".takeIf { t.neverPlayed > 0 }),
            timeNumber(t),
            StatNumber("$earned", if (earned == 1) "achievement" else "achievements").takeIf { earned > 0 },
        ),
        bios = bios, shelf = shelf, newest = newest,
    )
}

/** Collection [name]'s page over its [entries]: games and the consoles they span, play time, games finished (every achievement). */
fun collectionStats(name: String, entries: List<Listed>, info: Map<String, GameInfo>): TopStats {
    val t = tally(entries, info)
    // A merged console (GBA and MGBA) is one console.
    val consoles = entries.map { it.system.displayName }.distinct().size
    val finished = t.ra.count { it.total > 0 && it.unlocked >= it.total }
    val (newest, shelf) = shelf(entries, info)
    return TopStats(
        key = collectionKey(name), title = name,
        numbers = listOfNotNull(
            gamesNumber(entries.size, if (consoles == 1) "1 console" else "$consoles consoles"),
            timeNumber(t),
            StatNumber("$finished", "finished").takeIf { finished > 0 },
        ),
        bios = null, shelf = shelf, newest = newest,
    )
}

private fun gamesNumber(games: Int, note: String?) =
    StatNumber("$games", listOfNotNull(if (games == 1) "game" else "games", note).joinToString(INFO_SEPARATOR))

private fun timeNumber(t: Tally) =
    StatNumber(hoursText(t.totalMs), "played" + INFO_SEPARATOR + if (t.sessions == 1L) "1 session" else "${t.sessions} sessions")

/** The BIOS pill's words; null (no pill) when nothing is missing. */
internal fun biosPill(bios: BiosMissing?): String? = when (bios) {
    BiosMissing.REQUIRED -> "BIOS missing"
    BiosMissing.OPTIONAL -> "BIOS missing (optional)"
    null -> null
}

private class Tally(val neverPlayed: Int, val totalMs: Long, val sessions: Long, val ra: List<RaProgress>)

private fun tally(entries: List<Listed>, info: Map<String, GameInfo>): Tally {
    var never = 0; var total = 0L; var sessions = 0L
    val ra = ArrayList<RaProgress>()
    for (e in entries) {
        val i = info[e.game.path]
        val ms = i?.totalMs ?: 0L
        val plays = i?.plays ?: 0L
        if (ms <= 0 && plays <= 0 && i?.lastPlayedAt == null) never++
        total += ms.coerceAtLeast(0)
        sessions += plays.coerceAtLeast(0)
        i?.ra?.let(ra::add)
    }
    return Tally(never, total, sessions, ra)
}

/** Most played (by play time) when anything was; else the newest added (true), so the shelf is never empty. */
private fun shelf(entries: List<Listed>, info: Map<String, GameInfo>): Pair<Boolean, List<ShelfGame>> {
    val played = entries.mapNotNull { e -> info[e.game.path]?.totalMs?.takeIf { it > 0 }?.let { e to it } }
    if (played.isNotEmpty()) {
        return false to played.sortedWith(compareByDescending<Pair<Listed, Long>> { it.second }.thenBy { it.first.shown.lowercase() })
            .take(SHELF_MAX).map { (e, ms) -> ShelfGame(e, ms, e.game.firstSeen) }
    }
    return true to entries.sortedWith(compareByDescending<Listed, Long?>(nullsFirst()) { it.game.firstSeen }.thenBy { it.shown.lowercase() })
        .take(SHELF_MAX).map { e -> ShelfGame(e, 0, e.game.firstSeen) }
}

/** The hero's caption: "Minish Cap · 24 h · most played", or "Tetris · added yesterday · newest" on a [newest] shelf. */
internal fun heroCaption(game: ShelfGame, newest: Boolean, now: Long): String = listOfNotNull(
    game.entry.shown,
    if (newest) game.added?.let { "added " + whenText(now, it).replaceFirstChar { c -> c.lowercaseChar() } } else hoursText(game.ms),
    if (newest) "newest" else "most played",
).joinToString(INFO_SEPARATOR)

/** "86 h" from an hour up, else "45 m" (under a minute: "0 m"). */
internal fun hoursText(ms: Long): String {
    val m = ms.coerceAtLeast(0) / 60_000L
    return if (m >= 60) "${m / 60} h" else "$m m"
}

/** What a shelf tile draws: a picture filling its 16:9, or a box art fitted over a blurred copy of itself. */
sealed interface ShelfImage {
    data class Shot(val url: String) : ShelfImage
    data class Box(val url: String) : ShelfImage
    data object None : ShelfImage
}

/** The scraped [screenshot], else the [resume] save's picture, else the [box] art. */
internal fun shelfImage(screenshot: String?, resume: String?, box: String?): ShelfImage = when {
    screenshot != null -> ShelfImage.Shot(screenshot)
    resume != null -> ShelfImage.Shot(resume)
    box != null -> ShelfImage.Box(box)
    else -> ShelfImage.None
}

/** The shelf in a [width] x [height] space: the hero's width, and whether the strips fit under it. */
internal data class ShelfFit(val heroWidth: Dp, val strips: Boolean)

/**
 * The largest 16:9 hero [width] x [height] holds over its caption, with the strips (each a third of its width less the
 * gaps, 16:9) while it stays [TS_HERO_STRIPS_MIN] tall, else alone while [TS_HERO_MIN] tall; null: no picture fits.
 */
internal fun shelfFit(width: Dp, height: Dp): ShelfFit? {
    // hero 9/16 w + caption + gap + strips (w - 2 gaps) * 3/16 = 12/16 w + caption + 10/16 gap
    val withStrips = minOf(width, (height - TS_CAPTION - TS_STRIP_GAP * 10 / 16) * 16 / 12)
    if (withStrips * 9 / 16 >= TS_HERO_STRIPS_MIN) return ShelfFit(withStrips, strips = true)
    val alone = minOf(width, (height - TS_CAPTION) * 16 / 9)
    return if (alone * 9 / 16 >= TS_HERO_MIN) ShelfFit(alone, strips = false) else null
}

private const val SHELF_MAX = 4

/**
 * The top screen's console / collection stats: the title, the big numbers and the BIOS pill in a column on the left;
 * the shelf on the right, its first game one large picture captioned under it, the next ones small strips below.
 * Nothing scrolls: a short screen drops the strips, then the picture ([shelfFit]). [onLaunch]: a picture's clean tap.
 * [prefer]: Layouts → Backdrop art, for a game whose only picture is its box.
 */
@Composable
fun TopStatsPage(stats: TopStats, onLaunch: (Listed) -> Unit, prefer: BackdropArt = BackdropArt.BOX3D) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val now = remember(stats) { nowMs() }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val vPad = minOf(maxWidth * 0.07f, maxHeight * 0.08f)
        val gap = (maxHeight * 0.045f).coerceIn(4.dp, 16.dp)
        val pageWidth = maxWidth
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(pageWidth * 0.38f).fillMaxHeight().padding(start = pageWidth * 0.07f, top = vPad, bottom = vPad),
                verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
            ) {
                Text(stats.title, color = colors.listText, fontFamily = font, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                stats.numbers.forEach { n ->
                    Column {
                        Text(n.value, color = colors.listText, fontFamily = font, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.caption, color = colors.hint, fontFamily = font, fontSize = TS_CAPTION_TEXT, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                biosPill(stats.bios)?.let { pill ->
                    Text(
                        pill, color = colors.alertText, fontFamily = font, fontSize = TS_CAPTION_TEXT, maxLines = 1,
                        modifier = Modifier.border(1.dp, colors.alertLine, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
            }
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxHeight().padding(start = pageWidth * 0.03f, end = pageWidth * 0.06f, top = vPad, bottom = vPad),
                contentAlignment = Alignment.CenterStart,
            ) {
                val hero = stats.shelf.firstOrNull()
                val fit = shelfFit(maxWidth, maxHeight)
                if (hero != null && fit != null) Shelf(stats, hero, fit, now, prefer, onLaunch)
            }
        }
    }
}

@Composable
private fun Shelf(stats: TopStats, hero: ShelfGame, fit: ShelfFit, now: Long, prefer: BackdropArt, onLaunch: (Listed) -> Unit) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val caption = heroCaption(hero, stats.newest, now)
    val name = hero.entry.shown
    Column(Modifier.width(fit.heroWidth)) {
        ShelfPicture(hero, prefer, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).cleanTap { onLaunch(hero.entry) })
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.listText)) { append(name) }
                withStyle(SpanStyle(color = colors.hint)) { append(caption.removePrefix(name)) }
            },
            fontFamily = font, fontSize = TS_HERO_TEXT, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(TS_CAPTION).wrapContentHeight(Alignment.CenterVertically),
        )
        if (fit.strips) {
            Row(Modifier.fillMaxWidth().padding(top = TS_STRIP_GAP), horizontalArrangement = Arrangement.spacedBy(TS_STRIP_GAP)) {
                val strips = stats.shelf.drop(1)
                strips.forEach { g ->
                    // role: the strips sit back from the hero ([TS_STRIP_ALPHA])
                    ShelfPicture(g, prefer, Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(5.dp)).alpha(TS_STRIP_ALPHA).cleanTap { onLaunch(g.entry) })
                }
                repeat(SHELF_MAX - 1 - strips.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tile's [shelfImage]: a picture cropped to 16:9, or the box fitted over a blurred, darkened copy of itself; an app's icon. */
@Composable
private fun ShelfPicture(game: ShelfGame, prefer: BackdropArt, modifier: Modifier) {
    val e = game.entry
    Box(modifier.background(LocalNxColors.current.pictureGround), contentAlignment = Alignment.Center) {
        val icon = rememberAppIcon(e.appId)
        when (val image = shelfImage(e.game.screenshotRef?.value, game.resume, panelArt(prefer, e.game) ?: abstractArtFor(e))) {
            is ShelfImage.Shot -> AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            is ShelfImage.Box -> {
                AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(TS_BLUR))
                Box(Modifier.fillMaxSize().background(BOX_DIM))
                AsyncImage(model = image.url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            ShelfImage.None -> if (icon != null) Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.6f))
        }
    }
}

private val TS_CAPTION_TEXT = 10.sp
private val TS_HERO_TEXT = 12.sp
private val TS_CAPTION: Dp = 22.dp   // the hero's caption line, its space above included
private val TS_STRIP_GAP: Dp = 6.dp
private val TS_HERO_STRIPS_MIN: Dp = 90.dp   // a shorter hero drops the strips
private val TS_HERO_MIN: Dp = 60.dp          // a shorter one is not drawn
private const val TS_STRIP_ALPHA = 0.8f   // role: the strips sit back from the hero
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
