package com.nxredux.mobile.gpudriver

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidGpuDriversTest {
    private lateinit var base: File
    private lateinit var root: File
    private lateinit var drivers: AndroidGpuDrivers

    @Before fun setUp() {
        base = File.createTempFile("gpudrivers", "").also { it.delete(); it.mkdirs() }
        root = File(base, "gpu_drivers")
        drivers = AndroidGpuDrivers(root, 34)
    }

    @After fun tearDown() { base.deleteRecursively() }

    private fun elf(): ByteArray = ByteArray(64).also {
        byteArrayOf(0x7F, 0x45, 0x4C, 0x46, 2, 1, 1, 0).copyInto(it)
        it[18] = 0xB7.toByte(); it[19] = 0
    }

    private val meta = """{"schemaVersion":1,"name":"Turnip","description":"d","author":"a","packageVersion":"1","vendor":"Mesa","driverVersion":"24.1","minApi":28,"libraryName":"vulkan.ad07XX.so"}"""

    private fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((n, b) in entries) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun valid() = zip(listOf("meta.json" to meta.toByteArray(), "vulkan.ad07XX.so" to elf()))
    private fun install(bytes: ByteArray) = drivers.install { ByteArrayInputStream(bytes) }

    @Test fun validInstallIsListed() {
        val r = install(valid())
        assertTrue(r.toString(), r is PackageCheck.Ok)
        val list = drivers.installed()
        assertEquals(1, list.size)
        assertEquals("Turnip", list[0].name)
        assertTrue(File(drivers.directory(list[0].id), "vulkan.ad07XX.so").isFile)
        assertEquals(16, list[0].id.length)
    }

    @Test fun installingTwiceKeepsOne() {
        install(valid()); val r = install(valid())
        assertTrue(r is PackageCheck.Ok)
        assertEquals(1, drivers.installed().size)
    }

    @Test fun traversalIsRejectedAndNothingEscapes() {
        val r = install(zip(listOf("meta.json" to meta.toByteArray(), "vulkan.ad07XX.so" to elf(), "../evil.so" to elf())))
        assertTrue(r is PackageCheck.Rejected)
        assertFalse(File(base, "evil.so").exists())
        assertTrue(base.list()!!.all { it == "gpu_drivers" })
        assertEquals(emptyList<String>(), root.list()?.toList() ?: emptyList<String>())
    }

    @Test fun rejectedLeavesNoTmp() {
        val r = install(zip(listOf("meta.json" to meta.toByteArray())))
        assertTrue(r is PackageCheck.Rejected)
        assertEquals(emptyList<String>(), root.list()?.toList() ?: emptyList<String>())
    }

    @Test fun tooManyEntriesAreRejectedBeforeTheRestIsRead() {
        val extra = (1..GpuDriverPackage.MAX_ENTRIES).map { "pad/$it.txt" to ByteArray(1) }
        var opened = 0
        val r = drivers.install { opened++; ByteArrayInputStream(zip(listOf("meta.json" to meta.toByteArray(), "vulkan.ad07XX.so" to elf()) + extra)) }
        assertEquals(PackageCheck.Rejected("This package has too many files to be a driver."), r)
        assertEquals(1, opened)   // the scan stopped; nothing unpacked
        assertEquals(emptyList<String>(), root.list()?.toList() ?: emptyList<String>())
    }

    @Test fun aPackageAtTheEntryCapInstalls() {
        val extra = (1..GpuDriverPackage.MAX_ENTRIES - 2).map { "pad/$it.txt" to ByteArray(1) }
        assertTrue(install(zip(listOf("meta.json" to meta.toByteArray(), "vulkan.ad07XX.so" to elf()) + extra)) is PackageCheck.Ok)
    }

    @Test fun notAZipIsRejected() {
        val r = install("hello".toByteArray())
        assertEquals(PackageCheck.Rejected("This file isn't a zip."), r)
    }

    @Test fun actualBytesOverTheLimitAbort() {
        // A zip's headers can lie; the cap is enforced on bytes written. Highly compressible 101 MB entry.
        val big = ByteArray(101 * 1024 * 1024)
        val bytes = zip(listOf("meta.json" to meta.toByteArray(), "vulkan.ad07XX.so" to elf(), "pad.bin" to big))
        val r = install(bytes)
        assertTrue(r is PackageCheck.Rejected)
        assertEquals(emptyList<String>(), root.list()?.toList() ?: emptyList<String>())
    }

    @Test fun removeDeletesTheFolder() {
        install(valid())
        val id = drivers.installed().single().id
        drivers.remove(id)
        assertFalse(drivers.directory(id).exists())
        assertTrue(drivers.installed().isEmpty())
    }

    @Test fun staleTmpIsIgnoredThenCleanedByInstall() {
        File(root, "abc.tmp").mkdirs()
        assertTrue(drivers.installed().isEmpty())
        assertTrue(File(root, "abc.tmp").exists())
        install(valid())
        assertFalse(File(root, "abc.tmp").exists())
    }

    @Test fun aSecondInstanceWaitsForAnInstallStillUnpacking() {
        // A recreated activity has its own AndroidGpuDrivers: its install must not sweep the first one's work folder.
        val other = AndroidGpuDrivers(root, 34)
        val otherMeta = meta.replace("\"Turnip\"", "\"Other\"")
        val otherZip = zip(listOf("meta.json" to otherMeta.toByteArray(), "vulkan.ad07XX.so" to elf()))
        var otherResult: PackageCheck? = null
        var otherThread: Thread? = null
        var tmpSurvived = false
        var opened = 0
        val bytes = valid()
        val r = drivers.install {
            if (++opened == 2) {   // the unpack pass: the work folder exists
                otherThread = thread { otherResult = other.install { ByteArrayInputStream(otherZip) } }
                otherThread!!.join(300)
                tmpSurvived = root.listFiles()!!.any { it.name.endsWith(".tmp") }
            }
            ByteArrayInputStream(bytes)
        }
        otherThread!!.join(10_000)
        assertTrue(tmpSurvived)
        assertTrue(r.toString(), r is PackageCheck.Ok)
        assertTrue(otherResult.toString(), otherResult is PackageCheck.Ok)
        assertEquals(listOf("Other", "Turnip"), drivers.installed().map { it.name })
    }

    @Test fun availability() {
        assertNull(AndroidGpuDrivers.unavailableReason(true, 34))
        assertEquals("Custom drivers need Android 9 or later", AndroidGpuDrivers.unavailableReason(true, 27))
        assertEquals("", AndroidGpuDrivers.unavailableReason(false, 34))
    }
}
