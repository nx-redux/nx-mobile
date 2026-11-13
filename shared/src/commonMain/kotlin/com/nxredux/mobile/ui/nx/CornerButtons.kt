package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.pad_mic
import com.nxredux.mobile.shortcuts.TouchSlot
import org.jetbrains.compose.resources.painterResource

/**
 * The DS / 3DS mic button in the screen's bottom-left corner: shown with a [mic] (a mic core, not in netplay) where no pad
 * is drawn ([padDrawn]: a skin or its buttons carry their own MIC), so a player with a controller can still blow or shout.
 */
fun micCornerShown(mic: PadMic?, padDrawn: Boolean): Boolean = mic != null && !padDrawn

/**
 * The touch shortcut buttons in the screen's bottom-right corner: where no pad is drawn ([padDrawn]: a skin's strip
 * carries them), the slots the player set ([shortcuts]), in slot order.
 */
fun cornerShortcuts(shortcuts: Map<TouchSlot, TouchShortcut>, padDrawn: Boolean): List<Pair<TouchSlot, TouchShortcut>> =
    if (padDrawn) emptyList() else TouchSlot.entries.mapNotNull { slot -> shortcuts[slot]?.let { slot to it } }

/**
 * The buttons over the game where no pad is drawn: [mic]'s round button in the bottom-left corner ([micCornerShown]) and
 * the set touch shortcuts ([shortcuts], [cornerShortcuts]) as a row of pills ending in the bottom-right corner, the
 * DS / 3DS split's Panel pill ([panel]: shows the game panel over the touch screen; null: none) the last of the row. All
 * see-through, lit (white, dark ink) while on. The mic toggles on a tap; a shortcut goes down on the press and up on the
 * release ([onShortcut]), as the skin strip's, so its hold actions hold. Clear of the system bars and the cutout; only the
 * buttons take touches.
 */
@Composable
fun CornerButtons(
    mic: PadMic?, shortcuts: List<Pair<TouchSlot, TouchShortcut>>, onShortcut: (TouchSlot, Boolean) -> Unit,
    modifier: Modifier = Modifier, panel: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(CORNER_MARGIN)) {
        if (mic != null) {
            CornerButton(mic.on, CircleShape, Modifier.align(Alignment.BottomStart).size(CORNER_BUTTON_HEIGHT), onPress = { down -> if (down) mic.onTap() }) {
                Icon(painterResource(Res.drawable.pad_mic), contentDescription = "Mic", tint = cornerInk(mic.on), modifier = Modifier.size(MIC_ICON_SIZE))
            }
        }
        if (shortcuts.isNotEmpty() || panel != null) {
            Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(CORNER_GAP)) {
                shortcuts.forEach { (slot, s) ->
                    CornerPill(touchShortcutLabel(s.action), s.on) { down -> onShortcut(slot, down) }
                }
                if (panel != null) CornerPill("Panel", on = false) { down -> if (!down) panel() }
            }
        }
    }
}

/** A corner pill with its [label]: a touch shortcut's, or the Panel pill in the corner buttons' row. */
@Composable
private fun CornerPill(label: String, on: Boolean, modifier: Modifier = Modifier, onPress: (Boolean) -> Unit) {
    CornerButton(on, RoundedCornerShape(50), modifier.height(CORNER_BUTTON_HEIGHT).widthIn(min = CORNER_BUTTON_HEIGHT), onPress) {
        Text(
            label, color = cornerInk(on), fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold,
            fontSize = SHORTCUT_TEXT_SIZE.pinned(), maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * The game panel's Game pill, the Panel pill it answers' look ([PanelPill]) at its spot: bottom-right, clear of a
 * cutout on those sides, 10 dp in. The two switch pills land on one spot and look the same.
 */
@Composable
fun BoxScope.GamePillCorner(onClick: () -> Unit) {
    PanelPill(
        "Game", Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End + WindowInsetsSides.Bottom)).padding(10.dp),
        onClick = onClick,
    )
}

/** One corner button: the see-through fill (lit while [on]) and its ring; [onPress] true on the press, false on the release. */
@Composable
private fun CornerButton(on: Boolean, shape: Shape, modifier: Modifier, onPress: (Boolean) -> Unit, content: @Composable () -> Unit) {
    val press by rememberUpdatedState(onPress)
    Box(
        modifier.background(if (on) CORNER_ON_FILL else CORNER_OFF_FILL, shape).border(1.5.dp, CORNER_RING, shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    press(true)
                    waitForUpOrCancellation()?.consume()
                    press(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun cornerInk(on: Boolean): Color = if (on) CORNER_ON_INK else CORNER_OFF_INK

private val CORNER_MARGIN = 16.dp
private val CORNER_GAP = 10.dp
private val CORNER_BUTTON_HEIGHT = 52.dp
private val MIC_ICON_SIZE = 26.dp
private val SHORTCUT_TEXT_SIZE = 15.sp
private val CORNER_OFF_FILL = Color.Black.copy(alpha = 0.45f)   // role: see-through over the game, as the skin buttons
private val CORNER_ON_FILL = Color.White.copy(alpha = 0.9f)     // role: lit while on (the mic listens, a toggle runs)
private val CORNER_RING = Color.White.copy(alpha = 0.5f)        // role: the button's edge over any picture
private val CORNER_OFF_INK = Color.White.copy(alpha = 0.85f)    // role: the icon or word on the dark fill
private val CORNER_ON_INK = Color.Black                         // role: the icon or word on the lit fill
