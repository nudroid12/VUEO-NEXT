package com.vueo.shared.core.plugin

import com.vueo.shared.core.language.LanguagePolicy
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.media.SubtitleCipherKey
import com.vueo.shared.core.media.SubtitleTextTransform
import org.json.JSONArray

/** Keep sidecars on their owning stream, including CDN request headers. */
internal fun parsePluginSubtitles(
    array: JSONArray?, streamHeaders: Map<String, String>,
    providerId: String, providerName: String,
): List<SubtitleTrack> = (0 until (array?.length() ?: 0)).mapNotNull { index ->
    val item = array?.optJSONObject(index) ?: return@mapNotNull null
    fun text(vararg fields: String): String? = fields.firstNotNullOfOrNull { field ->
        (item.opt(field) as? String)?.trim()?.takeIf { it.isNotBlank() }
    }
    val url = text("url", "file", "src")?.takeIf {
        it.startsWith("https://") || it.startsWith("http://")
    } ?: return@mapNotNull null
    val transform = parseSubtitleTransform(item.optJSONObject("textTransform"))
    if (item.has("textTransform") && !item.isNull("textTransform") && transform == null) return@mapNotNull null
    val language = LanguagePolicy.canonicalCode(text("language", "lang", "srclang", "name", "label"))
        ?: "und"
    SubtitleTrack(
        id = text("id") ?: "stream-$language-${url.hashCode()}",
        language = language, url = url, providerId = providerId, providerName = providerName,
        name = text("name", "label"),
        textTransform = transform,
        mimeType = text("mimeType", "mime_type", "type", "format"),
        headers = streamHeaders.filterKeys { key ->
            item.optJSONObject("headers")?.keys()?.asSequence()?.none { it.equals(key, true) } ?: true
        } + item.optJSONObject("headers").toStringMap(),
    )
}.distinctBy { it.url }.take(32)

/** Accept only bounded, declarative local transforms; never execute plugin code here. */
private fun parseSubtitleTransform(obj: org.json.JSONObject?): SubtitleTextTransform? {
    obj ?: return null
    if (obj.optString("algorithm") != "aes-cbc" || obj.optString("scope") != "cue-lines") return null
    val rows = obj.optJSONArray("keys") ?: return null
    if (rows.length() !in 1..4) return null
    val keys = (0 until rows.length()).map { index ->
        val row = rows.optJSONObject(index) ?: return null
        val key = row.optString("key")
        val iv = row.optString("iv")
        if (key.length !in 1..64 || iv.length !in 1..32) return null
        val keyBytes = runCatching { android.util.Base64.decode(key, android.util.Base64.DEFAULT) }.getOrNull() ?: return null
        val ivBytes = runCatching { android.util.Base64.decode(iv, android.util.Base64.DEFAULT) }.getOrNull() ?: return null
        if (keyBytes.size !in listOf(16, 24, 32) || ivBytes.size != 16) return null
        SubtitleCipherKey(key, iv)
    }
    return SubtitleTextTransform("aes-cbc", "cue-lines", keys)
}
