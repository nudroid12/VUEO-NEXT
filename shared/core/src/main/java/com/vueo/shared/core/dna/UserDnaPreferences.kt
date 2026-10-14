package com.vueo.shared.core.dna

import android.content.Context
import com.vueo.shared.core.storage.ProfileStore

/**
 * Per-profile controls for VUEO User DNA.
 *
 * These preferences live in vueo_settings so they are included in the normal
 * VUEO local backup/reset flow and profile-scoped keys are cleaned with the
 * rest of that profile's settings.
 *
 * Turning User DNA off does not delete History, My List or playback data.
 * It only disables use of those signals for DNA-based personalization.
 */
class UserDnaPreferences(
    context: Context,
    prefsName: String = PREFS_NAME,
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                prefsName,
                Context.MODE_PRIVATE,
            )

    fun userDnaEnabled(
        profileId: String,
    ): Boolean =
        prefs.getBoolean(
            key(
                profileId,
                KEY_USER_DNA_ENABLED,
            ),
            DEFAULT_ENABLED,
        )

    fun setUserDnaEnabled(
        profileId: String,
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                key(
                    profileId,
                    KEY_USER_DNA_ENABLED,
                ),
                enabled,
            )
            .apply()
        if (!enabled) com.vueo.shared.core.home.HomeRecommendationFeeds.cancelProfile(profileId)
    }

    fun showDnaMatchEnabled(
        profileId: String,
    ): Boolean =
        prefs.getBoolean(
            key(
                profileId,
                KEY_SHOW_DNA_MATCH,
            ),
            DEFAULT_SHOW_DNA_MATCH,
        )

    fun setShowDnaMatchEnabled(
        profileId: String,
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                key(
                    profileId,
                    KEY_SHOW_DNA_MATCH,
                ),
                enabled,
            )
            .apply()
    }

    fun personalizedRecommendationsEnabled(
        profileId: String,
    ): Boolean =
        prefs.getBoolean(
            key(
                profileId,
                KEY_PERSONALIZED_RECOMMENDATIONS,
            ),
            DEFAULT_PERSONALIZED_RECOMMENDATIONS,
        )

    fun setPersonalizedRecommendationsEnabled(
        profileId: String,
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                key(
                    profileId,
                    KEY_PERSONALIZED_RECOMMENDATIONS,
                ),
                enabled,
            )
            .apply()
    }

    fun forYouEnabled(profileId: String): Boolean =
        prefs.getBoolean(key(profileId, KEY_FOR_YOU), personalizedRecommendationsEnabled(profileId))

    fun setForYouEnabled(profileId: String, enabled: Boolean) {
        prefs.edit().putBoolean(key(profileId, KEY_FOR_YOU), enabled).apply()
        if (!enabled) com.vueo.shared.core.home.HomeRecommendationFeeds.cancelProfile(profileId, forYou = true, because = false)
    }

    fun becauseYouWatchedEnabled(profileId: String): Boolean =
        prefs.getBoolean(key(profileId, KEY_BECAUSE_YOU_WATCHED), personalizedRecommendationsEnabled(profileId))

    fun setBecauseYouWatchedEnabled(profileId: String, enabled: Boolean) {
        prefs.edit().putBoolean(key(profileId, KEY_BECAUSE_YOU_WATCHED), enabled).apply()
        if (!enabled) com.vueo.shared.core.home.HomeRecommendationFeeds.cancelProfile(profileId, forYou = false, because = true)
    }

    fun shouldShowForYou(profileId: String): Boolean = userDnaEnabled(profileId) && forYouEnabled(profileId)
    fun shouldShowBecauseYouWatched(profileId: String): Boolean = userDnaEnabled(profileId) && becauseYouWatchedEnabled(profileId)

    /**
     * Convenience gate for Details.
     * Both the master switch and the DNA Match switch must be enabled.
     */
    fun shouldShowDnaMatch(
        profileId: String,
    ): Boolean =
        userDnaEnabled(
            profileId
        ) &&
            showDnaMatchEnabled(
                profileId
            )

    /**
     * Convenience gate for discovery/recommendation ranking.
     * The DNA master gate and at least one Home row switch must be enabled.
     */
    fun shouldPersonalizeRecommendations(profileId: String): Boolean =
        shouldShowForYou(profileId) || shouldShowBecauseYouWatched(profileId)

    private fun key(
        profileId: String,
        baseKey: String,
    ): String =
        ProfileStore.scopedPreferenceKey(
            profileId = profileId,
            baseKey = baseKey,
        )

    companion object {
        private const val PREFS_NAME =
            "vueo_settings"

        private const val KEY_USER_DNA_ENABLED =
            "user_dna_enabled"

        private const val KEY_SHOW_DNA_MATCH =
            "user_dna_show_match"

        private const val KEY_FOR_YOU = "user_dna_for_you"
        private const val KEY_BECAUSE_YOU_WATCHED = "user_dna_because_you_watched"

        private const val KEY_PERSONALIZED_RECOMMENDATIONS =
            "user_dna_personalized_recommendations"

        private const val DEFAULT_ENABLED =
            true

        private const val DEFAULT_SHOW_DNA_MATCH =
            true

        private const val DEFAULT_PERSONALIZED_RECOMMENDATIONS =
            true
    }
}
