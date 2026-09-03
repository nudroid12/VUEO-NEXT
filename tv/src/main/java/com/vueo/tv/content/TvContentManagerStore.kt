package com.vueo.tv.content

import android.content.Context
import com.vueo.shared.core.plugin.PluginProviderDescriptor
import com.vueo.shared.core.plugin.PluginRepositoryDescriptor
import com.vueo.shared.core.plugin.PluginRepositoryManager
import com.vueo.shared.core.plugin.PluginStore
import com.vueo.shared.core.plugin.ProviderCodeSyncManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TvContentManagerStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    private val pluginStore = PluginStore(appContext)
    private val pluginRepositoryManager = PluginRepositoryManager(appContext)
    private val providerCodeSyncManager = ProviderCodeSyncManager(appContext)
    private val pluginPrepareMutex = Mutex()
    @Volatile private var pluginsPrepared = false

    init {
        seedAddonDefaultsIfNeeded()
    }

    suspend fun preparePlugins() {
        if (pluginsPrepared) return
        pluginPrepareMutex.withLock {
            if (pluginsPrepared) return@withLock
            pluginStore.seedDevelopmentDefaultsIfNeeded()
            migrateLegacyPluginStateIfNeeded()
            providerCodeSyncManager.syncMissing(pluginStore.repositories())
            pluginsPrepared = true
        }
    }

    fun sharedPluginStore(): PluginStore = pluginStore

    suspend fun snapshot(): TvContentSnapshot =
        withContext(Dispatchers.IO) {
            preparePlugins()

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
                pluginStore.repositories()
                    .map { repository -> repository.toTvInfo() }

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
            val installed =
                pluginRepositoryManager.installOrRefresh(
                    inputUrl = input,
                    forceCodeRefresh = true,
                )
            pluginStore.setRepositoryEnabled(installed.repository, true)
            installed.repository.providers.forEach { provider ->
                pluginStore.setProviderEnabled(
                    repository = installed.repository,
                    provider = provider,
                    enabled = provider.defaultEnabled,
                )
            }
            installed.repository.toTvInfo()
        }

    fun setAddonEnabled(url: String, enabled: Boolean) {
        prefs.edit().putBoolean(addonEnabledKey(url), enabled).apply()
    }

    fun setRepositoryEnabled(url: String, enabled: Boolean) {
        pluginStore.repositories()
            .firstOrNull { it.manifestUrl == url }
            ?.let { pluginStore.setRepositoryEnabled(it, enabled) }
    }

    fun setProviderEnabled(
        repositoryUrl: String,
        providerId: String,
        enabled: Boolean,
    ) {
        val repository =
            pluginStore.repositories()
                .firstOrNull { it.manifestUrl == repositoryUrl }
                ?: return
        val provider =
            repository.providers.firstOrNull { it.id == providerId }
                ?: return
        pluginStore.setProviderEnabled(repository, provider, enabled)
    }

    fun enabledAddonUrls(): List<String> =
        addonUrls().filter(::isAddonEnabled)

    private fun addonUrls(): List<String> =
        prefs.getStringSet(KEY_ADDON_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    private fun legacyRepositoryUrls(): List<String> =
        prefs.getStringSet(KEY_REPOSITORY_URLS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    private fun isAddonEnabled(url: String): Boolean =
        prefs.getBoolean(addonEnabledKey(url), true)

    private fun seedAddonDefaultsIfNeeded() {
        val revision = prefs.getInt(KEY_DEFAULTS_REVISION, 0)
        if (revision >= DEFAULTS_REVISION) return

        val addons = addonUrls().toMutableSet().apply { addAll(DEFAULT_STREMIO_ADDONS) }
        prefs.edit()
            .putStringSet(KEY_ADDON_URLS, addons)
            .putInt(KEY_DEFAULTS_REVISION, DEFAULTS_REVISION)
            .apply()
    }

    private suspend fun migrateLegacyPluginStateIfNeeded() {
        if (prefs.getBoolean(KEY_SHARED_PLUGIN_MIGRATION_DONE, false)) return

        legacyRepositoryUrls().forEach { manifestUrl ->
            runCatching {
                pluginRepositoryManager.installOrRefresh(
                    inputUrl = manifestUrl,
                    forceCodeRefresh = false,
                ).repository
            }.onSuccess { repository ->
                val repositoryEnabled =
                    prefs.getBoolean(repositoryEnabledKey(repository.manifestUrl), true)
                pluginStore.setRepositoryEnabled(repository, repositoryEnabled)
                repository.providers.forEach { provider ->
                    val legacyKey = providerEnabledKey(repository.manifestUrl, provider.id)
                    if (prefs.contains(legacyKey)) {
                        pluginStore.setProviderEnabled(
                            repository = repository,
                            provider = provider,
                            enabled = prefs.getBoolean(legacyKey, provider.defaultEnabled),
                        )
                    }
                }
            }
        }

        prefs.edit().putBoolean(KEY_SHARED_PLUGIN_MIGRATION_DONE, true).apply()
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

    private fun PluginRepositoryDescriptor.toTvInfo(): TvPluginRepositoryInfo =
        TvPluginRepositoryInfo(
            manifestUrl = manifestUrl,
            name = name,
            version = version,
            description = description,
            enabled = pluginStore.isRepositoryEnabled(this),
            reachable = true,
            providers = providers.map { provider -> provider.toTvInfo(this) },
        )

    private fun PluginProviderDescriptor.toTvInfo(
        repository: PluginRepositoryDescriptor,
    ): TvPluginProviderInfo =
        TvPluginProviderInfo(
            id = id,
            name = name,
            description = description,
            version = version,
            enabled = pluginStore.isProviderEnabled(repository, this),
        )

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
            if (code !in 200..299) error("HTTP $code while loading manifest")
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
        private const val KEY_SHARED_PLUGIN_MIGRATION_DONE = "shared_plugin_migration_done_v1"
        private const val DEFAULTS_REVISION = 1
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 7_000

        val DEFAULT_STREMIO_ADDONS = setOf(
            "https://yastream.tamthai.de/manifest.json",
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
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
