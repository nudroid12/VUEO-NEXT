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
    val qualityLabel: String?,
    val durationLabel: String?,
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
    runtimeMinutes: Int?,
    originalLanguage: String?,
    preferredQuality: String?,
    logoUrl: String?,
): TvSourceCardModel {
    val provider = sourceProviderDisplayName(sourceProviderKey(this))
        .lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: "Other"
    val title = sourceCardMediaTitle(
        mediaName = mediaName,
        releaseInfo = releaseInfo,
        episode = episode,
    )
    val server = buildList {
        sourceServerDisplayName(this@toTvSourceCardModel)?.let(::add)
        add(name)
    }
        .flatMap(::sourceCardLabelParts)
        .mapNotNull { candidate ->
            cleanSourceCardServerLabel(
                value = candidate,
                providerName = provider,
            )
        }
        .firstOrNull { candidate ->
            !sourceCardLabelMatchesContent(
                value = candidate,
                providerName = provider,
                mediaName = mediaName,
                cardTitle = title,
            )
        }
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

    return TvSourceCardModel(
        providerName = provider,
        serverName = server,
        title = title,
        qualityLabel = quality,
        durationLabel = runtimeMinutes
            ?.takeIf { it > 0 }
            ?.let { "$it minutes" },
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

private fun sourceCardLabelMatchesContent(
    value: String,
    providerName: String,
    mediaName: String,
    cardTitle: String,
): Boolean {
    val candidate = sourceCardComparable(value)
    val provider = sourceCardComparable(providerName)
    val media = sourceCardComparable(mediaName)
    val title = sourceCardComparable(cardTitle)
    return candidate.isBlank() ||
        candidate == provider ||
        candidate == title ||
        candidate == media ||
        (media.length >= 3 && candidate.startsWith(media))
}

private fun sourceCardComparable(value: String): String =
    value
        .lowercase()
        .replace(SOURCE_CARD_NON_ALPHANUMERIC, "")

private val SOURCE_CARD_YEAR = Regex("""\b(?:19|20)\d{2}\b""")
private val SOURCE_CARD_TRAILING_YEAR =
    Regex("""\s*\((?:19|20)\d{2}(?:\s*[–—-]\s*)?\)\s*$""")
private val SOURCE_CARD_NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

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
    val title = source.name.trim()
    if (title.isBlank() ||
        title.startsWith("http://", ignoreCase = true) ||
        title.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }

    val provider = sourceProviderDisplayName(sourceProviderKey(source))
    val server = sourceServerDisplayName(source)
    return title.takeUnless {
        it.equals(provider, ignoreCase = true) ||
            server?.let { serverName ->
                it.equals(serverName, ignoreCase = true)
            } == true
    }
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
