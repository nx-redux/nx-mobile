package com.nxredux.mobile.library

import com.nxredux.mobile.runner.Companion
import com.nxredux.mobile.runner.ContentRef
import com.nxredux.mobile.storage.TextLines

/** A file a sheet names, and the sheet that named it (for "Missing <name> next to <sheet>"). */
data class SheetRef(val name: String, val sheet: String)

sealed interface CompanionLookup {
    data class Found(val companions: List<Companion>) : CompanionLookup
    data class Missing(val name: String, val sheet: String) : CompanionLookup
}

/**
 * Multi-file discs: a `.cue` or `.gdi` names its track files, an `.m3u` its discs, a `.ccd`/`.toc` implies its
 * same-stem image. Only plain siblings count: a name with a directory part (after a leading `./` is stripped) is
 * ignored, so a sheet can never reach outside its folder.
 */
object DiscSheet {
    val EXTENSIONS: Set<String> = setOf("cue", "m3u", "gdi", "ccd", "toc")
    const val HEAD_BYTES = 64 * 1024
    private val READ = setOf("cue", "m3u", "gdi")   // ccd/toc are not read: their image shares their stem
    private val IMPLIED = setOf("ccd", "toc")

    fun isSheet(fileName: String): Boolean = Names.extensionOf(fileName) in EXTENSIONS

