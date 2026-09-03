package com.vueo.app.core.player

import com.vueo.app.core.model.StreamSource

enum class PlayerSourceQuality(
    val label: String,
    val automaticRecoveryEligible: Boolean,
) {
    FULL_HD("1080p", true),
    HD("720p", true),
    AUTO("Auto", true),
    UNKNOWN("Unknown", true),
    ULTRA_HD("4K", true),
    LOW("Below 720p", false),
}

data class PlayerSourceAssessment(
    val quality: PlayerSourceQuality,
    val score: Int,
    val summary: String,
    val audioMatch: PlayerSourceAudioMatch,
)

enum class PlayerSourceAudioMatch(
    val recommendationEligible: Boolean,
) {
    ORIGINAL(true),
    MULTI_WITH_ORIGINAL(true),
    UNKNOWN(true),
    FOREIGN_DUB(false),
}

/**
 * Deterministic source policy. It uses only metadata available for the current
 * title and never learns from viewing history.
 */
object PlayerSourcePolicy {
    fun assess(
        source: StreamSource,
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): PlayerSourceAssessment {
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
            PlayerSourceQuality.FULL_HD -> 420
            PlayerSourceQuality.HD -> 400
            PlayerSourceQuality.AUTO -> 390
            PlayerSourceQuality.UNKNOWN -> 380
            PlayerSourceQuality.ULTRA_HD -> 360
            PlayerSourceQuality.LOW -> 0
        }

        val preferenceBoost = when {
            preferred == null -> 0
            quality.label.lowercase() == preferred -> 100
            preferred == "4k" && quality == PlayerSourceQuality.ULTRA_HD -> 100
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
            PlayerSourceAudioMatch.ORIGINAL -> 260
            PlayerSourceAudioMatch.MULTI_WITH_ORIGINAL -> 220
            PlayerSourceAudioMatch.UNKNOWN -> 0
            PlayerSourceAudioMatch.FOREIGN_DUB -> -600
        }
        val providerBoost = source.rankBoost.coerceIn(-30, 30)
        val score = directBoost + qualityScore + preferenceBoost +
            audioBoost + deliveryBoost + codecBoost + providerBoost

        return PlayerSourceAssessment(
            quality = quality,
            score = score,
            audioMatch = audioMatch,
            summary = buildList {
                add(quality.label)
                when {
                    ".m3u8" in searchable || " hls" in searchable ->
                        add("HLS")

                    ".mp4" in searchable || " mp4" in searchable ->
                        add("MP4")
                }
                when {
                    "h264" in searchable ||
                        "h.264" in searchable ||
                        "avc" in searchable -> add("H.264")

                    "hevc" in searchable || "h265" in searchable ->
                        add("HEVC")

                    "av1" in searchable -> add("AV1")
                }
            }.distinct().joinToString(" • "),
        )
    }

    fun comparator(
        preferredQuality: String? = null,
        originalLanguage: String? = null,
    ): Comparator<StreamSource> =
        compareByDescending<StreamSource> {
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

    fun automaticRecoveryCandidates(
        rankedSources: List<StreamSource>,
        attemptedUrls: Set<String>,
        originalLanguage: String? = null,
    ): List<StreamSource> = rankedSources.filter { source ->
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
        source: StreamSource,
        originalLanguage: String?,
    ): PlayerSourceAudioMatch {
        val original = canonicalLanguageCode(originalLanguage)
            ?: return PlayerSourceAudioMatch.UNKNOWN
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
            source.audio
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in LANGUAGE_ALIASES }
                ?.let(::canonicalLanguageCode)
                ?.let(::add)
        }
        val detectedLanguages =
            explicitLanguages + detectLanguages(source.name)
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
            markedOriginal ->
                PlayerSourceAudioMatch.ORIGINAL

            original in detectedLanguages && multiAudio ->
                PlayerSourceAudioMatch.MULTI_WITH_ORIGINAL

            original in detectedLanguages ->
                PlayerSourceAudioMatch.ORIGINAL

            dubbed ->
                PlayerSourceAudioMatch.FOREIGN_DUB

            detectedLanguages.isNotEmpty() && !multiAudio ->
                PlayerSourceAudioMatch.FOREIGN_DUB

            multiAudio && detectedLanguages.isNotEmpty() ->
                PlayerSourceAudioMatch.FOREIGN_DUB

            else ->
                PlayerSourceAudioMatch.UNKNOWN
        }
    }

    fun detectQuality(
        source: StreamSource,
    ): PlayerSourceQuality {
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
                PlayerSourceQuality.ULTRA_HD

            "1080" in value -> PlayerSourceQuality.FULL_HD
            "720" in value -> PlayerSourceQuality.HD
            "480" in value ||
                "576" in value ||
                "540" in value ||
                "360" in value ||
                "240" in value ||
                "144" in value ||
                explicit == "sd" -> PlayerSourceQuality.LOW

            explicit == "auto" || "adaptive" in value ->
                PlayerSourceQuality.AUTO

            explicit.isBlank() ||
                explicit == "unknown" ||
                explicit == "other" -> PlayerSourceQuality.UNKNOWN

            else -> PlayerSourceQuality.UNKNOWN
        }
    }

    private fun buildSearchableText(
        source: StreamSource,
    ): String = listOf(
        source.quality,
        source.name,
        source.codec,
        source.url,
    ).joinToString(" ")
        .lowercase()

    fun canonicalLanguageCode(value: String?): String? {
        val normalized = value
            ?.trim()
            ?.lowercase()
            ?.replace('_', '-')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val primary = normalized.substringBefore('-')

        return LANGUAGE_ALIASES[normalized]
            ?: LANGUAGE_ALIASES[primary]
            ?: primary.takeIf {
                it.length in 2..3 &&
                    it.all(Char::isLetter)
            }
    }

    private fun detectLanguages(value: String?): Set<String> {
        val normalized = value
            ?.trim()
            ?.lowercase()
            ?.replace('_', '-')
            ?.takeIf { it.isNotBlank() }
            ?: return emptySet()
        val words = normalized
            .replace(Regex("[^a-z-]+"), " ")
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)

        return buildSet {
            words.forEach { word ->
                LANGUAGE_ALIASES[word]?.let(::add)
            }
            LANGUAGE_ALIASES.forEach { (alias, code) ->
                if (
                    ' ' in alias &&
                    Regex("\\b${Regex.escape(alias)}\\b")
                        .containsMatchIn(normalized)
                ) {
                    add(code)
                }
            }
        }
    }

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

    private val LANGUAGE_ALIASES = mapOf(
        "en" to "en",
        "eng" to "en",
        "english" to "en",
        "es" to "es",
        "spa" to "es",
        "spanish" to "es",
        "espanol" to "es",
        "hi" to "hi",
        "hin" to "hi",
        "hindi" to "hi",
        "ta" to "ta",
        "tam" to "ta",
        "tamil" to "ta",
        "te" to "te",
        "tel" to "te",
        "telugu" to "te",
        "ml" to "ml",
        "mal" to "ml",
        "malayalam" to "ml",
        "kn" to "kn",
        "kan" to "kn",
        "kannada" to "kn",
        "bn" to "bn",
        "ben" to "bn",
        "bengali" to "bn",
        "ur" to "ur",
        "urd" to "ur",
        "urdu" to "ur",
        "pa" to "pa",
        "pan" to "pa",
        "punjabi" to "pa",
        "mr" to "mr",
        "mar" to "mr",
        "marathi" to "mr",
        "ar" to "ar",
        "ara" to "ar",
        "arabic" to "ar",
        "fr" to "fr",
        "fra" to "fr",
        "fre" to "fr",
        "french" to "fr",
        "de" to "de",
        "deu" to "de",
        "ger" to "de",
        "german" to "de",
        "it" to "it",
        "ita" to "it",
        "italian" to "it",
        "pt" to "pt",
        "por" to "pt",
        "portuguese" to "pt",
        "ja" to "ja",
        "jpn" to "ja",
        "japanese" to "ja",
        "ko" to "ko",
        "kor" to "ko",
        "korean" to "ko",
        "zh" to "zh",
        "zho" to "zh",
        "chi" to "zh",
        "chinese" to "zh",
        "th" to "th",
        "tha" to "th",
        "thai" to "th",
        "id" to "id",
        "ind" to "id",
        "indonesian" to "id",
        "bahasa indonesia" to "id",
        "ms" to "ms",
        "may" to "ms",
        "msa" to "ms",
        "malay" to "ms",
        "bahasa melayu" to "ms",
        "ru" to "ru",
        "rus" to "ru",
        "russian" to "ru",
    )
}

