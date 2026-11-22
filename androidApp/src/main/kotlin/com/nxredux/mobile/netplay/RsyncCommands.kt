package com.nxredux.mobile.netplay

/**
 * netplay: the rsync command lines and daemon config, exactly the handheld's (nx-redux wizard_sync.c),
 * minus its `uid = 0`/`gid = 0` and `log file` lines, plus `reverse lookup = no`. Pure, so they are unit-tested. Every
 * value that reaches a config line or a URL is checked: a newline would start a directive the caller never wrote.
 */
object RsyncCommands {
    const val PORT = 18731
    const val MODULE = "sync"

    /** `rsync -t --inplace --no-perms --timeout=8 --contimeout=4 rsync://<host>:18731/sync/<name> <dest>/` (no shell). */
    fun pullArgs(binary: String, hostIp: String, wireName: String, destDir: String): List<String> {
        require(NetplayAddress.ipv4(hostIp) == hostIp) { "not a dotted IPv4 address: $hostIp" }
        require(wireName in NetplaySaves.WIRE_NAMES) { "not a save name: $wireName" }
        return listOf(
            binary, "-t", "--inplace", "--no-perms", "--timeout=8", "--contimeout=4",
            "rsync://$hostIp:$PORT/$MODULE/$wireName", destDir.trimEnd('/') + "/",
        )
    }

    /** `rsync --daemon --no-detach --config=<conf>`: a child process the app stops itself. */
    fun daemonArgs(binary: String, conf: String): List<String> = listOf(binary, "--daemon", "--no-detach", "--config=$conf")

    /** The daemon config: one read-only, unlisted module serving [staging] to [joinerIp] only. */
    fun daemonConf(joinerIp: String, staging: String, pidFile: String): String {
        require(NetplayAddress.ipv4(joinerIp) == joinerIp) { "not a dotted IPv4 address: $joinerIp" }
        require(listOf(staging, pidFile).none { '\n' in it || '\r' in it }) { "a path with a line break" }
        return buildString {
            append("pid file = ").append(pidFile).append('\n')
            append("port = ").append(PORT).append('\n')
            append("use chroot = no\n")
            append("hosts allow = ").append(joinerIp).append('\n')
            append("reverse lookup = no\n")   // a LAN reverse-DNS lookup of the joiner could stall the copy
            append('\n')
            append('[').append(MODULE).append("]\n")
            append("  path = ").append(staging).append('\n')
            append("  read only = true\n")
            append("  list = false\n")
        }
    }
}
