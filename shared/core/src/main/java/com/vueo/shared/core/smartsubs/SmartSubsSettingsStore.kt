package com.vueo.shared.core.smartsubs

import android.content.Context
import com.vueo.shared.core.sync.SyncSecureFile
import org.json.JSONObject

/** Encrypted credentials. Account sync shares only the API key; sessions and switches are local. */
class SmartSubsSettingsStore(context: Context) {
    private val syncSignal = context.applicationContext.getSharedPreferences("vueo_smartsubs_sync_signal", Context.MODE_PRIVATE)
    private val file = SyncSecureFile(context.applicationContext, "smartsubs")

    data class Configuration(
        val apiKey: String = "",
        val backendUrl: String = VueoSmartSubsClient.BACKEND_URL,
        val enabled: Boolean = false,
        val token: String = "",
        val expiresAt: Long = 0,
        val model: String = "",
    ) {
        val connected: Boolean get() = apiKey.isNotBlank() && backendUrl.isNotBlank() &&
            token.isNotBlank() && expiresAt > System.currentTimeMillis()
    }

    fun configuration(): Configuration = synchronized(LOCK) { runCatching {
        val value = file.read() ?: JSONObject()
        val sameBackend = value.optString("backendUrl").trimEnd('/') == VueoSmartSubsClient.BACKEND_URL
        Configuration(apiKey = value.optString("apiKey"),
            enabled = sameBackend && value.optBoolean("enabled"),
            token = if (sameBackend) value.optString("token") else "",
            expiresAt = if (sameBackend) value.optLong("expiresAt") else 0,
            model = if (sameBackend) value.optString("model") else "")
    }.getOrDefault(Configuration()) }

    internal fun apiKeyForSync(): String = synchronized(LOCK) { file.read()?.optString("apiKey").orEmpty() }

    fun saveCredentials(apiKey: String) {
        updateApiKey(apiKey, publishChange = true, preserveEnabled = false)
    }

    /** A remote key never changes this device's ON/OFF preference or imports another session. */
    fun importApiKey(apiKey: String) {
        updateApiKey(apiKey, publishChange = false, preserveEnabled = true)
    }

    private fun updateApiKey(apiKey: String, publishChange: Boolean, preserveEnabled: Boolean) {
        val changed = synchronized(LOCK) {
            val value = file.read() ?: JSONObject()
            val key = apiKey.trim()
            val url = VueoSmartSubsClient.BACKEND_URL
            val keyChanged = key != value.optString("apiKey")
            if (keyChanged || url != value.optString("backendUrl")) {
                value.remove("token"); value.remove("expiresAt"); value.remove("model")
                if (!preserveEnabled) value.put("enabled", false)
            }
            value.put("apiKey", key).put("backendUrl", url)
            file.write(value)
            keyChanged
        }
        if (changed && publishChange) signalKeyChange()
    }

    private fun signalKeyChange() {
        // This preference contains no credential; it wakes the existing debounced sync capture.
        syncSignal.edit().putString("revision", java.util.UUID.randomUUID().toString()).apply()
    }

    fun saveSession(apiKey: String, session: VueoSmartSubsClient.Session): Boolean = synchronized(LOCK) {
        val value = file.read() ?: return@synchronized false
        if (value.optString("apiKey") != apiKey.trim() || value.optString("backendUrl") != VueoSmartSubsClient.BACKEND_URL) return@synchronized false
        value.put("token", session.token).put("expiresAt", session.expiresAt).put("model", session.model)
        file.write(value)
        true
    }

    fun setEnabled(enabled: Boolean) = synchronized(LOCK) {
        val value = file.read() ?: JSONObject()
        val valid = value.optString("backendUrl") == VueoSmartSubsClient.BACKEND_URL && value.optString("token").isNotBlank() && value.optLong("expiresAt") > System.currentTimeMillis()
        value.put("enabled", enabled && valid)
        file.write(value)
    }

    fun clear() {
        val hadKey = synchronized(LOCK) {
            val present = file.read()?.optString("apiKey").orEmpty().isNotBlank()
            file.delete()
            present
        }
        if (hadKey) signalKeyChange()
    }
    companion object { private val LOCK = Any() }
}
