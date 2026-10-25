package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.InfoLine

/**
 * A game list's Carousel, orientation Vertical: the horizontal Carousel's tiles ([NxMenuTile], the selected one lit, the
 * neighbours scaled and darkened as [NxCarousel]'s) stood on end ([NxStack]) on the plain background, the top and bottom
 * edges fading. The same arguments as [NxCarousel], so a call site switches on one word. The stack sits left of centre
 * ([carouselStackGeometry]) with the caption to its right ([StackCaption]: name at 18 sp, time row and trophy row, no
 * text shadow); [alignment] RIGHT mirrors the two (Layouts → Vertical alignment).
 * [titleRoom]: a hidden page title's room at the top of this box. The stack is sized without it and centred with it
 * ([StackGeometry.centredIn]), so it and its caption move up by half the room.
 */
@Composable
fun NxCarouselStack(
    tiles: List<GridTile>, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    captionTitle: () -> String?, captionInfo: () -> List<InfoLine>,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    alignment: VerticalListAlignment = VerticalListAlignment.LEFT,
    titleRoom: Dp = 0.dp,
    onPageRows: ((Int) -> Unit)? = null,   // the items one screen shows (a game list's LEFT / RIGHT page)
) {
    val colors = LocalNxColors.current
    val portrait = LocalPortrait.current
    val window = LocalWindowSize.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (tiles.isEmpty()) return@BoxWithConstraints
        val bodyW = maxWidth.value
        val bodyH = maxHeight.value
        // The family is the screen's (the whole window), not the body's; a window not yet measured falls back to the body.
        val screenW = if (window.width.value > 0f) window.width.value else bodyW
        val screenH = if (window.height.value > 0f) window.height.value else bodyH
        val g = carouselStackGeometry(bodyW, titleRoomSizingBody(bodyH, titleRoom.value), screenW, screenH, portrait, gutter = if (portrait) 16f else 24f, alignment = alignment)
            .centredIn(bodyH)
        if (g.slotW <= 1f || g.slotH <= 1f) return@BoxWithConstraints
        if (onPageRows != null) {
            val shown = stackPageItems(RowStyle.CAROUSEL, g, bodyH)
            val report by rememberUpdatedState(onPageRows)
            LaunchedEffect(shown) { report(shown) }
        }
        val itemRow = g.asItemRow()
        val logoInset = remember(itemRow) { rowLogoInset(itemRow) }
        val toolSize = carouselToolSize(itemRow)
        val nameSp = 18f * nxTextScale()   // the horizontal Carousel's, both orientations: Appearance → Text size
        NxStack(
            tiles.size, selectedIndex, g, RowStyle.CAROUSEL, edgeFade = true,
            onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
            layoutKey = layoutKey, dataKey = tiles,
            item = { i, selected, distance ->
                NxMenuTile(
                    tiles[i], lit = selected, caption = false,
                    info = NoTileInfo, logoInset = logoInset, portrait = portrait,
                    modifier = Modifier.fillMaxSize(), toolSize = toolSize,
                )
                // The neighbour's black layer, on the tile's own rounded shape (the ring outside stays bright).
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(NX_TILE_CORNER.dp))
                        .graphicsLayer { alpha = stackItem(RowStyle.CAROUSEL, distance(), g).dim }
                        .background(colors.background),
                )
            },
            overlay = {
                StackCaption(
                    captionTitle(), captionInfo(), g, bodyW, bodyH, shadow = false, nameSp = nameSp,
                    splitNext = stackPortraitFamily(screenW, screenH, portrait), alignment = alignment,
                    infoShare = 0.8f,   // the stats at 80% of the name, as the horizontal Carousel's
                )
            },
        )
    }
}
