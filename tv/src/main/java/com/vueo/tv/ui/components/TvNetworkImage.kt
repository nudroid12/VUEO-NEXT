package com.vueo.tv.ui

import com.vueo.tv.ui.motion.*
import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

private object TvImageCache {
    private const val DISK_CACHE_DIRECTORY = "vueo-tv-image-cache"
    private const val MAX_DOWNLOAD_BYTES = 16L * 1024L * 1024L
    private const val MAX_PREVIEWS = 512

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L)
            .coerceIn(1L, 24L * 1024L)
            .toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            ((value.allocationByteCount.toLong() + 1023L) / 1024L).toInt().coerceAtLeast(1)
    }

    private val urlLocks = Array(64) { Mutex() }
    private val loadingPermits = Semaphore(3)
    // Ordinary posters use at most two slots, leaving capacity for hero/focus.
    private val ordinaryPermits = Semaphore(2)
    // Lock only local disk operations, never network I/O. Readers and final
    // publication cannot race with eviction; the hero's reserved permit stays intact.
    private val diskLock = Mutex()
    private val diskMaintenance = TvImageDiskCacheMaintenance()
    private val callbacksRegistered = AtomicBoolean(false)
    private val previewByUrl = LinkedHashMap<String, WeakReference<Bitmap>>(MAX_PREVIEWS, 0.75f, true)

    private fun key(url: String, size: IntSize) = "$url:${size.width}x${size.height}"

    private fun rememberPreview(url: String, bitmap: Bitmap): Unit = synchronized(previewByUrl) {
        if (!bitmap.isRecycled) {
            previewByUrl[url] = WeakReference(bitmap)
            while (previewByUrl.size > MAX_PREVIEWS) {
                previewByUrl.remove(previewByUrl.keys.first())
            }
        }
    }

    private fun clearMemory() {
        memoryCache.evictAll()
        synchronized(previewByUrl) { previewByUrl.clear() }
        // Do not recycle bitmaps; a Compose image may still be displaying one.
    }

    private fun registerCallbacks(context: Context) {
        if (!callbacksRegistered.compareAndSet(false, true)) return
        context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
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

    fun memoryEntry(url: String?, size: IntSize): Bitmap? =
        url?.takeIf(String::isNotBlank)?.let { cleanUrl ->
            memoryCache.get(key(cleanUrl, size))?.also { rememberPreview(cleanUrl, it) }
        }

    // A previous decoded size can paint immediately while layout determines
    // the exact target size. Keep an O(1) weak preview index instead of copying
    // the whole LruCache with snapshot() for every composed poster.
    fun cachedPreview(url: String?): Bitmap? = synchronized(previewByUrl) {
        if (url.isNullOrBlank()) return@synchronized null
        val bitmap = previewByUrl[url]?.get()
        if (bitmap == null || bitmap.isRecycled) {
            previewByUrl.remove(url)
            null
        } else bitmap
    }

    suspend fun load(context: Context, url: String, size: IntSize, highPriority: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            registerCallbacks(context)
            val cacheKey = key(url, size)
            memoryCache.get(cacheKey)?.let { return@withContext it }

            val lock = urlLocks[(url.hashCode() and Int.MAX_VALUE) % urlLocks.size]
            lock.withLock loadUrl@{
                currentCoroutineContext().ensureActive()
                memoryCache.get(cacheKey)?.let { return@loadUrl it }
                val cacheDirectory = File(context.cacheDir, DISK_CACHE_DIRECTORY)
                val cacheFile = File(cacheDirectory, url.sha256())
                // A disk cache hit should not wait for an available network slot.
                val cached = diskLock.withLock {
                    if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) {
                        return@loadUrl null
                    }
                    diskMaintenance.maintain(cacheDirectory, SystemClock.elapsedRealtime())
                    readCachedBitmap(cacheFile, size)
                }
                cached?.let {
                    memoryCache.put(cacheKey, it)
                    rememberPreview(url, it)
                    return@loadUrl it
                }
                suspend fun loadWithPermit(): Bitmap? = loadingPermits.withPermit {
                    currentCoroutineContext().ensureActive()
                    var downloaded: Bitmap? = null
                    repeat(2) { attempt ->
                        if (downloaded == null) {
                            downloaded = downloadBitmap(url, cacheFile, size)
                            if (downloaded == null && attempt == 0) delay(250)
                        }
                    }

                    currentCoroutineContext().ensureActive()
                    downloaded?.also {
                        memoryCache.put(cacheKey, it)
                        rememberPreview(url, it)
                    }
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
            currentCoroutineContext().ensureActive()
            diskLock.withLock {
                if (!temporaryFile.renameTo(cacheFile)) {
                    // No partial final file may survive a failed copy.
                    try {
                        temporaryFile.copyTo(cacheFile, overwrite = true)
                    } catch (error: Exception) {
                        cacheFile.delete()
                        throw error
                    }
                    temporaryFile.delete()
                }
                cacheFile.setLastModified(System.currentTimeMillis())
                // Recheck the budget after every completed download, not just at launch.
                diskMaintenance.maintain(
                    requireNotNull(cacheFile.parentFile),
                    SystemClock.elapsedRealtime(),
                    force = true,
                )
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
            clearMemory()
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
}

/** Opt-in Home prefetch; shares existing limits, cache and download policy. */
internal suspend fun tvPrefetchImage(context: Context, url: String?, size: IntSize) {
    if (url.isNullOrBlank() || !com.vueo.shared.core.stremio.StremioUrls.isHttpUrl(url)) return
    val target = IntSize(
        TvImageDecodePolicy.targetDimension(size.width),
        TvImageDecodePolicy.targetDimension(size.height),
    )
    TvImageCache.load(context, url, target, highPriority = false)
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
    alignment: Alignment = Alignment.Center,
    onLoadResult: ((Boolean) -> Unit)? = null,
    fadeEnabled: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    val latestLoadResult by rememberUpdatedState(onLoadResult)
    var targetSize by remember { mutableStateOf(IntSize.Zero) }
    var image by remember(url) {
        mutableStateOf(TvImageCache.memoryEntry(url, targetSize) ?: TvImageCache.cachedPreview(url))
    }

    LaunchedEffect(url, targetSize, loadEnabled, highPriority) {
        if (targetSize.width <= 0 || targetSize.height <= 0) return@LaunchedEffect
        val cached = TvImageCache.memoryEntry(url, targetSize)
        if (cached != null) {
            image = cached
            latestLoadResult?.invoke(true)
            return@LaunchedEffect
        }
        if (!loadEnabled) return@LaunchedEffect
        if (url.isNullOrBlank() || !com.vueo.shared.core.stremio.StremioUrls.isHttpUrl(url)) {
            latestLoadResult?.invoke(false)
            return@LaunchedEffect
        }
        val loaded = TvImageCache.load(context, url, targetSize, highPriority)
        loaded?.let { image = it }
        latestLoadResult?.invoke(image != null)
    }

    val imageAlpha by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tvTunedSpec(TvMotionGroup.IMAGE, image != null, tween(durationMillis = 180)),
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
                modifier = Modifier.matchParentSize().alpha(if (fadeEnabled) imageAlpha else 1f),
                contentScale = contentScale,
                alignment = alignment,
            )
        }
    }
}

