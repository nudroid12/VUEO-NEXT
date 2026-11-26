package com.vueo.tv.source

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourceDisplay
import com.vueo.shared.core.player.PlayerSourcePolicy
import com.vueo.shared.core.source.ProviderSourceDetails
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
    val stopped: Boolean,
    val sourceOpenFailed: Boolean,
    val rankedSources: List<StreamSource>,
    val filteredSources: List<StreamSource>,
    val visibleProviders: List<String>,
    val loadingProviders: Set<String>,
    val providerLogos: Map<String, String>,
    val selectedProvider: String,
    val preferredQuality: String?,
    val showEngineDetails: Boolean,
    val runningProviders: Set<String> = emptySet(),
    val cancelledProviders: Set<String> = emptySet(),
    val skippedProviders: Set<String> = emptySet(),
)

/** Clean UI contract between provider output and the TV source card. */
internal data class TvSourceCardModel(
    val providerName: String,
    val serverName: String?,
    val title: String,
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
    val title = ProviderSourceDetails.lines(this, PlayerSourcePolicy.assess(this), includeServer = false)
        .joinToString("\n")
    val serverText = ProviderSourceDetails.serverLines(this).joinToString("\n").takeIf(String::isNotBlank)

    return TvSourceCardModel(
        providerName = provider,
        serverName = serverText,
        title = title,
        logoUrl = logoUrl,
        detailUrl = url?.trim()?.takeIf(String::isNotBlank),
    )
}

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
