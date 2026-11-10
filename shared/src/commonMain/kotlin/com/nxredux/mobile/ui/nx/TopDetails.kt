package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.achievements.RaAchievement
import com.nxredux.mobile.achievements.RaAchievementState
import com.nxredux.mobile.achievements.RaProgress
import com.nxredux.mobile.library.path
import com.nxredux.mobile.scraper.StoredGameInfo
import com.nxredux.mobile.settings.BackdropArt
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.ui.GameInfo
import com.nxredux.mobile.ui.INFO_SEPARATOR
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.RaPanel
import com.nxredux.mobile.ui.RaPanelContent
import com.nxredux.mobile.ui.abstractArtFor
import com.nxredux.mobile.ui.nowMs
import com.nxredux.mobile.ui.panelArt
import com.nxredux.mobile.ui.whenText

/** What the top screen's details know of the game's achievements: nothing, the downloading state, or its [Rows]. */
sealed interface DetailsBody {
    data object None : DetailsBody
    data object Downloading : DetailsBody
    data class Rows(val achievements: List<RaAchievement>, val badges: Map<String, String>) : DetailsBody
}

/** [ra] when it is [entry]'s (its feed may lag the highlight by a moment, or still be another game's); else null. */
internal fun detailsRaFor(entry: Listed, ra: RaPanel?): RaPanel? = ra?.takeIf { it.path == entry.game.path }

/** [ra]'s body: the achievement rows of a game with any, the downloading state while they download, else nothing (signed out, no data). */
internal fun detailsBody(ra: RaPanel?): DetailsBody = when (val c = ra?.content) {
    is RaPanelContent.Game -> if (c.achievements.isEmpty()) DetailsBody.None else DetailsBody.Rows(c.achievements, c.badges)
    RaPanelContent.Downloading -> DetailsBody.Downloading
    else -> DetailsBody.None
}

/** Scraped facts about a game; until a scraper fills them only debug builds show placeholders ([gameMeta]). */
data class GameMeta(val genre: String, val year: String)

private val PLACEHOLDER_META = GameMeta(genre = "[Genre]", year = "[Year]")

/** The details page's facts: a visible placeholder when [placeholderMeta] (debug), else none, so release never shows fake facts. */
internal fun gameMeta(placeholderMeta: Boolean): GameMeta? = if (placeholderMeta) PLACEHOLDER_META else null

/**
 * The top screen's page for the highlighted game: its play history ([info]), resume save, achievements, scraped
 * facts ([meta]) and its stored ScreenScraper info ([scraped], null until read or fetched).
 */
data class TopDetails(
    val entry: Listed,
    val info: GameInfo?,
    val resume: ResumeInfo,
    val achievements: DetailsBody,
    val meta: GameMeta?,
    val scraped: StoredGameInfo? = null,
)

/** "RPG · 2004 · Game Boy Advance" with scraped facts, else the console's name alone. */
internal fun genreLine(meta: GameMeta?, console: String): String =
    listOfNotNull(meta?.genre, meta?.year, console).joinToString(INFO_SEPARATOR)

/**
 * The play line at [now]: "41 m played · 25 sessions · last played today", each part left out without data (the first
 * capitalised), "Never played" with none.
 */
internal fun playLine(info: GameInfo?, now: Long): String {
    val parts = listOfNotNull(
        info?.totalMs?.takeIf { it > 0 }?.let { "${hoursText(it)} played" },
        info?.plays?.takeIf { it > 0 }?.let { if (it == 1L) "1 session" else "$it sessions" },
        info?.lastPlayedAt?.let { "last played " + whenText(now, it).lowercase() },
    )
    return if (parts.isEmpty()) "Never played" else parts.joinToString(INFO_SEPARATOR).replaceFirstChar { it.uppercase() }
}

/** The achievements block: [unlocked] (pending counted) of [total], and the first locked one's title ([next], null when all are done). */
data class DetailsAchievements(val unlocked: Int, val total: Int, val next: String?)

/** From the game's [body] rows, else the menus' cached [progress] while they download; null for a game without a set. */
internal fun detailsAchievements(body: DetailsBody, progress: RaProgress?): DetailsAchievements? = when (body) {
    is DetailsBody.Rows -> DetailsAchievements(
        body.achievements.count { it.state != RaAchievementState.LOCKED }, body.achievements.size,
        body.achievements.firstOrNull { it.state == RaAchievementState.LOCKED }?.title,
    )
    else -> progress?.takeIf { it.total > 0 }?.let { DetailsAchievements(it.unlocked, it.total, it.next) }
}

/** The page's picture: the [resume] save's, else the scraped [screenshot], else the [box] art, else a placeholder. */
internal fun detailsImage(resume: String?, screenshot: String?, box: String?): ShelfImage =
    if (resume != null) ShelfImage.Shot(resume) else shelfImage(screenshot, null, box)

