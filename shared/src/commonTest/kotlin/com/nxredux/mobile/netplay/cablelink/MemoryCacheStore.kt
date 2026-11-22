package com.nxredux.mobile.netplay.cablelink

/** In memory. */
class MemoryCacheStore : CacheStore {
    val files = HashMap<String, ByteArray>()
    override fun path(name: String): String? = if (name in files) "/mem/$name" else null
    override suspend fun write(name: String, write: suspend (ByteSink) -> Unit): String {
        val buf = ArrayList<Byte>()
        write(object : ByteSink {
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) { for (i in offset until offset + length) buf += bytes[i] }
        })
        files[name] = buf.toByteArray()
        return "/mem/$name"
    }
    override fun sizeBytes(): Long = files.values.sumOf { it.size.toLong() }
    override fun clear() = files.clear()
}
