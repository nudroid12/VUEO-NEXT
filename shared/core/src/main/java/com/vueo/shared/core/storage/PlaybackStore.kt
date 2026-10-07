package com.vueo.shared.core.storage

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Strictly increasing wall-clock-ish stamp for playback cursor events.
 *
 * Progress can be persisted off Main and those writes may finish out of order.
 * Capturing the event stamp before dispatch lets stores reject stale writes
 * without assuming that a larger playback position is newer (rewinds are valid).
 */
object PlaybackUpdateClock {
    private val lastEpochMs = AtomicLong(0L)

    fun observe(epochMs: Long) {
        if (epochMs <= 0L) return
        while (true) {
            val current = lastEpochMs.get()
            if (epochMs <= current) return
            if (lastEpochMs.compareAndSet(current, epochMs)) return
        }
    }

    fun next(): Long {
        while (true) {
            val current = lastEpochMs.get()
            val candidate = maxOf(System.currentTimeMillis(), current + 1L)
            if (lastEpochMs.compareAndSet(current, candidate)) {
                return candidate
            }
        }
    }
}

data class PlaybackPositionSnapshot(
    val profileId: String,
    val mediaKey: String,
    /** Actual player position captured for this event. */
    val positionMs: Long,
    val durationMs: Long,
    /** Position that is safe to use as a resume target; 0 means start fresh. */
    val resumePositionMs: Long,
    val updatedAtEpochMs: Long,
)

private object PlaybackSessionCursorMemory {
    val snapshots = ConcurrentHashMap<String, PlaybackPositionSnapshot>()
}

