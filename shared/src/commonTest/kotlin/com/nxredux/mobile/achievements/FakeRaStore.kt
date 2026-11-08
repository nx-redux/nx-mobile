package com.nxredux.mobile.achievements

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** In-memory RaStore. Paths are the relative paths the model uses; [failAppend] makes append throw, [failList] makes list throw; [beforeList] and [beforeDelete] run (and may suspend) before each list and delete. */
class FakeRaStore : RaStore {
    val files = LinkedHashMap<String, ByteArray>()
    var failAppend = false
    var failList = false
    var beforeList: suspend () -> Unit = {}
    var beforeDelete: suspend () -> Unit = {}
    var beforeRead: suspend () -> Unit = {}
    var beforeWrite: suspend () -> Unit = {}
    /** Stands in for the platform's cross-process lock: one per path, shared by every user of this store. */
    private val locks = HashMap<String, Mutex>()
    val writes = ArrayList<String>()
    val reads = ArrayList<String>()
    override suspend fun read(path: String): ByteArray? { beforeRead(); reads += path; return files[path] }
    override suspend fun writeAtomic(path: String, bytes: ByteArray) { beforeWrite(); files[path] = bytes; writes += path }
    override suspend fun append(path: String, bytes: ByteArray) {
        if (failAppend) throw RaStoreException("append failed")
        files[path] = (files[path] ?: ByteArray(0)) + bytes; writes += path
    }
    override suspend fun size(path: String): Long? = files[path]?.size?.toLong()
    override suspend fun delete(path: String) { beforeDelete(); files.remove(path) }
    override suspend fun deleteTree(dir: String) { beforeDelete(); if (dir.isEmpty()) throw RaStoreException("refusing to delete the store root"); files.keys.filter { it == dir || it.startsWith("$dir/") }.forEach { files.remove(it) } }
    override suspend fun list(dir: String): List<String> = if (failList.also { beforeList() }) throw RaStoreException("list failed") else files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/").substringBefore('/') }.distinct()
    override suspend fun exists(path: String): Boolean = path in files || files.keys.any { it.startsWith("$path/") }
    override suspend fun <T> locked(path: String, block: suspend () -> T): T = locks.getOrPut(path) { Mutex() }.withLock { block() }
    fun text(path: String) = files[path]?.decodeToString()
    fun put(path: String, text: String) { files[path] = text.encodeToByteArray() }
}
