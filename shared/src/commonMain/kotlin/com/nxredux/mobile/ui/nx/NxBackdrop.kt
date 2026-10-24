package com.nxredux.mobile.ui.nx

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.nxredux.mobile.ui.BackdropGame
import com.nxredux.mobile.ui.BoxShown
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.InfoKind
import com.nxredux.mobile.ui.InfoLine
import com.nxredux.mobile.ui.backdropBoxShown
import com.nxredux.mobile.ui.gamesLabel
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.compose.resources.painterResource

/**
 * The shade over a Backdrop screenshot, key stops top to bottom: (position, black alpha). Eased at the top and
 * the bottom: light enough that the picture shows, with the tabs still readable (the tab band is
 * not drawn over the picture, and the labels keep their shadows) and the hint bar's own background grounding the bottom.
 */
internal val BACKDROP_SHADE_KEYS: List<Pair<Float, Float>> =
    listOf(0f to 0.85f, 0.12f to 0.60f, 0.32f to 0.20f, 0.55f to 0.28f, 0.82f to 0.62f, 1f to 0.80f)

/** The shade's gradient stops: [BACKDROP_SHADE_KEYS] eased ([easedStops]), so the linear gradient shows no kink. */
internal val BACKDROP_SHADE: List<Pair<Float, Float>> = easedStops(BACKDROP_SHADE_KEYS)

/**
 * A smooth curve through the [keys] (position, value), as linear gradient stops at most [step] apart: a monotone cubic
 * (Fritsch–Carlson), so it passes through every key, never overshoots between two of them, and flattens at a turn (the
 * darkest-to-lightest valley) instead of making a V.
 */
internal fun easedStops(keys: List<Pair<Float, Float>>, step: Float = 0.02f): List<Pair<Float, Float>> {
    if (keys.size < 3) return keys
    val n = keys.size
    val h = List(n - 1) { keys[it + 1].first - keys[it].first }
    val d = List(n - 1) { (keys[it + 1].second - keys[it].second) / h[it] }
    val m = FloatArray(n) { k ->
        when {
            k == 0 -> d[0]
            k == n - 1 -> d[n - 2]
            d[k - 1] * d[k] <= 0f -> 0f
            else -> {
                val w1 = 2 * h[k] + h[k - 1]
                val w2 = h[k] + 2 * h[k - 1]
                (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])
            }
        }
    }
    return buildList {
        for (i in 0 until n - 1) {
            val (x0, y0) = keys[i]
            val y1 = keys[i + 1].second
            val parts = ceil(h[i] / step - 1e-4f).toInt().coerceAtLeast(1)
            for (j in 0 until parts) {
                val t = j.toFloat() / parts
                val t2 = t * t
                val t3 = t2 * t
                val y = (2 * t3 - 3 * t2 + 1) * y0 + (t3 - 2 * t2 + t) * h[i] * m[i] +
                    (-2 * t3 + 3 * t2) * y1 + (t3 - t2) * h[i] * m[i + 1]
                add((if (j == 0) x0 else x0 + t * h[i]) to (if (j == 0) y0 else y))
            }
        }
        add(keys.last())
    }
}

private const val FADE_MS = 350         // the picture's crossfade
private const val SHADOW_ALPHA = 0.5f   // the box art's shadow: a black copy
private val SHADOW_DROP = 4.dp          // the copy's offset down
private val SHADOW_BLUR = 8.dp          // the copy's blur (a no-op below Android 12: a hard offset shadow there)

/**
 * The factor a Backdrop slot's content (the 60 dp tool icon, the 17 / 24 / 30 sp names) scales by: the slot's width
 * over its full-size width (logo slot 330 landscape, 250 portrait; tool slot 150 in both; collection slot 240; box art
 * slot 170, 130), never grown. The row ([NxBackdrop]) is the landscape size in both orientations, so it passes portrait
 * false; the main menu's stacks keep their own portrait slots, but for the console's (the row's, so it passes portrait
 * false too).
 */
internal fun backdropContentScale(g: RowGeometry, slot: BackdropSlot, portrait: Boolean): Float {
    val spec = when (slot) {
        BackdropSlot.TOOL -> TOOL_SLOT_W
        BackdropSlot.COLLECTION -> COLLECTION_SLOT_W
        BackdropSlot.LOGO -> if (portrait) 250f else 330f
        BackdropSlot.BOX -> if (portrait) 130f else 170f
    }
    return min(1f, g.itemW / spec)
}

/** What one Backdrop item draws: a game's box art ([BoxArt]) or a tile ([SlotTile]). */
internal sealed interface BackdropItem {
    data class Box(val game: BackdropGame) : BackdropItem
    data class Tile(val tile: GridTile) : BackdropItem
}

/** Item [i] of a Backdrop row: its game from a games row ([games]), else its tile from the plain row of [tiles]; null past it. */
internal fun backdropItem(games: List<BackdropGame>?, tiles: List<GridTile>, i: Int): BackdropItem? =
    if (games != null) games.getOrNull(i)?.let(BackdropItem::Box) else tiles.getOrNull(i)?.let(BackdropItem::Tile)

/** A tool's name lines in a slot: one in the wide logo slot, two in the tool (and collection) slot. */
internal fun backdropToolNameLines(slot: BackdropSlot): Int = if (slot == BackdropSlot.LOGO) 1 else 2

/**
 * Which slot a Backdrop row draws in: a games row (any game) the box art slot; a row of tools (the Tools tab) the tool
 * slot; a row of collections (the Collections tab) the collection slot; Consoles the logo slot.
 */
internal fun backdropSlot(games: Boolean, tiles: List<GridTile>): BackdropSlot = when {
    games -> BackdropSlot.BOX
    tiles.isNotEmpty() && tiles.all { it is GridTile.Tool } -> BackdropSlot.TOOL
    tiles.isNotEmpty() && tiles.all { it is GridTile.Collection } -> BackdropSlot.COLLECTION
    else -> BackdropSlot.LOGO
}

