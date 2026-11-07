package com.nxredux.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.kids.KidsMode
import com.nxredux.mobile.kids.LocalNow
import com.nxredux.mobile.kids.nextOpenLabel
import com.nxredux.mobile.kids.timeLabel
import com.nxredux.mobile.launcher.InstalledApp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.menu_icon_app
import com.nxredux.mobile.shared.res.menu_icon_lock
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont
import com.nxredux.mobile.ui.nx.NX_TILE_CORNER
import com.nxredux.mobile.ui.nx.NxButton
import com.nxredux.mobile.ui.nx.gridStep
import com.nxredux.mobile.ui.nx.nxHighlight
import com.nxredux.mobile.ui.nx.nxPageInset
import com.nxredux.mobile.ui.nx.nxScrollFade
import com.nxredux.mobile.ui.nx.rememberAppIcon
import com.nxredux.mobile.ui.nx.tilePlate
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import kotlin.math.floor
import kotlin.math.max

/** One app on Kids Mode's tiles screen. */
data class KidsTile(val id: String, val label: String)

/** The tiles: the [installed] apps on the [allowed] list (one not installed is skipped), by label. */
internal fun kidsTilesOf(installed: List<InstalledApp>, allowed: Set<String>): List<KidsTile> =
    installed.filter { it.id in allowed }.sortedBy { it.label.lowercase() }.map { KidsTile(it.id, it.label) }

private const val KIDS_TILE_W = 120f   // a tile's width, dp
private const val KIDS_ICON = 80f      // its app icon's side, dp
private const val KIDS_TILE_GAP = 16f  // between tiles, dp

/** How many tiles fit a row [widthDp] wide less its [sides]; one at least. */
internal fun kidsTileColumns(widthDp: Float, sides: Float): Int =
    max(1, floor((widthDp - sides + KIDS_TILE_GAP) / (KIDS_TILE_W + KIDS_TILE_GAP)).toInt())

/**
 * The d-pad on the tiles screen from [index]: [tiles] tiles [columns] to a row ([gridStep], the rows wrapping), then the
 * lock at index [tiles]. UP from the first row goes to the lock; DOWN or LEFT from the lock to the first tile.
 */
internal fun kidsHomeStep(index: Int, tiles: Int, columns: Int, button: NxButton): Int = when {
    index >= tiles -> if (tiles > 0 && (button == NxButton.DOWN || button == NxButton.LEFT)) 0 else index
    button == NxButton.UP && index < columns -> tiles
    else -> gridStep(index, tiles, columns, button, rowEnds = false) ?: index
}

/** [now] as the tiles screen's clock reads it ("9:05 AM"), or empty without one. */
internal fun kidsClockLabel(now: LocalNow?): String = now?.let { timeLabel((it.msOfDay / 60_000L).toInt()) }.orEmpty()

/**
 * Kids Mode's tiles screen while Locked: the allowed apps as large tiles (their own icons over their names), centred and
 * wrapping into rows, with when games open next under them; the clock at the top left, the lock (the parent's way in)
 * at the top right. A tap opens a tile or the lock; the d-pad's selection ([selectedIndex]: a tile, or the lock after
 * them) is lit as the menu's is. Nothing goes back from here.
 */
@Composable
internal fun KidsHomeScreen(
    tiles: List<KidsTile>, selectedIndex: Int, mode: KidsMode, now: () -> LocalNow?,
    onOpen: (String) -> Unit, onLock: () -> Unit, onColumns: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val inset = nxPageInset()
    // The clock checks every few seconds: a coroutine wake that recomposes only when the minute turns.
    val clock by produceState(kidsClockLabel(now()), now) { while (true) { delay(5_000); value = kidsClockLabel(now()) } }
    val next = (mode as? KidsMode.Locked)?.next?.let(::nextOpenLabel)
    Box(modifier.fillMaxSize().background(colors.background)) {
        // The same room above and below (the clock and the lock sit in the top one), so the tiles centre on the screen.
        KidsTileGrid(tiles, selectedIndex, { onOpen(tiles[it].id) }, onColumns, Modifier.fillMaxSize().padding(vertical = 64.dp), below = next)
        Text(
            clock, color = colors.main, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = inset.start, top = 16.dp),
        )
        KidsLockButton(lit = selectedIndex >= tiles.size, onLock, Modifier.align(Alignment.TopEnd).padding(end = inset.end, top = 12.dp))
    }
}

