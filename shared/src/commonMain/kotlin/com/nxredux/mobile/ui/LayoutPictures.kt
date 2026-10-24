package com.nxredux.mobile.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.nxredux.mobile.settings.ConsoleArt
import com.nxredux.mobile.settings.MenuCategory
import com.nxredux.mobile.settings.MenuStyle
import com.nxredux.mobile.settings.SwitcherLayout
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.onboarding_chrome_hints
import com.nxredux.mobile.shared.res.onboarding_chrome_none
import com.nxredux.mobile.shared.res.onboarding_chrome_title
import com.nxredux.mobile.shared.res.onboarding_chrome_title_hints
import com.nxredux.mobile.shared.res.onboarding_switcher_full
import com.nxredux.mobile.shared.res.onboarding_switcher_list
import com.nxredux.mobile.shared.res.onboarding_tab_collections_carousel
import com.nxredux.mobile.shared.res.onboarding_tab_collections_carousel_vertical
import com.nxredux.mobile.shared.res.onboarding_tab_collections_grid
import com.nxredux.mobile.shared.res.onboarding_tab_collections_list
import com.nxredux.mobile.shared.res.onboarding_games_backdrop
import com.nxredux.mobile.shared.res.onboarding_games_backdrop_vertical_left
import com.nxredux.mobile.shared.res.onboarding_games_backdrop_vertical_right
import com.nxredux.mobile.shared.res.onboarding_games_carousel
import com.nxredux.mobile.shared.res.onboarding_games_carousel_vertical_left
import com.nxredux.mobile.shared.res.onboarding_games_carousel_vertical_right
import com.nxredux.mobile.shared.res.onboarding_games_grid
import com.nxredux.mobile.shared.res.onboarding_games_list
import com.nxredux.mobile.shared.res.onboarding_games_minimal
import com.nxredux.mobile.shared.res.onboarding_tab_collections_minimal
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_minimal
import com.nxredux.mobile.shared.res.onboarding_tab_home_minimal
import com.nxredux.mobile.shared.res.onboarding_tab_home_minimal_nostats
import com.nxredux.mobile.shared.res.onboarding_tab_tools_minimal
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_carousel
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_carousel_background
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_carousel_none
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_carousel_vertical
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_carousel_vertical_background
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_carousel_vertical_none
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_grid
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_list
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_list_background
import com.nxredux.mobile.shared.res.onboarding_tab_consoles_list_none
import com.nxredux.mobile.shared.res.onboarding_tab_home_carousel
import com.nxredux.mobile.shared.res.onboarding_tab_home_carousel_nostats
import com.nxredux.mobile.shared.res.onboarding_tab_home_grid_nostats
import com.nxredux.mobile.shared.res.onboarding_tab_home_list_nostats
import com.nxredux.mobile.shared.res.onboarding_tab_home_grid
import com.nxredux.mobile.shared.res.onboarding_tab_home_list
import com.nxredux.mobile.shared.res.onboarding_tab_tools_carousel
import com.nxredux.mobile.shared.res.onboarding_tab_tools_carousel_vertical
import com.nxredux.mobile.shared.res.onboarding_tab_tools_grid
import com.nxredux.mobile.shared.res.onboarding_tab_tools_list
import com.nxredux.mobile.ui.nx.NxSetupLandscapeWidth
import com.nxredux.mobile.ui.nx.Hint
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.LocalPortrait
import com.nxredux.mobile.ui.nx.NX_TILE_RING
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.NxHintBar
import com.nxredux.mobile.ui.nx.NxList
import com.nxredux.mobile.ui.nx.NxRow
import com.nxredux.mobile.ui.nx.NxOptionsList
import com.nxredux.mobile.ui.nx.nxOptionsFitHeight
import com.nxredux.mobile.ui.nx.LocalNxOptionText
import com.nxredux.mobile.ui.nx.nxOptionsRowHeight
import com.nxredux.mobile.ui.nx.nxTextScale
import com.nxredux.mobile.ui.nx.NxListPitch
import com.nxredux.mobile.ui.nx.NxPageTitle
import com.nxredux.mobile.ui.nx.NxTitleActions
import com.nxredux.mobile.ui.nx.NxTextSizes
import com.nxredux.mobile.ui.nx.nxHighlight
import com.nxredux.mobile.ui.nx.nxListInset
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

