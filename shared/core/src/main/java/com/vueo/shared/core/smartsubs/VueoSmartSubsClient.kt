package com.vueo.shared.core.smartsubs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** No direct Gemini calls or translation on the device. */
object VueoSmartSubsClient {
    data class Session(val token: String, val expiresAt: Long, val model: String)

    const val BACKEND_URL = "https://vueo-smartsubs.nusmart.workers.dev"

    suspend fun connect(apiKey: String): Session = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        require(key.length in 20..256 && key.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Enter a valid Gemini API key" }
        val connection = URI(BACKEND_URL + "/v1/session").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val bytes = JSONObject().put("geminiApiKey", key).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status != 201) {
                val payload = runCatching {
                    connection.errorStream?.use { input -> JSONObject(readResponse(input)) }
                }.getOrNull()
                val model = payload?.optString("model")?.takeIf {
                    it.length in 1..80 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '.' || c == '_' }
                }
                val modelHint = model?.let { " Model: $it." } ?: ""
                val upstreamStatus = payload?.optInt("upstreamStatus")?.takeIf { it in 100..599 }
                val apiStatus = payload?.optString("upstreamApiStatus")?.takeIf { Regex("[A-Z_]{1,64}").matches(it) }
                val detail = payload?.optString("errorDetail").orEmpty()
                    .replace(key, "[redacted]")
                    .replace(Regex("AIza[A-Za-z0-9_-]+"), "[redacted]")
                    .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[URL redacted]")
                    .replace(Regex("[\\p{Cntrl}]"), " ").trim().take(300)
                val diagnostic = buildString {
                    if (upstreamStatus != null) append(" HTTP $upstreamStatus")
                    if (apiStatus != null) append(" $apiStatus")
                    if (upstreamStatus != null || apiStatus != null) append('.')
                    if (detail.isNotBlank()) append(" $detail")
                }
                val message = when (payload?.optString("errorCode")) {
                    "GEMINI_KEY_INVALID" -> "Gemini rejected this API key. Check or replace the key."
                    "GEMINI_ACCESS_DENIED" -> "Gemini denied access. Check key restrictions and project permissions."
                    "GEMINI_MODEL_UNAVAILABLE" -> "Gemini model unavailable for this key or API version.$modelHint"
                    "GEMINI_QUOTA_EXCEEDED" -> "Gemini quota or rate limit reached. Check quota or try again later."
                    "GEMINI_TIMEOUT" -> "Gemini validation timed out. Try again."
                    "GEMINI_NETWORK_ERROR" -> "SmartSubs could not reach Gemini. Try again later."
                    "GEMINI_SERVICE_UNAVAILABLE" -> "Gemini is temporarily unavailable. Try again later."
                    "GEMINI_BILLING_REQUIRED" -> "Gemini billing or credit issue.$diagnostic"
                    "GEMINI_PRECONDITION_FAILED" -> "Gemini account/service prerequisite failed.$diagnostic"
                    "GEMINI_INVALID_ARGUMENT" -> "Gemini validation parameter rejected.$diagnostic"
                    "GEMINI_REQUEST_REJECTED" -> "Gemini rejected the validation request.$modelHint$diagnostic"
                    "INVALID_INPUT" -> "Enter a valid Gemini API key."
                    "SMARTSUBS_SESSION_ERROR" -> "SmartSubs could not create a session. Try again later."
                    else -> when (status) {
                        429 -> "SmartSubs request limit reached. Try again later."
                        422 -> "Gemini validation failed. Update the SmartSubs backend for detailed errors."
                        503 -> "SmartSubs service is not ready. Try again later."
                        404 -> "SmartSubs service is unavailable. Try again later."
                        else -> "Backend connection failed (HTTP $status)."
                    }
                }
                throw IllegalStateException(message)
            }
            val json = connection.inputStream.use { input -> JSONObject(readResponse(input)) }
            val token = json.optString("token")
            val expires = json.optLong("expiresAt")
            check(token.length in 20..4096 && expires > System.currentTimeMillis()) { "Invalid backend session" }
            Session(token, expires, json.optString("model"))
        } finally { connection.disconnect() }
    }

    private fun readResponse(input: java.io.InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(output.size() + count <= 16_384) { "Invalid backend response" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun failureMessage(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException -> "Connection timed out. Try again."
        is java.io.IOException -> "Unable to reach SmartSubs. Check your internet connection."
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Connection failed"
        else -> "Connection failed. Check your Gemini key and access."
    }
}
