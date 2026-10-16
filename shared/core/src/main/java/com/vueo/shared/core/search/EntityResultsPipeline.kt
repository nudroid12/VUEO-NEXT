package com.vueo.shared.core.search

import com.vueo.shared.core.media.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/** A terminal batch also carries the latest items, so conflation cannot lose them on failure. */
data class EntityResultsBatch(
    val items: List<MediaItem>,
    val complete: Boolean = false,
    val failureClass: String? = null,
)

object EntityResultsPipeline {
    /** Fetch/parse/merge on Default; consumers collect batches on their own UI context. */
    fun batches(
        fetch: suspend (onPartial: (List<MediaItem>) -> Unit) -> List<MediaItem>,
    ): Flow<EntityResultsBatch> = channelFlow {
        // Preserve the orchestrator's single-caller sequencing for partial state.
        // Network calls can still overlap while suspended; CPU work stays off Main.
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            val lock = Any()
            var accepting = true
            var latest = emptyList<MediaItem>()
            val publish: (List<MediaItem>) -> Unit = { values ->
                synchronized(lock) {
                    if (accepting) {
                        latest = values.toList()
                        trySend(EntityResultsBatch(latest))
                    }
                }
            }
            val terminal = try {
                val final = fetch(publish)
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    accepting = false
                    EntityResultsBatch(final.toList(), complete = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(lock) {
                    accepting = false
                    EntityResultsBatch(latest, complete = true, failureClass = error.javaClass.simpleName)
                }
            } finally {
                synchronized(lock) { accepting = false }
            }
            send(terminal)
        }
    }.buffer(Channel.CONFLATED)

    /** Cancellation is checked around non-suspending presentation preparation too. */
    suspend fun <T> prepare(block: suspend () -> T): T = withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        block().also { currentCoroutineContext().ensureActive() }
    }
}
