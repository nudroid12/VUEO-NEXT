package com.vueo.shared.core.player

/** One explicit synchronous experiment for a decoder that already opened this clear video. */
internal class VideoQueueingPolicy {
    private var used = false
    private var active: Pair<String, String>? = null

    fun request(key: String, failedName: String?, initializedName: String?, encrypted: Boolean): String? {
        if (used || encrypted || failedName.isNullOrBlank() || failedName != initializedName) return null
        used = true
        active = key to failedName
        return failedName
    }

    fun target(key: String): String? = active?.takeIf { it.first == key }?.second

    fun failed(key: String, name: String?) {
        if (active == (key to name)) active = null
    }
}
