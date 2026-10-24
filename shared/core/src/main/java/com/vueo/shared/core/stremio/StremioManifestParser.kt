package com.vueo.shared.core.stremio

import org.json.JSONArray
import org.json.JSONObject

object StremioManifestParser {
    fun parse(
        manifestUrl: String,
        payload: String,
    ): StremioManifest {
        val normalizedUrl = StremioUrls.normalizeManifest(manifestUrl)

        val json = JSONObject(payload)
        val id = json.optString("id")
            .trim()
            .takeIf(String::isNotBlank)
            ?: error("Manifest is missing addon id.")
        val name = json.optString("name")
            .trim()
            .takeIf(String::isNotBlank)
            ?: error("Manifest is missing addon name.")

        val behaviorHints = json.optJSONObject("behaviorHints")

        return StremioManifest(
            id = id,
            name = name,
            version = json.optString("version", "0.0.0"),
            manifestUrl = normalizedUrl,
            baseUrl = normalizedUrl.substringBefore("?").removeSuffix("/manifest.json"),
            description = json.optString("description")
                .trim()
                .takeIf(String::isNotBlank),
            resources = parseResources(json.optJSONArray("resources")),
            types = json.optJSONArray("types").toStringSet(),
            idPrefixes = json.optJSONArray("idPrefixes").toStringSet(),
            resourceDescriptors = parseResourceDescriptors(json),
            configurable = behaviorHints?.optBoolean("configurable", false) == true,
            configurationRequired = behaviorHints?.optBoolean("configurationRequired", false) == true,
        )
    }

    fun parseResourceDescriptors(json: JSONObject): List<StremioResource> {
        val types = json.optJSONArray("types").toStringSet()
        val prefixes = json.optJSONArray("idPrefixes").toStringSet()
        val resources = json.optJSONArray("resources") ?: return emptyList()
        return (0 until resources.length()).mapNotNull { index ->
            when (val entry = resources.opt(index)) {
                is String -> entry.trim().takeIf(String::isNotBlank)?.let { StremioResource(it, types, prefixes) }
                is JSONObject -> entry.optString("name").trim().takeIf(String::isNotBlank)?.let {
                    StremioResource(it, entry.optJSONArray("types").toStringSet().ifEmpty { types },
                        entry.optJSONArray("idPrefixes").toStringSet().ifEmpty { prefixes })
                }
                else -> null
            }
        }
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
