package com.nxredux.mobile.achievements

import kotlinx.serialization.Serializable

/** rcheevos' "Unknown Emulator" pseudo-achievement: hidden in softcore everywhere. */
const val UNKNOWN_EMULATOR_ID = 101000001

enum class RaAchievementState { LOCKED, UNLOCKED, PENDING }

enum class RaType(val label: String) { MISSABLE("Missable"), PROGRESSION("Progression"), WIN("Win Condition") }

data class RaAchievement(
    val id: Int, val title: String, val description: String, val points: Int, val badge: String,
    val state: RaAchievementState, val unlockedAt: Long?, val progress: String?, val percent: Float, val rarity: Float, val type: RaType?,
)

enum class RaSortOrder(val label: String) {
    UNLOCKED_FIRST("Unlocked first"), DISPLAY_ORDER_FIRST("Display order"), DISPLAY_ORDER_LAST("Display order (reverse)"),
    WON_BY_MOST("Most common first"), WON_BY_LEAST("Rarest first"), POINTS_MOST("Most points first"), POINTS_LEAST("Fewest points first"),
    TITLE_AZ("Title A–Z"), TITLE_ZA("Title Z–A"), TYPE_ASC("Type"), TYPE_DESC("Type (reverse)");
    companion object { val DEFAULT = UNLOCKED_FIRST }
}

private fun typeRank(t: RaType?) = when (t) { null -> 0; RaType.MISSABLE -> 1; RaType.PROGRESSION -> 2; RaType.WIN -> 3 }

/** The 11 nx-redux orders; "display order" is the order the server sent (the list's own order). Stable. */
fun List<RaAchievement>.sortedBy(order: RaSortOrder): List<RaAchievement> = when (order) {
    RaSortOrder.UNLOCKED_FIRST -> sortedBy { if (it.state == RaAchievementState.LOCKED) 1 else 0 }
    RaSortOrder.DISPLAY_ORDER_FIRST -> this
    RaSortOrder.DISPLAY_ORDER_LAST -> reversed()
    RaSortOrder.WON_BY_MOST -> sortedByDescending { it.rarity }
    RaSortOrder.WON_BY_LEAST -> sortedBy { it.rarity }
    RaSortOrder.POINTS_MOST -> sortedByDescending { it.points }
    RaSortOrder.POINTS_LEAST -> sortedBy { it.points }
    RaSortOrder.TITLE_AZ -> sortedBy { it.title.lowercase() }
    RaSortOrder.TITLE_ZA -> sortedByDescending { it.title.lowercase() }
    RaSortOrder.TYPE_ASC -> sortedBy { typeRank(it.type) }
    RaSortOrder.TYPE_DESC -> sortedByDescending { typeRank(it.type) }
}

/** `cache/games/<md5>/game.json`: which library entry a cached game belongs to (for the browser's title and art). */
@Serializable
data class RaGamePointer(val tag: String, val source: String, val dir: String, val file: String, val title: String)

/** One row of the Tools browser; [console] is the console it is listed under, [consoleTag] its tag (its logo), null for [RaConsoleGroups.OTHER]. */
data class RaGameView(
    val hash: String, val gameId: Int, val title: String, val art: String?, val unlocked: Int, val total: Int, val pending: Int,
    val console: String = RaConsoleGroups.OTHER, val consoleTag: String? = null,
)

/** One row of the Tools browser's first level: a console's cached [games] and their progress summed. */
data class RaConsoleView(val name: String, val tag: String?, val games: List<RaGameView>) {
    val unlocked: Int get() = games.sumOf { it.unlocked }
    val total: Int get() = games.sumOf { it.total }
}

/** A cached game's RetroAchievements progress for the menu: unlocked (pending included) of [total], and the first locked achievement's title in display order. */
data class RaProgress(val unlocked: Int, val total: Int, val next: String?)
