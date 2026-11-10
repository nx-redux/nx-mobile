package com.nxredux.mobile.achievements

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.CoreRegistry

/** Registry tag → rcheevos console id (`rc_consoles.h`), with nx-redux's by-extension refinements. */
object RaConsoles {
    const val MEGA_DRIVE = 1; const val N64 = 2; const val SNES = 3; const val GAMEBOY = 4; const val GBA = 5; const val GBC = 6
    const val NES = 7; const val PC_ENGINE = 8; const val SEGA_CD = 9; const val MASTER_SYSTEM = 11; const val PLAYSTATION = 12
    const val LYNX = 13; const val NEOGEO_POCKET = 14; const val GAME_GEAR = 15; const val NDS = 18; const val POKEMON_MINI = 24
    const val ATARI_2600 = 25; const val ARCADE = 27; const val VIRTUAL_BOY = 28; const val SG1000 = 33; const val PSP = 41
    const val ATARI_5200 = 50; const val ATARI_7800 = 51; const val WONDERSWAN = 53; const val PC_ENGINE_CD = 76; const val FDS = 81
    const val SEGA_32X = 10; const val COLECOVISION = 44; const val DREAMCAST = 40
    /** One tag per core can hold either disc: the native hasher picks 16 vs 19 from the disc magic. */
    const val GAMECUBE = 16; const val WII = 19

    private val byTag = mapOf(
        "GB" to GAMEBOY, "SGB" to GAMEBOY, "GBC" to GBC, "GBA" to GBA, "MGBA" to GBA, "FC" to NES, "FDS" to FDS,
        "SFC" to SNES, "SUPA" to SNES, "NDS" to NDS, "N64" to N64, "PS" to PLAYSTATION, "PSX" to PLAYSTATION, "PSP" to PSP,
        "MD" to MEGA_DRIVE, "GPGX" to MEGA_DRIVE, "SMS" to MASTER_SYSTEM, "GG" to GAME_GEAR, "SG1000" to SG1000, "SEGACD" to SEGA_CD,
        "NGP" to NEOGEO_POCKET, "NGPC" to NEOGEO_POCKET, "PCE" to PC_ENGINE, "VB" to VIRTUAL_BOY, "LYNX" to LYNX,
        "A2600" to ATARI_2600, "A5200" to ATARI_5200, "A7800" to ATARI_7800, "PKM" to POKEMON_MINI, "WSC" to WONDERSWAN,
        "32X" to SEGA_32X, "COLECO" to COLECOVISION, "FBN" to ARCADE, "DC" to DREAMCAST,
        "WII" to WII, "GC" to GAMECUBE,
    )
    /**
     * The tag of RetroAchievements console [consoleId]: the first tag that maps to it, or the tag whose discs it is (PC
     * Engine CD); null for a console the app has no core for.
     */
    fun tagOf(consoleId: Int): String? =
        if (consoleId == PC_ENGINE_CD) "PCE" else byTag.entries.firstOrNull { it.value == consoleId }?.key

    private val discExtensions = setOf("cue", "chd", "iso", "m3u", "ccd", "toc")
    /**
     * What rcheevos hashes as a Dreamcast disc (an m3u as a playlist: its first disc). Not `.cdi` (rcheevos has no CDI
     * reader), and not a zip/7z: a NAOMI/Atomiswave set is not on RetroAchievements, and a zipped disc is left unidentified
     * here (not prefetched); the launch still hashes the disc it unpacks, by the inner name.
     */
    private val dreamcastDiscs = setOf("chd", "gdi", "cue", "m3u")

    /** [tag]'s console id for [fileName]; a GPGX_<X> tag is <X>'s ([CoreRegistry.baseTag]). */
    fun consoleId(tag: String, fileName: String): Int? {
        val base = byTag[CoreRegistry.baseTag(tag)] ?: return null
        val ext = Names.extensionOf(fileName).lowercase()
        return when (base) {
            MEGA_DRIVE -> when {
                ext == "sms" -> MASTER_SYSTEM
                ext == "gg" -> GAME_GEAR
                ext == "sg" -> SG1000
                ext in discExtensions -> SEGA_CD
                else -> MEGA_DRIVE
            }
            PC_ENGINE -> if (ext in discExtensions) PC_ENGINE_CD else PC_ENGINE
            DREAMCAST -> if (ext in dreamcastDiscs) DREAMCAST else null
            else -> base
        }
    }
}
