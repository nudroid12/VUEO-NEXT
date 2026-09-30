package com.vueo.tv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private object TvBitmapCache : LruCache<String, Bitmap>(48 * 1024) {
    private val latestKeyByUrl = ConcurrentHashMap<String, String>()

    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.byteCount / 1024).coerceAtLeast(1)

    fun putSized(url: String, key: String, bitmap: Bitmap) {
        put(key, bitmap)
        latestKeyByUrl[url] = key
    }

    fun bestAvailable(url: String, exactKey: String?): Bitmap? =
        exactKey?.let(::get) ?: latestKeyByUrl[url]?.let(::get)
}

private fun bucketedImageSize(size: IntSize): IntSize {
    fun bucket(value: Int): Int = ((value + 63) / 64) * 64
    return if (size.width > 0 && size.height > 0) {
        IntSize(bucket(size.width), bucket(size.height))
    } else {
        IntSize.Zero
    }
}

private fun bitmapCacheKey(url: String, size: IntSize): String? =
    if (size.width > 0 && size.height > 0) "$url@${size.width}x${size.height}" else null

private fun decodeSampledBitmap(bytes: ByteArray, target: IntSize): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= target.width &&
        bounds.outHeight / (sampleSize * 2) >= target.height
    ) {
        sampleSize *= 2
    }

    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

@Composable
fun TvNetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: Color = TvDesign.SurfaceRaised,
) {
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    val requestSize = bucketedImageSize(targetSize)
    val cacheKey = url?.let { bitmapCacheKey(it, requestSize) }
    val bitmap by produceState<Bitmap?>(
        initialValue = url?.let { TvBitmapCache.bestAvailable(it, cacheKey) },
        key1 = url,
        key2 = cacheKey,
    ) {
        value = if (url.isNullOrBlank()) {
            null
        } else if (cacheKey == null) {
            TvBitmapCache.bestAvailable(url, null)
        } else {
            TvBitmapCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 7_000
                        readTimeout = 9_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "VUEO-TV")
                    }
                    try {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        decodeSampledBitmap(bytes, requestSize)
                            ?.also { TvBitmapCache.putSized(url, cacheKey, it) }
                    } finally {
                        connection.disconnect()
                    }
                }.getOrNull() ?: value
            }
        }
    }

    val sizedModifier = modifier.onSizeChanged { size ->
        if (size.width > 0 && size.height > 0 && size != targetSize) targetSize = size
    }

    if (bitmap == null) {
        Box(modifier = sizedModifier.background(fallback))
    } else {
        Image(
            bitmap = requireNotNull(bitmap).asImageBitmap(),
            contentDescription = contentDescription,
            modifier = sizedModifier,
            contentScale = contentScale,
        )
    }
}
