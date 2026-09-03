package com.vueo.app.core.enrichment

import com.vueo.app.core.dna.UserDnaSnapshot
import com.vueo.app.core.model.MediaItem
import com.vueo.app.core.plugin.PluginHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * VUEO Gemini enhancement v1.1.
 *
 * Uses Gemini 3.5 Flash-Lite through the Interactions API.
 * Calls happen only after an explicit user action.
 * Raw History, My List and playback records are never sent.
 */
object GeminiClient {
    const val DEFAULT_MODEL =
        "gemini-3.5-flash-lite"

    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/interactions"

    private val jsonMediaType =
        "application/json; charset=utf-8"
            .toMediaType()

    private val client by lazy {
        PluginHttp.client
            .newBuilder()
            .readTimeout(
                35,
                TimeUnit.SECONDS,
            )
            .callTimeout(
                40,
                TimeUnit.SECONDS,
            )
            .build()
    }

    data class ConnectionResult(
        val connected: Boolean,
        val message: String,
    )

    suspend fun testConnection(
        apiKey: String,
        model: String = DEFAULT_MODEL,
    ): ConnectionResult {
        val cleanKey =
            apiKey.trim()

        if (cleanKey.isBlank()) {
            return ConnectionResult(
                connected = false,
                message = "Enter API key",
            )
        }

        return runCatching {
            val reply =
                interact(
                    apiKey = cleanKey,
                    model = model,
                    input =
                        "Reply with exactly VUEO_OK.",
                    systemInstruction =
                        "Follow the user's instruction exactly.",
                    maxOutputTokens = 32,
                )

            if (
                reply.contains(
                    "VUEO_OK",
                    ignoreCase = true,
                )
            ) {
                ConnectionResult(
                    connected = true,
                    message =
                        "Connected to Gemini 3.5 Flash-Lite",
                )
            } else {
                ConnectionResult(
                    connected = false,
                    message =
                        "Unexpected Gemini response: " +
                            reply
                                .replace(
                                    "\n",
                                    " ",
                                )
                                .trim()
                                .take(160),
                )
            }
        }.getOrElse { error ->
            val detail =
                error.message
                    ?.replace(
                        "\n",
                        " ",
                    )
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?: error
                        .javaClass
                        .simpleName
                        .takeIf {
                            it.isNotBlank()
                        }
                    ?: "Unknown error"

            ConnectionResult(
                connected = false,
                message =
                    detail.take(240),
            )
        }
    }

    suspend fun titleInsight(
        media: MediaItem,
        dna: UserDnaSnapshot?,
        dnaMatchPercent: Int?,
        apiKey: String,
        model: String = DEFAULT_MODEL,
    ): String {
        require(
            apiKey.isNotBlank()
        ) {
            "Gemini API key is required."
        }

        val dnaContext =
            dna
                ?.takeIf {
                    it.topGenres.isNotEmpty()
                }
                ?.let { snapshot ->
                    buildString {
                        append(
                            "Viewer taste context:\n"
                        )

                        append(
                            "- Top genres: "
                        )

                        append(
                            snapshot.topGenres
                                .take(5)
                                .joinToString(
                                    ", "
                                ) { affinity ->
                                    "${affinity.name} ${affinity.percent}%"
                                }
                        )

                        append('\n')

                        if (
                            snapshot.tasteTags
                                .isNotEmpty()
                        ) {
                            append(
                                "- Taste tags: "
                            )

                            append(
                                snapshot.tasteTags
                                    .take(5)
                                    .joinToString(
                                        ", "
                                    )
                            )

                            append('\n')
                        }

                        append(
                            "- DNA confidence: "
                        )

                        append(
                            snapshot.confidencePercent
                        )

                        append("%\n")

                        dnaMatchPercent
                            ?.let { match ->
                                append(
                                    "- Visible DNA Match: "
                                )

                                append(match)

                                append("%\n")
                            }
                    }
                }
                .orEmpty()

        val mediaContext =
            buildString {
                append(
                    "Selected title:\n"
                )

                append(
                    "- Name: ${media.name}\n"
                )

                append(
                    "- Type: ${media.type}\n"
                )

                media.releaseInfo
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        append(
                            "- Release: $it\n"
                        )
                    }

                if (
                    media.genres
                        .isNotEmpty()
                ) {
                    append(
                        "- Genres: "
                    )

                    append(
                        media.genres
                            .take(8)
                            .joinToString(
                                ", "
                            )
                    )

                    append('\n')
                }

                media.runtimeMinutes
                    ?.takeIf {
                        it > 0
                    }
                    ?.let {
                        append(
                            "- Runtime: ${it} minutes\n"
                        )
                    }

                media.imdbRating
                    ?.takeIf {
                        it.isFinite() &&
                            it > 0.0
                    }
                    ?.let {
                        append(
                            "- IMDb rating: $it\n"
                        )
                    }

                media.tmdbRating
                    ?.takeIf {
                        it.isFinite() &&
                            it > 0.0
                    }
                    ?.let {
                        append(
                            "- TMDB rating: $it\n"
                        )
                    }

                media.description
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.take(1400)
                    ?.let {
                        append(
                            "- Overview: $it\n"
                        )
                    }
            }

