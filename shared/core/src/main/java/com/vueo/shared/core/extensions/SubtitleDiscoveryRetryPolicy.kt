package com.vueo.shared.core.extensions

internal object SubtitleDiscoveryRetryPolicy {
    fun shouldRetry(attempt: Int, successful: Boolean, hasResults: Boolean): Boolean =
        !successful || (!hasResults && attempt < 2)
}
