/* SPDX-License-Identifier: GPL-3.0-only
 * VUEO serializer for the adapted Nuvio collection/folder/catalog data model.
 */
package com.vueo.shared.core.collections

import org.json.JSONArray
import org.json.JSONObject

object CollectionJson {
    const val VERSION = 1
    const val MAX_BYTES = 1024 * 1024

    fun encode(collections: List<Collection>): String {
        CollectionPolicy.validate(collections)
        val root = JSONObject().put("format", "vueo-collections").put("version", VERSION)
        root.put("collections", JSONArray().apply {
            collections.forEach { c -> put(JSONObject().apply {
                put("id", c.id); put("title", c.title); put("enabled", c.enabled)
                put("viewMode", c.viewMode.name); put("showAllTab", c.showAllTab)
                put("folders", JSONArray().apply { c.folders.forEach { f -> put(JSONObject().apply {
                    put("id", f.id); put("title", f.title)
                    put("coverImageUrl", f.coverImageUrl ?: JSONObject.NULL)
                    put("coverEmoji", f.coverEmoji ?: JSONObject.NULL)
                    put("catalogSources", JSONArray().apply { f.catalogSources.forEach { s -> put(JSONObject().apply {
                        put("addonId", s.addonId); put("type", s.type); put("catalogId", s.catalogId)
                        put("genre", s.genre ?: JSONObject.NULL)
                    }) } })
                }) } })
            }) }
        })
        return root.toString(2).also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Collections exceed 1 MiB" } }
    }

    fun decode(text: String): List<Collection> {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Collections exceed 1 MiB" }
        val root = JSONObject(text)
        require(root.getString("format") == "vueo-collections" && root.getInt("version") == VERSION) { "Use a VUEO collections export (version 1)" }
        val array = root.getJSONArray("collections")
        val result = (0 until array.length()).map { i ->
            val c = array.getJSONObject(i)
            val folders = c.getJSONArray("folders")
            Collection(c.getString("id"), c.getString("title"), c.getBoolean("enabled"),
                FolderViewMode.valueOf(c.getString("viewMode")), c.getBoolean("showAllTab"),
                (0 until folders.length()).map { j ->
                    val f = folders.getJSONObject(j)
                    val sources = f.getJSONArray("catalogSources")
                    CollectionFolder(f.getString("id"), f.getString("title"), f.optional("coverImageUrl"), f.optional("coverEmoji"),
                        (0 until sources.length()).map { k -> sources.getJSONObject(k).let { s ->
                            CollectionCatalogSource(s.getString("addonId"), s.getString("type"), s.getString("catalogId"), s.optional("genre"))
                        } })
                })
        }
        CollectionPolicy.validate(result)
        return result
    }
    private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key).trim().takeIf { it.isNotEmpty() }
}
