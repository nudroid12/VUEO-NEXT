package com.vueo.app.core.stremio

import android.net.Uri
import com.vueo.app.core.extensions.CatalogDescriptor
import com.vueo.app.core.extensions.CatalogExtraDescriptor
import com.vueo.app.core.extensions.ExtensionDescriptor
import com.vueo.app.core.extensions.ExtensionKind
import com.vueo.app.core.extensions.MediaExtension
import com.vueo.app.core.model.CatalogPage
import com.vueo.app.core.model.EpisodeItem
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.MediaPerson
import com.vueo.app.core.model.StreamSource
import com.vueo.app.core.model.SubtitleTrack
import org.json.JSONArray
import org.json.JSONObject

class StremioAddonProvider private constructor(
    override val descriptor: ExtensionDescriptor,
) : MediaExtension {

    private val base = descriptor.baseUrl
        .removeSuffix("/manifest.json")
        .removeSuffix("/")

    override suspend fun catalog(
        type: String,
        catalogId: String,
        extras: Map<String, String>,
    ): CatalogPage {
        val suffix = encodeExtras(extras)
        val url = "$base/catalog/${Uri.encode(type)}/${Uri.encode(catalogId)}$suffix.json"
        val json = JSONObject(SimpleHttp.get(url))
        val metas = json.optJSONArray("metas") ?: JSONArray()

        return CatalogPage(
            items = (0 until metas.length())
                .mapNotNull { metas.optJSONObject(it)?.toMediaItem(descriptor.id) }
        )
    }

    override suspend fun meta(type: String, id: String): MediaItem? {
        val url = "$base/meta/${Uri.encode(type)}/${Uri.encode(id)}.json"
        val json = JSONObject(SimpleHttp.get(url))
        return json.optJSONObject("meta")?.toMediaItem(descriptor.id)
    }

    override suspend fun streams(type: String, videoId: String): List<StreamSource> {
        val url = "$base/stream/${Uri.encode(type)}/${Uri.encode(videoId)}.json"
        val json = JSONObject(SimpleHttp.get(url))
        val streams = json.optJSONArray("streams") ?: JSONArray()

        return (0 until streams.length()).mapNotNull { index ->
            val item = streams.optJSONObject(index) ?: return@mapNotNull null
            val streamUrl = item.optString("url").takeIf { it.isNotBlank() }
            val infoHash = item.optString("infoHash").takeIf { it.isNotBlank() }

            if (streamUrl == null && infoHash == null) {
                return@mapNotNull null
            }

            val title = item.optString(
                "title",
                item.optString("name", descriptor.name),
            )

            StreamSource(
                name = title,
                url = streamUrl,
                infoHash = infoHash,
                fileIndex = item.optInt("fileIdx", -1).takeIf { it >= 0 },
                quality = inferQuality(title),
                codec = inferCodec(title),
                hdr = inferHdr(title),
                audio = item.optString("audio")
                    .takeIf { it.isNotBlank() },
                language = listOf(
                    "language",
                    "lang",
                    "audioLanguage",
                    "audio_language",
                ).firstNotNullOfOrNull { field ->
                    item.optString(field)
                        .trim()
                        .takeIf { it.isNotBlank() }
                },
                providerId = descriptor.id,
                providerName = descriptor.name,
            )
        }
    }

    override suspend fun subtitles(
        type: String,
        id: String,
        extras: Map<String, String>,
    ): List<SubtitleTrack> {
        val suffix = encodeExtras(extras)
        val url = "$base/subtitles/${Uri.encode(type)}/${Uri.encode(id)}$suffix.json"
        val json = JSONObject(SimpleHttp.get(url))
        val subtitles = json.optJSONArray("subtitles") ?: JSONArray()

        return (0 until subtitles.length()).mapNotNull { index ->
            val item = subtitles.optJSONObject(index) ?: return@mapNotNull null
            val subtitleUrl = item.optString("url")
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val subtitleLanguage =
                listOf(
                    "lang",
                    "language",
                    "languageCode",
                    "locale",
                    "label",
                )
                    .firstNotNullOfOrNull { field ->
                        item.optString(field)
                            .trim()
                            .takeIf { it.isNotBlank() }
                    }
                    ?: "und"

            SubtitleTrack(
                id = buildString {
                    append(descriptor.id)
                    append(":")
                    append(index)
                    append(":")
                    append(
                        item.optString(
                            "id",
                            subtitleLanguage,
                        )
                    )
                },
                language = subtitleLanguage,
                url = subtitleUrl,
                providerId = descriptor.id,
                providerName = descriptor.name,
                name = item.optString(
                    "title",
                    item.optString("name"),
                ).takeIf { it.isNotBlank() },
            )
        }
    }

    companion object {
        suspend fun fromManifestUrl(manifestUrl: String): StremioAddonProvider {
            require(manifestUrl.startsWith("https://")) {
                "VUEO requires HTTPS Stremio addon manifests."
            }

            val json = JSONObject(SimpleHttp.get(manifestUrl))

            val id = json.optString("id").takeIf { it.isNotBlank() }
                ?: error("Manifest is missing addon id.")
            val name = json.optString("name").takeIf { it.isNotBlank() }
                ?: error("Manifest is missing addon name.")

            val resources = parseResources(json.optJSONArray("resources"))
            val catalogs = parseCatalogs(json.optJSONArray("catalogs"))

            return StremioAddonProvider(
                descriptor = ExtensionDescriptor(
                    id = id,
                    name = name,
                    version = json.optString("version", "0.0.0"),
                    kind = ExtensionKind.STREMIO_ADDON,
                    baseUrl = manifestUrl,
                    description = json.optString("description").takeIf { it.isNotBlank() },
                    resources = resources,
                    types = json.optJSONArray("types").toStringSet(),
                    catalogs = catalogs,
                )
            )
        }

        private fun parseResources(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()

            return buildSet {
                for (i in 0 until array.length()) {
                    when (val value = array.opt(i)) {
                        is String -> add(value)
                        is JSONObject -> value.optString("name")
                            .takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
            }
        }

        private fun parseCatalogs(array: JSONArray?): List<CatalogDescriptor> {
            if (array == null) return emptyList()

            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val type = item.optString("type").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                CatalogDescriptor(
                    type = type,
                    id = id,
                    name = item.optString("name").takeIf { it.isNotBlank() },
                    extras = parseCatalogExtras(item.optJSONArray("extra")),
                )
            }
        }

        private fun parseCatalogExtras(array: JSONArray?): List<CatalogExtraDescriptor> {
            if (array == null) return emptyList()

            return (0 until array.length()).mapNotNull { index ->
                when (val value = array.opt(index)) {
                    is String -> CatalogExtraDescriptor(name = value)
                    is JSONObject -> {
                        val name = value.optString("name").takeIf { it.isNotBlank() }
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
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    val name = optString("name").takeIf { it.isNotBlank() } ?: return null

    return MediaItem(
        id = id,
        type = optString("type", "movie"),
        name = name,
        poster = optString("poster").takeIf { it.startsWith("https://") },
        background = optString("background").takeIf { it.startsWith("https://") },
        description = optString("description").takeIf { it.isNotBlank() },
        releaseInfo = optString("releaseInfo").takeIf { it.isNotBlank() },
        originalLanguage = listOf(
            "originalLanguage",
            "original_language",
            "language",
        ).firstNotNullOfOrNull { field ->
            optString(field)
                .trim()
                .takeIf { it.isNotBlank() }
        },
        genres = optJSONArray("genres").toStringList(),
        episodes = optJSONArray("videos").toEpisodeList(),
        sourceExtensionId = sourceId,
        imdbRating = optFlexibleDouble(
            "imdbRating",
            "imdb_rating",
        ),
        runtimeMinutes = optRuntimeMinutes(),
        certification = optCertification(),
        directors = optFlexibleStrings(
            "director",
            "directors",
        ),
        creators = optFlexibleStrings(
            "creator",
            "creators",
        ),
        writers = optFlexibleStrings(
            "writer",
            "writers",
        ),
        cast = optFlexibleStrings(
            "cast",
        ).map {
            MediaPerson(
                name = it
            )
        },
    )
}

private fun JSONObject.optFlexibleDouble(
    vararg keys: String,
): Double? {
    for (key in keys) {
        val value = opt(key)
        val parsed =
            when (value) {
                is Number -> value.toDouble()
                is String ->
                    value
                        .trim()
                        .replace(",", ".")
                        .toDoubleOrNull()
                else -> null
            }

        if (
            parsed != null &&
            parsed.isFinite() &&
            parsed > 0.0
        ) {
            return parsed
        }
    }

    return null
}

private fun JSONObject.optFlexibleStrings(
    vararg keys: String,
): List<String> {
    val values =
        buildList {
            keys.forEach { key ->
                when (val raw = opt(key)) {
                    is JSONArray -> {
                        for (index in 0 until raw.length()) {
                            when (val entry = raw.opt(index)) {
                                is JSONObject ->
                                    entry
                                        .optString("name")
                                        .trim()
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::add)
                                else ->
                                    entry
                                        ?.toString()
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let(::add)
                            }
                        }
                    }
                    is String ->
                        raw
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .forEach(::add)
                }
            }
        }

    return values.distinctBy { it.lowercase() }
}

private fun JSONObject.optRuntimeMinutes(): Int? {
    val raw = opt("runtime") ?: return null

    if (raw is Number) {
        return raw
            .toInt()
            .takeIf { it > 0 }
    }

    val text = raw.toString().trim().lowercase()
    if (text.isBlank()) return null

    val hours =
        Regex("""(\d+)\s*h""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

    val minutes =
        Regex("""(\d+)\s*(?:m|min)""")
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

private fun JSONObject.optCertification(): String? {
    val keys =
        listOf(
            "certification",
            "ageRating",
            "age_rating",
            "rated",
            "contentRating",
            "content_rating",
        )

    return keys
        .asSequence()
        .map { optString(it).trim() }
        .firstOrNull { it.isNotBlank() }
}

private fun JSONArray?.toEpisodeList(): List<EpisodeItem> {
    if (this == null) return emptyList()

    val parsed =
        (0 until length())
            .mapNotNull { index ->
                val item =
                    optJSONObject(index)
                        ?: return@mapNotNull null

                val id =
                    item.optString("id")
                        .takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                val idParts =
                    id.split(":")

                val idSeason =
                    idParts
                        .getOrNull(idParts.lastIndex - 1)
                        ?.toIntOrNull()

                val idEpisode =
                    idParts
                        .lastOrNull()
                        ?.toIntOrNull()

                val rawSeason =
                    item.optInt("season", -1)

                val rawEpisode =
                    item.optInt("episode", -1)

                val season =
                    when {
                        rawSeason > 0 -> rawSeason
                        idSeason != null &&
                            idSeason > 0 -> idSeason
                        else -> rawSeason
                    }

                val episode =
                    when {
                        rawEpisode > 0 -> rawEpisode
                        idEpisode != null &&
                            idEpisode > 0 -> idEpisode
                        else -> rawEpisode
                    }

                if (
                    season < 0 ||
                    episode < 0
                ) {
                    return@mapNotNull null
                }

                EpisodeItem(
                    id = id,
                    title =
                        item.optString("title")
                            .trim()
                            .takeIf { it.isNotBlank() }
                            ?: item.optString("name")
                                .trim()
                                .takeIf { it.isNotBlank() }
                            ?: "Episode $episode",
                    season = season,
                    episode = episode,
                    released =
                        item.optString("released")
                            .takeIf {
                                it.isNotBlank()
                            },
                    overview =
                        item.optString("overview")
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?: item
                                .optString("description")
                                .takeIf {
                                    it.isNotBlank()
                                },
                    thumbnail =
                        item.optString("thumbnail")
                            .takeIf {
                                it.startsWith("https://")
                            },
                )
            }

    val normalized =
        if (
            parsed.isNotEmpty() &&
            parsed.none { it.season > 0 } &&
            parsed.all { it.season == 0 }
        ) {
            parsed.map {
                it.copy(
                    season = 1
                )
            }
        } else {
            parsed
        }

    return normalized
        .sortedWith(
            compareBy<EpisodeItem> {
                it.season
            }.thenBy {
                it.episode
            }
        )
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length())
        .mapNotNull { optString(it).takeIf(String::isNotBlank) }
}

private fun JSONArray?.toStringSet(): Set<String> = toStringList().toSet()

private fun encodeExtras(extras: Map<String, String>): String {
    if (extras.isEmpty()) return ""
    val encoded = extras.entries.joinToString("&") {
        "${Uri.encode(it.key)}=${Uri.encode(it.value)}"
    }
    return "/$encoded"
}

private fun inferQuality(text: String): String? {
    val t = text.lowercase()
    return when {
        "2160" in t || "4k" in t -> "4K"
        "1080" in t -> "1080p"
        "720" in t -> "720p"
        "480" in t -> "480p"
        else -> null
    }
}

private fun inferCodec(text: String): String? {
    val t = text.lowercase()
    return when {
        "av1" in t -> "AV1"
        "hevc" in t || "h265" in t || "x265" in t -> "HEVC"
        "h264" in t || "x264" in t -> "H.264"
        else -> null
    }
}

private fun inferHdr(text: String): String? {
    val t = text.lowercase()
    return when {
        "dolby vision" in t || " dovi" in t || " dv " in t -> "Dolby Vision"
        "hdr10+" in t -> "HDR10+"
        "hdr" in t -> "HDR"
        else -> null
    }
}
