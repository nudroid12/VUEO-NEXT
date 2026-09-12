package com.vueo.shared.core.plugin.resolver

import com.vueo.shared.core.media.StreamTransport
import com.vueo.shared.core.media.StreamTransportPolicy
import com.vueo.shared.core.plugin.PluginWebViewResolver
import com.vueo.shared.core.source.SourceCandidate
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject

/**
 * Generic embed fallback backed by the hardened native WebView resolver.
 *
 * Host-specific resolvers can be inserted in the registry before this class
 * later without adding host branching back into PluginRuntime.
 */
internal class WebViewEmbedProviderStreamResolver(
    private val webViewResolver: PluginWebViewResolver,
    private val webViewConcurrency: Semaphore,
) : ProviderStreamResolver {
    override val id: String = "webview-embed"
    override val stage: ProviderResolverStage = ProviderResolverStage.FALLBACK

    override fun canResolve(source: SourceCandidate): Boolean =
        source.transport == StreamTransport.EMBED

    override suspend fun resolve(
        source: SourceCandidate,
    ): List<SourceCandidate> {
        val url = source.url ?: return emptyList()
        val referer = source.headers.valueIgnoreCase("Referer") ?: url
        val userAgent = source.headers.valueIgnoreCase("User-Agent")

        val requestJson = JSONObject()
            .put("url", url)
            .put("referer", referer)
            .put("timeoutMs", AUTO_EMBED_RESOLVE_TIMEOUT_MS)
            .put("finishAfterFirstMs", AUTO_EMBED_FINISH_AFTER_FIRST_MS)
            .put("directLoad", true)
            .put("suppressPopups", true)
            .apply {
                if (!userAgent.isNullOrBlank()) {
                    put("userAgent", userAgent)
                }
            }
            .toString()

        val responseJson = webViewConcurrency.withPermit {
            webViewResolver.resolveJson(requestJson)
        }
        val response = runCatching {
            JSONObject(responseJson)
        }.getOrNull() ?: return emptyList()
        if (response.optString("error").isNotBlank()) {
            return emptyList()
        }

        val streams = response.optJSONArray("streams") ?: return emptyList()
        return (0 until streams.length()).mapNotNull { index ->
            val item = streams.optJSONObject(index) ?: return@mapNotNull null
            val resolvedUrl = item.optString("url")
                .trim()
                .takeIf {
                    it.startsWith("https://", ignoreCase = true)
                }
                ?: return@mapNotNull null
            val streamType = item.optString("type")
                .trim()
                .takeIf { it.isNotBlank() }
            val rawMimeType = item.optString("mimeType")
                .trim()
                .takeIf { it.isNotBlank() }
            val resolvedHeaders = item.optJSONObject("headers").toStringMap()
            val label = item.optString("label")
                .trim()
                .takeIf {
                    it.isNotBlank() && !it.equals("Auto", ignoreCase = true)
                }

            source.copy(
                id = "${source.id}:resolved:$index",
                name = label?.let { "${source.name} • $it" } ?: source.name,
                url = resolvedUrl,
                streamType = streamType,
                mimeType = StreamTransportPolicy.playbackMimeType(
                    url = resolvedUrl,
                    streamType = streamType,
                    mimeType = rawMimeType,
                ),
                quality = source.quality ?: label,
                headers = source.headers + resolvedHeaders,
            )
        }
    }

    private companion object {
        const val AUTO_EMBED_RESOLVE_TIMEOUT_MS = 4_500L
        const val AUTO_EMBED_FINISH_AFTER_FIRST_MS = 650L
    }
}

private fun Map<String, String>.valueIgnoreCase(
    name: String,
): String? = entries
    .firstOrNull { it.key.equals(name, ignoreCase = true) }
    ?.value
    ?.takeIf { it.isNotBlank() }

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()

    val result = linkedMapOf<String, String>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        result[key] = optString(key)
    }
    return result
}
