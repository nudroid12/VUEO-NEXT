package com.vueo.tv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

private object TvBitmapCache : LruCache<String, Bitmap>(32 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.byteCount / 1024).coerceAtLeast(1)
}

@Composable
fun TvNetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: Color = TvDesign.SurfaceRaised,
) {
    val bitmap by produceState<Bitmap?>(initialValue = url?.let(TvBitmapCache::get), key1 = url) {
        value = if (url.isNullOrBlank()) {
            null
        } else {
            TvBitmapCache.get(url) ?: withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 7_000
                        readTimeout = 9_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "VUEO-TV")
                    }
                    try {
                        connection.inputStream.use(BitmapFactory::decodeStream)
                            ?.also { TvBitmapCache.put(url, it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull()
            }
        }
    }

    if (bitmap == null) {
        Box(modifier = modifier.background(fallback))
    } else {
        Image(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
