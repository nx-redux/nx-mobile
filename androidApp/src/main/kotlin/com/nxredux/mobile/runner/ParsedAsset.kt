package com.nxredux.mobile.runner

import android.content.Context

/** The asset file [path], parsed once per process; [empty] when it is missing or unreadable. */
abstract class ParsedAsset<T : Any>(private val path: String, private val empty: T, private val parse: (String) -> T) {
    @Volatile private var cached: T? = null

    fun get(context: Context): T = cached ?: synchronized(this) {
        cached ?: runCatching { context.assets.open(path).use { parse(it.readBytes().decodeToString()) } }
            .onFailure(::onUnreadable).getOrDefault(empty).also { cached = it }
    }

    protected open fun onUnreadable(e: Throwable) = Unit
}
