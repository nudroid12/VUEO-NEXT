package com.vueo.app.core.storage

import android.content.Context

enum class PlayerOrientation(
    val label: String,
) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    FOLLOW_DEVICE("Follow device"),
}

enum class PreferredQuality(
    val label: String,
    val rankKey: String?,
) {
    AUTO(
        label = "Auto",
        rankKey = null,
    ),
    FOUR_K(
        label = "4K",
        rankKey = "4K",
    ),
    FULL_HD(
        label = "1080p",
        rankKey = "1080p",
    ),
    HD(
        label = "720p",
        rankKey = "720p",
    ),
}

enum class PlayerVideoFit(
    val label: String,
) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
}

enum class SubtitleLanguage(
    val label: String,
    val languageCode: String?,
) {
    AUTO("Auto", null),
    ENGLISH("English", "en"),
    MALAY("Malay", "ms"),
    INDONESIAN("Indonesian", "id"),
    CHINESE("Chinese", "zh"),
    TAMIL("Tamil", "ta"),
    HINDI("Hindi", "hi"),
    ARABIC("Arabic", "ar"),
    JAPANESE("Japanese", "ja"),
    KOREAN("Korean", "ko"),
}

enum class SubtitleSize(
    val label: String,
) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large"),
}

enum class AppAccent(
    val label: String,
    val argb: Long,
) {
    WHITE(
        label = "White",
        argb = 0xFFF2F3F5,
    ),
    LIME(
        label = "Lime Green",
        argb = 0xFF8CE66A,
    ),
    OCEAN(
        label = "Ocean",
        argb = 0xFF63A6FF,
    ),
    VIOLET(
        label = "Violet",
        argb = 0xFFA78BFA,
    ),
    AMBER(
        label = "Amber",
        argb = 0xFFFFC857,
    ),
    CORAL(
        label = "Coral",
        argb = 0xFFFF7A7A,
    ),
}