/**
 * A tool name's side padding in [slot], dp: [SLOT_TEXT_PAD], but [TOOL_NAME_PAD] in the 150 dp tool (and collection)
 * slot, so "Achievements" at the full 17 sp ([TOOL_LONGEST_EM]) fits whole.
 */
internal fun backdropToolNamePad(slot: BackdropSlot): Float = if (slot.toolSized) TOOL_NAME_PAD else SLOT_TEXT_PAD

/** The tool slot and the collection slot, which share its height, gap and name padding (the collection's is wider). */
private val BackdropSlot.toolSized: Boolean get() = this == BackdropSlot.TOOL || this == BackdropSlot.COLLECTION

private const val TOOL_NAME_PAD = 8f   // dp

/**
 * The row's height: the games band on a games row; the slot's own on the main menu's rows (Consoles, Collections,
 * Tools), each centred alone, the tool slot possibly taller than the band.
 */
internal fun backdropRowHeight(slot: BackdropSlot, band: RowGeometry, g: RowGeometry): Float =
    if (slot == BackdropSlot.BOX) band.itemH else g.itemH

/** A tool item's height in dp at content scale [k]: the 60 dp icon, the [gap], [lines] of 17 sp name. */
internal fun backdropToolHeight(k: Float, lines: Int, gap: Float): Float = (TOOL_ICON + gap + lines * TOOL_NAME * NAME_LINE) * k

/** A tool's icon-to-name gap in [slot], dp: 12 in the tool (and collection) slot, 6 elsewhere. */
internal fun backdropToolGap(slot: BackdropSlot): Float = if (slot.toolSized) TOOL_SLOT_ICON_GAP else TOOL_GAP

/**
 * A tool's name size in sp in a slot of [g] at content scale [k]: 17 × [k]; in a games
 * row's box art slot and the tool (and collection) slot, also never wider than "Achievements" ([TOOL_LONGEST_EM]) fits between the
 * slot's name padding ([backdropToolNamePad]), so "Retro Achievements" breaks between its words even on a small phone.
 */
internal fun backdropToolNameSp(g: RowGeometry, k: Float, slot: BackdropSlot): Float = when (slot) {
    BackdropSlot.LOGO -> TOOL_NAME * k
    BackdropSlot.BOX, BackdropSlot.TOOL, BackdropSlot.COLLECTION -> min(TOOL_NAME * k, (g.itemW - 2 * backdropToolNamePad(slot)) / TOOL_LONGEST_EM)
}

/** A collection's side padding in its slot, dp, for its name and its "N games" line. */
internal const val BACKDROP_COLLECTION_PAD = 8f
/** A collection's name lines; the second ends in an ellipsis when the name is longer still. */
internal const val BACKDROP_COLLECTION_LINES = 2
private const val COLLECTION_NAME = 30f       // sp, at full size
private const val COLLECTION_NAME_MIN = 17f   // sp, the floor it shrinks to
private const val COLLECTION_FIT_MARGIN = 1f   // dp the longest word keeps inside the width

/** [name]'s longest whitespace-separated word (a collection name is user text), the one that must fit a line. */
internal fun backdropLongestWord(name: String): String = name.split(Regex("\\s+")).maxByOrNull { it.length } ?: ""

/**
 * A collection name's size in sp at content scale [k]: 30 × [k], shrunk until its
 * longest word, [wordWidthDp] wide as measured at that size, fits 1 dp inside [availDp] (the slot less its
 * [BACKDROP_COLLECTION_PAD]s), so no word breaks inside; never below 17 × [k].
 */
internal fun backdropCollectionNameSp(wordWidthDp: Float, availDp: Float, k: Float): Float {
    val full = COLLECTION_NAME * k
    val room = availDp - COLLECTION_FIT_MARGIN   // pixel rounding must not push the word over and break its last letter
    if (wordWidthDp <= room || wordWidthDp <= 0f) return full
    return (full * room / wordWidthDp).coerceAtLeast(COLLECTION_NAME_MIN * k)
}

/** A slot name's side padding, dp ([SlotText]). */
internal const val SLOT_TEXT_PAD = 12f

private const val TOOL_ICON = 60f   // dp
private const val TOOL_GAP = 6f     // dp
private const val TOOL_SLOT_ICON_GAP = 12f   // dp, the tool slot's
private const val TOOL_NAME = 17f   // sp
private const val NAME_LINE = 1.15f // a slot name's line height, of its size

/** The most a Backdrop box art slot grows to: 1.3 × its full-size 170 × 150 (both orientations). */
internal const val BACKDROP_SLOT_MAX = 1.3f
/** The Backdrop box art slot's full-size height, dp ([rowGeometry]'s 170 × 150). */
internal const val BOX_SLOT_H = 150f
/** The dp a grown Backdrop block (row, gap, caption reserve) leaves free above and below it in the body. */
internal const val BACKDROP_SLOT_MARGIN = 16f
/** The dp (× f) between the Backdrop band's bottom edge, its shadow room included, and the caption block's top. */
internal const val BACKDROP_ROW_CAPTION_GAP = 18f

/**
 * The factor the Backdrop games slot (its height-fit [itemH], and its neighbours and gap with it) grows by in a body
 * [bodyH] dp tall: the block ([backdropPlacement]: the slot, its shadow room [GRID_RING_ROOM], the
 * [BACKDROP_ROW_CAPTION_GAP] × f gap, the caption reserve [captionH]) fills the body less [BACKDROP_SLOT_MARGIN] above
 * and below. Never under 1 (the height-fit slot) and never past a slot of [BACKDROP_SLOT_MAX] × the full-size
 * height. The aspect ratio is kept (the caller scales both sides).
 */
