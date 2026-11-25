package com.nxredux.mobile.storage

import com.nxredux.mobile.cheats.db.CheatDbManifest

/** What moves to the home folder when it changes, by path relative to the private root. */
object StorageLayout {
    /** [MOVE] goes with the home, [CHEATS] has its own rule, [STAY] (caches, credentials, art) stays private. */
    enum class Kind { MOVE, CHEATS, STAY }

    private const val SHARED = ".userdata/shared/"
    private const val ANDROID = ".userdata/android/"
    private const val HIDDEN = "hidden.txt"
    private const val BADGES = "badges.txt"
    private val STAY_FOLDERS = setOf("ra", "sidecars")

    /** The kind of [rel], a `/`-separated path relative to the private root. */
    fun kind(rel: String): Kind = when {
        rel == CheatDbManifest.PATH || rel.startsWith("Cheats/") -> Kind.CHEATS
        rel.startsWith("Saves/") || rel.startsWith(SHARED) -> Kind.MOVE
        rel.startsWith(ANDROID) -> androidKind(rel.removePrefix(ANDROID))
        else -> Kind.STAY
    }

    /** `hidden.txt`, `badges.txt`, and a file in a per-core folder (`<TAG>-<core>/`); every other android file stays. */
    private fun androidKind(tail: String): Kind {
        if (tail == HIDDEN || tail == BADGES) return Kind.MOVE
        val slash = tail.indexOf('/')
        if (slash <= 0) return Kind.STAY
        val folder = tail.substring(0, slash)
        return if ('-' in folder && folder !in STAY_FOLDERS) Kind.MOVE else Kind.STAY
    }
}
