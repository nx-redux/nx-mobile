package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A modal button's and a segmented control's tap target. */
internal val NxModalControlHeight = 40.dp
/** The pad's ring: its stroke, and its gap from the control. */
private val RingStroke = 2.dp
private val RingGap = 3.dp
/** The room a control keeps round it for the ring, shown or not, so nothing moves when the pad arrives. */
internal val NxPadRingRoom = RingStroke + RingGap

/**
 * A modal's colours by job: the accent is the one thing the modal is for (its main action), the ink marks a pick, and
 * the pad's place is this ring round a control of [corner] (null: a pill). The ring's room is kept whether it shows or
 * not.
 */
@Composable
internal fun Modifier.nxPadRing(shown: Boolean, corner: Dp? = null): Modifier {
    val shape = if (corner == null) RoundedCornerShape(50) else RoundedCornerShape(corner + NxPadRingRoom)
    return (if (shown) border(RingStroke, LocalNxColors.current.main, shape) else this).padding(NxPadRingRoom)
}

/** The pad's ring is drawn only where there is a pad to move it with. */
@Composable
internal fun nxPadRings(): Boolean = LocalFooterMode.current == FooterMode.HINTS

/** How much a modal's button asks for the eye: its one main action, another beside it, or a plain word. */
internal enum class ModalActionWeight { MAIN, SECOND, QUIET }

/**
 * A modal's button: [weight] sets its look, which the pad never changes; [ring]: the pad is on it. The word alone: a
 * pad glyph on it would have to read on every accent and on the dark panel alike. A tap presses it.
 */
@Composable
internal fun NxModalAction(label: String, weight: ModalActionWeight, ring: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(50)
    val ground = when (weight) {
        ModalActionWeight.MAIN -> Modifier.background(nxHighlight(), shape)
        ModalActionWeight.SECOND -> Modifier.border(1.dp, colors.inkRing, shape)
        ModalActionWeight.QUIET -> Modifier
    }
    val ink = if (weight == ModalActionWeight.MAIN) colors.onAccent else colors.listText
    Box(
        modifier.nxPadRing(ring).heightIn(min = NxModalControlHeight * nxTextScale()).clip(shape).then(ground).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = ink, fontFamily = LocalNxFont.current, fontWeight = FontWeight.SemiBold, fontSize = nxModalButton(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A modal's actions side by side on its panel, under the card: the first is the main one, the rest beside it. [focus]:
 * the one the pad is on (none: -1).
 */
@Composable
internal fun NxModalActionRow(labels: List<String>, focus: Int, onPress: (Int) -> Unit) {
    val rings = nxPadRings()
    // The rings' own room makes most of the gap between two buttons.
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { i, label ->
            NxModalAction(
                label, if (i == 0) ModalActionWeight.MAIN else ModalActionWeight.SECOND, ring = rings && i == focus,
                modifier = Modifier.weight(1f),
            ) { onPress(i) }
        }
    }
}