internal fun backdropSlotScale(bodyH: Float, captionH: Float, itemH: Float): Float {
    if (itemH <= 0f) return 1f
    val f = itemH / BOX_SLOT_H
    val rest = GRID_RING_ROOM + BACKDROP_ROW_CAPTION_GAP * f + captionH
    val fits = (bodyH - 2 * BACKDROP_SLOT_MARGIN - rest) / itemH
    return fits.coerceAtMost(BACKDROP_SLOT_MAX * BOX_SLOT_H / itemH).coerceAtLeast(1f)
}

/** Where a Backdrop row and its caption sit in the body, dp from the body's top: the band's top, the caption block's top and bottom. */
internal data class BackdropPlacement(val rowTop: Float, val captionTop: Float, val captionBottom: Float)

/**
 * The Backdrop row's vertical placement in a body [bodyH] dp tall: one block, the band ([slotH]) and its
 * shadow room ([GRID_RING_ROOM]), a [BACKDROP_ROW_CAPTION_GAP] × [f] gap, then the caption reserve (the name's one line
 * and two info rows, [captionH] dp), centred between the header and the hint bar (the [BACKDROP_DIM] layer keeps the
 * text readable anywhere). A null [captionH] (the main menu's rows: no caption block): the row and its
 * shadow room alone, centred; the caption's top and bottom are the block's end.
 * A body too short keeps the band [GRID_RING_ROOM] under the header.
 */
internal fun backdropPlacement(bodyH: Float, slotH: Float, captionH: Float?, f: Float): BackdropPlacement {
    val gap = if (captionH == null) 0f else BACKDROP_ROW_CAPTION_GAP * f
    val block = slotH + GRID_RING_ROOM + gap + (captionH ?: 0f)
    val rowTop = ((bodyH - block) / 2).coerceAtLeast(GRID_RING_ROOM)
    val captionTop = rowTop + slotH + GRID_RING_ROOM + gap
    return BackdropPlacement(rowTop, captionTop, captionTop + (captionH ?: 0f))
}

/**
 * A Backdrop row laid out ([backdropRowPlace]): [g] the items' geometry (the games band, or a main-menu slot's), [rowH]
 * the row's height, [f] the height-fit factor of the full-size box art slot, [place] where the row and its caption sit in the box.
 */
internal data class BackdropRowPlace(val g: RowGeometry, val rowH: Float, val f: Float, val place: BackdropPlacement)

/**
 * The Backdrop row of [slot] in a box [boxDp] tall, a games caption reserve [captionH] dp under it when [captioned]: the
 * games band fitted to the body and grown into its spare height ([backdropSlotScale]), a main-menu slot fitted
 * ([backdropSlotGeometry]), then the block placed ([backdropPlacement]). Everything is sized from the shown
 * title's body ([titleRoomSizingBody]: the box less a hidden page title's [titleRoomDp]), the block centred in the whole
 * box, so a hidden title moves it up by half its room and changes nothing else.
 */
internal fun backdropRowPlace(
    slot: BackdropSlot, captioned: Boolean, boxDp: Float, titleRoomDp: Float, captionH: Float, portraitW: Float? = null, bodyW: Float? = null,
): BackdropRowPlace {
    val body = titleRoomSizingBody(boxDp, titleRoomDp)
    val spec = rowGeometry(RowStyle.BACKDROP, false, body, BACKDROP_ROW_CAPTION_GAP + captionH)
    val band = spec.grown(backdropSlotScale(body, captionH, spec.itemH)).let { b -> bodyW?.let { b.boxCapped(it) } ?: b }
    val g = if (slot == BackdropSlot.BOX) band else backdropSlotGeometry(slot, body, portraitW = portraitW)
    val rowH = backdropRowHeight(slot, band, g)
    val f = spec.itemH / BOX_SLOT_H
    return BackdropRowPlace(g, rowH, f, backdropPlacement(boxDp, rowH, captionH.takeIf { captioned }, f))
}

/** A game list Backdrop's box art (row or stack) is never wider than this share of the body's width. */
internal const val BOX_MAX_SHARE = 0.3f

/**
 * The games band held to [BOX_MAX_SHARE] of a body [bodyW] wide: the slot no wider (so no box art drawn Fit
 * in it is), no taller than a [PLACEHOLDER_ASPECT] case that wide, the gap shrunk with the width. Never grown.
 */
internal fun RowGeometry.boxCapped(bodyW: Float): RowGeometry {
    val most = BOX_MAX_SHARE * bodyW
    if (itemW <= most || itemW <= 0f) return this
    return copy(itemW = most, itemH = min(itemH, most / PLACEHOLDER_ASPECT), gap = gap * most / itemW)
}

/** [this] row with its slot, its neighbours and its gap grown by [s] (the slot's aspect ratio kept). */
internal fun RowGeometry.grown(s: Float): RowGeometry = copy(itemW = itemW * s, itemH = itemH * s, gap = gap * s)

/**
 * A game caption's info rows (gameInfoLines' segments), the Backdrop's and the Carousel's ([carouselCaptionRows]) and
 * the vertical stacks', in the handheld's two lines (as the Brick's): the time and the achievement count
 * first, then the next goal on a row of its own; a row without data is left out. Any other segment (a game count) is
 * the first row. Each row is joined and drawn by [infoText]; a next goal too long for the caption wraps onto the room
 * under it ([fitCaptionRows]).
 */
internal fun backdropCaptionRows(lines: List<InfoLine>): List<List<InfoLine>> {
    val (next, rest) = lines.partition { it.kind == InfoKind.NEXT }
    return listOf(rest, next).filter { it.isNotEmpty() }
}

/** The screenshot a Backdrop game list's picture shows: the selected game's [url] while its page is [live]; none (black) on the page sliding out. */
internal fun backdropPictureTarget(live: Boolean, url: String?): String? = if (live) url else null

/**
 * The uniform black layer over a Backdrop game list's screenshot, under [BACKDROP_SHADE]: the text, the caption
 * included, reads anywhere, even over bright screenshots.
 */
internal const val BACKDROP_DIM = 0.65f

