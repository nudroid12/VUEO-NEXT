package com.vueo.shared.core.storage

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader

/** Saved items contain identity/display fields, never an episode catalogue or people metadata. */
object CompactLibraryCodec {
    private val mediaFields=setOf("id","type","name","poster","background","description","releaseInfo",
        "originalTitle","aliases","originalLanguage","countries","genres","sourceExtensionId","catalogSources",
        "imdbRating","tmdbRating","runtimeMinutes","certification","directors","creators","writers")

    fun readArray(raw: String, keepEpisodeIndex: Boolean=false, onEntry: (JSONObject)->Unit={}): List<JSONObject> = JsonReader(StringReader(raw)).use { reader ->
        val entries=mutableListOf<JSONObject>()
        reader.beginArray()
        while(reader.hasNext()) {
            val value=readValue(reader,keepEpisodeIndex=keepEpisodeIndex)
            if(value is JSONObject) { onEntry(value); entries+=value }
        }
        reader.endArray()
        check(reader.peek()==JsonToken.END_DOCUMENT)
        entries
    }

    /** Also used while streaming legacy journals; discarded metadata never becomes JSON objects. */
    fun readValue(reader: JsonReader, media: Boolean=false, depth: Int=0, keepEpisodeIndex: Boolean=false): Any {
        require(depth<40) { "Stored data is too deeply nested" }
        return when(reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while(reader.hasNext()) {
                    val name=reader.nextName()
                    if(media && name=="episodes" && keepEpisodeIndex && reader.peek()==JsonToken.BEGIN_ARRAY) put(name,readEpisodeIndex(reader))
                    else if(media && name !in mediaFields) reader.skipValue()
                    else put(name,readValue(reader,name=="media",depth+1,keepEpisodeIndex))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while(reader.hasNext()) put(readValue(reader,depth=depth+1,keepEpisodeIndex=keepEpisodeIndex))
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                val number=reader.nextString()
                number.toLongOrNull() ?: number.toDouble()
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> error("Invalid stored JSON")
        }
    }
    private fun readEpisodeIndex(reader: JsonReader): JSONArray = JSONArray().apply {
        reader.beginArray()
        while(reader.hasNext()) {
            if(reader.peek()!=JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            put(JSONObject().apply {
                reader.beginObject()
                while(reader.hasNext()) {
                    val name=reader.nextName()
                    if(name in setOf("id","title","season","episode","released")) put(name,readValue(reader))
                    else reader.skipValue()
                }
                reader.endObject()
            })
        }
        reader.endArray()
    }

    fun compactEntry(entry: JSONObject): JSONObject = JSONObject().apply {
        entry.keys().forEach { name ->
            val value=entry.get(name)
            put(name,if(name=="media" && value is JSONObject) compactMedia(value) else value)
        }
    }
    private fun compactMedia(media: JSONObject): JSONObject = JSONObject().apply {
        media.keys().forEach { name -> if(name in mediaFields) put(name,media.get(name)) }
    }
    fun compactRecord(row: JSONObject): JSONObject {
        if(row.optString("kind") !in setOf("favorites","history","progress")) return row
        val value=row.optJSONObject("value") ?: return row
        val entry=value.optJSONObject("entry") ?: return row
        return JSONObject().apply {
            row.keys().forEach { put(it,row.get(it)) }
            // Array position is not entity data. Library ordering uses saved/watched timestamps.
            put("value",JSONObject().put("entry",compactEntry(entry)))
        }
    }
}
