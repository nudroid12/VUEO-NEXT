package com.vueo.shared.core.stremio

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

interface StremioHttpClient {
    suspend fun get(url: String): String
}

/**
 * Default pooled transport for shared Stremio traffic.
 *
 * Network I/O is asynchronous so callers do not block the Android main thread.
 * The HTTPS-only rule preserves the current VUEO security baseline.
 */
object DefaultStremioHttpClient : StremioHttpClient {
    private const val MAX_DECLARED_BODY_BYTES = 8L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String): String {
        require(url.startsWith("https://", ignoreCase = true)) {
            "VUEO network requests require HTTPS."
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.8")
            .header("User-Agent", "VUEO-NEXT")
            .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            val result = runCatching {
                                if (!response.isSuccessful) {
                                    error(
                                        "HTTP ${response.code} from " +
                                            response.request.url.host
                                    )
                                }

                                val length = response.body.contentLength()
                                if (length > MAX_DECLARED_BODY_BYTES) {
                                    error("Response too large ($length bytes)")
                                }

                                response.body.string()
                            }
                            continuation.resumeWith(result)
                        }
                    }
                }
            )
        }
    }
}
