package com.vueo.shared.core.smartsubs

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** Opens only this backend's configured Diagnose; never logs or copies a session token. */
object SmartSubsDiagnostics {
    fun url(token: String): String {
        require(token.isNotBlank() && token.length <= 4096 && token.none { it.isWhitespace() || it.isISOControl() })
        val encoded = URLEncoder.encode(token, "UTF-8").replace("+", "%20")
        return "${VueoSmartSubsClient.BACKEND_URL}/c/$encoded/diagnose"
    }

    fun open(context: Context, configuration: SmartSubsSettingsStore.Configuration): Boolean {
        if (!configuration.connected) return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url(configuration.token)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) { false }
    }
}
