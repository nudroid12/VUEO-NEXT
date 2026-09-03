package com.vueo.app.core.storage

import android.content.Context

class AddonStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun manifestUrls(): List<String> =
        prefs.getStringSet(KEY_MANIFEST_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    fun isAddonEnabled(
        manifestUrl: String,
    ): Boolean =
        prefs.getBoolean(
            addonEnabledKey(manifestUrl),
            true,
        )

    fun setAddonEnabled(
        manifestUrl: String,
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                addonEnabledKey(manifestUrl),
                enabled,
            )
            .apply()
    }

    fun catalogOrder(): List<String> =
        prefs.getString(
            KEY_CATALOG_ORDER,
            "",
        )
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

    fun setCatalogOrder(
        order: List<String>,
    ) {
        prefs.edit()
            .putString(
                KEY_CATALOG_ORDER,
                order
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("\n"),
            )
            .apply()
    }

    fun reconcileCatalogOrder(
        availableKeys: List<String>,
    ): List<String> {
        val available =
            availableKeys
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()

        val current =
            catalogOrder()
                .filter {
                    it in available
                }

        val next =
            (
                current +
                    available.filterNot {
                        it in current
                    }
            ).distinct()

        if (next != catalogOrder()) {
            setCatalogOrder(next)
        }

        return next
    }

    /**
     * Seeds development defaults once per seed revision.
     *
     * A user can still remove one afterwards and it will not be forced back
     * on every launch. Increment DEV_DEFAULTS_REVISION only when we
     * intentionally want to seed a new development default set.
     */
    fun seedDevelopmentDefaultsIfNeeded(): Boolean {
        val currentRevision = prefs.getInt(KEY_DEV_DEFAULTS_REVISION, 0)

        if (currentRevision >= DEV_DEFAULTS_REVISION) {
            return false
        }

        val next = manifestUrls().toMutableSet()
        next += DEVELOPMENT_DEFAULT_MANIFESTS

        prefs.edit()
            .putStringSet(KEY_MANIFEST_URLS, next)
            .putInt(KEY_DEV_DEFAULTS_REVISION, DEV_DEFAULTS_REVISION)
            .apply()

        return true
    }

    fun add(manifestUrl: String) {
        val next = manifestUrls().toMutableSet()
        next += manifestUrl
        prefs.edit()
            .putStringSet(
                KEY_MANIFEST_URLS,
                next,
            )
            .putBoolean(
                addonEnabledKey(manifestUrl),
                true,
            )
            .apply()
    }

    fun remove(manifestUrl: String) {
        val next = manifestUrls().toMutableSet()
        next -= manifestUrl
        prefs.edit()
            .putStringSet(
                KEY_MANIFEST_URLS,
                next,
            )
            .remove(
                addonEnabledKey(manifestUrl)
            )
            .apply()
    }

    fun isDevelopmentDefault(manifestUrl: String): Boolean =
        manifestUrl in DEVELOPMENT_DEFAULT_MANIFESTS

    private fun addonEnabledKey(
        manifestUrl: String,
    ): String =
        "addon_enabled:$manifestUrl"

    companion object {
        private const val PREFS_NAME = "vueo_content_manager"
        private const val KEY_MANIFEST_URLS = "stremio_manifest_urls"
        private const val KEY_CATALOG_ORDER = "catalog_order"
        private const val KEY_DEV_DEFAULTS_REVISION = "dev_defaults_revision"

        private const val DEV_DEFAULTS_REVISION = 1

        val DEVELOPMENT_DEFAULT_MANIFESTS = setOf(
            "https://yastream.tamthai.de/manifest.json",
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
        )
    }
}
