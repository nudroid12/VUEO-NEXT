package com.vueo.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.vueo.app.BuildConfig
import com.vueo.app.core.extensions.CatalogDiscoveryCache
import com.vueo.app.core.extensions.SourceDiscoveryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VueoBackupSummary(
    val preferenceGroups: Int,
    val valueCount: Int,
    val includesCredentials: Boolean,
)

data class VueoRestoreSummary(
    val preferenceGroups: Int,
    val valueCount: Int,
    val includedCredentials: Boolean,
    val sourceVersion: String?,
)

object VueoBackupManager {
    const val CURRENT_SCHEMA_VERSION = 2

    private const val FORMAT = "vueo-backup"

    private val BACKUP_PREFERENCE_FILES = listOf(
        "vueo_content_manager",
        "vueo_plugins",
        "vueo_settings",
        "vueo_library",
        "vueo_playback",
        "vueo_profiles",
    )

    private val CREDENTIAL_KEYS = mapOf(
        "vueo_plugins" to setOf(
            "tmdb_api_key",
        ),
        "vueo_settings" to setOf(
            "mdblist_api_key",
            "gemini_api_key",
        ),
    )

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        includeCredentials: Boolean,
    ): VueoBackupSummary = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val preferences = JSONObject()
        var valueCount = 0
        var groupCount = 0

        BACKUP_PREFERENCE_FILES.forEach { name ->
            val prefs = appContext.getSharedPreferences(
                name,
                Context.MODE_PRIVATE,
            )
            val group = JSONObject()
            val secrets = CREDENTIAL_KEYS[name].orEmpty()

            prefs.all
                .toSortedMap()
                .forEach valueLoop@ { (key, value) ->
                    if (!includeCredentials && key in secrets) {
                        return@valueLoop
                    }

                    encodePreferenceValue(value)
                        ?.let { encoded ->
                            group.put(key, encoded)
                            valueCount++
                        }
                }

            preferences.put(name, group)
            groupCount++
        }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", CURRENT_SCHEMA_VERSION)
            .put("createdAtEpochMs", System.currentTimeMillis())
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appVersionCode", BuildConfig.VERSION_CODE)
            .put("includesCredentials", includeCredentials)
            .put("preferences", preferences)

        val output = appContext.contentResolver
            .openOutputStream(uri)
            ?: error("Unable to open the selected backup destination.")

        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(root.toString(2))
        }

        VueoBackupSummary(
            preferenceGroups = groupCount,
            valueCount = valueCount,
            includesCredentials = includeCredentials,
        )
    }

    suspend fun restoreFromUri(
        context: Context,
        uri: Uri,
    ): VueoRestoreSummary {
        val appContext = context.applicationContext

        val decoded = withContext(Dispatchers.IO) {
            val input = appContext.contentResolver
                .openInputStream(uri)
                ?: error("Unable to open the selected VUEO backup.")

            val raw = input.bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }

            val root = JSONObject(raw)
            require(root.optString("format") == FORMAT) {
                "This file is not a VUEO backup."
            }

            val schema = root.optInt("schemaVersion", -1)
            require(schema in 1..CURRENT_SCHEMA_VERSION) {
                "Unsupported VUEO backup schema $schema."
            }

            val groups = root.optJSONObject("preferences")
                ?: error("Backup preferences are missing.")

            Triple(
                root,
                groups,
                root.optBoolean("includesCredentials", false),
            )
        }

        val root = decoded.first
        val groups = decoded.second
        val includesCredentials = decoded.third

        var restoredGroups = 0
        var restoredValues = 0

        withContext(Dispatchers.IO) {
            BACKUP_PREFERENCE_FILES.forEach { name ->
                val source = groups.optJSONObject(name)
                    ?: return@forEach

                val prefs = appContext.getSharedPreferences(
                    name,
                    Context.MODE_PRIVATE,
                )

                val preservedCredentials =
                    if (includesCredentials) {
                        emptyMap()
                    } else {
                        CREDENTIAL_KEYS[name]
                            .orEmpty()
                            .mapNotNull { key ->
                                prefs.getString(key, null)
                                    ?.let { value -> key to value }
                            }
                            .toMap()
                    }

                val editor = prefs.edit().clear()
                val keys = source.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val encoded = source.optJSONObject(key)
                        ?: continue

                    if (restorePreferenceValue(editor, key, encoded)) {
                        restoredValues++
                    }
                }

                preservedCredentials.forEach { (key, value) ->
                    if (!source.has(key)) {
                        editor.putString(key, value)
                    }
                }

                check(editor.commit()) {
                    "Unable to restore $name preferences."
                }
                restoredGroups++
            }

            appContext.getSharedPreferences(
                "vueo_plugin_health",
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }

        CatalogDiscoveryCache.clearAll(appContext)
        SourceDiscoveryCache.clearAll()
        VueoDataMigration.migrateIfNeeded(appContext)

        return VueoRestoreSummary(
            preferenceGroups = restoredGroups,
            valueCount = restoredValues,
            includedCredentials = includesCredentials,
            sourceVersion = root.optString("appVersion")
                .takeIf { it.isNotBlank() },
        )
    }

    suspend fun resetUserData(
        context: Context,
    ) {
        val appContext = context.applicationContext

        withContext(Dispatchers.IO) {
            (
                BACKUP_PREFERENCE_FILES +
                    listOf(
                        "vueo_plugin_health",
                        "vueo_update_state",
                    )
            ).distinct().forEach { name ->
                appContext.getSharedPreferences(
                    name,
                    Context.MODE_PRIVATE,
                ).edit().clear().commit()
            }

            runCatching {
                File(
                    appContext.filesDir,
                    "nuvio_plugin_scrapers",
                ).deleteRecursively()
            }

            runCatching {
                appContext.cacheDir
                    .listFiles()
                    ?.forEach { it.deleteRecursively() }
            }
        }

        CatalogDiscoveryCache.clearAll(appContext)
        SourceDiscoveryCache.clearAll()
    }

    private fun encodePreferenceValue(
        value: Any?,
    ): JSONObject? = when (value) {
        is String -> JSONObject()
            .put("type", "string")
            .put("value", value)

        is Boolean -> JSONObject()
            .put("type", "boolean")
            .put("value", value)

        is Int -> JSONObject()
            .put("type", "int")
            .put("value", value)

        is Long -> JSONObject()
            .put("type", "long")
            .put("value", value)

        is Float -> JSONObject()
            .put("type", "float")
            .put("value", value.toDouble())

        is Set<*> -> JSONObject()
            .put("type", "string_set")
            .put(
                "value",
                JSONArray(
                    value
                        .filterIsInstance<String>()
                        .sorted()
                ),
            )

        else -> null
    }

    private fun restorePreferenceValue(
        editor: SharedPreferences.Editor,
        key: String,
        encoded: JSONObject,
    ): Boolean {
        return when (encoded.optString("type")) {
            "string" -> {
                editor.putString(
                    key,
                    encoded.optString("value"),
                )
                true
            }

            "boolean" -> {
                editor.putBoolean(
                    key,
                    encoded.optBoolean("value"),
                )
                true
            }

            "int" -> {
                editor.putInt(
                    key,
                    encoded.optInt("value"),
                )
                true
            }

            "long" -> {
                editor.putLong(
                    key,
                    encoded.optLong("value"),
                )
                true
            }

            "float" -> {
                editor.putFloat(
                    key,
                    encoded.optDouble("value").toFloat(),
                )
                true
            }

            "string_set" -> {
                val array = encoded.optJSONArray("value")
                    ?: JSONArray()
                val values = buildSet {
                    for (index in 0 until array.length()) {
                        array.optString(index)
                            .takeIf { it.isNotBlank() }
                            ?.let(::add)
                    }
                }
                editor.putStringSet(key, values)
                true
            }

            else -> false
        }
    }
}
