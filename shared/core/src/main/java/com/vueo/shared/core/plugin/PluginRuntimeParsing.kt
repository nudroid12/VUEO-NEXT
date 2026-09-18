package com.vueo.shared.core.plugin

import android.app.ActivityManager
import android.content.Context
import android.util.Base64
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import com.vueo.shared.core.diagnostics.RuntimeDiagnostics
import com.vueo.shared.core.source.SourceCandidate
import com.vueo.shared.core.source.SourceRequest
import com.vueo.shared.core.source.SourceResolveResult
import com.vueo.shared.core.source.SourceResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

internal fun classifyProviderFailure(
    error: String,
): ProviderHealthStatus {
    val normalized =
        error.lowercase()

    return when {
        "timed out" in normalized ||
            "timeout" in normalized ->
            ProviderHealthStatus.TIMEOUT

        "nxdomain" in normalized ||
            "unable to resolve host" in normalized ||
            "unknownhost" in normalized ||
            "no address associated" in normalized ->
            ProviderHealthStatus.UNAVAILABLE

        "no token available" in normalized ||
            "token required" in normalized ||
            "requires token" in normalized ||
            "missing token" in normalized ||
            "ui token" in normalized &&
            (
                "expired" in normalized ||
                "required" in normalized ||
                "missing" in normalized
            ) ->
            ProviderHealthStatus.NEEDS_SETUP

        "http 403" in normalized ||
            "status 403" in normalized ||
            "forbidden" in normalized ||
            "cloudflare" in normalized ||
            "captcha" in normalized ->
            ProviderHealthStatus.BLOCKED

        else ->
            ProviderHealthStatus.FAILED
    }
}

internal fun parseProviderStreams(
    repository: PluginRepositoryDescriptor,
    provider: PluginProviderDescriptor,
    resultJson: String,
): List<SourceCandidate> {
    val array =
        runCatching {
            JSONArray(resultJson)
        }.getOrNull()
            ?: return emptyList()

    return (0 until array.length())
        .mapNotNull { index ->
            val item =
                array.optJSONObject(index)
                    ?: return@mapNotNull null

            val url =
                item.optString("url")
                    .takeIf {
                        it.startsWith(
                            "https://"
                        ) ||
                        it.startsWith(
                            "http://"
                        )
                    }
                    ?: return@mapNotNull null

            val headers =
                item.optJSONObject("headers")
                    .toStringMap()

            val quality =
                item.optString("quality")
                    .takeIf {
                        it.isNotBlank()
                    }

            val title =
                item.optString("title")
                    .trim()
                    .takeIf { it.isNotBlank() }

            val itemName =
                item.optString("name")
                    .trim()
                    .takeIf { it.isNotBlank() }

            val displayName =
                title
                    ?: itemName
                    ?: provider.name

            val serverName =
                listOf(
                    "server",
                    "extractor",
                    "host",
                    "embedder",
                ).firstNotNullOfOrNull { field ->
                    item.metadataLabel(field)
                }
                    ?.takeUnless { candidate ->
                        candidate.equals(displayName, ignoreCase = true) ||
                            candidate.equals(provider.name, ignoreCase = true)
                    }
                    ?: itemName?.takeIf { candidate ->
                        title != null &&
                            !candidate.equals(title, ignoreCase = true) &&
                            !candidate.equals(provider.name, ignoreCase = true)
                    }
                    ?: item.metadataLabel("source")
                        ?.takeUnless { candidate ->
                            candidate.equals(displayName, ignoreCase = true) ||
                                candidate.equals(provider.name, ignoreCase = true)
                        }

            SourceCandidate(
                id =
                    "plugin:" +
                    repository.manifestUrl.hashCode() +
                    ":" + provider.id +
                    ":" + index,
                name =
                    displayName,
                url =
                    url,
                quality =
                    quality,
                codec =
                    item.optString("codec")
                        .takeIf { it.isNotBlank() },
                hdr =
                    item.optString("hdr")
                        .takeIf { it.isNotBlank() },
                audio =
                    item.optString("audio")
                        .takeIf { it.isNotBlank() },
                language =
                    listOf(
                        "language",
                        "lang",
                        "audioLanguage",
                        "audio_language",
                    ).firstNotNullOfOrNull { field ->
                        item.optString(field)
                            .trim()
                            .takeIf { it.isNotBlank() }
                    },
                headers =
                    headers,
                providerId =
                    "plugin:" +
                    repository.manifestUrl
                        .hashCode() +
                    ":" +
                    provider.id,
                providerName =
                    "${repository.name} / " +
                    provider.name,
                serverName =
                    serverName,
            )
        }
}

private fun JSONObject.metadataLabel(
    field: String,
): String? {
    val value = opt(field)
    val label = when (value) {
        is String -> value
        is JSONObject ->
            listOf("name", "title", "label", "id")
                .firstNotNullOfOrNull { key ->
                    value.optString(key)
                        .trim()
                        .takeIf { it.isNotBlank() }
                }
        else -> null
    }
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return label.takeIf { candidate ->
        candidate.length <= 80 &&
            !candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
    }
}

internal fun JSONObject?.toStringMap():
    Map<String, String> {

    if (this == null) {
        return emptyMap()
    }

    val result =
        linkedMapOf<String, String>()

    val iterator =
        keys()

    while (
        iterator.hasNext()
    ) {
        val key =
            iterator.next()

        result[key] =
            optString(key)
    }

    return result
}

/** Exposes JavaScript providers through the common shared source contract. */
