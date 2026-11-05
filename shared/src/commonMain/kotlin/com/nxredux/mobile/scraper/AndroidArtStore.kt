package com.nxredux.mobile.scraper

import com.nxredux.mobile.runner.ContentRef

/** The Android games console's tag ([com.nxredux.mobile.ui.ANDROID_TAG]): Tools -> Artwork's Fetch and Reset scope for it. */
const val ANDROID_CONSOLE_TAG = "ANDROID"

/**
 * The Android games console's pictures, per app ([com.nxredux.mobile.launcher.InstalledApp.id]): a screenshot, a 2D box
 * and a wheel ([SLOTS]), and the app-built mix while Mix is on ([STORED]), kept in app-private storage (never the home
 * tree: they are not ROM data). The screenshot is fetched from ScreenScraper by Tools -> Artwork; every slot of [SLOTS]
 * can be picked in Change art; the mix is only ever built from the others.
 */
interface AndroidArtStore {
    companion object {
        /** The slots an Android game's pictures are fetched, offered and picked in (no 3D box, no mix). */
        val SLOTS: Set<ArtSlot> = setOf(ArtSlot.SCREENSHOT, ArtSlot.BOX_2D, ArtSlot.WHEEL)

        /** The slots the store holds a picture in: [SLOTS] and the app-built mix (never offered or picked). */
        val STORED: Set<ArtSlot> = SLOTS + ArtSlot.MIX
    }

    /** [appId]'s stored picture in [slot], or null when none is stored. A quick lookup the console build calls per game. */
    fun art(appId: String, slot: ArtSlot): ContentRef?

    /** [appId]'s stored screenshot is a SteamGridDB hero (written with `hero`). */
    fun heroShot(appId: String): Boolean

    /**
     * Stores [bytes] as [appId]'s picture in [slot] (replacing one; a slot outside [STORED] is never written); its ref,
     * or null when the write failed. A screenshot written with [hero] is marked a hero, one written without it is not.
     */
    suspend fun write(appId: String, slot: ArtSlot, bytes: ByteArray, hero: Boolean = false): ContentRef?

    /** [appId]'s stored picture in [slot] as bytes; null when none is stored or it cannot be read. */
    suspend fun read(appId: String, slot: ArtSlot): ByteArray?

    /** Deletes every stored picture and hero mark (Tools -> Artwork -> Reset of Android or All); how many pictures went. */
    suspend fun deleteAll(): Int
}
