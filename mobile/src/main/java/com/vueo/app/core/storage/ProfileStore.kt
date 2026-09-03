package com.vueo.app.core.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VueoProfile(
    val id: String,
    val name: String,
    val avatar: String,
    val isKids: Boolean,
    val createdAtEpochMs: Long,
)

class ProfileStore(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    init {
        ensureDefaultProfile()
    }

    @Synchronized
    fun ensureDefaultProfile() {
        val current = readProfiles()

        if (current.isNotEmpty()) {
            if (
                prefs.getString(
                    KEY_ACTIVE_PROFILE_ID,
                    null,
                ).isNullOrBlank()
            ) {
                prefs.edit()
                    .putString(
                        KEY_ACTIVE_PROFILE_ID,
                        current.first().id,
                    )
                    .apply()
            }
            return
        }

        val profile =
            VueoProfile(
                id = DEFAULT_PROFILE_ID,
                name = DEFAULT_PROFILE_NAME,
                avatar = DEFAULT_AVATAR,
                isKids = false,
                createdAtEpochMs =
                    System.currentTimeMillis(),
            )

        writeProfiles(
            listOf(profile)
        )

        prefs.edit()
            .putString(
                KEY_ACTIVE_PROFILE_ID,
                profile.id,
            )
            .putBoolean(
                KEY_ASK_ON_STARTUP,
                false,
            )
            .apply()
    }

    @Synchronized
    fun profiles(): List<VueoProfile> {
        ensureDefaultProfile()
        return readProfiles()
    }

    @Synchronized
    fun activeProfileId(): String {
        ensureDefaultProfile()

        val profiles = readProfiles()
        val stored =
            prefs.getString(
                KEY_ACTIVE_PROFILE_ID,
                null,
            )

        val valid =
            profiles.firstOrNull {
                it.id == stored
            }

        return (
            valid
                ?: profiles.first()
        ).id
    }

    @Synchronized
    fun activeProfile(): VueoProfile {
        val id = activeProfileId()

        return profiles()
            .firstOrNull {
                it.id == id
            }
            ?: profiles().first()
    }

    @Synchronized
    fun setActiveProfile(
        profileId: String,
    ): Boolean {
        val exists =
            profiles().any {
                it.id == profileId
            }

        if (!exists) {
            return false
        }

        prefs.edit()
            .putString(
                KEY_ACTIVE_PROFILE_ID,
                profileId,
            )
            .apply()

        return true
    }

    @Synchronized
    fun createProfile(
        name: String,
        avatar: String,
        isKids: Boolean,
    ): VueoProfile {
        val cleanName =
            name.trim()
                .take(MAX_NAME_LENGTH)

        require(cleanName.isNotBlank()) {
            "Profile name cannot be empty."
        }

        val existing =
            profiles()
                .toMutableList()

        require(
            existing.size <
                MAX_PROFILES
        ) {
            "VUEO supports up to $MAX_PROFILES local profiles."
        }

        val profile =
            VueoProfile(
                id = UUID
                    .randomUUID()
                    .toString(),
                name = cleanName,
                avatar =
                    avatar
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?: DEFAULT_AVATAR,
                isKids = isKids,
                createdAtEpochMs =
                    System.currentTimeMillis(),
            )

        existing += profile
        writeProfiles(existing)

        if (existing.size > 1) {
            prefs.edit()
                .putBoolean(
                    KEY_ASK_ON_STARTUP,
                    true,
                )
                .apply()
        }

        return profile
    }

    @Synchronized
    fun updateProfile(
        profileId: String,
        name: String,
        avatar: String,
        isKids: Boolean,
    ): Boolean {
        val cleanName =
            name.trim()
                .take(MAX_NAME_LENGTH)

        if (cleanName.isBlank()) {
            return false
        }

        val current =
            profiles()
                .toMutableList()

        val index =
            current.indexOfFirst {
                it.id == profileId
            }

        if (index < 0) {
            return false
        }

        val existing = current[index]

        current[index] =
            existing.copy(
                name = cleanName,
                avatar =
                    avatar
                        .takeIf {
                            it.isNotBlank()
                        }
                        ?: DEFAULT_AVATAR,
                isKids = isKids,
            )

        writeProfiles(current)
        return true
    }

    @Synchronized
    fun deleteProfile(
        profileId: String,
    ): Boolean {
        if (
            profileId ==
                DEFAULT_PROFILE_ID
        ) {
            return false
        }

        val current =
            profiles()
                .toMutableList()

        val removed =
            current.removeAll {
                it.id == profileId
            }

        if (!removed) {
            return false
        }

        writeProfiles(current)
        clearProfileData(profileId)

        if (
            prefs.getString(
                KEY_ACTIVE_PROFILE_ID,
                null,
            ) == profileId
        ) {
            prefs.edit()
                .putString(
                    KEY_ACTIVE_PROFILE_ID,
                    current.first().id,
                )
                .apply()
        }

        if (current.size <= 1) {
            prefs.edit()
                .putBoolean(
                    KEY_ASK_ON_STARTUP,
                    false,
                )
                .apply()
        }

        return true
    }

    fun askWhoIsWatchingOnStartup():
        Boolean =
        prefs.getBoolean(
            KEY_ASK_ON_STARTUP,
            false,
        )

    fun setAskWhoIsWatchingOnStartup(
        enabled: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                KEY_ASK_ON_STARTUP,
                enabled,
            )
            .apply()
    }

    fun shouldShowPickerOnStartup():
        Boolean =
        profiles().size > 1 &&
            askWhoIsWatchingOnStartup()

    private fun readProfiles():
        List<VueoProfile> {
        val raw =
            prefs.getString(
                KEY_PROFILES,
                null,
            )
                ?: return emptyList()

        return runCatching {
            val array =
                JSONArray(raw)

            buildList {
                for (
                    index in
                    0 until array.length()
                ) {
                    val json =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    val id =
                        json.optString(
                            "id"
                        ).trim()

                    val name =
                        json.optString(
                            "name"
                        ).trim()

                    if (
                        id.isBlank() ||
                        name.isBlank()
                    ) {
                        continue
                    }

                    add(
                        VueoProfile(
                            id = id,
                            name = name,
                            avatar =
                                json.optString(
                                    "avatar",
                                    DEFAULT_AVATAR,
                                ).ifBlank {
                                    DEFAULT_AVATAR
                                },
                            isKids =
                                json.optBoolean(
                                    "isKids",
                                    false,
                                ),
                            createdAtEpochMs =
                                json.optLong(
                                    "createdAtEpochMs",
                                    0L,
                                ),
                        )
                    )
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    private fun writeProfiles(
        profiles:
            List<VueoProfile>,
    ) {
        val array =
            JSONArray()

        profiles.forEach {
            profile ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        profile.id,
                    )
                    .put(
                        "name",
                        profile.name,
                    )
                    .put(
                        "avatar",
                        profile.avatar,
                    )
                    .put(
                        "isKids",
                        profile.isKids,
                    )
                    .put(
                        "createdAtEpochMs",
                        profile.createdAtEpochMs,
                    )
            )
        }

        prefs.edit()
            .putString(
                KEY_PROFILES,
                array.toString(),
            )
            .apply()
    }

    private fun clearProfileData(
        profileId: String,
    ) {
        val prefix =
            profilePrefix(
                profileId
            )

        listOf(
            "vueo_library",
            "vueo_playback",
            "vueo_settings",
        ).forEach {
            prefsName ->
            val target =
                appContext
                    .getSharedPreferences(
                        prefsName,
                        Context.MODE_PRIVATE,
                    )

            val editor =
                target.edit()

            target.all.keys
                .filter {
                    key ->
                    key.startsWith(
                        prefix
                    ) ||
                        key.contains(
                            ":$prefix"
                        )
                }
                .forEach(
                    editor::remove
                )

            editor.apply()
        }
    }

    companion object {
        const val PREFS_NAME =
            "vueo_profiles"

        const val DEFAULT_PROFILE_ID =
            "default"

        const val MAX_PROFILES =
            8

        private const val DEFAULT_PROFILE_NAME =
            "You"

        private const val DEFAULT_AVATAR =
            "avatar_man_1"

        private const val KEY_PROFILES =
            "profiles"

        private const val KEY_ACTIVE_PROFILE_ID =
            "active_profile_id"

        private const val KEY_ASK_ON_STARTUP =
            "ask_who_is_watching_on_startup"

        private const val MAX_NAME_LENGTH =
            24

        fun profilePrefix(
            profileId: String,
        ): String =
            "profile:$profileId:"

        fun scopedPreferenceKey(
            profileId: String,
            baseKey: String,
        ): String =
            if (
                profileId ==
                    DEFAULT_PROFILE_ID
            ) {
                baseKey
            } else {
                profilePrefix(
                    profileId
                ) + baseKey
            }
    }
}