class SettingsStore(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            )

    private val profileStore =
        ProfileStore(appContext)

    private fun profileKey(
        key: String,
    ): String =
        ProfileStore.scopedPreferenceKey(
            profileStore.activeProfileId(),
            key,
        )

    fun resumePlaybackEnabled(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_RESUME_PLAYBACK),
            true,
        )

    fun setResumePlaybackEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_RESUME_PLAYBACK),
                enabled,
            )
            .apply()
    }

    fun preferredQuality(): PreferredQuality =
        enumValue(
            key = profileKey(KEY_PREFERRED_QUALITY),
            default = PreferredQuality.AUTO,
        )

    fun playerOrientation(): PlayerOrientation =
        enumValue(
            key = profileKey(KEY_PLAYER_ORIENTATION),
            default = PlayerOrientation.AUTO,
        )

    fun playerPlaybackSpeed(): Float =
        prefs.getFloat(
            profileKey(KEY_PLAYER_PLAYBACK_SPEED),
            1f,
        ).coerceIn(0.5f, 2f)

    fun setPlayerPlaybackSpeed(
        speed: Float,
    ) {
        prefs.edit()
            .putFloat(
                profileKey(KEY_PLAYER_PLAYBACK_SPEED),
                speed.coerceIn(0.5f, 2f),
            )
            .apply()
    }

    fun playerVideoFit(): PlayerVideoFit =
        enumValue(
            key = profileKey(KEY_PLAYER_VIDEO_FIT),
            default = PlayerVideoFit.FIT,
        )

    fun setPlayerVideoFit(
        value: PlayerVideoFit,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_PLAYER_VIDEO_FIT),
                value.name,
            )
            .apply()
    }

    fun setPlayerOrientation(
        value: PlayerOrientation,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_PLAYER_ORIENTATION),
                value.name,
            )
            .apply()
    }

    fun autoSourceRecoveryEnabled(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_AUTO_SOURCE_RECOVERY),
            true,
        )

    fun setAutoSourceRecoveryEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_AUTO_SOURCE_RECOVERY),
                enabled,
            )
            .apply()
    }

    fun autoPlayNextEpisodeEnabled(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_AUTO_PLAY_NEXT_EPISODE),
            true,
        )

    fun setAutoPlayNextEpisodeEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_AUTO_PLAY_NEXT_EPISODE),
                enabled,
            )
            .apply()
    }

    fun skipSegmentsEnabled(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_SKIP_SEGMENTS),
            true,
        )

    fun setSkipSegmentsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_SKIP_SEGMENTS),
                enabled,
            )
            .apply()
    }

    fun setPreferredQuality(
        value: PreferredQuality,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_PREFERRED_QUALITY),
                value.name,
            )
            .apply()
    }

    fun showSourceTechnicalDetails(): Boolean =
        prefs.getBoolean(
            KEY_SOURCE_TECHNICAL_DETAILS,
            true,
        )

    fun setShowSourceTechnicalDetails(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_SOURCE_TECHNICAL_DETAILS,
                enabled,
            )
            .apply()
    }

    fun preferredSubtitleLanguage(): SubtitleLanguage =
        enumValue(
            key = profileKey(KEY_SUBTITLE_LANGUAGE),
            default = SubtitleLanguage.ENGLISH,
        )

    fun setPreferredSubtitleLanguage(
        value: SubtitleLanguage,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_SUBTITLE_LANGUAGE),
                value.name,
            )
            .apply()
    }

    fun secondarySubtitleLanguage(): SubtitleLanguage =
        enumValue(
            key = profileKey(KEY_SECONDARY_SUBTITLE_LANGUAGE),
            default = SubtitleLanguage.AUTO,
        )

    fun setSecondarySubtitleLanguage(
        value: SubtitleLanguage,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_SECONDARY_SUBTITLE_LANGUAGE),
                value.name,
            )
            .apply()
    }

    fun subtitlesOnByDefault(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_SUBTITLES_ON_BY_DEFAULT),
            true,
        )

    fun setSubtitlesOnByDefault(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_SUBTITLES_ON_BY_DEFAULT),
                enabled,
            )
            .apply()
    }

    fun autoSelectPreferredSubtitle(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_AUTO_SELECT_SUBTITLE),
            true,
        )

    fun setAutoSelectPreferredSubtitle(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_AUTO_SELECT_SUBTITLE),
                enabled,
            )
            .apply()
    }

    fun embeddedSubtitlePriority(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_EMBEDDED_SUBTITLE_PRIORITY),
            true,
        )

    fun setEmbeddedSubtitlePriority(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_EMBEDDED_SUBTITLE_PRIORITY),
                enabled,
            )
            .apply()
    }

    fun subtitleSize(): SubtitleSize =
        enumValue(
            key = profileKey(KEY_SUBTITLE_SIZE),
            default = SubtitleSize.MEDIUM,
        )

    fun setSubtitleSize(
        value: SubtitleSize,
    ) {
        val fontSizeSp = when (value) {
            SubtitleSize.SMALL -> 16
            SubtitleSize.MEDIUM -> 20
            SubtitleSize.LARGE -> 24
        }
        prefs.edit()
            .putString(
                profileKey(KEY_SUBTITLE_SIZE),
                value.name,
            )
            .putInt(
                profileKey(KEY_SUBTITLE_FONT_SIZE_SP),
                fontSizeSp,
            )
            .apply()
    }

    fun subtitleFontSizeSp(): Int {
        val key = profileKey(KEY_SUBTITLE_FONT_SIZE_SP)
        if (prefs.contains(key)) {
            return prefs.getInt(key, 20).coerceIn(12, 40)
        }

        return when (subtitleSize()) {
            SubtitleSize.SMALL -> 16
            SubtitleSize.MEDIUM -> 20
            SubtitleSize.LARGE -> 24
        }
    }

    fun setSubtitleFontSizeSp(value: Int) {
        prefs.edit()
            .putInt(
                profileKey(KEY_SUBTITLE_FONT_SIZE_SP),
                value.coerceIn(12, 40),
            )
            .apply()
    }

    fun subtitleBold(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_SUBTITLE_BOLD),
            false,
        )

    fun setSubtitleBold(enabled: Boolean) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_SUBTITLE_BOLD),
                enabled,
            )
            .apply()
    }

    fun subtitleTextColor(): Int =
        prefs.getInt(
            profileKey(KEY_SUBTITLE_TEXT_COLOR),
            0xFFFFFFFF.toInt(),
        )

    fun setSubtitleTextColor(value: Int) {
        prefs.edit()
            .putInt(
                profileKey(KEY_SUBTITLE_TEXT_COLOR),
                value,
            )
            .apply()
    }

    fun subtitleOutlineEnabled(): Boolean =
        prefs.getBoolean(
            profileKey(KEY_SUBTITLE_OUTLINE_ENABLED),
            true,
        )

    fun setSubtitleOutlineEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(
                profileKey(KEY_SUBTITLE_OUTLINE_ENABLED),
                enabled,
            )
            .apply()
    }

    fun subtitleOutlineColor(): Int =
        prefs.getInt(
            profileKey(KEY_SUBTITLE_OUTLINE_COLOR),
            0xFF000000.toInt(),
        )

    fun setSubtitleOutlineColor(value: Int) {
        prefs.edit()
            .putInt(
                profileKey(KEY_SUBTITLE_OUTLINE_COLOR),
                value,
            )
            .apply()
    }

    fun subtitleBottomPaddingPercent(): Int =
        prefs.getInt(
            profileKey(KEY_SUBTITLE_BOTTOM_PADDING_PERCENT),
            22,
        ).coerceIn(5, 40)

    fun setSubtitleBottomPaddingPercent(value: Int) {
        prefs.edit()
            .putInt(
                profileKey(KEY_SUBTITLE_BOTTOM_PADDING_PERCENT),
                value.coerceIn(5, 40),
            )
            .apply()
    }

    fun subtitleSelection(contentId: String): String? =
        contentId
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { normalizedId ->
                prefs.getString(
                    profileKey(
                        "$KEY_SUBTITLE_SELECTION|$normalizedId"
                    ),
                    null,
                )
            }

    fun setSubtitleSelection(
        contentId: String,
        selectionId: String,
    ) {
        val normalizedId =
            contentId.trim().takeIf { it.isNotBlank() }
                ?: return
        prefs.edit()
            .putString(
                profileKey(
                    "$KEY_SUBTITLE_SELECTION|$normalizedId"
                ),
                selectionId,
            )
            .apply()
    }

    fun audioSelection(contentId: String): String? =
        contentId
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { normalizedId ->
                prefs.getString(
                    profileKey(
                        "$KEY_AUDIO_SELECTION|$normalizedId"
                    ),
                    null,
                )
            }

    fun setAudioSelection(
        contentId: String,
        selectionId: String,
    ) {
        val normalizedId =
            contentId.trim().takeIf { it.isNotBlank() }
                ?: return
        prefs.edit()
            .putString(
                profileKey(
                    "$KEY_AUDIO_SELECTION|$normalizedId"
                ),
                selectionId,
            )
            .apply()
    }

    fun lastSubtitleSelection(): String? =
        prefs.getString(
            profileKey(KEY_LAST_SUBTITLE_SELECTION),
            null,
        )

    fun setLastSubtitleSelection(
        selection: String,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_LAST_SUBTITLE_SELECTION),
                selection,
            )
            .apply()
    }

    fun lastAudioSelection(): String? =
        prefs.getString(
            profileKey(KEY_LAST_AUDIO_SELECTION),
            null,
        )

    fun setLastAudioSelection(
        selection: String,
    ) {
        prefs.edit()
            .putString(
                profileKey(KEY_LAST_AUDIO_SELECTION),
                selection,
            )
            .apply()
    }

    fun tmdbMetadataEnrichmentEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_METADATA,
            true,
        )

    fun setTmdbMetadataEnrichmentEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_METADATA,
                enabled,
            )
            .apply()
    }

    fun tmdbRecommendationsEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_RECOMMENDATIONS,
            true,
        )

    fun setTmdbRecommendationsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_RECOMMENDATIONS,
                enabled,
            )
            .apply()
    }

    fun tmdbSimilarTitlesEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_SIMILAR,
            true,
        )

    fun setTmdbSimilarTitlesEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_SIMILAR,
                enabled,
            )
            .apply()
    }

    fun tmdbArtworkEnrichmentEnabled(): Boolean =
        prefs.getBoolean(
            KEY_TMDB_ARTWORK,
            true,
        )

    fun setTmdbArtworkEnrichmentEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_TMDB_ARTWORK,
                enabled,
            )
            .apply()
    }

    fun mdblistApiKey(): String =
        prefs.getString(
            KEY_MDBLIST_API_KEY,
            "",
        ).orEmpty().trim()

    fun setMdblistApiKey(
        apiKey: String,
    ) {
        prefs.edit()
            .putString(
                KEY_MDBLIST_API_KEY,
                apiKey.trim(),
            )
            .apply()
    }

    fun mdblistRatingsEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_RATINGS,
            true,
        )

    fun setMdblistRatingsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_RATINGS,
                enabled,
            )
            .apply()
    }

    fun mdblistImdbEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_IMDB,
            true,
        )

    fun setMdblistImdbEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_IMDB,
                enabled,
            )
            .apply()
    }

    fun mdblistRottenTomatoesEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_RT,
            true,
        )

    fun setMdblistRottenTomatoesEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_RT,
                enabled,
            )
            .apply()
    }

    fun mdblistMetacriticEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_METACRITIC,
            true,
        )

    fun setMdblistMetacriticEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_METACRITIC,
                enabled,
            )
            .apply()
    }

    fun mdblistTmdbRatingEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_TMDB,
            true,
        )

    fun setMdblistTmdbRatingEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_TMDB,
                enabled,
            )
            .apply()
    }

    fun mdblistTraktEnabled(): Boolean =
        prefs.getBoolean(
            KEY_MDBLIST_TRAKT,
            true,
        )

    fun setMdblistTraktEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_MDBLIST_TRAKT,
                enabled,
            )
            .apply()
    }

    fun geminiApiKey(): String =
        prefs.getString(
            KEY_GEMINI_API_KEY,
            "",
        ).orEmpty().trim()

    fun setGeminiApiKey(
        apiKey: String,
    ) {
        prefs.edit()
            .putString(
                KEY_GEMINI_API_KEY,
                apiKey.trim(),
            )
            .apply()
    }

    fun geminiInsightsEnabled(): Boolean =
        prefs.getBoolean(
            KEY_GEMINI_INSIGHTS,
            true,
        )

    fun setGeminiInsightsEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_GEMINI_INSIGHTS,
                enabled,
            )
            .apply()
    }

    fun appAccent(): AppAccent =
        enumValue(
            key = KEY_APP_ACCENT,
            default = AppAccent.WHITE,
        )

    fun setAppAccent(
        value: AppAccent,
    ) {
        prefs.edit()
            .putString(
                KEY_APP_ACCENT,
                value.name,
            )
            .apply()
    }

    fun automaticUpdateChecksEnabled(): Boolean =
        prefs.getBoolean(
            KEY_AUTO_UPDATE_CHECKS,
            true,
        )

    fun setAutomaticUpdateChecksEnabled(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_AUTO_UPDATE_CHECKS,
                enabled,
            )
            .apply()
    }

    fun includeCredentialsInBackup(): Boolean =
        prefs.getBoolean(
            KEY_INCLUDE_CREDENTIALS_IN_BACKUP,
            false,
        )

    fun setIncludeCredentialsInBackup(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_INCLUDE_CREDENTIALS_IN_BACKUP,
                enabled,
            )
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValue(
        key: String,
        default: T,
    ): T {
        val stored =
            prefs.getString(
                key,
                default.name,
            )

        return runCatching {
            enumValueOf<T>(
                stored ?: default.name
            )
        }.getOrDefault(default)
    }

    companion object {
        private const val PREFS_NAME =
            "vueo_settings"

        private const val KEY_RESUME_PLAYBACK =
            "resume_playback"

        private const val KEY_PREFERRED_QUALITY =
            "preferred_quality"

        private const val KEY_PLAYER_ORIENTATION =
            "player_orientation"

        private const val KEY_PLAYER_PLAYBACK_SPEED =
            "player_playback_speed"

        private const val KEY_PLAYER_VIDEO_FIT =
            "player_video_fit"

        private const val KEY_AUTO_SOURCE_RECOVERY =
            "auto_source_recovery"

        private const val KEY_AUTO_PLAY_NEXT_EPISODE =
            "auto_play_next_episode"

        private const val KEY_SKIP_SEGMENTS =
            "skip_segments"

        private const val KEY_SOURCE_TECHNICAL_DETAILS =
            "source_technical_details"

        private const val KEY_SUBTITLE_LANGUAGE =
            "subtitle_language"

        private const val KEY_SECONDARY_SUBTITLE_LANGUAGE =
            "secondary_subtitle_language"

        private const val KEY_SUBTITLES_ON_BY_DEFAULT =
            "subtitles_on_by_default"

        private const val KEY_AUTO_SELECT_SUBTITLE =
            "auto_select_preferred_subtitle"

        private const val KEY_EMBEDDED_SUBTITLE_PRIORITY =
            "embedded_subtitle_priority"

        private const val KEY_SUBTITLE_SIZE =
            "subtitle_size"

        private const val KEY_SUBTITLE_FONT_SIZE_SP =
            "subtitle_font_size_sp"

        private const val KEY_SUBTITLE_BOLD =
            "subtitle_bold"

        private const val KEY_SUBTITLE_TEXT_COLOR =
            "subtitle_text_color"

        private const val KEY_SUBTITLE_OUTLINE_ENABLED =
            "subtitle_outline_enabled"

        private const val KEY_SUBTITLE_OUTLINE_COLOR =
            "subtitle_outline_color"

        private const val KEY_SUBTITLE_BOTTOM_PADDING_PERCENT =
            "subtitle_bottom_padding_percent"

        private const val KEY_SUBTITLE_SELECTION =
            "subtitle_selection"

        private const val KEY_AUDIO_SELECTION =
            "audio_selection"

        private const val KEY_LAST_SUBTITLE_SELECTION =
            "last_subtitle_selection"

        private const val KEY_LAST_AUDIO_SELECTION =
            "last_audio_selection"

        private const val KEY_TMDB_METADATA =
            "tmdb_metadata_enrichment"

        private const val KEY_TMDB_RECOMMENDATIONS =
            "tmdb_recommendations"

        private const val KEY_TMDB_SIMILAR =
            "tmdb_similar_titles"

        private const val KEY_TMDB_ARTWORK =
            "tmdb_artwork_enrichment"

        private const val KEY_MDBLIST_API_KEY =
            "mdblist_api_key"

        private const val KEY_MDBLIST_RATINGS =
            "mdblist_ratings"

        private const val KEY_MDBLIST_IMDB =
            "mdblist_imdb"

        private const val KEY_MDBLIST_RT =
            "mdblist_rotten_tomatoes"

        private const val KEY_MDBLIST_METACRITIC =
            "mdblist_metacritic"

        private const val KEY_MDBLIST_TMDB =
            "mdblist_tmdb"

        private const val KEY_MDBLIST_TRAKT =
            "mdblist_trakt"

        private const val KEY_GEMINI_API_KEY =
            "gemini_api_key"

        private const val KEY_GEMINI_INSIGHTS =
            "gemini_ai_insights"

        private const val KEY_APP_ACCENT =
            "app_accent"

        private const val KEY_AUTO_UPDATE_CHECKS =
            "automatic_update_checks"

        private const val KEY_INCLUDE_CREDENTIALS_IN_BACKUP =
            "include_credentials_in_backup"
    }
}