/** A layout picture's width over height: the menu's area of a Fold cover-screen capture above the pad (624 × 442). */
internal const val LAYOUT_PICTURE_AR = 624f / 442f
/** A preview picture's widest: a landscape page keeps it at a phone page's width. */
private val PREVIEW_MAX_WIDTH = 420.dp
/** A preview picture's narrowest: under it the page shows none. */
private val PREVIEW_MIN_WIDTH = 140.dp

/** A picture's grey border, so a dark capture keeps its edge on the black page. */
private val PICTURE_BORDER = 1.5.dp

/** The black mat between a picture's border and the capture, so the capture's edge does not crowd it. */
private val PICTURE_MAT = 8.dp

/** The capture's own corners inside the mat. */
private val PICTURE_INNER_CORNER = 6.dp

/**
 * How tab [category] looks in [style] (List, Grid, Carousel or Minimal; the tabs have no Backdrop); Home with its stats strip or not
 * ([stats]); a Carousel stood on end ([vertical]; Home's Carousel has no orientation). A List or Carousel Consoles tab
 * as Layouts → Consoles background ([art]) draws it: its controllers, a console's bg.png, or neither.
 */
internal fun tabPicture(
    category: MenuCategory, style: MenuStyle, stats: Boolean = true, vertical: Boolean = false, art: ConsoleArt = ConsoleArt.CONTROLLER,
): DrawableResource =
    if (style == MenuStyle.MINIMAL) minimalTabPicture(category, stats)
    else if (!vertical || style != MenuStyle.CAROUSEL) horizontalTabPicture(category, style, stats, art) else when (category) {
        MenuCategory.CONSOLES -> byArt(
            art, Res.drawable.onboarding_tab_consoles_carousel_vertical, Res.drawable.onboarding_tab_consoles_carousel_vertical_background,
            Res.drawable.onboarding_tab_consoles_carousel_vertical_none,
        )
        MenuCategory.COLLECTIONS -> Res.drawable.onboarding_tab_collections_carousel_vertical
        MenuCategory.TOOLS -> Res.drawable.onboarding_tab_tools_carousel_vertical
        MenuCategory.HOME -> horizontalTabPicture(category, style, stats, art)
    }

/** A Minimal tab: its names alone (no art, so no orientation or Consoles background); Home with its stats strip or not. */
private fun minimalTabPicture(category: MenuCategory, stats: Boolean): DrawableResource = when (category) {
    MenuCategory.HOME -> if (stats) Res.drawable.onboarding_tab_home_minimal else Res.drawable.onboarding_tab_home_minimal_nostats
    MenuCategory.CONSOLES -> Res.drawable.onboarding_tab_consoles_minimal
    MenuCategory.COLLECTIONS -> Res.drawable.onboarding_tab_collections_minimal
    MenuCategory.TOOLS -> Res.drawable.onboarding_tab_tools_minimal
}

private fun horizontalTabPicture(category: MenuCategory, style: MenuStyle, stats: Boolean, art: ConsoleArt): DrawableResource = when (category) {
    MenuCategory.HOME -> if (stats) pick(style, Res.drawable.onboarding_tab_home_list, Res.drawable.onboarding_tab_home_grid, Res.drawable.onboarding_tab_home_carousel)
        else pick(style, Res.drawable.onboarding_tab_home_list_nostats, Res.drawable.onboarding_tab_home_grid_nostats, Res.drawable.onboarding_tab_home_carousel_nostats)
    MenuCategory.CONSOLES -> pick(
        style,
        byArt(art, Res.drawable.onboarding_tab_consoles_list, Res.drawable.onboarding_tab_consoles_list_background, Res.drawable.onboarding_tab_consoles_list_none),
        Res.drawable.onboarding_tab_consoles_grid,   // the Grid has no controllers or background
        byArt(art, Res.drawable.onboarding_tab_consoles_carousel, Res.drawable.onboarding_tab_consoles_carousel_background, Res.drawable.onboarding_tab_consoles_carousel_none),
    )
    MenuCategory.COLLECTIONS -> pick(style, Res.drawable.onboarding_tab_collections_list, Res.drawable.onboarding_tab_collections_grid, Res.drawable.onboarding_tab_collections_carousel)
    MenuCategory.TOOLS -> pick(style, Res.drawable.onboarding_tab_tools_list, Res.drawable.onboarding_tab_tools_grid, Res.drawable.onboarding_tab_tools_carousel)
}

/** The Consoles picture for Layouts → Consoles background [art]: with the controllers, a console's bg.png, or neither. */
private fun byArt(art: ConsoleArt, controller: DrawableResource, background: DrawableResource, none: DrawableResource) = when (art) {
    ConsoleArt.CONTROLLER -> controller
    ConsoleArt.BACKGROUND -> background
    ConsoleArt.NONE -> none
}

