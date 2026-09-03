package com.vueo.shared.core.plugin

import org.json.JSONArray
import org.json.JSONObject

object PluginRepositoryClient {
    suspend fun fetch(inputUrl: String): PluginRepositoryDescriptor {
        val manifestUrl = normalizeManifestUrl(inputUrl)
        require(manifestUrl.startsWith("https://")) {
            "VUEO requires an HTTPS plugin repository URL."
        }

        val json = JSONObject(PluginHttp.getText(manifestUrl))
        val providersArray =
            json.optJSONArray("scrapers")
                ?: json.optJSONArray("providers")
                ?: json.optJSONArray("plugins")
                ?: JSONArray()

        val providers =
            (0 until providersArray.length())
                .mapNotNull { index ->
                    providersArray.optJSONObject(index)?.toProvider()
                }
                .distinctBy { it.id }

        require(providers.isNotEmpty()) {
            "Repository manifest contains no scrapers/providers."
        }

        val repoName =
            json.optString("name")
                .takeIf { it.isNotBlank() }
                ?: "Plugin Repository"

        return PluginRepositoryDescriptor(
            manifestUrl = manifestUrl,
            baseUrl = manifestUrl.substringBeforeLast("/"),
            name = repoName,
            version = json.optString("version", "0.0.0"),
            description =
                json.optString("description")
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
        val id =
            optString("id")
                .takeIf { it.isNotBlank() }
                ?: return null

        val name =
            optString("name")
                .takeIf { it.isNotBlank() }
                ?: return null

        val filename =
            firstString(
                "filename",
                "file",
                "script",
            ) ?: return null

        val supportedTypes =
            firstStringList(
                "supportedTypes",
                "types",
                "mediaTypes",
            )
                .map { it.lowercase() }
                .toSet()

        val disabledPlatforms =
            firstStringList(
                "disabledPlatforms",
                "disabledPlatform",
            )
                .map { it.lowercase() }
                .toSet()

        return PluginProviderDescriptor(
            id = id,
            name = name,
            description =
                optString("description")
                    .takeIf { it.isNotBlank() },
            version = optString("version", "0.0.0"),
            author =
                optString("author")
                    .takeIf { it.isNotBlank() },
            supportedTypes = supportedTypes,
            filename = filename,
            defaultEnabled =
                when {
                    has("defaultEnabled") ->
                        optBoolean("defaultEnabled", true)
                    else ->
                        optBoolean("enabled", true)
                },
            logo =
                firstString("logo", "icon")
                    ?.takeIf { it.startsWith("https://") },
            contentLanguages =
                firstStringList(
                    "contentLanguage",
                    "contentLanguages",
                    "languages",
                ),
            formats =
                firstStringList(
                    "formats",
                    "format",
                ),
            limited = optBoolean("limited", false),
            disabledPlatforms = disabledPlatforms,
            supportsExternalPlayer =
                optBoolean(
                    "supportsExternalPlayer",
                    true,
                ),
        )
    }

    private fun JSONObject.firstString(
        vararg keys: String,
    ): String? =
        keys.asSequence()
            .map { key -> optString(key).trim() }
            .firstOrNull { it.isNotBlank() }

    private fun JSONObject.firstStringList(
        vararg keys: String,
    ): List<String> {
        keys.forEach { key ->
            val value = opt(key)

            when (value) {
                is JSONArray -> {
                    val parsed = value.toStringList()
                    if (parsed.isNotEmpty()) return parsed
                }

                is String -> {
                    val parsed = value.toFlexibleStringList()
                    if (parsed.isNotEmpty()) return parsed
                }
            }
        }

        return emptyList()
    }
}

private fun String.toFlexibleStringList(): List<String> {
    val trimmed = trim()
    if (trimmed.isBlank()) return emptyList()

    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
        return runCatching {
            JSONArray(trimmed).toStringList()
        }.getOrDefault(emptyList())
    }

    return trimmed
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length())
        .mapNotNull { index ->
            optString(index)
                .trim()
                .takeIf(String::isNotBlank)
        }
