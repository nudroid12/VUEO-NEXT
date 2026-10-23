package com.vueo.shared.core.plugin

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack
import org.json.JSONArray

/** Keep sidecars on their owning stream, including CDN request headers. */
internal fun parsePluginSubtitles(
    array: JSONArray?, streamHeaders: Map<String, String>,
    providerId: String, providerName: String,
    limit: Int = 32,
): List<SubtitleTrack> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
    val item = array?.optJSONObject(index) ?: return@mapNotNull null
    fun text(vararg fields: String): String? = fields.firstNotNullOfOrNull { field ->
        (item.opt(field) as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
    val url = text("url", "file", "src")?.takeIf {
        it.startsWith("https://") || it.startsWith("http://")
    } ?: return@mapNotNull null
    val language = LanguagePolicy.canonicalCode(text("language", "lang", "srclang", "name", "label"))
        ?: "und"
    SubtitleTrack(
        id = text("id") ?: "stream-$language-${url.hashCode()}",
        language = language, url = url, providerId = providerId, providerName = providerName,
        name = text("name", "label"),
        mimeType = text("mimeType", "mime_type", "type", "format"),
        headers = streamHeaders.filterKeys { key ->
            item.optJSONObject("headers")?.keys()?.asSequence()?.none { it.equals(key, true) } ?: true
        } + item.optJSONObject("headers").toStringMap(),
    )
}.distinctBy { it.url }.take(limit.coerceIn(1, 128))

/** JSON indexes travel with their owning source; they are not subtitle tracks for playback. */
internal fun parsePluginSubtitleRequests(array: JSONArray?, providerId: String, providerName: String): List<SubtitleTrack> =
    parsePluginSubtitles(array, emptyMap(), providerId, providerName, 2)
        .filter { track -> runCatching {
            val uri = java.net.URI(track.url)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false) }
        .map { it.copy(mimeType = com.vueo.shared.core.player.DeferredPluginSubtitles.INDEX_MIME, language = "und") }
