package com.vueo.shared.core.storage

import android.content.Context
import com.vueo.shared.core.media.EpisodeItem
import org.json.JSONArray
import org.json.JSONObject

/** Device-local next-episode index, stored once per title rather than inside every history entry. */
internal class LibraryEpisodeCache(context: Context) {
    private val prefs=context.getSharedPreferences("vueo_library_episode_index",Context.MODE_PRIVATE)
    fun save(type: String,id: String,episodes: List<EpisodeItem>) {
        if(episodes.isEmpty()) return
        val array=JSONArray(episodes.map { e -> JSONObject().put("id",e.id).put("title",e.title)
            .put("season",e.season).put("episode",e.episode).put("released",e.released) })
        save(type,id,array)
    }
    fun save(type: String,id: String,array: JSONArray) = synchronized(LOCK) {
        if(array.length()==0) return@synchronized
        val key="$type:$id"
        val previous=read(type,id)
        val incoming=decode(array)
        val merged=(incoming+previous).distinctBy { it.season to it.episode }
            .sortedWith(compareBy<EpisodeItem> { it.season }.thenBy { it.episode })
        if(merged==previous) return@synchronized
        val json=JSONArray(merged.map { e -> JSONObject().put("id",e.id).put("title",e.title)
            .put("season",e.season).put("episode",e.episode).put("released",e.released) }).toString()
        prefs.edit().putString(key,json).apply()
        cache[key]=merged
        while(cache.size>32) cache.remove(cache.keys.first())
    }
    fun read(type: String,id: String): List<EpisodeItem> = synchronized(LOCK) {
        val key="$type:$id"
        cache[key] ?: run {
            val values=runCatching { decode(JSONArray(prefs.getString(key,"[]"))) }.getOrDefault(emptyList())
            cache[key]=values
            while(cache.size>32) cache.remove(cache.keys.first())
            values
        }
    }
    private fun decode(array: JSONArray): List<EpisodeItem> = (0 until array.length()).mapNotNull { index ->
        val e=array.optJSONObject(index) ?: return@mapNotNull null
        val id=e.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        EpisodeItem(id=id,title=e.optString("title"),season=e.optInt("season"),episode=e.optInt("episode"),
            released=e.optString("released").takeIf { it.isNotBlank() && it!="null" })
    }
    companion object {
        private val LOCK=Any()
        // All LibraryStore instances share immutable lists, never one copy per played episode.
        private val cache=linkedMapOf<String,List<EpisodeItem>>()
    }
}