/** How long a Backdrop picture waits on a new screenshot while the selection may still be moving (one step's settle). */
internal const val BACKDROP_PICTURE_SETTLE_MS = 150L

/**
 * How long [NxBackdropPicture] waits before showing [next] over [shown]: a screenshot replacing a screenshot waits
 * [BACKDROP_PICTURE_SETTLE_MS], so scrolling through a list fades only to where it stops; the first screenshot and
 * going plain (no screenshot, a page sliding out) are immediate.
 */
internal fun backdropPictureDelayMs(shown: String?, next: String?): Long =
    if (shown != null && next != null && shown != next) BACKDROP_PICTURE_SETTLE_MS else 0L

/**
 * The Backdrop game tab's full-screen picture: the selected game's screenshot [url] (Crop) under a [BACKDROP_DIM] black
 * layer and the shade ([BACKDROP_SHADE], of the theme's background), crossfading (0.35 s, [NxMenuEasing]) when it changes. A null [url] (no
 * screenshot, or a page sliding out: [backdropPictureTarget]) is the plain background. A freshly composed picture
 * starts plain and fades its first screenshot in, so opening the list (or coming back to it) doesn't pop. Only the
 * screenshots crossfade; the dim and shade are drawn once above them, fading out with the picture when it goes plain,
 * and a new screenshot waits for the selection to settle ([backdropPictureDelayMs]).
 */
@Composable
fun NxBackdropPicture(url: String?, modifier: Modifier = Modifier) {
    val ground = LocalNxColors.current.background
    val shade = remember(ground) {
        Brush.verticalGradient(*BACKDROP_SHADE.map { (at, alpha) -> at to ground.copy(alpha = alpha) }.toTypedArray())
    }
    var target by remember { mutableStateOf<String?>(null) }   // black first; the url a frame later, so it fades in
    LaunchedEffect(url) {
        delay(backdropPictureDelayMs(target, url))
        target = url
    }
    val layers by animateFloatAsState(if (target != null) 1f else 0f, tween(FADE_MS, easing = NxMenuEasing), label = "backdropLayers")
    Box(modifier.fillMaxSize().background(ground)) {
        Crossfade(targetState = target, animationSpec = tween(FADE_MS, easing = NxMenuEasing), modifier = Modifier.fillMaxSize()) { shown ->
            if (shown != null) {
                AsyncImage(model = shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (layers <= 0f) return@drawBehind
                drawRect(Color.Black, alpha = BACKDROP_DIM * layers)
                drawRect(shade, alpha = layers)
            },
        )
    }
}

/**
 * The Backdrop style: a frameless row with the selection full size in the centre, the others smaller and fading with
 * distance ([rowItem]), gone from four steps out. It slides (0.3 s, [NxMenuEasing]) to keep the selection centred.
 * [games] non-null: a games row of box art cut-outs with a soft drop shadow and a placeholder box for a game without
 * box art. Null: the main menu's plain row of [tiles] on black (white console logos,
 * tool icons with their names, collection names). On a games row (only a games row has a caption block), [captionTitle] (the game's
 * name) and [captionInfo] (its info on two rows, [backdropCaptionRows]) sit under the row, [BACKDROP_ROW_CAPTION_GAP]
 * × f below it ([backdropPlacement]); the box art slot, grown into the body's spare height ([backdropSlotScale]), and
 * the caption's reserve are centred as one block between the header and the hint bar. The main menu's rows have no
 * caption block and are centred alone: the selected console's "N games" hangs 8 dp under its logo as
 * drawn ([underLogoTop]) in the accent, gliding as the logos differ; a console without a logo shows its name in the
 * slot. A collection's count is under its name ([SlotTile]). No info band.
 * A tap on a side item selects it, a tap on the centre item opens it, a long press calls [onLongPress]. A horizontal
 * drag moves the row live by its items' positions ([rowSwipeDelta]) and, on release, selects the item nearest where
 * it would come to rest ([rowSwipeTarget]).
 * Only items [rowItem] shows are drawn and take touches. Nothing here clips.
 * The picture behind is [NxBackdropPicture], drawn by the caller. [layoutKey]: the current Screen instance; a new key
 * snaps the row to its selection, as a new list does. [titleRoom]: a hidden page title's room at the top
 * of this box: sized without it, centred with it ([backdropRowPlace]); the caller's picture doesn't move.
 * [centreShift]: the row moved down this far from that centre (a main menu row centred on the screen, not its body).
 */
@Composable
fun NxBackdrop(
    games: List<BackdropGame>?,
    tiles: List<GridTile>, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    captionTitle: () -> String?, captionInfo: () -> List<InfoLine>,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    titleRoom: Dp = 0.dp,
    centreShift: Dp = 0.dp,
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val slot = backdropSlot(games != null, tiles)
        // The games caption's styles and its room: the name's one line and two info rows (kept on every tab), as drawn.
        // The name follows Appearance → Text size, the info rows at 80% of it (as the Carousel's); the room follows them,
        // so the row does too.
        val font = LocalNxFont.current
        val shadow = Shadow(colors.background, blurRadius = 6f)
        val nameSize = 18.sp * nxTextScale()
        val nameStyle = TextStyle(
            fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = nameSize, textAlign = TextAlign.Center, shadow = shadow,
        )
        val infoStyle = TextStyle(fontFamily = font, fontSize = nameSize * 0.8f, textAlign = TextAlign.Center, shadow = shadow)
        val captionH = rememberCaptionRoom(0f, nameStyle, infoStyle, infoRows = 2, nameLines = 1)

        // The games slot's band: one size in both orientations, fitted so the box, the gap and the caption reserve fit the
        // body's height, then grown into the body's spare height. The main menu's slots (logo, tool, collection) keep
        // their full size (fitted to the height), each in a row of its height. A games row, the gap and the caption
        // reserve centred as one block; a main menu row alone. Sized without a hidden title's [titleRoom], centred in the
        // whole box ([backdropRowPlace]).
        val captioned = games != null   // the main menu's rows carry no caption block
        val (g, rowH, f, place) = backdropRowPlace(slot, captioned, maxHeight.value, titleRoom.value, captionH, portraitW = maxWidth.value.takeIf { portrait }, bodyW = maxWidth.value)
        val count = games?.size ?: tiles.size
        if (g.itemW <= 1f || g.itemH <= 1f || count == 0) return@BoxWithConstraints
        val top = place.rowTop + centreShift.value

        val gutter = if (portrait) 16.dp else 24.dp
        val avail = maxWidth - gutter * 2
        val availPx = constraints.maxWidth - 2 * with(LocalDensity.current) { gutter.roundToPx() }
        val spare = (maxHeight.value - place.captionBottom - CAPTION_SPARE_MARGIN).coerceAtLeast(0f)

        // The animated position, in items ([rememberRowPosition]: a new screen or a new list snaps). The selection
        // itself is read only by FollowSelection and the caption ([NxRow]), so a step recomposes those, never the row.
        val pos = rememberRowPosition(selectedIndex, count, layoutKey, games, tiles)

        // A games row's boxes centred in the box, not the reserved block ([rowNeighbourTop],
        // [rowSelectedTop]): a neighbour by itself, the selection with its shadow room, the gap and its caption as drawn,
        // the caption's height gliding with the slide (a new screen snaps), each box between the two by its distance
        // ([rowItemTop], the scale's weight). The main menu's rows keep their place ([backdropPlacement]).
        val area = maxHeight.value
        val capGap = GRID_RING_ROOM + BACKDROP_ROW_CAPTION_GAP * f
        val nbTop = if (captioned) rowNeighbourTop(area, rowH) else top
        val glide = remember(pos) { CaptionGlide() }
        val selTop = remember(glide, captioned, area, rowH, capGap, top) {
            { if (captioned) rowSelectedTop(area, rowH, capGap, glide.value()) else top }
        }
        // The swipe band: from the highest a selected box goes (its caption's reserve and every spare line) down to the
        // neighbours' bottom, so it covers every drawn box; the main menu's, the row itself.
        val bandTop = if (captioned) min(nbTop, rowSelectedTop(area, rowH, capGap, captionH + spare)) else top
        val bandH = if (captioned) nbTop + rowH - bandTop else rowH

        val hasLongPress = onLongPress != null
        val k = backdropContentScale(g, slot, portrait = false)   // the row is the landscape size in both orientations
        // Main-menu Consoles and Collections: the unselected items fade further (Tools and game lists keep the row's).
        val look = BackdropItemLook(
            g, nbTop, bandTop, bandH, rowH, fadeFurther = games == null && fadesFurther(slot), marks = games == null, k = k,
            toolLines = backdropToolNameLines(slot), toolNameSp = backdropToolNameSp(g, k, slot), toolNamePad = backdropToolNamePad(slot),
            toolSplit = slot == BackdropSlot.TOOL, toolGap = backdropToolGap(slot),
        )
        // Tools and Collections take their content's width, up to the slot's, one gap apart (as on the handheld).
        val widths = rememberSlotWidths(if (games == null) tiles else emptyList(), slot, look)
        val rowLook = if (widths == null) look else look.copy(g = g.copy(gap = g.gap * CONTENT_SLOT_GAP_K), widths = widths)
        // A drag steps by the items' mean width (the row's widths differ; the handheld has no touch to follow).
        val swipeG = if (widths == null) g else rowLook.g.copy(itemW = widths.average().toFloat())

        // No item lit: the selection shows by its size, so a step recomposes the window only, no item.
        NxRow(count, selectedIndex, pos, swipeG, bandTop, bandH, onSelect, onOpen, onLongPress) { i, _, at, taps ->
            BackdropRowItem(i, games?.getOrNull(i), if (games == null) tiles.getOrNull(i) else null, at, rowLook, selTop, taps, hasLongPress)
        }

        // The time and the achievements, then the next goal under the name's one line.
        if (captioned) RowCaption(
            captionTitle, captionInfo, glide, nameStyle, 1, infoStyle, gutter, avail, availPx, spare, selTop, rowH + capGap, colors.listText,
        )
    }
}

