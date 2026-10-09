package com.vueo.tv.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.vueo.shared.core.player.SubtitleSessionCache
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/** One on-demand TV media cache. It never preloads extra video or increases the RAM buffer. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object TvVideoCache {
    private const val MAX_BYTES = 64L * 1024L * 1024L
    private val mediaSuffixes = arrayOf(".ts", ".m2ts", ".m4s", ".mp4", ".aac", ".m4a", ".cmfv", ".cmfa")
    private val lock = Any()
    private var instance: SimpleCache? = null
    @Volatile private var disabled = false

    // Invoked from DataSource.open on Media3's loader thread, not player construction.
    private fun get(context: Context): SimpleCache? {
        if (disabled || Looper.myLooper() == Looper.getMainLooper()) return null
        return synchronized(lock) {
            if (disabled) return@synchronized null
            instance?.let { return@synchronized it }
            var candidate: SimpleCache? = null
            var database: StandaloneDatabaseProvider? = null
            try {
                val createdDatabase = StandaloneDatabaseProvider(context.applicationContext)
                database = createdDatabase
                val createdCache = SimpleCache(
                    File(context.cacheDir, "tv_video_cache_v1"),
                    LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                    createdDatabase,
                )
                candidate = createdCache
                createdCache.checkInitialization()
                instance = createdCache
                createdCache
            } catch (_: Exception) {
                disabled = true
                runCatching { candidate?.release() }
                runCatching { database?.close() }
                null
            }
        }
    }

    private data class SourcePolicy(
        val url: String,
        val namespace: String,
        val segmented: Boolean,
        val enabled: Boolean,
    )

    class Factory(
        context: Context,
        private val upstream: DataSource.Factory,
    ) : DataSource.Factory {
        private val appContext = context.applicationContext
        @Volatile private var policy: SourcePolicy? = null

        fun setSource(url: String, headers: Map<String, String>, mimeType: String?) {
            val segmented = mimeType == "application/x-mpegURL" || mimeType == "application/dash+xml"
            // Keep source/header identities separate; never strip signed URL query parameters.
            val identity = buildString {
                append(url.length).append(':').append(url)
                headers.entries.sortedBy { it.key.lowercase(Locale.ROOT) }.forEach { (key, value) ->
                    append(key.length).append(':').append(key)
                    append(value.length).append(':').append(value)
                }
            }
            val namespace = MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            // Segments wait for a concrete VOD timeline, so live streams cannot reuse stale data.
            policy = SourcePolicy(url, namespace, segmented, enabled = !segmented)
        }

        fun setTimeline(url: String?, live: Boolean) {
            val current = policy ?: return
            if (current.url == url) policy = current.copy(enabled = !live)
        }

        override fun createDataSource(): DataSource = VideoDataSource(appContext, upstream) cacheKey@{ spec ->
            val current = policy ?: return@cacheKey null
            if (!current.enabled || spec.httpMethod != DataSpec.HTTP_METHOD_GET ||
                SubtitleSessionCache.isRegistered(spec.uri.toString()) ||
                (spec.uri.scheme != "http" && spec.uri.scheme != "https")
            ) return@cacheKey null
            val path = spec.uri.path.orEmpty().lowercase(Locale.ROOT)
            // Manifest, key and subtitle requests remain on the original network/session path.
            if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return@cacheKey null
            val primaryFile = !current.segmented && spec.uri.toString() == current.url
            val mediaSegment = current.segmented && mediaSuffixes.any { path.endsWith(it) }
            if (!primaryFile && !mediaSegment) return@cacheKey null
            "${current.namespace}:${spec.key ?: spec.uri.toString()}"
        }
    }

    private class VideoDataSource(
        private val context: Context,
        private val upstream: DataSource.Factory,
        private val cacheKey: (DataSpec) -> String?,
    ) : DataSource {
        private val listeners = mutableListOf<TransferListener>()
        private var active: DataSource? = null
        private var request: DataSpec? = null
        private var deliveredBytes = 0L
        private var usingCache = false

        override fun addTransferListener(transferListener: TransferListener) {
            listeners += transferListener
            active?.addTransferListener(transferListener)
        }

        private fun attach(source: DataSource): DataSource {
            listeners.forEach(source::addTransferListener)
            active = source
            return source
        }

        override fun open(dataSpec: DataSpec): Long {
            check(active == null) { "DataSource is already open" }
            request = dataSpec
            deliveredBytes = 0L
            val key = cacheKey(dataSpec)
            val cache = if (key != null) get(context) else null
            usingCache = cache != null
            if (cache == null) return attach(upstream.createDataSource()).open(dataSpec)
            val cachedSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                // Do not block a parallel request behind a locked cache span.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
            val cachedSpec = dataSpec.buildUpon().setKey(key)
                // Commit bounded fragments during long progressive reads, not one huge file at close.
                .setFlags(dataSpec.flags or DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build()
            return try {
                attach(cachedSource).open(cachedSpec)
            } catch (error: IOException) {
                if (!isCacheFailure(error)) throw error
                bypassCache(dataSpec)
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = try {
                requireNotNull(active).read(buffer, offset, length)
            } catch (error: IOException) {
                if (!usingCache || !isCacheFailure(error)) throw error
                val spec = requireNotNull(request)
                if (spec.length != C.LENGTH_UNSET.toLong() && deliveredBytes >= spec.length) {
                    disabled = true
                    return C.RESULT_END_OF_INPUT
                }
                // Resume at the first byte not returned to Media3; never repeat or skip delivered data.
                bypassCache(spec.subrange(deliveredBytes))
                requireNotNull(active).read(buffer, offset, length)
            }
            if (count > 0) deliveredBytes += count
            return count
        }

        private fun bypassCache(spec: DataSpec): Long {
            disabled = true
            runCatching { active?.close() }
            active = null
            usingCache = false
            return attach(upstream.createDataSource()).open(spec)
        }

        override fun getUri(): Uri? = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

        override fun close() {
            try {
                active?.close()
            } catch (error: IOException) {
                if (!usingCache || !isCacheFailure(error)) throw error
                disabled = true
            } finally {
                active = null
                request = null
                deliveredBytes = 0L
                usingCache = false
            }
        }
    }

    private fun isCacheFailure(error: IOException): Boolean =
        generateSequence<Throwable>(error) { it.cause }.take(8).any {
            it is Cache.CacheException || it is FileDataSource.FileDataSourceException
        }
}
