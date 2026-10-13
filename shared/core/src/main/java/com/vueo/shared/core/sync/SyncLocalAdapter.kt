package com.vueo.shared.core.sync

import android.content.Context
import com.vueo.shared.core.plugin.PluginRuntimeCache
import com.vueo.shared.core.storage.PlaybackStore
import com.vueo.shared.core.storage.CompactLibraryCodec
import org.json.JSONArray
import org.json.JSONObject

/** Explicit allowlist: no UI, decoder, subtitle/audio, cache, logs or provider code. */
internal class SyncLocalAdapter(private val context: Context) {
    val files = listOf("vueo_profiles","vueo_library","vueo_plugins","vueo_content_manager","vueo_settings","vueo_trakt","vueo_smartsubs_sync_signal")
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
    fun preferenceCategories(file: String, rawKey: String?): Set<String> {
        if(rawKey==null) return VueoSyncConfig.categories.keys.toSet()
        val (_,base)=split(rawKey)
        val kind=when(file) {
            "vueo_profiles" -> if(base=="profiles") "profiles" else null
            "vueo_library" -> when(base) {
                "watchlist" -> "favorites"
                "continue_watching","dismissed_continue_watching" -> "progress"
                "history","marked_watched" -> "history"
                else -> null
            }
            "vueo_plugins" -> when(base) {
                "repositories_json" -> "addons"
                "tmdb_api_key" -> "integrations"
                else -> null
            }
            "vueo_content_manager" -> if(base=="stremio_manifest_urls") "addons" else null
            "vueo_settings" -> if(base in INTEGRATION_KEYS) "integrations" else null
            "vueo_trakt" -> if(base in TRAKT_KEYS) "trakt" else null
            "vueo_smartsubs_sync_signal" -> "integrations"
            else -> null
        }
        return kind?.let { setOf(it) }.orEmpty()
    }

    fun matchesScope(entityKey: String,scopes: Set<Pair<String,String?>>): Boolean {
        val parts=JSONArray(entityKey)
        return scopes.any { (file,raw) ->
            if(parts.getString(0)!=file) false
            else if(raw==null) true
            else {
                val (profile,base)=split(raw)
                parts.getString(2)==base && (file=="vueo_profiles" || parts.getString(1)==profile)
            }
        }
    }