/** What every item of one Backdrop row shares: its geometry and placement, its fade, and its slot's content sizes. */
internal data class BackdropItemLook(
    val g: RowGeometry, val nbTop: Float, val bandTop: Float, val bandH: Float, val rowH: Float,
    val fadeFurther: Boolean, val marks: Boolean, val k: Float,
    val toolLines: Int, val toolNameSp: Float, val toolNamePad: Float, val toolSplit: Boolean, val toolGap: Float,
    /** Each item's width at full size ([rememberSlotWidths], placed by [rowItemDxVar]); null, the slot's. */
    val widths: FloatArray? = null,
)

@Composable
private fun BoxScope.BackdropRowItem(
    i: Int, game: BackdropGame?, tile: GridTile?, at: () -> Float, look: BackdropItemLook, selTop: () -> Float,
    taps: ItemTaps, hasLongPress: Boolean,
) {
    val g = look.g
    val current by rememberUpdatedState(look)
    val shownAt by rememberUpdatedState(at)
    Box(
        Modifier.align(Alignment.Center).size(g.itemW.dp, g.itemH.dp)
            .graphicsLayer {
                val r = rowItem(RowStyle.BACKDROP, i - at(), g)
                val widths = look.widths
                translationX = (if (widths != null) rowItemDxVar(g, i, at(), widths) else r.dx).dp.toPx()
                // The row's slot at its top, the item centred in the slot as in the band (0 off a games row).
                translationY = (rowItemTop(look.nbTop, selTop(), i - at()) - look.bandTop - (look.bandH - look.rowH) / 2).dp.toPx()
                scaleX = r.scale
                scaleY = r.scale
                val a = if (look.fadeFurther) mainMenuSideAlpha(i - at()) else r.alpha
                alpha = if (r.visible) a.coerceIn(0f, 1f) else 0f
                compositingStrategy = itemCompositing(look.marks)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (game != null) BoxArt(game)
        else if (tile != null) SlotTile(
            tile, look.k, g.itemW, scale = { rowItem(RowStyle.BACKDROP, i - at(), g).scale }, toolLines = look.toolLines,
            toolNameSp = look.toolNameSp, toolNamePad = look.toolNamePad, toolSplit = look.toolSplit, toolGap = look.toolGap,
            pad = if (look.marks) PadFrame(rowPadBox(g.itemH)) { padAlpha(i - at()) } else null,
        )
        // The touch target: the item's own width (a content-sized item's, so a neighbour's edge never takes its taps).
        Box(
            Modifier.size((look.widths?.getOrNull(i) ?: g.itemW).dp, g.itemH.dp)
                .shownItemTaps(i, taps, hasLongPress) { rowItem(RowStyle.BACKDROP, i - shownAt(), current.g).visible },
        )
    }
}

/**
 * A game's box in its slot, by Layouts → Backdrop art ([backdropBoxShown]; a wheel is fitted whole, never cased). The
 * 3D chain ([boxShown]): the 3D box art cut-out, Fit, frameless, over its drop shadow (a black copy at [SHADOW_ALPHA],
 * [SHADOW_DROP] down, blurred [SHADOW_BLUR], unbounded so nothing clips it);
 * else the 2D front, else the grid, Crop-filled into the case ([BoxCover]); else the placeholder box ([BoxPlaceholder]).
 * Art that fails to load drops to the next link, so the row has no hole. While loading the slot stays blank.
 */
@Composable
internal fun BoxArt(game: BackdropGame) {
    val colors = LocalNxColors.current
    var failed by remember(game) { mutableStateOf(emptySet<String>()) }
    when (val shown = backdropBoxShown(game, failed)) {
        is BoxShown.Art3d -> {
            val url = shown.url
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colors.background),
                modifier = Modifier.fillMaxSize().offset(y = SHADOW_DROP)
                    .blur(SHADOW_BLUR, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = SHADOW_ALPHA },
            )
            AsyncImage(
                model = url, contentDescription = game.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onState = { if (it is AsyncImagePainter.State.Error) failed = failed + url },
            )
        }
        is BoxShown.Cover -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val (w, h) = placeholderBoxSize(maxWidth.value, maxHeight.value)
            BoxCover(shown.url, w, h, game.title) { failed = failed + shown.url }
        }
        is BoxShown.Wheel -> AsyncImage(
            model = shown.url, contentDescription = game.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            onState = { if (it is AsyncImagePainter.State.Error) failed = failed + shown.url },
        )
        BoxShown.Placeholder -> BoxPlaceholder(game)
    }
}

