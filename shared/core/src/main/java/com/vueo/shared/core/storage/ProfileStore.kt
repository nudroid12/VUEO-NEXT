package com.vueo.shared.core.storage

import android.content.Context
import android.util.Base64
import com.vueo.shared.core.profile.ProfileAvatarCatalog
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class VueoProfile(
    val id: String,
    val name: String,
    val avatar: String,
    val isKids: Boolean,
    val createdAtEpochMs: Long,
)

class ProfileStore(
    context: Context,
    private val prefsName: String = PREFS_NAME,
    private val scopedPreferenceFiles: Set<String> = DEFAULT_SCOPED_PREFERENCE_FILES,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            prefsName,
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
        prefs.edit()
            .remove(pinSaltKey(profileId))
            .remove(pinHashKey(profileId))
            .apply()

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

    @Synchronized
    fun hasProfilePin(
        profileId: String,
    ): Boolean {
        if (profiles().none { it.id == profileId }) {
            return false
        }

        return !prefs.getString(
            pinHashKey(profileId),
            null,
        ).isNullOrBlank() &&
            !prefs.getString(
                pinSaltKey(profileId),
                null,
            ).isNullOrBlank()
    }

    @Synchronized
    fun setProfilePin(
        profileId: String,
        pin: String,
    ): Boolean {
        if (profiles().none { it.id == profileId }) {
            return false
        }

        if (!PIN_REGEX.matches(pin)) {
            return false
        }

        val salt = ByteArray(PIN_SALT_BYTES)
        SecureRandom().nextBytes(salt)

        val hash = derivePin(pin, salt)

        prefs.edit()
            .putString(
                pinSaltKey(profileId),
                Base64.encodeToString(
                    salt,
                    Base64.NO_WRAP,
                ),
            )
            .putString(
                pinHashKey(profileId),
                Base64.encodeToString(
                    hash,
                    Base64.NO_WRAP,
                ),
            )
            .apply()

        return true
    }

    @Synchronized
    fun verifyProfilePin(
        profileId: String,
        pin: String,
    ): Boolean {
        if (!PIN_REGEX.matches(pin)) {
            return false
        }

        val encodedSalt =
            prefs.getString(
                pinSaltKey(profileId),
                null,
            )
                ?: return false

        val encodedHash =
            prefs.getString(
                pinHashKey(profileId),
                null,
            )
                ?: return false

        return runCatching {
            val salt =
                Base64.decode(
                    encodedSalt,
                    Base64.NO_WRAP,
                )
            val expected =
                Base64.decode(
                    encodedHash,
                    Base64.NO_WRAP,
                )
            val actual = derivePin(pin, salt)

            MessageDigest.isEqual(
                expected,
                actual,
            )
        }.getOrDefault(false)
    }

    @Synchronized
    fun clearProfilePin(
        profileId: String,
    ): Boolean {
        if (!hasProfilePin(profileId)) {
            return false
        }

        prefs.edit()
            .remove(pinSaltKey(profileId))
            .remove(pinHashKey(profileId))
            .apply()

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
        Boolean {
        val activeLocked =
            hasProfilePin(
                activeProfileId()
            )

        return activeLocked ||
            askWhoIsWatchingOnStartup()
    }

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

        scopedPreferenceFiles.forEach {
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

    private fun derivePin(
        pin: String,
        salt: ByteArray,
    ): ByteArray {
        val spec =
            PBEKeySpec(
                pin.toCharArray(),
                salt,
                PIN_ITERATIONS,
                PIN_KEY_BITS,
            )

        return try {
            SecretKeyFactory
                .getInstance(PIN_ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun pinSaltKey(
        profileId: String,
    ): String =
        "profile_pin_salt:$profileId"

    private fun pinHashKey(
        profileId: String,
    ): String =
        "profile_pin_hash:$profileId"

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
            ProfileAvatarCatalog.DEFAULT_ID

        private const val KEY_PROFILES =
            "profiles"

        private const val KEY_ACTIVE_PROFILE_ID =
            "active_profile_id"

        private const val KEY_ASK_ON_STARTUP =
            "ask_who_is_watching_on_startup"

        private const val MAX_NAME_LENGTH =
            24

        private const val PIN_ALGORITHM =
            "PBKDF2WithHmacSHA1"

        private const val PIN_ITERATIONS =
            120_000

        private const val PIN_KEY_BITS =
            256

        private const val PIN_SALT_BYTES =
            16

        private val PIN_REGEX =
            Regex("\\d{4}")

        private val DEFAULT_SCOPED_PREFERENCE_FILES =
            setOf(
                "vueo_library",
                "vueo_playback",
                "vueo_settings",
                "vueo_tv_library",
                "vueo_tv_playback",
                "vueo_tv_settings",
            )

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