    fun snapshot(kinds: Set<String> = VueoSyncConfig.categories.keys, scopes: Set<Pair<String,String?>>?=null): Map<String,JSONObject> {
        val result = linkedMapOf<String,JSONObject>()
        fun wants(file: String,raw: String)=scopes==null || scopes.any { it.first==file && (it.second==null || it.second==raw) }
        fun add(kind: String, k: String, value: JSONObject) { result[k] = record(kind,k,value) }
        if("profiles" in kinds && wants("vueo_profiles","profiles")) {
        val profiles = JSONArray(prefs("vueo_profiles").getString("profiles","[]"))
        for (i in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(i)
            add("profiles",key("vueo_profiles",profile.getString("id"),"profiles"),
                JSONObject().put("entry",profile).put("order",i))
        }
        }
        fun entries(file: String, rawKey: String, kind: String, array: JSONArray, identity: (JSONObject)->String) {
            val (profile,base) = split(rawKey)
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                add(kind,key(file,profile,base,identity(entry)),JSONObject().put("entry",entry).apply { if(file=="vueo_profiles") put("order",i) })
            }
        }
        if(kinds.any { it in setOf("favorites","history","progress") }) prefs("vueo_library").all.forEach { (raw,value) ->
            if(!wants("vueo_library",raw)) return@forEach
            val (_,base) = split(raw)
            val kind = when (base) {
                "watchlist" -> "favorites"
                "continue_watching", "dismissed_continue_watching" -> "progress"
                "history", "marked_watched" -> "history"
                else -> null
            } ?: return@forEach
            if(kind !in kinds) return@forEach
            if (value is String) entries("vueo_library",raw,kind,JSONArray(CompactLibraryCodec.readArray(value))) { e ->
                val m = e.getJSONObject("media")
                m.getString("type")+":"+m.getString("id")+if (base=="history") ":"+e.getString("videoId") else ""
            } else if (value is Set<*>) {
                val (profile,_) = split(raw)
                value.filterIsInstance<String>().sorted().forEach { item ->
                    add(kind,key("vueo_library",profile,base,item),JSONObject().put("member",item))
                }
            }
        }
        if("addons" in kinds || "integrations" in kinds) prefs("vueo_plugins").all.forEach { (base,value) ->
            if(!wants("vueo_plugins",base)) return@forEach
            if ("addons" in kinds && base == "repositories_json" && value is String) {
                entries("vueo_plugins",base,"addons",JSONArray(value)) { it.getString("manifestUrl") }
            } else if ("integrations" in kinds && base == "tmdb_api_key") {
                add("integrations",key("vueo_plugins","default",base),typed(value))
            }
        }
        if("addons" in kinds && wants("vueo_content_manager","stremio_manifest_urls")) prefs("vueo_content_manager").getStringSet("stremio_manifest_urls",emptySet())
            .orEmpty().sorted().forEach { item ->
                add("addons",key("vueo_content_manager","default","stremio_manifest_urls",item),JSONObject().put("member",item))
            }
        if("integrations" in kinds) prefs("vueo_settings").all.forEach { (raw,value) ->
            if(!wants("vueo_settings",raw)) return@forEach
            val (profile,base) = split(raw)
            if (base in INTEGRATION_KEYS) add("integrations",key("vueo_settings",profile,base),typed(value))
        }
        if ("integrations" in kinds && (scopes == null || scopes.any { it.first == "vueo_smartsubs_sync_signal" })) {
            val apiKey = com.vueo.shared.core.smartsubs.SmartSubsSettingsStore(context).apiKeyForSync()
            if (apiKey.isNotBlank()) add("integrations", key("vueo_smartsubs", "default", "gemini_api_key"), typed(apiKey))
        }
        if("trakt" in kinds) prefs("vueo_trakt").all.forEach { (raw,value) ->
            if(!wants("vueo_trakt",raw)) return@forEach
            val (profile,base) = split(raw)
            if (base in TRAKT_KEYS) add("trakt",key("vueo_trakt",profile,base),typed(value))
        }
        return result
    }
    /** Ignore legacy cloud switches/catalog records, including old pending outbox entries. */
    fun accepts(row: JSONObject): Boolean = runCatching {
        val a=JSONArray(row.getString("key"))
        if(a.length()!=4) return@runCatching false
        val file=a.getString(0); val base=a.getString(2); val kind=row.getString("kind")
        when(file) {
            "vueo_profiles" -> kind=="profiles" && base=="profiles"
            "vueo_library" -> when(base) {
                "watchlist" -> kind=="favorites"
                "continue_watching","dismissed_continue_watching" -> kind=="progress"
                "history","marked_watched" -> kind=="history"
                else -> false
            }
            "vueo_plugins" -> (base=="repositories_json" && kind=="addons") || (base=="tmdb_api_key" && kind=="integrations")
            "vueo_content_manager" -> base=="stremio_manifest_urls" && kind=="addons"
            "vueo_settings" -> kind=="integrations" && base in INTEGRATION_KEYS
            "vueo_trakt" -> kind=="trakt" && base in TRAKT_KEYS
            "vueo_smartsubs" -> kind=="integrations" && base=="gemini_api_key" && a.getString(1)=="default" && a.getString(3).isEmpty() && (row.optBoolean("deleted") || row.optJSONObject("value")?.optString("t")=="s")
            else -> false
        }
    }.getOrDefault(false)

    /** Merge only changed entities; never replaces the entire local library with a remote snapshot. */
    fun apply(remote: List<JSONObject>, before: Map<String,String?>): Set<String> = synchronized(com.vueo.shared.core.storage.LibraryStore.SYNC_STORAGE_LOCK) {
        if (remote.isEmpty()) return@synchronized emptySet()
        val current = snapshot(remote.map { it.getString("kind") }.toSet()).toMutableMap()
        val appliedKinds=linkedSetOf<String>()
        val touched = linkedSetOf<Triple<String,String,String>>()
        remote.forEach { row ->
            if(!accepts(row)) return@forEach
            val k = row.getString("key")
            // A UI/player write that arrived during the request stays local and enters the next outbox batch.
            if (syncFingerprint(current[k]?.optJSONObject("value")) != before[k]) return@forEach
            if(row.optBoolean("deleted") && current[k]==null) return@forEach
            if(!row.optBoolean("deleted") && current[k]?.optJSONObject("value")?.toString()==row.optJSONObject("value")?.toString()) return@forEach
            val parts = JSONArray(k)
            require(parts.length()==4)
            val file = parts.getString(0); val profile = parts.getString(1); val base = parts.getString(2)
            if (file !in files && file!="vueo_smartsubs") return@forEach
            if (file=="vueo_profiles" && row.optBoolean("deleted")) {
                com.vueo.shared.core.storage.ProfileStore(context).deleteProfile(profile)
            }
            if (file=="vueo_library" && base=="continue_watching" && row.optBoolean("deleted")) {
                PlaybackStore(context).applyCloudTitleClear(profile,parts.getString(3))
            }
            if (row.optBoolean("deleted")) current.remove(k) else current[k] = row
            touched += Triple(file,profile,base)
            appliedKinds+=row.getString("kind")
        }
        touched.forEach { (file,profile,base) ->
            if (file == "vueo_smartsubs") {
                val value = current[key(file, profile, base)]?.optJSONObject("value")?.optString("v").orEmpty()
                com.vueo.shared.core.smartsubs.SmartSubsSettingsStore(context).importApiKey(value)
                return@forEach
            }
            val target = prefs(file)
            val raw = if (file == "vueo_trakt") "profile:$profile:$base" else scoped(profile,base)
            val members = current.values.filter { row ->
                val a=JSONArray(row.getString("key")); a.getString(0)==file && a.getString(1)==profile && a.getString(2)==base
            }.map { it.getJSONObject("value") }
            val editor = target.edit()
            when (base) {
                "profiles" -> Unit // All profiles are reconstructed together below.
                "watchlist","history","continue_watching","repositories_json" -> {
                    val sorted = if(base=="repositories_json") members else members.sortedBy { it.optInt("order") }
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
        if ("addons" in appliedKinds) PluginRuntimeCache.clear()
        appliedKinds
    }
    companion object {
        private val INTEGRATION_KEYS=setOf("mdblist_api_key")
        private val TRAKT_KEYS=setOf("username")
    }
}
