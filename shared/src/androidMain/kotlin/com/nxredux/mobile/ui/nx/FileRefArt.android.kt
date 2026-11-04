package com.nxredux.mobile.ui.nx

import coil3.map.Mapper
import coil3.request.Options
import com.nxredux.mobile.storage.FileRefs
import java.io.File

/**
 * A local picture as a [File] (its `?v=` version dropped). Coil keys the memory cache on the mapped File, so the version
 * no longer counts there: a picture rewritten in place (a save state's screenshot) needs its request's memoryCacheKey set
 * to the versioned ref, as the slot strip and the resume pictures do.
 */
private object FileRefArtMapper : Mapper<String, File> {
    override fun map(data: String, options: Options): File? = FileRefs.artPath(data)?.let(::File)
}

internal actual fun fileRefMapper(): Mapper<String, *>? = FileRefArtMapper
