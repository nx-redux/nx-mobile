package com.nxredux.mobile.ui.nx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.nxredux.mobile.storage.FileRefs
import com.nxredux.mobile.ui.Listed
import com.nxredux.mobile.ui.appId
import org.jetbrains.compose.resources.painterResource

/** What a top screen picture draws: a picture filling its frame, a box art fitted over a blurred copy of itself, or a placeholder when the game has neither. */
sealed interface ShelfImage {
    data class Shot(val url: String) : ShelfImage
    data class Box(val url: String) : ShelfImage
    data object Placeholder : ShelfImage
}

/**
 * The memory cache key for [image]: its versioned ref, else null for Coil's own (path + modified time). A save state's
 * picture is rewritten in place by the game's process, and the menu's process can still see the old modified time
 * (Retroid Pocket Nova), so only the content stamp in the ref tells the new picture from the old one.
 */
internal fun topPictureCacheKey(image: ShelfImage): String? = when (image) {
    is ShelfImage.Shot -> image.url.takeIf(FileRefs::isVersioned)
    is ShelfImage.Box -> image.url.takeIf(FileRefs::isVersioned)
    ShelfImage.Placeholder -> null
}

/** The top screen pages' padding in a [width] x [height] screen: a share of the width every side, a wide, short screen's top and bottom capped by its height. */
internal fun topPagePadding(width: Dp, height: Dp): PaddingValues =
    PaddingValues(horizontal = width * TOP_PAD, vertical = minOf(width * TOP_PAD, height * TOP_PAD_MAX_H))

/** Trimmed line spacing: a title and the line under it read as one block. */
internal val TOP_TIGHT_LINES = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/**
 * [image] for [entry]: a picture cropped to the frame, or the box fitted over a blurred, darkened copy of itself; an
 * app's icon, else the console's logo or the game's name on a quiet surface. [onShotSize]: a picture's intrinsic size
 * once it loads.
 */
@Composable
internal fun TopPicture(entry: Listed, image: ShelfImage, modifier: Modifier, onShotSize: ((Size) -> Unit)? = null) {
    val colors = LocalNxColors.current
    val context = LocalPlatformContext.current
    val model: Any? = remember(image, context) {
        val url = (image as? ShelfImage.Shot)?.url ?: (image as? ShelfImage.Box)?.url
        topPictureCacheKey(image)?.let { ImageRequest.Builder(context).data(url).memoryCacheKey(it).build() } ?: url
    }
    Box(modifier.background(colors.pictureGround), contentAlignment = Alignment.Center) {
        when (image) {
            is ShelfImage.Shot -> AsyncImage(
                model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onSuccess = onShotSize?.let { report -> { state -> report(state.painter.intrinsicSize) } },
            )
            is ShelfImage.Box -> {
                AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(TOP_BLUR))
                Box(Modifier.fillMaxSize().background(BOX_DIM))
                AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            ShelfImage.Placeholder -> {
                val icon = rememberAppIcon(entry.appId)
                Box(Modifier.fillMaxSize().background(colors.modalCard), contentAlignment = Alignment.Center) {
                    val logo = consoleLogo(entry.system.tag)
                    when {
                        icon != null -> Image(icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.6f))
                        logo != null -> Image(painterResource(logo), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.6f), colorFilter = ColorFilter.tint(colors.hint))
                        else -> Text(
                            entry.shown, color = colors.hint, fontFamily = LocalNxFont.current, fontSize = 14.sp, maxLines = 3,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Whether a lift ends a tap: quick (a finger or palm resting on the cover is not one), within the touch slop, and not
 * consumed (a parent took the gesture, or the system cancelled it).
 */
internal fun isCleanTap(heldMs: Long, movedPx: Float, consumed: Boolean, timeoutMs: Long, slopPx: Float): Boolean =
    !consumed && heldMs <= timeoutMs && movedPx <= slopPx

/**
 * A tap that only taps: one finger, lifted within the long-press time and the touch slop of where it went down. A drag
 * or a second finger is not one, and nothing is consumed, so a swipe over it does what it did before.
 */
internal fun Modifier.cleanTap(onTap: () -> Unit): Modifier = composed {
    val action = rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var clean = true
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.size > 1) clean = false
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) clean = false
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) clean = false
                if (change.changedToUp()) {
                    val moved = (change.position - down.position).getDistance()
                    if (clean && isCleanTap(change.uptimeMillis - down.uptimeMillis, moved, change.isConsumed, viewConfiguration.longPressTimeoutMillis, viewConfiguration.touchSlop)) action.value()
                    break
                }
                if (!change.pressed) break
            }
        }
    }
}

private const val TOP_PAD = 0.07f            // of the width, every side
private const val TOP_PAD_MAX_H = 0.08f      // a wide, short screen's top and bottom padding: at most this of its height
private val TOP_BLUR: Dp = 16.dp
private val BOX_DIM = Color.Black.copy(alpha = 0.45f)   // role: darkens the blurred box copy so the fitted box stands out