/**
 * A flat cover (a 2D front or a SteamGridDB grid) as a game case: [BoxCase] [w] x [h] dp with the cover Crop-filled
 * in under the spine strip and the border (a wide cover loses its sides). [onError]: the cover did not load.
 */
@Composable
internal fun BoxCover(url: String, w: Float, h: Float, title: String?, onError: () -> Unit) {
    BoxCase(w, h) {
        AsyncImage(
            model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            onState = { if (it is AsyncImagePainter.State.Error) onError() },
        )
    }
}

/** A placeholder box's width over its height: a portrait game case. */
internal const val PLACEHOLDER_ASPECT = 0.72f

/** A placeholder box's size (w, h) in dp: a [PLACEHOLDER_ASPECT] portrait case the slot's height, or its width if narrower. */
internal fun placeholderBoxSize(slotW: Float, slotH: Float): Pair<Float, Float> {
    val w = min(slotW, slotH * PLACEHOLDER_ASPECT)
    return w to w / PLACEHOLDER_ASPECT
}

/** A placeholder box's title size in sp: 13% of the box's width (about 14 sp in the full-size 150 dp slot), 10 to 20 sp. */
internal fun placeholderTitleSp(boxW: Float): Float = (boxW * 0.13f).coerceIn(10f, 20f)

private const val SPINE_SHARE = 0.06f      // the spine strip's share of the placeholder's width
private const val ABSTRACT_COVER_SHADE = 0.2f   // the black over an abstract cover, so its title reads

/** A Carousel count's size, sp, and the least it is drawn at under a scaled side item. */
private const val COUNT_SP = 14f
internal const val MIN_COUNT_SP = 11f

/**
 * A row or stack item's layer. The main menu's [marks] (a logo and its count, an icon and its name, a collection's name
 * and count: nothing overlaps) dim per draw ([CompositingStrategy.ModulateAlpha]): a dimmed item drawn offscreen is cut
 * to its slot, and a side console's count hangs under its logo, past the slot's bottom. Box art (a plate over its
 * shadow) keeps the group's alpha ([CompositingStrategy.Auto]).
 */
internal fun itemCompositing(marks: Boolean): CompositingStrategy = if (marks) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto

/** The extra scale a count takes inside an item drawn at [itemScale]: back up to [MIN_COUNT_SP] as drawn, never shrunk. */
internal fun countGrowth(itemScale: Float): Float = if (itemScale <= 0f) 1f else max(1f, MIN_COUNT_SP / COUNT_SP / itemScale)

/** A console count's line height at full size, dp: "N games" as [SlotTile] draws it (the font's real metrics). */
@Composable
internal fun countLineDp(): Float {
    val measurer = rememberTextMeasurer()
    val font = LocalNxFont.current
    val density = LocalDensity.current
    return remember(measurer, font, density) {
        val style = TextStyle(fontFamily = font, fontSize = COUNT_SP.sp, textAlign = TextAlign.Center)
        with(density) { measurer.measure(gamesLabel(0), style, softWrap = false, maxLines = 1).size.height.toDp().value }
    }
}
private const val PLACEHOLDER_ICON_SHARE = 0.5f   // an Android game's app icon, of the placeholder's width
private val PLACEHOLDER_CORNER = 3.dp

