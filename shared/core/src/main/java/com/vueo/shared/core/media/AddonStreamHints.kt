package com.vueo.shared.core.media

/** Optional addon metadata; preserving it does not enable external resolvers or autoplay. */
data class AddonStreamHints(
    val filename: String? = null,
    val videoHash: String? = null,
    val bingeGroup: String? = null,
    val notWebReady: Boolean = false,
    val responseHeaders: Map<String, String> = emptyMap(),
)
