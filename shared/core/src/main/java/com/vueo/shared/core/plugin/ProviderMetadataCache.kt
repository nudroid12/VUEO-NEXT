package com.vueo.shared.core.plugin

import org.json.JSONObject

/** Small process-local metadata cache shared across disposable provider runtimes. No network or disk I/O. */
internal object ProviderMetadataCache {
    private const val MAX_ENTRIES = 64
    private const val MAX_VALUE_CHARS = 65_536
    private const val MAX_TOTAL_CHARS = 524_288
    private const val MAX_TTL_MS = 300_000L
    private data class Entry(val value: String, val expiresAt: Long)
    private val entries = LinkedHashMap<Pair<String, String>, Entry>(MAX_ENTRIES + 1, .75f, true)
    private var totalChars = 0

    @Synchronized
    fun execute(namespace: String, requestJson: String): String = try {
        require(requestJson.length <= MAX_VALUE_CHARS * 2 + 2048)
        val request = JSONObject(requestJson)
        val key = request.optString("key")
        if (namespace.isBlank() || key.isBlank() || key.length > 256) "null" else {
            val now = System.nanoTime() / 1_000_000L
            val iterator = entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.expiresAt <= now) { totalChars -= entry.value.value.length; iterator.remove() }
            }
            val scopedKey = namespace to key
            when (request.optString("op")) {
                "get" -> entries[scopedKey]?.value ?: "null"
                "remove" -> { entries.remove(scopedKey)?.let { totalChars -= it.value.length }; "true" }
                "put" -> {
                    val value = request.opt("value")
                    val encoded = value?.takeUnless { it == JSONObject.NULL }?.toString()
                    val ttl = request.optLong("ttlMs", MAX_TTL_MS).coerceAtMost(MAX_TTL_MS)
                    if (encoded == null || value !is JSONObject || encoded.length > MAX_VALUE_CHARS || ttl <= 0L) "false" else {
                        entries.remove(scopedKey)?.let { totalChars -= it.value.length }
                        entries[scopedKey] = Entry(encoded, now + ttl)
                        totalChars += encoded.length
                        while (entries.size > MAX_ENTRIES || totalChars > MAX_TOTAL_CHARS) {
                            val oldest = entries.entries.iterator()
                            totalChars -= oldest.next().value.value.length
                            oldest.remove()
                        }
                        "true"
                    }
                }
                else -> "null"
            }
        }
    } catch (_: Exception) { "null" }
}