/**
 * A portrait game case [w] x [h] dp ([placeholderBoxSize]) over the same soft drop shadow as real box art: a dark plate
 * (the background at 85%), [content] on it, then a lighter spine strip down its left edge and a 1 dp 12% white border
 * over everything, so a cover drawn in sits under both. The caller centres it.
 */
@Composable
internal fun BoxCase(w: Float, h: Float, content: @Composable () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(PLACEHOLDER_CORNER)
    Box(Modifier.size(w.dp, h.dp)) {
        Box(
            Modifier.fillMaxSize().offset(y = SHADOW_DROP)
                .blur(SHADOW_BLUR, BlurredEdgeTreatment.Unbounded).graphicsLayer { alpha = SHADOW_ALPHA }
                .background(colors.background, shape),
        )
        Box(Modifier.fillMaxSize().clip(shape).background(colors.background.copy(alpha = 0.85f))) {
            content()
            Box(Modifier.fillMaxHeight().width((w * SPINE_SHARE).dp).background(colors.main.copy(alpha = 0.10f)))
            Box(Modifier.fillMaxSize().border(1.dp, colors.main.copy(alpha = 0.12f), shape))
        }
    }
}

/**
 * The title [BoxPlaceholder] draws in its case: none over an abstract cover (the placeholder of every game in practice,
 * the caption under the row names it); the game's title only on a bare case with nothing else to show.
 */
internal fun placeholderTitle(game: BackdropGame): String? = game.title.takeIf { game.abstractUrl == null }

/**
 * A game without box art in the Backdrop row: a [BoxCase] centred in the slot showing only the game's title, centred
 * in the case, three lines at most, sized with the box (no console logo). An Android game
 * ([BackdropGame.appId]) without a screenshot or an abstract picture shows its app icon, centred in full colour, above
 * the title. A game with no art at all ([BackdropGame.abstractUrl], an Android game's too) has its generated abstract
 * picture as the case's cover (Crop, under [ABSTRACT_COVER_SHADE] black) and no title ([placeholderTitle]: the caption
 * under the row names it).
 */