/** Keeps the last decoded artwork painted until its replacement is ready. */
@Composable
internal fun TvRetainedArtwork(
    url: String?,
    modifier: Modifier,
    fallback: Color = TvDesign.Black,
    slideIn: Boolean = false,
) {
    val context = LocalContext.current.applicationContext
    var size by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<Bitmap?>(null) }
    var previous by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url, size) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val artwork = url?.takeIf(com.vueo.shared.core.stremio.StremioUrls::isHttpUrl)?.let {
            TvImageCache.memoryEntry(it, size)
                ?: TvImageCache.load(context, it, size, highPriority = true)
        }
        if (artwork == null && !slideIn) return@LaunchedEffect
        if (current === artwork) return@LaunchedEffect
        previous = current
        current = artwork
    }

    val opacity = remember(current) { Animatable(0f) }
    LaunchedEffect(current) {
        if (current == null) {
            previous = null
            return@LaunchedEffect
        }
        opacity.animateTo(
            1f,
            tvTunedSpec(TvMotionGroup.IMAGE, true, tween(durationMillis = 220)),
        )
        previous = null
    }

    Box(modifier.background(fallback).onSizeChanged {
        size = IntSize(
            TvImageDecodePolicy.targetDimension(it.width),
            TvImageDecodePolicy.targetDimension(it.height),
        )
    }) {
        previous?.let {
            Image(it.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        current?.let {
            Image(
                it.asImageBitmap(),
                null,
                Modifier.matchParentSize().graphicsLayer {
                    alpha = opacity.value
                    if (slideIn) translationX = this.size.width * .22f * (1f - opacity.value)
                },
                contentScale = ContentScale.Crop,
            )
        }
    }
}
