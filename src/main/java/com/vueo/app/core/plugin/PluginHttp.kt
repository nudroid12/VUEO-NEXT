package com.vueo.app.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Shared native networking for VUEO plugin repositories and QuickJS fetch().
 *
 * Normal device DNS is tried first. If it cannot resolve a hostname, VUEO
 * retries DNS through Cloudflare DoH using bootstrap IPs, avoiding the
 * raw.githubusercontent.com / cdn.jsdelivr.net resolver failure seen in
 * development builds.
 */
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
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname).ifEmpty {
                    throw UnknownHostException(
                        "System DNS returned no addresses for $hostname"
                    )
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
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(resilientDns)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val manualRedirectClient: OkHttpClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    suspend fun getText(url: String): String =
        withContext(Dispatchers.IO) {
            requireHttps(url)

            val request = Request.Builder()
                .url(url)
                .header("Accept", "*/*")
                .header("User-Agent", "VUEO/0.9.6")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error(
                        "HTTP ${response.code} from ${response.request.url.host}"
                    )
                }
                response.body.string()
            }
        }

    suspend fun executeJson(requestJson: String): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = JSONObject(requestJson)
                val url = input.optString("url")
                requireHttps(url)
                rejectLocalAddress(url)

                val method = input.optString("method", "GET")
                    .uppercase()

                val requestBuilder = Request.Builder()
                    .url(url)

                val headers = input.optJSONObject("headers")
                if (headers != null) {
                    val iterator = headers.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        if (key.lowercase() in BLOCKED_REQUEST_HEADERS) {
                            continue
                        }

                        val value = headers.optString(key)
                        if (value.isNotBlank()) {
                            requestBuilder.header(key, value)
                        }
                    }
                }

                if (headers?.has("User-Agent") != true) {
                    requestBuilder.header("User-Agent", "VUEO/0.9.6")
                }

                val bodyText = if (input.isNull("body")) {
                    null
                } else {
                    input.optString("body")
                }

                val body = bodyText?.toRequestBody(
                    input.optString("contentType")
                        .takeIf { it.isNotBlank() }
                        ?.toMediaTypeOrNull()
                )

                when (method) {
                    "GET" -> requestBuilder.get()
                    "HEAD" -> requestBuilder.head()
                    "POST" -> requestBuilder.post(
                        body ?: ByteArray(0).toRequestBody()
                    )
                    "PUT" -> requestBuilder.put(
                        body ?: ByteArray(0).toRequestBody()
                    )
                    "PATCH" -> requestBuilder.patch(
                        body ?: ByteArray(0).toRequestBody()
                    )
                    "DELETE" -> {
                        if (body != null) {
                            requestBuilder.delete(body)
                        } else {
                            requestBuilder.delete()
                        }
                    }
                    else -> requestBuilder.method(method, body)
                }

                val redirectMode =
                    input.optString(
                        "redirect",
                        "follow",
                    )

                val requestClient =
                    if (redirectMode == "manual") {
                        manualRedirectClient
                    } else {
                        client
                    }

                requestClient
                    .newCall(requestBuilder.build())
                    .execute()
                    .use { response ->
                        val responseHeaders = JSONObject()
                        response.headers.names().forEach { name ->
                            responseHeaders.put(
                                name,
                                response.headers.values(name)
                                    .joinToString(", "),
                            )
                        }

                        JSONObject()
                            .put("status", response.code)
                            .put("statusText", response.message)
                            .put("url", response.request.url.toString())
                            .put("body", response.body.string())
                            .put("headers", responseHeaders)
                            .toString()
                    }
            }.getOrElse { error ->
                JSONObject()
                    .put(
                        "error",
                        error.message ?: error::class.java.simpleName,
                    )
                    .toString()
            }
        }

    private fun requireHttps(url: String) {
        require(url.startsWith("https://")) {
            "Plugin network access only allows HTTPS."
        }
    }

    private fun rejectLocalAddress(url: String) {
        val host = url.toHttpUrl().host

        val addresses = resilientDns.lookup(host)
        require(addresses.none(::isPrivateAddress)) {
            "Plugin network access cannot reach local/private addresses."
        }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean {
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress
        ) {
            return true
        }

        val bytes = address.address

        if (bytes.size == 4) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF

            if (first == 100 && second in 64..127) {
                return true
            }
        }

        if (bytes.size == 16) {
            val first = bytes[0].toInt() and 0xFF
            if (first == 0xFC || first == 0xFD) {
                return true
            }
        }

        return false
    }

    private val BLOCKED_REQUEST_HEADERS = setOf(
        "host",
        "content-length",
        "connection",
        "accept-encoding",
    )
}
