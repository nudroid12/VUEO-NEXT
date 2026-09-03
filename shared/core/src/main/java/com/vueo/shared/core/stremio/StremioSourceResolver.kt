package com.vueo.shared.core.stremio

import com.vueo.shared.core.source.SourceRequest
import com.vueo.shared.core.source.SourceResolveResult
import com.vueo.shared.core.source.SourceResolver

/**
 * Shared adapter from the Stremio addon protocol to VUEO's source contract.
 * Both Mobile and TV will consume this same resolver after integration phases.
 */
class StremioSourceResolver(
    private val client: StremioAddonClient,
) : SourceResolver {
    override val id: String = client.manifest.id
    override val name: String = client.manifest.name

    override suspend fun resolve(request: SourceRequest): SourceResolveResult {
        val sourceAttempt = runCatching {
            client.streams(
                type = request.mediaType,
                videoId = request.videoId,
            )
        }
        val subtitleAttempt = runCatching {
            client.subtitles(
                type = request.mediaType,
                videoId = request.videoId,
            )
        }

        return SourceResolveResult(
            sources = sourceAttempt.getOrDefault(emptyList()),
            subtitles = subtitleAttempt.getOrDefault(emptyList()),
            warnings = buildList {
                sourceAttempt.exceptionOrNull()?.let {
                    add("${client.manifest.name} streams: ${it.message ?: "request failed"}")
                }
                subtitleAttempt.exceptionOrNull()?.let {
                    add("${client.manifest.name} subtitles: ${it.message ?: "request failed"}")
                }
            },
        )
    }

    companion object {
        suspend fun fromManifestUrl(
            manifestUrl: String,
            httpClient: StremioHttpClient = DefaultStremioHttpClient,
        ): StremioSourceResolver = StremioSourceResolver(
            StremioAddonClient.fromManifestUrl(
                manifestUrl = manifestUrl,
                httpClient = httpClient,
            )
        )
    }
}