/** "Continue · yesterday" under [image] when it is the [resume] picture (saved at [savedAt], when known); else none. */
internal fun resumeCaption(image: ShelfImage, resume: String?, savedAt: Long?, now: Long): String? {
    if (resume == null || image != ShelfImage.Shot(resume)) return null
    return listOfNotNull("Continue", savedAt?.let { whenText(now, it).lowercase() }).joinToString(INFO_SEPARATOR)
}

/** What a top screen [width] x [height] (inside its padding) keeps: a small one drops the picture first, then the next goal. */
internal data class DetailsFit(val picture: Boolean, val next: Boolean)

internal fun detailsFit(width: Dp, height: Dp): DetailsFit =
    DetailsFit(picture = width >= TD_PICTURE_MIN_W && height >= TD_PICTURE_MIN_H, next = height >= TD_NEXT_MIN_H)

/**
 * The top screen's game details, in the stats page's language: on the left, anchored to the bottom, the title, the
 * genre line, the play line and the achievements block (a count, a bar and the next goal); on the right, one 16:9
 * picture ([detailsImage]) captioned "Continue · <when>" when it is the resume save's. Nothing scrolls; a small screen
 * drops the picture, then the next goal ([detailsFit]). [onAchievements]: the achievements block's clean tap, the only
 * touch here. [prefer]: Layouts → Backdrop art, for a game whose only picture is its box.
 */
@Composable
fun TopDetailsPage(details: TopDetails, onAchievements: () -> Unit, prefer: BackdropArt = BackdropArt.BOX3D) {
    val colors = LocalNxColors.current
    val now = remember(details) { nowMs() }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val pad = topPagePadding(maxWidth, maxHeight)
        val gap = maxWidth * TD_COLUMN_GAP
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val fit = detailsFit(maxWidth, maxHeight)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                DetailsText(details, now, fit.next, onAchievements, Modifier.weight(TD_TEXT_SHARE))
                if (fit.picture) {
                    Spacer(Modifier.width(gap))
                    DetailsPicture(details, now, prefer, Modifier.weight(1f - TD_TEXT_SHARE))
                }
            }
        }
    }
}

@Composable
private fun DetailsText(details: TopDetails, now: Long, next: Boolean, onAchievements: () -> Unit, modifier: Modifier) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
        Text(
            details.entry.shown, color = colors.listText, fontFamily = font, fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeightStyle = TOP_TIGHT_LINES),
        )
        Text(
            genreLine(details.meta, details.entry.system.displayName), color = colors.hint, fontFamily = font, fontSize = 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(lineHeightStyle = TOP_TIGHT_LINES), modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            playLine(details.info, now), color = colors.listText, fontFamily = font, fontSize = 18.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp),
        )
        detailsAchievements(details.achievements, details.info?.ra)?.let { a ->
            Column(Modifier.padding(top = 20.dp).fillMaxWidth().cleanTap(onAchievements)) {
                Text(
                    "ACHIEVEMENTS$INFO_SEPARATOR${a.unlocked} OF ${a.total}", color = colors.hint, fontFamily = font, fontSize = 12.sp,
                    letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.inkFaint)) {
                    if (a.unlocked > 0) Box(Modifier.fillMaxWidth(a.unlocked.toFloat() / a.total).fillMaxHeight().background(colors.listText))
                }
                if (next && a.next != null) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.hint)) { append("Next: ") }
                            append(a.next)
                        },
                        color = colors.listText, fontFamily = font, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsPicture(details: TopDetails, now: Long, prefer: BackdropArt, modifier: Modifier) {
    val colors = LocalNxColors.current
    val e = details.entry
    val resume = details.resume.previewPath
    val image = detailsImage(resume, e.game.screenshotRef?.value, panelArt(prefer, e.game) ?: abstractArtFor(e))
    Column(modifier) {
        TopPicture(e, image, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
        resumeCaption(image, resume, details.resume.savedAt, now)?.let { caption ->
            val (head, tail) = caption.substringBefore(INFO_SEPARATOR) to caption.substringAfter(INFO_SEPARATOR, "")
            Text(
                buildAnnotatedString {
                    append(head)
                    if (tail.isNotEmpty()) withStyle(SpanStyle(color = colors.hint)) { append(INFO_SEPARATOR + tail) }
                },
                color = colors.listText, fontFamily = LocalNxFont.current, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private const val TD_TEXT_SHARE = 0.55f          // the text column's share of the row, the picture's the rest
private const val TD_COLUMN_GAP = 0.05f          // between the columns, of the screen's width
private val TD_PICTURE_MIN_W: Dp = 400.dp        // narrower: the text takes the width
private val TD_PICTURE_MIN_H: Dp = 220.dp        // shorter: the picture goes first
private val TD_NEXT_MIN_H: Dp = 176.dp           // then the next goal
