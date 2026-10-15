package com.vueo.shared.core.smartsubs

import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.player.PlayerTrackPolicy
import com.vueo.shared.core.player.SubtitleSessionCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/** Lists signed, on-demand AI tracks. Never requests their translation URLs here. */
object SmartSubsPlaybackBridge {
    const val PROVIDER_ID = "vueo-smartsubs-ai"
    private val allowedHeaders = setOf("referer", "origin", "user-agent", "authorization", "cookie", "accept", "accept-language")

    fun eligibleSources(tracks: List<SubtitleTrack>, includeAddonSources: Boolean = false): List<SubtitleTrack> =
        SmartSubsSourcePolicy.eligibleSources(tracks, includeAddonSources)

    suspend fun discover(
        config: SmartSubsSettingsStore.Configuration,
        mediaType: String,
        videoId: String,
        tracks: List<SubtitleTrack>,
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.connected) return@withContext emptyList()
        val sources = eligibleSources(tracks, config.includeAddonSources)
        if (sources.isEmpty()) return@withContext emptyList()
        val type = when (mediaType.lowercase()) {
            "movie" -> "movie"
            "series", "tv" -> "series"
            else -> return@withContext emptyList()
        }
        require(Regex("[\\w:.-]{1,128}").matches(videoId)) { "SmartSubs needs a valid video ID." }
        val token = URLEncoder.encode(config.token, "UTF-8").replace("+", "%20")
        val base = VueoSmartSubsClient.BACKEND_URL
        val configuredPath = "/c/$token/"
        val body = JSONObject().put("media", JSONObject().put("type", type).put("id", videoId))
            .put("subtitles", JSONArray().apply {
                sources.forEach { track ->
                    val headers = JSONObject()
                    track.headers.forEach { (name, value) ->
                        if (name.lowercase() in allowedHeaders && value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                            headers.put(name.lowercase(), value)
                        }
                    }
                    require(headers.toString().toByteArray(Charsets.UTF_8).size <= 8192) { "SmartSubs source headers are too large." }
                    val cachedText = SubtitleSessionCache.get(track.url)
                        ?.takeIf { it.size <= 128 * 1024 }
                        ?.toString(Charsets.UTF_8)
                        ?.takeIf { "-->" in it }
                    val item = JSONObject().put("id", track.id.take(256)).put("lang", "eng")
                        .put("providerName", track.providerName.take(160))
                    // An already loaded English subtitle can be uploaded as text for
                    // providers that bind downloads to this device's IP/session.
                    if (cachedText != null) item.put("text", cachedText)
                    else item.put("url", track.url).put("headers", headers)
                    put(item)
                }
            }).toString().toByteArray(Charsets.UTF_8)
        require(body.size <= 2 * 1024 * 1024) { "SmartSubs source request is too large." }
        coroutineContext.ensureActive()
        val connection = URI(base + configuredPath + "subtitles").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            when (connection.responseCode) {
                200 -> Unit
                401 -> error("SmartSubs session is invalid. Test Connection again in Gemini settings.")
                429 -> error("SmartSubs request limit reached. Refresh subtitles later.")
                else -> error("SmartSubs subtitle listing failed (HTTP ${connection.responseCode}). Original subtitles are still available.")
            }
            val response = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 128 * 1024) { "Invalid SmartSubs subtitle response." }
                    output.write(buffer, 0, count)
                }
                JSONObject(output.toString(Charsets.UTF_8.name()))
            }
            val returned = response.optJSONArray("subtitles") ?: error("Invalid SmartSubs subtitle response.")
            val ai = buildList {
                for (index in 0 until returned.length()) {
                    val track = returned.optJSONObject(index) ?: continue
                    if (!track.optString("id").startsWith("gemini-ai-")) continue
                    val url = track.optString("url")
                    val uri = runCatching { URI(url) }.getOrNull() ?: continue
                    if (uri.scheme != "https" || uri.host != URI(base).host || uri.port !in setOf(-1, 443) ||
                        uri.userInfo != null || uri.query != null || uri.fragment != null ||
                        !uri.rawPath.startsWith(configuredPath + "translated/") || !uri.rawPath.endsWith(".vtt")) continue
                    val source = sources.getOrNull(size) ?: break
                    add(SubtitleTrack(
                        id = track.getString("id"), language = "ms", url = url,
                        providerId = PROVIDER_ID, providerName = "VUEO x SmartSubs",
                        name = "Malay AI",
                        mimeType = "text/vtt",
                        // Match the ID shown on the original English card on both players.
                        // The raw source ID remains intact for requests and selection.
                        displayId = PlayerTrackPolicy.subtitleDisplayId(source)?.let { sourceDisplayId ->
                            val provider = PlayerTrackPolicy.subtitleProviderLabel(source)
                            if (provider.isBlank()) sourceDisplayId else "$provider - $sourceDisplayId"
                        },
                        // Carry display metadata with the selected GET as well as
                        // listing POST, including when older source records are reused.
                        headers = mapOf(
                            "X-Vueo-Provider-Name" to URLEncoder.encode(source.providerName.take(160), "UTF-8"),
                            "X-Vueo-Subtitle-Id" to URLEncoder.encode(source.id.take(256), "UTF-8"),
                        ),
                    ))
                }
            }
            coroutineContext.ensureActive()
            check(ai.isNotEmpty()) { "SmartSubs returned no AI tracks. Refresh subtitles to retry." }
            ai
        } finally {
            connection.disconnect()
        }
    }

    fun failureMessage(error: Exception): String = when (error) {
        is java.net.SocketTimeoutException -> "SmartSubs subtitle listing timed out. Refresh subtitles to retry."
        is java.io.IOException -> "Unable to reach SmartSubs. Original subtitles are still available."
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "SmartSubs subtitle listing failed."
        else -> "SmartSubs subtitle listing failed. Original subtitles are still available."
    }
}
