package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A page's note in an alert box under its title, above its rows (Settings → Library → Saves): a "!" badge and [text]
 * in the warning colour's tint, at the list's inset.
 */
@Composable
fun NxBanner(text: String, modifier: Modifier = Modifier) {
    val colors = LocalNxColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().padding(start = nxListInset(), end = nxListInset(), top = 8.dp, bottom = 20.dp)   // more room under it, before the rows
            .background(colors.warning.copy(alpha = 0.12f), shape).border(1.dp, colors.warning.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).background(colors.warning, CircleShape), contentAlignment = Alignment.Center) {
            Text("!", color = colors.background, fontFamily = LocalNxFont.current, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(text, color = colors.listText, fontFamily = LocalNxFont.current, fontSize = nxModalText())
    }
}
