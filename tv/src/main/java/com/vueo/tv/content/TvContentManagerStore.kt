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
            providerCodeSyncManager.syncEnabled(
                repositories = pluginStore.repositories(),
                pluginStore = pluginStore,
            )
            pluginsPrepared = true
        }
    }

    fun sharedPluginStore(): PluginStore = pluginStore

    suspend fun addons(): List<TvStremioAddonInfo> =
        withContext(Dispatchers.IO) {
            addonUrls().map { url ->
                runCatching { fetchAddon(url) }
                    .getOrElse {
                        TvStremioAddonInfo(
                            id = fallbackName(url),
                            manifestUrl = url,
                            baseUrl = url.removeSuffix("/manifest.json").removeSuffix("/"),
                            name = fallbackName(url),
                            version = null,
                            resources = emptyList(),
                            catalogs = emptyList(),
                            enabled = isAddonEnabled(url),
                            reachable = false,
                        )
                    }
            }.sortedBy { it.name.lowercase() }
        }

    suspend fun snapshot(): TvContentSnapshot =
        withContext(Dispatchers.IO) {
            preparePlugins()

            val addons = addons()

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
            bumpDiscoveryRevision()
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
        if (isAddonEnabled(url) == enabled) return
        prefs.edit().putBoolean(addonEnabledKey(url), enabled).apply()
        bumpDiscoveryRevision()
    }

    fun catalogOrder(): List<String> =
        prefs.getString(KEY_CATALOG_ORDER, "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()

    fun setCatalogOrder(order: List<String>) {
        val normalized = order.map(String::trim).filter(String::isNotBlank).distinct()
        if (normalized == catalogOrder()) return
        prefs.edit()
            .putString(KEY_CATALOG_ORDER, normalized.joinToString("\n"))
            .apply()
        bumpDiscoveryRevision()
    }

    fun reconcileCatalogOrder(availableKeys: List<String>): List<String> {
        val available = availableKeys.map(String::trim).filter(String::isNotBlank).distinct()
        val current = catalogOrder().filter { it in available }
        val next = (current + available.filterNot { it in current }).distinct()
        if (next != catalogOrder()) {
            prefs.edit().putString(KEY_CATALOG_ORDER, next.joinToString("\n")).apply()
        }
        return next
    }

    fun discoveryRevision(): Int = prefs.getInt(KEY_DISCOVERY_REVISION, 0)

    fun invalidateDiscovery() {
        bumpDiscoveryRevision()
    }

    private fun bumpDiscoveryRevision() {
        val next = discoveryRevision().let { if (it == Int.MAX_VALUE) 1 else it + 1 }
        prefs.edit().putInt(KEY_DISCOVERY_REVISION, next).apply()
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
        val id = json.optString("id").trim().ifBlank { fallbackName(manifestUrl) }
        val name = json.optString("name").trim().ifBlank { fallbackName(manifestUrl) }
        val resources = json.optJSONArray("resources").resourceNames()
        val baseUrl = manifestUrl.removeSuffix("/manifest.json").removeSuffix("/")
        val catalogs = json.optJSONArray("catalogs").catalogs(
            addonId = id,
            manifestUrl = manifestUrl,
            baseUrl = baseUrl,
            providerName = name,
        )

        return TvStremioAddonInfo(
            id = id,
            manifestUrl = manifestUrl,
            baseUrl = baseUrl,
            name = name,
            version = json.optString("version").trim().takeIf { it.isNotBlank() },
            resources = resources,
            catalogs = catalogs,
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
        private const val KEY_CATALOG_ORDER = "catalog_order_v1"
        private const val KEY_DISCOVERY_REVISION = "discovery_revision_v1"
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
    val id: String,
    val manifestUrl: String,
    val baseUrl: String,
    val name: String,
    val version: String?,
    val resources: List<String>,
    val catalogs: List<TvStremioCatalogInfo>,
    val enabled: Boolean,
    val reachable: Boolean,
)

data class TvStremioCatalogExtraInfo(
    val name: String,
    val isRequired: Boolean,
    val options: List<String>,
)

data class TvStremioCatalogInfo(
    val key: String,
    val addonId: String,
    val manifestUrl: String,
    val baseUrl: String,
    val providerName: String,
    val type: String,
    val id: String,
    val name: String,
    val extras: List<TvStremioCatalogExtraInfo>,
) {
    val canLoadWithoutExtras: Boolean
        get() = extras.none { it.isRequired }

    val requiredExtras: List<String>
        get() = extras.filter { it.isRequired }.map { it.name }

    val supportsSearch: Boolean
        get() = extras.any { it.name.equals("search", ignoreCase = true) }
}

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

private fun JSONArray?.catalogs(
    addonId: String,
    manifestUrl: String,
    baseUrl: String,
    providerName: String,
): List<TvStremioCatalogInfo> {
    if (this == null) return emptyList()

    return (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val type = item.optString("type").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val id = item.optString("id").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = item.optString("name").trim().ifBlank { id }
        val extras = item.optJSONArray("extra").catalogExtras()

        TvStremioCatalogInfo(
            key = "$addonId:$type:$id",
            addonId = addonId,
            manifestUrl = manifestUrl,
            baseUrl = baseUrl,
            providerName = providerName,
            type = type,
            id = id,
            name = name,
            extras = extras,
        )
    }
}

private fun JSONArray?.catalogExtras(): List<TvStremioCatalogExtraInfo> {
    if (this == null) return emptyList()

    return (0 until length()).mapNotNull { index ->
        when (val value = opt(index)) {
            is String ->
                value.trim().takeIf { it.isNotBlank() }?.let {
                    TvStremioCatalogExtraInfo(it, false, emptyList())
                }
            is JSONObject -> {
                val name = value.optString("name").trim().takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val options = value.optJSONArray("options").stringValues()
                TvStremioCatalogExtraInfo(
                    name = name,
                    isRequired = value.optBoolean("isRequired", false),
                    options = options,
                )
            }
            else -> null
        }
    }
}

private fun JSONArray?.stringValues(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index -> optString(index).trim().takeIf { it.isNotBlank() } }
}

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
