package com.vueo.tv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

private object TvImageCache {
    private const val DISK_CACHE_DIRECTORY = "vueo-tv-image-cache"
    private const val MAX_DISK_CACHE_BYTES = 96L * 1024L * 1024L
    private const val MAX_DOWNLOAD_BYTES = 16L * 1024L * 1024L

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L)
            .coerceAtMost(24L * 1024L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    private val urlLocks = Array(64) { Mutex() }
    private val loadingPermits = Semaphore(3)
    // Ordinary posters use at most two slots, leaving capacity for hero/focus.
    private val ordinaryPermits = Semaphore(2)
    private val diskCacheCleaned = AtomicBoolean(false)

    private fun key(url: String, size: IntSize) = "$url:${size.width}x${size.height}"

    fun memoryEntry(url: String?, size: IntSize): Bitmap? =
        url?.takeIf(String::isNotBlank)?.let { memoryCache.get(key(it, size)) }

    suspend fun load(context: Context, url: String, size: IntSize, highPriority: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            val cacheKey = key(url, size)
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val lock = urlLocks[(url.hashCode() and Int.MAX_VALUE) % urlLocks.size]
            lock.withLock {
                currentCoroutineContext().ensureActive()
                memoryCache.get(cacheKey)?.let { return@withLock it }
                suspend fun loadWithPermit(): Bitmap? = loadingPermits.withPermit {
                    currentCoroutineContext().ensureActive()

                    val cacheDirectory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
                    if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
                    cleanDiskCacheOnce(cacheDirectory)

                    val cacheFile = File(cacheDirectory, url.sha256())
                    readCachedBitmap(cacheFile, size)?.let {
                        memoryCache.put(cacheKey, it)
                        return@withPermit it
                    }

                    var downloaded: Bitmap? = null
                    repeat(2) { attempt ->
                        if (downloaded == null) {
                            downloaded = downloadBitmap(url, cacheFile, size)
                            if (downloaded == null && attempt == 0) delay(250)
                        }
                    }

                    currentCoroutineContext().ensureActive()
                    downloaded?.also { memoryCache.put(cacheKey, it) }
                }
                if (highPriority) loadWithPermit()
                else ordinaryPermits.withPermit { loadWithPermit() }
            }
        }

    private fun readCachedBitmap(file: File, size: IntSize): Bitmap? {
        if (!file.isFile) return null
        val bitmap = decodeBitmap(file, size)
        if (bitmap == null) {
            file.delete()
        } else {
            file.setLastModified(System.currentTimeMillis())
        }
        return bitmap
    }

    private suspend fun downloadBitmap(url: String, cacheFile: File, size: IntSize): Bitmap? {
        val temporaryFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temporaryFile.delete()

        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }
            .getOrNull() ?: return null

        return try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "VUEO-TV")

            if (connection.responseCode !in 200..299) return null
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_DOWNLOAD_BYTES) return null

            connection.inputStream.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_DOWNLOAD_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            val bitmap = decodeBitmap(temporaryFile, size) ?: return null
            if (!temporaryFile.renameTo(cacheFile)) {
                temporaryFile.copyTo(cacheFile, overwrite = true)
                temporaryFile.delete()
            }
            bitmap
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
            temporaryFile.delete()
        }
    }

    private fun decodeBitmap(file: File, size: IntSize): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = TvImageDecodePolicy.sampleSize(
                    bounds.outWidth, bounds.outHeight, size.width, size.height,
                )
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: OutOfMemoryError) {
            // A failed image must not terminate navigation on memory-limited TVs.
            memoryCache.evictAll()
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanDiskCacheOnce(directory: File) {
        if (!diskCacheCleaned.compareAndSet(false, true)) return

        val files = directory.listFiles()?.filter(File::isFile).orEmpty()
        var currentSize = files.sumOf(File::length)
        if (currentSize <= MAX_DISK_CACHE_BYTES) return

        files.sortedBy(File::lastModified).forEach { file ->
            if (currentSize <= MAX_DISK_CACHE_BYTES) return
            val fileSize = file.length()
            if (file.delete()) currentSize -= fileSize
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
}

@Composable
fun TvNetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: Color = TvDesign.SurfaceRaised,
    loadEnabled: Boolean = true,
    highPriority: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    var image by remember(url) {
        mutableStateOf(TvImageCache.memoryEntry(url, targetSize))
    }

    LaunchedEffect(url, targetSize, loadEnabled, highPriority) {
        if (targetSize.width <= 0 || targetSize.height <= 0) return@LaunchedEffect
        val cached = TvImageCache.memoryEntry(url, targetSize)
        if (cached != null) {
            image = cached
            return@LaunchedEffect
        }
        if (!loadEnabled || url.isNullOrBlank() || !url.startsWith("https://")) return@LaunchedEffect
        TvImageCache.load(context, url, targetSize, highPriority)?.let { image = it }
    }

    val imageAlpha by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "tvNetworkImageFade",
    )

    Box(modifier = modifier.background(fallback).onSizeChanged { measured ->
        val size = IntSize(
            TvImageDecodePolicy.targetDimension(measured.width),
            TvImageDecodePolicy.targetDimension(measured.height),
        )
        if (size != targetSize) targetSize = size
    }) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize().alpha(imageAlpha),
                contentScale = contentScale,
            )
        }
    }
}
