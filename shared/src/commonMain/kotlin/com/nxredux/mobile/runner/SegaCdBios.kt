package com.nxredux.mobile.runner

/** A Sega CD disc's region, by its header ([SegaCdBios.regionOf]); [label] reads "This game is a <label> disc". */
enum class DiscRegion(val label: String) { US("US"), EU("European"), JP("Japanese") }

/**
 * The Sega CD BIOS a disc boots with. The console is region-locked: PicoDrive and Genesis Plus GX both read the disc's
 * region from its header and load only that region's BIOS, so a BIOS of another region in the folder does not help
 * ([BiosCheck] alone accepts any of the three). Each core has its own names for the files (PicoDrive's find_bios
 * also takes the redump names, as `.bin` or `.zip`; Genesis Plus GX only `bios_CD_<U|E|J>.bin`).
 */
object SegaCdBios {
    /** The disc images whose header is read; an MSU-MD cue boots from its cartridge, whose BIOS need is not this. */
    val DISC_EXTENSIONS = setOf("cue", "chd", "iso", "m3u", "bin", "img")

    /** Genesis Plus GX's region option: a forced region loads that region's BIOS, whatever the disc says. */
    const val GPGX_REGION_KEY = "genesis_plus_gx_region_detect"

    private val PICODRIVE = mapOf(
        DiscRegion.US to listOf("us_scd2_9306", "SegaCDBIOS9303", "us_scd1_9210", "bios_CD_U"),
        DiscRegion.EU to listOf("eu_mcd2_9306", "eu_mcd2_9303", "eu_mcd1_9210", "bios_CD_E"),
        DiscRegion.JP to listOf("jp_mcd2_921222", "jp_mcd1_9112", "jp_mcd1_9111", "bios_CD_J"),
    )
    private val GPGX = mapOf(DiscRegion.US to "bios_CD_U.bin", DiscRegion.EU to "bios_CD_E.bin", DiscRegion.JP to "bios_CD_J.bin")

    /**
     * The region of a Sega CD disc from the user data of its first data sector ([sector0]): the security code's byte
     * at 0x20B past the header, 0x64 Europe, 0xA1 Japan, anything else the US (both cores' rule). The header is found
     * as PicoDrive's PicoCdCheck finds it, any case, at 0 or at 0x10 (a raw 2352-byte image read as 2048-byte
     * sectors, as a raw dump named `.iso` is). Null when it is no Sega CD header.
     */
    fun regionOf(sector0: ByteArray): DiscRegion? {
        val at = listOf(0, 0x10).firstOrNull { sector0.size > it + 0x20B && sector0.decodeAscii(it, 14).equals(HEADER, ignoreCase = true) } ?: return null
        return when (sector0[at + 0x20B].toInt() and 0xFF) {
            0x64 -> DiscRegion.EU
            0xA1 -> DiscRegion.JP
            else -> DiscRegion.US
        }
    }

    /** Whether [need] is [CoreRegistry]'s "any Sega CD BIOS" need, which a disc of known region replaces. */
    fun isSegaCdNeed(need: BiosNeed): Boolean = need.anyOf == CoreRegistry.SEGA_CD_BIOS

    private const val HEADER = "SEGADISCSYSTEM"

    private fun ByteArray.decodeAscii(at: Int, length: Int): String = String(CharArray(length) { (this[at + it].toInt() and 0xFF).toChar() })

    /** Genesis Plus GX's forced region for its [GPGX_REGION_KEY] value; null for Auto (the disc decides). */
    fun gpgxForced(value: String?): DiscRegion? = when (value) {
        "ntsc-u" -> DiscRegion.US
        "pal" -> DiscRegion.EU
        "ntsc-j" -> DiscRegion.JP
        else -> null
    }

    /** The file names [coreId] loads for [region], the name to ask for first; empty for a core that is neither. */
    fun accepted(coreId: String, region: DiscRegion): List<String> = when (coreId) {
        SegaPadType.PICODRIVE -> PICODRIVE.getValue(region).let { stems -> stems.reversed().flatMap { listOf("$it.bin", "$it.zip") } }
        SegaPadType.GPGX -> listOf(GPGX.getValue(region))
        else -> emptyList()
    }

    /** The file to ask for when none of [coreId]'s names for [region] is in [present]; null when one is (or the core is neither). */
    fun missing(coreId: String, region: DiscRegion, present: Set<String>): String? {
        val names = accepted(coreId, region)
        return if (names.isEmpty() || names.any { it in present }) null else names.first()
    }

    /**
     * "This game is a US disc: put bios_CD_U.bin in Bios/SEGACD/. Bios/MD/ has bios_CD_E.bin and bios_CD_J.bin, which
     * only boot European and Japanese discs." for the launched [tag]. The second sentence names the other regions'
     * files [coreId] would load that are in [present], each under the `Bios/` folder [folderOf] finds it in (they are
     * gathered from several folders, so "you have" alone would point at the wrong one); a file in none is left out.
     */
    fun message(tag: String, coreId: String, region: DiscRegion, file: String, present: Set<String>, folderOf: (String) -> String?): String {
        val ask = "This game is a ${region.label} disc: put $file in Bios/${CoreRegistry.consoleTag(tag.trim().uppercase())}/."
        val others = DiscRegion.entries.filter { it != region }
            .flatMap { r -> accepted(coreId, r).filter { it in present }.mapNotNull { f -> folderOf(f)?.let { Triple(f, r, it) } } }
        if (others.isEmpty()) return ask
        val where = others.groupBy({ it.third }, { it.first }).entries.joinToString("; ") { (folder, files) -> "Bios/$folder/ has ${and(files)}" }
        val regions = and(others.map { it.second.label }.distinct())
        val verb = if (others.size == 1) "boots" else "boot"
        return "$ask $where, which only $verb $regions discs."
    }

    private fun and(items: List<String>): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " and " + items.last()
}
