package com.nxredux.mobile.achievements

/** Scripted HTTP: [responders] answer by the request's `r=` value; unmatched requests throw RaHttpException. */
class FakeRaHttp : RaHttp {
    val posts = ArrayList<String>()
    val cores = ArrayList<String?>()
    val gets = ArrayList<String>()
    var offline = false
    val responders = LinkedHashMap<String, suspend (String) -> RaResponse>()
    var getBytesResponder: suspend (String) -> Pair<Int, ByteArray> = { 200 to "PNG".encodeToByteArray() }
    fun on(r: String, status: Int = 200, body: String) { responders[r] = { RaResponse(status, body) } }
    override suspend fun post(url: String, body: String, contentType: String, core: String?): RaResponse {
        if (offline) throw RaHttpException("offline")
        posts += body; cores += core
        val kind = RaRequestKind.of(body)
        return responders[kind.r]?.invoke(body) ?: throw RaHttpException("no responder for ${kind.r}")
    }
    override suspend fun getBytes(url: String): Pair<Int, ByteArray> { if (offline) throw RaHttpException("offline"); gets += url; return getBytesResponder(url) }
}
