package com.vueo.app.core.dna

import android.content.Context
import com.vueo.app.core.storage.ProfileStore

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
) {
    private val prefs =
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
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
     * Both the master switch and the recommendation switch must be enabled.
     */
    fun shouldPersonalizeRecommendations(
        profileId: String,
    ): Boolean =
        userDnaEnabled(
            profileId
        ) &&
            personalizedRecommendationsEnabled(
                profileId
            )

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
