package com.vueo.tv.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TvContentManagerStore(
    context: Context,
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    init {
        seedDefaultsIfNeeded()
    }

    suspend fun snapshot(): TvContentSnapshot =
        withContext(Dispatchers.IO) {
            val addons =
                addonUrls().map { url ->
                    runCatching { fetchAddon(url) }
                        .getOrElse {
                            TvStremioAddonInfo(
                                manifestUrl = url,
                                name = fallbackName(url),
                                version = null,
                                resources = emptyList(),
                                enabled = isAddonEnabled(url),
                                reachable = false,
                            )
                        }
                }

            val repositories =
                repositoryUrls().map { url ->
                    runCatching { fetchRepository(url) }
                        .getOrElse {
                            TvPluginRepositoryInfo(
                                manifestUrl = url,
                                name = fallbackName(url),
                                version = null,
                                description = null,
                                enabled = isRepositoryEnabled(url),
                                reachable = false,
                                providers = emptyList(),
                            )
                        }
                }

            TvContentSnapshot(
                addons = addons.sortedBy { it.name.lowercase() },
                repositories = repositories.sortedBy { it.name.lowercase() },
            )
        }

    suspend fun addAddon(input: String): TvStremioAddonInfo =
        withContext(Dispatchers.IO) {
            val url = normalizeManifestUrl(input)
            val info = fetchAddon(url)
            val next = addonUrls().toMutableSet().apply { add(url) }
            prefs.edit()
                .putStringSet(KEY_ADDON_URLS, next)
                .putBoolean(addonEnabledKey(url), true)
                .apply()
            info.copy(enabled = true)
        }

    suspend fun addRepository(input: String): TvPluginRepositoryInfo =
        withContext(Dispatchers.IO) {
            val url = normalizeManifestUrl(input)
            val info = fetchRepository(url)
            require(info.providers.isNotEmpty()) {
                "Repository manifest contains no providers."
            }
            val next = repositoryUrls().toMutableSet().apply { add(url) }
            prefs.edit()
                .putStringSet(KEY_REPOSITORY_URLS, next)
                .putBoolean(repositoryEnabledKey(url), true)
                .apply()
            info.copy(enabled = true)
        }

    fun setAddonEnabled(url: String, enabled: Boolean) {
        prefs.edit().putBoolean(addonEnabledKey(url), enabled).apply()
    }

    fun setRepositoryEnabled(url: String, enabled: Boolean) {
        prefs.edit().putBoolean(repositoryEnabledKey(url), enabled).apply()
    }

    fun setProviderEnabled(
        repositoryUrl: String,
        providerId: String,
        enabled: Boolean,
    ) {
        prefs.edit().putBoolean(providerEnabledKey(repositoryUrl, providerId), enabled).apply()
    }

    fun enabledAddonUrls(): List<String> =
        addonUrls().filter(::isAddonEnabled)

    fun enabledRepositoryUrls(): List<String> =
        repositoryUrls().filter(::isRepositoryEnabled)

    private fun addonUrls(): List<String> =
        prefs.getStringSet(KEY_ADDON_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    private fun repositoryUrls(): List<String> =
        prefs.getStringSet(KEY_REPOSITORY_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    private fun isAddonEnabled(url: String): Boolean =
        prefs.getBoolean(addonEnabledKey(url), true)

    private fun isRepositoryEnabled(url: String): Boolean =
        prefs.getBoolean(repositoryEnabledKey(url), true)

    private fun isProviderEnabled(
        repositoryUrl: String,
        providerId: String,
        defaultEnabled: Boolean,
    ): Boolean {
        val key = providerEnabledKey(repositoryUrl, providerId)
        return if (prefs.contains(key)) {
            prefs.getBoolean(key, defaultEnabled)
        } else {
            defaultEnabled
        }
    }

    private fun seedDefaultsIfNeeded() {
        val revision = prefs.getInt(KEY_DEFAULTS_REVISION, 0)
        if (revision >= DEFAULTS_REVISION) return

        val addons = addonUrls().toMutableSet().apply { addAll(DEFAULT_STREMIO_ADDONS) }
        val repositories = repositoryUrls().toMutableSet().apply { addAll(DEFAULT_PLUGIN_REPOSITORIES) }

        prefs.edit()
            .putStringSet(KEY_ADDON_URLS, addons)
            .putStringSet(KEY_REPOSITORY_URLS, repositories)
            .putInt(KEY_DEFAULTS_REVISION, DEFAULTS_REVISION)
            .apply()
    }

    private fun fetchAddon(manifestUrl: String): TvStremioAddonInfo {
        val json = JSONObject(httpGet(manifestUrl))
        val name = json.optString("name").trim().ifBlank { fallbackName(manifestUrl) }
        val resources = json.optJSONArray("resources").resourceNames()

        return TvStremioAddonInfo(
            manifestUrl = manifestUrl,
            name = name,
            version = json.optString("version").trim().takeIf { it.isNotBlank() },
            resources = resources,
            enabled = isAddonEnabled(manifestUrl),
            reachable = true,
        )
    }

    private fun fetchRepository(manifestUrl: String): TvPluginRepositoryInfo {
        val json = JSONObject(httpGet(manifestUrl))
        val providersJson =
            json.optJSONArray("scrapers")
                ?: json.optJSONArray("providers")
                ?: JSONArray()

        val providers =
            (0 until providersJson.length())
                .mapNotNull { index ->
                    val item = providersJson.optJSONObject(index) ?: return@mapNotNull null
                    val id = item.optString("id").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val name = item.optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val defaultEnabled = item.optBoolean("enabled", true)
                    TvPluginProviderInfo(
                        id = id,
                        name = name,
                        description = item.optString("description").trim().takeIf { it.isNotBlank() },
                        version = item.optString("version").trim().takeIf { it.isNotBlank() },
                        enabled = isProviderEnabled(manifestUrl, id, defaultEnabled),
                    )
                }

        return TvPluginRepositoryInfo(
            manifestUrl = manifestUrl,
            name = json.optString("name").trim().ifBlank { fallbackName(manifestUrl) },
            version = json.optString("version").trim().takeIf { it.isNotBlank() },
            description = json.optString("description").trim().takeIf { it.isNotBlank() },
            enabled = isRepositoryEnabled(manifestUrl),
            reachable = true,
            providers = providers,
        )
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://")) {
            "VUEO requires an HTTPS manifest URL."
        }

        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VUEO-TV-Content-Manager")
            }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("HTTP $code while loading manifest")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeManifestUrl(input: String): String {
        val trimmed = input.trim().removeSuffix("/")
        require(trimmed.startsWith("https://")) {
            "Use an HTTPS manifest or repository URL."
        }
        return if (trimmed.endsWith("manifest.json")) trimmed else "$trimmed/manifest.json"
    }

    private fun fallbackName(url: String): String =
        runCatching { URL(url).host.removePrefix("www.") }
            .getOrDefault(url)

    private fun addonEnabledKey(url: String) = "addon_enabled:$url"
    private fun repositoryEnabledKey(url: String) = "repository_enabled:$url"
    private fun providerEnabledKey(repositoryUrl: String, providerId: String) =
        "provider_enabled:$repositoryUrl:$providerId"

    companion object {
        private const val PREFS_NAME = "vueo_tv_content_manager"
        private const val KEY_ADDON_URLS = "stremio_manifest_urls"
        private const val KEY_REPOSITORY_URLS = "plugin_repository_urls"
        private const val KEY_DEFAULTS_REVISION = "defaults_revision"
        private const val DEFAULTS_REVISION = 1
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000

        val DEFAULT_STREMIO_ADDONS = setOf(
            "https://yastream.tamthai.de/manifest.json",
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
        )

        val DEFAULT_PLUGIN_REPOSITORIES = setOf(
            "https://raw.githubusercontent.com/yoruix/nuvio-providers/refs/heads/main/manifest.json",
            "https://raw.githubusercontent.com/D3adlyRocket/All-in-One-Nuvio/refs/heads/main/manifest.json",
        )
    }
}

data class TvContentSnapshot(
    val addons: List<TvStremioAddonInfo>,
    val repositories: List<TvPluginRepositoryInfo>,
)

data class TvStremioAddonInfo(
    val manifestUrl: String,
    val name: String,
    val version: String?,
    val resources: List<String>,
    val enabled: Boolean,
    val reachable: Boolean,
)

data class TvPluginRepositoryInfo(
    val manifestUrl: String,
    val name: String,
    val version: String?,
    val description: String?,
    val enabled: Boolean,
    val reachable: Boolean,
    val providers: List<TvPluginProviderInfo>,
)

data class TvPluginProviderInfo(
    val id: String,
    val name: String,
    val description: String?,
    val version: String?,
    val enabled: Boolean,
)

private fun JSONArray?.resourceNames(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index ->
            when (val value = opt(index)) {
                is String -> value.takeIf { it.isNotBlank() }
                is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }
                else -> null
            }
        }
        .distinct()
}
