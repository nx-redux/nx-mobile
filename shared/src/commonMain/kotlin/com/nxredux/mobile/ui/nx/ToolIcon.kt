package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.nxredux.mobile.launcher.AppIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** The session's Android app icons ([AppIcons]); null on a platform without a launcher (every app tile draws the glyph). */
val LocalAppIcons = staticCompositionLocalOf<AppIcons?> { null }

/**
 * App [id]'s icon, full colour or its [mono] silhouette: the cached one at once, else loaded in the background; null
 * with no [id], no icons, or none loaded.
 */
@Composable
internal fun rememberAppIcon(id: String?, mono: Boolean = false): ImageBitmap? {
    val icons = LocalAppIcons.current
    if (id == null || icons == null) return null
    val icon by produceState(icons.cached(id, mono), icons, id, mono) { value = icons.load(id, mono) }
    return icon
}

/**
 * A tool's icon tinted [tint]: an Android app ([appId]) draws its icon's silhouette, any other tool (or an app with no
 * silhouette, or one still loading) its white glyph [icon].
 */
@Composable
internal fun ToolIcon(icon: DrawableResource, appId: String?, tint: Color, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val app = rememberAppIcon(appId, mono = true)
    if (app != null) Image(app, contentDescription = null, modifier = modifier, contentScale = contentScale, colorFilter = ColorFilter.tint(tint))
    else Image(painterResource(icon), contentDescription = null, modifier = modifier, contentScale = contentScale, colorFilter = ColorFilter.tint(tint))
}

/** [ToolIcon] tinted [tint] read while drawing, so a changing colour redraws the icon, never recomposes it. */
@Composable
internal fun ToolIcon(icon: DrawableResource, appId: String?, tint: () -> Color, modifier: Modifier = Modifier) {
    val app = rememberAppIcon(appId, mono = true)
    val glyph = painterResource(icon)
    val painter = remember(app, glyph) { app?.let { BitmapPainter(it) } ?: glyph }
    Box(modifier.drawFitTinted(painter, tint))
}

/** [painter] Fit in this box, centred, tinted [tint] (read while drawing). */
internal fun Modifier.drawFitTinted(painter: Painter, tint: () -> Color): Modifier = drawBehind {
    val src = painter.intrinsicSize
    if (src.width <= 0f || src.height <= 0f) return@drawBehind
    val fit = minOf(size.width / src.width, size.height / src.height)
    val drawn = Size(src.width * fit, src.height * fit)
    translate((size.width - drawn.width) / 2, (size.height - drawn.height) / 2) {
        with(painter) { draw(drawn, colorFilter = ColorFilter.tint(tint())) }
    }
}
