package com.vueo.shared.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.vueo.shared.core.extensions.CatalogDiscoveryCache
import com.vueo.shared.core.source.SourceDiscoveryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Shared VUEO backup/restore format for Mobile and TV.
 *
 * The backup contains only app preferences. Cache/runtime health state is intentionally
 * excluded and rebuilt after restore. API credentials are excluded unless explicitly requested.
 */
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
    const val CURRENT_SCHEMA_VERSION = 3

    private const val FORMAT = "vueo-backup"

    private val backupPreferenceFiles = listOf(
        "vueo_content_manager",
        "vueo_tv_content_manager",
        "vueo_plugins",
        "vueo_settings",
        "vueo_tv_settings",
        "vueo_library",
        "vueo_tv_library",
        "vueo_playback",
        "vueo_tv_playback",
        "vueo_profiles",
    )

    private val credentialKeys = mapOf(
        "vueo_plugins" to setOf(
            "tmdb_api_key",
        ),
        "vueo_settings" to setOf(
            "mdblist_api_key",
            "gemini_api_key",
        ),
        "vueo_tv_settings" to setOf(
            "mdblist_api_key",
            "gemini_api_key",
        ),
    )

    private val mobileToTvAliases = mapOf(
        "vueo_content_manager" to "vueo_tv_content_manager",
        "vueo_settings" to "vueo_tv_settings",
        "vueo_library" to "vueo_tv_library",
        "vueo_playback" to "vueo_tv_playback",
    )

    private val tvToMobileAliases = mobileToTvAliases.entries
        .associate { (mobile, tv) -> tv to mobile }

    private val resetPreferenceFiles = (
        backupPreferenceFiles +
            listOf(
                "vueo_plugin_health",
                "vueo_update_state",
                "vueo_tv_update_state",
                "vueo_tv_home",
                "vueo_tv_browse",
                "vueo_tv_search",
                "vueo_player_gestures",
                "vueo_player_subtitles",
            )
        ).distinct()

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        includeCredentials: Boolean,
    ): VueoBackupSummary = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val preferences = JSONObject()
        var valueCount = 0
        var groupCount = 0

        backupPreferenceFiles.forEach { name ->
            val prefs = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val group = JSONObject()
            val secrets = credentialKeys[name].orEmpty()

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

            if (group.length() > 0) {
                preferences.put(name, group)
                groupCount++
            }
        }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", CURRENT_SCHEMA_VERSION)
            .put("createdAtEpochMs", System.currentTimeMillis())
            .put("appPackage", appContext.packageName)
            .put("appVersion", appVersionName(appContext))
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

            val raw = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
        val sourcePackage = root.optString("appPackage")

        val restoreGroups = buildRestoreGroups(
            groups = groups,
            sourcePackage = sourcePackage,
            targetPackage = appContext.packageName,
        )

        var restoredGroups = 0
        var restoredValues = 0

        withContext(Dispatchers.IO) {
            restoreGroups.forEach { (name, source) ->
                val prefs = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)

                val preservedCredentials =
                    if (includesCredentials) {
                        emptyMap()
                    } else {
                        credentialKeys[name]
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
                    val encoded = source.optJSONObject(key) ?: continue

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
        ProfileStore(appContext).ensureDefaultProfile()

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
            resetPreferenceFiles.forEach { name ->
                check(
                    appContext
                        .getSharedPreferences(name, Context.MODE_PRIVATE)
                        .edit()
                        .clear()
                        .commit()
                ) {
                    "Unable to clear $name preferences."
                }
            }

            runCatching {
                File(appContext.filesDir, "nuvio_plugin_scrapers")
                    .deleteRecursively()
            }

            runCatching {
                appContext.cacheDir
                    .listFiles()
                    ?.forEach { it.deleteRecursively() }
            }
        }

        CatalogDiscoveryCache.clearAll(appContext)
        SourceDiscoveryCache.clearAll()
        ProfileStore(appContext).ensureDefaultProfile()
    }

    private fun buildRestoreGroups(
        groups: JSONObject,
        sourcePackage: String,
        targetPackage: String,
    ): LinkedHashMap<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        val keys = groups.keys()

        while (keys.hasNext()) {
            val name = keys.next()
            groups.optJSONObject(name)?.let { result[name] = it }
        }

        val aliases = when {
            targetPackage == "com.vueo.tv" && sourcePackage != "com.vueo.tv" -> mobileToTvAliases
            targetPackage == "com.vueo.app" && sourcePackage == "com.vueo.tv" -> tvToMobileAliases
            else -> emptyMap()
        }

        aliases.forEach { (sourceName, targetName) ->
            val source = result[sourceName]
            if (source != null && targetName !in result) {
                result[targetName] = source
            }
        }

        return result
    }

    private fun appVersionName(context: Context): String? =
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
        }.getOrNull()

    private fun encodePreferenceValue(value: Any?): JSONObject? =
        when (value) {
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
    ): Boolean =
        when (encoded.optString("type")) {
            "string" -> {
                editor.putString(key, encoded.optString("value"))
                true
            }

            "boolean" -> {
                editor.putBoolean(key, encoded.optBoolean("value"))
                true
            }

            "int" -> {
                editor.putInt(key, encoded.optInt("value"))
                true
            }

            "long" -> {
                editor.putLong(key, encoded.optLong("value"))
                true
            }

            "float" -> {
                editor.putFloat(key, encoded.optDouble("value").toFloat())
                true
            }

            "string_set" -> {
                val array = encoded.optJSONArray("value") ?: JSONArray()
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
