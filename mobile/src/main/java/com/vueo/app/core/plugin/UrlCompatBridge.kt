package com.vueo.app.core.plugin

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

object UrlCompatBridge {
    fun execute(requestJson: String): String =
        runCatching {
            val request = JSONObject(requestJson)
            val input = request.getString("input")
            val base = request.optString("base")

            val url = resolve(
                input = input,
                base = base,
            )

            JSONObject()
                .put("href", url.toString())
                .put(
                    "origin",
                    "${url.scheme}://${url.host}" +
                        if (
                            url.port == defaultPort(url.scheme)
                        ) {
                            ""
                        } else {
                            ":${url.port}"
                        },
                )
                .put(
                    "protocol",
                    "${url.scheme}:",
                )
                .put("hostname", url.host)
                .put(
                    "host",
                    url.host +
                        if (
                            url.port == defaultPort(url.scheme)
                        ) {
                            ""
                        } else {
                            ":${url.port}"
                        },
                )
                .put("port", url.port.toString())
                .put("pathname", url.encodedPath)
                .put(
                    "search",
                    url.encodedQuery
                        ?.let { "?$it" }
                        .orEmpty(),
                )
                .put(
                    "hash",
                    url.encodedFragment
                        ?.let { "#$it" }
                        .orEmpty(),
                )
                .put(
                    "queryPairs",
                    JSONArray().apply {
                        for (
                            index in
                            0 until url.querySize
                        ) {
                            put(
                                JSONArray()
                                    .put(
                                        url.queryParameterName(
                                            index
                                        )
                                    )
                                    .put(
                                        url.queryParameterValue(
                                            index
                                        ).orEmpty()
                                    )
                            )
                        }
                    },
                )
        }.getOrElse { error ->
            JSONObject().put(
                "error",
                error.message
                    ?: error::class.java.simpleName,
            )
        }.toString()

    private fun resolve(
        input: String,
        base: String,
    ): HttpUrl {
        input.toHttpUrlOrNull()?.let {
            return it
        }

        if (base.isBlank()) {
            error("Invalid URL: $input")
        }

        val baseUrl = base.toHttpUrlOrNull()
            ?: error("Invalid base URL: $base")

        return baseUrl.resolve(input)
            ?: error(
                "Unable to resolve URL '$input' " +
                    "against '$base'"
            )
    }

    private fun defaultPort(scheme: String): Int =
        when (scheme) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
}
