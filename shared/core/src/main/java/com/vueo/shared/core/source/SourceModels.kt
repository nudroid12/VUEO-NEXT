package com.vueo.shared.core.source

data class SourceRequest(
    val mediaType: String,
    val videoId: String,
    val title: String,
    val originalLanguage: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

data class SourceCandidate(
    val id: String,
    val name: String,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIndex: Int? = null,
    val quality: String? = null,
    val codec: String? = null,
    val hdr: String? = null,
    val audio: String? = null,
    val language: String? = null,
    val sizeBytes: Long? = null,
    val headers: Map<String, String> = emptyMap(),
    val rankBoost: Int = 0,
    val providerId: String,
    val providerName: String,
) {
    /**
     * Keep the same security baseline as current VUEO Mobile: only HTTPS
     * direct URLs are considered immediately playable by the shared core.
     */
    val isDirectPlayable: Boolean
        get() = url?.startsWith("https://") == true

    val isTorrent: Boolean
        get() = !infoHash.isNullOrBlank()
}

data class SubtitleCandidate(
    val id: String,
    val language: String,
    val url: String,
    val providerId: String,
    val providerName: String,
    val name: String? = null,
)

enum class SourceQuality(
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

enum class SourceAudioMatch(
    val recommendationEligible: Boolean,
) {
    ORIGINAL(true),
    MULTI_WITH_ORIGINAL(true),
    UNKNOWN(true),
    FOREIGN_DUB(false),
}

data class SourceAssessment(
    val quality: SourceQuality,
    val score: Int,
    val summary: String,
    val audioMatch: SourceAudioMatch,
)
