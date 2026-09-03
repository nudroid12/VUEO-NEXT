package com.vueo.shared.core.plugin

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

enum class ProviderHealthStatus(val label: String) {
    ONLINE("Online"),
    SLOW("Slow"),
    NO_RESULTS("No Results"),
    NEEDS_SETUP("Needs Setup"),
    UNAVAILABLE("Unavailable"),
    BLOCKED("Blocked"),
    TIMEOUT("Timeout"),
    FAILED("Failed"),
    UNKNOWN("Unknown"),
}

data class ProviderHealthRecord(
    val repositoryManifestUrl: String,
    val repositoryName: String,
    val providerId: String,
    val providerName: String,
    val status: ProviderHealthStatus,
    val responseMs: Long? = null,
    val streamCount: Int = 0,
    val error: String? = null,
    val logs: List<String> = emptyList(),
    val lastCheckedEpochMs: Long,
)

data class ProviderHealthSummary(
    val online: Int,
    val slow: Int,
    val noResults: Int,
    val needsSetup: Int,
    val unavailable: Int,
    val blocked: Int,
    val timeout: Int,
    val failed: Int,
    val unknown: Int,
    val disabled: Int,
)

class PluginHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun records(): List<ProviderHealthRecord> = prefs.all
        .asSequence()
        .filter { (key, value) -> key.startsWith(RECORD_PREFIX) && value is String }
        .mapNotNull { (_, value) ->
            runCatching { JSONObject(value as String).toRecord() }.getOrNull()
        }
        .sortedBy { "${it.repositoryName}:${it.providerName}" }
        .toList()

    fun record(repositoryManifestUrl: String, providerId: String): ProviderHealthRecord? =
        records().firstOrNull {
            it.repositoryManifestUrl == repositoryManifestUrl && it.providerId == providerId
        }

    @Synchronized
    fun save(record: ProviderHealthRecord) {
        prefs.edit()
            .putString(recordKey(record.repositoryManifestUrl, record.providerId), record.toJson().toString())
            .apply()
    }

    @Synchronized
    fun removeRepository(manifestUrl: String) {
        val editor = prefs.edit()
        records()
            .filter { it.repositoryManifestUrl == manifestUrl }
            .forEach { editor.remove(recordKey(it.repositoryManifestUrl, it.providerId)) }
        editor.apply()
    }

    fun summary(
        repositories: List<PluginRepositoryDescriptor>,
        pluginStore: PluginStore,
    ): ProviderHealthSummary {
        val known = records().associateBy { it.repositoryManifestUrl to it.providerId }
        val counts = ProviderHealthStatus.entries.associateWith { 0 }.toMutableMap()
        var disabled = 0

        repositories.forEach { repository ->
            repository.providers.forEach { provider ->
                if (!pluginStore.isProviderEnabled(repository, provider)) {
                    disabled++
                } else {
                    val status = known[repository.manifestUrl to provider.id]?.status
                        ?: ProviderHealthStatus.UNKNOWN
                    counts[status] = (counts[status] ?: 0) + 1
                }
            }
        }

        return ProviderHealthSummary(
            online = counts[ProviderHealthStatus.ONLINE] ?: 0,
            slow = counts[ProviderHealthStatus.SLOW] ?: 0,
            noResults = counts[ProviderHealthStatus.NO_RESULTS] ?: 0,
            needsSetup = counts[ProviderHealthStatus.NEEDS_SETUP] ?: 0,
            unavailable = counts[ProviderHealthStatus.UNAVAILABLE] ?: 0,
            blocked = counts[ProviderHealthStatus.BLOCKED] ?: 0,
            timeout = counts[ProviderHealthStatus.TIMEOUT] ?: 0,
            failed = counts[ProviderHealthStatus.FAILED] ?: 0,
            unknown = counts[ProviderHealthStatus.UNKNOWN] ?: 0,
            disabled = disabled,
        )
    }

    private fun recordKey(repositoryManifestUrl: String, providerId: String): String {
        val identity = "$repositoryManifestUrl\u0000$providerId"
        val encoded = Base64.encodeToString(
            identity.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
        return RECORD_PREFIX + encoded
    }

    companion object {
        private const val PREFS_NAME = "vueo_plugin_health"
        private const val RECORD_PREFIX = "record_v2:"
    }
}

private fun ProviderHealthRecord.toJson(): JSONObject = JSONObject()
    .put("repositoryManifestUrl", repositoryManifestUrl)
    .put("repositoryName", repositoryName)
    .put("providerId", providerId)
    .put("providerName", providerName)
    .put("status", status.name)
    .apply { responseMs?.let { put("responseMs", it) } }
    .put("streamCount", streamCount)
    .put("error", error)
    .put("logs", JSONArray(logs))
    .put("lastCheckedEpochMs", lastCheckedEpochMs)

private fun JSONObject.toRecord(): ProviderHealthRecord? {
    val repositoryManifestUrl = optString("repositoryManifestUrl").takeIf { it.isNotBlank() }
        ?: return null
    val providerId = optString("providerId").takeIf { it.isNotBlank() } ?: return null

    return ProviderHealthRecord(
        repositoryManifestUrl = repositoryManifestUrl,
        repositoryName = optString("repositoryName", "Repository"),
        providerId = providerId,
        providerName = optString("providerName", providerId),
        status = runCatching {
            ProviderHealthStatus.valueOf(optString("status", ProviderHealthStatus.UNKNOWN.name))
        }.getOrDefault(ProviderHealthStatus.UNKNOWN),
        responseMs = if (has("responseMs")) optLong("responseMs") else null,
        streamCount = optInt("streamCount", 0),
        error = optString("error").takeIf { it.isNotBlank() && it != "null" },
        logs = optJSONArray("logs").toStringList(),
        lastCheckedEpochMs = optLong("lastCheckedEpochMs", 0L),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}