@Composable
internal fun BoxPlaceholder(game: BackdropGame) {
    val colors = LocalNxColors.current
    val cover = game.abstractUrl
    val icon = if (game.screenshotUrl == null && cover == null) rememberAppIcon(game.appId) else null
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val (w, h) = placeholderBoxSize(maxWidth.value, maxHeight.value)
        BoxCase(w, h) {
            if (cover != null) {
                AsyncImage(model = cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = ABSTRACT_COVER_SHADE)))
            }
            Column(
                Modifier.fillMaxSize().padding(start = (w * (SPINE_SHARE + 0.06f)).dp, end = (w * 0.06f).dp, top = (h * 0.07f).dp, bottom = (h * 0.07f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon != null) Image(
                        icon, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size((w * PLACEHOLDER_ICON_SHARE).dp).padding(bottom = (h * 0.03f).dp),
                    )
                    val title = placeholderTitle(game)
                    val size = placeholderTitleSp(w).sp
                    if (title != null) Text(
                        title, color = colors.listText, maxLines = if (icon != null) 2 else 3, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size,
                            lineHeight = size * 1.15f, textAlign = TextAlign.Center, lineBreak = LineBreak.Heading,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * A Consoles item in the logo slot, a collection in the collection slot, or a tool in the tool slot: no plate, no
 * frame. A console without a logo is its name. A console with a logo shows its game count ("N games", 14 sp) 8 dp under
 * the logo as drawn (its own aspect, Fit in the slot), past the slot's bottom when the logo fills it. A collection is its
 * name (30 sp, two lines) with its count 6 dp under it, one centred group. Every item shows its count, in its logo's or
 * name's colour (the ink), dimmed with the item; left out while unknown. [scale]: the item's own scale now (read while
 * drawing), so a side item's count is grown back to at least [MIN_COUNT_SP] as drawn ([countGrowth]). A tool's name:
 * [toolLines] lines at [toolNameSp] between [toolNamePad]s; [toolSplit]: its pre-split lines. [pad]: how to draw the
 * tile's controller (only a Carousel passes it; Grid never does).
 */
@Composable
internal fun SlotTile(tile: GridTile, k: Float, slotW: Float, scale: () -> Float = { 1f }, toolLines: Int = 1, toolNameSp: Float = TOOL_NAME * k, toolNamePad: Float = SLOT_TEXT_PAD, toolSplit: Boolean = false, toolGap: Float = TOOL_GAP, pad: PadFrame? = null) {
    val colors = LocalNxColors.current
    when (tile) {
        is GridTile.Logo -> {
            val res = tile.res
            if (res == null) SlotText(tile.name, 24.sp * k, maxLines = 2)
            else BoxWithConstraints(Modifier.fillMaxSize()) {
                val p = tile.pad
                if (p != null && pad != null) {
                    // Behind the logo, larger than the slot (nothing here clips: the item draws ModulateAlpha).
                    Image(
                        padPainter(p.res), contentDescription = null,
                        modifier = Modifier.align(Alignment.Center).offset(y = pad.box.dy.dp).requiredSize(pad.box.w.dp, pad.box.h.dp)
                            .graphicsLayer { alpha = pad.alpha() },
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(padColorMatrix(CAROUSEL_PAD_BRIGHTNESS * p.brightness, PAD_SATURATION))),
                    )
                }
                Image(
                    painterResource(res), contentDescription = tile.name, modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(colors.listText),
                )
                val count = tile.count
                if (count != null) {
                    val top = underLogoTop(0f, maxHeight.value, logoDrawnHeight(maxWidth.value, maxHeight.value, logoAspect(res)), 8f)
                    // Unbounded: a count wider than a narrow slot keeps its line, centred on the logo.
                    Text(
                        gamesLabel(count),
                        Modifier.align(Alignment.TopCenter).offset { IntOffset(0, top.dp.roundToPx()) }.wrapContentWidth(unbounded = true)
                            .graphicsLayer {
                                val c = countGrowth(scale())
                                scaleX = c; scaleY = c; transformOrigin = TransformOrigin(0.5f, 0f)
                            },
                        color = colors.listText, maxLines = 1, softWrap = false,
                        style = TextStyle(fontFamily = LocalNxFont.current, fontSize = COUNT_SP.sp, textAlign = TextAlign.Center),
                    )
                }
            }
        }
        is GridTile.Collection -> Column(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The longest word measured at the full size (the font's real widths: the name is user text).
            val measurer = rememberTextMeasurer()
            val font = LocalNxFont.current
            val density = LocalDensity.current
            val size = remember(tile.name, k, font, slotW, density) {
                val word = backdropLongestWord(tile.name)
                val style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = COLLECTION_NAME.sp * k)
                val wordW = if (word.isEmpty()) 0f else with(density) { measurer.measure(word, style, softWrap = false, maxLines = 1).size.width.toDp().value }
                backdropCollectionNameSp(wordW, slotW - 2 * BACKDROP_COLLECTION_PAD, k)
            }
            SlotText(tile.name, size.sp, maxLines = BACKDROP_COLLECTION_LINES, pad = BACKDROP_COLLECTION_PAD)
            val count = tile.count
            if (count != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    gamesLabel(count), Modifier.padding(horizontal = BACKDROP_COLLECTION_PAD.dp).graphicsLayer {
                        val c = countGrowth(scale())
                        scaleX = c; scaleY = c; transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                    color = colors.listText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontFamily = LocalNxFont.current, fontSize = COUNT_SP.sp, textAlign = TextAlign.Center),
                )
            }
        }
        is GridTile.Tool -> Column(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolIcon(tile.icon, tile.appId, colors.listText, Modifier.size(TOOL_ICON.dp * k))
            Spacer(Modifier.height(toolGap.dp * k))
            SlotText(tile.lines.joinToString(if (toolSplit) "\n" else " "), toolNameSp.sp, maxLines = toolLines, pad = toolNamePad)   // the tool slot: pre-split, "Retro" / "Achievements"
        }
        // A plain row carries no games; the games row draws them from BackdropGame.
        is GridTile.Game -> SlotText(tile.title, 17.sp * k, maxLines = 3)
    }
}

/** A name in a slot: white, centred, balanced lines; drawn at [size]. */
@Composable
internal fun SlotText(text: String, size: TextUnit, maxLines: Int, modifier: Modifier = Modifier, pad: Float = SLOT_TEXT_PAD) {
    Text(
        text, modifier.padding(horizontal = pad.dp), color = LocalNxColors.current.listText,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, style = slotTextStyle(LocalNxFont.current, size),
    )
}

/** [SlotText]'s style at [scaled]. */
private fun slotTextStyle(font: FontFamily, scaled: TextUnit) = TextStyle(
    fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = scaled, lineHeight = scaled * NAME_LINE,
    textAlign = TextAlign.Center, lineBreak = LineBreak.Heading,
)

/**
 * Each item's width (dp) at full size in a Carousel row of tools or collections (the handheld's slotContentPx), as
 * [SlotTile] draws it: the wider of a tool's icon and its name's widest line, or of a collection's
 * name's widest line and a "999 games" count (reserved, so nothing moves when a count arrives), plus the name's padding
 * each side; at most the slot's width. Null for the other slots (consoles, games): they keep the slot.
 */
@Composable
private fun rememberSlotWidths(tiles: List<GridTile>, slot: BackdropSlot, look: BackdropItemLook): FloatArray? {
    if (slot != BackdropSlot.TOOL && slot != BackdropSlot.COLLECTION || tiles.isEmpty()) return null
    val measurer = rememberTextMeasurer()
    val font = LocalNxFont.current
    val density = LocalDensity.current
    val slotW = look.g.itemW
    return remember(tiles, slot, slotW, look.k, look.toolNameSp, look.toolNamePad, look.toolLines, look.toolSplit, font, density) {
        fun widest(text: String, style: TextStyle, lines: Int, avail: Float): Float {
            val r = measurer.measure(
                text, style, maxLines = lines, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = with(density) { avail.coerceAtLeast(1f).dp.roundToPx() }),
            )
            val px = (0 until r.lineCount).maxOfOrNull { r.getLineRight(it) - r.getLineLeft(it) } ?: 0f
            return with(density) { px.toDp().value }
        }
        FloatArray(tiles.size) { i ->
            when (val tile = tiles[i]) {
                is GridTile.Tool -> {
                    val pad = look.toolNamePad
                    val name = widest(
                        tile.lines.joinToString(if (look.toolSplit) "\n" else " "), slotTextStyle(font, look.toolNameSp.sp),
                        look.toolLines, slotW - 2 * pad,
                    )
                    max(TOOL_ICON * look.k, name) + 2 * pad
                }
                is GridTile.Collection -> {
                    val pad = BACKDROP_COLLECTION_PAD
                    val word = backdropLongestWord(tile.name)
                    val wordStyle = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = COLLECTION_NAME.sp * look.k)
                    val wordW = if (word.isEmpty()) 0f else with(density) { measurer.measure(word, wordStyle, softWrap = false, maxLines = 1).size.width.toDp().value }
                    val sp = backdropCollectionNameSp(wordW, slotW - 2 * pad, look.k)
                    val name = widest(tile.name, slotTextStyle(font, sp.sp), BACKDROP_COLLECTION_LINES, slotW - 2 * pad)
                    val count = widest(gamesLabel(999), TextStyle(fontFamily = font, fontSize = COUNT_SP.sp), 1, slotW - 2 * pad)
                    max(name, count) + 2 * pad
                }
                else -> slotW
            }.coerceAtMost(slotW)
        }
    }
}
