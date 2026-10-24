package com.vueo.shared.core.stremio

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface StremioHttpClient {
    suspend fun get(url: String): String
}

/**
 * Default pooled transport for shared Stremio traffic.
 *
 * Network I/O is asynchronous so callers do not block the Android main thread.
 * HTTP and HTTPS addon URLs share this transport; HTTPS certificate validation stays enabled.
 */
object DefaultStremioHttpClient : StremioHttpClient {
    private const val MAX_RESPONSE_BODY_BYTES = 8L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    override suspend fun get(url: String): String {
        require(StremioUrls.isHttpUrl(url)) {
            "VUEO addon requests require HTTP or HTTPS."
        }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain;q=0.9, */*;q=0.8")
            .header("User-Agent", "VUEO-NEXT")
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!continuation.isActive) return@use
                            val result = runCatching {
                                if (!response.isSuccessful) {
                                    error(
                                        "HTTP ${response.code} from " +
                                            response.request.url.host
                                    )
                                }

                                response.body.readBoundedString(MAX_RESPONSE_BODY_BYTES) {
                                    continuation.isActive
                                }
                            }
                            if (continuation.isActive) continuation.resumeWith(result)
                        }
                    }
                }
            )
        }
    }
}
