package com.vueo.mobile.core.extensions

import com.vueo.mobile.core.stremio.StremioAddonProvider

object ExtensionInstaller {
    suspend fun installStremioAddon(manifestUrl: String): MediaExtension {
        val url = com.vueo.shared.core.stremio.StremioUrls.normalizeManifest(manifestUrl)
        return StremioAddonProvider.fromManifestUrlWithRetry(url)
    }
}