        val input =
            buildString {
                append(mediaContext)

                if (
                    dnaContext.isNotBlank()
                ) {
                    append('\n')
                    append(dnaContext)
                }

                append(
                    "\nGive a short, useful and spoiler-free insight for this title."
                )

                if (
                    dnaContext.isNotBlank()
                ) {
                    append(
                        " Explain why it may fit this viewer's taste using only the supplied taste context."
                    )
                } else {
                    append(
                        " Focus on what kind of viewing experience the title appears to offer."
                    )
                }
            }

        return interact(
            apiKey = apiKey,
            model = model,
            input = input,
            systemInstruction =
                "You are VUEO's optional movie and series assistant. " +
                    "Write 2 to 4 concise sentences in clear English. " +
                    "Stay spoiler-free. Use only facts supplied by VUEO. " +
                    "Do not invent plot details, ratings, cast, awards or availability. " +
                    "If viewer taste context is supplied, explain the fit naturally without claiming certainty.",
            maxOutputTokens = 256,
        )
    }

    private suspend fun interact(
        apiKey: String,
        model: String,
        input: String,
        systemInstruction: String,
        maxOutputTokens: Int,
    ): String =
        withContext(
            Dispatchers.IO
        ) {
            val generationConfig =
                JSONObject()
                    .put(
                        "max_output_tokens",
                        maxOutputTokens,
                    )
                    .put(
                        "thinking_level",
                        "minimal",
                    )

            val payload =
                JSONObject()
                    .put(
                        "model",
                        model,
                    )
                    .put(
                        "store",
                        false,
                    )
                    .put(
                        "input",
                        input,
                    )
                    .put(
                        "system_instruction",
                        systemInstruction,
                    )
                    .put(
                        "generation_config",
                        generationConfig,
                    )

            val request =
                Request.Builder()
                    .url(API_URL)
                    .header(
                        "x-goog-api-key",
                        apiKey.trim(),
                    )
                    .header(
                        "Accept",
                        "application/json",
                    )
                    .header(
                        "User-Agent",
                        "VUEO/0.9.6",
                    )
                    .post(
                        payload
                            .toString()
                            .toRequestBody(
                                jsonMediaType
                            )
                    )
                    .build()

            client
                .newCall(request)
                .execute()
                .use { response ->
                    val body =
                        response.body
                            .string()

                    if (
                        !response
                            .isSuccessful
                    ) {
                        val message =
                            extractApiError(
                                body
                            )

                        error(
                            "Gemini HTTP ${response.code}: $message"
                        )
                    }

                    extractText(
                        JSONObject(body)
                    )
                }
        }

    private fun extractApiError(
        body: String,
    ): String {
        if (
            body.isBlank()
        ) {
            return "Request failed"
        }

        return runCatching {
            val json =
                JSONObject(body)

            val error =
                json.optJSONObject(
                    "error"
                )

            val message =
                error
                    ?.optString(
                        "message"
                    )
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            val status =
                error
                    ?.optString(
                        "status"
                    )
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            when {
                message != null &&
                    status != null ->
                    "$status: $message"

                message != null ->
                    message

                else ->
                    body
                        .replace(
                            "\n",
                            " ",
                        )
                        .trim()
                        .take(220)
            }
        }.getOrElse {
            body
                .replace(
                    "\n",
                    " ",
                )
                .trim()
                .take(220)
        }
    }

    private fun extractText(
        json: JSONObject,
    ): String {
        val steps =
            json.optJSONArray(
                "steps"
            )
                ?: error(
                    "Gemini returned no output steps."
                )

        for (
            stepIndex in
            steps.length() - 1
                downTo 0
        ) {
            val step =
                steps.optJSONObject(
                    stepIndex
                )
                    ?: continue

            if (
                step.optString(
                    "type"
                ) !=
                "model_output"
            ) {
                continue
            }

            val content =
                step.optJSONArray(
                    "content"
                )
                    ?: continue

            val parts =
                mutableListOf<String>()

            for (
                contentIndex in
                0 until content.length()
            ) {
                val block =
                    content.optJSONObject(
                        contentIndex
                    )
                        ?: continue

                if (
                    block.optString(
                        "type"
                    ) !=
                    "text"
                ) {
                    continue
                }

                block.optString(
                    "text"
                )
                    .trim()
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.let(
                        parts::add
                    )
            }

            if (
                parts.isNotEmpty()
            ) {
                return parts
                    .joinToString(
                        "\n"
                    )
                    .trim()
            }
        }

        val status =
            json.optString(
                "status"
            )
                .trim()

        val errors =
            json.optJSONArray(
                "errors"
            )

        if (
            errors != null &&
            errors.length() > 0
        ) {
            val first =
                errors.optJSONObject(
                    0
                )

            val message =
                first
                    ?.optString(
                        "message"
                    )
                    ?.trim()
                    ?.takeIf {
                        it.isNotBlank()
                    }

            if (
                message != null
            ) {
                error(
                    "Gemini response error: $message"
                )
            }
        }

        if (
            status.isNotBlank()
        ) {
            error(
                "Gemini returned no text output. Status: $status"
            )
        }

        error(
            "Gemini returned no text output."
        )
    }
}
