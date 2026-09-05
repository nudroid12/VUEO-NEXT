package com.vueo.tv.core

import android.content.Context

/**
 * TV adaptation of Mobile AddonStore semantics.
 *
 * This intentionally uses the same preference/key model as Mobile so the TV
 * runtime consumes catalog ordering, addon enable state and per-catalog
 * visibility in the same way instead of preserving the legacy TV store.
 */
class TvContentPreferences(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun manifestUrls(): List<String> =
        prefs.getStringSet(KEY_MANIFEST_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    fun isAddonEnabled(manifestUrl: String): Boolean =
        prefs.getBoolean(addonEnabledKey(manifestUrl), true)

    fun setAddonEnabled(manifestUrl: String, enabled: Boolean) {
        prefs.edit().putBoolean(addonEnabledKey(manifestUrl), enabled).apply()
    }

    fun add(manifestUrl: String) {
        val normalized = manifestUrl.trim()
        if (normalized.isBlank()) return
        val next = manifestUrls().toMutableSet()
        next += normalized
        prefs.edit()
            .putStringSet(KEY_MANIFEST_URLS, next)
            .putBoolean(addonEnabledKey(normalized), true)
            .apply()
    }

    fun remove(manifestUrl: String) {
        val normalized = manifestUrl.trim()
        val next = manifestUrls().toMutableSet()
        next -= normalized
        prefs.edit()
            .putStringSet(KEY_MANIFEST_URLS, next)
            .remove(addonEnabledKey(normalized))
            .apply()
    }

    fun isDevelopmentDefault(manifestUrl: String): Boolean =
        manifestUrl in DEVELOPMENT_DEFAULT_MANIFESTS

    fun catalogOrder(): List<String> =
        prefs.getString(KEY_CATALOG_ORDER, "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

    fun setCatalogOrder(order: List<String>) {
        prefs.edit()
            .putString(
                KEY_CATALOG_ORDER,
                order.map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("\n"),
            )
            .apply()
    }

    fun disabledCatalogKeys(): Set<String> =
        prefs.getStringSet(KEY_DISABLED_CATALOG_KEYS, emptySet())
            .orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    fun isCatalogEnabled(catalogKey: String): Boolean =
        catalogKey.trim() !in disabledCatalogKeys()

    fun setCatalogEnabled(catalogKey: String, enabled: Boolean) {
        val normalized = catalogKey.trim()
        if (normalized.isBlank()) return

        val next = disabledCatalogKeys().toMutableSet()
        if (enabled) next -= normalized else next += normalized
        prefs.edit().putStringSet(KEY_DISABLED_CATALOG_KEYS, next).apply()
    }

    fun reconcileCatalogOrder(availableKeys: List<String>): List<String> {
        val available =
            availableKeys.map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        val current = catalogOrder().filter { it in available }
        val next = (current + available.filterNot { it in current }).distinct()
        if (next != catalogOrder()) setCatalogOrder(next)

        val disabled = disabledCatalogKeys()
        val validDisabled = disabled.filterTo(mutableSetOf()) { it in available }
        if (validDisabled != disabled) {
            prefs.edit().putStringSet(KEY_DISABLED_CATALOG_KEYS, validDisabled).apply()
        }
        return next
    }

    fun seedDevelopmentDefaultsIfNeeded(): Boolean {
        val revision = prefs.getInt(KEY_DEV_DEFAULTS_REVISION, 0)
        if (revision >= DEV_DEFAULTS_REVISION) return false

        val next = manifestUrls().toMutableSet()
        next += DEVELOPMENT_DEFAULT_MANIFESTS
        prefs.edit()
            .putStringSet(KEY_MANIFEST_URLS, next)
            .putInt(KEY_DEV_DEFAULTS_REVISION, DEV_DEFAULTS_REVISION)
            .apply()
        return true
    }

    private fun addonEnabledKey(manifestUrl: String): String =
        "addon_enabled:$manifestUrl"

    companion object {
        private const val PREFS_NAME = "vueo_content_manager"
        private const val KEY_MANIFEST_URLS = "stremio_manifest_urls"
        private const val KEY_CATALOG_ORDER = "catalog_order"
        private const val KEY_DISABLED_CATALOG_KEYS = "disabled_catalog_keys"
        private const val KEY_DEV_DEFAULTS_REVISION = "dev_defaults_revision"
        private const val DEV_DEFAULTS_REVISION = 1

        val DEVELOPMENT_DEFAULT_MANIFESTS = setOf(
            "https://yastream.tamthai.de/manifest.json",
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
        )
    }
}
