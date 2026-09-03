package com.vueo.app.core.enrichment

import android.net.Uri
import com.vueo.app.core.model.MediaCompany
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.model.MediaPerson
import com.vueo.app.core.plugin.TmdbResolver
import com.vueo.app.core.stremio.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject

object RichDetailsClient {
    private const val TMDB_BASE = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w342"

    suspend fun enrich(
        media: MediaItem,
        apiKey: String,
    ): MediaItem {
        if (apiKey.isBlank()) return media

        val tmdbId =
            resolveTmdbId(
                media = media,
                apiKey = apiKey,
            ) ?: return media

        val isSeries = media.type == "series"
        val endpoint = if (isSeries) "tv" else "movie"
        val append = if (isSeries) {
            "credits,content_ratings"
        } else {
            "credits,release_dates"
        }
        val url =
            "$TMDB_BASE/$endpoint/${Uri.encode(tmdbId)}" +
                "?api_key=${Uri.encode(apiKey.trim())}" +
                "&append_to_response=$append"

        val json = JSONObject(SimpleHttp.get(url))
        val credits = json.optJSONObject("credits")

        val tmdbCast = credits
            ?.optJSONArray("cast")
            .toPeople()
            .take(20)

        val crew = credits?.optJSONArray("crew")
        val tmdbDirectors = crew.namesForJobs(setOf("Director"))
        val tmdbWriters = crew.namesForJobs(
            setOf(
                "Writer",
                "Screenplay",
                "Story",
                "Teleplay",
            )
        )
        val tmdbCreators = json
            .optJSONArray("created_by")
            .toNameList()

        val companies = json
            .optJSONArray("production_companies")
            .toCompanies()

        val networks = if (isSeries) {
            json.optJSONArray("networks").toCompanies()
        } else {
            emptyList()
        }

        val runtime = if (isSeries) {
            json.optJSONArray("episode_run_time")
                ?.firstPositiveInt()
                ?: json.optJSONObject("last_episode_to_air")
                    ?.optInt("runtime", 0)
                    ?.takeIf { it > 0 }
        } else {
            json.optInt("runtime", 0)
                .takeIf { it > 0 }
        }

        val certification = if (isSeries) {
            json.optJSONObject("content_ratings")
                ?.optJSONArray("results")
                .preferredCertification("rating")
        } else {
            json.optJSONObject("release_dates")
                ?.optJSONArray("results")
                .preferredMovieCertification()
        }

        val tmdbRating = json
            .optDouble("vote_average", Double.NaN)
            .takeIf { it.isFinite() && it > 0.0 }

        return media.copy(
            tmdbRating = tmdbRating ?: media.tmdbRating,
            runtimeMinutes = media.runtimeMinutes ?: runtime,
            certification = media.certification ?: certification,
            directors = media.directors.ifEmpty { tmdbDirectors },
            creators = media.creators.ifEmpty { tmdbCreators },
            writers = media.writers.ifEmpty { tmdbWriters },
            cast = mergeCast(media.cast, tmdbCast),
            productionCompanies =
                media.productionCompanies.ifEmpty { companies },
            networks = media.networks.ifEmpty { networks },
        )
    }

    private suspend fun resolveTmdbId(
        media: MediaItem,
        apiKey: String,
    ): String? {
        TmdbResolver.resolve(
            media = media,
            apiKey = apiKey,
        )?.let {
            return it
        }

        val embeddedImdbId =
            Regex("""tt\d{5,}""")
                .find(media.id)
                ?.value

        if (embeddedImdbId != null) {
            TmdbResolver.resolve(
                media =
                    media.copy(
                        id = embeddedImdbId
                    ),
                apiKey = apiKey,
            )?.let {
                return it
            }
        }

        val query =
            media.name
                .trim()
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val isSeries =
            media.type == "series"

        val namespace =
            if (isSeries) {
                "tv"
            } else {
                "movie"
            }

        val year =
            media.releaseInfo
                ?.let {
                    Regex("""\b(19|20)\d{2}\b""")
                        .find(it)
                        ?.value
                }

        val yearParameter =
            if (year == null) {
                ""
            } else if (isSeries) {
                "&first_air_date_year=" +
                    Uri.encode(year)
            } else {
                "&year=" +
                    Uri.encode(year)
            }

        val url =
            "$TMDB_BASE/search/$namespace" +
                "?api_key=${Uri.encode(apiKey.trim())}" +
                "&query=${Uri.encode(query)}" +
                yearParameter

        val results =
            runCatching {
                JSONObject(
                    SimpleHttp.get(url)
                ).optJSONArray("results")
            }.getOrNull()
                ?: return null

        if (results.length() == 0) {
            return null
        }

        val normalizedQuery =
            normalizeTitle(query)

        val titleField =
            if (isSeries) {
                "name"
            } else {
                "title"
            }

        val dateField =
            if (isSeries) {
                "first_air_date"
            } else {
                "release_date"
            }

        val best =
            (0 until results.length())
                .mapNotNull { index ->
                    results.optJSONObject(index)
                }
                .maxByOrNull { candidate ->
                    val candidateTitle =
                        normalizeTitle(
                            candidate.optString(
                                titleField
                            )
                        )

                    val titleScore =
                        when {
                            candidateTitle ==
                                normalizedQuery -> 100
                            candidateTitle.contains(
                                normalizedQuery
                            ) ||
                                normalizedQuery.contains(
                                    candidateTitle
                                ) -> 55
                            else -> 0
                        }

                    val candidateYear =
                        candidate.optString(
                            dateField
                        ).take(4)

                    val yearScore =
                        if (
                            year != null &&
                            candidateYear == year
                        ) {
                            25
                        } else {
                            0
                        }

                    val popularityScore =
                        candidate
                            .optDouble(
                                "popularity",
                                0.0,
                            )
                            .coerceAtMost(100.0)
                            .toInt() / 10

                    titleScore +
                        yearScore +
                        popularityScore
                }
                ?: return null

        return best
            .optLong("id", -1L)
            .takeIf {
                it > 0L
            }
            ?.toString()
    }

