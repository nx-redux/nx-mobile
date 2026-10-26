package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.nxredux.mobile.layout.portraitBandPx
import com.nxredux.mobile.runner.Posture
import com.nxredux.mobile.runner.TabletopPad
import com.nxredux.mobile.runner.tabletopInArea
import kotlin.math.roundToInt

/** The portrait pad area is sized to this many centimetres so a clamp-on controller fits over it. */
const val PAD_PORTRAIT_CM = 5.8f

/**
 * True for a portrait layout (the narrow gutters, portrait sizes and art): a portrait window, with or without its pad
 * band, or a tabletop with its controls below the hinge; false in landscape. Whether a pad is drawn is [LocalPadBelow].
 */
val LocalPortrait = compositionLocalOf { false }

/** True while the on-screen pad is drawn below the screen (portrait with its band, or tabletop controls). */
val LocalPadBelow = compositionLocalOf { false }

/**
 * The screen area's vertical centre in window px (above the pad band, or the whole area without one), for content
 * centred on the screen rather than its own box ([NxCarousel]'s game-list neighbours). Null until measured.
 */
val LocalScreenCentreY = compositionLocalOf<Float?> { null }

/**
 * The measured app area (the whole window inside the safe insets, the pad included): the Grid's tablet-class test. In
 * tabletop it stays the whole area too (the controls below the hinge included); the screens lay out to their own body.
 */
val LocalWindowSize = compositionLocalOf { DpSize.Zero }

/** The device posture in window px (Flex mode), supplied by the host; [Posture.Flat] where there is no fold (and on iOS). */
val LocalPosture = compositionLocalOf<Posture> { Posture.Flat }

/**
 * Portrait: the screen fills the top and a [PAD_PORTRAIT_CM] pad band the bottom, sized from the display's real dpi
 * ([padBandPx]: the density bucket when the dpi is unknown, at most 60% of the height). Landscape: the screen fills the
 * display, no pad. Tabletop (Flex mode): the screen above the hinge, the pad or [clusters] below. [showPad] false
 * (Controls → Menu pad Off) drops the portrait band, the screen filling the area; tabletop keeps its controls below the
 * hinge. The caller wraps this in the safe-drawing insets.
 */
@Composable
fun HandheldShell(
    pad: @Composable (Modifier) -> Unit,
    screen: @Composable (modifier: Modifier) -> Unit,
    controllerConnected: Boolean = false,
    showPad: Boolean = true,
    clusters: @Composable (left: Modifier, right: Modifier) -> Unit = { _, _ -> },
) {
    val colors = LocalNxColors.current
    val density = LocalDensity.current
    val ydpi = rememberDisplayYdpi()
    val posture = LocalPosture.current
    var topInWindow by remember { mutableStateOf<Int?>(null) }   // the shell's top in window px, for the hinge
    // The screen and pad move between the branches below rather than being rebuilt: a rotation or a fold keeps their
    // lazy states, glides, animations and focus. The latest lambdas are read through, so the movable content stays one.
    val currentScreen by rememberUpdatedState(screen)
    val currentPad by rememberUpdatedState(pad)
    val movableScreen = remember { movableContentOf { m: Modifier -> currentScreen(m) } }
    val movablePad = remember { movableContentOf { m: Modifier -> currentPad(m) } }
    var screenCentreY by remember { mutableStateOf<Float?>(null) }
    val screenSlot = Modifier.fillMaxSize().onGloballyPositioned { screenCentreY = it.positionInWindow().y + it.size.height / 2f }
    CompositionLocalProvider(LocalScreenCentreY provides screenCentreY) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(colors.background)
                .onGloballyPositioned { topInWindow = it.positionInWindow().y.roundToInt() },
        ) {
            val portrait = maxHeight > maxWidth
            val window = DpSize(maxWidth, maxHeight)
            val tt = topInWindow?.let { top ->
                with(density) { tabletopInArea(posture, top, maxWidth.roundToPx(), maxHeight.roundToPx(), controllerConnected) }
            }
            if (posture is Posture.Tabletop && topInWindow == null) {
                // The first frame in tabletop: the hinge split needs the shell's position, so compose nothing rather than a
                // branch that is swapped out a frame later.
            } else if (tt != null) {
                // Flex mode: the screen above the hinge, the controls (portrait pad, or the two clusters drawn solid) below it.
                val screenH = with(density) { tt.gameBottom.toDp() }
                val padH = maxHeight - with(density) { tt.padTop.toDp() }
                Box(Modifier.fillMaxWidth().height(screenH)) {
                    CompositionLocalProvider(LocalPortrait provides (tt.pad != TabletopPad.NONE), LocalPadBelow provides (tt.pad != TabletopPad.NONE), LocalWindowSize provides window) {
                        movableScreen(screenSlot)
                    }
                }
                when (tt.pad) {
                    TabletopPad.PORTRAIT -> Box(Modifier.fillMaxWidth().height(padH).align(Alignment.BottomCenter)) { movablePad(Modifier.fillMaxSize()) }
                    TabletopPad.CLUSTERS -> Box(Modifier.fillMaxWidth().height(padH).align(Alignment.BottomCenter)) {
                        clusters(
                            Modifier.fillMaxWidth(CLUSTER_MAX_WIDTH_SHARE).fillMaxHeight().align(Alignment.BottomStart),
                            Modifier.fillMaxWidth(CLUSTER_MAX_WIDTH_SHARE).fillMaxHeight().align(Alignment.BottomEnd),
                        )
                    }
                    TabletopPad.NONE, TabletopPad.DS -> Unit
                }
            } else if (!portrait || !showPad) {
                // Menu pad Off keeps the portrait layout, the screen filling the area.
                CompositionLocalProvider(LocalPortrait provides portrait, LocalPadBelow provides false, LocalWindowSize provides window) { movableScreen(screenSlot) }
            } else {
                // Same band the game reserves, measured the same way (window minus the top cutout).
                val container = LocalWindowInfo.current.containerSize
                val areaPx = container.height - WindowInsets.displayCutout.getTop(density)
                val padHeight = with(density) { portraitBandPx(container.width.toFloat(), areaPx.toFloat(), ydpi, density.density).toDp() }
                val screenH = maxHeight - padHeight
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(screenH)) {
                        CompositionLocalProvider(LocalPortrait provides true, LocalPadBelow provides true, LocalWindowSize provides window) { movableScreen(screenSlot) }
                    }
                    Box(Modifier.fillMaxWidth().height(padHeight)) { movablePad(Modifier.fillMaxSize()) }
                }
            }
        }
    }
}
