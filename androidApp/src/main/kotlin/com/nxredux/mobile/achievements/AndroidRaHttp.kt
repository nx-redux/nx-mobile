package com.nxredux.mobile.achievements

import com.nxredux.mobile.scraper.BoundedBody
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** HttpURLConnection transport with AndroidCheatDb's timeouts. Only transport failures throw; an HTTP status is returned. */
class AndroidRaHttp(private val userAgent: String) : RaHttp {
    override suspend fun post(url: String, body: String, contentType: String): RaResponse = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        try {
            c = open(url, "POST"); c.doOutput = true; c.setRequestProperty("Content-Type", contentType)
            c.outputStream.use { it.write(body.encodeToByteArray()) }
            val status = c.responseCode
            RaResponse(status, bodyOf(c, status).decodeToString())
        } catch (e: IOException) {
            throw RaHttpException(e.message ?: "network", e)
        } finally {
            c?.disconnect()
        }
    }

    override suspend fun getBytes(url: String): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        try {
            c = open(url, "GET")
            val status = c.responseCode
            status to bodyOf(c, status)
        } catch (e: IOException) {
            throw RaHttpException(e.message ?: "network", e)
        } finally {
            c?.disconnect()
        }
    }

    /** The body up to [MAX_BODY_BYTES]; a larger one is no answer (an IOException, so a [RaHttpException]), never a heap filled. */
    private fun bodyOf(c: HttpURLConnection, status: Int): ByteArray {
        val stream = (if (status in 200..299) c.inputStream else c.errorStream) ?: return ByteArray(0)
        return stream.use { BoundedBody.read(it, MAX_BODY_BYTES) } ?: throw IOException("body over $MAX_BODY_BYTES bytes")
    }

    private fun open(url: String, method: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", userAgent)   // RA asks clients to identify themselves ([RaRequests.userAgent])
    }

    private companion object {
        /** The largest RA answer is a big game's achievementsets (~2 MB); badges are a few KB. */
        const val MAX_BODY_BYTES = 8 * 1024 * 1024
    }
}
