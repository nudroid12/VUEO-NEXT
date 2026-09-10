package com.vueo.shared.core.plugin

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONObject
import okio.Buffer
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Shared native networking for repository downloads and the future QuickJS fetch bridge. */
object PluginHttp {
    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val doh: Dns by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
            )
            .includeIPv6(false)
            .build()
    }

    private val resilientDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            try {
                Dns.SYSTEM.lookup(hostname).ifEmpty {
                    throw UnknownHostException("System DNS returned no addresses for $hostname")
                }
            } catch (systemError: UnknownHostException) {
                try {
                    doh.lookup(hostname)
                } catch (dohError: UnknownHostException) {
                    dohError.addSuppressed(systemError)
                    throw dohError
                }
            }
    }

    private val safeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = resilientDns.lookup(hostname)
            if (addresses.any(::isPrivateAddress)) {
                throw UnknownHostException(
                    "Plugin network access cannot reach local/private addresses."
                )
            }
            return addresses
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(safeDns)
            .addNetworkInterceptor { chain ->
                require(chain.request().url.isHttps) {
                    "Plugin network access only allows HTTPS."
                }
                chain.proceed(chain.request())
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val manualRedirectClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        requireHttps(url)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("User-Agent", "VUEO/0.9.6")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} from ${response.request.url.host}")
            }
            response.body.string()
        }
    }

    /**
     * A short-lived HTTP session owned by one provider execution.
     *
     * Providers commonly obtain a signed media URL only after a login,
     * consent or anti-bot request sets a cookie. Keeping this jar scoped to
     * one execution prevents cookies leaking between providers while still
     * allowing the provider's subsequent requests to behave like a browser.
     */
    fun newSession(): Session = Session()

    class Session internal constructor() {
        private val cookieJar = MemoryCookieJar()
        private val activeCalls = ConcurrentHashMap<String, Call>()
        private val sessionClient by lazy {
            client.newBuilder()
                .cookieJar(cookieJar)
                .build()
        }
        private val sessionManualRedirectClient by lazy {
            sessionClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        suspend fun executeJson(requestJson: String): String =
            executeJsonWithClients(
                requestJson = requestJson,
                normalClient = sessionClient,
                manualClient = sessionManualRedirectClient,
                onCallCreated = { requestId, call ->
                    if (requestId.isNotBlank()) {
                        activeCalls[requestId] = call
                    }
                },
                onCallFinished = { requestId ->
                    if (requestId.isNotBlank()) {
                        activeCalls.remove(requestId)
                    }
                },
            )

        fun cancel(requestId: String) {
            activeCalls.remove(requestId)?.cancel()
        }

        fun cancelAll() {
            activeCalls.values.forEach(Call::cancel)
            activeCalls.clear()
        }

        fun cookieHeader(url: String): String? {
            val parsed = url.toHttpUrlOrNull() ?: return null
            return cookieJar.loadForRequest(parsed)
                .joinToString("; ") { "${it.name}=${it.value}" }
                .takeIf { it.isNotBlank() }
        }
    }

    suspend fun executeJson(requestJson: String): String =
        executeJsonWithClients(
            requestJson = requestJson,
            normalClient = client,
            manualClient = manualRedirectClient,
        )

    private suspend fun executeJsonWithClients(
        requestJson: String,
        normalClient: OkHttpClient,
        manualClient: OkHttpClient,
        onCallCreated: (String, Call) -> Unit = { _, _ -> },
        onCallFinished: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            val input = JSONObject(requestJson)
            val url = input.optString("url")
            requireHttps(url)

            val method = input.optString("method", "GET").uppercase()
            val requestBuilder = Request.Builder().url(url)
            val headers = input.optJSONObject("headers")
            if (headers != null) {
                val iterator = headers.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    if (key.lowercase() in BLOCKED_REQUEST_HEADERS) continue
                    val value = headers.optString(key)
                    if (value.isNotBlank()) requestBuilder.header(key, value)
                }
            }
            if (headers?.has("User-Agent") != true) {
                requestBuilder.header("User-Agent", "VUEO/0.9.6")
            }

            val contentType =
                input.optString("contentType")
                    .takeIf { it.isNotBlank() }
                    ?.toMediaTypeOrNull()
            val body =
                when {
                    !input.optString("bodyBase64").isNullOrBlank() -> {
                        val bytes = Base64.decode(
                            input.optString("bodyBase64"),
                            Base64.DEFAULT,
                        )
                        require(bytes.size <= MAX_PLUGIN_REQUEST_BODY_BYTES) {
                            "Plugin request body exceeds the allowed size."
                        }
                        bytes.toRequestBody(contentType)
                    }

                    !input.isNull("body") -> {
                        val bodyText = input.optString("body")
                        require(
                            bodyText.toByteArray(Charsets.UTF_8).size <=
                                MAX_PLUGIN_REQUEST_BODY_BYTES
                        ) {
                            "Plugin request body exceeds the allowed size."
                        }
                        bodyText.toRequestBody(contentType)
                    }

                    else -> null
                }

            when (method) {
                "GET" -> requestBuilder.get()
                "HEAD" -> requestBuilder.head()
                "POST" -> requestBuilder.post(body ?: ByteArray(0).toRequestBody())
                "PUT" -> requestBuilder.put(body ?: ByteArray(0).toRequestBody())
                "PATCH" -> requestBuilder.patch(body ?: ByteArray(0).toRequestBody())
                "DELETE" -> if (body != null) requestBuilder.delete(body) else requestBuilder.delete()
                else -> requestBuilder.method(method, body)
            }

            val selectedClient = if (input.optString("redirect", "follow") == "manual") {
                manualClient
            } else {
                normalClient
            }

            val call =
                selectedClient.newCall(requestBuilder.build())
            val requestId =
                input.optString("requestId")
            onCallCreated(requestId, call)
            input.optLong("timeoutMs")
                .takeIf { it > 0L }
                ?.coerceIn(
                    MIN_REQUEST_TIMEOUT_MS,
                    MAX_REQUEST_TIMEOUT_MS,
                )
                ?.let { timeoutMs ->
                    call.timeout().timeout(
                        timeoutMs,
                        TimeUnit.MILLISECONDS,
                    )
                }

            try {
                call.awaitResponse().use { response ->
                val responseHeaders = JSONObject()
                response.headers.names().forEach { name ->
                    responseHeaders.put(name, response.headers.values(name).joinToString(", "))
                }

                /*
                 * Providers often fetch a URL only to inspect status, headers
                 * or redirects. Never copy an entire direct media/download
                 * response into the Java heap. Normal scraper text remains
                 * available, bounded to protect a 256 MB process.
                 */
                val responseBody = response.body
                val mediaType = responseBody.contentType()
                val contentType = mediaType?.toString().orEmpty().lowercase()
                val declaredLength = responseBody.contentLength()

                val textualResponse =
                    !input.optBoolean("binaryResponse", false) &&
                        (
                            contentType.isBlank() ||
                                contentType.startsWith("text/") ||
                                "json" in contentType ||
                                "javascript" in contentType ||
                                "xml" in contentType ||
                                "mpegurl" in contentType
                        )

                val oversizedBody =
                    declaredLength > MAX_PLUGIN_RESPONSE_BODY_BYTES

                val skipBody =
                    method == "HEAD" ||
                        contentType.startsWith("video/") ||
                        contentType.startsWith("audio/") ||
                        (oversizedBody && !textualResponse)

                var bodyTruncated =
                    oversizedBody && method != "HEAD"

                val responseBytes =
                    if (skipBody) {
                        ByteArray(0)
                    } else {
                        val source = responseBody.source()
                        val buffer = Buffer()
                        var remaining = MAX_PLUGIN_RESPONSE_BODY_BYTES

                        while (remaining > 0L) {
                            val read =
                                source.read(
                                    buffer,
                                    minOf(
                                        HTTP_READ_CHUNK_BYTES,
                                        remaining,
                                    ),
                                )
                            if (read == -1L) break
                            remaining -= read
                        }

                        if (remaining == 0L) {
                            bodyTruncated = true
                        }

                        buffer.readByteArray()
                    }

                val responseText =
                    if (textualResponse) {
                        String(
                            responseBytes,
                            mediaType?.charset(Charsets.UTF_8)
                                ?: Charsets.UTF_8,
                        )
                    } else {
                        ""
                    }

                val responseBase64 =
                    if (!textualResponse && responseBytes.isNotEmpty()) {
                        Base64.encodeToString(
                            responseBytes,
                            Base64.NO_WRAP,
                        )
                    } else {
                        ""
                    }

                    JSONObject()
                        .put("status", response.code)
                        .put("statusText", response.message)
                        .put("url", response.request.url.toString())
                        .put("body", responseText)
                        .put("bodyBase64", responseBase64)
                        .put("bodyTruncated", bodyTruncated)
                        .put("headers", responseHeaders)
                        .toString()
                }
            } finally {
                onCallFinished(requestId)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            JSONObject()
                .put("error", error.message ?: error::class.java.simpleName)
                .put("errorType", error::class.java.simpleName)
                .toString()
        }
    }

    private fun requireHttps(url: String) {
        require(url.startsWith("https://", ignoreCase = true)) {
            "Plugin network access only allows HTTPS."
        }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress
        ) return true

        val bytes = address.address
        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 100 && second in 64..127) return true
        }
        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            if (first == 0xFC || first == 0xFD) return true
        }
        return false
    }

    private const val MAX_PLUGIN_RESPONSE_BODY_BYTES =
        4L * 1024L * 1024L

    private const val HTTP_READ_CHUNK_BYTES =
        32L * 1024L

    private const val MAX_PLUGIN_REQUEST_BODY_BYTES =
        4 * 1024 * 1024

    private const val MIN_REQUEST_TIMEOUT_MS =
        250L

    private const val MAX_REQUEST_TIMEOUT_MS =
        30_000L

    private val BLOCKED_REQUEST_HEADERS = setOf(
        "host",
        "content-length",
        "connection",
        "accept-encoding",
    )
}

private suspend fun Call.awaitResponse(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.failure(error))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(response))
                    } else {
                        response.close()
                    }
                }
            }
        )
    }

private class MemoryCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        synchronized(lock) {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            cookies.filter { it.matches(url) }
        }

    override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
        if (newCookies.isEmpty()) return

        synchronized(lock) {
            newCookies.forEach { incoming ->
                cookies.removeAll { existing ->
                    existing.name == incoming.name &&
                        existing.domain == incoming.domain &&
                        existing.path == incoming.path
                }

                if (incoming.expiresAt >= System.currentTimeMillis()) {
                    cookies += incoming
                }
            }
        }
    }
}
