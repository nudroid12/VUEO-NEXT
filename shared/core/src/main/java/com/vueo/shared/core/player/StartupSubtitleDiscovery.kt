package com.vueo.shared.core.player

import com.vueo.shared.core.media.SubtitleTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One selected-source session. Closing is immediate at the first-frame callback. */
class StartupSubtitleDiscovery {
    private val lock = Any()
    private var closed = false
    private var tracks = emptyList<SubtitleTrack>()
    private var initialized = false
    private val jobs = mutableSetOf<Job>()
    val isOpen: Boolean get() = synchronized(lock) { !closed }

    fun snapshot(incoming: List<SubtitleTrack>): List<SubtitleTrack> = synchronized(lock) {
        if (!closed) {
            val existing = tracks.mapTo(mutableSetOf()) { it.url }
            val accepted = incoming.filterNot(DeferredPluginSubtitles::isIndex).map {
                // New startup sidecars use the overlay; arrival never replaces the video item.
                if (initialized && it.url !in existing) it.copy(liveOverlay = true) else it
            }
            initialized = true
            tracks = (tracks + accepted).filterNot(DeferredPluginSubtitles::isIndex)
                .groupBy { it.url }.values.map { rows -> rows.firstOrNull { it.liveOverlay } ?: rows.last() }
        }
        tracks
    }

    fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            jobs.toList().also { jobs.clear() }
        }
        pending.forEach { it.cancel() }
    }

    suspend fun <T> discover(block: suspend () -> T): T? {
        val context = currentCoroutineContext()
        val job = requireNotNull(context[Job])
        synchronized(lock) {
            if (closed) return null
            jobs.add(job)
        }
        try {
            context.ensureActive()
            val result = block()
            context.ensureActive()
            return if (isOpen) result else null
        } finally { synchronized(lock) { jobs.remove(job) } }
    }
}
