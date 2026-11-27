package com.nxredux.mobile.runner

/**
 * 3DS Download Play's hand-off to the downloaded game: before the client restarts into the child, it names it in a file
 * on the emulated SD card ([PICK_FILE]); the app looks the full game up in the title index ([INDEX_FILE], written at
 * every 3DS launch) and applies that game's own core options, then answers with [ACK_FILE]. The renderer, the 3D
 * resolution, the layout and the controls stay the row's: they are set when the session starts.
 */
object N3dsDownloadPlayHandoff {
    /** In Azahar's user folder (`<saves>/Azahar/`), as the client writes to `sdmc:/nx-redux/`. */
    const val PICK_FILE = "sdmc/nx-redux/download-play.txt"
    const val ACK_FILE = "sdmc/nx-redux/download-play.ack"
    /** In the 3DS core's config folder: one `title id<TAB>file name` line per game launched. */
    const val INDEX_FILE = "titles.tsv"

    private const val CHILD_HIGH = 0x00040001L
    private const val GAME_HIGH = 0x0004000000000000L

    /** Launch-time or app-owned options a child's session must not take from the full game. */
    private val ROW_OWNED = setOf(
        "citra_graphics_api", "citra_resolution_factor", "citra_is_new_3ds", "citra_use_libretro_save_path",
        "citra_use_disk_shader_cache", "citra_render_3d",
        "citra_layout_option", "citra_frontend_layout", "citra_swap_screen", "citra_swap_screen_mode",
        "citra_large_screen_proportion", "citra_stacked_small_proportion", "citra_touch_main_only", "citra_cursor_visible",
        "citra_input_type", "citra_analog_function", "citra_analog_deadzone", "citra_enable_mouse_touchscreen",
        "citra_enable_touch_touchscreen", "citra_enable_motion", "citra_motion_sensitivity",
    )

    /** The full game a Download Play child (title id in hex) belongs to; null for anything else. */
    fun parentOf(childHex: String): Long? {
        val child = childHex.trim().toULongOrNull(16)?.toLong() ?: return null
        if (child ushr 32 != CHILD_HIGH) return null
        return GAME_HIGH or (child and 0xFFFFFFFFL)
    }

    /** The full game's core options a child's session takes: the gameplay ones, not the row's own. */
    fun liveOptions(game: Map<String, String>): Map<String, String> =
        game.filterKeys { it.startsWith("citra_") && it !in ROW_OWNED }

    /** The title index as a map; broken lines are skipped. */
    fun titles(text: String): Map<Long, String> = text.lineSequence().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0 || tab == line.length - 1) return@mapNotNull null
        val id = line.substring(0, tab).toULongOrNull(16)?.toLong() ?: return@mapNotNull null
        id to line.substring(tab + 1)
    }.toMap()

    /** [text] with [titleId] naming [fileName] (a game renamed or re-dumped replaces its old line). */
    fun withTitle(text: String, titleId: Long, fileName: String): String =
        (titles(text) + (titleId to fileName)).entries.joinToString("") { (id, name) ->
            "${id.toULong().toString(16).padStart(16, '0').uppercase()}\t$name\n"
        }
}
