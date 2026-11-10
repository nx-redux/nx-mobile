package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.gametime.GameTimeFormat
import com.nxredux.mobile.library.path
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.StateShot
import com.nxredux.mobile.ui.FileFacts
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import com.nxredux.mobile.ui.abstractArtFor
import com.nxredux.mobile.ui.appId
import com.nxredux.mobile.ui.formatOf
import com.nxredux.mobile.ui.nowMs
import com.nxredux.mobile.ui.panelArt
import com.nxredux.mobile.ui.raBadgeModel
import com.nxredux.mobile.ui.regionOf
import com.nxredux.mobile.ui.whenText
import org.jetbrains.compose.resources.painterResource

/** What the top screen's game details list under their header: nothing, the downloading line, or the game's achievement [Rows]. */
sealed interface DetailsBody {
    data object None : DetailsBody
    data object Downloading : DetailsBody
    data class Rows(val achievements: List<RaAchievement>, val badges: Map<String, String>) : DetailsBody
}

/** [ra] when it is [entry]'s (its feed may lag the highlight by a moment, or still be another game's); else null. */
internal fun detailsRaFor(entry: Listed, ra: RaPanel?): RaPanel? = ra?.takeIf { it.path == entry.game.path }

/** [ra]'s body: the achievement rows of a game with any, the downloading line while they download, else nothing (signed out, no data). */
internal fun detailsBody(ra: RaPanel?): DetailsBody = when (val c = ra?.content) {
    is RaPanelContent.Game -> if (c.achievements.isEmpty()) DetailsBody.None else DetailsBody.Rows(c.achievements, c.badges)
    RaPanelContent.Downloading -> DetailsBody.Downloading
    else -> DetailsBody.None
}

/** [detailsBody] with the locked achievements first (the next goal leads), each group in the list's own order. */
internal fun topAchievements(ra: RaPanel?): DetailsBody = when (val b = detailsBody(ra)) {
    is DetailsBody.Rows -> b.copy(achievements = b.achievements.partition { it.state == RaAchievementState.LOCKED }.let { (l, r) -> l + r })
    else -> b
}

/** Scraped facts about a game; until a scraper fills them only debug builds show placeholders ([gameMeta]). */
data class GameMeta(val genre: String, val year: String, val publisher: String, val synopsis: String)

private val PLACEHOLDER_META = GameMeta(
    genre = "[Genre]", year = "[Year]", publisher = "[Publisher]",
    synopsis = "[Placeholder synopsis] A short description of the game goes here once game facts are scraped. " +
        "Only debug builds show this text, so the layout can be judged with a synopsis in place.",
)

/** The details page's facts block: a visible placeholder when [placeholderMeta] (debug), else none, so release never shows fake facts. */
internal fun gameMeta(placeholderMeta: Boolean): GameMeta? = if (placeholderMeta) PLACEHOLDER_META else null

/**
 * The top screen's page for the highlighted game: its play history ([info]), resume save, save-state pictures ([shots],
 * newest first), file facts ([facts], null until read or on a platform without them), achievements, scraped facts
 * ([meta]), whether [netplay] can start it, and whether it has a RetroAchievements set with achievements ([raSet]).
 */
data class TopDetails(
    val entry: Listed,
    val info: GameInfo?,
    val resume: ResumeInfo,
    val shots: List<StateShot>,
    val facts: FileFacts?,
    val achievements: DetailsBody,
    val meta: GameMeta?,
    val netplay: Boolean,
    val raSet: Boolean,
)

/** What fills the page under the chips: save states and achievements side by side, one of them, or neither. */
enum class LowerArea { BOTH, STATES, ACHIEVEMENTS, NONE }

/** An Android game has no states or achievements; otherwise whichever the game has (a download still running counts as none yet). */
fun TopDetails.lowerArea(): LowerArea {
    if (entry.system.android) return LowerArea.NONE
    val states = shots.isNotEmpty()
    val achievements = achievements is DetailsBody.Rows
    return when {
        states && achievements -> LowerArea.BOTH
        states -> LowerArea.STATES
        achievements -> LowerArea.ACHIEVEMENTS
        else -> LowerArea.NONE
    }
}

