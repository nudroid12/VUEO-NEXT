package com.vueo.shared.core.player

/** Addons rely on transport timeouts/retries rather than a first-frame deadline. */
object AddonPlaybackPolicy {
    const val NETWORK_TIMEOUT_MS = 15_000
    fun usesNetworkStartup(providerId: String): Boolean =
        providerId.isNotBlank() && !providerId.startsWith("plugin:")
}
