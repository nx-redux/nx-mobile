package com.nxredux.mobile.runner

import com.nxredux.mobile.storage.FileRefs

/**
 * A [LoadMode.REAL_PATH] core (prboom) opens the game by path. A game reached through a file ref (All files access)
 * already has one, so it is opened in place; only a SAF game is copied into the ROM cache first.
 */
object RealPathLaunch {
    /**
     * The path [content] can be opened at as it is, or null when it must be copied: it is a file ref whose name is the one
     * the core must see ([fileName]: an archive loaded as is goes lowercase), and every companion (a disc sheet's tracks)
     * is a file ref beside it under the name the sheet uses.
     */
    fun inPlace(content: ContentRef, fileName: String, companions: List<Pair<String, ContentRef>>): String? {
        if (!FileRefs.isFile(content)) return null
        val path = FileRefs.path(content)
        val dir = path.substringBeforeLast('/', "")
        if (path.substringAfterLast('/') != fileName) return null
        val beside = companions.all { (name, ref) -> FileRefs.isFile(ref) && FileRefs.path(ref) == "$dir/$name" }
        return path.takeIf { beside }
    }
}
