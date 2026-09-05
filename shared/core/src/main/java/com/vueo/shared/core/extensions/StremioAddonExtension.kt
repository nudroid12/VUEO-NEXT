package com.vueo.shared.core.extensions

import com.vueo.shared.core.media.CatalogPage
import com.vueo.shared.core.media.EpisodeItem
import com.vueo.shared.core.media.MediaItem
import com.vueo.shared.core.media.MediaPerson
import com.vueo.shared.core.media.StreamSource
import com.vueo.shared.core.media.SubtitleTrack
import com.vueo.shared.core.stremio.DefaultStremioHttpClient
import com.vueo.shared.core.stremio.StremioAddonClient
import com.vueo.shared.core.stremio.StremioHttpClient
import com.vueo.shared.core.stremio.StremioManifest
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Canonical Stremio MediaExtension used by both Mobile and TV.
 *
 * Catalog/meta/stream/subtitle transport and manifest parsing live here so
 * platform UIs only manage install state, presentation and navigation.
 */
class StremioAddonExtension private constructor(
    override val descriptor: ExtensionDescriptor,
    private val httpClient: StremioHttpClient,
) : MediaExtension {

    private val base = descriptor.baseUrl
        .removeSuffix("/manifest.json")
        .removeSuffix("manifest.json")
        .removeSuffix("/")

    private val sharedClient = StremioAddonClient(
        manifest = StremioManifest(
            id = descriptor.id,
            name = descriptor.name,
            version = descriptor.version,
            manifestUrl = descriptor.baseUrl,
            baseUrl = base,
            description = descriptor.description,
            resources = descriptor.resources,
            types = descriptor.types,
        ),
        httpClient = httpClient,
    )

    override suspend fun catalog(
        type: String,
        catalogId: String,
        extras: Map<String, String>,
    ): CatalogPage {
        val suffix = encodeExtras(extras)
        val url = "$base/catalog/${encode(type)}/${encode(catalogId)}$suffix.json"
        val json = JSONObject(httpClient.get(url))
        val metas = json.optJSONArray("metas") ?: JSONArray()

        return CatalogPage(
            items = (0 until metas.length())
                .mapNotNull { index ->
                    metas.optJSONObject(index)?.toMediaItem(descriptor.id)
                },
        )
    }

    override suspend fun meta(
        type: String,
        id: String,
    ): MediaItem? {
        val url = "$base/meta/${encode(type)}/${encode(id)}.json"
        val json = JSONObject(httpClient.get(url))
        return json.optJSONObject("meta")?.toMediaItem(descriptor.id)
    }

    override suspend fun streams(
        type: String,
        videoId: String,
    ): List<StreamSource> =
        sharedClient.streams(
            type = type,
            videoId = videoId,
        ).map { source ->
            StreamSource(
                name = source.name,
                url = source.url,
                infoHash = source.infoHash,
                fileIndex = source.fileIndex,
                quality = source.quality,
                codec = source.codec,
                hdr = source.hdr,
                audio = source.audio,
                language = source.language,
                sizeBytes = source.sizeBytes,
                headers = source.headers,
                rankBoost = source.rankBoost,
                providerId = source.providerId,
                providerName = source.providerName,
            )
        }

    override suspend fun subtitles(
        type: String,
        id: String,
        extras: Map<String, String>,
    ): List<SubtitleTrack> =
        sharedClient.subtitles(
            type = type,
            videoId = id,
            extras = extras,
        ).map { subtitle ->
            SubtitleTrack(
                id = subtitle.id,
                language = subtitle.language,
                url = subtitle.url,
                providerId = subtitle.providerId,
                providerName = subtitle.providerName,
                name = subtitle.name,
            )
        }

    companion object {
        suspend fun fromManifestUrl(
            manifestUrl: String,
            httpClient: StremioHttpClient = DefaultStremioHttpClient,
        ): StremioAddonExtension {
            require(manifestUrl.startsWith("https://", ignoreCase = true)) {
                "VUEO requires HTTPS addon manifest URLs."
            }

            val normalizedUrl = manifestUrl.trim()
            val json = JSONObject(httpClient.get(normalizedUrl))

            val id = json.optString("id")
                .trim()
                .takeIf(String::isNotBlank)
                ?: error("Manifest is missing addon id.")
            val name = json.optString("name")
                .trim()
                .takeIf(String::isNotBlank)
                ?: error("Manifest is missing addon name.")

            return StremioAddonExtension(
                descriptor = ExtensionDescriptor(
                    id = id,
                    name = name,
                    version = json.optString("version", "0.0.0"),
                    kind = ExtensionKind.STREMIO_ADDON,
                    baseUrl = normalizedUrl,
                    description = json.optString("description")
                        .trim()
                        .takeIf(String::isNotBlank),
                    resources = parseResources(json.optJSONArray("resources")),
                    types = json.optJSONArray("types").toStringSet(),
                    catalogs = parseCatalogs(json.optJSONArray("catalogs")),
                ),
                httpClient = httpClient,
            )
        }

        private fun parseResources(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()

            return buildSet {
                for (index in 0 until array.length()) {
                    when (val value = array.opt(index)) {
                        is String -> value.trim()
                            .takeIf(String::isNotBlank)
                            ?.let(::add)
                        is JSONObject -> value.optString("name")
                            .trim()
                            .takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                }
            }
        }

        private fun parseCatalogs(array: JSONArray?): List<CatalogDescriptor> {
            if (array == null) return emptyList()

            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val type = item.optString("type")
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val id = item.optString("id")
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?: return@mapNotNull null

                CatalogDescriptor(
                    type = type,
                    id = id,
                    name = item.optString("name")
                        .trim()
                        .takeIf(String::isNotBlank),
                    extras = parseCatalogExtras(item.optJSONArray("extra")),
                )
            }
        }

        private fun parseCatalogExtras(array: JSONArray?): List<CatalogExtraDescriptor> {
            if (array == null) return emptyList()

            return (0 until array.length()).mapNotNull { index ->
                when (val value = array.opt(index)) {
                    is String -> value.trim()
                        .takeIf(String::isNotBlank)
                        ?.let { CatalogExtraDescriptor(name = it) }
                    is JSONObject -> {
                        val name = value.optString("name")
                            .trim()
                            .takeIf(String::isNotBlank)
                            ?: return@mapNotNull null
                        CatalogExtraDescriptor(
                            name = name,
                            isRequired = value.optBoolean("isRequired", false),
                            options = value.optJSONArray("options").toStringList(),
                        )
                    }
                    else -> null
                }
            }
        }
    }
}

