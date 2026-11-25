package com.vueo.shared.core.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Local, per-profile Home row toggle, independent of recommendation/DNA settings. */
class NewEpisodesPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vueo_settings", Context.MODE_PRIVATE)
    private fun key(profileId: String) = ProfileStore.scopedPreferenceKey(profileId, "home_new_episodes_enabled")

    fun enabled(profileId: String): Boolean = prefs.getBoolean(key(profileId), true)

    fun setEnabled(profileId: String, enabled: Boolean) {
        prefs.edit().putBoolean(key(profileId), enabled).apply()
        changes.update { it + 1L }
    }

    companion object {
        private val changes = MutableStateFlow(0L)
        val revision = changes.asStateFlow()
    }
}
