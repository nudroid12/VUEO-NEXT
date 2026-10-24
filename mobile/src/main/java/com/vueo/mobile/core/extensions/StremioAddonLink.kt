package com.vueo.mobile.core.extensions

/** Shared HTTP(S)/Stremio manifest normalization for Android deep links. */
fun normalizeStremioAddonManifestUrl(rawUrl: String?): String? = rawUrl?.let {
    runCatching { com.vueo.shared.core.stremio.StremioUrls.normalizeManifest(it) }.getOrNull()
}
