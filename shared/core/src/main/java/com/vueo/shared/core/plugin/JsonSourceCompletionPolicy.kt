package com.vueo.shared.core.plugin

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** Opt-in JSON source eligibility. This proves discovery, never media playback. */
internal object JsonSourceCompletionPolicy {
    fun eligible(requestUrl: String, body: JSONObject, rule: JSONObject?, mediaHosts: List<String>): Boolean = runCatching {
        if (rule == null || !matchesRequest(requestUrl, rule.optString("requestMatcher"))) return@runCatching false
        val expected = rule.optString("identityValue")
        val identity = value(body, rule.optString("identityPath")) ?: return@runCatching false
        if (expected.isBlank() || identity.toString() != expected) return@runCatching false
        val source = value(body, rule.optString("sourcePath")) as? JSONObject ?: return@runCatching false
        val original = language(value(body, rule.optString("originalLanguagePath")))
        val minimum = rule.optInt("minimumHeight", 720).coerceIn(720, 4320)
        val variants = source.optJSONArray("qualities")
        if (variants != null && variants.length() > 0) {
            if (variants.length() > 128) return@runCatching false
            val selected = (0 until variants.length()).mapNotNull { variants.optJSONObject(it) }
                .filter { row ->
                    val height = height(row.optString("quality"))
                    validMedia(row, null) && (height == null || height >= minimum)
                }.sortedWith(compareBy<JSONObject> { Regex("hevc|h265", RegexOption.IGNORE_CASE).containsMatchIn(it.optString("codec")) }
                    .thenByDescending { Regex("^\\d+").find(it.optString("quality"))?.value?.toIntOrNull() ?: 0 })
                .firstOrNull() ?: return@runCatching false
            return@runCatching validMedia(selected, mediaHosts) && originalAudio(source, original, selected)
        }
        if (!validMedia(source, mediaHosts) || !originalAudio(source, original)) return@runCatching false
        val quality = height(source.optString("quality"))
        kind(source) != "mp4" || quality == null || quality >= minimum
    }.getOrDefault(false)

    private fun value(body: JSONObject, path: String): Any? {
        if (path.length !in 1..128) return null
        val keys = path.split('.')
        if (keys.size > 8 || keys.any { !Regex("[A-Za-z0-9_]+").matches(it) }) return null
        var result: Any? = body
        for (key in keys) result = (result as? JSONObject)?.opt(key)
        return result?.takeUnless { it === JSONObject.NULL }
    }

    private fun matchesRequest(actual: String, expected: String): Boolean = runCatching {
        val a = URI(actual); val b = URI(expected)
        if (a.scheme != "https" || b.scheme != "https" || a.userInfo != null || b.userInfo != null ||
            a.host.isNullOrBlank() || !a.host.equals(b.host, true) || a.port != b.port || a.rawPath != b.rawPath) return@runCatching false
        fun query(u: URI): Map<String, List<String>> = u.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }
            .map { val p = it.split('=', limit = 2)
                URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p.getOrElse(1) { "" }, "UTF-8") }
            .groupBy({ it.first }, { it.second })
        val required = query(b); val received = query(a)
        required.isNotEmpty() && required.all { (key, values) -> values.size == 1 && received[key] == values }
    }.getOrDefault(false)

    private fun validMedia(source: JSONObject, hosts: List<String>?): Boolean = runCatching {
        val url = source.optString("url")
        if (url != url.trim() || url.length > 16384 || url.any { it == '\r' || it == '\n' || it == '\u0000' }) return@runCatching false
        val parsed = URI(url)
        parsed.scheme == "https" && parsed.userInfo == null && !parsed.host.isNullOrBlank() &&
            (hosts == null || parsed.host?.lowercase() in hosts) && kind(source).isNotBlank()
    }.getOrDefault(false)

    private fun kind(source: JSONObject): String {
        val path = runCatching { URI(source.optString("url")).path.orEmpty().lowercase() }.getOrDefault("")
        val mime = listOf("mimeType", "contentType", "type").firstNotNullOfOrNull { key ->
            source.optString(key).takeIf { it.isNotBlank() }
        }.orEmpty().lowercase()
        return when {
            source.optString("manifest").trimStart().startsWith("#EXTM3U") || path.endsWith(".m3u8") || "mpegurl" in mime || mime == "hls" -> "hls"
            path.endsWith(".mpd") || "dash+xml" in mime || mime == "dash" -> "dash"
            path.endsWith(".mp4") || mime == "mp4" || mime == "video/mp4" -> "mp4"
            else -> ""
        }
    }
    private fun height(quality: String): Int? = Regex("\\b(2160|1440|1080|720|576|480|360|240)p?\\b", RegexOption.IGNORE_CASE)
        .find(quality)?.groupValues?.get(1)?.toIntOrNull()
    private fun language(raw: Any?): String {
        if (raw == null || raw === JSONObject.NULL) return ""
        val text = raw.toString().lowercase()
        return mapOf("eng" to "en", "jpn" to "ja", "kor" to "ko", "hin" to "hi", "ita" to "it", "zho" to "zh", "chi" to "zh", "msa" to "ms", "may" to "ms", "ind" to "id")[text] ?: text
    }
    private fun originalAudio(source: JSONObject, original: String, variant: JSONObject? = null): Boolean {
        fun field(key: String): Any? = if (variant?.has(key) == true) variant.opt(key) else source.opt(key)
        val single = language(field("audioLanguage")).ifBlank { language(field("language")) }
        val list = field("audioLanguages") as? JSONArray ?: JSONArray()
        val languages = (0 until minOf(list.length(), 128)).map { language(list.opt(it)) }
        val dual = field("isDualAudio") == true || field("isMultiAudio") == true
        val includes = original.isNotBlank() && (single == original || original in languages)
        val knownOther = original.isNotBlank() && !includes && (languages.isNotEmpty() || (single.isNotBlank() && !dual))
        return !knownOther && !(field("isDubbed") == true && !includes && !dual)
    }
}
