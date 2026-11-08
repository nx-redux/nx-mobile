package com.nxredux.mobile.launcher

import androidx.compose.ui.graphics.ImageBitmap
import com.nxredux.mobile.tryOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Android apps' icons by id, each asked of [launcher] once: tiles look an app's icon up here instead of loading it on
 * every composition. The full-colour icon and the [mono] one (its [AppLauncher.silhouette]) are kept apart. A missing
 * one (null) is kept too; its tile draws the generic app glyph. Loads run in [scope], so
 * tiles asking at once share one, and one finishes for the cache when its tile leaves. They stay for the session, so
 * the app drawer reopens drawn; [retain] drops uninstalled apps' icons. Main thread only.
 */
class AppIcons(private val launcher: AppLauncher, private val scope: CoroutineScope) {
    private data class Key(val id: String, val mono: Boolean)

    private val loaded = HashMap<Key, ImageBitmap?>()
    private val loading = HashMap<Key, Deferred<ImageBitmap?>>()

    /** [id]'s icon if it has loaded already (a tile's first frame); null before then, or when it has none. */
    fun cached(id: String, mono: Boolean = false): ImageBitmap? = loaded[Key(id, mono)]

    /** [id]'s icon, loaded through [launcher] the first time (or joining the load under way) and cached. */
    suspend fun load(id: String, mono: Boolean = false): ImageBitmap? {
        val key = Key(id, mono)
        if (key in loaded) return loaded[key]
        val pending = loading.getOrPut(key) {
            scope.async {
                // A failure is a missing icon, never the scope's end.
                tryOrNull { if (mono) launcher.silhouette(id) else launcher.icon(id) }
            }
        }
        val icon = pending.await()
        // The first caller back caches it; none does when [retain] dropped the app meanwhile.
        if (loading[key] === pending) { loading.remove(key); loaded[key] = icon }
        return icon
    }

    /** Keeps only [ids]' icons (and loads), both kinds, dropping the rest. */
    fun retain(ids: Collection<String>) {
        val keep = ids.toSet()
        loaded.keys.retainAll { it.id in keep }
        loading.keys.retainAll { it.id in keep }
    }
}