class PlayerSourceRecoverySession(
    private val automaticRecoveryBudgetMs: Long =
        PLAYER_AUTOMATIC_RECOVERY_BUDGET_MS,
    private val elapsedRealtimeMs: () -> Long = {
        System.nanoTime() / 1_000_000L
    },
) {
    private val attemptedUrls = linkedSetOf<String>()
    private val failedUrls = linkedSetOf<String>()
    private var recoveryStartedAtMs: Long? = null

    fun begin(source: StreamSource) {
        source.url?.let(attemptedUrls::add)
    }

    fun markFailed(source: StreamSource) {
        source.url?.let {
            attemptedUrls += it
            failedUrls += it
        }
    }

    fun allowRetry(source: StreamSource) {
        source.url?.let {
            attemptedUrls -= it
            failedUrls -= it
        }
        recoveryStartedAtMs = null
    }

    fun markReady() {
        recoveryStartedAtMs = null
    }

    fun isAutomaticRecoveryActive(): Boolean =
        recoveryStartedAtMs != null

    fun failedSourceCount(): Int = failedUrls.size

    private fun recoveryBudgetAvailable(nowMs: Long): Boolean {
        val startedAt = recoveryStartedAtMs
            ?: nowMs.also { recoveryStartedAtMs = it }
        return nowMs - startedAt < automaticRecoveryBudgetMs
    }

    fun next(
        rankedSources: List<StreamSource>,
        originalLanguage: String? = null,
    ): StreamSource? {
        if (!recoveryBudgetAvailable(elapsedRealtimeMs())) {
            return null
        }
        val candidate = PlayerSourcePolicy
            .automaticRecoveryCandidates(
                rankedSources = rankedSources,
                attemptedUrls = attemptedUrls,
                originalLanguage = originalLanguage,
            )
            .firstOrNull()
            ?: return null

        candidate.url?.let(attemptedUrls::add)
        return candidate
    }

    fun failedSourceUrls(): Set<String> = failedUrls.toSet()
}

enum class PlayerPlaybackPhase {
    LOADING,
    BUFFERING,
    RECOVERING,
    READY,
    FAILED,
}

const val PLAYER_STARTUP_TIMEOUT_MS = 15_000L
const val PLAYER_RECOVERY_SOURCE_TIMEOUT_MS = 5_000L
const val PLAYER_AUTOMATIC_RECOVERY_BUDGET_MS = 30_000L
const val PLAYER_REBUFFER_TIMEOUT_MS = 25_000L
