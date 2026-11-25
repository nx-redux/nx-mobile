package com.nxredux.mobile.achievements

data class RaResponse(val status: Int, val body: String)

/** A transport failure (no network, timeout, DNS); an HTTP status is never an exception. */
class RaHttpException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface RaHttp {
    /** [core]: the running core's [RaRequests.coreToken] for the user agent, null outside a game. */
    suspend fun post(url: String, body: String, contentType: String, core: String? = null): RaResponse
    /** Binary GET for badges: (status, body bytes). */
    suspend fun getBytes(url: String): Pair<Int, ByteArray>
}

interface RaConnectivity { fun isConnected(): Boolean }

/** Builds the md5-signed award request through rcheevos (`rc_api_init_award_achievement_request`, hardcore 0). */
interface RaSigner { fun award(user: String, token: String, achievementId: Int, hash: String, secondsSinceUnlock: Int): Pair<String, String>? }

/**
 * Identifies a library game of system [tag]: [ref] and [fileName] of the entry, [folder] its parent (for a sheet's
 * companions). The implementation derives the console id from the tag and the name of the file it hashes (for an
 * archive, the unpacked ROM). Null = unidentified.
 */
interface RaHasher {
    suspend fun hash(tag: String, ref: String, fileName: String, folder: String): String?
    /**
     * A cheap identity of the file at [ref] (its size and last-modified time), or null when unknown. A game whose stamp
     * is unchanged is not hashed again ([RaFingerprints]): an archive is not unpacked each "Download all game data".
     */
    suspend fun stamp(ref: String): String? = null
}

class RaPlatform(val store: RaStore, val http: RaHttp, val hasher: RaHasher, val signer: RaSigner, val connectivity: RaConnectivity, val home: RaHomeTree)
