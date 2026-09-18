package com.vueo.shared.core.extensions

import android.content.Context
import com.vueo.shared.core.media.CatalogRow
import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.sqrt

@kotlin.jvm.JvmName("catalogRowsToJson")
internal fun List<CatalogRow>
    .toJson(): JSONArray =
    JSONArray().also {
        array ->

        forEach { row ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        row.id,
                    )
                    .put(
                        "title",
                        row.title,
                    )
                    .put(
                        "providerName",
                        row.providerName,
                    )
                    .put(
                        "items",
                        row.items
                            .toJson(),
                    )
            )
        }
    }

@kotlin.jvm.JvmName("mediaItemsToJson")
internal fun List<MediaItem>
    .toJson(): JSONArray =
    JSONArray().also {
        array ->

        forEach { item ->
            array.put(
                JSONObject()
                    .put(
                        "id",
                        item.id,
                    )
                    .put(
                        "type",
                        item.type,
                    )
                    .put(
                        "name",
                        item.name,
                    )
                    .put(
                        "poster",
                        item.poster,
                    )
                    .put(
                        "background",
                        item.background,
                    )
                    .put(
                        "description",
                        item.description,
                    )
                    .put(
                        "releaseInfo",
                        item.releaseInfo,
                    )
                    .put(
                        "originalTitle",
                        item.originalTitle,
                    )
                    .put(
                        "aliases",
                        JSONArray(item.aliases),
                    )
                    .put(
                        "originalLanguage",
                        item.originalLanguage,
                    )
                    .put(
                        "countries",
                        JSONArray(item.countries),
                    )
                    .put(
                        "genres",
                        JSONArray(
                            item.genres
                        ),
                    )
                    .put(
                        "sourceExtensionId",
                        item.sourceExtensionId,
                    )
                    .put(
                        "catalogSources",
                        JSONArray(
                            item.catalogSources
                        ),
                    )
                    .put(
                        "imdbRating",
                        item.imdbRating,
                    )
                    .put(
                        "tmdbRating",
                        item.tmdbRating,
                    )
            )
        }
    }

internal fun JSONArray?
    .toCatalogRows():
    List<CatalogRow> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val row =
                optJSONObject(index)
                    ?: continue

            val id =
                row.optString("id")
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: continue

            val title =
                row.optString(
                    "title"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            val providerName =
                row.optString(
                    "providerName",
                    "Addon",
                )

            val items =
                row.optJSONArray(
                    "items"
                ).toMediaItems()

            if (
                items.isNotEmpty()
            ) {
                add(
                    CatalogRow(
                        id = id,
                        title = title,
                        providerName =
                            providerName,
                        items = items,
                    )
                )
            }
        }
    }
}

internal fun JSONArray?
    .toMediaItems():
    List<MediaItem> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            val json =
                optJSONObject(index)
                    ?: continue

            val id =
                json.optString(
                    "id"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            val name =
                json.optString(
                    "name"
                ).takeIf {
                    it.isNotBlank()
                }
                    ?: continue

            add(
                MediaItem(
                    id = id,
                    type =
                        json.optString(
                            "type",
                            "movie",
                        ),
                    name = name,
                    poster =
                        json.optNullableString(
                            "poster"
                        ),
                    background =
                        json.optNullableString(
                            "background"
                        ),
                    description =
                        json.optNullableString(
                            "description"
                        ),
                    releaseInfo =
                        json.optNullableString(
                            "releaseInfo"
                        ),
                    originalTitle =
                        json.optNullableString(
                            "originalTitle"
                        ),
                    aliases =
                        json.optJSONArray(
                            "aliases"
                        ).toStringList(),
                    originalLanguage =
                        json.optNullableString(
                            "originalLanguage"
                        ),
                    countries =
                        json.optJSONArray(
                            "countries"
                        ).toStringList(),
                    genres =
                        json.optJSONArray(
                            "genres"
                        ).toStringList(),
                    sourceExtensionId =
                        json.optNullableString(
                            "sourceExtensionId"
                        ),
                    catalogSources =
                        json.optJSONArray(
                            "catalogSources"
                        ).toStringList(),
                    imdbRating =
                        json.optDouble(
                            "imdbRating",
                            Double.NaN,
                        ).takeIf {
                            it.isFinite() &&
                                it > 0.0
                        },
                    tmdbRating =
                        json.optDouble(
                            "tmdbRating",
                            Double.NaN,
                        ).takeIf {
                            it.isFinite() &&
                                it > 0.0
                        },
                )
            )
        }
    }
}

internal fun JSONObject
    .optNullableString(
        key: String,
    ): String? {
    if (
        !has(key) ||
        isNull(key)
    ) {
        return null
    }

    return optString(key)
        .takeIf {
            it.isNotBlank() &&
                it != "null"
        }
}

private fun JSONArray?
    .toStringList():
    List<String> {
    if (this == null) {
        return emptyList()
    }

    return buildList {
        for (
            index in
            0 until length()
        ) {
            optString(index)
                .takeIf {
                    it.isNotBlank()
                }
                ?.let(::add)
        }
    }
}
