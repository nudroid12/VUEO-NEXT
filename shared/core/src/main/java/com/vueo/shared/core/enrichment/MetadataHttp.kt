package com.vueo.shared.core.enrichment

import com.vueo.shared.core.plugin.PluginHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Shared resilient HTTP transport for metadata/enhancement services.
 * Reuses the provider runtime DNS + connection pool, with a slightly longer
 * timeout for public metadata APIs.
 */
object MetadataHttp {
    private const val MAX_DECLARED_BODY_BYTES = 8L * 1024L * 1024L

    private val client by lazy {
        PluginHttp.client
            .newBuilder()
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            require(url.startsWith("https://", ignoreCase = true)) {
                "VUEO metadata requests require HTTPS."
            }

            val request =
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json, */*")
                    .header("User-Agent", "VUEO/0.9.6")
                    .build()

            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                if (!it.isSuccessful) error("HTTP ${it.code} from ${it.request.url.host}")
                                val length = it.body.contentLength()
                                if (length > MAX_DECLARED_BODY_BYTES) error("Metadata response too large ($length bytes)")
                                it.body.string()
                            }
                        }
                        if (continuation.isActive) result.fold(
                            onSuccess = { continuation.resume(it) },
                            onFailure = { continuation.resumeWithException(it) },
                        )
                    }
                })
            }
        }
}