/**
 * The tiles screen's grid, also Kids Mode's Apps tab: [tiles] as large tiles, centred and wrapping into rows ([onColumns]
 * hears how many fit a row), the one at [selectedIndex] lit, a tap on one [onTap] with its index. [below]: a line under
 * them (when games open next), balanced by an unseen twin above so the tiles themselves centre. Taller than the box, it
 * scrolls, its edges fading.
 */
@Composable
internal fun KidsTileGrid(
    tiles: List<KidsTile>, selectedIndex: Int, onTap: (Int) -> Unit, onColumns: (Int) -> Unit, modifier: Modifier = Modifier,
    below: String? = null,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val inset = nxPageInset()
    BoxWithConstraints(modifier) {
        val columns = kidsTileColumns(maxWidth.value, (inset.start + inset.end).value)
        // Again for new tiles or a new reporter: the Apps tab drawn beside before it shows reports once it is the page.
        LaunchedEffect(tiles, columns, onColumns) { onColumns(columns) }
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxSize().nxScrollFade(scroll).verticalScroll(scroll).heightIn(min = maxHeight)
                .padding(start = inset.start, end = inset.end),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            val line: @Composable (Modifier) -> Unit = { m ->
                Text(below.orEmpty(), color = colors.hint, fontFamily = font, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = m)
            }
            if (below != null) {
                line(Modifier.alpha(0f))   // role: an unseen spacer the size of the line below
                Spacer(Modifier.height(28.dp))
            }
            tiles.chunked(columns).forEachIndexed { r, row ->
                if (r > 0) Spacer(Modifier.height(KIDS_TILE_GAP.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(KIDS_TILE_GAP.dp)) {
                    row.forEachIndexed { c, tile ->
                        val i = r * columns + c
                        KidsAppTile(tile, lit = i == selectedIndex) { onTap(i) }
                    }
                }
            }
            if (below != null) {
                Spacer(Modifier.height(28.dp))
                line(Modifier)
            }
        }
    }
}

/** One app: its full-colour icon (the generic app glyph until it loads, or when it has none) over its name. */
@Composable
private fun KidsAppTile(tile: KidsTile, lit: Boolean, onTap: () -> Unit) {
    val colors = LocalNxColors.current
    val content = if (lit) colors.onAccent else colors.listText
    val icon = rememberAppIcon(tile.id)
    // The d-pad's tile scrolled into view when the grid is taller than its box.
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(lit) { if (lit) inView.bringIntoView() }
    val size = 14.sp
    val style = TextStyle(fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.15f, textAlign = TextAlign.Center)
    val iconMod = Modifier.size(KIDS_ICON.dp)
    // Every tile as tall as a two-line name's; its icon and name centred in it, so a one-line name sits mid-tile too.
    Box(
        Modifier.width(KIDS_TILE_W.dp).bringIntoViewRequester(inView).clip(RoundedCornerShape(NX_TILE_CORNER.dp))
            .then(if (lit) Modifier.background(nxHighlight()) else Modifier.tilePlate(colors.background, colors.inkEdge))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.alpha(0f), horizontalAlignment = Alignment.CenterHorizontally) {   // role: the unseen two-line size
            Spacer(iconMod)
            Spacer(Modifier.height(8.dp))
            Text("", minLines = 2, maxLines = 2, style = style, modifier = Modifier.fillMaxWidth())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) Image(icon, contentDescription = null, modifier = iconMod)
            else Image(painterResource(Res.drawable.menu_icon_app), contentDescription = null, modifier = iconMod, colorFilter = ColorFilter.tint(content))
            Spacer(Modifier.height(8.dp))
            Text(tile.label, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = style, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** The parent's way in: a lock and the word "Parent" in a pill, lit when the d-pad is on it. */
@Composable
private fun KidsLockButton(lit: Boolean, onTap: () -> Unit, modifier: Modifier) {
    val colors = LocalNxColors.current
    val content = if (lit) colors.onAccent else colors.hint
    Row(
        modifier.clip(RoundedCornerShape(50)).then(if (lit) Modifier.background(nxHighlight()) else Modifier)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(Res.drawable.menu_icon_lock), contentDescription = "Parent", modifier = Modifier.size(22.dp), colorFilter = ColorFilter.tint(content))
        Spacer(Modifier.width(6.dp))
        Text("Parent", color = content, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
