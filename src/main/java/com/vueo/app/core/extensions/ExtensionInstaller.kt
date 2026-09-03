package com.vueo.app.core.extensions

import com.vueo.app.core.stremio.StremioAddonProvider

object ExtensionInstaller {
    suspend fun installStremioAddon(manifestUrl: String): MediaExtension {
        val url = manifestUrl.trim()
        require(url.startsWith("https://")) {
            "Only HTTPS Stremio addon manifest URLs are accepted."
        }
        return StremioAddonProvider.fromManifestUrl(url)
    }
}
