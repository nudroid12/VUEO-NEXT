/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from Nuvio CollectionRepository (load/persist/upsert/order). Android
 * storage, profile isolation, atomic writes and cancellation are VUEO-specific.
 */
package com.vueo.shared.core.collections

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CollectionStore private constructor(context: Context, profileId: String) {
    private val profileKey = MessageDigest.getInstance("SHA-256").digest(profileId.toByteArray()).joinToString("") { "%02x".format(it) }
    private val file = AtomicFile(File(context.filesDir, "collections/$profileKey.json"))
    private val mutex = Mutex()
    private var loaded = false
    private val state = MutableStateFlow<List<Collection>>(emptyList())
    val collections = state.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { loadLocked() } }

    private fun loadLocked() {
        if (loaded) return
        // A corrupt file is surfaced to the UI; never silently overwrite it.
        state.value = if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) emptyList()
            else file.openRead().use { input ->
                val bytes = input.readNBytesCompat(CollectionJson.MAX_BYTES + 1)
                require(bytes.size <= CollectionJson.MAX_BYTES) { "Collections exceed 1 MiB" }
                CollectionJson.decode(bytes.toString(Charsets.UTF_8))
            }
        loaded = true
    }

    suspend fun update(transform: (List<Collection>) -> List<Collection>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val next = transform(state.value)
            val bytes = CollectionJson.encode(next).toByteArray(Charsets.UTF_8)
            file.baseFile.parentFile?.mkdirs()
            val stream = file.startWrite()
            try { stream.write(bytes); file.finishWrite(stream) }
            catch (failure: Throwable) { file.failWrite(stream); throw failure }
            state.value = next
        }
    }
    suspend fun save(collection: Collection) = update { current ->
        if (current.any { it.id == collection.id }) current.map { if (it.id == collection.id) collection else it }
        else current + collection
    }
    suspend fun remove(id: String) = update { it.filterNot { c -> c.id == id } }
    suspend fun move(id: String, direction: Int) = update { CollectionPolicy.move(it, it.indexOfFirst { c -> c.id == id }, it.indexOfFirst { c -> c.id == id } + direction) }
    suspend fun toggle(id: String) = update { it.map { c -> if (c.id == id) c.copy(enabled = !c.enabled) else c } }
    suspend fun merge(text: String) {
        val incoming = withContext(Dispatchers.Default) { CollectionJson.decode(text) }
        update { current -> current.map { old -> incoming.find { it.id == old.id } ?: old } + incoming.filter { n -> current.none { it.id == n.id } } }
    }

    companion object {
        private val stores = ConcurrentHashMap<String, CollectionStore>()
        fun get(context: Context, profileId: String): CollectionStore = stores.getOrPut(context.applicationContext.filesDir.path + ":" + profileId) {
            CollectionStore(context.applicationContext, profileId)
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
