package com.nxredux.mobile.ui.nx

import coil3.map.Mapper

/**
 * A local picture given as a file ref (a ROM folder's art, a save state's screenshot; [com.nxredux.mobile.storage.FileRefs.artPath])
 * handed to Coil as a file, never as a URI string (a name with '#' or '?' would lose its tail). Null where the platform
 * has no files.
 */
internal expect fun fileRefMapper(): Mapper<String, *>?
