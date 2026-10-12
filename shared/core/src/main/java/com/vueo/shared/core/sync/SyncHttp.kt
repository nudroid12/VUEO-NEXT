package com.vueo.shared.core.sync

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import android.util.JsonReader
import com.vueo.shared.core.storage.CompactLibraryCodec
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class SyncHttpError(val code: Int, val reason: String) : IOException("VUEO Sync HTTP $code")
internal object SyncHttp {
    private val client = OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS)
        .connectTimeout(8,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    suspend fun post(path: String, value: JSONObject, token: String? = null, compact: Boolean=false): JSONObject =
        suspendCancellableCoroutine { continuation ->
            val builder = Request.Builder().url(VueoSyncConfig.URL+path)
                .header("apikey",VueoSyncConfig.KEY).header("Content-Type","application/json")
                .post(value.toString().toRequestBody("application/json".toMediaType()))
            if (token != null) builder.header("Authorization","Bearer $token")
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if(compact && it.isSuccessful) {
                                return@use JsonReader(InputStreamReader(LimitedStream(it.body.byteStream()),Charsets.UTF_8)).use { reader ->
                                    val json=CompactLibraryCodec.readValue(reader) as JSONObject
                                    check(reader.peek()==android.util.JsonToken.END_DOCUMENT)
                                    json
                                }
                            }
                            val output = ByteArrayOutputStream()
                            it.body.byteStream().use { stream ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val count = stream.read(buffer)
                                    if (count < 0) break
                                    if (output.size()+count > 8*1024*1024) throw IOException("Sync response too large")
                                    output.write(buffer,0,count)
                                }
                            }
                            val text = output.toString("UTF-8")
                            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
                            if (!it.isSuccessful) throw SyncHttpError(it.code,json.optString("error_code",json.optString("code")))
                            json
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
    private class LimitedStream(input: InputStream): FilterInputStream(input) {
        private var bytes=0L
        private fun counted(count: Int): Int {
            if(count>0) bytes+=count
            if(bytes>8*1024*1024) throw IOException("Sync response too large")
            return count
        }
        override fun read(): Int { val value=`in`.read(); counted(if(value<0) 0 else 1); return value }
        override fun read(buffer: ByteArray,offset: Int,length: Int)=counted(`in`.read(buffer,offset,length))
    }
}
