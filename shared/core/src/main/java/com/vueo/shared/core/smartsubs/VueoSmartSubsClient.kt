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
                val message = when (status) {
                    429 -> "Quota or rate limit reached. Try again later."
                    422 -> "Gemini key/model validation failed. Check access and quota."
                    503 -> "SmartSubs service is not ready. Try again later."
                    404 -> "SmartSubs service is unavailable. Try again later."
                    else -> "Backend connection failed (HTTP $status)."
                }
                throw IllegalStateException(message)
            }
            val response = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 16_384) { "Invalid backend response" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val json = JSONObject(String(response, Charsets.UTF_8))
            val token = json.optString("token")
            val expires = json.optLong("expiresAt")
            check(token.length in 20..4096 && expires > System.currentTimeMillis()) { "Invalid backend session" }
            Session(token, expires, json.optString("model"))
        } finally { connection.disconnect() }
    }

    fun failureMessage(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException -> "Connection timed out. Try again."
        is java.io.IOException -> "Unable to reach SmartSubs. Check your internet connection."
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Connection failed"
        else -> "Connection failed. Check your Gemini key and access."
    }
}
