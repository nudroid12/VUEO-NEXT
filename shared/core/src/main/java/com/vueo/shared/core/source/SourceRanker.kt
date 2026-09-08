package com.vueo.shared.core.source

import com.vueo.shared.core.language.LanguagePolicy

/**
 * Deterministic source ranking extracted from the current VUEO Mobile
 * PlayerSourcePolicy. It uses current source metadata only and does not learn
 * from viewing history.
 *
 * Keep behavioural changes out of this class during migration. Mobile parity
 * is the baseline; tuning can happen only after both clients consume this core.
 */
object SourceRanker : SourceRankingPolicy {
    override fun assess(
        source: SourceCandidate,
        preferredQuality: String?,
        originalLanguage: String?,
    ): SourceAssessment {
        val quality = detectQuality(source)
        val audioMatch = detectAudioMatch(
            source = source,
            originalLanguage = originalLanguage,
        )
        val searchable = buildSearchableText(source)
        val preferred = preferredQuality
            ?.trim()
            ?.lowercase()

        val qualityScore = when (quality) {
            SourceQuality.FULL_HD -> 420
            SourceQuality.HD -> 400
            SourceQuality.AUTO -> 390
            SourceQuality.UNKNOWN -> 380
            SourceQuality.ULTRA_HD -> 360
            SourceQuality.LOW -> 0
        }

        val preferenceBoost = when {
            preferred == null -> 0
            quality.label.lowercase() == preferred -> 100
            preferred == "4k" && quality == SourceQuality.ULTRA_HD -> 100
            else -> 0
        }

        val deliveryBoost = when {
            ".m3u8" in searchable || " hls" in searchable -> 35
            ".mp4" in searchable || " mp4" in searchable -> 25
            else -> 0
        }

        val codecBoost = when {
            "h264" in searchable ||
                "h.264" in searchable ||
                "avc" in searchable -> 20

            "av1" in searchable -> -20
            else -> 0
        }

        val directBoost = if (source.isDirectPlayable) 1_000 else -1_000
        val audioBoost = when (audioMatch) {
            SourceAudioMatch.ORIGINAL -> 260
            SourceAudioMatch.MULTI_WITH_ORIGINAL -> 220
            SourceAudioMatch.UNKNOWN -> 0
            SourceAudioMatch.FOREIGN_DUB -> -600
        }
        val providerBoost = source.rankBoost.coerceIn(-30, 30)
        val transportBoost = when {
            source.url?.startsWith("https://", ignoreCase = true) == true -> 30
            source.url?.startsWith("http://", ignoreCase = true) == true -> 10
            else -> 0
        }
        val suspiciousPayloadPenalty = when {
            ".html" in searchable || ".htm" in searchable -> -700
            ".json" in searchable || ".xml" in searchable -> -500
            else -> 0
        }
        val score = directBoost + qualityScore + preferenceBoost +
            audioBoost + deliveryBoost + codecBoost + providerBoost +
            transportBoost + suspiciousPayloadPenalty

        return SourceAssessment(
            quality = quality,
            score = score,
            audioMatch = audioMatch,
            summary = buildList {
                add(quality.label)
                when {
                    ".m3u8" in searchable || " hls" in searchable -> add("HLS")
                    ".mp4" in searchable || " mp4" in searchable -> add("MP4")
                }
                when {
                    "h264" in searchable ||
                        "h.264" in searchable ||
                        "avc" in searchable -> add("H.264")

                    "hevc" in searchable || "h265" in searchable -> add("HEVC")
                    "av1" in searchable -> add("AV1")
                }
            }.distinct().joinToString(" • "),
        )
    }

    override fun comparator(
        preferredQuality: String?,
        originalLanguage: String?,
    ): Comparator<SourceCandidate> =
        compareByDescending<SourceCandidate> {
            assess(
                source = it,
                preferredQuality = preferredQuality,
                originalLanguage = originalLanguage,
            ).score
        }.thenBy {
            it.sizeBytes ?: Long.MAX_VALUE
        }.thenBy {
            it.providerName.lowercase()
        }

    override fun rank(
        sources: List<SourceCandidate>,
        preferredQuality: String?,
        originalLanguage: String?,
    ): List<SourceCandidate> = sources.sortedWith(
        comparator(
            preferredQuality = preferredQuality,
            originalLanguage = originalLanguage,
        ),
    )

