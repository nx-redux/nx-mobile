package com.nxredux.mobile.sync

/** Device Sync's text, worded after the handheld's Device Sync tool. */
object DeviceSyncText {
    const val TITLE = "Device Sync"
    /** The Ready page's headline, in the tool and setup alike (a setup page's question). */
    const val ASK = "Sync with your handheld"
    const val HEADLINE = "Sync saves, states, BIOS and collections between two devices."
    const val HEADLINE_ROMS = "Sync saves, states, BIOS, collections and ROMs between two devices."
    /** The ROMs toggle's notice (touch shows it as an icon only). */
    const val ROMS_ON_NOTICE = "ROMs will sync too"
    const val ROMS_OFF_NOTICE = "ROMs won't sync"
    const val CABLE = "Sync via cable"
    const val WIFI = "Sync via Wi-Fi"
    const val CABLE_HINT = "Connect the cable and pick Sync via cable on the other device too."
    const val WIFI_HINT = "Both devices on the same Wi-Fi."
    const val NO_WIFI = "Wi-Fi not connected. Turn on Wi-Fi, or sync via cable."
    const val WAITING_CABLE = "Waiting for the cable…"
    const val WAITING_CABLE_DETAIL = "Connect the phone to the handheld, then open Device Sync on the handheld and choose Sync via cable too."
    const val SEARCHING = "Searching for a device…"
    /** Searching's detail: what to pick on the other device for [link]. */
    fun searchingDetail(link: SyncLink) = "Open Device Sync on the other device, choose ${linkLabel(link)} and press Start."
    const val WAITING_PEER = "Waiting for the other device…"
    const val STOP_TITLE = "Stop the sync?"
    const val STOP_BODY = "Files already copied stay."
    const val STOPPED = "Sync stopped. Files already copied stay."
    const val CLOSE_GAME = "Close the running game first, then sync."
    const val IMPORTING = "Wait for the import to finish, then sync."
    const val OLD_FIRMWARE = "Update the handheld to the latest NX Redux to use Device Sync."
    const val CABLE_LOST = "The cable link was lost."
    /** The cable dropped mid-sync (unplugged, or not pushed fully in). */
    const val CABLE_DISCONNECTED = "The cable was disconnected. Plug it in firmly on both devices, then sync again."
    const val NETWORK_LOST = "The network was lost."
    const val NO_SOCKET = "Could not open the network."
    const val NO_RSYNC = "This build is missing its rsync, so Device Sync can't run."
    const val NO_SERVER = "Could not start the sync server."
    const val SERVER_TIMEOUT = "Timed out waiting for the other device (1 h)."
    const val PEER_SILENT = "The other device stopped answering (5 min)."
    const val NO_DEVICE = "No device found. Open Device Sync on the other device, then sync again."
    /** The paired peer sent FAIL: its run failed or was stopped. */
    const val PEER_FAILED = "The other device's sync failed. Check it, then sync again."
    /** The peer sent FAIL while the confirm page showed: its user pressed Cancel there. */
    const val PEER_CANCELLED = "The other device cancelled the sync."
    /** A on Done/Failed while the ended run still cleans up (a stopped server stopping its daemon). */
    const val FINISHING = "Device Sync is finishing… please wait"
    fun linkName(link: SyncLink) = if (link == SyncLink.CABLE) "cable" else "Wi-Fi"
    fun linkLabel(link: SyncLink) = if (link == SyncLink.CABLE) CABLE else WIFI
    fun hint(link: SyncLink) = if (link == SyncLink.CABLE) CABLE_HINT else WIFI_HINT
    fun ownIp(ip: String, link: SyncLink) = "IP: $ip (${linkName(link)})"
    fun confirmPeer(ip: String) = "Sync with the device at $ip?"
    fun thisDevice(ip: String, link: SyncLink) = "This device: $ip (${linkName(link)})"
    fun syncingWith(ip: String, link: SyncLink) = "Syncing with $ip via ${linkName(link)}"
    const val NO_FILE_TIMES = "This phone can't set file times in Roms, so ROM sync can't run."
    const val NO_SAVE_FILE_TIMES = "This phone can't set file times in Saves, so saves can't sync."
    const val PEER_NO_ROMS = "The other phone can't sync ROMs."
    const val NO_SPACE_HERE = "Not enough space on this phone."
    const val NO_SPACE_PEER = "Not enough space on the other device."
    const val ROMS_SKIPPED = "ROMs were skipped: turn on All files access to sync them."
    const val BIOS_NO_ACCESS = "BIOS was skipped: turn on All files access to sync it."
    const val BIOS_OLD_HANDHELD = "BIOS was skipped: update the handheld to the latest NX Redux to sync it."
    const val BIOS_PEER_PHONE = "BIOS was skipped: the other phone can't sync it."
    const val COLLECTIONS_NO_ACCESS = "Collections were skipped: turn on All files access to sync them."
    const val COLLECTIONS_OLD_HANDHELD = "Collections were skipped: update the handheld to the latest NX Redux to sync them."
    const val COLLECTIONS_PEER_PHONE = "Collections were skipped: the other phone can't sync them."
    const val COLLECTIONS_NOT_SAVED = "Some collections could not be saved on this phone."
    /** Done note: saves in the handheld's shared `Saves/GPGX` that no single phone console claims. */
    fun savesSkipped(n: Int) =
        "$n Sega ${if (n == 1) "save was" else "saves were"} left on the handheld: the game is in more than one console's GPGX folder, or in none."
    fun onlyOneSide(tags: List<String>) = "Only on one device, not synced: ${tags.joinToString(", ")}."
    fun step(step: SyncStep, total: Int = 4, console: String? = null) = "${step.number}/$total ${step.label}" + (console?.let { ": $it" } ?: "")
    fun files(done: Int, total: Int) = "$done/$total files"
    /** The other device paired with this phone's HELLO, but its answer never arrived, so it only sends READY. */
    const val MISSED_ACK = "The other device paired, but its answer never reached this phone. Stop the sync on it, then start again on both."
    fun noReady(ip: String) = "No answer from $ip (no READY in 1 h)."
    fun stepFailed(step: SyncStep, code: Int) = "rsync step ${step.number} failed (exit $code)."
    /** The step threw before an exit code (rsync could not be started). */
    fun stepError(step: SyncStep) = "rsync step ${step.number} could not run."
    fun done(received: Int, sent: Int) = "Sync complete: $received ${if (received == 1) "file" else "files"} received, $sent sent."
}
