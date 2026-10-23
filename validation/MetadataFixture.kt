package com.vueo.shared.core.plugin
import org.json.JSONObject
object TmdbResolver {fun metadataId(j:JSONObject):String?=j.optString("tmdbId").takeIf{it.isNotBlank() && it!="null"}}
