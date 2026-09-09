package com.vueo.mobile.core.extensions

import com.vueo.mobile.core.stremio.StremioAddonProvider

object ExtensionInstaller {
    suspend fun installStremioAddon(manifestUrl: String): MediaExtension {
        val url = manifestUrl.trim()
        require(url.startsWith("https://")) {
            "Only HTTPS addon manifest URLs are accepted."
        }
        return StremioAddonProvider.fromManifestUrl(url)
    }
}
