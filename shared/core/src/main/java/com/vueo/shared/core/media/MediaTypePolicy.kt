package com.vueo.shared.core.media

/** Canonical media-type handling shared by search and source discovery. */
object MediaTypePolicy {
    fun canonical(value: String?): String = when (value?.trim()?.lowercase().orEmpty()) {
        "tv", "show", "shows", "series" -> "series"
        "film", "films", "movies", "movie" -> "movie"
        else -> value?.trim()?.lowercase().orEmpty()
    }

    fun isSeries(value: String?): Boolean = canonical(value) == "series"

    fun isMovie(value: String?): Boolean = canonical(value) == "movie"

    fun pluginType(value: String?): String = if (isSeries(value)) "tv" else "movie"
}
