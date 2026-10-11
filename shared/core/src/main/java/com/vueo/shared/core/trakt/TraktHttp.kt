package com.vueo.shared.core.trakt

import com.vueo.shared.core.plugin.PluginHttp
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class TraktReply(val body: String, val pages: Int)
internal class TraktHttpError(val status: Int, val retryMs: Long, val payload: String) :
    IOException("Trakt HTTP $status")

internal object TraktHttp {
    private val writeGate = Mutex()
    private var lastWrite = 0L
    @Volatile private var blockedUntil = 0L
    private val client by lazy {
        PluginHttp.client.newBuilder().callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
    suspend fun request(path: String, token: String? = null, body: JSONObject? = null): TraktReply {
        val wait = blockedUntil - android.os.SystemClock.elapsedRealtime()
        if (wait > 0) throw TraktHttpError(429, wait, "")
        if (body == null) return execute(path, token, null)
        return writeGate.withLock {
            val pause = lastWrite + 1_100L - android.os.SystemClock.elapsedRealtime()
            if (pause > 0) delay(pause)
            lastWrite = android.os.SystemClock.elapsedRealtime()
            execute(path, token, body)
        }
    }
    private suspend fun execute(path: String, token: String?, body: JSONObject?): TraktReply =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder().url("https://api.trakt.tv$path")
                .header("trakt-api-key", TraktConfig.CLIENT_ID).header("trakt-api-version", "2")
                .header("Accept", "application/json").header("Content-Type", "application/json")
                .header("User-Agent", "VUEO/0.9.6")
            token?.let { builder.header("Authorization", "Bearer $it") }
            body?.let { builder.post(it.toString().toRequestBody("application/json".toMediaType())) }
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { reply ->
                            val output = ByteArrayOutputStream()
                            reply.body?.byteStream()?.use { input ->
                                val buffer = ByteArray(8_192)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (output.size() + count > 4 * 1024 * 1024) throw IOException("Trakt response too large")
                                    output.write(buffer, 0, count)
                                }
                            }
                            val text = output.toString("UTF-8")
                            if (!reply.isSuccessful) {
                                val retry = (reply.header("Retry-After")?.toLongOrNull() ?: 60L)
                                    .coerceIn(1L, 86_400L) * 1_000L
                                if (reply.code == 429) blockedUntil = android.os.SystemClock.elapsedRealtime() + retry
                                throw TraktHttpError(reply.code, retry, text)
                            }
                            TraktReply(text, reply.header("X-Pagination-Page-Count")?.toIntOrNull() ?: 1)
                        }
                    }
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
}
