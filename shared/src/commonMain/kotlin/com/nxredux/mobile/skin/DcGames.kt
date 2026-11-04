package com.nxredux.mobile.skin

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.library.NaomiSets

/**
 * flycast's games under the Dreamcast tag: discs (the Dreamcast skin, starting on the stick) and NAOMI / Atomiswave
 * sets (the arcade skin, on the d-pad). A set is told apart as the launcher does ([NaomiSets.loadsAsIs]): an archive
 * whose name is in flycast's set table; any other archive is a zipped disc.
 */
object DcGames {
    fun isArcadeSet(tag: String, fileName: String, sets: NaomiSets): Boolean =
        tag.uppercase() == "DC" && Names.isArchive(fileName) && !sets.isEmpty && sets.loadsAsIs(fileName)

    /** A Dreamcast disc starts on the stick (most of its games steer with it); arcade sets and other consoles do not. */
    fun stickFirst(baseTag: String, arcadeSet: Boolean): Boolean = baseTag.uppercase() == "DC" && !arcadeSet
}
