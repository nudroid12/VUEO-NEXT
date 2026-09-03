package com.vueo.app.core.plugin

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object PluginRepositoryClient {
    suspend fun fetch(inputUrl: String): PluginRepositoryDescriptor {
        val manifestUrl = normalizeManifestUrl(inputUrl)
        require(manifestUrl.startsWith("https://")) {
            "VUEO requires an HTTPS plugin repository URL."
        }

        val json = JSONObject(PluginHttp.getText(manifestUrl))
        val providersArray = json.optJSONArray("scrapers")
            ?: json.optJSONArray("providers")
            ?: JSONArray()

        val providers = (0 until providersArray.length())
            .mapNotNull { index ->
                providersArray.optJSONObject(index)?.toProvider()
            }

        require(providers.isNotEmpty()) {
            "Repository manifest contains no scrapers/providers."
        }

        val repoName = json.optString("name")
            .takeIf { it.isNotBlank() }
            ?: "Plugin Repository"

        return PluginRepositoryDescriptor(
            manifestUrl = manifestUrl,
            baseUrl = manifestUrl.substringBeforeLast("/"),
            name = repoName,
            version = json.optString("version", "0.0.0"),
            description = json.optString("description")
                .takeIf { it.isNotBlank() },
            providers = providers,
        )
    }

    fun providerScriptUrl(
        repository: PluginRepositoryDescriptor,
        provider: PluginProviderDescriptor,
    ): String {
        val filename = provider.filename.trim()

        return when {
            filename.startsWith("https://") -> filename
            filename.startsWith("/") -> repository.baseUrl + filename
            else -> repository.baseUrl + "/" + filename
        }
    }

    private fun normalizeManifestUrl(input: String): String {
        val trimmed = input.trim().removeSuffix("/")

        if (trimmed.endsWith("manifest.json")) {
            return trimmed
        }

        return "$trimmed/manifest.json"
    }

    private fun JSONObject.toProvider(): PluginProviderDescriptor? {
        val id = optString("id").takeIf { it.isNotBlank() }
            ?: return null
        val name = optString("name").takeIf { it.isNotBlank() }
            ?: return null
        val filename = optString("filename").takeIf { it.isNotBlank() }
            ?: return null

        return PluginProviderDescriptor(
            id = id,
            name = name,
            description = optString("description")
                .takeIf { it.isNotBlank() },
            version = optString("version", "0.0.0"),
            author = optString("author").takeIf { it.isNotBlank() },
            supportedTypes = optJSONArray("supportedTypes").toStringSet(),
            filename = filename,
            defaultEnabled = optBoolean("enabled", true),
            logo = optString("logo")
                .takeIf { it.startsWith("https://") },
            contentLanguages = optJSONArray("contentLanguage").toStringList(),
            formats = optJSONArray("formats").toStringList(),
            limited = optBoolean("limited", false),
            disabledPlatforms = optJSONArray("disabledPlatforms").toStringSet(),
            supportsExternalPlayer = optBoolean(
                "supportsExternalPlayer",
                true,
            ),
        )
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { optString(it).takeIf(String::isNotBlank) }
}

private fun JSONArray?.toStringSet(): Set<String> =
    toStringList().toSet()
