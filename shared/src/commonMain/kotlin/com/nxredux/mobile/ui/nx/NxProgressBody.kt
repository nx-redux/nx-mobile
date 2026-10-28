package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A title, one status line, an optional bar and an optional detail line (the current item), over the hint bar: the shape of a long-running tool page. */
@Composable
fun NxProgressBody(title: String, line: String, fraction: Float?, hints: List<Hint>, modifier: Modifier = Modifier, detail: String? = null, onHint: ((NxButton) -> Unit)? = null) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val inset = nxListInset()   // the list's row-label inset, as on NxDetailListBody
        val label = nxListLabelSize()   // sizes follow the list row labels, as on NxDetailListBody
        val status = NxTextSizes.secondary(label)
        Column(Modifier.fillMaxSize()) {
            NxPageTitle(title, bottom = 8.dp)
            Spacer(Modifier.weight(1f))
            NxHintBar(hints, onHint = onHint)
        }
        // Centred on the whole page, not the space between the title and the hint bar, so it sits the same with or
        // without a hint bar (touch only has none). The title and the bar are drawn around it.
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            // The secondary size: a status line, not a page headline; the detail line is the caption, one step below it.
            // The gaps follow the status size (1.0 x above the bar, 0.75 x above the detail line).
            Text(line, color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = status, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
            if (fraction != null) {
                Spacer(Modifier.height(status.value.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(6.dp).background(colors.hint.copy(alpha = 0.35f), RoundedCornerShape(3.dp))) {   // role: a progress bar's track
                    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(colors.listText, RoundedCornerShape(3.dp)))
                }
            }
            if (detail != null) {
                Spacer(Modifier.height((status.value * 0.75f).dp))
                Text(detail, color = colors.hint, fontFamily = font, fontSize = NxTextSizes.caption(label), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}
