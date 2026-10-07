package com.vueo.shared.core.player

import com.vueo.shared.core.media.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class PrefetchedSourceValidation {
    VALID,
    INVALID,
    UNKNOWN,
}

/**
 * Lightweight availability probe for an already-prefetched direct stream URL.
 *
 * Validation deliberately reuses the stream headers and closes the response body
 * immediately. A tiny range request is used instead of starting playback or
 * downloading the media. Definite auth/not-found/gone responses are treated as
 * stale; transport failures and server errors are retried once before returning
 * UNKNOWN so the caller can refresh discovery conservatively.
 */
object NextEpisodePrefetchValidator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun validate(source: StreamSource): PrefetchedSourceValidation {
        val first = probe(source)
        if (first != PrefetchedSourceValidation.UNKNOWN) return first

        delay(250L)
        return probe(source)
    }

    private suspend fun probe(source: StreamSource): PrefetchedSourceValidation =
        withContext(Dispatchers.IO) {
            val url = source.url
                ?.trim()
                ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: return@withContext PrefetchedSourceValidation.INVALID

            val request = try {
                Request.Builder()
                    .url(url)
                    .apply {
                        source.headers.forEach { (name, value) ->
                            if (!name.equals("Range", ignoreCase = true)) {
                                header(name, value)
                            }
                        }
                    }
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
            } catch (_: Throwable) {
                return@withContext PrefetchedSourceValidation.UNKNOWN
            }

            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code in 200..299 -> PrefetchedSourceValidation.VALID
                        response.code == 401 ||
                            response.code == 403 ||
                            response.code == 404 ||
                            response.code == 410 -> PrefetchedSourceValidation.INVALID
                        else -> PrefetchedSourceValidation.UNKNOWN
                    }
                }
            } catch (_: Throwable) {
                PrefetchedSourceValidation.UNKNOWN
            }
        }
}
