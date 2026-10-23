package com.vueo.shared.core.player

import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.plugin.PluginHttp
import com.vueo.shared.core.plugin.parsePluginSubtitles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Selected-source subtitle indexes are independent of video discovery and video preparation. */
object DeferredPluginSubtitles {
    const val INDEX_MIME = "application/vnd.vueo.subtitle-list+json"
    fun isIndex(track: SubtitleTrack): Boolean = track.mimeType == INDEX_MIME
    fun requests(tracks: List<SubtitleTrack>): List<SubtitleTrack> = tracks.filter(::isIndex).distinctBy { it.url }.take(2)

    suspend fun load(
        requests: List<SubtitleTrack>,
        fetchText: suspend (SubtitleTrack) -> String = ::fetchIndex,
    ): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SubtitleTrack>()
        for (request in DeferredPluginSubtitles.requests(requests)) {
            currentCoroutineContext().ensureActive()
            try {
                val text = fetchText(request)
                currentCoroutineContext().ensureActive()
                val array = when {
                    text.trimStart().startsWith("[") -> JSONArray(text)
                    else -> JSONObject(text).optJSONArray("subtitles") ?: JSONArray()
                }
                // Index credentials belong to its origin; never inherit them onto a different subtitle CDN.
                result += parsePluginSubtitles(array, emptyMap(), request.providerId, request.providerName, 128)
                    .filter { it.url.startsWith("https://") }
                    .map { it.copy(liveOverlay = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A missing/failed subtitle list never becomes a playback failure.
            }
        }
        result.distinctBy { it.url }.take(128)
    }

    private suspend fun fetchIndex(request: SubtitleTrack): String {
        val response = JSONObject(PluginHttp.executeJson(JSONObject().apply {
            put("url", request.url); put("method", "GET")
            put("headers", JSONObject(request.headers))
        }.toString()))
        check(!response.has("error") && response.optInt("status") in 200..299) { "Subtitle index request failed" }
        return response.optString("body")
    }
}
