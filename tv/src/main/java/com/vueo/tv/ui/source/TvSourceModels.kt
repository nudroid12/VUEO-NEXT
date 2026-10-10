package com.vueo.tv.source

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourceAssessment
import com.vueo.shared.core.player.PlayerSourceDisplay
import com.vueo.shared.core.player.PlayerSourceAudioMatch
import com.vueo.tv.core.TvSourceBundle

internal const val SOURCE_PROVIDER_ALL = "__vueo_all_sources__"

internal data class TvSourcePresentationState(
    val media: MediaItem,
    val episode: EpisodeItem?,
    val bundle: TvSourceBundle?,
    val searching: Boolean,
    val progress: String,
    val rawCount: Int,
    val notice: String?,
    val firstResultMs: Long?,
    val fromCache: Boolean,
    val error: String?,
    val rankedSources: List<StreamSource>,
    val filteredSources: List<StreamSource>,
    val visibleProviders: List<String>,
    val loadingProviders: Set<String>,
    val providerLogos: Map<String, String>,
    val selectedProvider: String,
    val preferredQuality: String?,
    val showEngineDetails: Boolean,
)

/** Clean UI contract between provider output and the TV source card. */
internal data class TvSourceCardModel(
    val providerName: String,
    val serverName: String?,
    val title: String,
    val metadataLabel: String?,
    val logoUrl: String?,
    val detailUrl: String?,
)

internal data class SourceUiMemoryState(
    var selectedProvider: String? = SOURCE_PROVIDER_ALL,
    var focusedSourceKey: String? = null,
    var showEngineDetails: Boolean = false,
)

internal object TvSourceUiMemory {
    private const val MAX_ENTRIES = 20
    private val entries =
        object : LinkedHashMap<String, SourceUiMemoryState>(24, .75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, SourceUiMemoryState>?,
            ): Boolean = size > MAX_ENTRIES
        }

    fun forKey(key: String): SourceUiMemoryState =
        entries.getOrPut(key) { SourceUiMemoryState() }
}

internal fun sourceProviderKey(source: StreamSource): String =
    source.providerName.trim().ifBlank { "Other" }

internal fun sourceProviderDisplayName(provider: String): String =
    provider
        .substringAfterLast(" / ", provider)
        .trim()
        .ifBlank { "Other" }

internal fun StreamSource.toTvSourceCardModel(
    logoUrl: String?,
): TvSourceCardModel {
    val provider = PlayerSourceDisplay.groupTitle(this)
        .lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "Other"
    // Display the provider's text once, without generated media or metadata labels.
    val providerText = sourceCardProviderText(name)
    val serverText = serverName?.let(::sourceCardProviderText)?.takeIf(String::isNotBlank)
    val title = providerText.ifBlank {
        serverText ?: sourceProviderDisplayName(sourceProviderKey(this))
    }

    return TvSourceCardModel(
        providerName = provider,
        serverName = serverText?.takeUnless { it == title },
        title = title,
        metadataLabel = null,
        logoUrl = logoUrl,
        detailUrl = url?.trim()?.takeIf(String::isNotBlank),
    )
}

private fun sourceCardProviderText(value: String): String =
    value
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()

private fun sourceRepositoryDisplayName(source: StreamSource): String? =
    source.providerName
        .takeIf { " / " in it }
        ?.substringBefore(" / ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun sourceServerDisplayName(source: StreamSource): String? =
    source.serverName
        ?.trim()
        ?.takeIf(String::isNotBlank)

internal fun sourceTitleDisplayName(source: StreamSource): String? {
    return com.vueo.shared.core.source.SourceDisplayText.details(
        source.name,
        sourceProviderDisplayName(sourceProviderKey(source)),
        sourceServerDisplayName(source),
    )
}

internal fun sourceMetadataLine(
    source: StreamSource,
    assessment: PlayerSourceAssessment,
): String =
    listOfNotNull(
        sourceServerDisplayName(source)
            ?: sourceRepositoryDisplayName(source),
        when (assessment.audioMatch) {
            PlayerSourceAudioMatch.ORIGINAL -> "Original audio"
            PlayerSourceAudioMatch.MULTI_WITH_ORIGINAL -> "Original in multi audio"
            PlayerSourceAudioMatch.FOREIGN_DUB -> "Dub"
            PlayerSourceAudioMatch.UNKNOWN -> null
        },
        assessment.summary
            .split(" • ")
            .filterNot { it.equals("Unknown", ignoreCase = true) }
            .joinToString(" • ")
            .takeIf(String::isNotBlank),
        source.hdr,
        source.audio,
    )
        .flatMap { value -> value.split(" • ") }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .joinToString(" • ")

internal fun sourceStableKey(source: StreamSource): String =
    listOf(
        sourceProviderKey(source),
        source.url,
        source.infoHash,
        source.fileIndex?.toString(),
        source.providerId,
        source.name,
    ).joinToString(":") { it.orEmpty() }

internal fun formatSourceBytes(bytes: Long): String {
    if (bytes <= 0L) return ""
    val gib = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) "%.1f GB".format(gib)
    else "%.0f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
}

internal fun sourceEpisodeLabel(episode: EpisodeItem?): String? =
    episode?.let {
        buildString {
            append("S")
            append(it.season)
            append(" E")
            append(it.episode)
            if (it.title.isNotBlank()) {
                append("  •  ")
                append(it.title)
            }
        }
    }
