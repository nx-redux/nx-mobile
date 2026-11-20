package com.nxredux.mobile.runner

import android.content.Context
import com.nxredux.mobile.bios.KnownBios

/** assets/bios/known.tsv (tools/bios-list), parsed once per process; empty when missing. */
object KnownBiosAsset {
    @Volatile private var cached: KnownBios? = null

    fun get(context: Context): KnownBios = cached ?: synchronized(this) {
        cached ?: runCatching {
            context.assets.open("bios/known.tsv").use { KnownBios.parse(it.readBytes().decodeToString()) }
        }.getOrDefault(KnownBios.EMPTY).also { cached = it }
    }
}
