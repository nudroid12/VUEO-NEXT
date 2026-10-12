package com.vueo.shared.core.sync

import android.content.Context
import com.vueo.shared.core.plugin.PluginRuntimeCache
import com.vueo.shared.core.storage.PlaybackStore
import org.json.JSONArray
import org.json.JSONObject

/** Explicit allowlist: no UI, decoder, subtitle/audio, cache, logs or provider code. */
internal class SyncLocalAdapter(private val context: Context) {
    val files = listOf("vueo_profiles","vueo_library","vueo_plugins","vueo_content_manager","vueo_settings","vueo_trakt")
    private fun prefs(file: String) = context.getSharedPreferences(file,Context.MODE_PRIVATE)
    private fun key(file: String, profile: String, base: String, item: String = "") =
        JSONArray(listOf(file,profile,base,item)).toString()
    private fun scoped(profile: String, base: String) = if (profile == "default") base else "profile:$profile:$base"
    private fun split(raw: String): Pair<String,String> {
        if (!raw.startsWith("profile:")) return "default" to raw
        val end = raw.indexOf(':',8)
        if (end < 0) return "default" to raw
        return raw.substring(8,end) to raw.substring(end+1)
    }
    private fun record(kind: String, key: String, value: JSONObject) =
        JSONObject().put("kind",kind).put("key",key).put("value",value)
    private fun typed(value: Any?): JSONObject = when (value) {
        is Boolean -> JSONObject().put("t","b").put("v",value)
        is Int -> JSONObject().put("t","i").put("v",value)
        is Long -> JSONObject().put("t","l").put("v",value)
        else -> JSONObject().put("t","s").put("v",value.toString())
    }
    fun snapshot(): Map<String,JSONObject> {
        val result = linkedMapOf<String,JSONObject>()
        fun add(kind: String, k: String, value: JSONObject) { result[k] = record(kind,k,value) }
        val profiles = JSONArray(prefs("vueo_profiles").getString("profiles","[]"))
        for (i in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(i)
            add("profiles",key("vueo_profiles",profile.getString("id"),"profiles"),
                JSONObject().put("entry",profile).put("order",i))
        }
        fun entries(file: String, rawKey: String, kind: String, array: JSONArray, identity: (JSONObject)->String) {
            val (profile,base) = split(rawKey)
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                add(kind,key(file,profile,base,identity(entry)),JSONObject().put("entry",entry).put("order",i))
            }
        }
        prefs("vueo_library").all.forEach { (raw,value) ->
            val (_,base) = split(raw)
            val kind = when (base) {
                "watchlist" -> "favorites"
                "continue_watching", "dismissed_continue_watching" -> "progress"
                "history", "marked_watched" -> "history"
                else -> null
            } ?: return@forEach
            if (value is String) entries("vueo_library",raw,kind,JSONArray(value)) { e ->
                val m = e.getJSONObject("media")
                m.getString("type")+":"+m.getString("id")+if (base=="history") ":"+e.getString("videoId") else ""
            } else if (value is Set<*>) {
                val (profile,_) = split(raw)
                value.filterIsInstance<String>().sorted().forEach { item ->
                    add(kind,key("vueo_library",profile,base,item),JSONObject().put("member",item))
                }
            }
        }
        prefs("vueo_plugins").all.forEach { (base,value) ->
            if (base == "repositories_json" && value is String) {
                entries("vueo_plugins",base,"addons",JSONArray(value)) { it.getString("manifestUrl") }
            } else if (base == "tmdb_api_key") {
                add("integrations",key("vueo_plugins","default",base),typed(value))
            } else if (base == "plugins_enabled" || base.startsWith("repository_enabled:") || base.startsWith("provider:")) {
                add("addons",key("vueo_plugins","default",base),typed(value))
            }
        }
        prefs("vueo_content_manager").all.forEach { (base,value) ->
            val kind = if (base in setOf("catalog_order","random_catalog_order","disabled_catalog_keys")) "catalogs" else "addons"
            if (base in setOf("stremio_manifest_urls","disabled_catalog_keys") && value is Set<*>) {
                value.filterIsInstance<String>().sorted().forEach { item ->
                    add(kind,key("vueo_content_manager","default",base,item),JSONObject().put("member",item))
                }
            } else if (base in setOf("catalog_order","random_catalog_order") || base.startsWith("addon_enabled:")) {
                add(kind,key("vueo_content_manager","default",base),typed(value))
            }
        }
        prefs("vueo_settings").all.forEach { (raw,value) ->
            val (profile,base) = split(raw)
            if (base in INTEGRATION_KEYS) add("integrations",key("vueo_settings",profile,base),typed(value))
        }
        prefs("vueo_trakt").all.forEach { (raw,value) ->
            val (profile,base) = split(raw)
            if (base in TRAKT_KEYS) add("trakt",key("vueo_trakt",profile,base),typed(value))
        }
        return result
    }
    /** Merge only changed entities; never replaces the entire local library with a remote snapshot. */
    fun apply(remote: List<JSONObject>, before: Map<String,JSONObject>): Boolean {
        if (remote.isEmpty()) return false
        val current = snapshot().toMutableMap()
        val touched = linkedSetOf<Triple<String,String,String>>()
        remote.forEach { row ->
            val k = row.getString("key")
            // A UI/player write that arrived during the request stays local and enters the next outbox batch.
            if (current[k]?.optJSONObject("value")?.toString() != before[k]?.optJSONObject("value")?.toString()) return@forEach
            val parts = JSONArray(k)
            require(parts.length()==4)
            val file = parts.getString(0); val profile = parts.getString(1); val base = parts.getString(2)
            if (file !in files) return@forEach
            if (file=="vueo_profiles" && row.optBoolean("deleted")) {
                com.vueo.shared.core.storage.ProfileStore(context).deleteProfile(profile)
            }
            if (file=="vueo_library" && base=="continue_watching" && row.optBoolean("deleted")) {
                PlaybackStore(context).applyCloudTitleClear(profile,parts.getString(3))
            }
            if (row.optBoolean("deleted")) current.remove(k) else current[k] = row
            touched += Triple(file,profile,base)
        }
        touched.forEach { (file,profile,base) ->
            val target = prefs(file)
            val raw = if (file == "vueo_trakt") "profile:$profile:$base" else scoped(profile,base)
            val members = current.values.filter { row ->
                val a=JSONArray(row.getString("key")); a.getString(0)==file && a.getString(1)==profile && a.getString(2)==base
            }.map { it.getJSONObject("value") }
            val editor = target.edit()
            when (base) {
                "profiles" -> Unit // All profiles are reconstructed together below.
                "watchlist","history","continue_watching","repositories_json" -> {
                    val sorted = members.sortedBy { it.optInt("order") }
                    editor.putString(raw,JSONArray(sorted.map { it.getJSONObject("entry") }).toString())
                    if (file=="vueo_library") editor.remove(scoped(profile,"home_continue_watching_cache_v1"))
                }
                "stremio_manifest_urls","disabled_catalog_keys","dismissed_continue_watching","marked_watched" ->
                    editor.putStringSet(raw,members.map { it.getString("member") }.toSet())
                else -> {
                    val value=members.firstOrNull()
                    if (value==null) editor.remove(raw) else when(value.getString("t")) {
                        "b" -> editor.putBoolean(raw,value.getBoolean("v"))
                        "i" -> editor.putInt(raw,value.getInt("v"))
                        "l" -> editor.putLong(raw,value.getLong("v"))
                        "s" -> editor.putString(raw,value.getString("v"))
                        else -> error("Unsupported sync value")
                    }
                }
            }
            check(editor.commit()) { "Unable to apply local sync data" }
            if (file=="vueo_library" && base in setOf("dismissed_continue_watching","marked_watched")) {
                members.forEach { PlaybackStore(context).applyCloudTitleClear(profile,it.getString("member")) }
            }
            if (file=="vueo_library" && base=="continue_watching") {
                members.forEach { entry ->
                    val e=entry.getJSONObject("entry"); val m=e.getJSONObject("media")
                    PlaybackStore(context).applyCloudPosition(profile,
                        m.getString("type")+":"+m.getString("id")+":"+e.getString("videoId"),
                        e.optLong("positionMs"),e.optLong("durationMs"))
                }
            }
        }
        if (touched.any { it.first=="vueo_profiles" }) {
            val values=current.values.filter { it.optString("kind")=="profiles" }
                .map { it.getJSONObject("value") }.sortedBy { it.optInt("order") }.map { it.getJSONObject("entry") }
            if (values.isNotEmpty()) {
                val p=prefs("vueo_profiles"); val editor=p.edit().putString("profiles",JSONArray(values).toString())
                if (values.none { it.getString("id")==p.getString("active_profile_id","") })
                    editor.putString("active_profile_id",values.first().getString("id"))
                check(editor.commit())
            }
        }
        if (touched.any { it.first in setOf("vueo_plugins","vueo_content_manager") }) PluginRuntimeCache.clear()
        return touched.isNotEmpty()
    }
    companion object {
        private val INTEGRATION_KEYS=setOf("tmdb_metadata_enrichment","tmdb_recommendations","tmdb_similar_titles",
            "tmdb_artwork_enrichment","mdblist_api_key","mdblist_ratings","mdblist_imdb","mdblist_rotten_tomatoes",
            "mdblist_metacritic","mdblist_tmdb","mdblist_trakt")
        private val TRAKT_KEYS=setOf("discovery","sync_favorites","sync_history","sync_playback","activity_scrobble",
            "username")
    }
}