    private fun normalizeTitle(
        value: String,
    ): String =
        value.lowercase()
            .replace(
                Regex("""[^a-z0-9]+"""),
                "",
            )

    private fun mergeCast(
        existing: List<MediaPerson>,
        enriched: List<MediaPerson>,
    ): List<MediaPerson> {
        if (enriched.isEmpty()) return existing
        if (existing.isEmpty()) return enriched

        val existingByName = existing.associateBy {
            it.name.trim().lowercase()
        }
        return buildList {
            enriched.forEach { person ->
                val old = existingByName[person.name.trim().lowercase()]
                add(
                    person.copy(
                        character = person.character ?: old?.character,
                        role = person.role ?: old?.role,
                        profile = person.profile ?: old?.profile,
                    )
                )
            }
            val enrichedNames = enriched
                .map { it.name.trim().lowercase() }
                .toSet()
            existing
                .filterNot { it.name.trim().lowercase() in enrichedNames }
                .forEach(::add)
        }.take(24)
    }

    private fun JSONArray?.toPeople(): List<MediaPerson> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    MediaPerson(
                        name = name,
                        character = item.optString("character")
                            .trim()
                            .takeIf { it.isNotBlank() },
                        profile = item.optString("profile_path")
                            .trim()
                            .takeIf { it.startsWith("/") }
                            ?.let { "$IMAGE_BASE$it" },
                    )
                )
            }
        }
    }

    private fun JSONArray?.toCompanies(): List<MediaCompany> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val name = item.optString("name").trim()
                if (name.isBlank()) continue
                add(
                    MediaCompany(
                        name = name,
                        logo = item.optString("logo_path")
                            .trim()
                            .takeIf { it.startsWith("/") }
                            ?.let { "$IMAGE_BASE$it" },
                    )
                )
            }
        }.distinctBy { it.name.lowercase() }
    }

    private fun JSONArray?.toNameList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)
                    ?.optString("name")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.distinct()
    }

    private fun JSONArray?.namesForJobs(
        jobs: Set<String>,
    ): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                if (item.optString("job") !in jobs) continue
                item.optString("name")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.distinct()
    }

    private fun JSONArray.firstPositiveInt(): Int? {
        for (index in 0 until length()) {
            val value = optInt(index, 0)
            if (value > 0) return value
        }
        return null
    }

    private fun JSONArray?.preferredCertification(
        field: String,
    ): String? {
        if (this == null) return null
        val entries = (0 until length())
            .mapNotNull { index -> optJSONObject(index) }
        return entries
            .firstOrNull { it.optString("iso_3166_1") == "US" }
            ?.optString(field)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: entries
                .asSequence()
                .map { it.optString(field).trim() }
                .firstOrNull { it.isNotBlank() }
    }

    private fun JSONArray?.preferredMovieCertification(): String? {
        if (this == null) return null
        val countries = (0 until length())
            .mapNotNull { index -> optJSONObject(index) }
        val preferred =
            countries.firstOrNull { it.optString("iso_3166_1") == "US" }
                ?: countries.firstOrNull()
                ?: return null
        val releases = preferred.optJSONArray("release_dates") ?: return null
        for (index in 0 until releases.length()) {
            val certification = releases
                .optJSONObject(index)
                ?.optString("certification")
                ?.trim()
                .orEmpty()
            if (certification.isNotBlank()) return certification
        }
        return null
    }
}
