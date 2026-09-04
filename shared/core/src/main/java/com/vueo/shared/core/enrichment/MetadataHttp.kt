package com.vueo.shared.core.enrichment

import com.vueo.shared.core.plugin.PluginHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("HTTP ${response.code} from ${response.request.url.host}")
                }

                val length = response.body.contentLength()
                if (length > MAX_DECLARED_BODY_BYTES) {
                    error("Metadata response too large ($length bytes)")
                }

                response.body.string()
            }
        }
}
