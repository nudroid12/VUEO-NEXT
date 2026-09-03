package com.vueo.app.core.storage

import android.content.Context

class PlaybackStore(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    private val profileStore =
        ProfileStore(appContext)

    private fun scopedMediaKey(
        mediaKey: String,
    ): String =
        ProfileStore.scopedPreferenceKey(
            profileStore.activeProfileId(),
            mediaKey,
        )

    fun positionMs(mediaKey: String): Long =
        prefs.getLong(
            positionKey(
                scopedMediaKey(
                    mediaKey
                )
            ),
            0L,
        )

    fun durationMs(mediaKey: String): Long =
        prefs.getLong(
            durationKey(
                scopedMediaKey(
                    mediaKey
                )
            ),
            0L,
        )

    fun clearPosition(
        mediaKey: String,
    ) {
        prefs.edit()
            .remove(
                positionKey(
                    scopedMediaKey(
                        mediaKey
                    )
                )
            )
            .remove(
                durationKey(
                    scopedMediaKey(
                        mediaKey
                    )
                )
            )
            .apply()
    }

    fun savePositionMs(
        mediaKey: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (positionMs <= 5_000L) {
            clearPosition(mediaKey)
            return
        }

        if (
            durationMs > 0L &&
            positionMs >=
                durationMs - 20_000L
        ) {
            clearPosition(mediaKey)
            return
        }

        prefs.edit()
            .putLong(
                positionKey(
                scopedMediaKey(
                    mediaKey
                )
            ),
                positionMs,
            )
            .putLong(
                durationKey(
                scopedMediaKey(
                    mediaKey
                )
            ),
                durationMs.coerceAtLeast(0L),
            )
            .apply()
    }

    private fun positionKey(
        mediaKey: String,
    ): String =
        "position:$mediaKey"

    private fun durationKey(
        mediaKey: String,
    ): String =
        "duration:$mediaKey"

    companion object {
        private const val PREFS_NAME = "vueo_playback"
    }
}
