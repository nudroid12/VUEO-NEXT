package com.vueo.shared.core.player

import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.min

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
        val normalizedUrl = url.trim()
        if (normalizedUrl.isEmpty()) return@withContext false

        val timeoutNs = timeoutMs.coerceAtLeast(1L) * NANOS_PER_MILLISECOND
        val deadlineNs = System.nanoTime() + timeoutNs
        val nowMs = System.currentTimeMillis()
        readyAtMs[normalizedUrl]
            ?.takeIf { nowMs - it <= READY_CACHE_MS }
            ?.let { return@withContext true }

        val probeLock = probeLocks.computeIfAbsent(normalizedUrl) { Mutex() }
        var waitingReported = false
        if (probeLock.isLocked) {
            waitingReported = true
            withContext(Dispatchers.Main.immediate) {
                onWaiting()
            }
        }

        probeLock.withLock {
            // Another caller may have completed the same URL while this caller
            // was waiting for the per-URL single-flight lock.
            val cachedAtMs = readyAtMs[normalizedUrl]
            if (
                cachedAtMs != null &&
                System.currentTimeMillis() - cachedAtMs <= READY_CACHE_MS
            ) {
                return@withContext true
            }

            while (System.nanoTime() < deadlineNs) {
                coroutineContext.ensureActive()
                val remainingMs = remainingMillis(deadlineNs)
                if (remainingMs <= 0L) break

                val connectTimeoutMs =
                    min(CONNECT_TIMEOUT_MS.toLong(), remainingMs)
                        .coerceAtLeast(1L)
                        .toInt()
                val readBudgetMs =
                    (remainingMs - connectTimeoutMs)
                        .coerceAtLeast(1L)
                val readTimeoutMs =
                    min(READ_TIMEOUT_MS.toLong(), readBudgetMs)
                        .coerceAtLeast(1L)
                        .toInt()
                val result = probeOnce(
                    url = normalizedUrl,
                    connectTimeoutMs = connectTimeoutMs,
                    readTimeoutMs = readTimeoutMs,
                )
                when (result) {
                    ProbeResult.READY -> {
                        readyAtMs[normalizedUrl] = System.currentTimeMillis()
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
                        val retryWaitMs =
                            min(RETRY_DELAY_MS, remainingMillis(deadlineNs))
                        if (retryWaitMs > 0L) delay(retryWaitMs)
                    }
                }
            }

            false
        }
    }

    private fun probeOnce(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
    ): ProbeResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Throwable) {
            return ProbeResult.FAILED
        }

        return try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
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

    private fun remainingMillis(deadlineNs: Long): Long =
        ((deadlineNs - System.nanoTime()) / NANOS_PER_MILLISECOND)
            .coerceAtLeast(0L)

    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val DEFAULT_TIMEOUT_MS = 90_000L
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val RETRY_DELAY_MS = 3_000L
    private const val BUFFER_SIZE = 16 * 1024
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024
    private const val READY_CACHE_MS = 15 * 60 * 1_000L
    private val readyAtMs = ConcurrentHashMap<String, Long>()
    private val probeLocks = ConcurrentHashMap<String, Mutex>()
}
