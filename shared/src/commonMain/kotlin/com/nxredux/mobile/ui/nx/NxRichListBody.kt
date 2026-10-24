package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A tool page around an [NxRichList]: the grey title, the list centred below it between its up and down scroll chevrons
 * (each in a strip of the same height, as far from the list), and the hint bar. [emptyText] replaces an empty list.
 * With a [sidePane], the list takes [listFraction] of the width and the pane fills the rest, between the title and the
 * hint bar.
 */
@Composable
fun NxRichListBody(
    title: String, rows: List<NxRichRow>, selectedIndex: Int, hints: List<Hint>,
    onSelect: (Int) -> Unit, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, onHint: ((NxButton) -> Unit)? = null,
    emptyText: String? = null,
    sidePane: (@Composable BoxScope.() -> Unit)? = null, listFraction: Float = 0.45f,
    onLongPress: ((Int) -> Unit)? = null,
    titleSuffix: String? = null,   // a part of the title a cut never reaches (a count), drawn after it
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val listState = rememberLazyListState()
    // Touch only, the hints' icon stand-ins ([HintTouch.Icon]) sit at the title's right.
    val touch = touchOnly()
    val icons: (@Composable () -> Unit)? = if (touch && onHint != null) ({ NxTopIcons(hints.touchIcons(onHint)) }) else null
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pitch = nxOptionsRowHeight(NxListPitch, LocalNxOptionText.current)   // Appearance → Text size
        val label = nxListLabelSize(maxHeight, pitch)
        val inset = nxListInset()
        Column(Modifier.fillMaxSize()) {
            // The body ends at the hint bar's top edge. Under the title row the list fades where more scrolls in (no
            // arrows), a quarter of a plain row clear above the hints, as the main menu's. The rich rows tile the slot whole
            // (NxRichList sizes them to it), and [NxTitledBody] centres the block by eye between the title and the hints.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val n = maxOf(2, nxRowCountFor(maxHeight, pitch))
                val bottomGap = maxHeight / n / 4
                NxTitledBody(title, Modifier.fillMaxSize(), titleSuffix, trailing = icons) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            Column(if (sidePane == null) Modifier.fillMaxSize() else Modifier.weight(listFraction).fillMaxHeight()) {
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    if (rows.isEmpty() && emptyText != null) {
                                        Text(emptyText, color = colors.hint, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = label, textAlign = TextAlign.Center)
                                    } else {
                                        // The list starts so the thumbnail's left edge sits on the title's first letter (the inset); the
                                        // capsule keeps its padding around it, so its edge lies that padding left of the image.
                                        NxRichList(
                                            rows, selectedIndex, onSelect, onOpen,
                                            Modifier.fillMaxSize().nxScrollFade(listState, height = 56.dp, strong = true).padding(start = inset - NxRichThumbGap),
                                            listState = listState, onLongPress = onLongPress, labelSize = label, edgeMargin = 1,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(bottomGap))
                            }
                            if (sidePane != null) {
                                Box(Modifier.weight(1f - listFraction).fillMaxHeight(), contentAlignment = Alignment.Center, content = sidePane)
                            }
                        }
                    }
                }
            }
            NxHintBar(hints, onHint = onHint)
        }
    }
}
