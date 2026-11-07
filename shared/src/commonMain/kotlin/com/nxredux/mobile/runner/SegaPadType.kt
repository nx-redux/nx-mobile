package com.nxredux.mobile.runner

/**
 * A Mega Drive / 32X cart header's I/O support field (16 bytes at 0x190; genesis_plus_gx `core/loadrom.c:290-295`):
 * a '6' in its first 14 bytes (the core's `i < 14` scan) means the game reads a 6-button pad. Null when the header is
 * not there (short file, no "SEGA" at 0x100: garbage), so the core decides. An interleaved .smd (512-byte header with
 * 0xAA 0xBB at 8-9, then 16 KB blocks holding the odd bytes in the first half and the even bytes in the second) has its
 * first block deinterleaved to find the header.
 */
object SegaHeader {
    /** Bytes needed: the header through the I/O field. */
    const val LENGTH = 0x1A0
    /** Bytes the caller reads so an interleaved .smd's first block is there too (the SMD header plus one 16 KB block). */
    const val READ_LENGTH = 0x4200
    private const val SMD_HEADER = 0x200
    private const val SMD_HALF = 0x2000
    private const val CONSOLE = 0x100
    private const val IO = 0x190
    private const val IO_SCANNED = 14   // of the field's 16 bytes, as genesis_plus_gx scans them
    private val CART_EXTENSIONS = setOf("md", "gen", "bin", "32x", "smd")

    fun padType(bytes: ByteArray): SegaPad? {
        if (bytes.size < LENGTH) return null
        val plain = parse(bytes)
        if (plain != null || !isSmd(bytes)) return plain
        return parse(deinterleave(bytes) ?: return null)
    }

    private fun isSmd(b: ByteArray) = b[8] == 0xAA.toByte() && b[9] == 0xBB.toByte()

    /** The ROM's first LENGTH bytes from an SMD's first block (odd bytes in its first half, even in its second); null when [b] is too short. */
    private fun deinterleave(b: ByteArray): ByteArray? {
        if (b.size < SMD_HEADER + SMD_HALF + LENGTH / 2) return null
        val out = ByteArray(LENGTH)
        for (i in 0 until LENGTH / 2) {
            out[2 * i + 1] = b[SMD_HEADER + i]
            out[2 * i] = b[SMD_HEADER + SMD_HALF + i]
        }
        return out
    }

    private fun parse(bytes: ByteArray): SegaPad? {
        val console = bytes.copyOfRange(CONSOLE, CONSOLE + 16).decodeToString()
        if (!console.contains("SEGA")) return null
        val io = bytes.copyOfRange(IO, IO + IO_SCANNED).decodeToString()
        return if ('6' in io) SegaPad.SIX else SegaPad.THREE
    }

    /** Whether a header is read for [fileName]: cart files only (a disc image or an unread archive is left to the core). */
    fun readable(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in CART_EXTENSIONS
}

/**
 * Controls → Controller Type for the Sega pad tags ([PadMap.isSegaPadTag]): Auto reads the header, 3 and 6 force.
 * genesis_plus_gx takes it as its port 0 device, and port 1's for player 2 (Auto leaves the core's own header detection,
 * `core/input_hw/input.c:85`); picodrive has no auto, so it gets the resolved type's pad as its port devices (its
 * `RETRO_DEVICE_MD_3BTN` / `_6BTN`, the same subclasses).
 */
object SegaPadType {
    const val AUTO = "auto"
    const val THREE = "3"
    const val SIX = "6"
    /** genesis_plus_gx `RETRO_DEVICE_MDPAD_3B` / `_6B` = RETRO_DEVICE_SUBCLASS(JOYPAD, 0 / 1) (`libretro/libretro.c:57-58`). */
    const val MDPAD_3B = 257
    const val MDPAD_6B = 513
    /** `RETRO_DEVICE_JOYPAD`: genesis_plus_gx's auto pad (2B|3B|6B, picked from the header by its `io_init`). */
    const val JOYPAD = 1
    const val GPGX = "genesis_plus_gx"
    const val PICODRIVE = "picodrive"

    /** The pad the game gets: the forced setting, else the [header] result; null = unknown (the core decides). */
    fun resolve(setting: String?, header: SegaPad?): SegaPad? = when (setting) {
        THREE -> SegaPad.THREE
        SIX -> SegaPad.SIX
        else -> header
    }

    fun gpgxPort0(setting: String?): Int? = when (setting) {
        THREE -> MDPAD_3B
        SIX -> MDPAD_6B
        else -> null
    }

    /** picodrive's pad on each player's port (it reads ports 0–1) for [setting] and the [header]; unknown is the 6-button pad. */
    fun picodrivePorts(setting: String?, header: SegaPad?, players: Int): Map<Int, Int> {
        val pad = if (resolve(setting, header) == SegaPad.THREE) MDPAD_3B else MDPAD_6B
        return (0 until players.coerceIn(1, 2)).associateWith { pad }
    }

    /**
     * genesis_plus_gx's port 0 device for a change mid-game: Auto hands the choice back to the core ([JOYPAD]), since
     * a forced device set earlier would otherwise stay.
     */
    fun gpgxPortLive(setting: String?): Int = gpgxPort0(setting) ?: JOYPAD

    /** genesis_plus_gx's forced pad on each player's port (it reads ports 0–1); null for Auto (the core decides). */
    fun gpgxPorts(setting: String?, players: Int): Map<Int, Int>? =
        gpgxPort0(setting)?.let { pad -> (0 until players.coerceIn(1, 2)).associateWith { pad } }

    /** What a mid-game Controller Type change sends to [coreId], for each of its [players]; empty for another core. */
    fun liveChanges(coreId: String, setting: String?, header: SegaPad?, players: Int): List<SegaPadChange> = when (coreId) {
        GPGX -> (0 until players.coerceIn(1, 2)).map { SegaPadChange.Port(it, gpgxPortLive(setting)) }
        PICODRIVE -> picodrivePorts(setting, header, players).map { (port, pad) -> SegaPadChange.Port(port, pad) }
        else -> emptyList()
    }
}

/** A Controller Type change as the core takes it ([SegaPadType.liveChanges]). */
sealed interface SegaPadChange {
    /** `retro_set_controller_port_device(port, device)`. */
    data class Port(val port: Int, val device: Int) : SegaPadChange
}
