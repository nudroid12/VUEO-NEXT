package com.vueo.app.core.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VueoDataMigration {
    const val CURRENT_SCHEMA_VERSION = 2

    private const val PREFS_NAME = "vueo_data_migrations"
    private const val KEY_SCHEMA_VERSION = "schema_version"

    suspend fun migrateIfNeeded(
        context: Context,
    ) = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val migrationPrefs = appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        var version = migrationPrefs.getInt(
            KEY_SCHEMA_VERSION,
            0,
        )

        if (version < 1) {
            migrateTmdbKeyIfNeeded(appContext)
            version = 1
        }

        if (version < 2) {
            ProfileStore(
                appContext
            ).ensureDefaultProfile()
            version = 2
        }

        if (version != migrationPrefs.getInt(KEY_SCHEMA_VERSION, 0)) {
            check(
                migrationPrefs.edit()
                    .putInt(KEY_SCHEMA_VERSION, version)
                    .commit()
            ) {
                "Unable to persist VUEO data schema version."
            }
        }
    }

    private fun migrateTmdbKeyIfNeeded(
        context: Context,
    ) {
        val settings = context.getSharedPreferences(
            "vueo_settings",
            Context.MODE_PRIVATE,
        )
        val plugins = context.getSharedPreferences(
            "vueo_plugins",
            Context.MODE_PRIVATE,
        )

        val legacy = settings.getString(
            "tmdb_api_key",
            null,
        )?.trim().orEmpty()

        val current = plugins.getString(
            "tmdb_api_key",
            null,
        )?.trim().orEmpty()

        if (current.isBlank() && legacy.isNotBlank()) {
            plugins.edit()
                .putString("tmdb_api_key", legacy)
                .commit()
        }

        if (settings.contains("tmdb_api_key")) {
            settings.edit()
                .remove("tmdb_api_key")
                .commit()
        }
    }
}
