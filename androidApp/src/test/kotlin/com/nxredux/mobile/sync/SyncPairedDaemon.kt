package com.nxredux.mobile.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** The real-rsync tests' pairing: one password, its secrets file as the phone writes it, and clients that send it. */
internal object SyncPairedDaemon {
    const val PASSWORD = "f55d7cdf6e74ca135fdcb442ca1a924e"

    /** `rsyncd.secrets` in [dir] with [password], 0600 (strict modes refuses anything looser). */
    fun secrets(dir: File, password: String = PASSWORD): File = File(dir, "rsyncd.secrets").apply {
        writeText(SyncPairing.secrets(password))
        Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rw-------"))
    }

    fun lock(dir: File): File = File(dir, "rsyncd.lock")

    /** [args] as a client process with [password] as RSYNC_PASSWORD. */
    fun client(args: List<String>, password: String = PASSWORD): ProcessBuilder =
        ProcessBuilder(args).redirectErrorStream(true).apply { environment()["RSYNC_PASSWORD"] = password }
}
