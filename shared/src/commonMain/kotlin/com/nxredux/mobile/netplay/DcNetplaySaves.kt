package com.nxredux.mobile.netplay

import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.CoreRegistry

/**
 * A Dreamcast session's files, byte-compatible with the handheld's DC.pak (nx_dc_netplay.sh): the host serves its game's
 * memory card, console settings, second card or an arcade game's saves under fixed wire names, and the joiner plays on
 * copies of them. Where each lives is flycast's layout with the runner's folders: the save directory holds the per-game
 * card (`<content stem>.A1.bin`), the shared second card (`vmu_save_A2.bin`) and arcade saves (`reicast/<content>.<ext>`);
 * the system directory's `dc/` holds the console settings (`dc_nvmem.bin`) and the BIOS.
 */
object DcNetplaySaves {
    const val CARD = "nxcard.bin"
    const val NVMEM = "nxnvmem.bin"
    const val CARD_A2 = "nxa2.bin"
    private const val ARCADE_STEM = "nxarc"
    private val ARCADE_EXTENSIONS = listOf("nvmem", "nvmem2", "eeprom")

    /** The handheld's `--fetch-files` list: the only names a host offers and a joiner takes. */
    val WIRE_NAMES: Set<String> = setOf(CARD, NVMEM, CARD_A2) + ARCADE_EXTENSIONS.map { "$ARCADE_STEM.$it" }

    /** The joiner's session copy keeps the console's `dc/` folder here, under the session's save directory. */
    const val SESSION_SYSTEM = "system"

    /** flycast's BIOS folder under the system directory. */
    const val BIOS_SUBDIR = "dc"
    const val BIOS_FILE = "dc_boot.bin"
    const val NVMEM_FILE = "dc_nvmem.bin"
    const val CARD_A2_FILE = "vmu_save_A2.bin"

    /** The `dcbios=` value for a device without a Dreamcast BIOS. */
    const val NO_BIOS = "none"

    /** `dcbios=`: the first 12 hex digits of the BIOS file's MD5 (the handheld's `md5sum | cut -c1-12`), or [NO_BIOS]. */
    fun fingerprint(md5Hex: String?): String = md5Hex?.lowercase()?.take(12)?.takeIf { it.length == 12 } ?: NO_BIOS

    /** Both sides boot the real BIOS only with the same one (a mismatch would desync); otherwise flycast's HLE BIOS. */
    fun hle(own: String?, peer: String?): Boolean = own == null || own == NO_BIOS || own != peer

    /** The per-game card flycast opens for [romFileName]: named after the content file it was given ([Names.stripExtension]). */
    fun cardName(romFileName: String): String = "${Names.stripExtension(romFileName)}.A1.bin"

    /** An arcade game's save for [ext], relative to the save directory: flycast's `reicast/<content name>.<ext>`. */
    fun arcadeName(romFileName: String, ext: String): String = "reicast/${CoreRegistry.contentName("DC", romFileName)}.$ext"

    /**
     * What the host serves: wire name to the file relative to its save directory, or (for [NVMEM]) to its system
     * directory's `dc/`. A disc game sends its cards and settings, an [arcade] set (NAOMI / Atomiswave, loaded as is) its
     * saves and settings.
     */
    fun hostFiles(romFileName: String, arcade: Boolean): List<Served> = buildList {
        if (arcade) {
            for (ext in ARCADE_EXTENSIONS) add(Served("$ARCADE_STEM.$ext", arcadeName(romFileName, ext), inSystem = false))
        } else {
            add(Served(CARD, cardName(romFileName), inSystem = false))
            add(Served(CARD_A2, CARD_A2_FILE, inSystem = false))
        }
        add(Served(NVMEM, NVMEM_FILE, inSystem = true))
    }

    data class Served(val wire: String, val path: String, val inSystem: Boolean)

    /**
     * Where the joiner keeps [wire] in its session copy, relative to the copy (the session's save directory): the cards
     * and arcade saves where flycast reads them, the console settings in [SESSION_SYSTEM]`/dc/`. Null for any other name.
     */
    fun localPath(wire: String, romFileName: String): String? = when {
        !NetplaySaves.isSafe(wire) || wire !in WIRE_NAMES -> null
        wire == CARD -> cardName(romFileName)
        wire == CARD_A2 -> CARD_A2_FILE
        wire == NVMEM -> "$SESSION_SYSTEM/$BIOS_SUBDIR/$NVMEM_FILE"
        else -> arcadeName(romFileName, wire.removePrefix("$ARCADE_STEM."))
    }
}
