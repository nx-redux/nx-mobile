package com.nxredux.mobile.bios

import com.nxredux.mobile.runner.BiosChecksum
import com.nxredux.mobile.runner.CoreRegistry
import com.nxredux.mobile.runner.DiscRegion
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KnownBiosTest {
    private val asset by lazy { KnownBios.parse(File("../androidApp/src/main/assets/bios/known.tsv").readText()) }

    @Test
    fun parsesRows() {
        val k = KnownBios.parse(
            "# header\n" +
                "scph5501.bin\tSCPH-5501.BIN\tPS\t524288\t490f666e1afb15b7362b406ed1cea246\t0\t0\n" +
                "gb_bios.bin\t\tGB\t256\t32fbbd84168d3482956eb3c5051637f5,aa\t1\t1\n" +
                "neogeo.zip\t\tFBN\t\t\t1\t0\n",
        )
        assertEquals(
            KnownBiosEntry("scph5501.bin", listOf("SCPH-5501.BIN"), "PS", setOf(524288L), setOf("490f666e1afb15b7362b406ed1cea246"), required = false, bootChange = false),
            k.entries[0],
        )
        assertEquals(setOf("32fbbd84168d3482956eb3c5051637f5", "aa"), k.entries[1].md5s)
        assertTrue(k.entries[1].required && k.entries[1].bootChange)
        assertEquals(emptySet(), k.entries[2].sizes)
        assertEquals(listOf("scph5501.bin"), k.byMd5("490F666E1AFB15B7362B406ED1CEA246").map { it.name })
        assertEquals(listOf("scph5501.bin"), k.byName("SCPH5501.BIN").map { it.name })
        assertEquals(listOf("scph5501.bin"), k.byName("scph-5501.bin").map { it.name })
        assertEquals(emptyList(), k.byName("nope.bin"))
    }

    @Test
    fun malformedLinesAreSkipped() {
        assertEquals(1, KnownBios.parse("a.bin\t\tGB\t1\t\t0\t0\nbroken line\n\n").entries.size)
    }

    @Test
    fun assetCoversEveryCoreBiosName() {
        // prboom.wad ships inside the APK; PS2's need is "any .bin" (see assetHasPs2Dumps); Cemu's keys.txt is the
        // player's own list of disc keys, so no dump of it can be listed.
        val bundled = setOf("prboom.wad", "keys.txt")
        val known = asset.entries.flatMap { listOf(it.name) + it.aliases }.mapTo(HashSet()) { it.lowercase() }
        val needed = CoreRegistry.biosFileNames + CoreRegistry.tags.flatMap { t -> CoreRegistry.requiredBios(t).flatMap { it.anyOf } }.map { it.lowercase() }
        val missing = needed.filter { it !in known && it !in bundled }.sorted()
        assertEquals(emptyList(), missing)
    }

    @Test
    fun assetConsolesAreKnown() {
        val bad = asset.entries.map { it.console }.distinct().filter { CoreRegistry.consoleTag(it) != it || CoreRegistry.consoleOf(it) == null }
        assertEquals(emptyList(), bad)
    }

    @Test
    fun assetHasPs2Dumps() {
        val ps2 = asset.entries.filter { it.console == "PS2" && it.name.endsWith(".bin", ignoreCase = true) && 4194304L in it.sizes }
        assertTrue(ps2.size >= 50, "PS2 dumps: ${ps2.size}")
        assertTrue(ps2.all { it.md5s.isNotEmpty() })
    }

    @Test
    fun assetRowsHaveSizeUnlessArchive() {
        val bad = asset.entries.filter { it.sizes.isEmpty() && !it.name.endsWith(".zip") && !it.name.endsWith(".7z") }
        assertEquals(emptyList(), bad.map { it.name })
    }

    @Test
    fun assetMarksBootChangers() {
        assertTrue(asset.byName("gb_bios.bin").all { it.bootChange })
        assertTrue(asset.byName("gba_bios.bin").none { it.bootChange })
    }

    @Test
    fun assetJudgesEveryRequiredBiosName() {
        // The launch's checksum check: each required name has listed dumps and sizes to hold a file to.
        val names = CoreRegistry.tags.flatMap { t -> CoreRegistry.requiredBios(t).flatMap { it.anyOf } }.distinct()
        val unjudged = names.filter { n -> BiosChecksum.forName(asset, n).let { it == null || it.md5s.isEmpty() || it.sizes.isEmpty() } }
        assertEquals(emptyList(), unjudged)
    }

    @Test
    fun assetSegaCdRegionsTakeEveryDumpOfTheirRegion() {
        val us = BiosChecksum.segaCd(asset, DiscRegion.US)!!
        for (n in listOf("bios_CD_U.bin", "us_scd1_9210.bin", "us_scd2_9306.bin", "SegaCDBIOS9303.bin")) {
            assertTrue(asset.byName(n).flatMap { it.md5s }.all { it in us.md5s }, n)
        }
        val eu = BiosChecksum.segaCd(asset, DiscRegion.EU)!!
        assertTrue(us.md5s.none { it in eu.md5s })
    }
}