private fun pick(style: MenuStyle, list: DrawableResource, grid: DrawableResource, carousel: DrawableResource) = when (style) {
    MenuStyle.LIST -> list
    MenuStyle.GRID -> grid
    else -> carousel
}

/** How a game list looks in [style]; a Carousel or Backdrop stood on end ([vertical]), on the left or the [right]. */
internal fun gamesPicture(style: MenuStyle, vertical: Boolean = false, right: Boolean = false): DrawableResource = when (style) {
    MenuStyle.LIST -> Res.drawable.onboarding_games_list
    MenuStyle.MINIMAL -> Res.drawable.onboarding_games_minimal
    MenuStyle.GRID -> Res.drawable.onboarding_games_grid
    MenuStyle.CAROUSEL -> when {
        !vertical -> Res.drawable.onboarding_games_carousel
        right -> Res.drawable.onboarding_games_carousel_vertical_right
        else -> Res.drawable.onboarding_games_carousel_vertical_left
    }
    MenuStyle.BACKDROP -> when {
        !vertical -> Res.drawable.onboarding_games_backdrop
        right -> Res.drawable.onboarding_games_backdrop_vertical_right
        else -> Res.drawable.onboarding_games_backdrop_vertical_left
    }
}

/**
 * The page title and button hints page's picture: Home on the Fold's cover screen with the tab row ([title]) and the hint
 * bar ([hints]) shown or hidden, the app's area above the pad (624 × 442).
 */
internal fun chromePicture(title: Boolean, hints: Boolean): DrawableResource = when {
    title && hints -> Res.drawable.onboarding_chrome_title_hints
    title -> Res.drawable.onboarding_chrome_title
    hints -> Res.drawable.onboarding_chrome_hints
    else -> Res.drawable.onboarding_chrome_none
}

/** The page title and button hints picture's width over height. */
internal const val CHROME_PICTURE_AR = 624f / 442f


/**
 * A page of value rows with one picture of their current values (onboarding's layout and page title steps, a Settings →
 * Layouts group): the page [title] and [details] lines, the value rows right under them, the [picture] ([aspect] its
 * width over height; null: none) at the page's width (up to [PREVIEW_MAX_WIDTH]). [titleAction]: the last of [rows]
 * (Continue) sits at the title's right; without, every row is a value row (B leaves). Non-wide landscape keeps the lines
 * and rows to [landscapeWidth] of the width. A wide landscape screen ([PREVIEW_WIDE_RATIO]: a 16:9 phone, not the Fold's
 * near-square one) puts the picture in a column of its own at the right, as large as that column takes it.
 * [touchRows]: the value rows' own list in place of the options rows (a Settings group's touch rows), given the room it may take.
 */
