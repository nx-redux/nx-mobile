package com.nxredux.mobile

/**
 * Every notification id (the foreground services' and the update notice), in one place. Android keeps one notification record per (package, id):
 * two services sharing an id overwrite each other's text and progress, and the first to stop removes the other's
 * notification (and its Pause / Cancel actions) while it still runs in the foreground. A new service takes the next
 * free id here, never a private constant of its own.
 */
object NotificationIds {
    /** Artwork scraping ([com.nxredux.mobile.scraper.ScrapeService]). */
    const val SCRAPE = 4101

    /** RetroAchievements "Download all game data" ([com.nxredux.mobile.achievements.RaDownloadService]). */
    const val RA_DOWNLOAD = 4102

    /** Device Sync ([com.nxredux.mobile.sync.DeviceSyncService]). */
    const val DEVICE_SYNC = 4103

    /** The in-app update's download (the github flavor's com.nxredux.mobile.update.AppUpdateService). */
    const val APP_UPDATE = 4104

    /** The one-shot "NX Redux updated to <version>" notice after a self-update (the github flavor's com.nxredux.mobile.update.AppUpdatedReceiver). */
    const val APP_UPDATED = 4105

    /** What a downloaded update's install waits for, a plain notification (the github flavor's com.nxredux.mobile.update.AndroidUpdatePlatform). */
    const val APP_UPDATE_WAIT = 4106

    /** Kids Mode's "Games close in 10 minutes" warning, while no game or menu is in front ([com.nxredux.mobile.kids.AndroidKids]). */
    const val KIDS_WARN = 4107

    /** Kids Mode's guard: the ongoing "Kids Mode is on" while it watches the front app ([com.nxredux.mobile.kids.KidsGuardService]). */
    const val KIDS_GUARD = 4108

    /** All of them, for the test that keeps them distinct. */
    val all: List<Int> = listOf(SCRAPE, RA_DOWNLOAD, DEVICE_SYNC, APP_UPDATE, APP_UPDATED, APP_UPDATE_WAIT, KIDS_WARN, KIDS_GUARD)
}