private fun JSONObject.toMediaItem(sourceId: String): MediaItem? {
    val id = optString("id").trim().takeIf(String::isNotBlank) ?: return null
    val name = optString("name").trim().takeIf(String::isNotBlank) ?: return null

    return MediaItem(
        id = id,
        type = optString("type", "movie").trim().ifBlank { "movie" },
        name = name,
        poster = optString("poster").trim().takeIf { it.startsWith("https://", true) },
        background = optString("background").trim().takeIf { it.startsWith("https://", true) },
        description = optString("description").trim().takeIf(String::isNotBlank),
        releaseInfo = optString("releaseInfo").trim().takeIf(String::isNotBlank),
        originalLanguage = listOf(
            "originalLanguage",
            "original_language",
            "language",
        ).firstNotNullOfOrNull { field ->
            optString(field).trim().takeIf(String::isNotBlank)
        },
        genres = optJSONArray("genres").toStringList(),
        episodes = optJSONArray("videos").toEpisodeList(),
        sourceExtensionId = sourceId,
        imdbRating = optFlexibleDouble("imdbRating", "imdb_rating"),
        runtimeMinutes = optRuntimeMinutes(),
        certification = optCertification(),
        directors = optFlexibleStrings("director", "directors"),
        creators = optFlexibleStrings("creator", "creators"),
        writers = optFlexibleStrings("writer", "writers"),
        cast = optFlexibleStrings("cast").map { MediaPerson(name = it) },
    )
}

