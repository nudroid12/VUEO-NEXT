package com.vueo.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private object VueoImageCache {
    private const val DISK_CACHE_DIRECTORY = "vueo-image-cache"
    private const val MAX_DISK_CACHE_BYTES = 96L * 1024L * 1024L
    private const val MAX_DOWNLOAD_BYTES = 16L * 1024L * 1024L

    private val memoryCache =
        object : LruCache<String, Bitmap>(
            (Runtime.getRuntime().maxMemory() / 1024L / 8L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }

    private val urlLocks = ConcurrentHashMap<String, Mutex>()
    private val diskCacheCleaned = AtomicBoolean(false)

    fun memoryEntry(url: String?): Bitmap? =
        url?.takeIf(String::isNotBlank)?.let(memoryCache::get)

    suspend fun load(context: Context, url: String): Bitmap? =
        withContext(Dispatchers.IO) {
            memoryCache.get(url)?.let { return@withContext it }

            val lock = urlLocks.computeIfAbsent(url) { Mutex() }
            lock.withLock {
                memoryCache.get(url)?.let { return@withLock it }

                val cacheDirectory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
                if (!cacheDirectory.exists()) cacheDirectory.mkdirs()
                cleanDiskCacheOnce(cacheDirectory)

                val cacheFile = File(cacheDirectory, url.sha256())
                readCachedBitmap(cacheFile)?.let {
                    memoryCache.put(url, it)
                    return@withLock it
                }

                var downloaded: Bitmap? = null
                repeat(2) { attempt ->
                    if (downloaded == null) {
                        downloaded = downloadBitmap(url, cacheFile)
                        if (downloaded == null && attempt == 0) delay(250)
                    }
                }

                downloaded?.also { memoryCache.put(url, it) }
            }
        }

    private fun readCachedBitmap(file: File): Bitmap? {
        if (!file.isFile) return null
        val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (bitmap == null) {
            file.delete()
        } else {
            file.setLastModified(System.currentTimeMillis())
        }
        return bitmap
    }

    private fun downloadBitmap(url: String, cacheFile: File): Bitmap? {
        val temporaryFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        temporaryFile.delete()

        val connection =
            runCatching { URL(url).openConnection() as HttpURLConnection }
                .getOrNull()
                ?: return null

        return try {
            connection.connectTimeout = 7_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "VUEO/0.2")

            if (connection.responseCode !in 200..299) return null
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_DOWNLOAD_BYTES) return null

            connection.inputStream.use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_DOWNLOAD_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                }
            }

            val bitmap = BitmapFactory.decodeFile(temporaryFile.absolutePath) ?: return null
            if (!temporaryFile.renameTo(cacheFile)) {
                temporaryFile.copyTo(cacheFile, overwrite = true)
                temporaryFile.delete()
            }
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
            temporaryFile.delete()
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
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = "",
    transparentBackground: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    var image by remember(url) { mutableStateOf(VueoImageCache.memoryEntry(url)) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        image = VueoImageCache.memoryEntry(url)
        failed = false

        if (image != null) return@LaunchedEffect
        if (url.isNullOrBlank() || !url.startsWith("https://")) {
            failed = true
            return@LaunchedEffect
        }

        image = VueoImageCache.load(context, url)
        failed = image == null
    }

    val imageAlpha by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "network-image-fade",
    )

    Box(
        modifier =
            if (transparentBackground) {
                modifier
            } else {
                modifier.background(
                    MaterialTheme.colorScheme.surfaceVariant
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize().alpha(imageAlpha),
                contentScale = contentScale,
            )
        }

        if (failed && fallbackText.isNotBlank()) {
            Text(
                text = fallbackText.take(1).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