@Composable
internal fun LayoutPreviewPage(
    title: String, details: List<String>, rows: List<NxRow>, picture: DrawableResource?, aspect: Float, selected: Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onRow: (Int) -> Unit, onHint: (NxButton) -> Unit, modifier: Modifier = Modifier,
    landscapeWidth: Float = NxSetupLandscapeWidth, titleAction: Boolean = true,
    touchRows: (@Composable (Modifier) -> Unit)? = null,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val values = if (titleAction) rows.dropLast(1) else rows
    val last = rows.lastIndex
    // Continue at the title's right; landscape fits the picture to the height left.
    val portrait = LocalPortrait.current
    val label = NxTextSizes.listLabel(NxListPitch) * nxTextScale()   // Appearance → Text size
    // [rowsMax]: the most the touch rows may take, so the picture under them keeps its room (they scroll past it).
    val text: @Composable (Modifier, Dp) -> Unit = { cap, rowsMax -> Column(cap) {
        Column(Modifier.padding(horizontal = nxListInset())) {
            details.forEachIndexed { i, line ->
                if (i == 0) Text(line, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = NxTextSizes.headline(label))
                else Text(line, color = colors.hint, fontFamily = font, fontSize = NxTextSizes.secondary(label))
            }
        }
        if (details.isNotEmpty()) Spacer(Modifier.height(12.dp))
        // The value rows, as the formats step draws them ("< value >" while selected); on a short page they scroll.
        val optionPitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)   // Appearance → Text size
        if (touchRows != null) touchRows(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = rowsMax))
        else NxOptionsList(
            values, selected.takeIf { it < values.size } ?: -1, onSelect, onRow, rowHeight = optionPitch, listText = NxTextSizes.listLabel(optionPitch),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(nxOptionsFitHeight(values, optionPitch)), scrollFade = true,
        )
    } }
    // Too little room left (a short page of many rows): no picture rather than a thumbnail.
    val framed: @Composable (Dp) -> Unit = { w -> if (picture != null && w >= PREVIEW_MIN_WIDTH) {
        val shape = RoundedCornerShape(12.dp)
        Box(Modifier.width(w).background(Color.Black, shape).border(PICTURE_BORDER, colors.hint.copy(alpha = 0.6f), shape).padding(PICTURE_MAT)) {
            Image(
                painterResource(picture), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(PICTURE_INNER_CORNER)),
            )
        }
    } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = !portrait && maxWidth / maxHeight >= PREVIEW_WIDE_RATIO
        val pageH = maxHeight
        Column(Modifier.fillMaxSize()) {
            NxPageTitle(title, bottom = 8.dp, trailing = if (titleAction) ({ NxTitleActions(listOf(rows[last].label), last, selected, onSelect, onRow) }) else null)
            if (wide) {
                // Text and rows at the left, the picture at the right, each half the width.
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.weight(1f).fillMaxHeight()) { text(Modifier, Dp.Infinity) }
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxHeight().padding(end = nxListInset(), bottom = 12.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        framed(min(maxWidth, (maxHeight - PICTURE_MAT * 2) * aspect + PICTURE_MAT * 2))
                    }
                }
            } else {
                // Landscape (the Fold's near-square pane): the rows at most [PREVIEW_ROWS_SHARE] of the height, the picture the rest.
                text(if (portrait) Modifier else Modifier.fillMaxWidth(landscapeWidth), if (portrait) Dp.Infinity else pageH * PREVIEW_ROWS_SHARE)   // landscape: long lines wrap
                // The picture at the page's width (inset), smaller when the rows leave less height; the room left stays below.
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f).padding(start = nxListInset(), end = nxListInset(), top = 16.dp, bottom = 12.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    framed(min(min(maxWidth, PREVIEW_MAX_WIDTH), ((maxHeight - PICTURE_MAT * 2) * aspect + PICTURE_MAT * 2).coerceAtLeast(0.dp)))
                }
            }
            NxHintBar(hints, onHint = onHint)
        }
    }
}

/** In landscape with the picture under the rows, the touch rows' most share of the page's height (they scroll past it). */
private const val PREVIEW_ROWS_SHARE = 0.5f

/** A landscape page at least this much wider than tall lays the picture out beside the rows (a 16:9 phone, not the Fold). */
private const val PREVIEW_WIDE_RATIO = 1.5f

/** What a Layouts group's picture shows ([Screen.Settings.shot]): a tab in a layout, a game list in one, the title and hints, the Game Switcher. */
sealed interface LayoutShot {
    /**
     * [stats]: Home's stats strip shown (Layouts → Home stats); the other tabs have none. [vertical]: a Carousel stood on end.
     * [art]: Consoles' Layouts → Consoles background; the other tabs keep the default.
     */
    data class Tab(
        val category: MenuCategory, val style: MenuStyle, val stats: Boolean = true, val vertical: Boolean = false,
        val art: ConsoleArt = ConsoleArt.CONTROLLER,
    ) : LayoutShot
    /** [vertical]: a Carousel or Backdrop stood on end; [right]: then on the right (Layouts → Game lists alignment). */
    data class Games(val style: MenuStyle, val vertical: Boolean = false, val right: Boolean = false) : LayoutShot
    data class Chrome(val title: Boolean, val hints: Boolean) : LayoutShot
    data class Switcher(val layout: SwitcherLayout) : LayoutShot
}

/**
 * [shot]'s picture: the tab, game list or Game Switcher capture of its layout, or the title and hints one.
 */
internal fun layoutShotPicture(shot: LayoutShot): DrawableResource? = when (shot) {
    is LayoutShot.Tab -> tabPicture(shot.category, shot.style, shot.stats, shot.vertical, shot.art)
    is LayoutShot.Games -> gamesPicture(shot.style, shot.vertical, shot.right)
    is LayoutShot.Chrome -> chromePicture(shot.title, shot.hints)
    is LayoutShot.Switcher -> when (shot.layout) {
        SwitcherLayout.FULL -> Res.drawable.onboarding_switcher_full
        SwitcherLayout.LIST -> Res.drawable.onboarding_switcher_list
    }
}
