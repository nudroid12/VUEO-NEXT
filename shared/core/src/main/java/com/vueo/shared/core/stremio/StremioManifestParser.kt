package com.vueo.shared.core.stremio

import org.json.JSONArray
import org.json.JSONObject

object StremioManifestParser {
    fun parse(
        manifestUrl: String,
        payload: String,
    ): StremioManifest {
        require(manifestUrl.startsWith("https://", ignoreCase = true)) {
            "VUEO requires HTTPS addon manifest URLs."
        }

        val json = JSONObject(payload)
        val id = json.optString("id")
            .trim()
            .takeIf(String::isNotBlank)
            ?: error("Manifest is missing addon id.")
        val name = json.optString("name")
            .trim()
            .takeIf(String::isNotBlank)
            ?: error("Manifest is missing addon name.")

        return StremioManifest(
            id = id,
            name = name,
            version = json.optString("version", "0.0.0"),
            manifestUrl = manifestUrl,
            baseUrl = manifestUrl
                .removeSuffix("/manifest.json")
                .removeSuffix("manifest.json")
                .removeSuffix("/"),
            description = json.optString("description")
                .trim()
                .takeIf(String::isNotBlank),
            resources = parseResources(json.optJSONArray("resources")),
            types = json.optJSONArray("types").toStringSet(),
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

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            for (index in 0 until length()) {
                optString(index)
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }
}
