package com.vueo.shared.core.plugin

/** Keep only the most recent console messages, even while a provider is running. */
internal class BoundedProviderLog(private val capacity: Int) {
    private val entries = java.util.ArrayDeque<String>()

    init { require(capacity > 0) }

    @Synchronized
    operator fun plusAssign(message: String) {
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast(message)
    }

    @Synchronized
    fun toList(): List<String> = entries.toList()
}
