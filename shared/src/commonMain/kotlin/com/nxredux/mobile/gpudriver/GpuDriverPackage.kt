package com.nxredux.mobile.gpudriver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** An installed Adreno Vulkan driver, as described by its package's meta.json. */
data class GpuDriver(
    val id: String,
    val name: String,
    val driverVersion: String,
    val author: String,
    val description: String,
    /** The driver `.so`'s path inside the package (and inside the installed folder). */
    val libraryName: String,
    /** The lowest Android API level the driver runs on; 0 when the package doesn't say. */
    val minApi: Int,
)

/** The outcome of [GpuDriverPackage.check]. */
sealed interface PackageCheck {
    /** The package is a usable driver. */
    data class Ok(val driver: GpuDriver) : PackageCheck

    /** The package can't be installed; [reason] is shown to the user as is. */
    data class Rejected(val reason: String) : PackageCheck
}

/** Validation of a driver package (a zip holding meta.json and a driver library), kept free of any zip or file API. */
object GpuDriverPackage {
    /** The descriptor every package carries at its root. */
    const val META = "meta.json"

    /** The most a package may unpack to. */
    const val MAX_UNPACKED_BYTES = 100L * 1024 * 1024

    private const val EM_AARCH64 = 183
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Checks a package before anything is written. [entries]: every zip entry name to its uncompressed size.
     * [libraryHead]: the first 20 bytes of the named entry (the ELF header up to `e_machine`), null if unreadable.
     */
    fun check(
        id: String,
        metaJson: String?,
        entries: Map<String, Long>,
        libraryHead: (String) -> ByteArray?,
        sdkInt: Int,
    ): PackageCheck {
        if (metaJson == null) return PackageCheck.Rejected("This zip has no meta.json, so it isn't a driver package.")
        val driver = parseMeta(id, metaJson) ?: return PackageCheck.Rejected("This package's meta.json can't be read.")
        if (driver.libraryName !in entries) return PackageCheck.Rejected("This package is missing its driver file.")
        val head = libraryHead(driver.libraryName) ?: return PackageCheck.Rejected("This package is missing its driver file.")
        if (!isArm64Elf(head)) return PackageCheck.Rejected("This driver isn't built for this phone (64-bit ARM).")
        if (driver.minApi > sdkInt) return PackageCheck.Rejected("This driver needs a newer Android version.")
        if (!entries.keys.all(::safeEntry)) return PackageCheck.Rejected("This package has unsafe file paths.")
        if (entries.values.sum() > MAX_UNPACKED_BYTES) return PackageCheck.Rejected("This package is too large.")
        return PackageCheck.Ok(driver)
    }

    /** The driver [json] describes, or null when it isn't a JSON object with a non-blank `libraryName`. */
    fun parseMeta(id: String, json: String): GpuDriver? {
        val obj = runCatching { this.json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        val library = obj.text("libraryName")?.takeIf { it.isNotBlank() } ?: return null
        return GpuDriver(
            id = id,
            name = obj.text("name") ?: library,
            driverVersion = obj.text("driverVersion").orEmpty(),
            author = obj.text("author").orEmpty(),
            description = obj.text("description").orEmpty(),
            libraryName = library,
            minApi = obj["minApi"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: 0,
        )
    }

    /** False for an empty, absolute, backslash or `..` entry name: it could escape the install folder. */
    fun safeEntry(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith("/") && '\\' !in name && name.split('/').none { it == ".." }

    private fun JsonObject.text(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive }.getOrNull() }?.takeIf { it.isString }?.content

    private fun isArm64Elf(head: ByteArray): Boolean =
        head.size >= 20 &&
            head[0] == 0x7F.toByte() && head[1] == 0x45.toByte() && head[2] == 0x4C.toByte() && head[3] == 0x46.toByte() &&
            head[4] == 2.toByte() &&
            (head[18].toInt() and 0xFF) or ((head[19].toInt() and 0xFF) shl 8) == EM_AARCH64
}
