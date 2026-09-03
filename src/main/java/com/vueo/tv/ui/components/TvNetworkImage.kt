package com.vueo.tv.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun TvNetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap by
        produceState<Bitmap?>(
            initialValue = url?.let(TvBitmapLoader::cached),
            key1 = url,
        ) {
            value =
                if (url.isNullOrBlank()) {
                    null
                } else {
                    TvBitmapLoader.load(url)
                }
        }

    Box(
        modifier =
            modifier.background(
                Color(0xFF121613)
            ),
    ) {
        bitmap?.let { loaded ->
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private object TvBitmapLoader {
    private val cache =
        object : LruCache<String, Bitmap>(24 * 1024) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int =
                (value.byteCount / 1024).coerceAtLeast(1)
        }

    fun cached(url: String): Bitmap? =
        synchronized(cache) {
            cache.get(url)
        }

    suspend fun load(url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            cached(url)?.let {
                return@withContext it
            }

            if (!url.startsWith("https://")) {
                return@withContext null
            }

            val bitmap =
                runCatching {
                    val connection =
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 4_000
                            readTimeout = 6_000
                            instanceFollowRedirects = true
                            setRequestProperty("User-Agent", "VUEO-TV/0.2")
                        }

                    try {
                        if (connection.responseCode !in 200..299) {
                            return@runCatching null
                        }

                        connection.inputStream.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()

            if (bitmap != null) {
                synchronized(cache) {
                    cache.put(url, bitmap)
                }
            }

            bitmap
        }
}