    /** The same-folder names [fileName]'s [text] (read by [decode] or [readSheet]) refers to, in order, each once. */
    fun references(fileName: String, text: String): List<String> = when (Names.extensionOf(fileName)) {
        "cue" -> text.lineSequence().mapNotNull(::cueFile).mapNotNull(::sibling).distinct().toList()
        "m3u" -> text.lineSequence().map { it.trim().removePrefix("\uFEFF").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull(::sibling).distinct().toList()
        "gdi" -> text.lineSequence().drop(1).mapNotNull(::gdiFile).mapNotNull(::sibling).distinct().toList()
        "ccd", "toc" -> Names.stripExtension(fileName).let { stem -> listOf("$stem.img", "$stem.bin", "$stem.sub") }
        else -> emptyList()
    }

    /**
     * What [sheet] loads, in order: its references and, for an `.m3u`, the references of each cue/gdi/ccd/toc it lists (one
     * level; a listed `.m3u` is not followed). [read] gives a same-folder file's text; a sheet it cannot read names nothing.
     */
    suspend fun expand(sheet: String, read: suspend (String) -> String?): List<SheetRef> = expandNamed(sheet) { referencesOf(it, read) }

    /** [expand] with each sheet's references from [refs] (a name -> what that sheet names, as [referencesOf] gives it). */
    private suspend fun expandNamed(sheet: String, refs: suspend (String) -> List<String>): List<SheetRef> {
        val out = ArrayList<SheetRef>()
        val isM3u = Names.extensionOf(sheet) == "m3u"
        for (name in refs(sheet)) {
            out += SheetRef(name, sheet)
            if (isM3u && isSheet(name) && Names.extensionOf(name) != "m3u") {
                for (inner in refs(name)) out += SheetRef(inner, name)
            }
        }
        return out.distinctBy { it.name.lowercase() }
    }

    /**
     * The lowercased names among [files] that a sibling sheet loads, so a set shows once, as its sheet. Each cue, gdi and
     * m3u is read at most once, through [head] (the first [HEAD_BYTES]).
     */
    suspend fun hiddenIn(files: List<Entry>, head: suspend (Entry) -> ByteArray?): Set<String> =
        hiddenNamed(files) { e -> head(e)?.let { references(e.name, decodeHead(it)) } }

    /**
     * [hiddenIn] with each read sheet's names from [named]: the [references] of its head read as its own type, null when
     * it cannot be read (it then names nothing). A scan passes its cached ones ([HeadReads]), so an unchanged sheet is not
     * read again. A name a sheet spells another way (case, code page) is read as the listed file it finds, whose type is
     * the same: only an ASCII name's case or a non-ASCII run differs, never a `.cue`/`.m3u`/`.gdi` extension.
     */
    internal suspend fun hiddenNamed(files: List<Entry>, named: suspend (Entry) -> List<String>?): Set<String> {
        val sheets = files.filter { isSheet(it.name) }
        if (sheets.isEmpty()) return emptySet()
        val listing = Listing(files)
        val known = HashMap<String, List<String>?>()   // by the listed file's lowercased name: each read at most once
        val refs: suspend (String) -> List<String> = { name ->
            if (Names.extensionOf(name) !in READ) references(name, "")
            else listing.find(name)?.let { e ->
                val key = e.name.lowercase()
                if (key in known) known[key] else named(e).also { known[key] = it }
            } ?: emptyList()
        }
        val out = HashSet<String>()
        for (s in sheets) {
            val self = s.name.lowercase()
            for (r in expandNamed(s.name, refs)) {
                val key = listing.find(r.name)?.name?.lowercase() ?: continue
                if (key != self) out += key
            }
        }
        return out
    }

    /**
     * A sheet's text, without a leading UTF-8 byte order mark: UTF-8 when the bytes are valid UTF-8, else Windows-1252,
     * which most legacy sheets were written in (ISO-8859-1 where it does not differ). [truncated]: the bytes are a head
     * read cut short, so a multi-byte character the cut split is dropped instead of failing the UTF-8 read.
     */
    fun decode(bytes: ByteArray, truncated: Boolean = false): String {
        val from = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val whole = bytes.size - if (truncated) cutTail(bytes) else 0
        return try {
            bytes.decodeToString(from, maxOf(from, whole), throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            buildString(bytes.size) { for (i in from until bytes.size) append(cp1252(bytes[i].toInt() and 0xFF)) }
        }
    }

    /** [decode] of a [HEAD_BYTES] head read: cut short when it filled the buffer. */
    internal fun decodeHead(bytes: ByteArray): String = decode(bytes, truncated = bytes.size >= HEAD_BYTES)

    /** How many trailing bytes are the start of a UTF-8 sequence the buffer ends inside of (0 when none). */
    private fun cutTail(bytes: ByteArray): Int {
        for (back in 1..minOf(3, bytes.size)) {
            val b = bytes[bytes.size - back].toInt() and 0xFF
            if (b and 0xC0 == 0x80) continue   // a continuation byte: look further back for its lead
            val length = when {
                b and 0xE0 == 0xC0 -> 2
                b and 0xF0 == 0xE0 -> 3
                b and 0xF8 == 0xF0 -> 4
                else -> 1
            }
            return if (length > back) back else 0
        }
        return 0
    }

    private fun cp1252(b: Int): Char = if (b in 0x80..0x9F) CP1252_HIGH[b - 0x80] else b.toChar()

    /** Windows-1252 0x80-0x9F; its five undefined codes stay their ISO-8859-1 control characters. */
    private const val CP1252_HIGH =
        "\u20AC\u0081\u201A\u0192\u201E\u2026\u2020\u2021\u02C6\u2030\u0160\u2039\u0152\u008D\u017D\u008F" +
            "\u0090\u2018\u2019\u201C\u201D\u2022\u2013\u2014\u02DC\u2122\u0161\u203A\u0153\u009D\u017E\u0178"

    /**
     * A folder's files by name, any case. A name not listed falls back to the one listed name with the same ASCII shape
     * (each run of non-ASCII characters counted as one): a sheet written in another code page than it is read in still
     * finds its files. Two names sharing a shape are never guessed between.
     */
    internal class Listing(files: List<Entry>) {
        private val byName = files.associateBy { it.name.lowercase() }
        private val byShape = files.groupBy { shape(it.name) }

        fun find(name: String): Entry? =
            byName[name.lowercase()] ?: if (name.all { it.code < 0x80 }) null else byShape[shape(name)]?.singleOrNull()

        private fun shape(name: String): String = buildString {
            var inRun = false
            for (c in name.lowercase()) {
                if (c.code < 0x80) { append(c); inRun = false } else if (!inRun) { append('\u0000'); inRun = true }
            }
        }
    }

    /**
     * The sheet that makes folder [folderName] (its [files]) one game, NX Redux's naming: `<folderName>.m3u`, else `.cue`,
     * else `.gdi` (any case). A sheet named otherwise (a TOSEC `disc.gdi`) is not a disc folder: saves, states and the VMU
     * are keyed by the file stem, so every such game would share one. Null: not a disc folder. Whether a core runs the
     * sheet is the caller's check.
     */
    fun folderSheet(folderName: String, files: List<Entry>): Entry? {
        val sheets = files.filter { !it.isDirectory }
        return folderSheetName(folderName, sheets.map { it.name })?.let { name -> sheets.first { it.name == name } }
    }

    /** [folderSheet] over bare file [names] (a listing without entry types): the sheet's name as listed, or null. */
    fun folderSheetName(folderName: String, names: Collection<String>): String? {
        for (ext in listOf("m3u", "cue", "gdi")) names.firstOrNull { it.equals("$folderName.$ext", ignoreCase = true) }?.let { return it }
        return null
    }

    /**
     * The files a launch of [fileName] (in [folder]) hands the core beside it, each under the name its sheet spells. A
     * name missing from the folder is [CompanionLookup.Missing] with the sheet that named it; a `.ccd`/`.toc` needs its
     * `.img` or `.bin` (its `.sub` is optional, and so is anything a ccd/toc listed by an m3u implies). Not a sheet:
     * `Found(emptyList())`.
     */
    suspend fun companions(source: RomsSource, folder: ContentRef, fileName: String): CompanionLookup {
        val r = resolve(source, folder, fileName)
        return r.missing?.let { CompanionLookup.Missing(it.name, it.sheet) } ?: CompanionLookup.Found(r.found.map { (name, e) -> Companion(name, e.ref) })
    }

    /**
     * What [fileName] loads, found as [companions] finds it, under each file's name in [folder] (once each): what a move
     * of the set takes along. A file the sheet needs that is not there stays in as spelled, so the set never moves
     * without it.
     */
    suspend fun loadedNames(source: RomsSource, folder: ContentRef, fileName: String): List<String> {
        val r = resolve(source, folder, fileName)
        return (r.found.map { it.second.name } + listOfNotNull(r.missing?.name)).filter { !it.equals(fileName, ignoreCase = true) }.distinctBy { it.lowercase() }
    }

    /** A sheet's text as the scanner and a launch read it: its head, [decode]d. */
    suspend fun readSheet(source: RomsSource, file: ContentRef): String? =
        source.readHead(file, HEAD_BYTES)?.let(::decodeHead) ?: source.readText(file)?.let(TextLines::stripBom)

    /** [companions]'s lookup: each name as spelled with the entry found for it, and the first needed one not found. */
    private class Resolved(val found: List<Pair<String, Entry>>, val missing: SheetRef?)

    private suspend fun resolve(source: RomsSource, folder: ContentRef, fileName: String): Resolved {
        if (!isSheet(fileName)) return Resolved(emptyList(), null)
        val files = FileLookup(source, folder)
        val out = ArrayList<Pair<String, Entry>>()
        if (Names.extensionOf(fileName) in IMPLIED) {
            for (name in references(fileName, "")) files.find(name)?.let { out += name to it }
            val image = out.any { Names.extensionOf(it.first) == "img" || Names.extensionOf(it.first) == "bin" }
            return Resolved(out, if (image) null else SheetRef("${Names.stripExtension(fileName)}.img", fileName))
        }
        val read: suspend (String) -> String? = { name -> files.find(name)?.let { source.readHead(it.ref, HEAD_BYTES) }?.let(::decodeHead) }
        for (r in expand(fileName, read)) {
            if (r.name.equals(fileName, ignoreCase = true)) continue
            val entry = files.find(r.name)
            if (entry == null) {
                if (Names.extensionOf(r.sheet) in IMPLIED) continue
                return Resolved(out, r)
            }
            out += r.name to entry
        }
        return Resolved(out, null)
    }

    private suspend fun referencesOf(sheet: String, read: suspend (String) -> String?): List<String> {
        val text = if (Names.extensionOf(sheet) in READ) read(sheet) ?: return emptyList() else ""
        return references(sheet, text)
    }

    /** `FILE "<name>" <type>` or `FILE <name> <type>` -> name; anything else (REM, TRACK, INDEX) -> null. */
    internal fun cueFile(line: String): String? {
        val l = line.trim()
        if (!l.startsWith("FILE ", ignoreCase = true)) return null
        val rest = l.substring(5).trim()
        return if (rest.startsWith('"')) rest.substring(1).substringBefore('"') else rest.substringBeforeLast(' ').trim()
    }

    /**
     * A GDI track line `<track> <lba> <type> <sector size> <file> <offset>` -> its file, the fifth whitespace-separated field
     * (double quotes keep spaces inside a name); a line with fewer than five fields -> null. Line 1 (the track count) is
     * skipped by the caller.
     */
    internal fun gdiFile(line: String): String? {
        val fields = ArrayList<String>()
        var i = 0
        val l = line.trim()
        while (i < l.length && fields.size < 5) {
            if (l[i].isWhitespace()) { i++; continue }
            if (l[i] == '"') {
                val end = l.indexOf('"', i + 1).let { if (it < 0) l.length else it }
                fields += l.substring(i + 1, end)
                i = end + 1
            } else {
                val start = i
                while (i < l.length && !l[i].isWhitespace()) i++
                fields += l.substring(start, i)
            }
        }
        return fields.getOrNull(4)
    }

    /** [raw] as a plain sibling name, or null when it points into another folder. */
    private fun sibling(raw: String): String? {
        var n = raw.trim().replace('\\', '/')
        while (n.startsWith("./")) n = n.removePrefix("./")
        return n.takeIf { it.isNotEmpty() && '/' !in it }
    }
}
