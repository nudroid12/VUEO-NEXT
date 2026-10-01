package com.vueo.tv.source

import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.player.PlayerSourceAssessment
import com.vueo.shared.core.player.PlayerSourceAudioMatch
import com.vueo.shared.core.player.PlayerSourcePolicy
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
    mediaName: String,
    releaseInfo: String?,
    episode: EpisodeItem?,
    originalLanguage: String?,
    preferredQuality: String?,
    logoUrl: String?,
): TvSourceCardModel {
    val provider = sourceProviderDisplayName(sourceProviderKey(this))
        .lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "Other"
    // Keep provider text instead of replacing it with the selected media title.
    val title = sourceTitleDisplayName(this)
        ?.let { sourceCardLabelParts(it).joinToString("\n") }
        ?.takeIf(String::isNotBlank)
        ?: sourceCardMediaTitle(mediaName, releaseInfo, episode)
    val server = sourceServerDisplayName(this)
        ?.let { cleanSourceCardServerLabel(it, provider) }
        ?.takeUnless { it.equals(title, ignoreCase = true) }
    val assessment = PlayerSourcePolicy.assess(
        source = this,
        preferredQuality = preferredQuality,
        originalLanguage = originalLanguage,
    )
    val quality = this.quality
        ?.trim()
        ?.takeIf {
            it.isNotBlank() &&
                !it.equals("Unknown", ignoreCase = true) &&
                !it.equals("Other", ignoreCase = true)
        }
        ?: assessment.quality.label.takeUnless {
            it.equals("Unknown", ignoreCase = true)
        }
    val metadata = listOfNotNull(
        quality,
        codec,
        hdr,
        audio,
        language,
        sizeBytes?.takeIf { it > 0 }?.let { bytes ->
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1.0) java.lang.String.format(java.util.Locale.US, "%.2f GB", gb)
            else java.lang.String.format(java.util.Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
        },
    )
        .map(String::trim)
        .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .filterNot { title.contains(it, ignoreCase = true) || server?.contains(it, ignoreCase = true) == true }
        .joinToString(" • ")
        .takeIf(String::isNotBlank)

    return TvSourceCardModel(
        providerName = provider,
        serverName = server,
        title = title,
        metadataLabel = metadata,
        logoUrl = logoUrl,
        detailUrl = url?.trim()?.takeIf(String::isNotBlank),
    )
}

private fun sourceCardMediaTitle(
    mediaName: String,
    releaseInfo: String?,
    episode: EpisodeItem?,
): String {
    val baseName = mediaName.trim().ifBlank { "Unknown title" }
    if (episode != null) {
        val cleanName = baseName
            .replace(SOURCE_CARD_TRAILING_YEAR, "")
            .trim()
            .ifBlank { baseName }
        return buildString {
            append(cleanName)
            append(" S")
            append(episode.season.toString().padStart(2, '0'))
            append("E")
            append(episode.episode.toString().padStart(2, '0'))
        }
    }

    val year = SOURCE_CARD_YEAR.find(releaseInfo.orEmpty())?.value
    return if (year != null && SOURCE_CARD_YEAR.find(baseName) == null) {
        "$baseName ($year)"
    } else {
        baseName
    }
}

private fun sourceCardLabelParts(value: String): List<String> =
    value
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

private fun cleanSourceCardServerLabel(
    value: String,
    providerName: String,
): String? {
    val candidate = value.trim()
    if (
        candidate.isBlank() ||
        candidate.startsWith("http://", ignoreCase = true) ||
        candidate.startsWith("https://", ignoreCase = true) ||
        candidate.equals("Unknown", ignoreCase = true)
    ) {
        return null
    }

    val withoutProvider = if (candidate.startsWith(providerName, ignoreCase = true)) {
        candidate
            .drop(providerName.length)
            .trimStart { character ->
                character.isWhitespace() ||
                    character == '-' ||
                    character == '–' ||
                    character == '—' ||
                    character == '|' ||
                    character == '•' ||
                    character == ':'
            }
    } else {
        candidate
    }
    return withoutProvider.trim().takeIf(String::isNotBlank)
}

private val SOURCE_CARD_YEAR = Regex("""\b(?:19|20)\d{2}\b""")
private val SOURCE_CARD_TRAILING_YEAR =
    Regex("""\s*\((?:19|20)\d{2}(?:\s*[–—-]\s*)?\)\s*$""")

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