class PlaybackStore(
    context: Context,
    prefsName: String = PREFS_NAME,
    private val minResumeMs: Long = 5_000L,
    private val completionWindowMs: Long = 20_000L,
    profileStore: ProfileStore? = null,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            prefsName,
            Context.MODE_PRIVATE,
        )

    private val profileStore =
        profileStore ?: ProfileStore(appContext)

    private fun scopedMediaKey(
        mediaKey: String,
    ): String =
        scopedMediaKey(
            profileId = profileStore.activeProfileId(),
            mediaKey = mediaKey,
        )

    private fun scopedMediaKey(
        profileId: String,
        mediaKey: String,
    ): String =
        ProfileStore.scopedPreferenceKey(
            profileId,
            mediaKey,
        )

    private fun sessionKey(
        profileId: String,
        mediaKey: String,
    ): String =
        "$profileId\u0000$mediaKey"

    private fun normalizeResumePosition(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ): Long {
        val position = positionMs.coerceAtLeast(0L)
        val duration = durationMs.coerceAtLeast(0L)
        val minimum =
            if (
                mediaKey.startsWith("series:") ||
                mediaKey.startsWith("tv:")
            ) {
                0L
            } else {
                minResumeMs
            }

        if (position <= minimum) return 0L
        if (
            duration > 0L &&
            position >= duration - completionWindowMs
        ) {
            return 0L
        }
        return position
    }

    /**
     * Publishes the newest player cursor in memory immediately, before any IO.
     * This is what closes the Back -> reopen race on both Mobile and TV.
     */
    fun capturePosition(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ): PlaybackPositionSnapshot {
        val profileId = profileStore.activeProfileId()
        val scopedKey = scopedMediaKey(profileId, mediaKey)
        PlaybackUpdateClock.observe(
            prefs.getLong(
                updatedAtKey(scopedKey),
                0L,
            )
        )

        val position = positionMs.coerceAtLeast(0L)
        val duration = durationMs.coerceAtLeast(0L)
        val snapshot =
            PlaybackPositionSnapshot(
                profileId = profileId,
                mediaKey = mediaKey,
                positionMs = position,
                durationMs = duration,
                resumePositionMs = normalizeResumePosition(
                    mediaKey = mediaKey,
                    positionMs = position,
                    durationMs = duration,
                ),
                updatedAtEpochMs = PlaybackUpdateClock.next(),
            )

        synchronized(PERSISTENCE_LOCK) {
            val key = sessionKey(profileId, mediaKey)
            val existing = PlaybackSessionCursorMemory.snapshots[key]
            if (
                existing == null ||
                snapshot.updatedAtEpochMs >= existing.updatedAtEpochMs
            ) {
                PlaybackSessionCursorMemory.snapshots[key] = snapshot
            }
        }
        return snapshot
    }

    /** Latest cursor captured in this process, including a deliberate rewind. */
    fun sessionSnapshot(
        mediaKey: String,
    ): PlaybackPositionSnapshot? {
        val profileId = profileStore.activeProfileId()
        return PlaybackSessionCursorMemory.snapshots[
            sessionKey(profileId, mediaKey)
        ]
    }

    /**
     * Persists a previously captured cursor. Older async jobs are ignored.
     * A 0 resume cursor still writes its timestamp so an old queued save cannot
     * resurrect a completed/cleared position later.
     */
    fun persistSnapshot(
        snapshot: PlaybackPositionSnapshot,
    ) {
        val scopedKey = scopedMediaKey(
            profileId = snapshot.profileId,
            mediaKey = snapshot.mediaKey,
        )

        synchronized(PERSISTENCE_LOCK) {
            val persistedUpdatedAt =
                prefs.getLong(
                    updatedAtKey(scopedKey),
                    0L,
                )
            PlaybackUpdateClock.observe(persistedUpdatedAt)
            if (
                persistedUpdatedAt > snapshot.updatedAtEpochMs
            ) {
                return
            }

            val editor = prefs.edit()
                .putLong(
                    updatedAtKey(scopedKey),
                    snapshot.updatedAtEpochMs,
                )

            if (snapshot.resumePositionMs > 0L) {
                editor
                    .putLong(
                        positionKey(scopedKey),
                        snapshot.resumePositionMs,
                    )
                    .putLong(
                        durationKey(scopedKey),
                        snapshot.durationMs.coerceAtLeast(0L),
                    )
            } else {
                editor
                    .remove(positionKey(scopedKey))
                    .remove(durationKey(scopedKey))
            }
            editor.apply()
        }
    }

    private fun persistedSnapshot(
        profileId: String,
        mediaKey: String,
    ): PlaybackPositionSnapshot? {
        val scopedKey = scopedMediaKey(profileId, mediaKey)
        val position =
            prefs.getLong(
                positionKey(scopedKey),
                0L,
            )
        val duration =
            prefs.getLong(
                durationKey(scopedKey),
                0L,
            )
        val updatedAt =
            prefs.getLong(
                updatedAtKey(scopedKey),
                0L,
            )
        PlaybackUpdateClock.observe(updatedAt)

        if (
            position <= 0L &&
            duration <= 0L &&
            updatedAt <= 0L
        ) {
            return null
        }

        return PlaybackPositionSnapshot(
            profileId = profileId,
            mediaKey = mediaKey,
            positionMs = position.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            resumePositionMs = position.coerceAtLeast(0L),
            updatedAtEpochMs = updatedAt,
        )
    }

    fun latestSnapshot(
        mediaKey: String,
    ): PlaybackPositionSnapshot? {
        val profileId = profileStore.activeProfileId()
        val session = PlaybackSessionCursorMemory.snapshots[
            sessionKey(profileId, mediaKey)
        ]
        val persisted = persistedSnapshot(profileId, mediaKey)

        return when {
            session == null -> persisted
            persisted == null -> session
            session.updatedAtEpochMs >= persisted.updatedAtEpochMs -> session
            else -> persisted
        }
    }

    fun positionMs(mediaKey: String): Long =
        latestSnapshot(mediaKey)
            ?.resumePositionMs
            ?: 0L

    fun durationMs(mediaKey: String): Long =
        latestSnapshot(mediaKey)
            ?.takeIf { it.resumePositionMs > 0L }
            ?.durationMs
            ?: 0L

    fun clearPosition(
        mediaKey: String,
    ) {
        persistSnapshot(
            capturePosition(
                mediaKey = mediaKey,
                positionMs = 0L,
                durationMs = 0L,
            )
        )
    }

    fun savePositionMs(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        persistSnapshot(
            capturePosition(
                mediaKey = mediaKey,
                positionMs = positionMs,
                durationMs = durationMs,
            )
        )
    }

    private fun positionKey(
        mediaKey: String,
    ): String =
        "position:$mediaKey"

    private fun durationKey(
        mediaKey: String,
    ): String =
        "duration:$mediaKey"

    private fun updatedAtKey(
        mediaKey: String,
    ): String =
        "updated_at:$mediaKey"

    companion object {
        private const val PREFS_NAME = "vueo_playback"
        private val PERSISTENCE_LOCK = Any()
    }
}
