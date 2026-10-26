package com.nxredux.mobile.ui.nx

import coil3.map.Mapper
import coil3.request.Options
import com.nxredux.mobile.storage.FileRefs
import java.io.File

/** A local picture as a [File] (its `?v=` version dropped): Coil's own File mapper and keyer take it from there (keyed by path and last-modified). */
private object FileRefArtMapper : Mapper<String, File> {
    override fun map(data: String, options: Options): File? = FileRefs.artPath(data)?.let(::File)
}

internal actual fun fileRefMapper(): Mapper<String, *>? = FileRefArtMapper
