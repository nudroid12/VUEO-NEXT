package com.vueo.mobile.ui.components

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

private object VueoImageCache {
    private const val DISK_CACHE_DIRECTORY = "vueo-image-cache"
    private const val MAX_DISK_CACHE_BYTES = 96L * 1024L * 1024L
    private const val MAX_DOWNLOAD_BYTES = 16L * 1024L * 1024L
    private const val PRUNE_INTERVAL_MS = 60_000L
    private const val MAX_PREVIEWS = 512

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L)
            .coerceIn(1L, 24L * 1024L).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            ((value.allocationByteCount.toLong() + 1023L) / 1024L).toInt().coerceAtLeast(1)
    }
    // Fixed stripes bound bookkeeping even during long browsing sessions.
    private val urlLocks = Array(64) { Mutex() }
    private val loadingPermits = Semaphore(3)
    private val diskLock = Mutex()
    private var lastPruneMs = 0L // guarded by diskLock
    private var removedOrphanTemps = false // guarded by diskLock
    private val callbacksRegistered = AtomicBoolean(false)
    private val previews = LinkedHashMap<String, WeakReference<Bitmap>>(MAX_PREVIEWS, 0.75f, true)

    private fun key(url: String, size: IntSize) = "$url:${size.width}x${size.height}"

    private fun rememberPreview(url: String, bitmap: Bitmap) = synchronized(previews) {
        previews[url] = WeakReference(bitmap)
        while (previews.size > MAX_PREVIEWS) previews.remove(previews.keys.first())
    }

    fun cachedPreview(url: String?): Bitmap? = synchronized(previews) {
        if (url.isNullOrBlank()) return@synchronized null
        val bitmap = previews[url]?.get()
        if (bitmap == null || bitmap.isRecycled) {
            previews.remove(url)
            null
        } else bitmap
    }

    fun memoryEntry(url: String?, size: IntSize): Bitmap? =
        url?.takeIf(String::isNotBlank)?.let { memoryCache.get(key(it, size)) }

    private fun clearMemory() {
        memoryCache.evictAll()
        synchronized(previews) { previews.clear() }
        // Do not recycle: Compose may still be displaying an evicted bitmap.
    }

    private fun registerCallbacks(context: Context) {
        if (!callbacksRegistered.compareAndSet(false, true)) return
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = clearMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) clearMemory()
                else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    memoryCache.trimToSize(memoryCache.maxSize() / 2)
                }
            }
        })
    }

    suspend fun load(context: Context, url: String, size: IntSize): Bitmap? =
        withContext(Dispatchers.IO) {
            registerCallbacks(context)
            val cacheKey = key(url, size)
            memoryCache.get(cacheKey)?.let { return@withContext it }
            urlLocks[(url.hashCode() and Int.MAX_VALUE) % urlLocks.size].withLock {
                currentCoroutineContext().ensureActive()
                memoryCache.get(cacheKey)?.let { return@withLock it }
                loadingPermits.withPermit {
                    currentCoroutineContext().ensureActive()
                    val directory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
                    val file = File(directory, url.sha256())
                    val cached = diskLock.withLock {
                        directory.mkdirs()
                        pruneDiskCache(directory)
                        if (!file.isFile) null else decodeBitmap(file, size).also { bitmap ->
                            if (bitmap == null) file.delete()
                            else file.setLastModified(System.currentTimeMillis())
                        }
                    }
                    var bitmap = cached
                    if (bitmap == null) {
                        repeat(2) { attempt ->
                            if (bitmap == null) {
                                bitmap = downloadBitmap(url, file, size)
                                if (bitmap == null && attempt == 0) delay(250)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    bitmap?.also {
                        memoryCache.put(cacheKey, it)
                        rememberPreview(url, it)
                    }
                }
            }
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
            connection.setRequestProperty("User-Agent", "VUEO/0.2")
            currentCoroutineContext().ensureActive()
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) return null
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
            currentCoroutineContext().ensureActive()
            diskLock.withLock {
                if (!temporaryFile.renameTo(cacheFile)) {
                    temporaryFile.copyTo(cacheFile, overwrite = true)
                    temporaryFile.delete()
                }
                // Enforce the disk budget after every published download.
                pruneDiskCache(requireNotNull(cacheFile.parentFile), force = true)
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

    private fun decodeBitmap(file: File, size: IntSize): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = MobileImageDecodePolicy.sampleSize(
                    bounds.outWidth, bounds.outHeight, size.width, size.height,
                )
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }
    } catch (_: OutOfMemoryError) {
        clearMemory()
        null
    } catch (_: Exception) {
        null
    }

    // All final-file reads, publication and pruning share diskLock. Active
    // downloads use .tmp files and are never removed by this maintenance.
    private fun pruneDiskCache(directory: File, force: Boolean = false) {
        if (!removedOrphanTemps) {
            // First maintenance runs before this process starts any download.
            directory.listFiles()?.filter { it.isFile && it.name.endsWith(".tmp") }
                ?.forEach { it.delete() }
            removedOrphanTemps = true
        }
        val now = SystemClock.elapsedRealtime()
        if (!force && lastPruneMs != 0L && now - lastPruneMs < PRUNE_INTERVAL_MS) return
        lastPruneMs = now
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        var currentSize = files.sumOf(File::length)
        if (currentSize <= MAX_DISK_CACHE_BYTES) return
        for (file in files.sortedBy(File::lastModified)) {
            if (currentSize <= MAX_DISK_CACHE_BYTES) break
            val bytes = file.length()
            if (file.delete()) currentSize -= bytes
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
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
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    var image by remember(url) {
        mutableStateOf(VueoImageCache.memoryEntry(url, targetSize) ?: VueoImageCache.cachedPreview(url))
    }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url, targetSize) {
        failed = false
        if (url.isNullOrBlank() || !url.startsWith("https://")) {
            failed = true
            return@LaunchedEffect
        }
        if (targetSize.width <= 0 || targetSize.height <= 0) return@LaunchedEffect
        val loaded = VueoImageCache.memoryEntry(url, targetSize)
            ?: VueoImageCache.load(context, url, targetSize)
        // Keep the same URL's existing image while its new size loads/fails.
        if (loaded != null) image = loaded
        failed = image == null
    }

    val imageAlpha by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "network-image-fade",
    )

    Box(
        modifier =
            (if (transparentBackground) {
                modifier
            } else {
                modifier.background(
                    MaterialTheme.colorScheme.surfaceVariant
                )
            }).onSizeChanged { measured ->
                targetSize = IntSize(
                    MobileImageDecodePolicy.targetDimension(measured.width),
                    MobileImageDecodePolicy.targetDimension(measured.height),
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
