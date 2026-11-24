package com.nxredux.mobile.netplay

import com.nxredux.mobile.library.ArchiveEntries
import com.nxredux.mobile.library.NaomiSets
import com.nxredux.mobile.library.Names
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.storage.NxPaths
import com.nxredux.mobile.storage.ResumeInfo
import com.nxredux.mobile.storage.SaveNaming

enum class NetplayRole { HOST, CLIENT }

/** Netplay save rules: wire names, the handheld's file whitelist, who plays on a copy, resume safety. */
object NetplaySaves {
    /** The battery saves that travel: `.srm` and `.sav`, the handheld's `--fetch-files nxsave.srm,nxsave.sav`. */
    val EXTENSIONS = listOf("srm", "sav")
    const val WIRE_STEM = "nxsave"
    /** The only names a host offers and a client takes. */
    val WIRE_NAMES = setOf("nxsave.srm", "nxsave.sav")

    /**
     * The unpacked file's name the host's runner names saves after under Use extracted file name (NxPaths.innerFileName):
     * for an archive [tag]'s core does not load as is, the entry the runner's unpack picks from [entryNames] (the archive's
     * normalised entry paths, Archives.extract's rule, with [tag]'s own core's extensions); null when [romFileName] is not
     * such an archive (not listed then), cannot be listed, or holds no single ROM (the runner refuses to launch it).
     */
    fun hostInnerFileName(tag: String, romFileName: String, naomiSets: NaomiSets, entryNames: () -> List<String>?): String? {
        if (!Names.isArchive(romFileName) || CoreRegistry.loadsArchive(tag, romFileName, naomiSets)) return null
        val names = entryNames() ?: return null
        val extensions = CoreRegistry.forTag(tag)?.extensions ?: CoreRegistry.coreExtensions
        return (ArchiveEntries.pick(names, extensions) as? ArchiveEntries.Pick.Single)?.fileName
    }

    /** A name a joiner running [core] takes from the host: a Dreamcast session's files ([DcNetplaySaves]), else [WIRE_NAMES]. */
    fun isWireName(core: String, name: String): Boolean =
        if (NetplayCores.ggpo(core)) name in DcNetplaySaves.WIRE_NAMES else name in WIRE_NAMES

    /** Every name any session moves (the rsync module's filter). */
    val ALL_WIRE_NAMES: Set<String> get() = WIRE_NAMES + DcNetplaySaves.WIRE_NAMES

    /** `<stem>.<ext>` → `nxsave.<ext>` for a battery-save extension; anything else is not sent. */
    fun wireName(fileName: String, stem: String): String? {
        if (!fileName.startsWith("$stem.")) return null
        val ext = fileName.removePrefix("$stem.")
        return if (ext in EXTENSIONS) "$WIRE_STEM.$ext" else null
    }

    /** `nxsave.<ext>` → `<stem>.<ext>`; null for any other name (the client then answers SYNC-FAIL). */
    fun localName(wire: String, stem: String): String? {
        if (!isSafe(wire) || !wire.startsWith("$WIRE_STEM.")) return null
        val ext = wire.removePrefix("$WIRE_STEM.")
        return if (ext in EXTENSIONS) "$stem.$ext" else null
    }

    /** wiz_name_is_safe: non-empty, < 64 chars, no leading '-', not "."/"..", only [A-Za-z0-9._-]. */
    fun isSafe(name: String): Boolean =
        name.isNotEmpty() && name.length < 64 && !name.startsWith('-') && name != "." && name != ".." &&
            name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }

    /**
     * Core pairs whose battery saves are the same bytes, so a host that runs the other one keeps its real save. Each was
     * checked by running real games in both cores (host builds of these pins) and crossing the saves:
     * - gpSP ↔ mGBA: 11 games over every GBA save type cross both ways unchanged; Pokémon Ruby's CONTINUE shows the same
     *   trainer in both.
     * - Snes9x ↔ Supafaust: a Zelda LttP file made in each core is byte-identical and lists in the other; 10 more games
     *   write the same SRAM. Super FX / SA-1 saves were not made.
     * - PCSX ReARMed ↔ SwanStation: a Marvel Super Heroes save makes byte-identical cards, each loads the other's
     *   ("DATA LOAD OK!"); SwanStation's card is SAVE_RAM with Memory Card 1 Type = Libretro, its default.
     * PicoDrive ↔ Genesis Plus GX stays out: only Sonic 3 (& Knuckles) SRAM was shown to cross.
     */
    private val SHARED_SAVES: Set<Set<String>> = setOf(
        setOf("gpsp", "mgba"),
        setOf("snes9x", "mednafen_supafaust"),
        setOf("pcsx_rearmed", "swanstation"),
    )

    /** [a] and [b] read and write the same battery save (the same core, or a pair in [SHARED_SAVES]). */
    fun sharesSaves(a: String, b: String): Boolean = a == b || setOf(a, b) in SHARED_SAVES

    fun playsOnCopy(role: NetplayRole, runCore: String, folderCore: String): Boolean =
        role == NetplayRole.CLIENT || !sharesSaves(runCore, folderCore)

    /**
     * A host session on real saves drops the game's auto-resume state, so resuming it later can't roll back battery
     * saves written in the session (the handheld's Menu_dropAutoResumeForSession). Manual slots are never touched.
     * Paths are relative to the SD root, like [NxPaths].
     */
    fun resumeVictims(tag: String, folderCoreId: String, romFileName: String, markerText: String?, naming: SaveNaming = SaveNaming.DEFAULT, innerFileName: String? = null, exists: (String) -> Boolean): List<String> {
        val p = NxPaths(tag, folderCoreId, romFileName, naming, innerFileName)
        return buildList {
            addAll(p.stateCandidates(NxPaths.AUTO_SLOT).filter(exists))
            if (exists(p.thumb(NxPaths.AUTO_SLOT))) add(p.thumb(NxPaths.AUTO_SLOT))
            if (ResumeInfo.slotFrom(markerText) == NxPaths.AUTO_SLOT && exists(p.lastSlot)) add(p.lastSlot)
        }
    }
}
