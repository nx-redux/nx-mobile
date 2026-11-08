package com.nxredux.mobile.storage

/**
 * A DS game on melonDS DS whose only save is the NX Redux handheld's DraStic/DeSmuME `<stem>.dsv` plays that file in
 * place (DraStic reads `.dsv`/`.sav`, never `.srm`, so a copy would never take progress back): loaded with its footer
 * stripped, every battery write goes back to the same `.dsv` with the footer it was read with. A save under any other
 * name ([NxPaths.saveRamCandidates], any format) wins and the `.dsv` is left alone. Pure: the caller lists the folder
 * and reads files.
 */
object NdsDsvSave {
    /** The core whose launches may play a `.dsv`. */
    const val CORE_ID = "melondsds"
    /** DeSmuME's `.dsv` footer (DraStic writes the same): 122 bytes, ending in [DSV_MAGIC]. */
    const val DSV_FOOTER_SIZE = 122
    const val DSV_MAGIC = "|-DESMUME SAVE-|"

    fun dsvName(stem: String): String = "$stem.dsv"

    /** The save [bytes] to load and the [footer] to append on every write (empty when the file had none, so it is written raw). */
    class Plan(val name: String, val bytes: ByteArray, val footer: ByteArray)

    /**
     * The `.dsv` to play, or null to use the usual battery-save lookup: the game already has a save under one of
     * [candidates] (the runner's battery-save file names; an empty file holds none, an unreadable one may), there is no
     * `.dsv`, or it is empty, unreadable or only a footer.
     */
    fun plan(stem: String, candidates: List<String>, existing: Set<String>, read: (String) -> ByteArray?): Plan? {
        if (candidates.any { it in existing && read(it)?.isEmpty() != true }) return null
        val name = dsvName(stem)
        if (name !in existing) return null
        val file = read(name)?.takeIf { it.isNotEmpty() } ?: return null
        val footer = footerOf(file) ?: return Plan(name, file, ByteArray(0))
        val save = file.copyOfRange(0, file.size - footer.size)
        return if (save.isEmpty()) null else Plan(name, save, footer)
    }

    /**
     * The file content for [save] with the [footer] it was loaded with. DeSmuME's footer is its 82-byte text, then
     * little-endian u32s: the save size (offset 82), the padded size the data before the footer fills (86), the chip
     * type (90), address size (94), memory size (98) and version 0 (102), then [DSV_MAGIC] (106..121).
     *
     * When [save] is not the padded size the footer records (the core saved another size), both size
     * fields are rewritten to [save]'s size so the footer matches the data it follows; everything else is kept. A save
     * of the padded size keeps the footer as it was, a size below the padded one included.
     */
    fun join(save: ByteArray, footer: ByteArray): ByteArray {
        if (footer.size != DSV_FOOTER_SIZE || Bytes.le32(footer, PAD_SIZE_AT) == save.size.toLong()) return save + footer
        val fixed = footer.copyOf()
        putU32(fixed, SIZE_AT, save.size)
        putU32(fixed, PAD_SIZE_AT, save.size)
        return save + fixed
    }

    private const val SIZE_AT = 82
    private const val PAD_SIZE_AT = 86

    private fun putU32(b: ByteArray, at: Int, v: Int) { for (i in 0 until 4) b[at + i] = (v ushr (8 * i)).toByte() }

    /** The last [DSV_FOOTER_SIZE] bytes of [bytes], or null when they do not end in [DSV_MAGIC] (or the file is shorter than a footer). */
    fun footerOf(bytes: ByteArray): ByteArray? {
        if (bytes.size < DSV_FOOTER_SIZE) return null
        if (!Bytes.startsWithAscii(bytes, DSV_MAGIC, bytes.size - DSV_MAGIC.encodeToByteArray().size)) return null
        return bytes.copyOfRange(bytes.size - DSV_FOOTER_SIZE, bytes.size)
    }

    /** [bytes] without its DeSmuME footer, or null when there is no valid footer. */
    fun stripDsvFooter(bytes: ByteArray): ByteArray? = footerOf(bytes)?.let { bytes.copyOfRange(0, bytes.size - it.size) }
}
