package com.vueo.shared.core.smartsubs

import android.content.Context
import com.vueo.shared.core.sync.SyncSecureFile
import org.json.JSONObject

/** Device-only credentials; excluded from backup and account sync. */
class SmartSubsSettingsStore(context: Context) {
    private val file = SyncSecureFile(context.applicationContext, "smartsubs")

    data class Configuration(
        val apiKey: String = "",
        val backendUrl: String = "",
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
        Configuration(value.optString("apiKey"), value.optString("backendUrl"),
            value.optBoolean("enabled"), value.optString("token"),
            value.optLong("expiresAt"), value.optString("model"))
    }.getOrDefault(Configuration()) }

    fun saveCredentials(apiKey: String, backendUrl: String) = synchronized(LOCK) {
        val value = file.read() ?: JSONObject()
        val key = apiKey.trim()
        val url = backendUrl.trim().trimEnd('/')
        if (key != value.optString("apiKey") || url != value.optString("backendUrl")) {
            value.remove("token"); value.remove("expiresAt"); value.remove("model")
            value.put("enabled", false)
        }
        value.put("apiKey", key).put("backendUrl", url)
        file.write(value)
    }

    fun saveSession(apiKey: String, backendUrl: String, session: VueoSmartSubsClient.Session): Boolean = synchronized(LOCK) {
        val value = file.read() ?: return@synchronized false
        if (value.optString("apiKey") != apiKey.trim() || value.optString("backendUrl") != backendUrl.trim().trimEnd('/')) return@synchronized false
        value.put("token", session.token).put("expiresAt", session.expiresAt).put("model", session.model)
        file.write(value)
        true
    }

    fun setEnabled(enabled: Boolean) = synchronized(LOCK) {
        val value = file.read() ?: JSONObject()
        val valid = value.optString("token").isNotBlank() && value.optLong("expiresAt") > System.currentTimeMillis()
        value.put("enabled", enabled && valid)
        file.write(value)
    }

    fun clear() = synchronized(LOCK) { file.delete() }
    companion object { private val LOCK = Any() }
}
