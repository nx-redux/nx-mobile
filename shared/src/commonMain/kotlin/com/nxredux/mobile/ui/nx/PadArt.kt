package com.nxredux.mobile.ui.nx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * The consoles' controller pictures ([ConsolePad]): decoded once per process and kept, but ahead of time off the main
 * thread ([PrefetchPads]), so a selection step never decodes a WebP (about 7 ms each) inside a frame. A picture not yet
 * decoded when it is first drawn is decoded there and then. The map is touched on the main thread only.
 */
internal object PadArt {
    private val bitmaps = HashMap<DrawableResource, ImageBitmap>()

    fun cached(res: DrawableResource): ImageBitmap? = bitmaps[res]

    /** [res] decoded now, on the calling (main) thread, if no prefetch got there first. */
    fun now(res: DrawableResource): ImageBitmap = bitmaps.getOrPut(res) { runBlocking { decode(res) } }

    /** Decodes each of [pads] not yet held, in order, off the main thread, keeping each as it lands (main thread). */
    suspend fun prefetch(pads: List<DrawableResource>) {
        for (res in pads) {
            if (res in bitmaps) continue
            val bitmap = withContext(Dispatchers.Default) { decode(res) }
            bitmaps.getOrPut(res) { bitmap }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun decode(res: DrawableResource): ImageBitmap =
        getDrawableResourceBytes(getSystemResourceEnvironment(), res).decodeToImageBitmap()
}

/** [res]'s picture as a painter, from [PadArt] (decoded now only if no prefetch has done it yet). */
@Composable
internal fun padPainter(res: DrawableResource): Painter = remember(res) { BitmapPainter(PadArt.now(res)) }

/** A pad picture's aspect (height over width), from [PadArt], as [logoAspect] gives a logo's. */
internal fun padAspect(res: DrawableResource): Float? = PadArt.now(res).let { if (it.width > 0) it.height.toFloat() / it.width else null }

/**
 * Decodes [pads] ahead of time ([PadArt.prefetch]), nearest the selection [around] first, so a step through
 * the consoles finds each picture ready. Restarts only when the list changes; the order is the selection when it does.
 */
@Composable
internal fun PrefetchPads(pads: List<DrawableResource?>, around: () -> Int) {
    LaunchedEffect(pads) {
        PadArt.prefetch(padPrefetchOrder(pads, around()))
    }
}

/** [pads]' pictures (no nulls, no repeats) ordered by their distance from index [at], the nearer first, ties forward first. */
internal fun <T : Any> padPrefetchOrder(pads: List<T?>, at: Int): List<T> =
    pads.indices.sortedWith(compareBy<Int>({ kotlin.math.abs(it - at) }, { if (it >= at) 0 else 1 }))
        .mapNotNull { pads[it] }.distinct()
