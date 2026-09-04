package com.vueo.shared.core.storage

import android.content.Context

data class VueoDataNamespace(
    val profilePrefsName: String = ProfileStore.PREFS_NAME,
    val libraryPrefsName: String = "vueo_library",
    val playbackPrefsName: String = "vueo_playback",
    val settingsPrefsName: String = "vueo_settings",
)

/**
 * One shared entry point for profile-scoped VUEO local data.
 * Mobile can use the default namespace while TV can provide its existing
 * preference names without changing the underlying contracts.
 */
class VueoUserDataCore(
    context: Context,
    namespace: VueoDataNamespace = VueoDataNamespace(),
) {
    private val appContext = context.applicationContext

    val profiles =
        ProfileStore(
            context = appContext,
            prefsName = namespace.profilePrefsName,
            scopedPreferenceFiles =
                setOf(
                    namespace.libraryPrefsName,
                    namespace.playbackPrefsName,
                    namespace.settingsPrefsName,
                ),
        )

    val library =
        LibraryStore(
            context = appContext,
            prefsName = namespace.libraryPrefsName,
            profileStore = profiles,
        )

    val playback =
        PlaybackStore(
            context = appContext,
            prefsName = namespace.playbackPrefsName,
            profileStore = profiles,
        )

    val settings =
        SettingsStore(
            context = appContext,
            prefsName = namespace.settingsPrefsName,
            profileStore = profiles,
        )
}
