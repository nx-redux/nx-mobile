package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nxredux.mobile.shared.res.Res
import com.nxredux.mobile.shared.res.nx_mark
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** A centred empty state (mark, title, explanation, pills), like the handheld's UI_renderEmptyState. */
@Composable
fun NxEmptyState(
    title: String,
    description: String,
    hints: List<Hint>,
    onHint: (NxButton) -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource = Res.drawable.nx_mark,
) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Sized like a detail page: the headline and its secondary text, from the list label of this space.
        val label = nxListLabelSize()
        val body = NxTextSizes.secondary(label)
        Column(Modifier.padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(64.dp), colorFilter = ColorFilter.tint(colors.main))
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                color = colors.listText, fontFamily = font, fontWeight = FontWeight.SemiBold,
                fontSize = NxTextSizes.headline(label), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                description,
                color = colors.hint, fontFamily = font, fontSize = body,
                textAlign = TextAlign.Center, lineHeight = body * 1.35f,
            )
            Spacer(Modifier.height(24.dp))
            // Touch only has no hint bar: the actions are buttons ([emptyStateButtons]).
            // NxHintBar fills its width after our modifier; IntrinsicSize.Max pins that width to the pills themselves.
            if (touchOnly()) NxTouchButtons(emptyStateButtons(hints), onHint)
            else NxHintBar(hints, Modifier.width(IntrinsicSize.Max), onHint = onHint)
        }
    }
}
