package com.vueo.app.core.stremio

import com.vueo.app.core.plugin.PluginHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

/**
 * Shared pooled HTTP transport for Stremio addons and resilient public
 * repository downloads.
 *
 * VUEO reuses the same OkHttp connection pool and resilient DNS layer used by
 * the plugin runtime instead of creating a new HttpURLConnection for every
 * catalog, metadata, stream, or subtitle request.
 */
object SimpleHttp {
    private const val MAX_DECLARED_BODY_BYTES =
        8L * 1024L * 1024L

    suspend fun get(
        url: String,
    ): String =
        withContext(
            Dispatchers.IO
        ) {
            getBlocking(url)
        }

    suspend fun getResilient(
        url: String,
    ): String =
        withContext(
            Dispatchers.IO
        ) {
            val candidates =
                candidateUrls(url)

            val failures =
                mutableListOf<String>()

            for (
                candidate in
                candidates
            ) {
                val result =
                    runCatching {
                        getBlocking(
                            candidate
                        )
                    }

                result.getOrNull()
                    ?.let {
                        return@withContext it
                    }

                val error =
                    result
                        .exceptionOrNull()

                val host =
                    runCatching {
                        URI(candidate)
                            .host
                    }.getOrNull()
                        ?: candidate

                failures +=
                    "$host: " +
                        (
                            error?.message
                                ?: "request failed"
                        )
            }

            error(
                "Unable to download resource. " +
                    failures
                        .joinToString(
                            " | "
                        )
            )
        }

    fun candidateUrls(
        url: String,
    ): List<String> {
        val mirror =
            githubJsDelivrMirror(
                url
            )

        return if (
            mirror == null
        ) {
            listOf(url)
        } else {
            listOf(
                mirror,
                url,
            ).distinct()
        }
    }

    private fun getBlocking(
        url: String,
    ): String {
        require(
            url.startsWith(
                "https://"
            )
        ) {
            "VUEO network requests require HTTPS."
        }

        val request =
            Request.Builder()
                .url(url)
                .header(
                    "Accept",
                    "*/*",
                )
                .header(
                    "User-Agent",
                    "VUEO/0.9.6",
                )
                .build()

        return PluginHttp.client
            .newCall(request)
            .execute()
            .use { response ->
                if (
                    !response.isSuccessful
                ) {
                    error(
                        "HTTP " +
                            "${response.code} " +
                            "from " +
                            response.request
                                .url
                                .host
                    )
                }

                val length =
                    response.body
                        .contentLength()

                if (
                    length >
                    MAX_DECLARED_BODY_BYTES
                ) {
                    error(
                        "Response too large " +
                            "($length bytes)"
                    )
                }

                response.body.string()
            }
    }

    private fun githubJsDelivrMirror(
        rawUrl: String,
    ): String? {
        val uri =
            runCatching {
                URI(rawUrl)
            }.getOrNull()
                ?: return null

        if (
            !uri.host.equals(
                "raw.githubusercontent.com",
                ignoreCase = true,
            )
        ) {
            return null
        }

        val parts =
            uri.path
                .trim('/')
                .split('/')
                .filter {
                    it.isNotBlank()
                }

        if (
            parts.size < 4
        ) {
            return null
        }

        val owner =
            parts[0]

        val repo =
            parts[1]

        val branch: String
        val fileStart: Int

        if (
            parts.size >= 6 &&
            parts[2] == "refs" &&
            parts[3] == "heads"
        ) {
            branch =
                parts[4]

            fileStart = 5
        } else {
            branch =
                parts[2]

            fileStart = 3
        }

        if (
            fileStart >=
            parts.size
        ) {
            return null
        }

        val filePath =
            parts
                .drop(fileStart)
                .joinToString("/")

        return "https://cdn.jsdelivr.net/gh/" +
            "$owner/$repo@$branch/$filePath"
    }
}
