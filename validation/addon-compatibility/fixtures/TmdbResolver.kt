package com.vueo.shared.core.plugin
import org.json.JSONObject
/** Fixture for unrelated metadata enrichment; addon transport/parser are real production code. */
object TmdbResolver { fun metadataId(json: JSONObject): String? = json.optString("tmdbId").takeIf { it.isNotBlank() } }
