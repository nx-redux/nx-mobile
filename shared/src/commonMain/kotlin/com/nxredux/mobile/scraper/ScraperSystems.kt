package com.nxredux.mobile.scraper

import com.nxredux.mobile.runner.CoreRegistry

/**
 * ScreenScraper system ids by nx tag (NX Redux `scraper_systems.c`), case-insensitive (a GPGX_<X> tag as <X>,
 * [CoreRegistry.baseTag]), and NX Redux's extension refinement.
 */
object ScraperSystems {
    val EXCLUDED_TAGS: Set<String> = setOf("PORTS", "CUSTOM", "EXTRAS")

    /** ScreenScraper's Android system: only the Android games console's games use it, never a ROM folder's tag. */
    const val ANDROID = 63

    private val ids: Map<String, Int> = mapOf(
        "NES" to 3, "FC" to 3, "FAMICOM" to 3, "FDS" to 106, "SNES" to 4, "SFC" to 4, "SUPA" to 4, "N64" to 14,
        "GB" to 9, "SGB" to 9, "GBC" to 10, "GBA" to 12, "MGBA" to 12, "NDS" to 15, "DS" to 15, "VB" to 11,
        "PKM" to 211, "POKE" to 211, "POKEMINI" to 211, "GC" to 13, "WII" to 16, "3DS" to 17,
        "SMS" to 2, "GG" to 21, "MD" to 1, "GPGX" to 1, "GENESIS" to 1, "GEN" to 1, "SMD" to 1, "32X" to 19,
        "SEGA32X" to 19, "SEGACD" to 20, "SCD" to 20, "MEGACD" to 20, "MDCD" to 20, "SATURN" to 22, "SS" to 22,
        "SAT" to 22, "DC" to 23, "SG1000" to 109, "SG" to 109,
        "PS" to 57, "PSX" to 57, "PS1" to 57, "PS2" to 58, "PSP" to 61, "PPSSPP" to 61,
        "PCE" to 31, "PCECD" to 114, "PCCD" to 114, "TGCD" to 114, "TG16CD" to 114, "TGFXCD" to 114, "TGFX" to 31,
        "TGFX16" to 31, "TG16" to 31, "PCENGINE" to 31, "SGFX" to 105, "SGX" to 105, "PCFX" to 72,
        "A2600" to 26, "ATARI" to 26, "ATARI2600" to 26, "A800" to 43, "ATARI800" to 43, "A5200" to 40, "A7800" to 41,
        "LYNX" to 28, "LNX" to 28, "JAGUAR" to 27, "JAG" to 27, "ST" to 42, "ATARIST" to 42,
        "NGP" to 25, "NGPC" to 82, "NEOGEO" to 142, "NEO" to 142, "NEOCD" to 70, "NEOGEOCD" to 70, "NGCD" to 70,
        "FBN" to 75, "FBNEO" to 75, "ARCADE" to 75, "MAME" to 75, "FBA" to 75, "MAME2003" to 75, "MAME2003PLUS" to 75,
        "MAME2010" to 75, "CPS1" to 6, "CPS2" to 7, "CPS3" to 8,
        "MSX" to 113, "MSX2" to 116, "CPC" to 65, "AMSTRAD" to 65, "C64" to 66, "C128" to 66, "VIC" to 73, "VIC20" to 73,
        "PET" to 240, "PLUS4" to 99, "AMIGA" to 64, "PUAE" to 64, "ZX" to 76, "ZXSPECTRUM" to 76, "SPECTRUM" to 76,
        "ZXS" to 76, "X68000" to 79,
        "COLECO" to 48, "INTELLIVISION" to 115, "INTV" to 115, "INTELLI" to 115, "CV" to 48,
        "WS" to 45, "WSC" to 46, "WSWAN" to 45, "WSWANC" to 46,
        "ODYSSEY" to 104, "O2EM" to 104, "ODYSSEY2" to 104, "VIDEOPAC" to 104, "3DO" to 29, "MEGADUCK" to 90, "GW" to 52,
        "SUPERVISION" to 207, "SV" to 207, "VECTREX" to 102, "CHANNELF" to 80, "ARDUBOY" to 263, "PICO8" to 234,
        "P8" to 234, "PICO" to 234, "TIC80" to 222, "SCUMMVM" to 123, "SCUMM" to 123, "DOS" to 135, "PRBOOM" to 135,
        "DOOM" to 135,
    )

    fun baseIdFor(tag: String): Int? = ids[CoreRegistry.baseTag(tag)]

    /** The whole tag table (the other sources' maps are tested against every id in it). */
    val table: Map<String, Int> get() = ids

    /** NX Redux `getIdForRom`: DC archives are arcade; the MD family refines by the file's last extension. */
    fun systemIdFor(tag: String, fileName: String): Int? {
        val id = baseIdFor(tag) ?: return null
        val name = fileName.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        val ext = if (dot <= 0) "" else name.substring(dot + 1).lowercase()
        return when (id) {
            23 -> if (ext == "zip" || ext == "7z") 75 else 23
            1 -> when (ext) {
                "cue", "chd", "ccd", "toc", "m3u", "iso" -> 20
                "sms" -> 2
                "gg" -> 21
                "sg" -> 109
                else -> 1
            }
            else -> id
        }
    }
}