/** The chips at [now]: play history, file facts, then what is ready ([readyChips]). */
fun TopDetails.chips(now: Long): List<String> = chips(now, files = true)

/** [files] false: without the file-facts chip (a small top screen drops it). */
internal fun TopDetails.chips(now: Long, files: Boolean): List<String> =
    listOfNotNull(playChip(now), fileChip().takeIf { files }) + readyChips()

/** "12h 40m · 23 sessions · Yesterday", each part left out without data; null for a game never played. */
private fun TopDetails.playChip(now: Long): String? {
    val i = info ?: return null
    return listOfNotNull(
        i.totalMs?.takeIf { it > 0 }?.let(GameTimeFormat::duration),
        i.plays?.takeIf { it > 0 }?.let { if (it == 1L) "1 session" else "$it sessions" },
        i.lastPlayedAt?.let { whenText(now, it) },
    ).takeIf { it.isNotEmpty() }?.joinToString(INFO_SEPARATOR)
}

/** "USA · ZIP 512 KB": the region, then the format and size; null for an Android game or a file with neither. */
private fun TopDetails.fileChip(): String? {
    if (entry.system.android) return null
    val name = entry.game.fileName
    val file = listOfNotNull(formatOf(name).takeIf { it.isNotEmpty() }, facts?.sizeBytes?.let(::sizeText)).joinToString(" ")
    return listOfNotNull(regionOf(name), file.takeIf { it.isNotEmpty() }).takeIf { it.isNotEmpty() }?.joinToString(INFO_SEPARATOR)
}

/** Cheats, a hi-res texture pack, a RetroAchievements set (when the achievements are not listed below), netplay. */
private fun TopDetails.readyChips(): List<String> = listOfNotNull(
    "Cheats".takeIf { facts?.cheats == true },
    "Hi-res textures".takeIf { facts?.hiresPack == true },
    "RetroAchievements".takeIf { raSet && achievements !is DetailsBody.Rows },
    "Netplay".takeIf { netplay },
)

/** "512 B", "512 KB", "1.5 MB", "2 MB", "4.3 GB": whole kilobytes, one decimal (dropped when zero) from a megabyte up. */
internal fun sizeText(bytes: Long): String {
    fun oneDecimal(v: Long, unit: Long): String { val t = (v * 10 + unit / 2) / unit; return if (t % 10 == 0L) "${t / 10}" else "${t / 10}.${t % 10}" }
    return when {
        bytes < KB -> "$bytes B"
        bytes < MB -> "${(bytes + KB / 2) / KB} KB"
        bytes < GB -> "${oneDecimal(bytes, MB)} MB"
        else -> "${oneDecimal(bytes, GB)} GB"
    }
}

private const val KB = 1024L
private const val MB = KB * 1024
private const val GB = MB * 1024

/** What a top screen [height] tall (inside its padding) keeps of the header: a short one drops the synopsis first, then the file chip. */
internal data class DetailsFit(val synopsis: Boolean, val fileChip: Boolean)

internal fun detailsFit(height: Dp): DetailsFit = DetailsFit(synopsis = height >= TD_SYNOPSIS_MIN, fileChip = height >= TD_FILE_CHIP_MIN)

/** A save state's name: "Auto" for the auto slot, else "Slot N" as the in-game menu names it. */
internal fun slotName(slot: Int): String = if (slot == NxPaths.AUTO_SLOT) "Auto" else "Slot $slot"

/**
 * The top screen's game details (layout "A"): box art (an Android game's app icon) with the resume picture under it on
 * the left; title, the facts line, synopsis and chips on the right, then the lower area ([lowerArea]): save states left,
 * achievements right, one alone the full width (achievements in two columns), neither: the art grows, the text centres
 * and the synopsis gets more lines. Nothing scrolls or takes a touch: lists show what fits and fade at the bottom. On a
 * short screen the synopsis goes first, then the file chip; the lower area takes what is left. [prefer]: Layouts →
 * Backdrop art.
 */
