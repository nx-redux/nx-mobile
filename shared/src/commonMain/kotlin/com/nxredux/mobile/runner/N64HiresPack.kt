package com.nxredux.mobile.runner

/**
 * N64 high-resolution texture packs: a ready-made GLideN64 cache (`<NAME>_HIRESTEXTURES.hts`, the "GLideN64 Cache"
 * download) in a `.cache` folder beside the game, as on the handheld, or in the home folder's `Textures/N64/` ([HOME_PATH]),
 * so a pack can sit on internal storage while the ROMs stay on an SD card (whose random reads stutter the game). The home
 * one wins: it is the copy put there on purpose. The app opens it and hands it to the core as a
 * virtual file in [VIRTUAL_DIR] (cores/patches/mupen64plus_next/0013), so it reads the same with or without All files
 * access. PNG folder packs are not supported: they need converting into a cache the core would write.
 */
object N64HiresPack {
    const val ENABLE_KEY = "mupen64plus-txHiresEnable"
    const val FOLDER = ".cache"
    /** The home folder's pack folder, per console tag like `Cheats/<TAG>`. */
    val HOME_PATH = listOf("Textures", "N64")
    /** Where the patched GLideN64 looks for the pack, through the frontend's VFS. */
    const val VIRTUAL_DIR = "/nx-vfs/hires"
    const val HEADER_BYTES = 0x40

    /** Max High-Res VRAM Limit: GLideN64 drops the least recently used HD textures over it (MB, "0" = no limit). */
    const val VRAM_KEY = "mupen64plus-MaxHiResTxVramLimit"
    const val VRAM_AUTO = "auto"
    /**
     * No limit, stored as "none" rather than the core's "0": every saved N64 config holds the old default "0" from before
     * packs could load, so "0" is a hidden value ([CoreRegistry]) and a stored one reads as Auto.
     */
    const val VRAM_NONE = "none"

    private const val NAME_AT = 0x20
    private const val GIB = 1024L * 1024 * 1024
    private const val NAME_LENGTH = 20

    /** Whether this launch looks for a pack: Use High-Res textures on, and GLideN64 (ParaLLEl-RDP on Vulkan has no packs). */
    fun wanted(options: Map<String, String>, onVulkan: Boolean): Boolean = !onVulkan && options[ENABLE_KEY] == "True"

    /**
     * The VRAM limit the core gets for the stored [value]: a picked number as it is, [VRAM_NONE] as "0"; Auto (or anything else) by
     * the device's [totalMemBytes], as the GPU shares the phone's RAM: 200 MB up to 3 GB, 500 MB up to 6 GB, no limit
     * above. Reported totals sit below the nominal size (a 2 GB device reports about 1.9), hence the half-GiB headroom.
     */
    fun vramLimit(value: String?, totalMemBytes: Long): String = if (value == VRAM_NONE) "0" else value?.takeIf { it.toIntOrNull() != null } ?: when {
        totalMemBytes < 3 * GIB + GIB / 2 -> "200"
        totalMemBytes < 6 * GIB + GIB / 2 -> "500"
        else -> "0"
    }

    fun fileName(headerName: String): String = "${headerName}_HIRESTEXTURES.hts"

    /**
     * The ROM's internal name as GLideN64 names its pack (RSP.cpp, TxFilter): the 20 header bytes at 0x20 in big-endian
     * (.z64) order up to the first NUL, trailing spaces dropped, ':' and '/' made '-'. [head] is the file's first bytes in
     * any of the three dump orders, told by the first word. Null when it is not an N64 ROM header or the name is not ASCII
     * (the core's wide-string conversion would not give the same name).
     */
    fun headerName(head: ByteArray): String? {
        if (head.size < NAME_AT + NAME_LENGTH) return null
        val word = (0 until 4).map { head[it].toInt() and 0xFF }
        val z64: (Int) -> Byte = when (word) {
            listOf(0x80, 0x37, 0x12, 0x40) -> { i -> head[i] }
            listOf(0x37, 0x80, 0x40, 0x12) -> { i -> head[i xor 1] }   // .v64: 16-bit words swapped
            listOf(0x40, 0x12, 0x37, 0x80) -> { i -> head[i xor 3] }   // .n64: 32-bit words little-endian
            else -> return null
        }
        val bytes = (NAME_AT until NAME_AT + NAME_LENGTH).map(z64).takeWhile { it != 0.toByte() }
        if (bytes.any { it < 0x20 || it > 0x7E }) return null
        val name = bytes.map { it.toInt().toChar() }.joinToString("").trimEnd(' ').replace(':', '-').replace('/', '-')
        return name.ifEmpty { null }
    }
}
