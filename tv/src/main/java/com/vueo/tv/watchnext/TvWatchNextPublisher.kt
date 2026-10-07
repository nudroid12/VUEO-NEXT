package com.vueo.tv.watchnext

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.vueo.shared.core.storage.ContinueWatchingPolicy
import com.vueo.shared.core.storage.LibraryPlaybackEntry
import com.vueo.shared.core.storage.LibraryStore
import com.vueo.shared.core.storage.ProfileStore
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TvWatchNextDeepLink {
    private const val SCHEME = "vueo"
    private const val HOST = "watch-next"
    private const val ENTRY_PARAM = "entry"

    fun build(mediaKey: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter(ENTRY_PARAM, mediaKey)
            .build()

    fun parseMediaKey(uri: Uri?): String? =
        uri
            ?.takeIf {
                it.scheme?.equals(SCHEME, ignoreCase = true) == true &&
                    it.host?.equals(HOST, ignoreCase = true) == true
            }
            ?.getQueryParameter(ENTRY_PARAM)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}

/**
 * Mirrors VUEO's active-profile Continue Watching cursor into Android TV's
 * system-managed Watch Next / Play Next row.
 *
 * The publisher owns only rows whose internal provider ID starts with
 * [INTERNAL_ID_PREFIX]. Other Watch Next rows, including future VUEO features,
 * are left alone.
 */
class TvWatchNextPublisher(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val profileStore = ProfileStore(appContext)
    private val libraryStore = LibraryStore(
        context = appContext,
        profileStore = profileStore,
    )
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sync(force: Boolean = false) {
        synchronized(SYNC_LOCK) {
            syncLocked(force)
        }
    }

    private fun syncLocked(force: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        profileStore.ensureDefaultProfile()
        val profileId = libraryStore.activeContinueWatchingProfileId()
        val entries = libraryStore.continueWatching()
        val desired = entries.associateBy { internalProviderId(profileId, it) }
        val fingerprint = fingerprint(profileId, desired)

        if (!force && prefs.getString(KEY_LAST_FINGERPRINT, null) == fingerprint) {
            return
        }

        val resolver = appContext.contentResolver
        val existing = existingRows()
        var successful = true

        desired.forEach { (internalId, entry) ->
            val program = buildProgram(internalId, entry)
            val rows = existing[internalId].orEmpty()
            val primary = rows.firstOrNull()

            val published = runCatching {
                if (primary == null) {
                    resolver.insert(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI,
                        program.toContentValues(),
                    ) != null
                } else {
                    val updated = resolver.update(
                        TvContractCompat.buildWatchNextProgramUri(primary.id),
                        program.toContentValues(),
                        null,
                        null,
                    )
                    if (updated > 0) {
                        true
                    } else {
                        resolver.insert(
                            TvContractCompat.WatchNextPrograms.CONTENT_URI,
                            program.toContentValues(),
                        ) != null
                    }
                }
            }.getOrDefault(false)

            successful = successful && published

            // Clean duplicate rows from interrupted/older syncs.
            rows.drop(1).forEach { duplicate ->
                runCatching {
                    resolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(duplicate.id),
                        null,
                        null,
                    )
                }
            }
        }

        existing
            .filterKeys { it.startsWith(INTERNAL_ID_PREFIX) && it !in desired }
            .values
            .flatten()
            .forEach { stale ->
                val removed = runCatching {
                    resolver.delete(
                        TvContractCompat.buildWatchNextProgramUri(stale.id),
                        null,
                        null,
                    )
                    true
                }.getOrDefault(false)
                successful = successful && removed
            }

        if (successful) {
            prefs.edit().putString(KEY_LAST_FINGERPRINT, fingerprint).apply()
        }
    }

    private fun buildProgram(
        internalId: String,
        entry: LibraryPlaybackEntry,
    ): WatchNextProgram {
        val isSeries = ContinueWatchingPolicy.isSeries(entry.media)
        val isNextEpisode = isSeries && entry.positionMs <= 0L
        val watchNextType =
            if (isNextEpisode) {
                TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
            } else {
                TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
            }

        val builder = WatchNextProgram.Builder()
            .setType(
                if (isSeries) {
                    TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE
                } else {
                    TvContractCompat.PreviewProgramColumns.TYPE_MOVIE
                },
            )
            .setWatchNextType(watchNextType)
            .setLastEngagementTimeUtcMillis(entry.lastWatchedEpochMs.coerceAtLeast(1L))
            .setTitle(entry.media.name)
            .setDescription(entry.media.description)
            .setIntentUri(TvWatchNextDeepLink.build(entry.mediaKey))
            .setInternalProviderId(internalId)
            .setContentId(entry.mediaKey)

        entry.media.poster
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.setPosterArtUri(Uri.parse(it)) }

        if (!isNextEpisode && entry.durationMs > 0L) {
            builder
                .setLastPlaybackPositionMillis(entry.positionMs.toSafeInt())
                .setDurationMillis(entry.durationMs.toSafeInt())
        }

        if (isSeries) {
            entry.season?.takeIf { it > 0 }?.let { season ->
                builder.setSeasonNumber(season)
                builder.setSeasonTitle("Season $season")
            }
            entry.episode?.takeIf { it > 0 }?.let { episode ->
                builder.setEpisodeNumber(episode)
            }
            entry.episodeTitle
                ?.takeIf { it.isNotBlank() }
                ?.let(builder::setEpisodeTitle)
        }

        return builder.build()
    }

    private fun existingRows(): Map<String, List<ExistingRow>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyMap()

        fun query(selection: String?, args: Array<String>?): List<ExistingRow> {
            val rows = mutableListOf<ExistingRow>()
            appContext.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(COLUMN_ID, COLUMN_INTERNAL_PROVIDER_ID, COLUMN_PACKAGE_NAME),
                selection,
                args,
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
                val internalIdIndex = cursor.getColumnIndexOrThrow(COLUMN_INTERNAL_PROVIDER_ID)
                val packageIndex = cursor.getColumnIndex(COLUMN_PACKAGE_NAME)
                while (cursor.moveToNext()) {
                    if (
                        packageIndex >= 0 &&
                        cursor.getString(packageIndex) != appContext.packageName
                    ) {
                        continue
                    }
                    val internalId = cursor.getString(internalIdIndex) ?: continue
                    rows += ExistingRow(
                        id = cursor.getLong(idIndex),
                        internalId = internalId,
                    )
                }
            }
            return rows
        }

        val rows = runCatching {
            query(
                selection = "$COLUMN_PACKAGE_NAME = ?",
                args = arrayOf(appContext.packageName),
            )
        }.getOrElse {
            runCatching { query(selection = null, args = null) }.getOrDefault(emptyList())
        }
        return rows.groupBy { it.internalId }
    }

    private fun internalProviderId(
        profileId: String,
        entry: LibraryPlaybackEntry,
    ): String = "$INTERNAL_ID_PREFIX$profileId:${entry.mediaKey}"

    private fun fingerprint(
        profileId: String,
        desired: Map<String, LibraryPlaybackEntry>,
    ): String {
        val payload = buildString {
            append(profileId)
            append('\n')
            desired.toSortedMap().forEach { (internalId, entry) ->
                append(internalId)
                append('|')
                append(entry.positionMs)
                append('|')
                append(entry.durationMs)
                append('|')
                append(entry.lastWatchedEpochMs)
                append('|')
                append(entry.media.name)
                append('|')
                append(entry.media.poster.orEmpty())
                append('|')
                append(entry.media.description.orEmpty())
                append('|')
                append(entry.episodeTitle.orEmpty())
                append('|')
                append(entry.season ?: 0)
                append('|')
                append(entry.episode ?: 0)
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun Long.toSafeInt(): Int =
        coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private data class ExistingRow(
        val id: Long,
        val internalId: String,
    )

    companion object {
        private const val INTERNAL_ID_PREFIX = "vueo:watch-next:"
        private const val PREFS_NAME = "vueo_tv_watch_next"
        private const val KEY_LAST_FINGERPRINT = "last_fingerprint"
        private const val COLUMN_ID = "_id"
        private const val COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id"
        private const val COLUMN_PACKAGE_NAME = "package_name"
        private val SYNC_LOCK = Any()
        private val ASYNC_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun schedule(context: Context) {
            val appContext = context.applicationContext
            ASYNC_SCOPE.launch {
                TvWatchNextPublisher(appContext).sync()
            }
        }
    }
}
