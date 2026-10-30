package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.ui.BackdropGame
import com.nxredux.mobile.settings.VerticalListAlignment
import com.nxredux.mobile.ui.GridTile
import com.nxredux.mobile.ui.InfoLine


/** A game list's box stack fades over most of a half-shown box, so a box beyond an edge fades out rather than cuts. */
private val BACKDROP_STACK_EDGE_FADE = 56.dp

/**
 * The Backdrop items stood on end ([NxStack]): Carousel, orientation Vertical. The same arguments as [NxBackdrop], so a
 * call site switches on one word. [games] null: the main menu's stack of [tiles] on black, the very items the horizontal
 * row draws ([SlotTile]) in the main menu's slots ([mainMenuStackGeometry]: Consoles' logos, Collections' names, Tools'
 * icons and names), centred, the top and bottom edges fading. Every console's "N games" hangs 8 dp under its logo as
 * drawn and every collection's under its name, in the ink, scaled and dimmed with its item ([SlotTile]); consoles are
 * placed by what they draw ([StackReach]: the logo as drawn and its count), so every console's count is the same gap
 * from the next one's logo, selected or not. A tool has no caption.
 * [games] set: a game list's box art ([BoxArt], the placeholder box without art) stacked left of centre
 * ([backdropStackGeometry]) over the selected game's screenshot (drawn by the caller, as [NxBackdrop]'s), with
 * [captionTitle] / [captionInfo] to its right ([StackCaption]: name, time row, trophy row, Backdrop's text shadow); no
 * edge fade. [alignment] RIGHT (game lists only; Layouts → Vertical alignment) mirrors the stack and the caption.
 * [titleRoom]: a hidden page title's room at the top of this box. Either stack is sized without it and centred with it
 * ([StackGeometry.centredIn]), moving up by half the room; the caller's picture doesn't move.
 */
@Composable
fun NxBackdropStack(
    games: List<BackdropGame>?,
    tiles: List<GridTile>, selectedIndex: () -> Int,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, onLongPress: ((Int) -> Unit)?,
    captionTitle: () -> String?, captionInfo: () -> List<InfoLine>,
    modifier: Modifier = Modifier,
    layoutKey: Any? = null,
    alignment: VerticalListAlignment = VerticalListAlignment.LEFT,
    titleRoom: Dp = 0.dp,
    onPageRows: ((Int) -> Unit)? = null,   // the items one screen shows (a game list's LEFT / RIGHT page)
) {
    val portrait = LocalPortrait.current
    val window = LocalWindowSize.current
    // The geometry is laid out for the left and mirrored for the right: the deeper side's inset, so it clears the camera either way.
    val gutter = nxPageInset().let { maxOf(it.start, it.end).value }
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (games != null) {
            // A game list: the box art stack left of centre over the caller's picture, the caption to its right (every
            // family). The boxes fade out at the top and bottom edges; the fade masks the stack alone, so the picture
            // behind runs on to the body's edges.
            if (games.isEmpty()) return@BoxWithConstraints
            val bodyW = maxWidth.value
            val bodyH = maxHeight.value
            // The family is the screen's (the whole window), not the body's; a window not yet measured falls back to the body.
            val screenW = if (window.width.value > 0f) window.width.value else bodyW
            val screenH = if (window.height.value > 0f) window.height.value else bodyH
            val g = backdropStackGeometry(bodyW, titleRoomSizingBody(bodyH, titleRoom.value), screenW, screenH, portrait, gutter, alignment = alignment)
                .centredIn(bodyH)
            if (onPageRows != null) {
                val shown = stackPageItems(RowStyle.BACKDROP, g, bodyH)
                val report by rememberUpdatedState(onPageRows)
                LaunchedEffect(shown) { report(shown) }
            }
            NxStack(
                games.size, selectedIndex, g, RowStyle.BACKDROP, edgeFade = true,
                onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
                layoutKey = layoutKey, dataKey = games, edgeFadeHeight = BACKDROP_STACK_EDGE_FADE,
                // The unselected boxes in greyscale, as the row's ([backdropSaturation]).
                item = { i, _, distance -> Box(Modifier.matchParentSize().nxSaturation(BOX_SHADOW_REACH) { backdropSaturation(distance()) }) { BoxArt(games[i]) } },
                overlay = { StackCaption(
                    captionTitle(), captionInfo(), g, bodyW, bodyH, shadow = true, nameSp = 18f * nxTextScale(),   // Appearance → Text size
                    splitNext = stackPortraitFamily(screenW, screenH, portrait), alignment = alignment, infoShare = 0.8f,
                ) },
            )
            return@BoxWithConstraints
        }
        val count = tiles.size
        if (count == 0) return@BoxWithConstraints
        val slot = backdropSlot(false, tiles)
        // A console's count is part of its footprint: measured, so the gaps hold at any UI and font scale.
        val countLine = if (tiles.any { it is GridTile.Logo && it.count != null }) countLineDp() else 0f
        val g = mainMenuStackGeometry(slot, maxWidth.value, titleRoomSizingBody(maxHeight.value, titleRoom.value), portrait, gutter, countLine)
            .centredIn(maxHeight.value)
        if (g.slotW <= 1f || g.slotH <= 1f) return@BoxWithConstraints
        // Consoles: each logo's height as drawn in its slot, so the step to the next one is from the logo and count drawn;
        // with its controller (Layouts → Consoles background), the selection's step is from the pad as drawn ([withPad]).
        val reaches = if (slot != BackdropSlot.LOGO) null else tiles.map { t ->
            val logo = t as? GridTile.Logo
            val res = logo?.res
            val base = if (res == null) StackReach(g.slotH / 2, g.slotH / 2)
                else key(res) { logoReach(logoDrawnHeight(g.slotW, g.slotH, logoAspect(res)), if (logo.count != null) countLine else 0f) }
            val pad = logo?.pad
            if (pad == null) base else base.withPad(stackPadBox(g.slotW, g.slotH, base), padAspect(pad.res))
        }
        val itemRow = g.asItemRow()
        val k = backdropContentScale(itemRow, slot, portrait && slot != BackdropSlot.LOGO)   // a console: the row's slot, so its scale
        val toolNameSp = backdropToolNameSp(itemRow, k, slot)
        NxStack(
            count, selectedIndex, g, RowStyle.BACKDROP, edgeFade = true,
            onSelect = onSelect, onOpen = onOpen, onLongPress = onLongPress,
            layoutKey = layoutKey, dataKey = tiles, marks = true,
            reach = reaches?.let { r -> { i: Int -> r[i] } },
            sideAlpha = if (fadesFurther(slot)) ::mainMenuSideAlpha else null,
            item = { i, _, distance ->
                SlotTile(
                    tiles[i], k, g.slotW, scale = { stackItem(RowStyle.BACKDROP, distance(), g).scale }, toolLines = backdropToolNameLines(slot), toolNameSp = toolNameSp,
                    toolNamePad = backdropToolNamePad(slot), toolSplit = slot == BackdropSlot.TOOL, toolGap = backdropToolGap(slot),
                    pad = reaches?.get(i)?.let { r -> PadFrame(stackPadBox(g.slotW, g.slotH, r)) { padAlpha(distance()) } },
                    lit = { slotLitness(distance()) },   // the selected console in the accent
                )
            },
        )
    }
}