    override fun automaticRecoveryCandidates(
        rankedSources: List<SourceCandidate>,
        attemptedUrls: Set<String>,
        originalLanguage: String?,
    ): List<SourceCandidate> = rankedSources.filter { source ->
        val url = source.url
        val assessment = assess(
            source = source,
            originalLanguage = originalLanguage,
        )
        url != null &&
            url !in attemptedUrls &&
            assessment.quality.automaticRecoveryEligible &&
            assessment.audioMatch.recommendationEligible
    }

    fun detectAudioMatch(
        source: SourceCandidate,
        originalLanguage: String?,
    ): SourceAudioMatch {
        val original = canonicalLanguageCode(originalLanguage)
            ?: return SourceAudioMatch.UNKNOWN
        val explicitText = listOfNotNull(
            source.language,
            source.audio,
        ).joinToString(" ")
        val searchable = listOf(
            explicitText,
            source.name,
        ).joinToString(" ")
            .lowercase()
        val explicitLanguages = buildSet {
            addAll(detectLanguages(explicitText))
            canonicalLanguageCode(source.language)?.let(::add)
            LanguagePolicy.knownCode(source.audio)?.let(::add)
        }
        val detectedLanguages = explicitLanguages + detectLanguages(source.name)
        val multiAudio = AUDIO_MULTI_MARKERS.any {
            it.containsMatchIn(searchable)
        }
        val dubbed = AUDIO_DUB_MARKERS.any {
            it.containsMatchIn(searchable)
        }
        val markedOriginal = AUDIO_ORIGINAL_MARKERS.any {
            it.containsMatchIn(searchable)
        }

        return when {
            markedOriginal -> SourceAudioMatch.ORIGINAL
            original in detectedLanguages && multiAudio ->
                SourceAudioMatch.MULTI_WITH_ORIGINAL
            original in detectedLanguages -> SourceAudioMatch.ORIGINAL
            dubbed -> SourceAudioMatch.FOREIGN_DUB
            detectedLanguages.isNotEmpty() && !multiAudio ->
                SourceAudioMatch.FOREIGN_DUB
            multiAudio && detectedLanguages.isNotEmpty() ->
                SourceAudioMatch.FOREIGN_DUB
            else -> SourceAudioMatch.UNKNOWN
        }
    }

    fun detectQuality(source: SourceCandidate): SourceQuality {
        val explicit = source.quality
            .orEmpty()
            .trim()
            .lowercase()
        val value = listOf(
            source.quality,
            source.name,
        ).joinToString(" ")
            .lowercase()

        return when {
            "2160" in value || "4k" in value || "uhd" in value ->
                SourceQuality.ULTRA_HD
            "1080" in value -> SourceQuality.FULL_HD
            "720" in value -> SourceQuality.HD
            "480" in value ||
                "576" in value ||
                "540" in value ||
                "360" in value ||
                "240" in value ||
                "144" in value ||
                explicit == "sd" -> SourceQuality.LOW
            explicit == "auto" || "adaptive" in value -> SourceQuality.AUTO
            explicit.isBlank() ||
                explicit == "unknown" ||
                explicit == "other" -> SourceQuality.UNKNOWN
            else -> SourceQuality.UNKNOWN
        }
    }

    fun canonicalLanguageCode(value: String?): String? =
        LanguagePolicy.canonicalCode(value)

    private fun buildSearchableText(source: SourceCandidate): String = listOf(
        source.quality,
        source.name,
        source.codec,
        source.url,
    ).joinToString(" ")
        .lowercase()

    private fun detectLanguages(value: String?): Set<String> =
        LanguagePolicy.detectCodes(value)

    private val AUDIO_MULTI_MARKERS = listOf(
        Regex("\\bdual[ ._-]*audio\\b"),
        Regex("\\bmulti[ ._-]*audio\\b"),
        Regex("\\bmultilingual\\b"),
    )

    private val AUDIO_DUB_MARKERS = listOf(
        Regex("\\bdub(?:bed)?\\b"),
        Regex("\\bdublado\\b"),
        Regex("\\blatino\\b"),
    )

    private val AUDIO_ORIGINAL_MARKERS = listOf(
        Regex("\\boriginal[ ._-]*(?:audio|language)\\b"),
        Regex("\\borg[ ._-]*audio\\b"),
    )

}
