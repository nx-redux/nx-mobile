package com.nxredux.mobile.achievements

/** One of the player's latest unlocks: the achievement [id] of the cached game [hash] ([game]: its title), at unix time [at]. */
data class RaRecentUnlock(val hash: String, val game: String, val id: Int, val title: String, val at: Long)

/** A recent unlock as the profile lists it: [points] and its [badge] image model, known once its game's set was read. */
data class RaProfileUnlock(val title: String, val game: String, val at: Long, val points: Int = 0, val badge: String? = null)

/**
 * The RetroAchievements page's summary of the signed-in player, from what the device has cached: how many
 * [achievements] are unlocked over how many games [played] (one unlock or more), the games [mastered] (every one
 * unlocked), the latest unlocks ([recent], newest first) and the games nearest to mastery ([inProgress]).
 */
data class RaProfile(
    val achievements: Int, val played: Int, val mastered: Int,
    val recent: List<RaProfileUnlock>, val inProgress: List<RaGameView>,
) {
    /** Nothing unlocked on this device's cached games yet: the page says so instead of three zeros. */
    val empty: Boolean get() = achievements == 0
}

object RaProfiles {
    /** How many games the profile lists as in progress, and how many unlocks as recent. */
    const val IN_PROGRESS = 3
    const val RECENT = 4

    /**
     * [games] (the browser's cached games) summed, with [recent] as read. In progress: started and not mastered, the
     * nearest to mastery first (the browser holds no time a game was last played), ties by title.
     */
    fun of(games: List<RaGameView>, recent: List<RaProfileUnlock>): RaProfile {
        val started = games.filter { it.unlocked > 0 && it.total > 0 }
        return RaProfile(
            achievements = started.sumOf { it.unlocked },
            played = started.size,
            mastered = started.count { it.unlocked >= it.total },
            recent = recent.take(RECENT),
            inProgress = started.filter { it.unlocked < it.total }
                .sortedWith(compareByDescending<RaGameView> { it.unlocked.toFloat() / it.total }.thenBy { it.title.lowercase() }).take(IN_PROGRESS),
        )
    }
}
