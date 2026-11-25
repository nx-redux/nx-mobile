package com.nxredux.mobile.gpudriver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuDriverPackageTest {
    private val meta = """{"name":"Turnip","driverVersion":"24.1","author":"A","description":"D","libraryName":"vulkan.adreno.so","minApi":28}"""
    private val goodHead = ByteArray(20).also {
        it[0] = 0x7F; it[1] = 0x45; it[2] = 0x4C; it[3] = 0x46; it[4] = 2; it[18] = 183.toByte()
    }
    private val entries = mapOf("meta.json" to 100L, "vulkan.adreno.so" to 5000L)

    private fun check(
        metaJson: String? = meta,
        entries: Map<String, Long> = this.entries,
        head: ByteArray? = goodHead,
        sdk: Int = 33,
    ) = GpuDriverPackage.check("id1", metaJson, entries, { head }, sdk)

    private fun rejected(reason: String, result: PackageCheck) = assertEquals(PackageCheck.Rejected(reason), result)

    @Test fun noMeta() = rejected("This zip has no meta.json, so it isn't a driver package.", check(metaJson = null))

    @Test fun unparseableMeta() = rejected("This package's meta.json can't be read.", check(metaJson = "{nope"))

    @Test fun metaWithoutLibraryName() = rejected("This package's meta.json can't be read.", check(metaJson = """{"name":"x"}"""))

    @Test fun libraryNotInEntries() = rejected("This package is missing its driver file.", check(entries = mapOf("meta.json" to 1L)))

    @Test fun libraryHeadUnreadable() = rejected("This package is missing its driver file.", check(head = null))

    private val notArm = "This driver isn't built for this phone (64-bit ARM)."

    @Test fun badMagic() = rejected(notArm, check(head = goodHead.copyOf().also { it[0] = 0 }))

    @Test fun notSixtyFourBit() = rejected(notArm, check(head = goodHead.copyOf().also { it[4] = 1 }))

    @Test fun wrongMachine() = rejected(notArm, check(head = goodHead.copyOf().also { it[18] = 62 }))

    @Test fun machineHighByteMatters() = rejected(notArm, check(head = goodHead.copyOf().also { it[19] = 1 }))

    @Test fun shortHead() = rejected(notArm, check(head = goodHead.copyOf(10)))

    @Test fun needsNewerAndroid() = rejected(
        "This driver needs a newer Android version.",
        check(metaJson = meta.replace("28", "34"), sdk = 33),
    )

    @Test fun unsafeRelativePath() = rejected("This package has unsafe file paths.", check(entries = entries + ("../x" to 1L)))

    @Test fun unsafeAbsolutePath() = rejected("This package has unsafe file paths.", check(entries = entries + ("/x" to 1L)))

    @Test fun tooLarge() = rejected(
        "This package is too large.",
        check(entries = entries + ("big.bin" to GpuDriverPackage.MAX_UNPACKED_BYTES)),
    )

    @Test fun valid() = assertEquals(
        PackageCheck.Ok(GpuDriver("id1", "Turnip", "24.1", "A", "D", "vulkan.adreno.so", 28)),
        check(),
    )

    @Test fun optionalFieldsDefault() {
        val d = GpuDriverPackage.parseMeta("i", """{"libraryName":"lib.so"}""")
        assertEquals(GpuDriver("i", "lib.so", "", "", "", "lib.so", 0), d)
    }

    @Test fun parseMetaRejectsGarbage() {
        assertNull(GpuDriverPackage.parseMeta("i", "[1]"))
        assertNull(GpuDriverPackage.parseMeta("i", """{"libraryName":""}"""))
    }

    @Test fun safeEntry() {
        assertTrue(GpuDriverPackage.safeEntry("a/b.so"))
        assertTrue(GpuDriverPackage.safeEntry("a..b"))
        for (bad in listOf("", "/x", "../x", "a/../x", "a\\b", "..")) assertFalse(GpuDriverPackage.safeEntry(bad), bad)
    }
}