private fun JSONObject.optFlexibleDouble(vararg keys: String): Double? {
    for (key in keys) {
        val parsed = when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }
        if (parsed != null && parsed.isFinite() && parsed > 0.0) return parsed
    }
    return null
}

private fun JSONObject.optFlexibleStrings(vararg keys: String): List<String> =
    buildList {
        keys.forEach { key ->
            when (val raw = opt(key)) {
                is JSONArray -> {
                    for (index in 0 until raw.length()) {
                        when (val entry = raw.opt(index)) {
                            is JSONObject -> entry.optString("name")
                                .trim()
                                .takeIf(String::isNotBlank)
                                ?.let(::add)
                            else -> entry?.toString()
                                ?.trim()
                                ?.takeIf(String::isNotBlank)
                                ?.let(::add)
                        }
                    }
                }
                is String -> raw.split(",")
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .forEach(::add)
            }
        }
    }.distinctBy { it.lowercase() }

private fun JSONObject.optRuntimeMinutes(): Int? {
    val raw = opt("runtime") ?: return null
    if (raw is Number) return raw.toInt().takeIf { it > 0 }

    val text = raw.toString().trim().lowercase()
    if (text.isBlank()) return null

    val hours = Regex("""(\d+)\s*h""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0
    val minutes = Regex("""(\d+)\s*(?:m|min)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0
    val total = hours * 60 + minutes
    if (total > 0) return total

    return Regex("""\d+""")
        .find(text)
        ?.value
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun JSONObject.optCertification(): String? =
    listOf(
        "certification",
        "ageRating",
        "age_rating",
        "rated",
        "contentRating",
        "content_rating",
    )
        .asSequence()
        .map { optString(it).trim() }
        .firstOrNull(String::isNotBlank)

private fun JSONArray?.toEpisodeList(): List<EpisodeItem> {
    if (this == null) return emptyList()

    val parsed = (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id").trim().takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        val idParts = id.split(":")
        val idSeason = idParts.getOrNull(idParts.lastIndex - 1)?.toIntOrNull()
        val idEpisode = idParts.lastOrNull()?.toIntOrNull()
        val rawSeason = item.optInt("season", -1)
        val rawEpisode = item.optInt("episode", -1)
        val season = when {
            rawSeason > 0 -> rawSeason
            idSeason != null && idSeason > 0 -> idSeason
            else -> rawSeason
        }
        val episode = when {
            rawEpisode > 0 -> rawEpisode
            idEpisode != null && idEpisode > 0 -> idEpisode
            else -> rawEpisode
        }
        if (season < 0 || episode < 0) return@mapNotNull null

        EpisodeItem(
            id = id,
            title = item.optString("title")
                .trim()
                .takeIf(String::isNotBlank)
                ?: item.optString("name")
                    .trim()
                    .takeIf(String::isNotBlank)
                ?: "Episode $episode",
            season = season,
            episode = episode,
            released = item.optString("released").trim().takeIf(String::isNotBlank),
            overview = item.optString("overview").trim().takeIf(String::isNotBlank)
                ?: item.optString("description").trim().takeIf(String::isNotBlank),
            thumbnail = item.optString("thumbnail")
                .trim()
                .takeIf { it.startsWith("https://", true) },
        )
    }

    val normalized = if (
        parsed.isNotEmpty() &&
        parsed.none { it.season > 0 } &&
        parsed.all { it.season == 0 }
    ) {
        parsed.map { it.copy(season = 1) }
    } else {
        parsed
    }

    return normalized.sortedWith(
        compareBy<EpisodeItem> { it.season }
            .thenBy { it.episode },
    )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { index -> optString(index).trim().takeIf(String::isNotBlank) }
}

private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

private fun encode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        .replace("+", "%20")

private fun encodeExtras(extras: Map<String, String>): String {
    if (extras.isEmpty()) return ""
    return extras.entries
        .joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        .let { "/$it" }
}
