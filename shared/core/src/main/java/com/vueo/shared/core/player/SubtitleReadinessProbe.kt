package com.vueo.shared.core.player

import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Warms a slow generated subtitle before its Media3 text track is selected.
 * This keeps a translating endpoint from putting the whole player into BUFFERING.
 */
object SubtitleReadinessProbe {
    suspend fun awaitReady(
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onWaiting: suspend () -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        readyAtMs[url]
            ?.takeIf { nowMs - it <= READY_CACHE_MS }
            ?.let { return@withContext true }

        val deadlineNs = System.nanoTime() + timeoutMs.coerceAtLeast(1L) * 1_000_000L
        var waitingReported = false

        while (System.nanoTime() < deadlineNs) {
            coroutineContext.ensureActive()
            val result = probeOnce(url)
            when (result) {
                ProbeResult.READY -> {
                    readyAtMs[url] = System.currentTimeMillis()
                    return@withContext true
                }
                ProbeResult.FAILED -> return@withContext false
                ProbeResult.RETRY -> {
                    if (!waitingReported) {
                        waitingReported = true
                        withContext(Dispatchers.Main.immediate) {
                            onWaiting()
                        }
                    }
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        false
    }

    private fun probeOnce(url: String): ProbeResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Throwable) {
            return ProbeResult.FAILED
        }

        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/vtt,text/plain,application/x-subrip,*/*")
            connection.setRequestProperty("User-Agent", "VUEO subtitle preflight")

            when (connection.responseCode) {
                in 200..201, in 203..299 -> {
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        val output = ByteArrayOutputStream()
                        var cacheable = true
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (cacheable && output.size() + count <= MAX_CACHE_BYTES) {
                                output.write(buffer, 0, count)
                            } else {
                                cacheable = false
                            }
                        }
                        if (cacheable && output.size() > 0) {
                            SubtitleSessionCache.put(url, output.toByteArray())
                        }
                    }
                    ProbeResult.READY
                }

                202, 425, 429, 503 -> ProbeResult.RETRY
                else -> ProbeResult.FAILED
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ProbeResult.RETRY
        } finally {
            connection.disconnect()
        }
    }

    private enum class ProbeResult {
        READY,
        RETRY,
        FAILED,
    }

    private const val DEFAULT_TIMEOUT_MS = 60_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 35_000
    private const val RETRY_DELAY_MS = 1_000L
    private const val BUFFER_SIZE = 16 * 1024
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024
    private const val READY_CACHE_MS = 15 * 60 * 1_000L
    private val readyAtMs = ConcurrentHashMap<String, Long>()
}
