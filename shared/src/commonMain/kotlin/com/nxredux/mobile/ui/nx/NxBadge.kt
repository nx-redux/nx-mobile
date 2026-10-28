package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** A square achievement badge on a dark tile; [model] is an image model (a `content://` or `file://` URI); null draws nothing. */
@Composable
fun NxBadge(model: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 4.dp) {
    if (model == null) return
    val shape = RoundedCornerShape(corner)
    Box(modifier.size(size).clip(shape).background(LocalNxColors.current.pictureGround, shape)) {
        AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}