@Composable
fun TopDetailsPage(details: TopDetails, prefer: BackdropArt = BackdropArt.BOX3D) {
    val colors = LocalNxColors.current
    val now = remember(details) { nowMs() }
    val area = details.lowerArea()
    val none = area == LowerArea.NONE
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background).padding(TD_PAD)) {
        val (synopsis, fileChip) = detailsFit(maxHeight)
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(TD_PAD)) {
            Column(
                Modifier.fillMaxWidth(if (none) 0.36f else 0.30f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(TD_GAP, if (none) Alignment.CenterVertically else Alignment.Top),
            ) {
                DetailsArt(details, prefer, if (none) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.fillMaxWidth().weight(1f))
                if (!none) ResumePicture(details, now, Modifier.fillMaxWidth().weight(0.45f))
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = if (none) Arrangement.Center else Arrangement.Top,
            ) {
                DetailsHeader(details, now, synopsisLines = if (!synopsis) 0 else if (none) 7 else 3, fileChip = fileChip, large = none)
                if (!none) {
                    Spacer(Modifier.height(TD_GAP))
                    LowerAreaContent(details, area, now, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DetailsArt(details: TopDetails, prefer: BackdropArt, modifier: Modifier) {
    val colors = LocalNxColors.current
    val e = details.entry
    Box(modifier.clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        val icon = rememberAppIcon(e.appId)
        val logo = consoleLogo(e.system.tag)
        val art = panelArt(prefer, e.game)
        when {
            icon != null -> Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.7f))
            art != null -> AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            e.system.android -> abstractArtFor(e)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            logo != null -> Image(painterResource(logo), contentDescription = null, modifier = Modifier.fillMaxSize(0.6f), colorFilter = ColorFilter.tint(colors.listText))
            else -> abstractArtFor(e)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
    }
}

/** The resume save's picture captioned "Continue · <when>"; nothing without a resume save. */
@Composable
private fun ResumePicture(details: TopDetails, now: Long, modifier: Modifier) {
    val r = details.resume
    val slot = r.slot ?: return
    val shot = details.shots.firstOrNull { it.slot == slot }
    val picture = r.previewPath ?: shot?.uri ?: return
    val caption = listOfNotNull("Continue", shot?.let { whenText(now, it.savedAt) }).joinToString(INFO_SEPARATOR)
    Shot(picture, caption, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeader(details: TopDetails, now: Long, synopsisLines: Int, fileChip: Boolean, large: Boolean) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val meta = details.meta
    Text(
        details.entry.shown, color = colors.listText, fontFamily = font, fontSize = if (large) 34.sp else 28.sp,
        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    val facts = listOfNotNull(meta?.genre, meta?.year, meta?.publisher, details.entry.system.displayName).joinToString(INFO_SEPARATOR)
    Text(facts, color = colors.hint, fontFamily = font, fontSize = TD_SMALL, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (meta != null && synopsisLines > 0) {
        Text(
            meta.synopsis, color = colors.listText, fontFamily = font, fontSize = if (large) 20.sp else TD_SMALL,
            lineHeight = if (large) 28.sp else 22.sp, maxLines = synopsisLines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    val chips = details.chips(now, files = fileChip)
    if (chips.isNotEmpty()) {
        FlowRow(
            Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            maxLines = if (large) 3 else 2,
        ) { chips.forEach { Chip(it) } }
    }
}

@Composable
private fun Chip(text: String) {
    val colors = LocalNxColors.current
    Text(
        text, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = TD_CHIP, maxLines = 1,
        modifier = Modifier.border(1.dp, colors.inkDim, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp),
    )
}

@Composable
private fun LowerAreaContent(details: TopDetails, area: LowerArea, now: Long, modifier: Modifier) {
    val rows = details.achievements as? DetailsBody.Rows
    when (area) {
        LowerArea.BOTH -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatesSection(details.shots, now, columns = 2, Modifier.weight(1f).fillMaxHeight())
            AchievementsSection(rows!!, columns = 1, Modifier.weight(1.3f).fillMaxHeight())
        }
        LowerArea.STATES -> StatesSection(details.shots, now, columns = 4, modifier)
        LowerArea.ACHIEVEMENTS -> AchievementsSection(rows!!, columns = 2, modifier)
        LowerArea.NONE -> Unit
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = LocalNxColors.current.hint, fontFamily = LocalNxFont.current, fontSize = TD_LABEL,
        letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/** The game's save states, newest first, [columns] to a row; what does not fit fades at the bottom. */
@Composable
private fun StatesSection(shots: List<StateShot>, now: Long, columns: Int, modifier: Modifier) {
    Column(modifier) {
        SectionLabel("Save states")
        Spacer(Modifier.height(8.dp))
        FadingColumn(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(8.dp)) {
            shots.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        Shot(s.uri, slotName(s.slot) + INFO_SEPARATOR + whenText(now, s.savedAt), Modifier.weight(1f).aspectRatio(4f / 3f))
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** "Achievements · N of M" (pending counted as unlocked), a progress bar, then the rows in [columns], read across. */
@Composable
private fun AchievementsSection(rows: DetailsBody.Rows, columns: Int, modifier: Modifier) {
    val colors = LocalNxColors.current
    val all = rows.achievements
    val unlocked = all.count { it.state != RaAchievementState.LOCKED }
    Column(modifier) {
        SectionLabel("Achievements$INFO_SEPARATOR$unlocked of ${all.size}")
        Box(Modifier.padding(vertical = 7.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.inkFaint)) {
            if (unlocked > 0) Box(Modifier.fillMaxWidth(unlocked.toFloat() / all.size).fillMaxHeight().background(colors.listText))
        }
        // Only the first rows can show without scrolling: never compose the rest.
        val shown = all.take(TD_MAX_ROWS * columns)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            (0 until columns).forEach { c ->
                FadingColumn(Modifier.weight(1f).fillMaxHeight(), Arrangement.Top) {
                    // The in-game panel's rows, grown as a whole (badge, text, padding) to the top screen's larger text.
                    val d = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(d.density * TD_ROW_SCALE, d.fontScale)) {
                        shown.filterIndexed { i, _ -> i % columns == c }.forEach { a ->
                            AchievementRow(a.title, a, raBadgeModel(rows.badges, a), selected = false, Modifier)
                        }
                    }
                }
            }
        }
    }
}

/** A column that never scrolls (no input reaches it) but fades at the bottom while its content runs past the edge. */
@Composable
private fun FadingColumn(modifier: Modifier, arrangement: Arrangement.Vertical, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier.clipToBounds().nxScrollFade(scroll, height = TD_FADE, strong = true).verticalScroll(scroll, enabled = false),
        verticalArrangement = arrangement, content = content,
    )
}

/** A save-state picture with its [caption] on a dark tag at the bottom-left. */
@Composable
private fun Shot(uri: String, caption: String, modifier: Modifier) {
    val colors = LocalNxColors.current
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(colors.inkFaint)) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Text(
            caption, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = TD_CAPTION, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))   // role: a caption's plate over a picture
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

private val TD_PAD = 20.dp
private val TD_GAP = 12.dp
private val TD_SMALL = 16.sp
private val TD_CHIP = 13.sp
private val TD_LABEL = 12.sp
private val TD_CAPTION = 13.sp
private val TD_FADE = 40.dp
private const val TD_ROW_SCALE = 1.4f        // the achievement rows' growth over the in-game panel's
private val TD_SYNOPSIS_MIN: Dp = 390.dp     // shorter top screens drop the synopsis first
private val TD_FILE_CHIP_MIN: Dp = 290.dp    // then the file chip
private const val TD_MAX_ROWS = 12           // more rows than any top screen fits
