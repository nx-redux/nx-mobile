package com.nxredux.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nxredux.mobile.ui.nx.LocalNxColors
import com.nxredux.mobile.ui.nx.LocalNxFont

/** The bar's button: ends the parent's access, back to what the schedule gives the kid. */
internal const val KIDS_BAR_BUTTON = "Back to Kids Mode"

/** The bar's note on the right. */
internal const val KIDS_BAR_NOTE = "Kids Mode is active"

/**
 * Over the menu while a parent has the full menu (Kids Mode's Parent mode): on the left the button that ends it
 * ([onClose]), on the right [KIDS_BAR_NOTE]. A controller ends it with Tools' Lock Kids Mode row.
 */
@Composable
internal fun KidsParentBar(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    val font = LocalNxFont.current
    val ink = Color.Black   // on the warning amber
    Row(
        modifier.fillMaxWidth().background(colors.warning).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, ink, RoundedCornerShape(18.dp))
                .clickable(onClick = onClose).padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(KIDS_BAR_BUTTON, color = ink, fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(KIDS_BAR_NOTE, color = ink, fontFamily = font, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
