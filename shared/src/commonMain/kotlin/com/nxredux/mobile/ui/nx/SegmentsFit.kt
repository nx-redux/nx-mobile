package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** The least of a touch row's width its text keeps beside segments on its right, so its description still reads. */
internal const val SEGMENTS_TEXT_MIN_SHARE = 0.4f

/**
 * Whether a segmented control of choices fits on a line of its own [lineWidth] wide (under a touch row's text): its
 * natural width as [rememberSegmentsFitRight] measures it. A row that must be segmented falls back to a picker when not.
 */
@Composable
internal fun rememberSegmentsFitLine(valueSize: TextUnit, lineWidth: Dp): (List<String>) -> Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val font = LocalNxFont.current
    return remember(valueSize, lineWidth, measurer, density, font) {
        val style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = valueSize * 0.9f)
        val cache = HashMap<List<String>, Boolean>()
        val fits: (List<String>) -> Boolean = { choices ->
            cache.getOrPut(choices) {
                val text = with(density) { choices.sumOf { measurer.measure(it, style).size.width }.toDp() }
                text + 20.dp * choices.size + 6.dp <= lineWidth
            }
        }
        fits
    }
}

/**
 * Whether a segmented control of choices fits on the right of a touch row [rowWidth] wide, beside its name: the control's
 * natural width (each label at 0.9 × [valueSize] with its 10 dp sides, the track's 2 dp inset and 1 dp border) plus the
 * 12 dp gap, next to the name in full (bold, [labelSize]) with room for its info icon, and never leaving the text under
 * [SEGMENTS_TEXT_MIN_SHARE] of the row.
 */
@Composable
internal fun rememberSegmentsFitRight(valueSize: TextUnit, labelSize: TextUnit, rowWidth: Dp): (String, List<String>) -> Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val font = LocalNxFont.current
    return remember(valueSize, labelSize, rowWidth, measurer, density, font) {
        val style = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = valueSize * 0.9f)
        val nameStyle = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = labelSize)
        // The info icon beside the name: its disc (1.25 × the description, ~0.9 × the name) and its padding.
        val iconRoom = with(density) { (labelSize * 1.15f).toDp() } + 12.dp
        val cache = HashMap<Pair<String, List<String>>, Boolean>()
        val fits: (String, List<String>) -> Boolean = { name, choices ->
            cache.getOrPut(name to choices) {
                val text = with(density) { choices.sumOf { measurer.measure(it, style).size.width }.toDp() }
                val nameW = with(density) { measurer.measure(name, nameStyle).size.width.toDp() } + iconRoom
                text + 20.dp * choices.size + 6.dp + 12.dp <= rowWidth - maxOf(nameW, rowWidth * SEGMENTS_TEXT_MIN_SHARE)
            }
        }
        fits
    }
}
